"""Celestial sprites (celestials atlas): the moon dog halo (EF-LUN-01). Run from the mod root: python -I tools/pixelart/celestial.py"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402

OUT = pix.TEXTURES / "environment/celestial/moon_halo.png"


def halo(size=32, radius=0.8, width=0.12):
    """Pale ring, faded at its edges, drawn with the vanilla celestial pipeline around the moon."""
    img = pix.new(size, size)
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / c
            a = max(0.0, 1 - abs(d - radius) / width)
            if a > 0:
                img.putpixel((x, y), (214, 226, 255, round(150 * a)))
    return img


if __name__ == "__main__":
    pix.save(halo(), OUT)
    print(OUT)
