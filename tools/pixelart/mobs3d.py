"""Skins of the MITE mobs, painted on the box-UV layouts of their own models (geometry: models3d.py; editable Blockbench
sources: docs/art/models/*.bbmodel). Painting helpers in skins.py, rules in docs/art/STYLE.md section 7.

    python -I tools/pixelart/mobs3d.py [ghoul ...]   # write the skins; preview them on the model in Blockbench
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from skins import OFF, OUT as OUT_DIR, SIDES, SKIP, Skin, C, h01  # noqa: E402

import models3d  # noqa: E402  (registers the box-UV layouts of the MITE models)


def ghoul():
    s = Skin("ghoul", "ghoul")
    flesh = ("skull", "jaw", "chest", "hump", "arm R", "arm L", "leg R", "leg L")
    # smooth sallow skin: little pixel noise, a few broad 2x2 bruises, grime darkening towards hands and feet
    for p in flesh:
        s.box(p, "ghoul", base=4 if p in ("skull", "arm R", "arm L") else 3, noise=0.08, cell=2)
    s.blotches(flesh, "ghoul", 2, 0.035)
    s.blotches(("chest", "hump", "arm R", "arm L"), "rotflesh", 2, 0.02, seed=7)
    for p in ("arm R", "arm L", "leg R", "leg L"):
        s.each(p, lambda f, x, y, w, h: C("ghoul", s.shade_of(p, f, x, y, "ghoul") - 1)
               if (y >= h * 2 // 3 or (y >= h * 2 // 3 - 2 and (x + y) % 2 == 0)) and s.shade_of(p, f, x, y, "ghoul") else SKIP)
    cm = {"x": C("ghoul", 0), "b": C("ghoul", 1), "e": C("jelly", 5), "t": C("bone", 4), "s": C("ghoul", 2), "h": C("ghoul", 5)}
    # sunken eye pits with a faint sickly glint, slit nose, stretched cheek seams
    s.grid("skull", "F", ["..b...b.",
                          ".h....h.",
                          ".bb..bb.",
                          ".xe..ex.",
                          ".xx..xx.",
                          "...xx...",
                          ".b....b.",
                          "bx....xb"], cm)
    for f in "RL":
        s.grid("skull", f, ["........", "........", "...b....", "...bb...", "....b...", "........", "......x.", ".....xx."], cm)
    # hanging jaw: a row of teeth on top of the front, dark gullet behind
    s.grid("jaw", "F", ["tttttt", "xtxxtx", ".bxxb."], cm)
    s.grid("jaw", "T", ["xxxxxx", "xxxxxx", "xxxxxx", "xxxxxx", "tttttt"], cm)
    # ribs over a sunken belly, rag loincloth, ridge of the spine on the back
    s.grid("chest", "F", [".......", ".s...s.", "bb.s.bb", ".s.s.s.", "bb.s.bb", ".s.s.s.", ".b.s.b.", "...b..."], cm)
    s.grid("chest", "K", ["...b...", "...s...", "...b...", "...s...", "...b...", "...s...", "...b..."], cm)
    s.hem("chest", "rag", 9, 11, base=2, depth=0)
    s.box("chest", "rag", base=2, only="B")
    for p in ("spine", "spine2", "spine3"):
        s.box(p, "bone", base=3, noise=0.2)
    for p in ("leg R", "leg L"):
        s.hem(p, "rag", 0, 2, base=2, depth=3)
        s.each(p, lambda f, x, y, w, h: C("ghoul", 1) if y == h - 1 else SKIP)
    # dark knuckles and wrists where the claws grow
    for p in ("arm R", "arm L"):
        s.each(p, lambda f, x, y, w, h: C("ghoul", 2) if y >= h - 2 else SKIP)
        s.box(p, "ghoul", base=2, only="B")
    for i in range(6):
        s.each(f"claw{i}", lambda f, x, y, w, h: C("bone", 4 - y), "FRLK")
        s.box(f"claw{i}", "bone", base=1, only="B")
        s.box(f"claw{i}", "bone", base=3, only="T")
    return s.save()


GOLEM_PARTS = ("head", "brow", "jaw", "crest", "torso", "shoulder R", "shoulder L", "back rock", "back rock2", "spike R", "spike R2",
               "spike L", "chest rock", "belly rock", "arm", "arm_l", "fist", "fist_l", "forearm rock", "forearm rock_l",
               "knuckles", "knuckles_l", "leg", "leg_l", "knee", "knee_l")
ROCKS = ("brow", "crest", "back rock", "back rock2", "spike R", "spike R2", "spike L", "chest rock", "belly rock",
         "forearm rock", "forearm rock_l", "knee", "knee_l", "knuckles", "knuckles_l")


def stone_plates(s, part, r="stone", crack="deepslate", seed=0):
    """Fitted stone plates: staggered rows 3-4 px high, each plate its own shade, bevelled (lit top-left edge, dark crack at
    the bottom-right), sparse grit."""
    def fn(f, x, y, w, h):
        rh = 3 + (h01(s.name, seed, part, f, "rh") < 0.5)
        row = y // rh
        shift = int(h01(s.name, seed, part, f, row) * 4)
        cw = 4 + int(h01(s.name, seed, part, f, row, "w") * 3)
        col = (x + shift) // cw
        px, py = (x + shift) % cw, y % rh
        tone = 3 + OFF.get(f, 0) + (-1 if h01(s.name, part, f, row, col) < 0.25 else 1 if h01(s.name, part, f, row, col) > 0.85 else 0)
        if py == rh - 1 or (px == cw - 1 and h01("vj", s.name, part, f, row, col) < 0.7):
            return C(crack, 2 if h01("cr", part, f, x, y) < 0.7 else 3)
        if py == 0 or px == 0:
            tone += 1
        n = h01("grit", s.name, part, f, x, y)
        return C(r, tone + (-1 if n < 0.1 else 1 if n > 0.95 else 0))
    s.each(part, fn, SIDES + "TB")


def earth_elemental():
    s = Skin("earth_elemental", "earth_elemental")
    for i, p in enumerate(GOLEM_PARTS):
        stone_plates(s, p, seed=i)
    for p in ROCKS:   # jutting rocks: rougher, darker boulders
        s.box(p, "stone", base=2, noise=0.3, cell=1)
        s.each(p, lambda f, x, y, w, h: C("stone", 4 + (f == "T")) if y == 0 or (f == "T") else C("deepslate", 2) if y == h - 1 else SKIP)
    # ore veins: gold and copper specks in pairs, a few iron flecks
    for p in GOLEM_PARTS:
        s.each(p, lambda f, x, y, w, h, p=p: C("gold", 4 if (x + y) % 2 else 3) if h01("au", p, f, x // 2, y // 2) < 0.03 and h01("au2", p, f, x, y) < 0.7
               else C("copper", 4) if h01("cu", p, f, x // 2, y // 2) < 0.025 and h01("cu2", p, f, x, y) < 0.6
               else C("iron", 5) if h01("fe", p, f, x, y) < 0.01 else SKIP, SIDES)
    # moss and earth on everything that faces the sky, creeping down the first row
    for p in GOLEM_PARTS:
        s.each(p, lambda f, x, y, w, h, p=p: C("moss", 2 + (h01("m", p, x, y) > 0.55) + (h01("m3", p, x, y) > 0.85))
               if h01("mo", p, x // 2, y // 2) < 0.55 else C("clay", 1) if h01("dirt", p, x, y) < 0.15 else SKIP, "T")
        s.each(p, lambda f, x, y, w, h, p=p: C("moss", 3) if y == 0 and h01("drip", p, f, x) < 0.35 else
               C("moss", 2) if y == 1 and h01("drip", p, f, x) < 0.12 else SKIP, SIDES)
    # glowing core in the chest crack, eyes like molten gold
    cm = {"G": C("gold", 5), "g": C("gold", 3), "x": C("deepslate", 0), "c": C("deepslate", 1)}
    s.grid("head", "F", ["c....c", ".xx.xx", ".xG.Gx", "......", "c.xxx.", "......"], cm)
    s.grid("torso", "F", ["............", ".....c......", "....cGc.....", "...cGGGc....", "....cgc.....", ".....c......"], cm, y0=3)
    for p in ("fist", "fist_l", "knuckles", "knuckles_l"):
        s.each(p, lambda f, x, y, w, h: C("deepslate", 1) if x % 2 == 0 and f == "F" else SKIP)
    return s.save()


def clay_golem():
    s = Skin("earth_elemental", "clay_golem")
    for p in GOLEM_PARTS:
        s.box(p, "clay", base=3, noise=0.05)
    # finger smears and pressed marks
    for p in GOLEM_PARTS:
        s.each(p, lambda f, x, y, w, h, p=p: C("clay", 4 + OFF.get(f, 0) if h01("s", p, f, y) < 0.5 else 2 + OFF.get(f, 0))
               if h01("smear", p, f, (x + int(h01(p, f, y) * 4)) // 3, y) < 0.18 else SKIP, SIDES + "T")
    cm = {"x": C("clay", 0), "h": C("clay", 5), "r": C("clay", 1)}
    s.grid("head", "F", ["......", "hh..hh", "xx..xx", "......", ".xxxx.", "..rr.."], cm)
    s.grid("torso", "F", ["............", "...r....r...", "....rrrr....", ".....rr.....", "....r..r...."], cm, y0=3)
    return s.save()


WRAITH_PARTS = ("head", "hood", "chest", "robe", "arm", "arm_l", "sleeve", "sleeve_l", "leg", "leg_l")


def hood_opening(s, r, rim=4):
    """The hood front is a frame around the face: clear inside, a lit rim on top, darker sides."""
    s.each("hood", lambda f, x, y, w, h: C(r, rim if y == 0 else rim - 2) if y <= 1 or x in (0, w - 1) else None, "F")
    s.each("hood", lambda f, x, y, w, h: None if y == h - 1 and x % 3 == 1 else SKIP, "RLK")
    s.each("hood", lambda f, x, y, w, h: None, "B")


def wraith_robe(s, r, base=3):
    for p in ("hood", "chest", "robe", "sleeve", "sleeve_l"):
        s.box(p, r, base=base, noise=0.1)
    hood_opening(s, r)
    for p in ("robe", "sleeve", "sleeve_l"):   # ragged lower edges
        s.each(p, lambda f, x, y, w, h, p=p: None if y >= h - 2 and h01("rag", s.name, p, f, x) < (0.5 if y == h - 1 else 0.2) else SKIP)
        s.each(p, lambda f, x, y, w, h: None, "B")
    s.each("robe", lambda f, x, y, w, h: C(r, base - 1 + OFF[f]) if x % 3 == 1 and y < h - 2 else SKIP, "FK")


def wight():
    s = Skin("wraith", "wight")
    for p in ("head", "arm", "arm_l", "leg", "leg_l"):
        s.box(p, "wight", base=4, noise=0.12)
    wraith_robe(s, "shroud")
    cm = {"x": C("wight", 0), "b": C("wight", 2), "E": C("diamond", 5), "g": C("diamond", 3), "m": C("wight", 1)}
    s.grid("head", "F", [".......", ".bb.bb.", ".xE.Ex.", ".bg.gb.", "...b...", "..mmm..", "...b..."], cm)
    s.grid("chest", "F", ["..bggb..", "...gg..."], {"b": C("shroud", 5), "g": C("wight", 3)})
    for p in ("arm", "arm_l"):
        s.each(p, lambda f, x, y, w, h: C("wight", 5 if y == h - 1 else 3) if y >= h - 2 and x % 2 == 1 else SKIP)
    return s.save()


def shadow():
    s = Skin("wraith", "shadow")
    for p in WRAITH_PARTS:
        s.box(p, "shade", base=2, noise=0.2)
    hood_opening(s, "shade", rim=3)
    for p in WRAITH_PARTS:   # rising wisps
        s.each(p, lambda f, x, y, w, h, p=p: C("shade", 5 if h01("w", p, f, x, y) < 0.3 else 4)
               if h01("wisp", p, f, x, (y + x) // 3) < 0.11 else SKIP, SIDES + "T")
    cm = {"E": C("lava", 5), "e": C("lava", 3), "x": C("shade", 0), "m": C("shade", 0)}
    s.grid("head", "F", [".......", ".xx.xx.", ".EE.EE.", ".xe.ex.", ".......", "..mmm..", "......."], cm)
    for p in ("robe", "leg", "leg_l"):   # the hem dissolves into smoke
        s.each(p, lambda f, x, y, w, h: None if y >= h // 2 and h01("fade", s.name, f, x, y) < (y - h // 2 + 1) / h * 1.6 else SKIP)
        s.each(p, lambda f, x, y, w, h: None, "B")
    return s.save()


def invisible_stalker():
    s = Skin("wraith", "invisible_stalker")
    for p in WRAITH_PARTS:
        s.box(p, "mist", base=3, noise=0.1, cell=2, ao=False)
    hood_opening(s, "mist")
    s.grid("head", "F", [".......", ".......", ".xE.Ex.", ".......", ".......", "...x...", "......."], {"x": C("mist", 1), "E": C("diamond", 4)})
    for p in WRAITH_PARTS:   # faint: a lattice of mist with holes
        s.each(p, lambda f, x, y, w, h, p=p: None if ((x + y) % 2 == 0 or h01("holes", p, f, x, y) < 0.2)
               and not (p == "head" and f == "F" and 1 <= y <= 5) else SKIP, SIDES + "TB")
    return s.save()


def revenant():
    s = Skin("revenant", "revenant")
    for p in ("head", "jaw", "arm", "arm_l"):
        s.box(p, "rotflesh", noise=0.15, cell=2)
    cm = {"x": C("rotflesh", 0), "b": C("rotflesh", 1), "R": C("lava", 4), "t": C("bone", 3), "i": C("iron", 1)}
    s.grid("head", "F", ["iiiiiiii", "iiiiiiii", ".xx..xx.", ".xR..Rx.", ".b.ii.b.", "...ii...", "........", "........"], cm)
    s.grid("jaw", "F", ["tttttt", "xtxxtx"], cm)
    # helm: iron cap over the skull with a nose guard, red crest
    s.each("head", lambda f, x, y, w, h: C("iron", (4 if y == 0 else 3) + OFF.get(f, 0)) if y <= 2 else SKIP, "RLK")
    s.box("head", "iron", base=4, only="T", noise=0.05)
    s.box("crest", "lava", base=2, noise=0.2)
    # chain torso, rusted breastplate, belt; tattered cape
    s.each("chest", lambda f, x, y, w, h: C("iron", 3 + OFF[f] if (x + y) % 2 else 1) if y <= 8 else C("rag", 2 + OFF[f]))
    s.box("chest", "iron", base=3, only="T", noise=0)
    s.each("chest", lambda f, x, y, w, h: C("iron", (5 if x == 1 and y == 1 else 4 if x <= 2 else 3) - (y == 6)) if 1 <= x <= 6 and 1 <= y <= 6 else SKIP, "F")
    s.box("chest", "rag", base=1, rows=(8, 8), noise=0, ao=False)
    s.set("chest", "F", 3, 8, C("gold", 4))
    s.box("cape", "rag", base=1, noise=0.15)
    s.each("cape", lambda f, x, y, w, h: C("lava", 1) if f in "FK" and x in (0, w - 1) else SKIP)
    s.each("cape", lambda f, x, y, w, h: None if y >= h - 4 and h01("tear", f, x) < (y - h + 5) * 0.22 else SKIP)
    for p in ("pauldron", "pauldron_l"):
        s.box(p, "iron", base=3, noise=0.05)
        s.each(p, lambda f, x, y, w, h: C("iron", 5) if y == 0 else C("iron", 1) if y == h - 1 else SKIP)
    for p in ("arm", "arm_l"):
        s.each(p, lambda f, x, y, w, h: C("iron", 3 + OFF[f] if (x + y) % 2 else 1) if y <= 6 else SKIP)
    for p in ("leg", "leg_l"):
        s.box(p, "rag", base=2, rows=(0, 5))
        s.each(p, lambda f, x, y, w, h: C("iron", (4 if y == 6 else 3) + OFF[f]) if 6 <= y <= 9 else C("rag", 1 if y == 10 else 0) if y >= 10 else SKIP)
        s.box(p, "rag", base=1, only="B")
    for p in ("head", "chest", "pauldron", "pauldron_l", "leg", "leg_l"):   # rust streaks on the plates
        s.each(p, lambda f, x, y, w, h, p=p: C("rusted_iron", 3 if h01("rs", p, f, x, y) < 0.5 else 2)
               if s.shade_of(p, f, x, y, "iron") is not None and h01("rust", p, f, x, y // 2) < 0.12 else SKIP, SIDES + "T")
    return s.save()


BONES = ("skull", "jaw", "spine", "shoulders", "rib1", "rib2", "rib3", "pelvis", "arm", "arm_l", "leg", "leg_l")


def skeleton3d(s, r, eye=None, seed=0):
    for p in BONES:
        s.box(p, r, base=3, noise=0.12, seed=seed)
    cm = {"x": C(r, 0), "b": C(r, 1), "E": eye or C(r, 0), "h": C(r, 5)}
    s.grid("skull", "F", ["........", ".h......", ".bb..bb.", ".xx..xx.", ".xE..Ex.", "...xx...", ".bxbxbx."], cm)
    s.grid("jaw", "F", ["bxbxbx", ".bbbb."], cm)
    for p in ("arm", "arm_l", "leg", "leg_l"):   # knuckles of the joints
        s.each(p, lambda f, x, y, w, h: C(r, 5 if y == 0 else 4) if y in (0, 6) else C(r, 1) if y in (5, 7) and x == 1 else SKIP)
    for p in ("rib1", "rib2", "rib3"):
        s.each(p, lambda f, x, y, w, h: C(r, 1) if f == "F" and x in (w // 2 - 1, w // 2) else SKIP)
    return cm


def longdead():
    s = Skin("longdead", "longdead")
    skeleton3d(s, "bone")
    s.blotches(BONES, "bone", 2, 0.05, seed=4)
    return s.save()


def longdead_guardian():
    s = Skin("longdead", "longdead_guardian")
    skeleton3d(s, "bone", eye=C("diamond", 2), seed=1)
    m = "ancient_metal"
    # half helm with a crest, breastplate on the ribs, plated shoulders
    s.each("skull", lambda f, x, y, w, h: C(m, (4 if y == 0 else 3) + OFF[f] // 2) if y <= 1 or (f != "F" and y <= 3)
           or (f == "F" and x in (3, 4) and y <= 3) else SKIP, SIDES)
    s.box("skull", m, base=3, only="T", noise=0.1)
    s.each("skull", lambda f, x, y, w, h: C("royal", 4 if x in (3, 4) else 3) if 2 <= x <= 5 else SKIP, "T")
    for p in ("shoulders", "rib1", "rib2"):
        s.box(p, m, base=3, noise=0.05)
    s.set("rib1", "F", 3, 0, C("diamond", 4))
    for p in ("arm", "arm_l", "leg", "leg_l"):
        s.each(p, lambda f, x, y, w, h: C(m, 3 + OFF[f] // 2) if 7 <= y <= 9 else SKIP)
    return s.save()


LORD = BONES + ("crown", "spike1", "spike2", "spike3", "spike4", "spike5", "mantle", "robe", "cuff", "cuff_l")


def lord(s, bone, eye, robe, trim, gem, seed):
    skeleton3d(s, bone, eye=eye, seed=seed)
    for p in ("crown", "spike1", "spike2", "spike3", "spike4", "spike5"):
        s.box(p, trim, base=3, noise=0.1)
    s.each("crown", lambda f, x, y, w, h: C(gem, 4) if f == "F" and x in (4, 5) and y == 0 else C(trim, 4) if y == 0 else SKIP)
    s.each("crown", lambda f, x, y, w, h: None, "TB")
    for p in ("mantle", "robe", "cuff", "cuff_l"):
        s.box(p, robe, base=3, noise=0.12)
    s.each("mantle", lambda f, x, y, w, h: C(trim, 4 if y == 0 else 3) if y == 0 or y == h - 1 else SKIP)
    s.each("robe", lambda f, x, y, w, h: None if y >= h - 2 and h01("hem", s.name, f, x) < (0.5 if y == h - 1 else 0.2) else
           C(trim, 3) if f == "F" and x in (1, w - 2) else SKIP)
    s.each("robe", lambda f, x, y, w, h: None, "B")


def bone_lord():
    s = Skin("bone_lord", "bone_lord")
    lord(s, "old_bone", C("lava", 5), "royal", "gold", "lava", 2)
    return s.save()


def ancient_bone_lord():
    s = Skin("bone_lord", "ancient_bone_lord")
    lord(s, "dark_bone", C("diamond", 5), "obsidian", "ancient_metal", "emerald", 3)
    return s.save()


WOLF = ("skull", "snout", "ear R", "ear L", "body", "mane", "ridge", "right_hind", "left_hind", "right_front", "left_front", "tail")


def wolf3d(s, r, belly, eye, base=3):
    for p in WOLF:
        s.box(p, r, base=base, noise=0.12, cell=1)
    for p in ("body", "mane"):   # lighter belly and throat
        s.box(p, belly, base=4, noise=0.15, only="B", ao=False)
    s.box("mane", belly, base=4, noise=0.2, only="F")
    for p in ("mane", "ridge"):   # shaggy tufts
        s.each(p, lambda f, x, y, w, h, p=p: C(r, base + 1) if (x + y) % 3 == 0 and h01("ruff", p, f, x, y) < 0.6 else
               C(r, base - 1) if h01("ruff2", p, f, x, y) < 0.15 else SKIP, SIDES + "T")
        s.each(p, lambda f, x, y, w, h, p=p: C(r, 0) if y == h - 1 and f in SIDES and h01("tuft", p, f, x) < 0.4 else SKIP)   # tuft shadows
    cm = {"x": C(r, 0), "E": eye, "b": C(r, 1), "h": C(r, base + 2)}
    s.grid("skull", "F", [".......", ".bh.hb.", ".xE.Ex.", ".......", ".......", "......."], cm)
    s.grid("snout", "F", [".xx.", "....", "xxxx"], {"x": C(r, 0)})
    s.box("snout", belly, base=4, only="B", noise=0.1)
    s.set("snout", "T", 1, 0, C("widow", 1))
    s.set("snout", "T", 2, 0, C("widow", 1))
    for p in ("fang R", "fang L"):
        s.box(p, "bone", base=5, noise=0)
    for p in ("right_hind", "left_hind", "right_front", "left_front"):
        s.each(p, lambda f, x, y, w, h: C(r, 1) if y == h - 1 else SKIP)
        s.box(p, r, base=1, only="B", noise=0)
    s.each("tail", lambda f, x, y, w, h: C(belly, 4) if y >= h - 3 else SKIP)
    for p in ("ear R", "ear L"):
        s.each(p, lambda f, x, y, w, h: C(r, 0) if y >= 1 and x == 0 else SKIP, "F")
    return cm


def dire_wolf():
    s = Skin("dire_wolf", "dire_wolf")
    wolf3d(s, "direwolf", "direwolf", C("gold", 4), base=2)
    s.each("skull", lambda f, x, y, w, h: C("direwolf", 1) if x in (3,) else SKIP, "T")   # dark forehead stripe
    s.each("ridge", lambda f, x, y, w, h: C("direwolf", 1) if x in (1, 2) else SKIP, "T")
    return s.save()


def hellhound():
    s = Skin("dire_wolf", "hellhound")
    wolf3d(s, "char", "char", C("lava", 5))
    for p in WOLF:   # ember veins
        s.each(p, lambda f, x, y, w, h, p=p: C("lava", 2 if h01(p, f, x, y) < 0.5 else 3)
               if h01("vein", p, f, (x + y) // 2, (x - y) // 3) < 0.07 else SKIP, SIDES + "T")
    s.each("ridge", lambda f, x, y, w, h: C("lava", 3 + (x + y) % 2), "T")   # smouldering ridge
    s.each("tail", lambda f, x, y, w, h: C("lava", 4 if y == h - 1 else 3) if y >= h - 3 else SKIP)
    s.grid("snout", "F", ["....", "....", "tLLt"], {"t": C("bone", 4), "L": C("lava", 4)})
    return s.save()


SPIDER_BODY = ("head", "thorax", "abdomen", "hump", "spinneret")


def spider3d(s, r, base=2, noise=0.15):
    for p in SPIDER_BODY + ("leg", "spike", "fang R", "fang L"):
        s.box(p, r, base=base, noise=noise)
    # cluster of eyes, pale fangs, leg joints and dark tips
    s.grid("head", "F", ["..x.x..", ".xExEx.", "..x.x..", ".......", ".......", "......."], {"x": C(r, 0), "E": C(r, base + 3)})
    for p in ("fang R", "fang L"):
        s.box(p, "bone", base=4, noise=0)
        s.each(p, lambda f, x, y, w, h: C(r, 0) if y == h - 1 else SKIP)
    s.each("leg", lambda f, x, y, w, h: C(r, base + 2 + OFF[f] // 2) if x in (5, 10) else C(r, 0) if x >= 14 else SKIP, "TFKB")
    s.box("spike", r, base=base + 2, noise=0)


def phase_spider():
    s = Skin("spider", "phase_spider")
    spider3d(s, "phase", base=3)
    for p in SPIDER_BODY:   # shimmering cyan bands (phasing)
        s.each(p, lambda f, x, y, w, h, p=p: C("diamond", 4 if h01("g", p, f, x, y) < 0.3 else 2)
               if (x + y) % 4 == 0 and h01("band", p, f, (x - y) // 3) < 0.35 else SKIP, SIDES + "T")
    s.each("leg", lambda f, x, y, w, h: C("diamond", 2) if x in (3, 8, 13) else SKIP, "TF")
    s.set("head", "F", 2, 1, C("diamond", 5))
    s.set("head", "F", 4, 1, C("diamond", 5))
    return s.save()


def black_widow():
    s = Skin("spider", "black_widow")
    spider3d(s, "widow", base=2, noise=0.08)
    w, d = s.size("abdomen", "B")
    hour = ["...rrrr...", "....rr....", ".....r....", "....rr....", "...rrRr...", "...rrrr..."]
    s.grid("abdomen", "B", hour, {"r": C("royal", 4), "R": C("royal", 5)}, x0=(w - 10) // 2, y0=(d - 6) // 2)
    s.each("hump", lambda f, x, y, w, h: C("widow", 5) if (x, y) in ((2, 2), (3, 2), (2, 3)) else SKIP, "T")   # gloss
    s.grid("hump", "T", ["........", "........", "........", "...rr...", "...rr..."], {"r": C("royal", 4)})
    return s.save()


def demon_spider():
    s = Skin("spider", "demon_spider")
    spider3d(s, "demon", base=2)
    for p in SPIDER_BODY:   # glowing cracks
        s.each(p, lambda f, x, y, w, h, p=p: C("lava", 3 if h01(p, f, x, y) < 0.6 else 4)
               if h01("crack", p, f, (x + y) // 2, y // 3) < 0.12 and (x + y) % 2 == 0 else SKIP, SIDES + "T")
    s.grid("hump", "T", [".x....x.", ".xx..xx.", "..xxxx..", ".xLxxLx.", "..xxxx..", "..x.x.x.", "........", "........"],
           {"x": C("demon", 0), "L": C("lava", 5)})
    s.each("spike", lambda f, x, y, w, h: C("lava", 3 + (y == 0)))
    s.set("head", "F", 2, 1, C("lava", 5))
    s.set("head", "F", 4, 1, C("lava", 5))
    return s.save()


def wood_spider():
    s = Skin("spider", "wood_spider")
    spider3d(s, "wood", base=2, noise=0.1)
    for p in SPIDER_BODY:   # bark grain and moss on the back
        s.each(p, lambda f, x, y, w, h, p=p: C("wood", 1 + OFF.get(f, 0) // 2) if (x + int(h01(p, f, y // 3) * 3)) % 3 == 0 and f != "B" else SKIP,
               SIDES + "T")
    for p in ("abdomen", "hump"):
        s.each(p, lambda f, x, y, w, h: C("moss", 4 if h01("m2", x, y) < 0.3 else 3) if h01("moss", x // 2, y // 2) < 0.35 else SKIP, "T")
    s.each("leg", lambda f, x, y, w, h: C("wood", 5) if x % 5 == 2 else SKIP, "TF")
    return s.save()


def infernal_creeper():
    s = Skin("infernal_creeper", "infernal_creeper")
    parts = ("head", "body", "leg", "horn R", "horn L", "claw")
    for p in parts:
        s.box(p, "char", base=3, noise=0.15, cell=2)
        s.each(p, lambda f, x, y, w, h, p=p: C("lava", 2 if h01(p, f, x, y) < 0.5 else 1)
               if h01("fis", p, f, (x + y) // 2, y // 2) < 0.06 else SKIP, SIDES + "T")
    s.grid("head", "F", ["........", ".x....x.", ".xx..xx.", ".LW..WL.", "...xx...", "..xllx..", "..xllx..", "..x..x.."],
           {"x": C("char", 0), "L": C("lava", 4), "W": C("lava", 5), "l": C("lava", 2)})
    for p in ("shard1", "shard2", "shard3"):
        s.each(p, lambda f, x, y, w, h: C("lava", 5 - min(3, y)) if (x + y) % 3 else C("lava", 2), SIDES + "TB")
    for p in ("horn R", "horn L"):
        s.each(p, lambda f, x, y, w, h: C("bone", 1 + y) if f in SIDES else SKIP)
    s.box("claw", "bone", base=2, noise=0)
    s.each("leg", lambda f, x, y, w, h: C("char", 0) if y == h - 1 and x % 2 == 0 else SKIP)
    return s.save()


def fire_elemental():
    s = Skin("fire_elemental", "fire_elemental")
    s.box("head", "lava", base=2, noise=0.25, cell=1)
    s.each("head", lambda f, x, y, w, h: C("lava", 4 if y < 2 else 3) if h01("flick", f, x, y // 2) < 0.35 else SKIP, SIDES)
    s.grid("head", "F", ["........", "........", ".00..00.", ".05..50.", "........", "...00...", "..0110..", "........"],
           {"0": C("lava", 0), "1": C("lava", 1), "5": C("lava", 5)})
    s.box("head", "lava", base=4, only="T", noise=0.3)
    for p in ("flame1", "flame2", "flame3", "tip"):
        s.each(p, lambda f, x, y, w, h: C("lava", 5 if y < h // 2 else 4), SIDES + "TB")
    # molten core: dark crust with bright seams; rods white-hot at the top cooling to deep red
    for p in ("core", "ember"):
        s.box(p, "lava", base=1, noise=0.2)
        s.each(p, lambda f, x, y, w, h, p=p: C("lava", 4 + (h01(p, x, y) < 0.3)) if h01("seam", p, f, (x + y) // 2, y // 2) < 0.3 else SKIP)
    s.each("rod", lambda f, x, y, w, h: C("lava", 5 - y * 4 // 8 - (1 if (x + y) % 3 == 0 else 0)), SIDES)
    s.box("rod", "lava", base=5, only="T", noise=0)
    s.box("rod", "lava", base=2, only="B", noise=0)
    return s.save()


def cube(name, r):
    s = Skin("gelatinous_cube", name)
    s.box("shell", r, base=3, noise=0.12, alpha=150, ao=False)
    s.each("shell", lambda f, x, y, w, h: C(r, 5, 170) if (x == 0 or y == 0) and h01("rim", f, x, y) < 0.6 else
           C(r, 1, 170) if (x == w - 1 or y == h - 1) else SKIP, "TFRLKB")
    s.box("core", r, base=2, noise=0.25)
    s.each("core", lambda f, x, y, w, h: C(r, 5) if h01(name, "bub", f, x, y) < 0.1 else SKIP, "TFRLKB")
    s.box("skull", "bone", base=3, noise=0.1)
    s.grid("skull", "F", ["xx", ".."], {"x": C("bone", 0)})
    for p in ("bone1", "bone2"):
        s.box(p, "bone", base=3, noise=0.1)
    for e in ("eye R", "eye L"):
        s.box(e, r, base=0, noise=0, ao=False)
        s.set(e, "F", 0, 0, C(r, 5))
    s.box("mouth", r, base=0, noise=0, ao=False)
    return s.save()


def jelly():
    return cube("jelly", "jelly")


def blob():
    return cube("blob", "blob")


def ooze():
    return cube("ooze", "ooze")


def pudding():
    return cube("pudding", "pudding")


def bat(name, fur, wing, eye, fang=True):
    s = Skin("bat", name)
    for p in ("body", "head", "snout", "ruff"):
        s.box(p, fur, base=3, noise=0.18)
    s.each("ruff", lambda f, x, y, w, h: C(fur, 4) if (x + y) % 2 == 0 else SKIP)
    for p in ("ear R", "ear L"):
        s.box(p, fur, base=3, noise=0.1, ao=False)
        s.each(p, lambda f, x, y, w, h: C(wing, 4) if x == 1 and 1 <= y <= h - 2 else None if y == 0 and x != 1 else SKIP, "FK")
    s.grid("head", "F", ["E..E", "....", "...."], {"E": eye})
    s.grid("snout", "F", ["xx", "ff" if fang else ".."], {"x": C(fur, 0), "f": C("bone", 5)})
    for p in ("wing R", "wing L"):
        s.box(p, wing, base=2, noise=0.12, ao=False)
        s.each(p, lambda f, x, y, w, h: C(fur, 2) if y == 0 or x == 0 else SKIP, "FK")
    for p in ("wing tip R", "wing tip L"):
        s.box(p, wing, base=3, noise=0.12, ao=False)
        def wfn(f, x, y, w, h):   # finger bones fanning out, scalloped trailing edge
            xx = x if f == "F" else w - 1 - x
            if y == 0 or (xx == y and xx < 7) or (y > 2 and xx in (3, 6) and y < h - 1):
                return C(fur, 2)
            if y == h - 1 and xx % 2 == 0 or (y == h - 2 and xx in (1, 5)):
                return None
            return SKIP
        s.each(p, wfn, "FK")
    s.box("feet", fur, base=1, noise=0, ao=False)
    s.each("feet", lambda f, x, y, w, h: C("bone", 2) if y == 1 and x != 1 else None if x == 1 else SKIP, "FK")
    return s


def vampire_bat():
    return bat("vampire_bat", "batfur", "bat_red", C("lava", 4)).save()


def giant_vampire_bat():
    s = bat("giant_vampire_bat", "bat_red", "char", C("jelly", 5))
    s.each("body", lambda f, x, y, w, h: C("bat_red", 1) if y == 0 else SKIP, "FRLK")    # dark collar
    return s.save()


def nightwing():
    s = bat("nightwing", "shade", "phase", C("diamond", 5), fang=False)
    for p in ("wing tip R", "wing tip L"):   # star specks
        s.each(p, lambda f, x, y, w, h, p=p: C("phase", 4) if h01("st", p, f, x, y) < 0.05 else SKIP, "FK")
    return s.save()


SEGS = tuple(f"seg{i}" for i in range(7))
SCALES = ("scales0", "scales1", "scales2")
SPINES = ("spine0", "spine1", "spine2", "spine3")


def fish(name, body, scale, accent, seed=0):
    s = Skin("silverfish", name)
    for p in SEGS + ("mandible R", "mandible L"):
        s.box(p, body, base=3, noise=0.2, ao=False, seed=seed)
        s.each(p, lambda f, x, y, w, h: C(body, 1) if (f in "RL" and x == w - 1) or (f in "TB" and y == h - 1) else SKIP, "TRLB")
    for p in SCALES:
        s.box(p, scale, base=3, noise=0.2, ao=False, seed=seed)
        s.each(p, lambda f, x, y, w, h: C(scale, 1) if y == h - 1 else SKIP, SIDES)
    for p in SPINES:
        s.each(p, lambda f, x, y, w, h: C(scale, 5 - y), SIDES + "TB")
    s.grid("seg0", "F", ["E.E", "..."], {"E": accent})
    return s


def copperspine():
    s = fish("copperspine", "iron", "copper", C("lava", 4))
    for p in SCALES:   # spines with verdigris tips
        s.each(p, lambda f, x, y, w, h: C("copper", 5) if x % 2 == 0 and y == 0 else C("emerald", 3) if x % 2 == 0 and y == 1 and h01(f, x) < 0.4 else SKIP, SIDES)
        s.each(p, lambda f, x, y, w, h: C("copper", 5 if x % 2 == 0 else 2), "T")
    for p in SPINES:
        s.each(p, lambda f, x, y, w, h: C("emerald", 4) if y == 0 else SKIP, SIDES)
    return s.save()


def netherspawn():
    s = fish("netherspawn", "netherrack", "char", C("lava", 5))
    for p in SEGS + SCALES:   # glowing joints: it explodes
        s.each(p, lambda f, x, y, w, h: C("lava", 3) if (f in "RL" and x == w - 1) or (f == "T" and y == h - 1) else SKIP, "TRL")
    for p in SPINES:
        s.each(p, lambda f, x, y, w, h: C("lava", 5 - y), SIDES + "T")
    return s.save()


def hoary_silverfish():
    s = fish("hoary_silverfish", "hoary", "hoary", C("royal", 4))
    for p in SCALES:   # frosty tufts
        s.each(p, lambda f, x, y, w, h: C("hoary", 5) if y <= 1 and h01("fr", p, f, x, y) < 0.6 else SKIP, SIDES)
        s.box(p, "hoary", base=5, only="T", noise=0.2)
    return s.save()


def dire_wolf_collar():
    """Collar ring around the neck end of the mane, light grey (tinted with the dye colour), with a tag ring under the throat.
    Box UV: the front (head) edge is the last columns of R, the first of L and the last rows of T and B."""
    s = Skin("dire_wolf", "dire_wolf_collar")
    s.each("mane", lambda f, x, y, w, h: (200 + 20 * (y == 0), ) * 3 + (255,) if (f in "RL" and (x >= w - 2 if f == "R" else x <= 1))
           or (f in "TB" and y >= h - 2) else SKIP, "RLTB")
    s.each("mane", lambda f, x, y, w, h: (150, 150, 150, 255) if x == w // 2 and y >= h - 2 else SKIP, "F")
    return s.save()


ARMOR_PARTS = models3d.ARMOR_PARTS


def _armor_cell(p, f, x, y, w, h):
    """Kind of a wolf armour pixel, like the vanilla armour: plate bands across the spine (seam every 3rd row), a dark rim
    along the lower edge; the legs are covered on their upper half only. None = bare (belly, lower legs)."""
    if f == "B":
        return None
    if p.endswith(("hind", "front")):
        if f == "T":
            return "plate"
        return None if y > 4 else "rim" if y == 4 else "seam" if y == 2 else "plate"
    if f in "RL" and y == h - 1:
        return "rim"
    t = y if f == "T" else x if f in "RL" else None   # along the spine; front and back faces are one plate
    if t is None:
        return "rim" if x in (0, w - 1) or y in (0, h - 1) else "plate"
    return "seam" if t % 3 == 2 else "edge" if t % 3 == 0 else "plate"


def dire_wolf_armor():
    """Armadillo scute wolf armour over the mane, back, ridge and upper legs; head, belly, paws and tail stay bare."""
    s = Skin("dire_wolf", "dire_wolf_armor")
    shade = {"seam": 1, "rim": 1, "edge": 4, "plate": 3}
    for p in ARMOR_PARTS:
        def fn(f, x, y, w, h, p=p):
            k = _armor_cell(p, f, x, y, w, h)
            if k is None:
                return SKIP
            n = h01("sc", p, f, x, y)
            return C("scute", shade[k] + OFF[f] // 2 + (-1 if k == "plate" and n < 0.12 else 0))
        s.each(p, fn, SIDES + "TB")
    return s.save()


def dire_wolf_armor_overlay():
    """Dyeable part of the wolf armour: the plates (not the seams and rim) in greys, tinted by the dye colour."""
    s = Skin("dire_wolf", "dire_wolf_armor_overlay")
    grey = {"edge": 235, "plate": 200}
    for p in ARMOR_PARTS:
        def fn(f, x, y, w, h, p=p):
            k = _armor_cell(p, f, x, y, w, h)
            if k not in grey:
                return SKIP
            v = grey[k] + OFF[f] * 18 - (20 if h01("sc", p, f, x, y) < 0.12 else 0)
            return (v, v, v, 255)
        s.each(p, fn, SIDES + "TB")
    return s.save()


def spider_eyes(name, eye):
    """Glow layer of a spider: only the eye pixels of the head front (drawn full bright by an eyes render type)."""
    s = Skin("spider", f"{name}_eyes")
    s.grid("head", "F", ["..x.x..", ".xExEx.", "..x.x.."], {"x": eye[:3] + (170,), "E": eye})
    return s.save()


def spider_glows():
    for name, eye in (("phase_spider", C("diamond", 5)), ("black_widow", C("royal", 5)), ("demon_spider", C("lava", 5)),
                      ("wood_spider", C("gold", 4))):
        spider_eyes(name, eye)
    return spider_eyes("phase_spider", C("diamond", 5))


def dire_wolf_armor_cracks():
    """Damage overlays of the dire wolf armour (low, medium, high), like vanilla: cracks only over the scute plates, the
    higher levels keep the lower ones and add more and longer ones."""
    from PIL import Image
    armor = Image.open(OUT_DIR / "dire_wolf_armor.png").convert("RGBA").load()
    out = None
    for level, (density, length) in enumerate(((0.012, 3), (0.022, 4), (0.035, 5))):
        s = Skin("dire_wolf", f"dire_wolf_armor_crackiness_{('low', 'medium', 'high')[level]}")
        for p in ARMOR_PARTS:
            for f, (fx, fy, fw, fh) in s.faces(p).items():
                if f == "B":
                    continue
                for k in range(max(1 if level else 0, round(fw * fh * density))):
                    x, y = int(h01("cx", p, f, k) * fw), int(h01("cy", p, f, k) * fh)
                    for step in range(length):
                        if 0 <= x < fw and 0 <= y < fh and armor[fx + x, fy + y][3]:
                            s.px[fx + x, fy + y] = (30, 17, 10, 255)
                            if x + 1 < fw and armor[fx + x + 1, fy + y][3] and s.px[fx + x + 1, fy + y][3] == 0 and h01("lit", p, f, x, y) < 0.5:
                                s.px[fx + x + 1, fy + y] = (245, 228, 200, 230)   # lit edge of the crack
                        d = h01("cd", p, f, k, step)
                        x, y = x + (1 if d < 0.45 else -1 if d < 0.9 else 0), y + (1 if d > 0.25 else 0)
        out = s.save()
    return out


MOBS = [dire_wolf_armor_cracks, dire_wolf_collar, dire_wolf_armor, dire_wolf_armor_overlay, spider_glows, vampire_bat, giant_vampire_bat, nightwing, copperspine, netherspawn, hoary_silverfish, jelly, blob, ooze, pudding, infernal_creeper, fire_elemental, phase_spider, black_widow, demon_spider, wood_spider, dire_wolf, hellhound, ghoul, earth_elemental, clay_golem, wight, shadow, invisible_stalker, revenant, longdead, longdead_guardian, bone_lord,
        ancient_bone_lord]

if __name__ == "__main__":
    want = set(sys.argv[1:])
    for fn in MOBS:
        if not want or fn.__name__ in want:
            print(fn().name)
