"""Generates 16x16 placeholder item textures (original art TBD, docs/03 Q01).

Usage: python -I tools/placeholder_textures.py <out_dir> <name>:<shape>:<hex color>[:<hex background>] ...
Shapes: nugget, ingot, shard, raw, tool, block, bowl, ore, helmet, chestplate, leggings, boots (16x16), layer (64x32 armor layer), horse (64x64 horse armor).
A background fills the empty pixels (ores on stone).
"""
import struct
import sys
import zlib
from pathlib import Path

SHAPES = {
    "nugget": ["................"] * 6 + ["......###......."] + [".....#####......"] * 3 + ["......###......."] + ["................"] * 5,
    "ingot": ["................"] * 5 + ["....#########..."] + ["...###########.."] * 4 + ["..###########..."] + ["................"] * 5,
    "shard": ["................"] * 3 + ["........#......."] + [".......###......"] * 2 + ["......####......"] * 2 + [".....#####......"] * 2 + ["....######......"] * 2 + ["................"] * 4,
    "raw": ["................"] * 4 + [".....######....."] + ["....########...."] + ["...##########..."] * 5 + ["....########...."] + [".....######....."] + ["................"] * 3,
    "tool": [
        "................", "..........####..", ".........#####..", "........#####...",
        ".......#####....", "......#..##.....", ".....#..........", "....#...........",
        "...#............", "..#.............", ".#..............", "................",
        "................", "................", "................", "................"],
    "block": ["################"] * 16,
    "bowl": ["................"] * 6 + [".##############."] * 2 + ["..############.."] * 2 + ["...##########..."] * 2 + ["....########...."] + [".....######....."] + ["................"] * 2,
    "helmet": ["................"] * 3 + ["...##########..."] + ["..############.."] * 3 + ["..###......###.."] * 3 + ["................"] * 6,
    "chestplate": ["................"] * 2 + ["..####....####.."] + ["..############.."] * 2 + ["...##########..."] * 8 + ["................"] * 3,
    "leggings": ["................"] * 2 + ["...##########..."] * 3 + ["...####..####..."] * 8 + ["................"] * 3,
    "boots": ["................"] * 6 + ["...###....###..."] * 4 + ["..####....####.."] * 2 + ["..####....####.."] + ["................"] * 3,
    "layer": ["#" * 64] * 32,
    "horse": ["#" * 64] * 64,
    "ore": [
        "................", "..##........##..", "..###......###..", "...#..........#.",
        "........##......", ".......####.....", "........##......", ".##.............",
        ".###.......##...", "..#.......###...", "...........#....", "....##..........",
        "...####.....##..", "....##.....###..", "............#...", "................"],
}


def png(rows, rgb, bg=None):
    raw = b""
    for row in rows:
        raw += b"\x00"
        for i, c in enumerate(row):
            if c == "#":
                shade = 1.0 - 0.25 * (i % 4 == 0) if rows is not SHAPES["block"] else 1.0 - 0.1 * ((i + len(raw)) % 3)
                raw += bytes(int(v * shade) for v in rgb) + b"\xff"
            elif bg:
                raw += bytes(int(v * (1.0 - 0.08 * ((i * 7 + len(raw)) % 3))) for v in bg) + b"\xff"
            else:
                raw += b"\x00\x00\x00\x00"

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(rows[0]), len(rows), 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def check(shape_rows):
    assert len({len(r) for r in shape_rows}) == 1, "ragged shape"


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for spec in sys.argv[2:]:
        name, shape, color, *bg = spec.split(":")
        check(SHAPES[shape])
        (out / f"{name}.png").write_bytes(png(SHAPES[shape], hex_rgb(color), hex_rgb(bg[0]) if bg else None))


def hex_rgb(color):
    return tuple(int(color[i:i + 2], 16) for i in (0, 2, 4))


if __name__ == "__main__":
    main()
