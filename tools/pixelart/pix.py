"""Pixel-art toolkit for Hardscrabble textures (stdlib + Pillow). Rules: docs/art/STYLE.md.

Library (import with sys.path.insert(0, "tools/pixelart"); import pix):
    img = pix.render([HANDLE, HEAD], digit="iron")          # ASCII grids -> 16x16 RGBA
    pix.save(img, "src/main/resources/assets/hardscrabble/textures/item/iron_mattock.png")
    pix.contact_sheet(paths, "build/art/sheet.png")          # then LOOK at it with the Read tool
    problems = pix.validate(paths)

Grid characters: '.' or ' ' transparent; '0'-'5' shade of the `digit` ramp (the main material);
'a'-'f' shade 0-5 of the `lower` ramp (default wood); 'A'-'F' shade 0-5 of the `upper` ramp (default string);
any other character must be given in `extra` as {char: (r, g, b, a)}.

CLI (from the mod root):
    python -I tools/pixelart/pix.py sheet OUT.png FILE_OR_DIR... [--scale 8] [--cols 8] [--nolabel]
    python -I tools/pixelart/pix.py validate FILE_OR_DIR...
    python -I tools/pixelart/pix.py swatches OUT.png
"""
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from palettes import RAMPS, ramp  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src/main/resources/assets/hardscrabble/textures"
SLOT = (139, 139, 139, 255)      # vanilla inventory slot grey: previews show what the player sees
CHECK = (122, 122, 122, 255)     # checker for transparent pixels on large previews
INK = (24, 24, 28, 255)

# Texture size each vanilla model expects (LayerDefinition.create(mesh, w, h) in 26.3).
MODEL_SIZES = {
    "zombie": (64, 64), "skeleton": (64, 32), "spider": (64, 32), "cave_spider": (64, 32), "wolf": (64, 32),
    "slime": (64, 32), "bat": (32, 32), "silverfish": (64, 32), "creeper": (64, 32), "blaze": (64, 32),
    "humanoid": (64, 32), "humanoid_leggings": (64, 32), "horse_body": (64, 64),
}
MAX_COLOURS = {"item": 16, "block": 32, "entity": 96}
TRANSLUCENT_OK = ("glass", "bottle")  # items allowed partial alpha


# ---------------------------------------------------------------- image basics
def new(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def load(path):
    return Image.open(path).convert("RGBA")


def save(img, path):
    """Always 8-bit RGBA (colour type 6): TexturesTest decodes PNGs assuming 4 bytes per pixel."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGBA").save(path, optimize=True)


def _rows(grid):
    if isinstance(grid, str):
        rows = [r.strip() for r in grid.strip("\n").splitlines()]
        rows = [r for r in rows if r]
    else:
        rows = list(grid)
    assert len({len(r) for r in rows}) == 1, f"ragged grid: {[len(r) for r in rows]}"
    return rows


def draw(img, grid, digit="iron", lower="wood", upper="string", extra=None, offset=(0, 0)):
    """Paint an ASCII grid onto img (in place). Returns img."""
    ramps = {"digit": ramp(digit) if digit else None, "lower": ramp(lower) if lower else None,
             "upper": ramp(upper) if upper else None}
    px = img.load()
    ox, oy = offset
    for y, row in enumerate(_rows(grid)):
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            if extra and ch in extra:
                c = extra[ch]
            elif "0" <= ch <= "5":
                c = ramps["digit"][int(ch)]
            elif "a" <= ch <= "f":
                c = ramps["lower"][ord(ch) - 97]
            elif "A" <= ch <= "F":
                c = ramps["upper"][ord(ch) - 65]
            else:
                raise ValueError(f"unknown grid char {ch!r} at {x},{y}")
            if 0 <= x + ox < img.width and 0 <= y + oy < img.height:
                px[x + ox, y + oy] = c
    return img


def autoshade(grid, mark="#"):
    """First-pass shading of a silhouette, light from the top-left. `mark` pixels become ramp digits:
    edge facing down/right -> 0 (outline), edge facing up/left -> 1 (lit outline), next to a lit edge -> 4,
    next to a dark edge -> 2, otherwise 3; an inner pixel with both its top and left neighbours on the edge -> 5.
    Other characters are kept. Print the result, then hand-polish it (highlights, tips) and keep the literal grid."""
    rows = _rows(grid)
    h, w = len(rows), len(rows[0])
    ins = lambda x, y: 0 <= x < w and 0 <= y < h and rows[y][x] == mark
    edge = {}
    for y in range(h):
        for x in range(w):
            if ins(x, y):
                lit = (not ins(x, y - 1)) + (not ins(x - 1, y))
                dark = (not ins(x, y + 1)) + (not ins(x + 1, y))
                if dark and dark >= lit:
                    edge[x, y] = "0"
                elif lit:
                    edge[x, y] = "1"
    out = [list(r) for r in rows]
    for y in range(h):
        for x in range(w):
            if not ins(x, y):
                continue
            if (x, y) in edge:
                out[y][x] = edge[x, y]
                continue
            up, left = edge.get((x, y - 1)), edge.get((x - 1, y))
            down, right = edge.get((x, y + 1)), edge.get((x + 1, y))
            if up == "1" and left == "1":
                out[y][x] = "5"
            elif "1" in (up, left):
                out[y][x] = "4"
            elif "0" in (down, right):
                out[y][x] = "2"
            else:
                out[y][x] = "3"
    return ["".join(r) for r in out]


def stick(x0, y0, length, grain="ddc"):
    """Diagonal wooden handle (lowercase wood grid) whose core runs up-right from (x0, y0) for `length` pixels:
    each row is [lit edge 'b'][core][shadow edge 'a'], with an end cap below the start. Core cycles through `grain`."""
    g = [["."] * 16 for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = c
    for i in range(length):
        x, y = x0 + i, y0 - i
        put(x - 1, y, "b")
        put(x, y, grain[i % len(grain)])
        put(x + 1, y, "a")
    put(x0 - 1, y0 + 1, "a")
    put(x0, y0 + 1, "a")
    return ["".join(r) for r in g]


def render(grids, digit="iron", lower="wood", upper="string", extra=None, size=(16, 16)):
    """Overlay grids in order (first = bottom) on a new transparent image."""
    img = new(*size)
    one = isinstance(grids, str) or all(isinstance(r, str) and "\n" not in r for r in grids)  # a single list-of-rows grid
    for g in [grids] if one else grids:
        draw(img, g, digit, lower, upper, extra)
    return img


def compose(head, material, handle=None, handle_ramp="wood", accent_ramp="string"):
    """Tool = handle grid (lowercase = wood) under a head grid (digits = material)."""
    return render([g for g in (handle, head) if g], digit=material, lower=handle_ramp, upper=accent_ramp)


LIGHT = (-0.52, -0.62, 0.59)  # toward the light: top-left and in front (image y points down)


def _unit(v):
    n = sum(c * c for c in v) ** 0.5 or 1.0
    return tuple(c / n for c in v)


def relief(parts, size=16, cuts=(0.15, 0.45, 0.72), spec=0.97, glints=2, shadow="2", light=LIGHT):
    """Shade rounded 3D parts into ramp digits, light from the top-left. Each part is f(x, y) -> (z, (nx, ny, nz)) or
    None outside it, sampled at pixel centres; parts are z-buffered (higher z wins). The diffuse term n.L is quantised
    by `cuts` into shades 1-4; per part the `glints` pixels with the strongest half-vector term above `spec` become 5.
    Silhouette edges facing down/right become 0, up/left 1; pixels of a part tucked under another part get `shadow`
    along the occluder's edge (contact shadow), so overlapping bulges read as separate. Returns the rows of digits.
    Works for bumps of 5+ px; smaller shapes (nugget lumps, chain links) read better hand-drawn."""
    L = _unit(light)
    H = _unit((L[0], L[1], L[2] + 1.0))
    zb = [[None] * size for _ in range(size)]
    own = [[-1] * size for _ in range(size)]
    covers = [[set() for _ in range(size)] for _ in range(size)]
    norm = {}
    for i, f in enumerate(parts):
        for y in range(size):
            for x in range(size):
                r = f(x + 0.5, y + 0.5)
                if r is None:
                    continue
                covers[y][x].add(i)
                if zb[y][x] is None or r[0] > zb[y][x]:
                    zb[y][x], own[y][x], norm[x, y] = r[0], i, _unit(r[1])
    g = [["."] * size for _ in range(size)]
    specs = {}
    for (x, y), n in norm.items():
        d = sum(a * b for a, b in zip(n, L))
        g[y][x] = str(1 + sum(d > t for t in cuts))
        s = sum(a * b for a, b in zip(n, H))
        if s > spec:
            specs.setdefault(own[y][x], []).append((s, x, y))
    for lst in specs.values():
        for _, x, y in sorted(lst, reverse=True)[:glints]:
            g[y][x] = "5"

    def state(x, y, i):
        if not (0 <= x < size and 0 <= y < size) or own[y][x] == -1:
            return "out"
        if own[y][x] == i:
            return "self"
        return "front" if i in covers[y][x] else "out"
    out = [r[:] for r in g]
    for (x, y) in norm:
        i = own[y][x]
        down, right, up, left = state(x, y + 1, i), state(x + 1, y, i), state(x, y - 1, i), state(x - 1, y, i)
        if "out" in (down, right):
            out[y][x] = "0"
        elif "out" in (up, left):
            out[y][x] = "1"
        elif "front" in (up, left):
            out[y][x] = min(g[y][x], shadow)
    return ["".join(r) for r in out]


def ellipsoid(cx, cy, rx, ry, z=0.0, depth=1.0):
    """Part for relief(): a bump with an elliptical outline (rock bulge); depth scales its height."""
    def f(x, y):
        u, v = (x - cx) / rx, (y - cy) / ry
        q = u * u + v * v
        if q > 1:
            return None
        w, R = (1 - q) ** 0.5, depth * max(rx, ry)
        return z + R * w, (R * u / rx, R * v / ry, w + 1e-6)
    return f


def tiled(img, n=3):
    """n x n repeat of a block face, to check seams in a contact sheet."""
    out = new(img.width * n, img.height * n)
    for i in range(n * n):
        out.paste(img, ((i % n) * img.width, (i // n) * img.height))
    return out


def overlay(base, top):
    """Alpha-composite top over base (e.g. ore speckles over stone). Returns a new image."""
    out = base.convert("RGBA").copy()
    out.alpha_composite(top.convert("RGBA"))
    return out


# ---------------------------------------------------------------- previews
def _font():
    return ImageFont.load_default()


def _expand(items):
    out = []
    for it in items:
        if isinstance(it, Image.Image) or isinstance(it, tuple):
            out.append(it)
            continue
        p = Path(it)
        out.extend(sorted(p.rglob("*.png")) if p.is_dir() else [p])
    return out


def contact_sheet(items, out, scale=8, label=True, cols=None, bg=SLOT):
    """Labelled nearest-neighbour previews: each cell = big preview (transparent shown as checker) + 1x and 2x copies on
    slot grey. items: paths, directories, PIL images, or (label, image) tuples. Returns the output path."""
    entries = []
    for it in _expand(items):
        if isinstance(it, tuple):
            entries.append(it)
        elif isinstance(it, Image.Image):
            entries.append(("", it.convert("RGBA")))
        else:
            entries.append((Path(it).stem, load(it)))
    if not entries:
        raise ValueError("contact_sheet: nothing to show")
    font = _font()
    big = max(max(im.size) for _, im in entries)
    s = max(1, min(scale, 512 // big))
    cw = max(max(im.width for _, im in entries) * s, 96) + 8
    small_h = max(im.height for _, im in entries) * 2
    ch = max(im.height for _, im in entries) * s + small_h + 8 + (14 if label else 0) + 6
    cols = cols or max(1, min(len(entries), 1600 // cw))
    rows = (len(entries) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw, rows * ch), bg)
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(entries):
        x0, y0 = (i % cols) * cw + 4, (i // cols) * ch + 4
        w, h = im.width * s, im.height * s
        checker = Image.new("RGBA", (w, h), bg)
        cd = ImageDraw.Draw(checker)
        for cy in range(0, h, 8):
            for cx in range(0, w, 8):
                if (cx // 8 + cy // 8) % 2:
                    cd.rectangle([cx, cy, cx + 7, cy + 7], fill=CHECK)
        checker.alpha_composite(im.resize((w, h), Image.NEAREST))
        sheet.paste(checker, (x0, y0))
        sy = y0 + h + 4
        sheet.alpha_composite(im, (x0, sy))                                       # 1x: true in-game inventory size
        sheet.alpha_composite(im.resize((im.width * 2, im.height * 2), Image.NEAREST), (x0 + im.width + 6, sy))
        if label:
            d.text((x0, sy + small_h + 2), name[:28], fill=INK, font=font)
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


def swatches(out, names=None, cell=32):
    """One row per ramp: shade 0 (left) to 5 (right), with the ramp name and hex values."""
    names = names or list(RAMPS)
    font = _font()
    w = 130 + 6 * cell + 6 * 64
    sheet = Image.new("RGBA", (w, len(names) * (cell + 6) + 6), SLOT)
    d = ImageDraw.Draw(sheet)
    for r, name in enumerate(names):
        y = 6 + r * (cell + 6)
        d.text((6, y + cell // 2 - 6), name, fill=INK, font=font)
        for i, c in enumerate(ramp(name)):
            d.rectangle([130 + i * cell, y, 130 + (i + 1) * cell - 1, y + cell - 1], fill=c)
        d.text((130 + 6 * cell + 8, y + cell // 2 - 6), " ".join(RAMPS[name]), fill=INK, font=font)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


# ---------------------------------------------------------------- validation
def entity_models():
    """{texture stem: vanilla model} parsed from MobRenderers.java (the renderer each MITE mob borrows)."""
    src = (ROOT / "src/main/java/dev/mite/entity/mob/client/MobRenderers.java").read_text("utf-8")
    body = src[src.index("registerRenderers(EntityRenderersEvent"):]
    kinds = [("CAVE_SPIDER", "cave_spider"), ("zombie(", "zombie"), ("skeleton(", "skeleton"), ("wolf(", "wolf"),
             ("slime(", "slime"), ("bat(", "bat"), ("silverfish(", "silverfish"), ("spider(", "spider"),
             ("SpiderRenderer", "spider"), ("CreeperRenderer", "creeper"), ("BlazeRenderer", "blaze")]
    out = {}
    # statements: each starts at a registerEntityRenderer / for / Identifier line
    for stmt in re.split(r"\n\s*(?=event\.registerEntityRenderer|for \(|Identifier )", body):
        if "ThrownItemRenderer" in stmt:
            continue
        names = re.findall(r"MobContent\.([A-Z_]+)", stmt)
        model = next((m for key, m in kinds if key in stmt), None)
        if names and model:
            for n in set(names):
                out[n.lower()] = model
    return out


def expected_size(path):
    p = Path(path).as_posix()
    if "/textures/entity/equipment/" in p:
        return MODEL_SIZES[p.split("/textures/entity/equipment/")[1].split("/")[0]]
    if "/textures/entity/" in p:
        model = entity_models().get(Path(p).stem)
        return MODEL_SIZES.get(model) if model else None
    return (16, 16)


def kind(path):
    p = Path(path).as_posix()
    return "entity" if "/textures/entity/" in p else "block" if "/textures/block/" in p else "item"


def validate(items):
    """List of 'path: problem' strings (empty = all good). Errors and WARN-prefixed style warnings."""
    problems = []
    for p in _expand(items):
        p = Path(p)
        try:
            raw = Image.open(p)
        except Exception as e:  # unreadable file
            problems.append(f"{p}: unreadable ({e})")
            continue
        if raw.mode != "RGBA":
            problems.append(f"{p}: mode {raw.mode}, must be 8-bit RGBA (pix.save does it)")
        im = raw.convert("RGBA")
        k = kind(p)
        exp = expected_size(p)
        if k == "block" and im.width == 16 and im.height % 16 == 0:
            pass  # animated strip allowed
        elif exp and im.size != exp:
            problems.append(f"{p}: size {im.size[0]}x{im.size[1]}, expected {exp[0]}x{exp[1]}")
        elif exp is None:
            problems.append(f"WARN {p}: no known model size")
        px = list(im.get_flattened_data())
        opaque = [c for c in px if c[3]]
        if not opaque:
            problems.append(f"{p}: fully transparent")
            continue
        colours = {c for c in opaque}
        if len(colours) > MAX_COLOURS[k]:
            problems.append(f"{p}: {len(colours)} colours > {MAX_COLOURS[k]} for {k} textures")
        if k == "item" and not any(t in p.stem for t in TRANSLUCENT_OK) and any(0 < c[3] < 255 for c in px):
            problems.append(f"{p}: semi-transparent pixels (items use alpha 0 or 255 only)")
        if any(c[:3] == (0, 0, 0) for c in opaque):
            problems.append(f"WARN {p}: pure black pixels (outline with the ramp's shade 0 instead)")
    return problems


def main(argv):
    if len(argv) < 2 or argv[0] not in ("sheet", "validate", "swatches"):
        print(__doc__)
        return 2
    cmd, args = argv[0], argv[1:]
    opt = {}
    rest = []
    it = iter(args)
    for a in it:
        if a in ("--scale", "--cols"):
            opt[a[2:]] = int(next(it))
        elif a == "--nolabel":
            opt["label"] = False
        else:
            rest.append(a)
    if cmd == "sheet":
        print(contact_sheet(rest[1:], rest[0], **opt))
    elif cmd == "swatches":
        print(swatches(rest[0]))
    else:
        probs = validate(rest)
        print("\n".join(probs) or "OK")
        return 1 if any(not s.startswith("WARN") for s in probs) else 0
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
