"""Batch C block textures (original designs, rules in docs/art/STYLE.md section 6).

    python -I tools/pixelart/blocks.py write            # (re)draw every batch C texture under textures/block
    python -I tools/pixelart/blocks.py sheets [DIR]     # review sheets (default docs/art/sheets/batch_c_*.png)

Grids: digits '0'-'5' = main ramp, 'a'-'f' = lower ramp, 'A'-'F' = upper ramp (see pix.draw). Hosts (stone, deepslate,
mantle) are drawn procedurally from wrapped blotches so they tile; everything else is hand-drawn ASCII.
"""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
from palettes import RAMPS, ramp  # noqa: E402

# Block-only ramps (6 shades, dark -> light, hue-shifted). Kept here so batch C does not touch the shared palette file;
# move them to palettes.py when the batches are merged.
RAMPS.update({
    "clay":       ["#3a221a", "#5e3727", "#845036", "#a76b48", "#c48b63", "#dcae86"],  # fired clay, terracotta tan
    "sandstone":  ["#55452a", "#857044", "#b09a63", "#d0bd88", "#e5d4a3", "#f5ebc6"],
    "netherrack": ["#260b10", "#461519", "#6a2023", "#8c312f", "#aa4a42", "#c76c5e"],
    "plant":      ["#0f2a12", "#1c4a1c", "#2e6d27", "#478f30", "#6fb145", "#a6d56e"],
    "dry":        ["#2c2319", "#4a3c2a", "#6a583d", "#8a7453", "#a6916c", "#c2ae8a"],  # dead crops: no green
    "cloth":      ["#360c13", "#62151f", "#8f1f2b", "#b4333a", "#d05950", "#e88a76"],
    "onion":      ["#43261a", "#73452a", "#a26a3c", "#c48f55", "#ddb276", "#f1d9a6"],
})
METALS = ("silver", "mithril", "adamantium", "copper", "gold", "iron", "ancient_metal", "obsidian")
OUT = pix.TEXTURES / "block"


def _rows(s):
    return [r.strip() for r in s.strip("\n").splitlines() if r.strip()]


def grid_img(g, digit, lower="wood", upper="string", extra=None):
    return pix.render(_rows(g) if isinstance(g, str) else g, digit=digit, lower=lower, upper=upper, extra=extra)


def to_rows(m):
    return ["".join(str(v) for v in row) for row in m]


# ---------------------------------------------------------------- hosts (procedural, wrapped so they tile)
BLOBS = [
    [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1)],
    [(0, 0), (1, 0), (2, 0), (3, 0), (1, 1), (2, 1)],
    [(1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1), (1, 2)],
    [(0, 0), (1, 0), (1, 1), (2, 1), (3, 1)],
    [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)],
    [(0, 0), (1, 0), (2, 0), (1, 1), (2, 1), (3, 1), (4, 1)],
]


def stone_host(seed=7):
    """Original stone: base 3, recessed blotches (2, with a lit 4 rim below-right) and raised ones (4, dark 1 rim)."""
    rng = random.Random(seed)
    g = [[3] * 16 for _ in range(16)]
    for i in range(12):  # jittered 4x3 grid: blotches spread evenly, no bald patches
        shape = rng.choice(BLOBS)
        ox, oy = (i % 4) * 4 + rng.randrange(3), (i // 4) * 5 + rng.randrange(3)
        dark = rng.random() < 0.6
        cells = {((ox + dx) % 16, (oy + dy) % 16) for dx, dy in shape}
        for x, y in cells:
            g[y][x] = 2 if dark else 4
        for x, y in cells:  # rim on the outside, bottom/right side
            for nx, ny in (((x + 1) % 16, y), (x, (y + 1) % 16)):
                if (nx, ny) not in cells and g[ny][nx] == 3:
                    g[ny][nx] = 4 if dark else 2
    return to_rows(g)


def deepslate_host(seed=3):
    """Original deepslate: base 2/3 bands with horizontal streaks of 1 (cracks) and 4 (lit ridges above them)."""
    rng = random.Random(seed)
    g = [[3 if (y // 4) % 2 == 0 else 2 for _ in range(16)] for y in range(16)]
    for _ in range(14):
        y, x0, n = rng.randrange(16), rng.randrange(16), rng.randrange(3, 8)
        for k in range(n):
            x = (x0 + k) % 16
            g[y][x] = 1
            if g[(y - 1) % 16][x] in (2, 3):
                g[(y - 1) % 16][x] = 4 if k < n - 1 else 3
    return to_rows(g)


# ---------------------------------------------------------------- ores
# Cluster masks ('#') placed at (x, y); rim and shading are computed so every cluster reads the same way.
CLUSTERS = {
    "a": [".##.", "####", ".###"],
    "b": ["##.", "###", ".##"],
    "c": [".##", "###", "##."],
    "d": ["###.", "####"],
    "e": [".#", "##", "##"],
    "f": ["##.", "###"],
}
ORE_LAYOUT = {
    "silver":     [("a", 1, 1), ("d", 10, 2), ("c", 5, 6), ("b", 11, 9), ("f", 2, 11), ("e", 7, 12)],
    "mithril":    [("b", 2, 2), ("f", 9, 1), ("a", 10, 6), ("d", 3, 8), ("c", 12, 12), ("e", 6, 12)],
    "adamantium": [("c", 1, 1), ("a", 7, 3), ("f", 13, 5), ("e", 2, 8), ("b", 9, 10), ("d", 3, 13)],
}


def ore_overlay(metal):
    core = set()
    for name, ox, oy in ORE_LAYOUT[metal]:
        for dy, row in enumerate(CLUSTERS[name]):
            for dx, ch in enumerate(row):
                if ch == "#":
                    core.add(((ox + dx) % 16, (oy + dy) % 16))
    g = [["."] * 16 for _ in range(16)]
    inn = lambda x, y: ((x % 16), (y % 16)) in core
    for x, y in core:
        up, left, down, right = inn(x, y - 1), inn(x - 1, y), inn(x, y + 1), inn(x + 1, y)
        if not up and not left:
            v = 5
        elif not up or not left:
            v = 4
        elif not down or not right:
            v = 2
        else:
            v = 3
        g[y][x] = str(v)
    for x, y in core:  # rim outside the cluster: shade 0 below/right, 1 above/left
        for nx, ny in ((x + 1, y), (x, y + 1)):
            nx, ny = nx % 16, ny % 16
            if (nx, ny) not in core:
                g[ny][nx] = "0"
    return ["".join(r) for r in g]


def ore(metal, host="stone"):
    base = grid_img(stone_host() if host == "stone" else deepslate_host(), host)
    return pix.overlay(base, grid_img(ore_overlay(metal), metal))


# ---------------------------------------------------------------- storage blocks: stacked bars, running bond
BAR = """
1145555444444211
1444444444444421
2333333333333320
2433333333333320
2433333333333320
2333333333333320
2222222222222220
1000000000000000
"""


def metal_block(metal):
    bar = _rows(BAR)
    shifted = [r[8:] + r[:8] for r in bar]
    return grid_img(bar + shifted, metal)


# ---------------------------------------------------------------- strongbox: riveted frame, inset panel, keyhole plate
STRONGBOX = """
1111111111111110
1444444444444420
1454444444444520
1442222222222420
1442333333333420
1442333333333420
1442334444323420
1442334003123420
1442334003123420
1442334301123420
1442334301123420
1442333111123420
1442444444444420
1454444444444520
1422222222222220
0000000000000000
"""


def strongbox(metal):
    return grid_img(STRONGBOX, metal)


# ---------------------------------------------------------------- runestone: dark obsidian blocks with a glowing rune
RUNE_HOST = """
dcccccccdccccccc
cddddddcbddddddc
cddddddcbddddddc
cddddddcbddddddc
cbbbbbbcbbbbbbbc
dcccdcccccccdccc
dddcbddddddcbddd
dddcbddddddcbddd
dddcbddddddcbddd
bbbcbbbbbbbcbbbb
dcccccccdccccccc
cddddddcbddddddc
cddddddcbddddddc
cddddddcbddddddc
cddddddcbddddddc
cbbbbbbcbbbbbbbc
"""
RUNES = {
    "mithril": """
................
................
.......5........
......545.......
.....4.3.4......
....4..3..4.....
.......3........
.....43334......
.......3........
.......3........
......4.4.......
.....4...4......
.....3...3......
................
................
................
""",
    "adamantium": """
................
................
....5.....5.....
....43...34.....
.....43.34......
......434.......
.......3........
.......3........
.....43334......
.......3........
.......3........
......434.......
.....3...3......
................
................
................
""",
}


def runestone(metal):
    img = grid_img(RUNE_HOST, metal, lower="obsidian")
    # every rune pixel gets a dark obsidian shadow below-right so the glyph looks carved and lit
    rune = _rows(RUNES[metal])
    shadow = [["."] * 16 for _ in range(16)]
    for y, r in enumerate(rune):
        for x, ch in enumerate(r):
            if ch != "." and x + 1 < 16 and y + 1 < 16 and rune[y + 1][x + 1] == ".":
                shadow[y + 1][x + 1] = "a"
    pix.draw(img, ["".join(r) for r in shadow], digit=metal, lower="obsidian")
    pix.draw(img, rune, digit=metal)
    return img


# ---------------------------------------------------------------- workbench: wood frame + planks, metal corners, tools
BENCH_WOOD = """
bbbbbbbbbbbbbbbb
beeeeeeeeebeeeea
bdddcddddddbdcda
bddddddccddbddda
bccddddddddbdcca
bbbbbbbbbbbbbbba
beeeeebeeeeeeeea
bddccdbddddddcda
bdddddbccddddda.
bcddddbdddddddca
bbbbbbbbbbbbbbba
beeebeeeeeeeeeea
bdddbccddddddcda
bdddbddddccdddda
bdcdbddddddddcda
aaaaaaaaaaaaaaaa
"""
HAMMER_HEAD = ["111110", "154420", "143320", "100000"]  # item-style outline: 1 top/left, 0 bottom/right


def bench_tools():
    """Crossed hammer (head top-left, handle down-right) and saw (blade up-right, grip bottom-left)."""
    g = [["."] * 16 for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = c
    for y in range(6, 13):  # hammer handle: [b][e][a] per row, end cap below
        x = y - 1
        put(x - 1, y, "b"), put(x, y, "e"), put(x + 1, y, "a")
    put(11, 13, "a"), put(12, 13, "a")
    for y in range(3, 11):  # saw blade: teeth (alternating 0/2) on the lower-left edge, lit back on the upper-right
        c = 14 - y
        put(c - 1, y, "0" if y % 2 else "2"), put(c, y, "3"), put(c + 1, y, "5" if y == 3 else "4")
    for y in range(11, 14):  # saw grip
        c = 14 - y
        put(c - 1, y, "b"), put(c, y, "e"), put(c + 1, y, "a")
    put(0 + 1, 14, "a"), put(2, 14, "a")
    for dy, r in enumerate(HAMMER_HEAD):
        for dx, ch in enumerate(r):
            put(2 + dx, 2 + dy, ch)
    return ["".join(r) for r in g]


BENCH_CORNERS = """
4443........4442
4511........1151
42............31
21............20
................
................
................
................
................
................
................
................
43............31
43............31
4543........4451
2110........1110
"""


def workbench(metal):
    wood = [r if r[-1] != "." else r[:-1] + "a" for r in _rows(BENCH_WOOD)]
    return pix.render([wood, bench_tools(), _rows(BENCH_CORNERS)], digit=metal, lower="wood")


# ---------------------------------------------------------------- furnaces (orientable: front, front_on, side, top)
def bricks(courses, tones=(3, 3, 2, 3, 4, 3, 2), round_corners=False):
    """courses: list of (height, [brick widths], x offset). Mortar 1, brick top/left edge lit, bottom/right inner shaded."""
    g = [[3] * 16 for _ in range(16)]
    y, t = 0, 0
    for h, widths, off in courses:
        x = off
        for w in widths * 8:
            if x >= off + 16:
                break
            tone = tones[t % len(tones)]
            t += 1
            for dy in range(h):
                for dx in range(w):
                    xx, yy = (x + dx) % 16, y + dy
                    if yy >= 16:
                        continue
                    if dy == h - 1 or dx == w - 1:
                        v = 1
                    elif dy == 0 or dx == 0:
                        v = min(5, tone + 1)
                    elif dy == h - 2 or dx == w - 2:
                        v = max(2, tone - 1)
                    else:
                        v = tone
                    if round_corners and dx == 0 and dy in (0, h - 2) and h > 2:
                        v = 1 if dy == h - 2 else 2
                    g[yy][xx] = v
            x += w
        y += h
    return g


def framed(g):
    g = [row[:] for row in g]
    for i in range(16):
        g[0][i], g[i][0] = 1, 1
        g[15][i], g[i][15] = 0, 0
    return g


FURNACE_BODY = {
    "clay": [(4, [8], 0), (4, [8], 4), (4, [8], 0), (4, [8], 4)],
    "large_clay": [(5, [8], 2), (6, [8], 6), (5, [8], 2)],
    "sandstone": [(4, [16], 0), (3, [16], 8), (5, [16], 4), (4, [16], 12)],
    "obsidian": [(8, [8], 0), (8, [8], 0)],
    "netherrack": [(4, [5, 4, 7], 0), (3, [6, 5, 5], 3), (5, [4, 7, 5], 1), (4, [7, 4, 5], 5)],
}
FURNACE_RAMP = {"clay": "clay", "large_clay": "clay", "sandstone": "sandstone", "obsidian": "obsidian",
                "netherrack": "netherrack"}
OPEN_OFF = ["0000000000", "0000000000", "0000000000", "0000000000", "0101010101", "1010101010", "1111111111"]
OPEN_ON = ["AAAAAAAAAA", "AABAAAABAA", "ABCBAABCBA", "BCDCBBCDCB", "CDEDCCDEDC", "DEFEDDEFED", "EFFFEEFFFE"]


def furnace_body(kind):
    g = bricks(FURNACE_BODY[kind], round_corners=kind in ("clay", "large_clay"))
    if kind == "sandstone":  # strata: a lit line and a shaded line through each course
        for y, v in ((1, 4), (9, 4), (5, 2), (13, 2)):
            for x in range(16):
                if g[y][x] == 3:
                    g[y][x] = v
    if kind == "obsidian":  # polished glints on each slab
        for ox, oy in ((0, 0), (8, 0), (0, 8), (8, 8)):
            g[oy + 2][ox + 2], g[oy + 2][ox + 3], g[oy + 3][ox + 2] = 5, 4, 4
    if kind == "large_clay":  # fired, darker band across the middle course
        for y in range(5, 11):
            for x in range(16):
                g[y][x] = max(1, g[y][x] - 1)
    return g


def furnace_side(kind):
    return grid_img(to_rows(framed(furnace_body(kind))), FURNACE_RAMP[kind])


def furnace_top(kind):
    g = bricks([(8, [8], 0), (8, [8], 0)], tones=(3, 2, 2, 3))
    if kind == "large_clay":
        g = bricks([(8, [16], 0), (8, [16], 8)], tones=(3, 2))
    if kind in ("clay", "large_clay"):  # flue hole in the middle of the roof
        for y in range(6, 10):
            for x in range(6, 10):
                g[y][x] = 0 if 6 < y < 9 and 6 < x < 9 else 1 if (y == 6 or x == 6) else 2
    return grid_img(to_rows(framed(g)), FURNACE_RAMP[kind])


def furnace_front(kind, lit):
    g = framed(furnace_body(kind))
    for x in range(2, 14):  # lintel over the opening
        g[6][x] = 4 if x < 13 else 2
        g[7][x] = 2 if x > 2 else 3
    for y in range(8, 15):  # jambs
        g[y][2], g[y][13] = 4, 2
    rows = to_rows(g)
    opening = OPEN_ON if lit else OPEN_OFF
    for i, line in enumerate(opening):
        y = 8 + i
        rows[y] = rows[y][:3] + line + rows[y][13:]
    if kind in ("clay", "large_clay"):  # arched mouth: round the two top corners (identical in both states)
        rows[8] = rows[8][:3] + "4" + rows[8][4:12] + "2" + rows[8][13:]
    return grid_img(rows, FURNACE_RAMP[kind], upper="lava")


# ---------------------------------------------------------------- anvils
ANVIL_BODY = """
5444444444444442
4333333333333332
4333233333332332
4333333333333332
4332333333333232
2222222222222221
3333333333333332
3332333333332332
3333333333333332
3333333233333332
3333333333333332
4444444444444442
4333333333333332
3332333333323332
2222222222222221
1111111111111110
"""
ANVIL_TOP = """
...1111111111...
...1333333331...
...1345444431...
...1354444431...
...1344444431...
...1344444431...
...1344444431...
...1344444431...
...1344444431...
...1344444431...
...1344444431...
...1344440031...
...1340440031...
...1344444431...
...1333333331...
...1111111111...
"""


def anvil(metal):
    return grid_img(ANVIL_BODY, metal)


def anvil_top(metal):
    return grid_img(ANVIL_TOP, metal)


# ---------------------------------------------------------------- bars and doors
BAR_COLS = (2, 7, 12)  # 2 px bars; 7-8 is also the post the model samples


def bars(metal):
    g = [["."] * 16 for _ in range(16)]
    for y in range(16):
        for c in BAR_COLS:
            g[y][c], g[y][c + 1] = ("2", "1") if y == 15 else ("5", "4") if y == 0 else ("4", "2")
    for y in (4, 11):
        for x in range(16):
            g[y][x] = "5" if x in BAR_COLS else "1" if x - 1 in BAR_COLS else "3"
    return grid_img(["".join(r) for r in g], metal)


DOOR_BOTTOM = """
1433333333333320
1435333333335320
1431111421111320
1431223421223320
1431223421223320
1431223421223320
1431223421223320
1431223421223320
1431223421223320
1431223421223320
1431223421223320
1434444424444320
1433333333333320
1435333333335320
1222222222222220
0000000000000000
"""
DOOR_TOP = """
1111111111111110
1444444444444420
1431111421111320
143....42....320
143....42....320
143....42....320
143....42....320
143....42....320
1434444424444320
1433333333333320
1435333333335320
1433333333333320
1433333333453320
1433333333213320
1433333333333320
1433333333333320
"""


def door(metal, half):
    return grid_img(DOOR_BOTTOM if half == "bottom" else DOOR_TOP, metal)


# ---------------------------------------------------------------- emerald enchanting table (digit emerald, a-f obsidian, A-F cloth)
ENCH_TOP = """
eddddddddddddddc
d54CCCCCCCCCC54b
d32CCCCCCCCCC32b
dCCEEEEEEEEEECCb
dCCECCCCCCCCECCb
dCCECCC45CCCECCb
dCCECC4533CCECCb
dCCEC453332CECCb
dCCEC433322CECCb
dCCECC3221CCECCb
dCCECCC21CCCECCb
dCCECCCCCCCCECCb
dCCEEEEEEEEEECCb
d54CCCCCCCCCC54b
d32CCCCCCCCCC32b
cbbbbbbbbbbbbbba
"""
ENCH_SIDE = """
................
................
................
................
EEEEEEEEEEEEEEEE
CCCCCCCCCCCCCCCC
BCC45CCBBCC45CCB
cBB21BBccBB21BBc
eddddddddddddddc
dccccccccccecccb
dcecccc45ccccccb
dcccccc4533ccccb
dccccc3221ecccbb
dcccccc21ccccccb
dcccecccccccccbb
cbbbbbbbbbbbbbba
"""


def enchanting(face):
    if face == "bottom":
        g = framed(bricks([(8, [8], 0), (8, [8], 4)], tones=(2, 3, 2, 2)))
        return grid_img(to_rows(g), "obsidian")
    return grid_img(ENCH_TOP if face == "top" else ENCH_SIDE, "emerald", lower="obsidian", upper="cloth")


# ---------------------------------------------------------------- mantle and core (tileable)
def _walk(rng, g, start, steps, val):
    x, y = start
    for _ in range(steps):
        g[y % 16][x % 16] = val
        dx, dy = rng.choice(((1, 0), (1, 0), (0, 1), (1, 1), (-1, 1), (1, -1)))
        x, y = x + dx, y + dy


def mantle():
    """Deep rock (deepslate ramp a-f) split by glowing magma cracks (lava ramp digits)."""
    rng = random.Random(11)
    host = [["abcdef"[int(ch)] for ch in r] for r in deepslate_host(5)]
    for start in ((0, 2), (5, 9), (11, 4)):
        _walk(rng, host, start, 14, "2")
    for y in range(16):  # hotter core where cracks meet
        for x in range(16):
            if host[y][x] == "2":
                n = sum(host[(y + dy) % 16][(x + dx) % 16] in "234" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if n >= 2:
                    host[y][x] = "3"
    for x, y in ((3, 3), (9, 11)):
        host[y][x] = "4"
    return pix.render(["".join(r) for r in host], digit="lava", lower="deepslate")


def core():
    """Molten core: lava (digits) in soft hot/cool blotches, with dark crust plates (obsidian a-f) drifting on it."""
    rng = random.Random(4)
    g = [list(r) for r in stone_host(19)]  # reuse the wrapped blotch layout, recoloured: 2 cool, 3 base, 4 hot
    for i in range(5):
        shape = BLOBS[(i * 2) % len(BLOBS)]
        ox, oy = (i % 3) * 5 + rng.randrange(3), (i * 3 + rng.randrange(3)) % 16
        cells = {((ox + dx) % 16, (oy + dy) % 16) for dx, dy in shape}
        for x, y in cells:
            g[y][x] = "b"
        x, y = min(cells, key=lambda c: (c[1], c[0]))
        g[y][x] = "c"
        for x, y in cells:  # glowing seam around each plate: hot above/left, cooler below/right
            for nx, ny, v in (((x + 1) % 16, y, "2"), (x, (y + 1) % 16, "2"), ((x - 1) % 16, y, "5"),
                              (x, (y - 1) % 16, "5")):
                if (nx, ny) not in cells and g[ny][nx] in "12345":
                    g[ny][nx] = v
    g = [["2" if ch == "1" else ch for ch in r] for r in g]
    return pix.render(["".join(r) for r in g], digit="lava", lower="obsidian")


# ---------------------------------------------------------------- crops
def stalk(g, x, h, lean=0, every=3):
    """Upright stalk from the ground (row 15) up h rows: 2 px (lit 4 | shade 2), 1 px tip (4 then 5);
    lean -1/+1 shifts it one column every `every` rows."""
    for i in range(h):
        y = 15 - i
        xx = x + lean * (i // every)
        top = i >= h - 2
        if 0 <= xx < 16:
            g[y][xx] = "5" if i == h - 1 else "2" if i == 0 else "4" if i < h - 4 or top else "3"
        if not top and 0 <= xx + 1 < 16:
            g[y][xx + 1] = "1" if i == 0 else "2"


ONION_STAGES = [
    [(2, 3, 0), (6, 2, 0), (9, 3, 0), (13, 2, 0)],
    [(2, 5, -1), (5, 6, 0), (9, 5, 0), (12, 6, 1)],
    [(1, 8, -1), (4, 10, 0), (8, 11, 0), (11, 9, 1), (13, 7, 1)],
    [(1, 9, -1), (4, 12, 0), (8, 13, 0), (11, 11, 1), (13, 8, 1)],
]
BULB = [".ed.", "edcb", "dcba", ".ba."]


def onion(stage):
    g = [["."] * 16 for _ in range(16)]
    for x, h, lean in ONION_STAGES[stage]:
        stalk(g, x, h, lean, every=4)
    if stage == 3:
        for bx in (3, 7, 11):
            for dy, r in enumerate(BULB):
                for dx, ch in enumerate(r):
                    if ch != ".":
                        g[12 + dy][bx + dx] = ch
    return pix.render(["".join(r) for r in g], digit="plant", lower="onion")


DEAD = {
    "wheat": """
................
................
.....DC.........
....C..3...CD...
...D...3..C..3..
.......3.D...3..
...2...3.....3..
...2..3......2..
...2..3.....2...
....2.2.....2...
....2.2....2....
....1.2....2....
....1.1...1.....
.....11...1.....
.....1....1.....
.....1....1.....
""",
    "carrots": """
................
................
................
................
................
................
.....C.......C..
....D.3....3.D..
...C..3...3..C..
.....3.3.3.4....
..D.3...33....C.
...3.....3.3..D.
..C......2..3...
.........2...C..
........21......
........11......
""",
    "potatoes": """
................
................
................
................
................
................
................
......CD........
.....4332..DC...
....43221.4332..
...C3211.43221..
..CD.21..3211D..
.....21.D.21....
....1.2..21.....
.......21.......
.......11.......
""",
    "onions": """
................
................
................
................
................
................
................
................
..CDD3.....43DC.
.D...3.....3...C
.....3....3.....
....2.....3.CDD.
....2.....2.3...
.....2...2.2....
.....1...1.1....
.....1...1.1....
""",
}


def dead(crop):
    return grid_img(DEAD[crop], "dry", upper="string")


# ---------------------------------------------------------------- registry
def textures():
    """{file stem: image} for every batch C texture."""
    t = {}
    for m in ("silver", "mithril", "adamantium"):
        t[f"{m}_ore"] = ore(m)
        t[f"deepslate_{m}_ore"] = ore(m, "deepslate")
        t[f"{m}_block"] = metal_block(m)
    t["ancient_metal_block"] = metal_block("ancient_metal")
    for m in ("silver", "mithril", "adamantium", "ancient_metal", "gold"):
        t[f"{m}_bars"] = bars(m)
        t[f"{m}_door_bottom"] = door(m, "bottom")
        t[f"{m}_door_top"] = door(m, "top")
    for m in ("mithril", "adamantium"):
        t[f"{m}_runestone"] = runestone(m)
    for m in ("silver", "mithril", "adamantium", "copper", "gold", "iron", "ancient_metal"):
        t[f"{m}_strongbox"] = strongbox(m)
    for m in METALS:
        t[f"{m}_workbench"] = workbench(m)
    for m in ("silver", "mithril", "adamantium", "copper", "gold", "ancient_metal"):
        t[f"{m}_anvil"] = anvil(m)
        t[f"{m}_anvil_top"] = anvil_top(m)
    for k in FURNACE_BODY:
        t[f"{k}_furnace_front"] = furnace_front(k, False)
        t[f"{k}_furnace_front_on"] = furnace_front(k, True)
        t[f"{k}_furnace_side"] = furnace_side(k)
        t[f"{k}_furnace_top"] = furnace_top(k)
    for f in ("top", "side", "bottom"):
        t[f"emerald_enchanting_table_{f}"] = enchanting(f)
    t["mantle"] = mantle()
    t["core"] = core()
    for s in range(4):
        t[f"onions_stage{s}"] = onion(s)
    for c in DEAD:
        t[f"dead_{c}"] = dead(c)
    return t


TILEABLE = ("_ore", "_block", "mantle", "core")
GROUPS = [
    ("ores_blocks", lambda n: n.endswith(("_ore", "_block")) or n in ("mantle", "core")),
    ("workbench_strongbox_runestone", lambda n: n.endswith(("_workbench", "_strongbox", "_runestone"))),
    ("furnaces", lambda n: "_furnace_" in n),
    ("anvils_bars_doors", lambda n: n.endswith(("_anvil", "_anvil_top", "_bars", "_door_bottom", "_door_top"))),
    ("table_crops", lambda n: n.startswith(("emerald_", "onions_", "dead_"))),
]


def sheets(out_dir):
    t = textures()
    out_dir = Path(out_dir)
    paths = []
    for name, keep in GROUPS:
        items = [(n, im) for n, im in sorted(t.items()) if keep(n)]
        paths.append(pix.contact_sheet(items, out_dir / f"batch_c_{name}.png", cols=8))
    tiles = [(n + " 3x3", pix.tiled(im)) for n, im in sorted(t.items()) if n.endswith(TILEABLE) or "furnace_side" in n]
    paths.append(pix.contact_sheet(tiles, out_dir / "batch_c_tiling.png", scale=4, cols=6))
    return paths


def main(argv):
    if argv[:1] == ["write"]:
        t = textures()
        for n, im in t.items():
            pix.save(im, OUT / f"{n}.png")
        print(f"wrote {len(t)} textures")
    elif argv[:1] == ["sheets"]:
        for p in sheets(argv[1] if len(argv) > 1 else pix.ROOT / "docs/art/sheets"):
            print(p)
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
