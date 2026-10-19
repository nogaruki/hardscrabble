"""Batch B textures (materials, food and dishes, spawn eggs, misc items). Rules: docs/art/STYLE.md.

    python -I tools/pixelart/batch_b.py sheet [OUT_DIR]   # review sheets per group (default build/art)
    python -I tools/pixelart/batch_b.py write             # (re)draw every batch B texture under textures/item

Shared silhouettes per item type, the material/content only changes the ramp. Extra ramps used only here (foods, mob
colours) are registered into palettes.RAMPS by `_ramp()` below: a 6-shade hue-shifted ramp built from one base colour.
"""
import colorsys
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import palettes  # noqa: E402
import pix  # noqa: E402
from palettes import RAMPS, ramp  # noqa: E402

ITEM = pix.TEXTURES / "item"


# ---------------------------------------------------------------- extra ramps
def _ramp(base, dark=0.30, light=0.20):
    """6 shades around `base` (shade 3): lightness steps, darks rotate toward blue, lights toward yellow."""
    r, g, b = (int(base[i:i + 2], 16) / 255 for i in (1, 3, 5))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    lo = min(dark, l - 0.07)
    hi = min(light, 0.95 - l)
    out = []
    for k, t in enumerate((-1.0, -0.62, -0.30, 0.0, 0.5, 1.0)):
        L = l + (t * lo if t < 0 else t * hi)
        target = 0.66 if t < 0 else 0.15
        d = ((target - h + 0.5) % 1.0) - 0.5
        hh = (h + max(-1, min(1, abs(t))) * (0.035 if abs(d) > 0.035 else abs(d)) * (1 if d > 0 else -1)) % 1.0
        S = max(0.0, min(1.0, s * (1.08 if t < 0 else 1 - 0.25 * t)))
        rr, gg, bb = colorsys.hls_to_rgb(hh, max(0.05, min(0.97, L)), S)
        out.append("#%02x%02x%02x" % (round(rr * 255), round(gg * 255), round(bb * 255)))
    return out


EXTRA_RAMPS = {
    # foods
    "onion": "#c9944a", "onion_green": "#6f9a3a", "orange": "#ec8a1c", "leaf": "#4f8f2c", "banana": "#ecc93a",
    "flour": "#e9e3d6", "dough": "#e4cf9e", "cheese": "#efc23d", "chocolate": "#6b3f25", "worm": "#c98378",
    "cooked_worm": "#93593a", "manure": "#6a4a2a", "sinew": "#d9b9a0", "broth": "#9a5a2e", "meat": "#7a3424",
    "carrot": "#e5761e", "chicken": "#e6c26a", "cream": "#d9c7a6", "mushroom": "#8d6447", "veg_cream": "#b9cf86",
    "veg_broth": "#7f8a3a", "pumpkin": "#e48a26", "potato": "#f0dfa0", "butter": "#f6dc52", "oats": "#cdb183",
    "flake": "#e4b24a", "salad": "#5ea83a", "tomato": "#d23a2a", "sorbet": "#f19a3e", "strawberry": "#e7849a",
    "vanilla": "#f3e6c4",
    # slimes / bottles
    "jelly": "#e6cf3a", "blob": "#c93a32", "ooze": "#3f5a2a", "pudding": "#5a3a22", "magic": "#b64ad6",
    "void": "#3a3a5a",
    # mob colours (spawn eggs)
    "bone": "#d8cfb4", "rot": "#6f8a4a", "pale": "#b8c4cc", "clay": "#9aa3b4", "dirt": "#7a5a3a",
    "shadow": "#2c2a3a", "spirit": "#8e7bc4", "blood": "#9a1f24", "bat": "#4a3428", "night": "#2a2f52",
    "netherrack": "#8a3030", "wolf": "#7a7a80", "brown": "#7a5236", "black": "#2a2a30", "red": "#c23a2e",
    "stalker": "#c8d6dc",
}
for _k, _v in EXTRA_RAMPS.items():
    RAMPS.setdefault(_k, _ramp(_v))
palettes._check()


def ext(**chars):
    """extra={char: (ramp, shade)} -> {char: rgba}"""
    return {c: ramp(r)[i] for c, (r, i) in chars.items()}


def grid(s):
    return [list(r) for r in pix._rows(s)]


def text(g):
    return ["".join(r) for r in g]


def auto(s, mark="#", lit_edge=None):
    """pix.autoshade; lit_edge replaces the shade-1 outline (raw ores have no outline on the lit side)."""
    rows = pix.autoshade(s, mark)
    return [r.replace("1", lit_edge) for r in rows] if lit_edge else rows


BLANK = ["." * 16] * 16


def mask(fn):
    return "\n".join("".join("#" if fn(x, y) else "." for x in range(16)) for y in range(16))


def paint(base, over):
    """Overlay grid `over` onto `base` (both rows of text), '.' in over keeps base."""
    b = grid(base) if isinstance(base, str) else [list(r) for r in base]
    for y, row in enumerate(pix._rows(over)):
        for x, ch in enumerate(row):
            if ch != ".":
                b[y][x] = ch
    return text(b)


# ---------------------------------------------------------------- materials
# Ingot, coin, door, nugget, chain link and flint are hand-drawn grids with one shade per face (full 0-5 ramp, glints in
# 5); crystal and glass shards are flat facets via `faces()`; the raw-ore rock is lit 3D bulges via `pix.relief()` with
# crystals and pits painted over. One construction per item type for every material: only the ramp and a small
# identity accent change.
def _ell(cx, cy, rx, ry):
    return mask(lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0)


def setpx(rows, pts, ch, only=None):
    g = [list(r) for r in rows]
    for x, y in pts:
        if g[y][x] != "." and (only is None or g[y][x] in only):
            g[y][x] = ch
    return text(g)


# 3/4 view bar: lit top face with a glint on its front-left bevel, mid front face with a stamped mark, dark end face.
INGOT = """
................
................
................
....11111111110.
...1555444444310
..15444444443210
.154444444432110
.155544444432110
.133333333322110
.13332223332110.
.1333244333210..
.122222222220...
.00000000000....
................
................
................
"""

# Disc with a raised rim (lit top-left, inner wall dark at the top / lit at the bottom), an embossed diamond with its
# own facets, and the coin's thickness as a dark band at the bottom.
COIN = """
................
................
.....11111......
...115544410....
...152222230....
..14233333420...
..14235433420...
..14234312420...
..14233112420...
..14233223420...
...134444420....
...022222220....
...01111110.....
.....00000......
................
................
"""

# Panel door: frame (lit left/top, dark right/bottom), riveted corners and rail, recessed window with a glint and a
# mullion, recessed lower panel (shadow on its top/left walls, light on bottom/right), knob with its shadow.
DOOR = """
...1111111110...
...1544444420...
...1400000020...
...1401141320...
...1401411320...
...1403333320...
...1533333520...
...1422222220...
...1400000420...
...1404443420...
...1404335420...
...1404331420...
...1403222420...
...1404444420...
...1522222220...
...0000000000...
"""


NUGGET = """
................
................
................
................
................
........110.....
.......15420....
....11103210....
...15432000.....
...143220.110...
...13221015420..
....0000143320..
........132210..
.........0000...
................
................
"""


def raw_ore():
    bulges = [pix.ellipsoid(7.6, 9.2, 6.2, 4.9, z=0, depth=0.6), pix.ellipsoid(5.0, 5.2, 3.6, 2.9, z=0.5, depth=0.7),
              pix.ellipsoid(11.0, 5.8, 3.2, 2.7, z=0.3, depth=0.7)]
    g = pix.relief(bulges, cuts=(0.2, 0.5, 0.75), glints=0, shadow="1")
    rock = {"1": "1", "2": "1", "3": "2", "4": "3", "5": "3", "0": "0"}   # host rock: lower half of the ramp
    g = ["".join(rock[c] if c != "." else c for c in r) for r in g]
    return paint(g, RAW_VEINS)


# Metal crystals (lit facet 5/4, shaded facet 2) catching the light, and pits (dark with a lit lower lip) in the rock.
RAW_VEINS = """
................
................
................
................
.....5..........
....453......5..
.....2......452.
..1..........2..
...3...5........
......4542...1..
.......42.....3.
...5..........1.
..452...1.......
...2.....3......
................
................
"""


# Face-on oval link, shaded as a torus: lit band along the outer top-left, dark inner wall at the top-left of the eye,
# lit inner wall at its bottom-right.
LINK = pix._rows("""
...1110.
..154430
.144..30
143...40
14...420
12..430.
023430..
.0000...
""")


def chain():
    """Two face-on links joined by an edge-on link (a rod) that lies over the lower ring and slides under the upper
    one, its end showing in the upper ring's eye."""
    g = [["."] * 16 for _ in range(16)]

    def put(rows, ox, oy):
        for y, r in enumerate(rows):
            for x, c in enumerate(r):
                if c != "." and 0 <= x + ox < 16 and 0 <= y + oy < 16:
                    g[y + oy][x + ox] = c
    put(LINK, 0, 8)
    for i in range(8):
        x, y = 4 + i, 11 - i
        put(["1" + ("5" if i == 3 else "4") + "0"], x - 1, y)
    put(LINK, 8, 0)
    return text(g)


# ---- shards
def crystal_shard():
    main = [((11, 1), (5, 8), (8, 12), "4"), ((11, 1), (8, 12), (13, 9), "2"), ((11, 1), (13, 9), (14, 6), "1"),
            ((11, 1), (7, 5), (9, 7), "5")]
    small = [((3, 9), (1, 14), (4, 14), "4"), ((3, 9), (4, 14), (6, 13), "2")]
    g = faces([(list(p[:3]), p[3]) for p in main + small])
    return setpx(g, [(10, 2), (9, 3), (6, 7), (2, 11)], "5")


def glass_shard():
    """Two broken panes: two facets each, white top-left edges, light streaks; only the bottom-right edge is dark."""
    panes = [([(10, 1), (4, 9), (8, 9)], "4"), ([(10, 1), (8, 9), (12, 10)], "3"),
             ([(3, 10), (1, 15), (4, 14)], "4"), ([(3, 10), (4, 14), (7, 14)], "3")]
    g = [r.replace("1", "5").replace("0", "1") for r in faces(panes)]
    g = setpx(g, [(8, 4), (7, 6), (10, 5), (9, 7), (2, 12)], "5", only="34")
    return setpx(g, [(9, 3), (6, 7), (2, 13)], "2", only="34")


# Knapped flake: lit facet (top-left) and dark facet meeting on a bright ridge from the tip to the butt, ripple arcs
# (trough / crest) on both facets, glossy glints at the tip and on the lit edge.
FLINT = """
................
............10..
...........1520.
.........114220.
.......11342120.
......143421220.
.....132422120..
....1324322120..
...1424232120...
...1342232120...
..1341223210....
..14212210......
...02110........
....00..........
................
................
"""


# ---- material identity accents (one accent ramp at most, STYLE.md)
def accents(rows, material):
    g = [list(r) for r in rows]
    if material == "ancient_metal":                                       # verdigris specks in the shaded hollows
        for y in range(16):
            for x in range(16):
                if g[y][x] in "23" and (x * 3 + y * 5) % 8 == 0:
                    g[y][x] = "D" if g[y][x] == "3" else "C"
    return text(g), ("verdigris" if material == "ancient_metal" else "string")


MATERIAL_ITEMS = {"ingot": lambda: pix._rows(INGOT), "nugget": lambda: pix._rows(NUGGET), "coin": lambda: pix._rows(COIN), "chain": chain,
                  "door": lambda: pix._rows(DOOR)}
METALS = {"adamantium": "adamantium", "ancient_metal": "ancient_metal", "mithril": "mithril", "silver": "silver",
          "gold": "gold", "golden": "gold", "copper": "copper"}
SHARDS = {"diamond": crystal_shard, "emerald": crystal_shard, "quartz": crystal_shard, "obsidian": crystal_shard,
          "glass": glass_shard, "flint": lambda: pix._rows(FLINT)}


def metal(rows, material):
    rows, acc = accents(rows, material)
    return pix.render(rows, digit=material, upper=acc)


def material_textures():
    out = {}
    for p in sorted(ITEM.glob("*.png")):
        n = p.stem
        if n.startswith("raw_") and n[4:] in METALS:
            out[n] = metal(raw_ore(), METALS[n[4:]])
        elif n.endswith("_shard") and n[:-6] in SHARDS:
            out[n] = pix.render(SHARDS[n[:-6]](), digit=n[:-6])
        else:
            for pre in sorted(METALS, key=len, reverse=True):
                kind = n[len(pre) + 1:]
                if n.startswith(pre + "_") and kind in MATERIAL_ITEMS:
                    out[n] = metal(MATERIAL_ITEMS[kind](), METALS[pre])
                    break
    return out


# ---------------------------------------------------------------- multi-ramp helpers
# chars: '0'-'5' digit ramp, 'A'-'F' upper, 'a'-'f' lower (wood), 'k'-'p' third ramp, 'q'-'v' fourth ramp
FAMILIES = ["012345", "ABCDEF", "abcdef", "klmnop", "qrstuv"]


def as_(rows, fam):
    """Remap digit shades of rows to another ramp family ('A', 'a', 'k' or 'q')."""
    f = next(x for x in FAMILIES if x[0] == fam)
    return ["".join(f[int(c)] if c.isdigit() else c for c in r) for r in rows]


def multi(rows, digit, upper="string", lower="wood", third=None, fourth=None, extra=None):
    ex = dict(extra or {})
    for fam, name in (("klmnop", third), ("qrstuv", fourth)):
        if name:
            ex.update({c: ramp(name)[i] for i, c in enumerate(fam)})
    return pix.render(rows, digit=digit, upper=upper, lower=lower, extra=ex)


def sh(m, fam="0", lit_edge=None):
    return as_(auto(m, lit_edge=lit_edge), fam)


def clip(m, fn):
    """Keep only mask pixels where fn(x, y)."""
    rows = pix._rows(m)
    return "\n".join("".join(c if c == "#" and fn(x, y) else "." for x, c in enumerate(r)) for y, r in enumerate(rows))


def union(*ms):
    rows = [pix._rows(m) for m in ms]
    return "\n".join("".join("#" if any(r[y][x] == "#" for r in rows) else "." for x in range(16)) for y in range(16))


def curve(points, r):
    """Mask of discs of radius r along a polyline (sampled finely)."""
    pts = []
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 4) + 1
        pts += [(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n) for i in range(n + 1)]
    return mask(lambda x, y: any((x - px) ** 2 + (y - py) ** 2 <= r * r for px, py in pts))


# ---------------------------------------------------------------- dishes (shared wooden bowl)
BOWL = """
................
................
................
................
................
...bbbbbbbbbb...
..bd22222222cb..
.bd2333333333ca.
.b33334543333ca.
.beeeeeeeeeedca.
.bddddddddcccca.
..bddddddcccca..
...bddcccccca...
....aaaaaaaa....
................
................
"""


def bowl(over=None, mound=None):
    g = pix._rows(BOWL)
    if mound:
        g = paint(g, mound)
        g = paint(g, ["." * 16] * 9 + [pix._rows(BOWL)[9]] + ["." * 16] * 6)  # front lip over the mound
    return paint(g, over) if over else g


def ov(rows, top=6):
    """Overlay rows starting at row `top` (16 wide each)."""
    return ["." * 16] * top + list(rows) + ["." * 16] * (16 - top - len(rows))


DISHES = {
    "water_bowl": (dict(digit="water"), None),
    "milk_bowl": (dict(digit="milk"), None),
    "beef_stew": (dict(digit="broth", upper="meat", third="carrot"), ov([
        "....2DC22n2.....",
        "...2CB3no3DC....",
        "..33no3DC33no...",
    ])),
    "chicken_soup": (dict(digit="chicken", upper="vanilla", third="leaf"), ov([
        "....2E2n22D.....",
        "...2DC34o3EC....",
        "..3n33EF3mn33...",
    ])),
    "cream_of_mushroom_soup": (dict(digit="cream", upper="mushroom"), ov([
        "....2DC22ED.....",
        "...2BB345BC3....",
        "..33DE33CD333...",
    ])),
    "cream_of_vegetable_soup": (dict(digit="veg_cream", upper="carrot", third="leaf"), ov([
        "....2n22DE2.....",
        "...2mDC45nm3....",
        "..3DE3nm33DE3...",
    ])),
    "vegetable_soup": (dict(digit="veg_broth", upper="carrot", third="potato"), ov([
        "....2DE2no2.....",
        "...2CC3nm3DE....",
        "..3no3DE3noCC...",
    ])),
    "pumpkin_soup": (dict(digit="pumpkin", upper="vanilla", third="leaf"), ov([
        "....22EEE22.....",
        "...2EF3DE3E3....",
        "..33DE33o3333...",
    ])),
    "porridge": (dict(digit="oats", upper="chocolate"), ov([
        "....2232D232....",
        "...234322C343...",
        "..3C3432343C3...",
    ])),
    "cereal": (dict(digit="milk", upper="flake"), ov([
        "....2DE2EC2D....",
        "...2CB4DE3EC....",
        "..3DE3CB34DE3...",
    ])),
}


def mound_dish(kind):
    if kind == "mashed_potatoes":
        m = sh(_ell(7.5, 6.6, 5.4, 3.4))
        m = paint(m, ov(["......EF........", ".....CDD........"], top=3))
        return dict(digit="potato", upper="butter"), m
    if kind == "salad":
        leaves = union(_ell(5.0, 6.5, 3.2, 2.4), _ell(9.8, 6.0, 3.4, 2.6), _ell(7.4, 4.6, 2.6, 2.0))
        m = sh(leaves)
        m = paint(m, ov(["........3.......", ".......345......", "...ABC..4..DE...", "...BDE.....CD..."], top=3))
        m = paint(m, ov(["....245.........", ".........234...."], top=4))
        return dict(digit="salad", upper="tomato"), m
    if kind == "sorbet":
        m = paint(sh(_ell(5.2, 6.2, 3.2, 2.8)), sh(_ell(10.1, 6.0, 3.2, 2.8), "q"))
        m = paint(m, ov(["....5.....t.....", "...5..4..t..s..."], top=4))
        m = [r.replace("v", "u") for r in m]  # 16-colour cap: second scoop without its own highlight
        return dict(digit="sorbet", fourth="strawberry"), m
    if kind == "ice_cream":
        a = sh(_ell(5.0, 6.4, 3.3, 2.9), "A")
        b = sh(_ell(10.1, 5.8, 3.4, 3.2), "0")
        m = paint(a, b)
        m = paint(m, ov([".........b......", "........nm......", "........mm......"], top=0))
        m = paint(m, ov(["...EE...........", "..E.............", "...........4....", "..........4....."], top=4))
        m = [r.replace("F", "E").replace("A", "0").replace("5", "4") for r in m]  # 16-colour cap
        return dict(digit="chocolate", upper="vanilla", third="tomato"), m


def dish(kind):
    if kind in DISHES:
        kw, over = DISHES[kind]
        return multi(bowl(over), **kw)
    kw, m = mound_dish(kind)
    return multi(bowl(mound=m), **kw)


# ---------------------------------------------------------------- loose foods
def onion():
    body = union(_ell(7.5, 9.8, 5.0, 4.6), mask(lambda x, y: y in (4, 5) and 6 <= x <= 9 - (y == 4)))
    g = sh(body)
    g = paint(g, ov([
        ".......mn.......",
        "......lmno......",
        ".......ln.......",
        "................",
        "................",
        ".....2....2.....",
        "....2......2....",
        "....2..4...2....",
        "....2......2....",
        ".....2....2.....",
        "................",
        "......1.1.......",
        ".....1...1......",
    ], top=1))
    return multi(g, digit="onion", third="onion_green")


def orange():
    g = sh(_ell(7.5, 9.0, 5.6, 5.3))
    g = paint(g, ov([
        "..........lmn...",
        ".........lmno...",
        "........cl......",
        "................",
        ".....5..........",
        "....5...........",
        "................",
        "..........2.....",
        "........2.......",
        "................",
        "......2.........",
    ], top=1))
    return multi(g, digit="orange", third="leaf")


def tube(spine):
    """Mask along a spine of (x, y, radius) points, radius interpolated."""
    pts = []
    for (x0, y0, r0), (x1, y1, r1) in zip(spine, spine[1:]):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 4) + 1
        pts += [(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, r0 + (r1 - r0) * i / n) for i in range(n + 1)]
    return mask(lambda x, y: any((x - px) ** 2 + (y - py) ** 2 <= r * r for px, py, r in pts))


def banana():
    m = tube([(12.6, 3.2, 1.0), (12.6, 6.0, 1.7), (11.0, 9.4, 2.1), (7.6, 11.8, 2.0), (4.0, 12.4, 1.4),
              (1.8, 11.4, 0.8)])
    g = sh(m)
    g = paint(g, ov([".............b..", "............cb.."], top=1))
    g = paint(g, ov([".b.............."], top=11))
    return multi(g, digit="banana")


def flour():
    sack = union(_ell(7.5, 10.6, 5.4, 4.4), mask(lambda x, y: 5 <= x <= 10 and 5 <= y <= 7))
    g = sh(sack, "A")
    g = paint(g, ov(["....BDDEDDCA....", ".....bdccba....."], top=5))
    g = paint(g, sh(clip(_ell(7.5, 5.0, 3.6, 2.4), lambda x, y: y <= 5)))
    g = paint(g, ov(["......45........"], top=3))
    return multi(g, digit="flour", upper="string")


def dough():
    g = sh(union(_ell(7.5, 10.0, 6.3, 4.0), _ell(7.0, 7.4, 4.2, 3.2)))
    g = paint(g, ov([
        "......o.........",
        ".....o..p.......",
        "................",
        "...........2....",
        "....2222222.....",
        "................",
        "........o.......",
    ], top=5))
    return multi(g, digit="dough", third="flour")


def faces(polys):
    """Flat polygons (points, char) rasterised in order, then a 1 px outline in each char's ramp family."""
    from PIL import Image, ImageDraw
    g = [["."] * 16 for _ in range(16)]
    for pts, ch in polys:
        im = Image.new("L", (16, 16), 0)
        ImageDraw.Draw(im).polygon(pts, fill=1)
        for y in range(16):
            for x in range(16):
                if im.getpixel((x, y)):
                    g[y][x] = ch
    return outline(g)


def outline(g):
    fill = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and g[y][x] != "."
    out = [r[:] for r in g]
    for y in range(16):
        for x in range(16):
            if not fill(x, y):
                continue
            fam = next((f for f in FAMILIES if g[y][x] in f), "012345")
            if not fill(x, y + 1) or not fill(x + 1, y):
                out[y][x] = fam[0]
            elif not fill(x, y - 1) or not fill(x - 1, y):
                out[y][x] = fam[1]
    return text(out)


def cheese():
    g = faces([([(1, 10), (12, 3), (14, 5), (14, 7)], "4"), ([(1, 10), (14, 6), (14, 13), (1, 13)], "3"),
               ])
    g = paint(g, ov([
        "............5...",
        "..........5.....",
        "........2.......",
        "......5.24......",
        "...........1....",
        "....12.....24...",
        "....24..12......",
        "........24......",
    ], top=3))
    return multi(g, digit="cheese")


CHOCOLATE = """
................
................
................
..........BEDCA.
.111111111BEDCA.
.1443144314EDCA.
.1432143214FDCA.
.1322132212EDCA.
.1111111111EDCA.
.1443144314EDCA.
.1432143214EFCA.
.1322132212EDCA.
.0000000000EDCA.
..........AAAAA.
................
................
"""


def worm(cooked=False):
    pts = [(1.8, 9.0), (4.0, 6.6), (7.0, 8.4), (9.6, 10.4), (12.4, 8.6), (13.6, 6.4)]
    g = [list(r) for r in sh(curve(pts, 1.45))]
    for x, y in [(3, 7), (6, 8), (9, 10), (12, 9)]:  # segment rings across the body
        if g[y][x] in "345":
            g[y][x] = "2"
    return multi(text(g), digit="cooked_worm" if cooked else "worm")


def manure():
    g = BLANK
    for m in (_ell(7.5, 12.0, 6.3, 2.6), _ell(7.8, 9.0, 4.6, 2.2), _ell(7.4, 6.4, 3.0, 1.9)):
        g = paint(g, auto(m))
    g = paint(g, ov(["........1.......", ".......10......."], top=3))
    g = paint(g, ov(["..........2.....", "................", "...2............", "................",
                     "........2......."], top=7))
    return multi(g, digit="manure")


def sinew():
    pts = [(2.0, 13.5), (4.0, 10.0), (3.5, 7.5), (6.0, 6.0), (9.0, 8.0), (11.0, 6.5), (11.0, 3.5), (13.5, 2.0)]
    g = sh(curve(pts, 1.1))
    g = paint(g, ov([".4..............", "................", "..4............."], top=12))
    return multi(g, digit="sinew")


def slime_ball(kind):
    g = sh(_ell(7.5, 8.5, 5.0, 4.8))
    g = paint(g, auto(_ell(7.0, 8.0, 2.4, 2.2)).__iter__() and [r.replace("0", ".").replace("1", ".")
                                                                .replace("2", "3").replace("3", "4") for r in
                                                                auto(_ell(6.8, 8.2, 2.6, 2.4))])
    g = paint(g, ov(["....5...........", "...5............"], top=6))
    return multi(g, digit=kind)


def bottle(liquid):
    """Round flask: glass outline (upper ramp), translucent empty glass, liquid (digit ramp) in the lower half, cork."""
    glass = union(_ell(7.5, 10.4, 5.0, 4.6), mask(lambda x, y: 6 <= x <= 9 and 3 <= y <= 6))
    g = [list(r) for r in as_(auto(glass), "A")]
    for y in range(16):
        for x in range(16):
            if g[y][x] in "CDEF":
                g[y][x] = "w"
    g = text(g)
    liq = auto(clip(_ell(7.5, 10.4, 4.0, 3.6), lambda x, y: y >= 8))
    g = paint(g, [r.replace("1", "4").replace("0", "2") for r in liq])
    g = paint(g, ov(["......cddb......", "......bcca......"], top=1))
    g = paint(g, ov(["....F...........", "....E...........", "........5.......", "......5....5....",
                     ".........5......"], top=7))
    extra = {"w": ramp("glass")[4][:3] + (110,)}
    return multi(g, digit=liquid, upper="glass", extra=extra)


FOODS = {
    "onion": onion, "orange": orange, "banana": banana, "flour": flour, "dough": dough, "cheese": cheese,
    "chocolate": lambda: multi(CHOCOLATE, digit="chocolate", upper="silver"),
    "worm": worm, "cooked_worm": lambda: worm(True), "manure": manure, "sinew": sinew,
    **{f"{k}_ball": (lambda k=k: slime_ball(k)) for k in ("jelly", "blob", "ooze", "pudding")},
    "enchanting_bottle": lambda: bottle("magic"), "disenchanting_bottle": lambda: bottle("void"),
}
MOUNDS = ["mashed_potatoes", "salad", "sorbet", "ice_cream"]


def food_textures():
    return {k: dish(k) for k in list(DISHES) + MOUNDS}


def misc_textures():
    return {k: fn() for k, fn in FOODS.items()}


# ---------------------------------------------------------------- spawn eggs
EGG = mask(lambda x, y: ((x - 7.5) / (4.9 * (1 - 0.22 * max(0.0, 8.6 - y) / 7.0))) ** 2
           + ((y - 8.6) / 6.9) ** 2 <= 1.0)
SPOTS = """
................
................
................
................
......SS........
.....SSS....S...
.....SS....SS...
................
...S.....SSS....
..SS.....SSS....
..........S.....
.......SS.......
......SSSS...S..
.......SS...SS..
................
................
"""
EGGS = {
    "longdead": ("bone", "flint"), "longdead_guardian": ("bone", "rusted_iron"), "bone_lord": ("iron", "bone"),
    "ancient_bone_lord": ("ancient_metal", "bone"), "black_widow": ("black", "red"),
    "demon_spider": ("blood", "obsidian"), "phase_spider": ("spirit", "obsidian"), "wood_spider": ("wood", "black"),
    "jelly": ("jelly", "copper"), "blob": ("blob", "shadow"), "ooze": ("ooze", "black"), "pudding": ("pudding", "black"),
    "clay_golem": ("clay", "deepslate"), "earth_elemental": ("dirt", "stone"), "fire_elemental": ("lava", "gold"),
    "copperspine": ("copper", "brown"), "hoary_silverfish": ("silver", "milk"), "netherspawn": ("netherrack", "lava"),
    "dire_wolf": ("wolf", "black"), "hellhound": ("black", "lava"), "infernal_creeper": ("netherrack", "black"),
    "ghoul": ("rot", "meat"), "wight": ("pale", "shadow"), "revenant": ("rusted_iron", "rot"),
    "invisible_stalker": ("stalker", "diamond"), "shadow": ("shadow", "spirit"), "vampire_bat": ("bat", "blood"),
    "giant_vampire_bat": ("brown", "blood"), "nightwing": ("night", "spirit"),
}


def egg(base, spot):
    g = [list(r) for r in auto(EGG)]
    for y, row in enumerate(pix._rows(SPOTS)):
        for x, ch in enumerate(row):
            if ch == "S" and g[y][x].isdigit():
                g[y][x] = "ABCDEF"[int(g[y][x])]
    return multi(text(g), digit=base, upper=spot)


def egg_textures():
    return {f"{k}_spawn_egg": egg(*v) for k, v in EGGS.items()}


GROUPS = {"materials": material_textures, "dishes": food_textures, "food_misc": misc_textures, "eggs": egg_textures}


def main(argv):
    cmd = argv[0] if argv else "sheet"
    if cmd == "write":
        n = 0
        for fn in GROUPS.values():
            for name, img in fn().items():
                pix.save(img, ITEM / f"{name}.png")
                n += 1
        print(f"wrote {n} textures")
        return 0
    out = Path(argv[1]) if len(argv) > 1 else pix.ROOT / "build/art"
    for g, fn in GROUPS.items():
        imgs = fn()
        print(pix.contact_sheet(sorted(imgs.items()), out / f"batch_b_{g}.png", scale=8, cols=8), len(imgs))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
