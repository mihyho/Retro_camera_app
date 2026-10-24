"""
앱 아이콘(레트로 컴팩트 카메라)을 만든다: 같은 도형 목록으로 어댑티브 아이콘용 벡터(XML)와 미리보기 PNG를 함께 낸다.
사용법:  python tools/make_icon.py [미리보기.png]
도형 좌표는 108x108 안이고, 어댑티브 아이콘 안전 영역(중앙 66)에 카메라가 들어간다.
"""
import sys
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "FilmCamera" / "app" / "src" / "main" / "res"
BG = "#1F1F23"
# (종류, 좌표, 색, 모서리 반지름) — rect: x0,y0,x1,y1 / circle: cx,cy,r
SHAPES = [
    ("rect", (22, 38, 86, 78), "#D8D8D4", 6),      # 은색 몸통
    ("rect", (22, 38, 86, 47), "#B9B9B5", 6),      # 윗면(어두운 은색)
    ("rect", (22, 43, 86, 47), "#B9B9B5", 0),
    ("rect", (22, 70, 86, 78), "#2A2A2E", 6),      # 아래 검은 띠(가죽)
    ("rect", (22, 70, 86, 73), "#2A2A2E", 0),
    ("rect", (66, 33, 76, 38), "#FF7A1A", 1.5),    # 셔터 버튼
    ("rect", (28, 42, 38, 48), "#F5E9B8", 1.5),    # 플래시 창
    ("rect", (42, 42, 50, 48), "#2A2A2E", 1.5),    # 뷰파인더 창
    ("rect", (70, 52, 80, 56), "#2A2A2E", 1),      # 작은 LCD/표시창
    ("circle", (50, 60, 15), "#9A9A96", 0),        # 렌즈 외곽
    ("circle", (50, 60, 12.5), "#17171A", 0),
    ("circle", (50, 60, 8.5), "#27395B", 0),
    ("circle", (50, 60, 4.2), "#101012", 0),
    ("circle", (46.5, 56.5, 2.4), "#B3FFFFFF", 0),  # 렌즈 반사
    ("circle", (31, 56, 1.8), "#E0402B", 0),       # 빨간 표시등
]


def rect_path(x0, y0, x1, y1, r):
    w, h = x1 - x0, y1 - y0
    if r <= 0:
        return f"M{x0},{y0}h{w}v{h}h-{w}z"
    return (f"M{x0 + r},{y0}h{w - 2 * r}a{r},{r} 0 0,1 {r},{r}v{h - 2 * r}a{r},{r} 0 0,1 -{r},{r}"
            f"h-{w - 2 * r}a{r},{r} 0 0,1 -{r},-{r}v-{h - 2 * r}a{r},{r} 0 0,1 {r},-{r}z")


def circle_path(cx, cy, r):
    return f"M{cx},{cy}m-{r},0a{r},{r} 0 1,1 {2 * r},0a{r},{r} 0 1,1 -{2 * r},0"


def argb(c):
    return "#" + c[1:] if len(c) == 7 else c


def vector_xml():
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           "<!-- 레트로 컴팩트 카메라 (tools/make_icon.py 로 생성). 어댑티브 아이콘 안전 영역(중앙 66dp) 안에 그렸다 -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp"', '    android:height="108dp"',
           '    android:viewportWidth="108"', '    android:viewportHeight="108">']
    for kind, g, color, r in SHAPES:
        d = rect_path(*g, r) if kind == "rect" else circle_path(*g)
        out += ["    <path", f'        android:fillColor="{argb(color)}"', f'        android:pathData="{d}" />']
    out.append("</vector>")
    return "\n".join(out) + "\n"


def preview(path):
    from PIL import Image, ImageDraw
    S = 8
    im = Image.new("RGBA", (108 * S, 108 * S), BG)
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for kind, g, color, r in SHAPES:
        c = color if len(color) == 7 else "#" + color[3:]
        alpha = 255 if len(color) == 7 else int(color[1:3], 16)
        layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        rgba = tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (alpha,)
        if kind == "rect":
            d.rounded_rectangle([g[0] * S, g[1] * S, g[2] * S, g[3] * S], radius=r * S, fill=rgba)
        else:
            d.ellipse([(g[0] - g[2]) * S, (g[1] - g[2]) * S, (g[0] + g[2]) * S, (g[1] + g[2]) * S], fill=rgba)
        im = Image.alpha_composite(im, layer)
    # 둥근 아이콘 모양 미리보기
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse([6 * S, 6 * S, 102 * S, 102 * S], fill=255)
    out = Image.new("RGBA", im.size, (40, 40, 40, 255))
    out.paste(im, mask=mask)
    out.convert("RGB").save(path)


if __name__ == "__main__":
    (RES / "drawable" / "ic_launcher_foreground.xml").write_text(vector_xml(), encoding="utf-8")
    (RES / "values" / "ic_launcher_background.xml").write_text(
        f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{BG}</color>\n</resources>\n', encoding="utf-8")
    if len(sys.argv) > 1:
        preview(sys.argv[1])
