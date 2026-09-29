"""Regenerates the app's logo PNGs from design/logo-source.png. Run from the repo root: python3 design/make_icons.py"""
from pathlib import Path

from PIL import Image

RES = Path("android/app/src/main/res")

src = Image.open("design/logo-source.png").convert("RGBA")
w, h = src.size
px = src.load()
# The source sits on white; flood-fill that white from the corners into transparency.
stack, seen = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)], set()
while stack:
    x, y = stack.pop()
    if (x, y) in seen or not (0 <= x < w and 0 <= y < h):
        continue
    r, g, b, a = px[x, y]
    if min(r, g, b) < 200:
        continue
    seen.add((x, y))
    px[x, y] = (255, 255, 255, 0)
    stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
logo = src.crop(src.getbbox())
(RES / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
logo.save(RES / "drawable-nodpi" / "logo.png")

# Adaptive icon foreground: a 108dp canvas at xxxhdpi (432px). The logo's own dark square blends into the
# #1A1D25 background layer, and the glyph stays inside the 66dp safe zone.
canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
side = 300
scaled = logo.resize((side, round(side * logo.height / logo.width)), Image.LANCZOS)
canvas.paste(scaled, ((432 - scaled.width) // 2, (432 - scaled.height) // 2), scaled)
(RES / "drawable-xxxhdpi").mkdir(parents=True, exist_ok=True)
canvas.save(RES / "drawable-xxxhdpi" / "ic_launcher_foreground.png")
