"""
LUT 교차 시험: 앱에 들어 있는 필름 LUT 3종을 서로 다른 장면(local/samples 의 모든 *_before.*)에 적용해
한 장의 비교 이미지(local/luts/cross_check.jpg)로 만든다.
행 = 장면, 열 = 원본 | tungsten | gold | forest.
학습에 쓴 장면이 아니라 처음 보는 장면에서 색이 엉뚱하게 쏠리지 않는지 눈으로 확인하는 용도다.

사용법: python tools/cross_check.py
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
import extract_lut as E  # noqa: E402

FILMS = ["tungsten", "gold", "forest"]
ASSETS = ROOT / "RetroCamera" / "app" / "src" / "main" / "assets" / "luts"
OUT = ROOT / "local" / "luts" / "cross_check.jpg"
H = 380


def load_cube(p):
    rows = [l.split() for l in open(p) if l[0].isdigit() or l[0] == "-"]
    v = np.array(rows, dtype=np.float32).reshape(33, 33, 33, 3)
    return v.transpose(2, 1, 0, 3)  # .cube 순서 [b][g][r] -> [r, g, b]


def main():
    luts = {f: load_cube(ASSETS / f"{f}.cube") for f in FILMS}
    scenes = sorted((ROOT / "local" / "samples").glob("*/*_before.*"))
    if not scenes:
        sys.exit("local/samples 에 *_before.* 사진이 없습니다")
    rows = []
    for path in scenes:
        b, _, src, _ = E.load_pair(str(path), str(path))
        w, h = b.size
        tiles = [b.resize((int(w * H / h), H))]
        for f in FILMS:
            out = E.apply_lut(src, luts[f]).reshape(h, w, 3)
            tiles.append(Image.fromarray((out * 255).astype(np.uint8)).resize((int(w * H / h), H)))
        row = Image.new("RGB", (sum(t.width for t in tiles) + 10 * 3, H), (255, 255, 255))
        x = 0
        for t in tiles:
            row.paste(t, (x, 0)); x += t.width + 10
        rows.append(row)
        print(f"{path.parent.name}/{path.name.split('_before')[0]}")
    sheet = Image.new("RGB", (max(r.width for r in rows), H * len(rows) + 10 * (len(rows) - 1)), (255, 255, 255))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, i * (H + 10)))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT, quality=88)
    print(f"저장: {OUT}  (행: 위 목록 순서 / 열: 원본 | {' | '.join(FILMS)})")


if __name__ == "__main__":
    main()
