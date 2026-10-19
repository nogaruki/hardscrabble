"""Curse HUD icons (EF-UI-08, gui atlas): a 9x9 obsidian-purple badge with a 7x7 milk glyph, "?" while the curse is unknown.
Run from the mod root: python -I tools/pixelart/curse_icons.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
from palettes import ramp  # noqa: E402

OUT = pix.TEXTURES / "gui/sprites/hud/curse"
BORDER, FILL = ramp("obsidian")[1], ramp("obsidian")[3]
GLYPH = ramp("milk")[4]

# One glyph per curse (Curse enum names, lower case), 7 rows of 7: w = glyph, . = badge.
GLYPHS = {
    "unknown": "..www..|.w...w.|.....w.|....w..|...w...|.......|...w...",
    "equipment_decay": "w......|.w...w.|..w.w..|...w...|..w.w..|.w...w.|w....w.",
    "cant_hold_breath": "..www..|.w...w.|w.w.w.w|w..w..w|w.w.w.w|.w...w.|..www..",
    "cant_run": ".......|wwwwwww|.wwwww.|..www..|..www..|.wwwww.|wwwwwww",
    "cant_eat_animals": ".....ww|....www|...www.|..www..|.www...|w.w....|ww.....",
    "cant_eat_plants": "....www|..wwwww|.www.ww|.ww.www|.w.www.|w.www..|w......",
    "cant_drink": "..www..|...w...|...w...|..www..|.wwwww.|.wwwww.|..www..",
    "endermen_enemy": ".......|.......|www.www|w.w.w.w|www.www|.......|.......",
    "clumsiness": "...w...|...w...|...w...|w..w..w|.w.w.w.|..www..|...w...",
    "entanglement": "w.w.w.w|.w.w.w.|w.w.w.w|.w.w.w.|w.w.w.w|.w.w.w.|w.w.w.w",
    "cant_wear_armor": "ww...ww|wwwwwww|.wwwww.|.wwwww.|.wwwww.|.wwwww.|.......",
    "cant_open_chests": ".......|wwwwwww|w.....w|www.www|w..w..w|w.....w|wwwwwww",
    "cant_sleep": ".......|..www..|.w...w.|w..w..w|.w...w.|..www..|.......",
    "fear_of_spiders": "w.....w|.w.w.w.|..www..|wwwwwww|..www..|.w.w.w.|w.....w",
    "fear_of_wolves": "w.....w|ww...ww|wwwwwww|w.www.w|.wwwww.|..www..|...w...",
    "fear_of_creepers": ".......|ww...ww|ww...ww|...w...|..www..|..w.w..|.......",
    "fear_of_undead": ".wwwww.|wwwwwww|w..w..w|wwwwwww|.wwwww.|.w.w.w.|.......",
}


def badge(art):
    rows = art.split("|")
    assert len(rows) == 7 and all(len(r) == 7 for r in rows), art
    img = pix.new(9, 9)
    for y in range(9):
        for x in range(9):
            if x in (0, 8) and y in (0, 8):
                continue  # rounded corners
            edge = x in (0, 8) or y in (0, 8)
            img.putpixel((x, y), BORDER if edge else GLYPH if rows[y - 1][x - 1] == "w" else FILL)
    return img


if __name__ == "__main__":
    for name, art in GLYPHS.items():
        pix.save(badge(art), OUT / f"{name}.png")
    print(len(GLYPHS), "icons in", OUT)
