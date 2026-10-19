"""Mod logo (the ghoul) and the other concepts (64x64 pixel art, saved x8 = 512x512): python -I tools/pixelart/logo.py [out_dir]
Colours from palettes.py ramps only; sprites reused from the mod's own textures.
"""
import math
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
from palettes import ramp  # noqa: E402
from skins import h01  # noqa: E402

N = 64


def c(r, i, a=255):
    return ramp(r)[max(0, min(5, i))][:3] + (a,)


def sprite(path, scale, mirror=False):
    im = Image.open(path).convert("RGBA")
    if mirror:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im.resize((im.width * scale, im.height * scale), Image.NEAREST)


def outlined(im, colour):
    """Sprite with a 1 px outline (pixel-art sticker look)."""
    out = Image.new("RGBA", (im.width + 2, im.height + 2))
    px, src = out.load(), im.load()
    for y in range(im.height):
        for x in range(im.width):
            if src[x, y][3]:
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    px[x + 1 + dx, y + 1 + dy] = colour
    out.alpha_composite(im, (1, 1))
    return out


def night(img):
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = c("shade", 1 + (y > 40) - (y < 16))
            if h01("star", x, y) < 0.012 and y < 44:
                px[x, y] = c("mist", 4 + (h01("tw", x, y) < 0.3))


def moon(img, cx, cy, r, ramp_name):
    px = img.load()
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d <= r:
                shade = 3 + (x - cx + y - cy < -r * 0.5) - (x - cx + y - cy > r * 0.6)
                if h01("crater", x // 3, y // 3) < 0.18:
                    shade -= 1
                px[x, y] = c(ramp_name, shade)
            elif d <= r + 1.5:
                px[x, y] = c(ramp_name, 1, 140)  # halo


def rounded_block(img):
    """Stone tile with a darker rim and rounded corners."""
    px = img.load()
    for y in range(N):
        for x in range(N):
            corner = min(x, N - 1 - x) + min(y, N - 1 - y)
            if corner < 3:
                continue
            edge = min(x, y, N - 1 - x, N - 1 - y)
            n = h01("stone", x // 2, y // 2)
            shade = 2 + (n > 0.6) - (n < 0.2)
            if edge < 3:
                shade = 1 if edge < 2 else 2
            px[x, y] = c("deepslate", shade)


def save(img, out, name):
    out.mkdir(parents=True, exist_ok=True)
    img.resize((512, 512), Image.NEAREST).save(out / f"logo_{name}.png")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else pix.ROOT / "build/art/logo"
    item = pix.TEXTURES / "item"
    dark = c("shade", 0)

    a = Image.new("RGBA", (N, N))  # A: flint hatchet before a blood moon
    night(a)
    moon(a, 38, 24, 19, "royal")
    a.alpha_composite(outlined(sprite(item / "flint_hatchet.png", 3), dark), (2, 13))
    save(a, out, "blood_moon")

    b = Image.new("RGBA", (N, N))  # B: crossed flint hatchet and mithril pickaxe on a stone tile
    rounded_block(b)
    b.alpha_composite(outlined(sprite(item / "mithril_pickaxe.png", 3, mirror=True), dark), (7, 7))
    b.alpha_composite(outlined(sprite(item / "flint_hatchet.png", 3), dark), (7, 7))
    save(b, out, "crossed_tools")

    g = Image.new("RGBA", (N, N))  # C: the ghoul's face over a blood moon
    night(g)
    moon(g, 32, 32, 27, "royal")
    face = Image.open(pix.TEXTURES / "entity/ghoul.png").convert("RGBA").crop((8, 8, 16, 16))
    face = face.resize((40, 40), Image.NEAREST)
    g.alpha_composite(outlined(face, dark), (11, 11))
    save(g, out, "ghoul")
    # chosen by the owner (2026-10-19): the ghoul is the mod logo (jar root for the mod list, README, platforms)
    for dst in (pix.ROOT / "src/main/resources/hardscrabble.png", pix.ROOT / "docs/media/logo.png"):
        g.resize((512, 512), Image.NEAREST).save(dst)
    print(out)


if __name__ == "__main__":
    main()
