"""Mob skin painting helpers: Skin paints per UV face of a box-UV model (layouts from uvmaps.MODELS, which models3d.py fills
for the MITE models), with colours from palettes.py only. The skins themselves are in mobs3d.py; rules in docs/art/STYLE.md
section 7.
"""
import hashlib
import sys
from pathlib import Path



sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
from palettes import ramp  # noqa: E402
from uvmaps import MODELS, faces as unwrap  # noqa: E402

OUT = pix.TEXTURES / "entity"
SIDES = "FRLK"
OFF = {"T": 1, "F": 0, "R": 0, "L": -1, "K": -1, "B": -2}   # light from the top-left: top lit, back/left/bottom shaded
SKIP = False


def h01(*k):
    """Deterministic hash in [0, 1) (str hash() is salted per process)."""
    return int.from_bytes(hashlib.blake2b(repr(k).encode(), digest_size=3).digest(), "big") / 0x1000000


def C(r, i, a=255):
    return ramp(r)[max(0, min(5, i))][:3] + (a,)


class Skin:
    def __init__(self, model, name):
        (w, h), parts, _ = MODELS[model]
        self.model, self.name = model, name
        self.img = pix.new(w, h)
        self.px = self.img.load()
        self.parts = {p[0].split(" (")[0]: p[1:6] for p in parts}

    def faces(self, part):
        return unwrap(*self.parts[part])

    def size(self, part, f):
        return self.faces(part)[f][2:]

    def set(self, part, f, x, y, c):
        fx, fy, fw, fh = self.faces(part)[f]
        x, y = x + fw if x < 0 else x, y + fh if y < 0 else y
        if 0 <= x < fw and 0 <= y < fh:
            self.px[fx + x, fy + y] = (0, 0, 0, 0) if c is None else c

    def get(self, part, f, x, y):
        fx, fy, fw, fh = self.faces(part)[f]
        return self.px[fx + x % fw, fy + y % fh]

    def box(self, part, r, base=3, noise=0.16, cell=1, off=OFF, ao=True, alpha=255, only=None, rows=None, seed=0):
        """Shade every face of a box with ramp r: per-face offset, sparse +-1 noise, darker bottom rows on tall faces.
        rows=(y0, y1) limits the side faces to a band (clothes, armour), only= limits the faces."""
        for f, (fx, fy, fw, fh) in self.faces(part).items():
            if only is not None and f not in only:
                continue
            for y in range(fh):
                if rows and f in SIDES and not rows[0] <= y <= rows[1]:
                    continue
                for x in range(fw):
                    s = base + off.get(f, 0)
                    n = h01(self.name, seed, part, f, x // cell, y // cell)
                    s += -1 if n < noise else 1 if n > 1 - noise * 0.6 else 0
                    if ao and f in SIDES and fh >= 8 and y >= fh - max(1, fh // 6):
                        s -= 1
                    self.px[fx + x, fy + y] = C(r, s, alpha)

    def each(self, part, fn, only=SIDES):
        """fn(face, x, y, w, h) -> colour, None (clear) or SKIP, for every pixel of the given faces."""
        for f, (fx, fy, fw, fh) in self.faces(part).items():
            if f not in only:
                continue
            for y in range(fh):
                for x in range(fw):
                    c = fn(f, x, y, fw, fh)
                    if c is not SKIP:
                        self.px[fx + x, fy + y] = (0, 0, 0, 0) if c is None else c

    def grid(self, part, f, rows, cmap, x0=0, y0=0):
        """ASCII paint on one face: '.' keeps, '_' clears, other chars from cmap."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    self.set(part, f, x0 + x, y0 + y, None if ch == "_" else cmap[ch])

    def shade_of(self, part, f, x, y, r):
        """Index of the pixel's colour in ramp r (or None)."""
        c = self.get(part, f, x, y)
        rr = [q[:3] for q in ramp(r)]
        return rr.index(c[:3]) if c[:3] in rr else None

    def blotches(self, parts, r, shade, chance, seed=1, only=SIDES + "T"):
        """Scattered 1-2 px darker/lighter patches (rot, moss, soot) in ramp r at a fixed shade."""
        for part in parts:
            self.each(part, lambda f, x, y, w, h: C(r, shade + OFF.get(f, 0) // 2)
                      if h01(self.name, seed, part, f, x // 2, y // 2) < chance and h01(seed, x, y, f) < 0.7 else SKIP, only)

    def hem(self, part, r, top, bottom, base=3, depth=2, seed=3, only=SIDES):
        """Cloth band rows top..bottom whose lower edge hangs down 0..depth px more, column by column."""
        def fn(f, x, y, w, h):
            end = bottom + int(h01(self.name, seed, part, f, x) * (depth + 1))
            if top <= y <= end:
                s = base + OFF[f] - (1 if y >= end - 0 and y > bottom else 0)
                n = h01(self.name, seed, f, x, y)
                return C(r, s + (-1 if n < 0.15 else 1 if n > 0.92 else 0))
            return SKIP
        self.each(part, fn, only)

    def save(self):
        pix.save(self.img, OUT / f"{self.name}.png")
        return self
