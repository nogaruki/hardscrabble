"""Material colour ramps for Hardscrabble art (original values, see docs/art/STYLE.md).

Every ramp has exactly 6 shades, index 0 = darkest (outline / deepest shadow) to 5 = highlight.
Same length everywhere so one shape grid renders in any material.
Shades are hue-shifted: darks lean cool (toward blue/purple), lights lean warm (toward yellow); no pure black, and pure
white only in glass/milk. Add a ramp here (6 shades, dark -> light) rather than hard-coding colours in a texture script.
"""

RAMPS = {
    # --- tool / weapon / armour materials ---
    "wood":         ["#2b1b14", "#4b301f", "#6c472b", "#8d6239", "#ad7f4b", "#c99f66"],
    "flint":        ["#16161c", "#2b2a32", "#44434b", "#5f5d63", "#7e7b7c", "#a6a29c"],
    "obsidian":     ["#0c0913", "#1b1428", "#2d2143", "#44325f", "#634d85", "#9179b3"],
    "copper":       ["#3a1a14", "#6c3122", "#a14f2f", "#cd7143", "#e99862", "#f8c596"],
    "silver":       ["#33363f", "#5d6170", "#8a8f9c", "#b7bcc6", "#dde0e6", "#fbfcfd"],
    "gold":         ["#4a2509", "#8a5011", "#c4821a", "#e8b02c", "#f7d65a", "#fff3ac"],
    "iron":         ["#262626", "#47474a", "#6c6c6e", "#939394", "#bdbcba", "#e2e0dc"],
    "rusted_iron":  ["#26211f", "#4a3d38", "#73503b", "#9c6236", "#b9834f", "#c8b3a0"],  # grey-brown darks, rust mids, bare-metal light
    "ancient_metal": ["#191b11", "#323722", "#4f5636", "#6d784b", "#909b66", "#b9c18f"],
    "mithril":      ["#222d45", "#3e5477", "#6281a8", "#8eabcb", "#bad3e7", "#e6f3fc"],
    "adamantium":   ["#0a1e14", "#123e29", "#1c613e", "#2b8955", "#4daf74", "#8ed8a4"],
    "verdigris":    ["#132b27", "#1f4a42", "#2e6b5d", "#45907a", "#6db59a", "#a6dcc2"],  # patina accent (ancient metal, copper)
    "leather":      ["#2d160c", "#552b17", "#7e4424", "#a25e33", "#c27d48", "#dba26c"],
    "string":       ["#4b453d", "#7a7166", "#a39a8c", "#c5bdaf", "#e0d9cc", "#f6f2ea"],
    "feather":      ["#3c3c42", "#6a6a70", "#97979a", "#bfbfbf", "#dfdfdc", "#f9f9f5"],
    # --- gems and minerals ---
    "emerald":      ["#052a17", "#0a5329", "#117f3d", "#1eaa53", "#50d47b", "#b5f5c9"],
    "diamond":      ["#0c383f", "#126970", "#1da1a5", "#3fcfca", "#8beee6", "#ddfffb"],
    "quartz":       ["#5d534f", "#897f7a", "#b1a8a2", "#d2cbc5", "#ebe6e1", "#fbfaf7"],
    "glass":        ["#33505d", "#5c8695", "#8cb7c3", "#b8dce4", "#dff2f5", "#ffffff"],
    # --- fills and hosts ---
    "water":        ["#13296a", "#1e42a2", "#2e61cf", "#4b85e7", "#79a9f1", "#b4d0f9"],
    "lava":         ["#6a1305", "#a72f0a", "#d8520f", "#f17f1c", "#fbaf3a", "#fde68a"],
    "milk":         ["#9aa1a6", "#c3c9cd", "#dde1e3", "#eceff0", "#f7f8f9", "#ffffff"],
    "stone":        ["#3e3e43", "#57575c", "#6f6f73", "#87878a", "#a0a0a1", "#bcbcbb"],
    "deepslate":    ["#1b1b21", "#2b2b33", "#3d3d46", "#51515a", "#66666e", "#7d7d85"],
}

# Item/texture-id material prefix -> ramp name (mite ids use "golden_" for some items, "gold_" for others).
MATERIAL_OF_PREFIX = {
    "adamantium": "adamantium", "ancient_metal": "ancient_metal", "copper": "copper", "flint": "flint", "golden": "gold",
    "gold": "gold", "iron": "iron", "mithril": "mithril", "obsidian": "obsidian", "rusted_iron": "rusted_iron",
    "silver": "silver", "wooden": "wood",
}


def rgba(hex_colour, alpha=255):
    h = hex_colour.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def ramp(name):
    """The ramp as a list of RGBA tuples, darkest first."""
    return [rgba(c) for c in RAMPS[name]]


def _check():
    lum = lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
    for name, hexes in RAMPS.items():
        assert len(hexes) == 6, name
        ls = [lum(rgba(c)) for c in hexes]
        assert ls == sorted(ls), f"{name} not dark -> light: {ls}"
        assert rgba(hexes[0])[:3] != (0, 0, 0), f"{name}: no pure black"


_check()

# --- entity skins (tools/pixelart/mobs3d.py) ---
ENTITY_RAMPS = {
    "ghoul":      ["#262a22", "#40463a", "#5f6855", "#808a73", "#a2a98f", "#c4c8ac"],  # pale grey-green rotting flesh
    "rag":        ["#231d18", "#3a312a", "#53473c", "#6d5f50", "#887966", "#a59680"],
    "wight":      ["#2a3245", "#4a5670", "#72819d", "#9dacc4", "#c6d3e3", "#ecf2f9"],  # spectral pale blue
    "shroud":     ["#101219", "#1b1f2b", "#282e3e", "#363e52", "#465067", "#58637c"],
    "rotflesh":   ["#1f2419", "#33402a", "#4b5c3b", "#64764d", "#7f8f63", "#9ca97d"],
    "shade":      ["#0a0910", "#131119", "#1c1925", "#262232", "#322c41", "#413953"],  # near-black shadow
    "mist":       ["#5d6272", "#7b8191", "#9aa0af", "#b7bdca", "#d2d7e1", "#eceff4"],
    "bone":       ["#4b4538", "#776f5e", "#a19884", "#c5bda8", "#e0dac9", "#f6f3ea"],  # bleached ancient bone
    "old_bone":   ["#3a3024", "#5c4e3a", "#7f6d52", "#a08c6b", "#bba884", "#d2c3a0"],
    "dark_bone":  ["#1d1917", "#322b26", "#4a4038", "#62564b", "#7b6e60", "#968877"],
    "royal":      ["#250a10", "#43121b", "#661c28", "#8a2a35", "#ab4146", "#c9625c"],
    "clay":       ["#3a2219", "#5e3826", "#845137", "#a76c4a", "#c28b66", "#d9ad8c"],
    "char":       ["#150c0c", "#251212", "#3b1916", "#56221b", "#743022", "#93432c"],  # charred red-black
    "phase":      ["#140f2b", "#251b4c", "#3a2b72", "#533f96", "#715bb6", "#9a86d4"],
    "widow":      ["#0b0b10", "#15151d", "#20212c", "#2d2f3d", "#3e4253", "#565c71"],
    "demon":      ["#1c0709", "#350d10", "#521418", "#721d1f", "#902c27", "#ad4233"],
    "moss":       ["#13230f", "#20391a", "#2f5225", "#426c31", "#5c8a40", "#7ea855"],
    "direwolf":   ["#1e1f24", "#33353c", "#4b4e56", "#666971", "#83868d", "#a3a5aa"],
    "jelly":      ["#5a3a06", "#8a5d0c", "#b98614", "#dcab2a", "#efcb55", "#fbe696"],
    "blob":       ["#3b0e33", "#621a55", "#8b2b78", "#b04499", "#cd6bb5", "#e6a0d2"],
    "ooze":       ["#1d241c", "#323d30", "#4b5a47", "#66775f", "#85947b", "#a8b49c"],
    "pudding":    ["#0f0b0a", "#1e1613", "#30231d", "#433128", "#594335", "#725a47"],
    "batfur":     ["#1a120f", "#2d1f19", "#433026", "#5b4334", "#765a46", "#94765d"],
    "bat_red":    ["#250b09", "#431511", "#63211a", "#842f23", "#a3442f", "#c06243"],
    "netherrack": ["#1f0909", "#3a1110", "#571a17", "#77251f", "#96362a", "#b24f3a"],
    "hoary":      ["#4c5560", "#6f7a86", "#939eaa", "#b4bec8", "#d1d9e1", "#eef2f6"],
    "scute":      ["#3b1d1c", "#5f302d", "#86453f", "#a95e55", "#c67c6e", "#dea290"],  # armadillo scute (wolf armour)
}
RAMPS.update(ENTITY_RAMPS)
_check()
