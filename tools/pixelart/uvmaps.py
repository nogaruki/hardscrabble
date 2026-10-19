"""Annotated UV maps for the vanilla models MITE mobs and armour borrow (geometry transcribed from the 26.3 model code:
HumanoidModel, SkeletonModel, SpiderModel, AdultWolfModel, SlimeModel, BatModel, SilverfishModel, CreeperModel, BlazeModel,
AbstractEquineModel). Generated from box sizes only: no vanilla pixels involved.

    python -I tools/pixelart/uvmaps.py [OUT_DIR]     # default docs/art/uv: <model>.png (annotated, scaled) and
                                                     # <model>_template.png (real size, flat face colours to paint over)
Face letters: T top, B bottom, R right side, F front, L left side, K back (right/left = the mob's own sides).
"""
import colorsys
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402

# model -> (texture size, [(part, u, v, w, h, d, note)])   w = x size, h = y size, d = z size of the box
MODELS = {
    "humanoid_zombie": ((64, 64), [
        ("head", 0, 0, 8, 8, 8, ""), ("hat", 32, 0, 8, 8, 8, "overlay +0.5"), ("body", 16, 16, 8, 12, 4, ""),
        ("arm R (L mirrored)", 40, 16, 4, 12, 4, "both arms"), ("leg R (L mirrored)", 0, 16, 4, 12, 4, "both legs")],
        "Zombie renderer (ghoul, wight, revenant, shadow, invisible stalker, earth elemental, clay golem). Texture is 64x64: only "
        "the top half is read (left arm/leg reuse the right ones mirrored), keep the bottom half transparent."),
    "skeleton": ((64, 32), [
        ("head", 0, 0, 8, 8, 8, ""), ("hat", 32, 0, 8, 8, 8, "overlay"), ("body", 16, 16, 8, 12, 4, ""),
        ("arm", 40, 16, 2, 12, 2, "both"), ("leg", 0, 16, 2, 12, 2, "both")],
        "Skeleton renderer (longdead, longdead guardian, bone lord, ancient bone lord). Body box is 8x12x4 but thin limbs are 2x12x2."),
    "spider": ((64, 32), [
        ("head", 32, 4, 8, 8, 8, "faces -Z"), ("neck", 0, 0, 6, 6, 6, "body0"), ("abdomen", 0, 12, 10, 8, 12, "body1"),
        ("leg", 18, 0, 16, 2, 2, "all 8 legs")],
        "Spider renderer (phase spider, black widow, demon spider) and cave spider layer (wood spider, scaled 0.7). The vanilla "
        "eyes layer (red glow) is drawn on top of the head front by SpiderRenderer."),
    "wolf": ((64, 32), [
        ("head", 0, 0, 6, 6, 4, ""), ("ear", 16, 14, 2, 2, 1, "both ears"), ("snout", 0, 10, 3, 3, 4, ""),
        ("body", 18, 14, 6, 9, 6, "rotated 90 deg: T/B = back/belly ends"), ("mane", 21, 0, 8, 6, 7, "rotated 90 deg"),
        ("leg", 0, 18, 2, 8, 2, "all 4"), ("tail", 9, 18, 2, 8, 2, "")],
        "Wolf renderer (dire wolf, hellhound). Adult model only (babies use another layout)."),
    "slime": ((64, 32), [
        ("outer cube", 0, 0, 8, 8, 8, "translucent shell"), ("inner cube", 0, 16, 6, 6, 6, ""), ("eye R", 32, 0, 2, 2, 2, ""),
        ("eye L", 32, 4, 2, 2, 2, ""), ("mouth", 32, 8, 1, 1, 1, "")],
        "Slime renderer (jelly, blob, ooze, pudding). Outer cube is drawn translucent: use partial alpha there."),
    "bat": ((32, 32), [
        ("body", 0, 0, 3, 5, 2, ""), ("head", 0, 7, 4, 3, 2, ""), ("ear R", 1, 15, 3, 5, 0, "flat"), ("ear L", 8, 15, 3, 5, 0, "flat"),
        ("wing R", 12, 0, 2, 7, 0, "flat"), ("wing tip R", 16, 0, 6, 8, 0, "flat"), ("wing L", 12, 7, 2, 7, 0, "flat"),
        ("wing tip L", 16, 8, 6, 8, 0, "flat"), ("feet", 16, 16, 3, 2, 0, "flat")],
        "Bat renderer (vampire bat, giant vampire bat, nightwing). 32x32 texture! Flat parts show F and K side by side."),
    "silverfish": ((64, 32), [
        ("seg0 head", 0, 0, 3, 2, 2, ""), ("seg1", 0, 4, 4, 3, 2, ""), ("seg2", 0, 9, 6, 4, 3, ""), ("seg3", 0, 16, 3, 3, 3, ""),
        ("seg4", 0, 22, 2, 2, 3, ""), ("seg5", 11, 0, 2, 1, 2, ""), ("seg6 tail", 13, 4, 1, 1, 2, ""),
        ("scales0", 20, 0, 10, 8, 3, "over seg2"), ("scales1", 20, 11, 6, 4, 3, "over seg4"), ("scales2", 20, 18, 6, 5, 2, "over seg1")],
        "Silverfish renderer (copperspine, netherspawn, hoary silverfish)."),
    "creeper": ((64, 32), [("head", 0, 0, 8, 8, 8, ""), ("body", 16, 16, 8, 12, 4, ""), ("leg", 0, 16, 4, 6, 4, "all 4")],
                "Creeper renderer (infernal creeper). The charged-armour overlay is vanilla."),
    "blaze": ((64, 32), [("head", 0, 0, 8, 8, 8, ""), ("rod", 0, 16, 2, 8, 2, "all 12 rods")],
              "Blaze renderer (fire elemental). Fullbright: the whole texture glows."),
    "armor_humanoid": ((64, 32), [
        ("helmet", 0, 0, 8, 8, 8, "head +1.0"), ("helmet overlay", 32, 0, 8, 8, 8, "hat +1.5"), ("chest", 16, 16, 8, 12, 4, "body +1.0"),
        ("arm", 40, 16, 4, 12, 4, "both arms"), ("boot / leg", 0, 16, 4, 12, 4, "boots use the lower part")],
        "entity/equipment/humanoid/<material>.png: helmet + chestplate + boots."),
    "armor_humanoid_leggings": ((64, 32), [("waist", 16, 16, 8, 12, 4, "body +0.5, lower part shows"), ("leg", 0, 16, 4, 12, 4, "both legs")],
                                "entity/equipment/humanoid_leggings/<material>.png: leggings only."),
    "horse_body": ((64, 64), [
        ("body", 0, 32, 10, 10, 22, ""), ("neck", 0, 35, 4, 12, 7, "overlaps body UV"), ("head", 0, 13, 6, 5, 7, ""),
        ("mane", 56, 36, 2, 16, 2, ""), ("muzzle", 0, 25, 4, 5, 5, ""), ("leg", 48, 21, 4, 11, 4, "all 4"),
        ("tail", 42, 36, 3, 14, 4, ""), ("ear", 19, 16, 2, 3, 1, "both")],
        "entity/equipment/horse_body/<material>.png: horse armour, same layout as the horse skin."),
}
FACE_SHADE = {"T": 1.0, "F": 0.85, "R": 0.7, "L": 0.7, "K": 0.55, "B": 0.45}


def faces(u, v, w, h, d):
    """Standard Minecraft cube unwrap -> {face: (x, y, width, height)} (zero-size faces dropped)."""
    f = {"T": (u + d, v, w, d), "B": (u + d + w, v, w, d), "R": (u, v + d, d, h), "F": (u + d, v + d, w, h),
         "L": (u + d + w, v + d, d, h), "K": (u + 2 * d + w, v + d, w, h)}
    return {k: r for k, r in f.items() if r[2] > 0 and r[3] > 0}


def colour(i, n, shade):
    r, g, b = colorsys.hsv_to_rgb(i / max(n, 1), 0.55, 0.95 * shade)
    return (int(r * 255), int(g * 255), int(b * 255), 255)


def render(name, out_dir):
    (tw, th), parts, note = MODELS[name]
    template = pix.new(tw, th)
    td = ImageDraw.Draw(template)
    s = max(4, 1024 // max(tw, th))
    big = Image.new("RGBA", (tw * s, th * s + 120), (40, 40, 46, 255))
    bd = ImageDraw.Draw(big)
    for gx in range(0, tw * s + 1, s):
        bd.line([(gx, 0), (gx, th * s)], fill=(52, 52, 60, 255))
    for gy in range(0, th * s + 1, s):
        bd.line([(0, gy), (tw * s, gy)], fill=(52, 52, 60, 255))
    font = pix._font()
    for i, (part, u, v, w, h, d, extra) in enumerate(parts):
        for face, (x, y, fw, fh) in faces(u, v, w, h, d).items():
            c = colour(i, len(parts), FACE_SHADE[face])
            td.rectangle([x, y, x + fw - 1, y + fh - 1], fill=c)
            bd.rectangle([x * s, y * s, (x + fw) * s - 1, (y + fh) * s - 1], fill=c, outline=(15, 15, 18, 255))
            label = f"{face}" if fw * s < 70 else f"{part} {face}"
            bd.text((x * s + 3, y * s + 2), label[: max(1, fw * s // 6)], fill=(10, 10, 12, 255), font=font)
            if face == "F" and fw * s >= 48 and fh * s >= 30:
                bd.text((x * s + 3, y * s + 14), f"{fw}x{fh} @{x},{y}", fill=(10, 10, 12, 255), font=font)
    y0 = th * s + 6
    bd.text((6, y0), f"{name}: texture {tw}x{th}. T top B bottom R right F front L left K back.", fill=(230, 230, 230, 255), font=font)
    words, line, lines = note.split(), "", []
    for wd in words:
        if len(line) + len(wd) > tw * s // 6 - 2:
            lines.append(line)
            line = ""
        line += wd + " "
    lines.append(line)
    for j, ln in enumerate(lines[:5]):
        bd.text((6, y0 + 16 + 14 * j), ln, fill=(200, 200, 200, 255), font=font)
    legend = "  ".join(f"{p[0]}: {p[1]},{p[2]} {p[3]}x{p[4]}x{p[5]}" for p in parts)
    for j in range(0, len(legend), tw * s // 6 - 2):
        if y0 + 90 + (j // (tw * s // 6 - 2)) * 14 < big.height - 10:
            bd.text((6, y0 + 90 + (j // (tw * s // 6 - 2)) * 14), legend[j:j + tw * s // 6 - 2], fill=(170, 170, 170, 255), font=font)
    out_dir.mkdir(parents=True, exist_ok=True)
    big.save(out_dir / f"{name}.png")
    pix.save(template, out_dir / f"{name}_template.png")
    return out_dir / f"{name}.png"


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else pix.ROOT / "docs/art/uv"
    for m in MODELS:
        print(render(m, out))
