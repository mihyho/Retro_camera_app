"""
니콘 Picture Control 레시피(.NP3)를 앱용 3D LUT(.cube)로 근사 변환한다.

NP3 는 색 표가 아니라 슬라이더 묶음이라 니콘 내부 처리와 똑같이 만들 수는 없다. 파일에서 읽는 값:
  - 톤 커브: 파일 끝쪽의 257개 표(0..32767). 입력 0..1 -> 출력 0..1
  - 컬러 블렌더: 빨강/주황/노랑/초록/청록/파랑/보라/자홍 각각 (색조, 채도, 밝기) 조정값(0x80 = 변화 없음)
  - 컬러 그레이딩: 그림자/중간/하이라이트 각각 (색조 각도, 채도)
아래 값은 "근사"를 위한 가정이다. 폰에서 보고 맞춰 가며 조정한다(HUE_MAX, CHROMA_MAX, LIGHT_MAX, GRADE_K).

사용법:  python tools/np3_to_lut.py <입력 폴더>      -> assets/luts/<이름>.cube 5개
"""
import colorsys
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import extract_lut as X  # noqa: E402

ASSETS = Path(__file__).resolve().parent.parent / "FilmCamera" / "app" / "src" / "main" / "assets" / "luts"
RECIPES = {  # 파일 이름 -> 앱 필름 키
    "Ektar100": "ektar100", "KGold200": "kgold200", "KPortra800": "kportra800",
    "Provia100F": "provia100f", "WarmAutumn": "warmautumn",
}
BLEND_HUES = [29, 55, 110, 142, 195, 264, 305, 328]  # 빨강 주황 노랑 초록 청록 파랑 보라 자홍 (Oklab 색상각)
BLEND_SIGMA = 22.0
HUE_MAX = 30.0     # 컬러 블렌더 색조 값 ±128 이 돌리는 각도
CHROMA_MAX = 0.6   # 채도 값 ±128 이 바꾸는 비율
LIGHT_MAX = 0.08   # 밝기 값 ±128 이 바꾸는 Oklab 밝기
GRADE_K = 0.15     # 컬러 그레이딩 채도 값(0x80 기준 차이/128)이 더하는 Oklab 색 크기


def parse(path):
    d = Path(path).read_bytes()
    assert d[:3] == b"NCP", path
    curve = np.array(struct.unpack(">257H", d[0x1CC:0x1CC + 514]), dtype=np.float64) / 32767.0
    blend = np.array(list(d[0x14C:0x14C + 24]), dtype=np.float64).reshape(8, 3) - 128.0
    g = d[0x170:0x170 + 12]
    grade = [(g[i * 4 + 1], g[i * 4 + 2] - 128.0) for i in range(3)]  # (색조 0..255, 채도 차이)
    return curve, blend, grade


def _hue_dist(h, c):
    return np.abs((h - c + 180.0) % 360.0 - 180.0)


def build(curve, blend, grade):
    n = X.N
    grid = np.stack(np.meshgrid(*[np.linspace(0, 1, n)] * 3, indexing="ij"), axis=-1).reshape(-1, 3)
    lab = X.to_oklab(grid)
    L, a, b = lab[:, 0].copy(), lab[:, 1].copy(), lab[:, 2].copy()
    chroma = np.hypot(a, b)
    hue = np.degrees(np.arctan2(b, a)) % 360.0

    # 1) 톤 커브: 같은 밝기의 회색이 커브를 지난 뒤의 밝기로 옮긴다(색은 유지)
    ramp = np.linspace(0, 1, 513)
    ramp_L = X.to_oklab(np.stack([ramp] * 3, axis=-1))[:, 0]
    g_in = np.interp(L, ramp_L, ramp)
    g_out = np.interp(g_in, np.linspace(0, 1, 257), curve)
    L_new = np.interp(g_out, ramp, ramp_L)
    # 밝기가 변한 만큼 채도도 같은 방향으로 조금 따라가게(대비가 오르면 색도 진해지는 필름 느낌)
    chroma_gain = 1.0 + 0.5 * (L_new / np.maximum(L, 1e-3) - 1.0)
    L = L_new
    a, b = a * chroma_gain, b * chroma_gain

    # 2) 컬러 블렌더: 색상각 주변(가우시안)에서 색조/채도/밝기 이동. 회색은 건드리지 않는다.
    gate = X._smoothstep(0.02, 0.08, chroma)
    dh = np.zeros_like(L); dc = np.zeros_like(L); dl = np.zeros_like(L)
    for c, (h_off, c_off, l_off) in zip(BLEND_HUES, blend):
        w = np.exp(-0.5 * (_hue_dist(hue, c) / BLEND_SIGMA) ** 2) * gate
        dh += w * h_off / 128.0 * HUE_MAX
        dc += w * c_off / 128.0 * CHROMA_MAX
        dl += w * l_off / 128.0 * LIGHT_MAX
    ch = np.hypot(a, b) * np.clip(1.0 + dc, 0.0, 2.0)
    ang = np.arctan2(b, a) + np.radians(dh)
    a, b, L = ch * np.cos(ang), ch * np.sin(ang), L + dl

    # 3) 컬러 그레이딩: 그림자/중간/하이라이트 구간에 색을 얹는다(구간 가중치의 합 = 1)
    w_sh = 1.0 - X._smoothstep(0.15, 0.55, L)
    w_hi = X._smoothstep(0.55, 0.95, L)
    for w, (h_byte, c_off) in zip((w_sh, 1.0 - w_sh - w_hi, w_hi), grade):
        r, g, bl = colorsys.hsv_to_rgb(h_byte / 255.0, 1.0, 1.0)
        tab = X.to_oklab(np.array([[r, g, bl]]))[0]
        v = np.array([tab[1], tab[2]]) / max(np.hypot(tab[1], tab[2]), 1e-6)
        k = c_off / 128.0 * GRADE_K
        a, b = a + w * k * v[0], b + w * k * v[1]

    out = X._fit_gamut(np.stack([np.clip(L, 0, 1), a, b], axis=-1))
    return np.clip(X.from_oklab(out), 0, 1).reshape(n, n, n, 3).astype(np.float32)


def main():
    src = Path(sys.argv[1])
    for name, key in RECIPES.items():
        lut = build(*parse(src / f"{name}.NP3"))
        X.write_cube(str(ASSETS / f"{key}.cube"), lut, title=key)
        mid = lut[16, 16, 16]
        print(f"{key}: 중간 회색 -> {np.round(mid, 3)}  (0.5 = 변화 없음)")


if __name__ == "__main__":
    main()
