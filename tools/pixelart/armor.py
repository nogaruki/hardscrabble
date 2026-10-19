"""Armour textures for Hardscrabble (original designs, see docs/art/STYLE.md sections 5 and 7).

Item icons (16x16) share one grid per piece across materials; chainmail = the same silhouette with a 1 px ring checker
(shades 1/3) inside the outline; rusted iron icons = iron blotched with shapes.rust(), like the tools.
Worn layers (entity/equipment/...) are built by Layer (see "worn layers"): roles per row/face on the box layouts from
uvmaps.MODELS, rounded-box normals for curvature and specular, soft dither, then per-material character (rust blooms,
patina, verdigris, filigree, facets). Chainmail = offset ring rows over a dark underlayer with leather trims.

    python -I tools/pixelart/armor.py sheet [OUT_PREFIX]   # icons + layers previews (default build/art/armor_*.png)
    python -I tools/pixelart/armor.py write [--dry]        # (re)draw every armour icon and layer of the inventory
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
import shapes  # noqa: E402
from palettes import MATERIAL_OF_PREFIX, ramp  # noqa: E402
from uvmaps import faces  # noqa: E402

# ---------------------------------------------------------------- item icons
HELMET = """
................
................
................
................
.....111110.....
....15544320....
...1544333320...
...1433333220...
...1544444420...
...142.10.120...
...140.00.120...
...000....000...
................
................
................
................
"""
CHESTPLATE = """
................
................
..1110....1110..
..15420..14320..
..143330033320..
..143334233320..
..143334233320..
..003334233200..
....14342320....
....14342320....
....01111110....
....14342320....
....14342320....
....02222220....
.....000000.....
................
"""
LEGGINGS = """
................
................
...1111111110...
...1544444420...
...1022222200...
...1433003320...
...1430..1320...
...1430..1320...
...1540..1540...
...140....130...
...140....130...
...140....130...
...140....130...
...000....000...
................
................
"""
BOOTS = """
................
................
................
................
................
...1110.........
...1540...1110..
...1430...1540..
.111430...1430..
.154320.111430..
.000000.154320..
........000000..
................
................
................
................
"""
# Horse barding seen from the side, head to the left; 'c'/'d' = leather girth strap (lower ramp).
HORSE_ARMOR = """
................
..1.............
.1510...........
15444110........
14333342........
1432.0342.......
00....14321110..
......143344320.
.....1cd333332..
.....1cd333320..
.....1cd222220..
.....140....140.
.....140....130.
.....00......00.
................
................
"""
ICONS = {"helmet": HELMET, "chestplate": CHESTPLATE, "leggings": LEGGINGS, "boots": BOOTS, "horse_armor": HORSE_ARMOR}


def chain_grid(grid):
    """Same silhouette, interior (shades 2-5) replaced by a ring checker of shades 3/1; the outline is kept."""
    out = []
    for y, row in enumerate(pix._rows(grid)):
        out.append("".join(("3" if (x + y) % 2 == 0 else "1") if ch in "2345" else ch for x, ch in enumerate(row)))
    return out


def icon(piece, material, chain=False):
    base = "iron" if material == "rusted_iron" else material
    grid = chain_grid(ICONS[piece]) if chain else ICONS[piece]
    img = pix.render(grid, digit=base, lower="leather")
    return shapes.rust(img) if material == "rusted_iron" else img


# ---------------------------------------------------------------- worn layers
# Every worn layer is built the same way: boxes of the model are covered with *roles* (base plate, lit lip, dark rim,
# cast shadow, rivet, engraved band, chain, leather trim...), each pixel keeps the normal of a rounded box so the light
# (top-left-front) gives curvature and specular streaks, then finish() turns roles + light into ramp shades with a soft
# dither and adds the material's character (rust, patina, filigree, facets). Only ramps and accents differ per material.
#
# Roles: b plate | h lit lip/rim | H specular dot | d dark rim (edge facing down) | s cast shadow | x dark interior
#        r rivet (lit pixel + shadow below-right) | e engraved band | c chain | l leather | m leather stitch
#        k/K buckle light/dark | '-' keep | '.' clear
import hashlib  # noqa: E402
import math  # noqa: E402

LIGHT = (-0.45, 0.65, 0.6)
_ln = math.sqrt(sum(v * v for v in LIGHT))
LIGHT = tuple(v / _ln for v in LIGHT)
_hv = (LIGHT[0], LIGHT[1] + 0.2, LIGHT[2] + 1.0)
_hn = math.sqrt(sum(v * v for v in _hv))
HALF = tuple(v / _hn for v in _hv)

# material: spec amplitude, spec power, dither amplitude, lip/highlight may reach shade 5, accent
MAT = {
    "copper":        dict(spec=1.3, pw=14, dith=0.75, bright=False, off=0.0),
    "gold":          dict(spec=1.9, pw=12, dith=0.6, bright=True, off=-0.15),
    "silver":        dict(spec=2.6, pw=18, dith=0.45, bright=True, off=-0.8),
    "iron":          dict(spec=1.2, pw=16, dith=0.8, bright=False, off=0.0),
    "rusted_iron":   dict(spec=0.9, pw=16, dith=0.9, bright=False, off=0.0),
    "ancient_metal": dict(spec=1.0, pw=10, dith=0.8, bright=False, off=0.2),
    "mithril":       dict(spec=1.6, pw=8, dith=0.45, bright=True, off=-0.35),
    "adamantium":    dict(spec=1.4, pw=20, dith=0.35, bright=False, off=0.15),
}


def h01(*k):
    """Deterministic hash in [0, 1)."""
    return int.from_bytes(hashlib.blake2b(repr(k).encode(), digest_size=3).digest(), "big") / 0x1000000


def _norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return tuple(c / n for c in v)


class Layer:
    """Role canvas over the box layout of one equipment texture."""

    def __init__(self, w, h, material, chain=False):
        self.w, self.h, self.mat, self.chain = w, h, material, chain
        self.cells = {}                                        # (x, y) -> dict(role, n, part, f, i, j, fw, fh)
        self.at = {}                                           # (part, f, i, j) -> (x, y)

    # -- construction
    def box(self, part, box, rows, faces_="TFRLK", tilt=None, top="b", bulge=1.0):
        """Cover the side faces (F R L K) with one role per row (rows: string, length h; '.' = not covered) and the
        top/bottom faces with role `top` (a char, or a function (i, j, fw, fh) -> role). tilt[j] leans row j up (+) or
        down (-); bulge scales the horizontal roundness."""
        u, v, w, h, d = box
        a, b = w / 2, d / 2
        for f, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            if f not in faces_:
                continue
            for j in range(fh):
                for i in range(fw):
                    if f in "TB":
                        x, z = -a + i + 0.5, -b + j + 0.5
                        n = (x / a * 0.75 * bulge, 1.0 if f == "T" else -1.0, z / b * 0.75 * bulge)
                        role = top(i, j, fw, fh) if callable(top) else top
                    else:
                        role = rows[j] if j < len(rows) else "."
                        t = i + 0.5
                        x, z = {"F": (-a + t, b), "R": (-a, -b + t), "L": (a, b - t), "K": (a - t, -b)}[f]
                        ny = tilt[j] if tilt else 0.0
                        n = (x / a * bulge if f in "FK" else x / a, ny, z / b * bulge if f in "RL" else z / b)
                    if role in ".-" or role is None:
                        continue
                    self.cells[x0 + i, y0 + j] = dict(role=role, n=_norm(n), part=part, f=f, i=i, j=j, fw=fw, fh=fh)
                    self.at[part, f, i, j] = (x0 + i, y0 + j)

    def grid(self, part, f, rows, x0=0, y0=0):
        """Overlay roles on one face (rows of chars, '-' keeps, '.' clears)."""
        for j, row in enumerate(pix._rows(rows) if isinstance(rows, str) else rows):
            for i, ch in enumerate(row):
                xy = self.at.get((part, f, x0 + i, y0 + j))
                if ch == "-" or xy is None or xy not in self.cells:
                    continue
                if ch == ".":
                    del self.cells[xy]
                else:
                    self.cells[xy]["role"] = ch

    def each(self, part, fn, faces_="FRLK"):
        """fn(cell) -> new role or None (keep), for every covered cell of a part."""
        for xy, c in list(self.cells.items()):
            if c["part"] == part and c["f"] in faces_:
                r = fn(c)
                if r == ".":
                    del self.cells[xy]
                elif r:
                    c["role"] = r

    def role(self, part, f, i, j):
        xy = self.at.get((part, f, i, j))
        return self.cells[xy]["role"] if xy in self.cells else None

    # -- finishing
    def finish(self):
        img = pix.new(self.w, self.h)
        px = img.load()
        mat = self.mat
        base = "iron" if mat == "rusted_iron" else mat
        m = MAT[mat]
        r, lea = ramp(base), ramp("rag" if mat == "copper" else "leather")
        rust, verd = ramp("rusted_iron"), ramp("verdigris")
        shade = {}
        for (x, y), c in self.cells.items():
            n, role = c["n"], c["role"]
            d = sum(p * q for p, q in zip(n, LIGHT))
            lam = (d + 0.8) / 1.8
            s = 1.9 + 1.5 * lam + m["off"]
            sp = max(0.0, sum(p * q for p, q in zip(n, HALF))) ** m["pw"]
            if role in "bhe":
                s += m["spec"] * sp
            s += {"h": 1.2, "s": -1.0, "d": -2.0, "e": 0.0}.get(role, 0.0)
            shade[x, y] = (s, sp)
        for (x, y), c in self.cells.items():
            role = c["role"]
            s, sp = shade[x, y]
            dz = (0.7 * h01(mat, "c", x // 2, y) + 0.3 * h01(mat, "p", x, y) - 0.5) * m["dith"] * 0.5
            if role in "lmkKg":                                         # leather trim, stitches, buckle, underlayer
                if role == "g":
                    px[x, y] = lea[0] if s < 3.0 else lea[1]
                elif role in "kK":
                    px[x, y] = r[5 if role == "k" else 1]
                else:
                    li = int(math.floor(s - 0.6 + 0.5 + dz * 0.6)) + (2 if role == "m" else 0)
                    px[x, y] = lea[max(0, min(5, li))]
                continue
            if role == "c":
                self._ring(px, x, y, s, r, lea)
                continue
            if role == "x":
                px[x, y] = r[0]
                continue
            if role == "H":
                px[x, y] = r[5]
                continue
            if role == "r":
                px[x, y] = r[5]
                c2 = self.at.get((c["part"], c["f"], c["i"] + 1, c["j"] + 1))
                if c2 in self.cells and self.cells[c2]["role"] in "bhe":
                    self.cells[c2]["role"] = "s"
                    shade[c2] = (shade[c2][0] - 1.4, 0)
                continue
            idx = int(math.floor(s + 0.5 + dz))
            hi = 5 if (role == "h" and (m["bright"] or s > 4.4)) or (role == "b" and sp > 0.55 and m["bright"]) else 4
            lo = 0 if role in "ds" else 1
            px[x, y] = r[max(lo, min(hi, idx))]
        self._character(px, r, rust, verd)
        return img

    def _ring(self, px, x, y, s, r, lea):
        """Mail: rows of rings offset by one pixel every ring row. A ring = lit top pixel over a shadow pixel, its side
        pixel next to them; the gap shows the dark padded underlayer (sometimes the skin)."""
        k = y // 2
        top = (x + k) % 2 == 0
        if y % 2 == 0:
            o = 1.0 if top else -0.25
        else:
            if not top:
                px[x, y] = (0, 0, 0, 0) if h01(self.mat, "gap", x, y) < 0.12 else r[0 if s < 2.6 else 1]
                return
            o = -0.65
        idx = int(math.floor(s + o + 0.3 + (h01(self.mat, "ring", x, y) - 0.5) * 0.5))
        px[x, y] = r[max(1, min(5 if MAT[self.mat]["bright"] else 4, idx))]

    def _character(self, px, r, rust, verd):
        mat = self.mat
        idx_of = {c[:3]: i for i, c in enumerate(r)}
        plate_cells = [(xy, c) for xy, c in self.cells.items() if c["role"] in "bhes"]

        def near(xy, roles, rad=1):
            x, y = xy
            return any(self.cells.get((x + dx, y + dy), {}).get("role", "?") in roles
                       for dx in range(-rad, rad + 1) for dy in range(-rad, rad + 1) if (dx, dy) != (0, 0))

        def bump(xy, k, rr=None):
            i = idx_of.get(px[xy][:3])
            if i is not None:
                px[xy] = (rr or r)[max(0, min(5, i + k))]

        # scratches: short diagonal lit nicks (2-3 px), and a dark one now and then
        for xy, c in plate_cells:
            if c["role"] == "b" and h01(mat, "scr", xy) < (0.018 if mat in ("silver", "mithril") else 0.03):
                k = 1 if h01(mat, "sk", xy) < 0.75 else -1
                for t in range(2 + (h01(mat, "sl", xy) < 0.4)):
                    q = (xy[0] + t, xy[1] + t)
                    if q in self.cells and self.cells[q]["role"] == "b":
                        bump(q, k)
        # engraved bands ('e'): a running meander, dark groove with a lit lower lip
        for xy, c in self.cells.items():
            if c["role"] != "e":
                continue
            x, y = xy
            upper = self.cells.get((x, y - 1), {}).get("role") == "e"
            groove = (x // 2) % 2 == (1 if upper else 0)
            if mat == "ancient_metal":
                if groove and h01(mat, "worn", x // 3, y) > 0.25:
                    bump(xy, -2)
                elif not groove:
                    bump(xy, 1)
            elif mat == "mithril":
                if groove:
                    px[xy] = r[5]
                else:
                    bump(xy, -1)
            elif mat == "gold":
                if groove:
                    bump(xy, 1)
            else:
                if groove:
                    bump(xy, -1)
        if mat == "rusted_iron":                       # rust blooms along rims, seams, rivets and bottoms, pitted
            for xy, c in self.cells.items():
                if c["role"] in "lmgkKx":
                    continue
                x, y = xy
                blob = (h01("rb", x // 3, y // 3) + h01("rb2", (x + 1) // 2, (y + 1) // 2)) / 2
                v = blob + (0.1 if near(xy, "dsrx") else 0) + (0.1 if c["j"] >= c["fh"] - 2 else 0)
                i = idx_of.get(px[xy][:3])
                if i is None:
                    continue
                if v > 0.78:
                    px[xy] = rust[max(1, min(4, [1, 1, 2, 3, 3, 4][i] - (1 if v > 0.9 else 0)))]
                elif v > 0.71 and h01("edge", x, y) < 0.5:
                    px[xy] = rust[min(5, max(2, i))]
                elif c["role"] == "b" and h01("pit", x, y) < 0.07:
                    bump(xy, -1)
        if mat == "copper":                            # darker patina specks and a few deep tarnish pits
            for xy, c in plate_cells:
                hh = h01("pat", xy)
                if c["role"] == "b" and hh < 0.09:
                    bump(xy, -1)
                elif hh > 0.93 and c["role"] == "b":
                    bump(xy, -2)                       # deeper tarnish pit
        if mat == "ancient_metal":                     # verdigris spots, heavier in recesses
            for xy, c in self.cells.items():
                if c["role"] not in "bhsdc":
                    continue
                x, y = xy
                i = idx_of.get(px[xy][:3])
                if i is None:
                    continue
                p = 0.06 if near(xy, "dsx") else 0.015
                if h01("vg", x // 2, y // 2) < p * 2.2 and h01("vg2", x, y) < 0.55:
                    px[xy] = verd[max(1, min(4, i))]
                elif c["role"] == "b" and h01("vpit", x, y) < 0.06:
                    bump(xy, -1)
        if mat == "mithril":                           # filigree scrolls on the wide plates, soft glow beside them
            for xy, c in plate_cells:
                if c["role"] != "b" or c["fw"] < 6 or c["f"] not in "FKT":
                    continue
                i, j = c["i"], c["j"]
                if MITHRIL_SCROLL[j % 6][i % 6] == "#":
                    px[xy] = r[5] if idx_of.get(px[xy][:3], 3) >= 3 else r[4]
                elif MITHRIL_SCROLL[j % 6][i % 6] == "o":
                    bump(xy, -1)
        if mat == "adamantium":                        # crisp facets: lit left half, darker right half, bright edges
            for xy, c in plate_cells:
                i, fw, f = c["i"], c["fw"], c["f"]
                if f in "FK" and fw >= 6:
                    if i == fw // 2 - 1 and c["role"] == "b":
                        bump(xy, 1)
                    elif i >= fw // 2 + 1 and c["role"] == "b" and c["j"] % 4 < 2 and h01("fac", xy) < 0.5:
                        bump(xy, -1)
                if f in "FR" and i == 0 and c["role"] in "bh":
                    px[xy] = r[5] if c["role"] == "h" or h01("ed", xy) < 0.5 else r[4]
        if mat == "gold":                              # warm sparkle on the lit side
            for xy, c in plate_cells:
                if c["role"] == "b" and idx_of.get(px[xy][:3]) == 4 and h01("gs", xy) < 0.12:
                    px[xy] = r[5]
        if mat == "silver":                            # polished: dark reflection band under the specular streaks
            for xy, c in plate_cells:
                if c["role"] == "b" and c["f"] in "FRK" and c["j"] == c["fh"] // 2 and h01("sr", xy) < 0.5:
                    bump(xy, -1)


MITHRIL_SCROLL = pix._rows("""
.#....
#.#..o
...#..
..o.#.
.....#
o.....
""")

HEAD, BODY, ARM, LEG = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)


def humanoid(material, chain=False):
    """64x32 helmet + chestplate + boots layer."""
    L = Layer(64, 32, material, chain)
    ridge = lambda i, j, fw, fh: "h" if i == 3 else "s" if i == 4 else "b"
    if chain:
        # coif: mail all round, leather brow band, face opening framed in leather
        L.box("head", HEAD, "cccccccc", "TFRLK", tilt=[.7, .45, .25, .1, 0, -.05, -.1, -.2], top="c")
        L.box("head", HEAD, "--l----m", "FRLK")
        L.grid("head", "F", ["--------", "--------", "-mllllm-", "-l....l-", "-l....l-", "-l....l-", "-l....l-",
                             "-m....m-"])
        # hauberk: leather collar, mail, belt, mail skirt, hem
        L.box("body", BODY, "lcccccccmccl", "TFRLK", tilt=[.35, .2, .1, 0, -.05, -.1, -.15, -.2, -.2, -.25, -.3, -.3],
              top=lambda i, j, fw, fh: "l" if j == 3 else "c")
        L.grid("body", "F", ["--------"] * 8 + ["---kK---"])
        L.box("arm", ARM, "cccccccccclm", "TFRLK", tilt=[.6, .35, .2, .1, 0, 0, 0, -.05, -.1, -.1, -.15, -.2], top="c")
        # mail chausses: leather cuff, mail, leather sole
        L.box("leg", LEG, "-------mcccl", "FRLK")
        L.box("leg", LEG, "", "B", top="l")
        return L.finish()
    # helmet: dome with a crest ridge, brow band with rivets, cheek guards, nasal, eye slits, rolled lower rim
    L.box("head", HEAD, "bbbhbsbd", "TFRLK", tilt=[.75, .5, .25, .15, 0, -.05, -.1, -.2], top=ridge)
    L.grid("head", "K", ["---hs---", "---hs---", "---hs---", "---hs---", "-r-hs-r-"])
    L.grid("head", "R", ["--------", "--------", "--------", "--------", "-r----r-", "--------", "--ee----"])
    L.grid("head", "L", ["--------", "--------", "--------", "--------", "-r----r-", "--------", "----ee--"])
    L.grid("head", "F", ["---hs---",
                         "---hs---",
                         "---hs---",
                         "---hs---",
                         "-rxhsxr-",
                         "-sxdsxs-",
                         "-b....b-",
                         "dd....dd"])
    # breastplate: gorget rim, rounded breast with a keel, lower edge, belt, fauld lames; arms: two-lame pauldron,
    # cast shadow on the rerebrace, elbow line, vambrace, cuff
    L.box("body", BODY, "hbbbbbbdlhbd", "TFRLK", tilt=[.45, .35, .2, .05, -.05, -.15, -.25, -.3, -.2, .1, 0, -.2],
          top=lambda i, j, fw, fh: "h" if j == fh - 1 else "s" if j == 0 else "b")
    L.grid("body", "F", ["-h-hs-h-",
                         "-r-hs-r-",
                         "---hs---",
                         "-e-hs-e-",
                         "---hs---",
                         "s--hs--s",
                         "-s-hs-s-",
                         "--------",
                         "---kK---",
                         "-r----r-"])
    L.grid("body", "K", ["--------", "-r----r-", "--------", "---ee---", "---ee---", "--------", "--------",
                         "--------", "--------", "--------"])
    L.box("arm", ARM, "hbdhdsbbdhbd", "TFRLK", tilt=[.8, .55, .3, .55, .25, -.1, 0, 0, -.1, .1, 0, -.2], top="b",
          bulge=1.2)
    L.grid("arm", "R", ["----", "-r--", "----", "-ee-", "----", "----", "----", "--r-"])
    L.grid("arm", "F", ["----", "-r--", "----", "----", "----", "----", "-ee-", "----"])
    # boots (sabatons): lit top roll, lames, leather sole
    L.box("leg", LEG, "-------hbdhl", "FRLK", tilt=[0] * 7 + [.3, .1, -.1, .1, -.3])
    L.grid("leg", "F", ["----"] * 8 + ["-r--"])
    L.grid("leg", "R", ["----"] * 8 + ["--r-"])
    L.box("leg", LEG, "", "B", top="l")
    return L.finish()


def humanoid_leggings(material, chain=False):
    """64x32 leggings layer: waist (lower body rows) + legs."""
    L = Layer(64, 32, material, chain)
    if chain:
        L.box("body", BODY, "-------lmccc", "FRLK", tilt=[0] * 7 + [0, -.1, -.15, -.2, -.25])
        L.grid("body", "F", ["--------"] * 7 + ["---kK---"])
        L.box("leg", LEG, "cccccccccccl", "TFRLK", tilt=[-.1, -.1, 0, 0, .05, .2, 0, -.15, 0, 0, -.05, -.1], top="c")
        L.grid("leg", "F", ["----"] * 5 + ["-lm-", "-ll-"])        # leather knee pad over the mail
        return L.finish()
    # waist: belt with buckle, fauld lames; legs: tasset, cuisse, knee cop with highlight, greave
    L.box("body", BODY, "-------lhbdh", "FRLK", tilt=[0] * 7 + [0, .2, 0, -.2, .1])
    L.grid("body", "F", ["--------"] * 7 + ["---kK---", "-r----r-"])
    L.grid("body", "K", ["--------"] * 9 + ["--e--e--"])
    L.box("leg", LEG, "bdshbbhbdsbd", "TFRLK", tilt=[0, -.2, 0, .2, 0, -.1, .45, .1, -.3, 0, 0, -.2], top="b")
    L.grid("leg", "F", ["----", "----", "----", "-r--", "----", "----", "-hH-", "-bs-", "----", "----", "-ee-"])
    L.grid("leg", "R", ["----", "----", "----", "--r-", "----", "----", "-h--", "----", "----", "----", "----"])
    L.grid("leg", "K", ["----"] * 4 + ["-ee-"])
    return L.finish()


def horse_body(material, chain=False):
    """64x64 horse barding: crinet (neck), chanfron (head and nose), peytral, flanchards and crupper on the body;
    legs, tail, mane and ears stay bare."""
    L = Layer(64, 64, material, chain)
    # neck: overlapping crinet lames running down the neck
    L.box("neck", (0, 35, 4, 12, 7), "hbdhbdhbdhbl", "TFRLK", tilt=[.3, .1, -.2] * 4,
          top=lambda i, j, fw, fh: "h" if j == 0 else "b")
    L.grid("neck", "R", ["-------", "--r--r-", "-------", "-------", "--r--r-", "-------", "-------", "--r--r-"])
    L.grid("neck", "L", ["-------", "-r--r--", "-------", "-------", "-r--r--", "-------", "-------", "-r--r--"])
    # head: chanfron plate with a central ridge, rivets, eye guards (dark rims) on the sides
    L.box("head", (0, 13, 6, 5, 7), "hbbbd", "TFRLK", tilt=[.4, .1, 0, -.1, -.3],
          top=lambda i, j, fw, fh: "h" if i == 2 else "s" if i == 3 else "b")
    L.grid("head", "F", ["--hs--", "r-hs-r", "--hs--", "--hs--", "------"])
    L.grid("head", "R", ["-------", "-r--dd-", "----sx-", "-------", "-------"])
    L.grid("head", "L", ["-------", "-dd--r-", "-xs----", "-------", "-------"])
    L.box("muzzle", (0, 25, 4, 5, 5), "hbd..", "TFRL", tilt=[.3, 0, -.2, 0, 0],
          top=lambda i, j, fw, fh: "h" if i == 1 else "s" if i == 2 else "b")
    # body: back plates (lames across the back), peytral and crupper with a keel, flanchards with panels
    L.box("body", (0, 32, 10, 10, 22), "hbbebbbbdl", "TFRLK", tilt=[.5, .35, .2, .1, 0, -.1, -.2, -.3, -.4, -.4],
          top=lambda i, j, fw, fh: "d" if j % 6 == 5 else "h" if j % 6 == 0 else "s" if i == fw - 1 else "b")
    for f in "FK":
        L.grid("body", f, ["----hs----", "-r--hs--r-", "----hs----", "----hs----", "----hs----", "----hs----",
                           "-r--hs--r-", "----------", "----------", "-m--m--m--"])
    for f in "RL":
        L.each("body", lambda c: ("h" if c["i"] % 7 == 0 else "s" if c["i"] % 7 == 6 else None)
               if 1 <= c["j"] <= 7 and c["role"] == "b" else None, f)
        L.each("body", lambda c: "r" if c["j"] == 1 and c["i"] % 7 == 3 else "m" if c["j"] == 9 and c["i"] % 3 == 1
               else None, f)
    return L.finish()


# ---------------------------------------------------------------- targets / CLI
PIECES = ("helmet", "chestplate", "leggings", "boots", "horse_armor")


def _material(prefix_name):
    for p in sorted(MATERIAL_OF_PREFIX, key=len, reverse=True):
        if prefix_name.startswith(p + "_") or prefix_name == p:
            return MATERIAL_OF_PREFIX[p], prefix_name[len(p) + 1:]
    return None, None


def targets():
    """[(path, image factory)] for every MITE armour icon and equipment layer in the texture tree."""
    out = []
    for p in sorted((pix.TEXTURES / "item").glob("*.png")):
        for piece in PIECES:
            if p.stem.endswith("_" + piece):
                mat, rest = _material(p.stem[: -len(piece) - 1])
                if mat:
                    chain = rest == "chainmail"
                    out.append((p, lambda piece=piece, mat=mat, chain=chain: icon(piece, mat, chain)))
    layers = {"humanoid": humanoid, "humanoid_leggings": humanoid_leggings, "horse_body": horse_body}
    for layer, fn in layers.items():
        for p in sorted((pix.TEXTURES / "entity/equipment" / layer).glob("*.png")):
            chain = p.stem.endswith("_chainmail")
            mat, _ = _material(p.stem[: -len("_chainmail")] if chain else p.stem)
            out.append((p, lambda fn=fn, mat=mat, chain=chain: fn(mat, chain)))
    return out


MATERIALS = ("copper", "gold", "silver", "rusted_iron", "ancient_metal", "mithril", "adamantium")


def main(argv):
    if argv[:1] == ["write"]:
        for p, make in targets():
            print(p.relative_to(pix.ROOT))
            if "--dry" not in argv:
                pix.save(make(), p)
        return 0
    prefix = argv[1] if len(argv) > 1 else str(pix.ROOT / "build/art/armor")
    icons = [(f"{m}{'_chain' if c else ''}_{k}", icon(k, m, c)) for m in MATERIALS for c in (False, True) for k in PIECES]
    print(pix.contact_sheet(icons, prefix + "_icons.png", scale=6, cols=10))
    lay = [(f"{m}{'_chain' if c else ''}", humanoid(m, c)) for m in MATERIALS for c in (False, True)]
    print(pix.contact_sheet(lay, prefix + "_humanoid.png", scale=6, cols=4))
    leg = [(f"{m}{'_chain' if c else ''}", humanoid_leggings(m, c)) for m in MATERIALS for c in (False, True)]
    print(pix.contact_sheet(leg, prefix + "_leggings.png", scale=6, cols=4))
    print(pix.contact_sheet([(m, horse_body(m)) for m in ("silver", "rusted_iron")], prefix + "_horse.png", scale=6))
    dolls = [(f"{m}{'_chain' if c else ''}", doll(humanoid(m, c), humanoid_leggings(m, c))) for m in MATERIALS
             for c in (False, True)]
    print(pix.contact_sheet(dolls, prefix + "_worn.png", scale=8, cols=7))
    return 0


def doll(layer, leggings):
    """Flat front + back view of a player wearing the full set (leggings under the main layer), for review only."""
    out = pix.new(36, 32)
    skin = pix.new(16, 32)
    for x0, y0, w, h in ((4, 0, 8, 8), (4, 8, 8, 12), (0, 8, 4, 12), (12, 8, 4, 12), (4, 20, 8, 12)):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                skin.putpixel((x, y), (96, 86, 78, 255))
    # (front face, back face) of head, body, arm, leg in the 64x32 layout and where they go on the 16x32 doll
    parts = [((8, 8), (24, 8), 8, 8, [(4, 0, False)]), ((20, 20), (32, 20), 8, 12, [(4, 8, False)]),
             ((44, 20), (52, 20), 4, 12, [(0, 8, False), (12, 8, True)]),
             ((4, 20), (12, 20), 4, 12, [(4, 20, False), (8, 20, True)])]
    for side, ox in ((0, 0), (1, 20)):
        view = skin.copy()
        for tex in (leggings, layer):
            for faces_uv, _, w, h, spots in [(p[side], 0, p[2], p[3], p[4]) for p in parts]:
                face = tex.crop((faces_uv[0], faces_uv[1], faces_uv[0] + w, faces_uv[1] + h))
                for x, y, mirror in spots:
                    view.alpha_composite(face.transpose(0) if mirror else face, (x, y))
        out.alpha_composite(view, (ox, 0))
    return out


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or ["sheet"]))
