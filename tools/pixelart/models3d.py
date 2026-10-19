"""Geometry of the MITE mobs with their own model: one spec per mob, in Blockbench coordinates (y up from the feet, front
towards -z, +x is the mob's right side), from which this tool generates

    python -I tools/pixelart/models3d.py java          # src/main/java/dev/mite/entity/mob/client/MiteModelLayers.java
    python -I tools/pixelart/models3d.py js NAME       # build/art/models/NAME.js: rebuilds the open Blockbench project
                                                      # (load it with eval(require('fs').readFileSync(path, 'utf8')))
and the box-UV layout that mobs3d.py paints on (uvmaps.MODELS[name]). Box UVs are packed automatically unless a cube
gives uv=(u, v). Humanoid specs keep the HumanoidModel part names (head with a hat child, body, right_arm, left_arm,
right_leg, left_leg) and their arm pivots at x = +-5, z = 0: the vanilla attack swing moves the arms back there.
"""
import math
import sys
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
from uvmaps import MODELS as UV_MODELS  # noqa: E402

JAVA = pix.ROOT / "src/main/java/dev/mite/entity/mob/client/MiteModelLayers.java"
JS_DIR = pix.ROOT / "build/art/models"


@dataclass
class B:
    """Box cube: absolute Blockbench from/to, optional fixed uv, mirrored UV, inflate."""
    name: str
    frm: tuple
    to: tuple
    uv: tuple = None
    mirror: bool = False
    inflate: float = 0
    java: bool = True   # False: only reserves its UV and shows in the preview (drawn by a vanilla layer, e.g. the slime shell)

    @property
    def size(self):
        return tuple(round(self.to[i] - self.frm[i], 3) for i in range(3))


@dataclass
class G:
    name: str
    origin: tuple
    cubes: list = field(default_factory=list)
    children: list = field(default_factory=list)
    rot: tuple = (0, 0, 0)


@dataclass
class Model:
    name: str
    tex: tuple
    root: list
    note: str = ""

    def boxes(self):
        def walk(gs):
            for g in gs:
                yield from g.cubes
                yield from walk(g.children)
        return list(walk(self.root))


def pack(model):
    """Shelf-pack the box UVs (w = 2(d+x), h = d+y) that have no fixed uv; fixed ones are reserved first."""
    tw, th = model.tex
    used = [[False] * tw for _ in range(th)]

    def footprint(b):
        x, y, z = whole_size(b, model)
        return 2 * (z + x), z + y

    def mark(u, v, w, h):
        for j in range(v, v + h):
            for i in range(u, u + w):
                used[j][i] = True

    def free(u, v, w, h):
        return u + w <= tw and v + h <= th and all(not used[j][i] for j in range(v, v + h) for i in range(u, u + w))

    for b in model.boxes():
        if b.uv:
            mark(*b.uv, *footprint(b))
    for b in sorted((b for b in model.boxes() if not b.uv), key=lambda b: -footprint(b)[1] * 100 - footprint(b)[0]):
        w, h = footprint(b)
        spot = next(((u, v) for v in range(th) for u in range(tw) if free(u, v, w, h)), None)
        if spot is None:
            raise SystemExit(f"{model.name}: no room for {b.name} ({w}x{h}) in {tw}x{th}")
        b.uv = spot
        mark(*spot, w, h)


def whole_size(b, model):
    """Box UV faces are as wide as the cube (ModelPart.Cube uses the exact float sizes): a fractional size would sample half
    texels across the painted faces, so sizes must be whole pixels (move half-pixel offsets into from/to)."""
    if any(v != int(v) for v in b.size):
        raise SystemExit(f"{model.name}: {b.name} has a fractional box-UV size {b.size}")
    return tuple(int(v) for v in b.size)


def register_uv(model):
    UV_MODELS[model.name] = (model.tex, [(b.name, *b.uv, *whole_size(b, model), "") for b in model.boxes()], model.note)


# ------------------------------------------------------------------------------------------------ Java generation
def f(v):
    v = round(v, 4)
    return str(int(v)) if v == int(v) else f"{v}F"


def java_part(g, parent_pivot, var, out, depth):
    px, py, pz = -g.origin[0], 24 - g.origin[1], g.origin[2]
    ox, oy, oz = px - parent_pivot[0], py - parent_pivot[1], pz - parent_pivot[2]
    cubes = ["CubeListBuilder.create()"]
    for b in (b for b in g.cubes if b.java):
        sx, sy, sz = b.size
        x0, y0, z0 = -b.to[0] - px, 24 - b.to[1] - py, b.frm[2] - pz
        mirror = ".mirror()" if b.mirror else ""
        deform = f", new CubeDeformation({f(b.inflate)})" if b.inflate else ""
        cubes.append(f".texOffs({b.uv[0]}, {b.uv[1]}){mirror}.addBox({f(x0)}, {f(y0)}, {f(z0)}, {f(sx)}, {f(sy)}, {f(sz)}{deform})"
                     + (".mirror(false)" if b.mirror else ""))
    rx, ry, rz = (math.radians(a) for a in g.rot)
    pose = (f"PartPose.offsetAndRotation({f(ox)}, {f(oy)}, {f(oz)}, {f(-rx)}, {f(-ry)}, {f(rz)})" if any(g.rot)
            else f"PartPose.offset({f(ox)}, {f(oy)}, {f(oz)})" if (ox, oy, oz) != (0, 0, 0) else "PartPose.ZERO")
    name = f"p{depth}_{g.name}"
    body = ("\n                " + "\n                ".join(cubes)) if len(cubes) > 1 else cubes[0]
    decl = f"PartDefinition {name} = " if g.children else ""
    out.append(f"        {decl}{var}.addOrReplaceChild(\"{g.name}\", {body}, {pose});")
    for c in g.children:
        java_part(c, (px, py, pz), name, out, depth + 1)


def camel_of(name):
    return "".join(w.capitalize() if i else w for i, w in enumerate(name.split("_")))


def java(models):
    lines = ["package dev.mite.entity.mob.client;", "",
             "import dev.mite.MiteMod;",
             "import net.minecraft.client.model.geom.ModelLayerLocation;",
             "import net.minecraft.client.model.geom.PartPose;",
             "import net.minecraft.client.model.geom.builders.CubeDeformation;",
             "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
             "import net.minecraft.client.model.geom.builders.LayerDefinition;",
             "import net.minecraft.client.model.geom.builders.MeshDefinition;",
             "import net.minecraft.client.model.geom.builders.PartDefinition;",
             "import net.neoforged.neoforge.client.event.EntityRenderersEvent;", "",
             "/** GENERATED by tools/pixelart/models3d.py from the model specs: do not edit, rerun the tool. */",
             "@SuppressWarnings(\"unused\")",
             "public final class MiteModelLayers {",
             "    private MiteModelLayers() {}", ""]
    for m in models:
        lines.append(f"    public static final ModelLayerLocation {m.name.upper()} = new ModelLayerLocation(MiteMod.id(\"{m.name}\"), \"main\");")
    lines += ["", "    static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {"]
    for m in models:
        lines.append(f"        event.registerLayerDefinition({m.name.upper()}, MiteModelLayers::{camel_of(m.name)});")
    lines += ["    }", ""]
    for m in models:
        camel = camel_of(m.name)
        lines += [f"    /** {m.note} */", f"    static LayerDefinition {camel}() {{", "        MeshDefinition mesh = new MeshDefinition();",
                  "        PartDefinition root = mesh.getRoot();"]
        for g in m.root:
            java_part(g, (0, 0, 0), "root", lines, 1)
        lines += [f"        return LayerDefinition.create(mesh, {m.tex[0]}, {m.tex[1]});", "    }", ""]
    lines[-1] = "}"
    text = "\n".join(lines) + "\n"
    # unused CubeDeformation import is harmless but keep the file warning-free
    if "CubeDeformation(" not in text.split("public final class", 1)[1]:
        text = text.replace("import net.minecraft.client.model.geom.builders.CubeDeformation;\n", "")
    JAVA.write_text(text, encoding="utf-8")
    return JAVA


# ------------------------------------------------------------------------------------------------ Blockbench preview
def js(model, texture=None):
    out = ["Project.texture_width = %d; Project.texture_height = %d;" % model.tex,
           "Undo.initEdit({outliner: true, elements: [], selection: true});",
           "[...Outliner.root].forEach(n => n.remove());"]

    def emit(g, parent):
        v = "g_" + g.name
        out.append(f"var {v} = new Group({{name: '{g.name}', origin: {list(g.origin)}, rotation: {list(g.rot)}}}).addTo({parent}).init();")
        for b in g.cubes:
            out.append(f"new Cube({{name: '{b.name}', from: {list(b.frm)}, to: {list(b.to)}, origin: {list(g.origin)}, uv_offset: {list(b.uv)}, "
                       f"box_uv: true, mirror_uv: {str(b.mirror).lower()}, inflate: {b.inflate}}}).addTo({v}).init();")
        for c in g.children:
            emit(c, v)

    for g in model.root:
        emit(g, "undefined")
    if texture:
        path = str(texture).replace("\\", "\\\\")
        out.append(f"var tex = Texture.all[0] || new Texture().add(); tex.fromPath('{path}'); Cube.all.forEach(c => c.applyTexture(tex, true));")
    out += ["Canvas.updateAll();", f"Undo.finishEdit('{model.name} from models3d');", "'built ' + Cube.all.length"]
    JS_DIR.mkdir(parents=True, exist_ok=True)
    p = JS_DIR / f"{model.name}.js"
    p.write_text("\n".join(out), encoding="utf-8")
    return p


def inflated(model, name, amount, keep, note):
    """Same geometry and UVs, boxes in {keep} inflated by {amount}, the others left out (armour layers drawn over a model)."""
    import copy
    m = copy.deepcopy(model)
    m.name, m.note = name, note

    def walk(gs):
        for g in gs:
            for b in g.cubes:
                b.inflate, b.java = (amount, True) if b.name in keep else (0, False)
            walk(g.children)
    walk(m.root)
    return m


# ================================================================================================ specs
def humanoid(name, note, head, body, arm, leg, tex=(64, 64), hat=()):
    """head/body: (group origin, cubes); arm/leg: cubes of the RIGHT limb (+x), mirrored to the left automatically."""
    def mirror(cubes, suffix):
        return [B(c.name + suffix, (-c.to[0], c.frm[1], c.frm[2]), (-c.frm[0], c.to[1], c.to[2]), mirror=not c.mirror, inflate=c.inflate)
                for c in cubes]
    arm_o, arm_c = arm
    leg_o, leg_c = leg
    return Model(name, tex, [
        G("head", head[0], head[1], [G("hat", head[0], list(hat))]),
        G("body", body[0], body[1], body[2] if len(body) > 2 else []),
        G("right_arm", arm_o, arm_c),
        G("left_arm", (-arm_o[0], arm_o[1], arm_o[2]), mirror(arm_c, "_l")),
        G("right_leg", leg_o, leg_c),
        G("left_leg", (-leg_o[0], leg_o[1], leg_o[2]), mirror(leg_c, "_l")),
    ], note)


GHOUL = Model("ghoul", (64, 64), [
    G("head", (0, 21, -5), [B("skull", (-4, 19, -8), (4, 27, 0), uv=(0, 0)), B("jaw", (-3, 16, -8), (3, 19, -3), uv=(32, 0))],
      [G("hat", (0, 21, -5))]),
    G("body", (0, 24, 0), [], [G("torso", (0, 12, 0), [
        B("chest", (-3.5, 12, -1.5), (3.5, 24, 1.5), uv=(16, 16)), B("spine", (-0.5, 21, 1.5), (0.5, 23, 2.5), uv=(40, 16)),
        B("spine2", (-0.5, 17, 1.5), (0.5, 19, 2.5), uv=(44, 16)), B("spine3", (-0.5, 13, 1.5), (0.5, 15, 2.5), uv=(48, 16)),
        B("hump", (-3, 19, 1.5), (3, 24, 3.5), uv=(40, 38))], rot=(-34, 0, 0))]),
    G("right_arm", (5, 20, 0), [B("arm R", (4, 5, -5.5), (7, 20, -2.5), uv=(52, 20), mirror=True),
                                B("claw3", (6, 2, -5.5), (7, 5, -4.5), uv=(12, 48)), B("claw4", (5, 2, -4.5), (6, 5, -3.5), uv=(16, 48)),
                                B("claw5", (6, 2, -3.5), (7, 5, -2.5), uv=(20, 48))]),
    G("left_arm", (-5, 20, 0), [B("arm L", (-7, 5, -5.5), (-4, 20, -2.5), uv=(40, 20)),
                                B("claw0", (-7, 2, -5.5), (-6, 5, -4.5), uv=(0, 48)), B("claw1", (-6, 2, -4.5), (-5, 5, -3.5), uv=(4, 48)),
                                B("claw2", (-7, 2, -3.5), (-6, 5, -2.5), uv=(8, 48))]),
    G("right_leg", (1.9, 12, 0), [B("leg R", (0.4, 0, -1.5), (3.4, 12, 1.5), uv=(16, 32), mirror=True)]),
    G("left_leg", (-1.9, 12, 0), [B("leg L", (-3.4, 0, -1.5), (-0.4, 12, 1.5), uv=(0, 32))]),
], "Ghoul: hunched gaunt body leaning 34 degrees with a humped back, head sunk low with a hanging jaw, long clawed arms.")

# Earth elemental / clay golem: a squat boulder body wider than the hitbox, small head sunk between rock shoulders, heavy arms
# ending in stone fists (pivot kept at x = +-5, the bulk offset outwards), short thick legs.
GOLEM = humanoid(
    "earth_elemental", "Earth elemental and clay golem: boulder body with jutting rocks, head sunk between spiked rock shoulders, heavy fisted arms, stubby legs.",
    head=((0, 24, -1), [B("head", (-3, 23.5, -5), (3, 29.5, 1)), B("brow", (-3.5, 27, -5.5), (3.5, 29, -3.5)),
                        B("jaw", (-2.5, 22.5, -5.5), (2.5, 24.5, -3.5)), B("crest", (-1.5, 29, -3), (1.5, 31, 1))]),
    body=((0, 24, 0), [B("torso", (-6, 9, -3.5), (6, 23, 3.5)), B("shoulder R", (5, 19, -3), (9, 24, 3)),
                       B("shoulder L", (-9, 19, -3), (-5, 24, 3), mirror=True), B("back rock", (-3, 17, 3.5), (3, 23, 5.5)),
                       B("back rock2", (-5, 11, 3), (0, 15, 5)), B("spike R", (5.5, 23.5, -1.5), (8.5, 26.5, 1.5)),
                       B("spike R2", (7, 23.5, 1), (9, 25.5, 3)), B("spike L", (-8.5, 23.5, -1.5), (-5.5, 26.5, 1.5), mirror=True),
                       B("chest rock", (-5, 14, -4.5), (-1, 18, -3.5)), B("belly rock", (1, 10, -4.5), (5, 13, -3.5))]),
    arm=((5, 21, 0), [B("arm", (6, 7, -2.5), (10, 21, 2.5)), B("fist", (5.5, 1, -3), (10.5, 7, 3)),
                      B("forearm rock", (9, 10, -2), (11, 16, 2)), B("knuckles", (5.5, 4, -3.5), (10.5, 6, -2.5))]),
    leg=((2.5, 9, 0), [B("leg", (0, 0, -2.5), (5, 9, 2.5)), B("knee", (0.5, 4.5, -3), (4.5, 7.5, -2))]), tex=(128, 64),
)

# Wraith (wight, shadow, invisible stalker): hooded head with an open face, robe skirt hanging over thin legs, long thin arms
# in flared sleeves.
WRAITH = humanoid(
    "wraith", "Wraith (wight, shadow, invisible stalker): open hood, robe skirt over thin legs, long arms in flared sleeves.",
    head=((0, 24, 0), [B("head", (-3.5, 24, -3.5), (3.5, 31, 3.5)), B("hood", (-4.5, 23.5, -4.5), (4.5, 32.5, 4.5))]),
    body=((0, 24, 0), [B("chest", (-4, 12, -2), (4, 24, 2)), B("robe", (-4.5, 3, -2.5), (4.5, 13, 2.5))]),
    arm=((5, 22, 0), [B("arm", (4, 7, -1.5), (7, 22, 1.5)), B("sleeve", (3.5, 12.5, -2), (7.5, 22.5, 2))]),
    leg=((1.9, 12, 0), [B("leg", (0.4, 0, -1.5), (3.4, 12, 1.5))]),
)

# Revenant: vanilla humanoid proportions (vanilla armour fits it) with a crested helm, pauldrons, a tattered cape and a jutting jaw.
REVENANT = humanoid(
    "revenant", "Revenant: armour-ready humanoid with crested helm, pauldrons, tattered cape and jutting jaw.",
    head=((0, 24, 0), [B("head", (-4, 24, -4), (4, 32, 4)), B("jaw", (-3, 23, -5), (3, 25, -1)), B("crest", (-0.5, 32, -3), (0.5, 34, 4))]),
    body=((0, 24, 0), [B("chest", (-4, 12, -2), (4, 24, 2)), B("cape", (-4, 3, 2.1), (4, 24, 3.1))]),
    arm=((5, 22, 0), [B("arm", (4, 12, -2), (8, 24, 2)), B("pauldron", (3.5, 21, -2.5), (8.5, 25, 2.5))]),
    leg=((1.9, 12, 0), [B("leg", (-0.1, 0, -2), (3.9, 12, 2))]),
)

# Skeletons: open ribcage (spine, shoulder bar, three ribs, pelvis) instead of a solid torso, skull with a dropped jaw.
_SKEL_BODY = [B("spine", (-1, 12, -1), (1, 24, 1)), B("shoulders", (-4.5, 22, -1.5), (4.5, 24, 1.5)),
              B("rib1", (-3.5, 19.5, -2), (3.5, 21.5, 2)), B("rib2", (-3, 17, -2), (3, 19, 2)), B("rib3", (-2.5, 14.5, -1.5), (2.5, 16.5, 1.5)),
              B("pelvis", (-3.5, 12, -1.5), (3.5, 14, 1.5))]
_SKULL = [B("skull", (-4, 25, -4), (4, 32, 4)), B("jaw", (-3, 23, -4), (3, 25, 2))]
SKELETON = humanoid(
    "longdead", "Longdead and longdead guardian: open ribcage, dropped jaw, thin bones.",
    head=((0, 24, 0), _SKULL), body=((0, 24, 0), _SKEL_BODY),
    arm=((5, 22, 0), [B("arm", (4, 10, -1), (6, 22, 1))]), leg=((2, 12, 0), [B("leg", (1, 0, -1), (3, 12, 1))]),
)
BONE_LORD = humanoid(
    "bone_lord", "Bone lords: the skeleton with a spiked crown, a heavy mantle on the shoulders and a robe skirt.",
    head=((0, 24, 0), _SKULL + [B("crown", (-4.5, 31, -4.5), (4.5, 33, 4.5)), B("spike1", (-4.5, 33, -4.5), (-3.5, 35, -3.5)),
                                B("spike2", (3.5, 33, -4.5), (4.5, 35, -3.5)), B("spike3", (-0.5, 33, -4.5), (0.5, 36, -3.5)),
                                B("spike4", (-4.5, 33, 3.5), (-3.5, 35, 4.5)), B("spike5", (3.5, 33, 3.5), (4.5, 35, 4.5))]),
    body=((0, 24, 0), _SKEL_BODY + [B("mantle", (-5.5, 19.5, -3), (5.5, 24.5, 3)), B("robe", (-4, 5, -2.5), (4, 13, 2.5))]),
    arm=((5, 22, 0), [B("arm", (4, 10, -1), (6, 22, 1)), B("cuff", (3.5, 17.5, -1.5), (6.5, 22.5, 1.5))]),
    leg=((2, 12, 0), [B("leg", (1, 0, -1), (3, 12, 1))]),
)

# Dire wolf / hellhound: a bigger wolf (WolfModel part names) with a shaggy raised mane, long snout with fangs, thick legs.
def _wolf_leg(name, x, z):
    side = 1 if x > 0 else -1
    return G(name, (x, 10, z), [B(name.replace("_leg", ""), (x - 1.5, 0, z - 1.5), (x + 1.5, 10, z + 1.5), mirror=side < 0)])


DIRE_WOLF = Model("dire_wolf", (64, 64), [
    G("head", (0, 17, -9), [], [G("real_head", (0, 17, -9), [
        B("skull", (-3.5, 14, -14), (3.5, 20, -8)), B("snout", (-2, 14, -18), (2, 17, -14)),
        B("ear R", (1.5, 20, -11), (3.5, 23, -10)), B("ear L", (-3.5, 20, -11), (-1.5, 23, -10), mirror=True),
        B("fang R", (0.5, 13, -17.5), (1.5, 14, -16.5)), B("fang L", (-1.5, 13, -17.5), (-0.5, 14, -16.5))])]),
    G("body", (0, 13, 2), [B("body", (-4, 9, -3), (4, 16, 9))]),
    G("upper_body", (0, 14, -3), [B("mane", (-5, 9, -8), (5, 19, -1)), B("ridge", (-2, 18.5, -7), (2, 20.5, 2))]),
    _wolf_leg("right_hind_leg", 2.5, 7), _wolf_leg("left_hind_leg", -2.5, 7),
    _wolf_leg("right_front_leg", 2.5, -5), _wolf_leg("left_front_leg", -2.5, -5),
    G("tail", (0, 15, 9), [], [G("real_tail", (0, 15, 9), [B("tail", (-1.5, 5, 7.5), (1.5, 15, 10.5))])], rot=(-36, 0, 0)),
], "Dire wolf and hellhound: big wolf with a raised shaggy mane and ridge, long fanged snout, thick legs.")

# MITE spiders (phase spider, black widow, demon spider, wood spider): SpiderModel part names and rest pose (the animation
# adds to it); a smaller head with fangs, a bulbous abdomen with a hump and spinnerets, spiked knees. All legs share one UV.
def _spider_leg(name, z, ry, rz):
    right = name.startswith("right")
    x0, x1 = (3, 19) if right else (-19, -3)
    knee = (10, 11) if right else (-11, -10)
    return G(name, (4 if right else -4, 9, z), [B("leg", (x0, 8, z - 1), (x1, 10, z + 1), uv=(0, 58), mirror=not right),
                                                 B("spike", (knee[0], 10, z - 0.5), (knee[1], 12, z + 0.5), uv=(40, 58))], rot=(0, ry, rz))


SPIDER = Model("spider", (64, 64), [
    G("head", (0, 9, -3), [B("head", (-3.5, 6, -10), (3.5, 12, -3)), B("fang R", (1.5, 4, -10.5), (2.5, 7, -9.5)),
                           B("fang L", (-2.5, 4, -10.5), (-1.5, 7, -9.5), mirror=True)]),
    G("body0", (0, 9, 0), [B("thorax", (-3, 6, -3), (3, 12, 3))]),
    G("body1", (0, 9, 9), [B("abdomen", (-5.5, 5, 3), (5.5, 14, 15)), B("hump", (-4, 14, 5), (4, 16, 13)),
                           B("spinneret", (-1.5, 6, 15), (1.5, 9, 17))]),
    _spider_leg("right_hind_leg", 2, -45, -45), _spider_leg("left_hind_leg", 2, 45, 45),
    _spider_leg("right_middle_hind_leg", 1, -22.5, -33.3), _spider_leg("left_middle_hind_leg", 1, 22.5, 33.3),
    _spider_leg("right_middle_front_leg", 0, 22.5, -33.3), _spider_leg("left_middle_front_leg", 0, -22.5, 33.3),
    _spider_leg("right_front_leg", -1, 45, -45), _spider_leg("left_front_leg", -1, -45, 45),
], "MITE spiders: fanged head, bulbous humped abdomen with spinnerets, spiked knees (SpiderModel rest pose).")

# Infernal creeper: CreeperModel parts; horned head, magma shards along the back, clawed feet.
def _creeper_leg(name, x, z):
    return G(name, (x, 6, z), [B("leg", (x - 2, 0, z - 2), (x + 2, 6, z + 2), uv=(0, 16)),
                               B("claw", (x - 2, 0, z - 3), (x + 2, 2, z - 2), uv=(16, 52))])


INFERNAL_CREEPER = Model("infernal_creeper", (64, 64), [
    G("head", (0, 18, 0), [B("head", (-4, 18, -4), (4, 26, 4), uv=(0, 0)), B("horn R", (2.5, 26, -1), (4.5, 29, 1)),
                           B("horn L", (-4.5, 26, -1), (-2.5, 29, 1), mirror=True)]),
    G("body", (0, 18, 0), [B("body", (-4, 6, -2), (4, 18, 2), uv=(16, 16)), B("shard1", (-1, 14, 2), (1, 18, 4)),
                           B("shard2", (-2.5, 10, 2), (-0.5, 13, 4)), B("shard3", (0.5, 8, 2), (2.5, 11, 4))]),
    _creeper_leg("right_hind_leg", 2, 4), _creeper_leg("left_hind_leg", -2, 4),
    _creeper_leg("right_front_leg", 2, -4), _creeper_leg("left_front_leg", -2, -4),
], "Infernal creeper: horned head, magma shards along the back, clawed feet.")


# Fire elemental: BlazeModel parts (head + 12 orbiting rods whose positions the animation sets) plus a static molten core
# under the head; the head wears a crest of flames, each rod is a flame tongue with a flickering tip.
def _fire_rods():
    rods, angle = [], 0.0
    rings = [(4, 9.0, lambda i: -2 + math.cos(i * 2 * 0.25), 0.0), (4, 7.0, lambda i: 2 + math.cos(i * 2 * 0.25), math.pi / 4),
             (4, 5.0, lambda i: 11 + math.cos(i * 1.5 * 0.5), 0.47123894)]
    i = 0
    for n, radius, ys, start in rings:
        angle = start
        for _ in range(n):
            x, y, z = math.cos(angle) * radius, ys(i), math.sin(angle) * radius
            bx, by = -x, 24 - y
            rods.append(G(f"part{i}", (round(bx, 3), round(by, 3), round(z, 3)), [
                B("rod", (round(bx - 2, 3), round(by - 8, 3), round(z, 3)), (round(bx, 3), round(by, 3), round(z + 2, 3)), uv=(0, 32)),
                B("tip", (round(bx - 1.5, 3), round(by, 3), round(z + 0.5, 3)), (round(bx - 0.5, 3), round(by + 3, 3), round(z + 1.5, 3)), uv=(8, 32))]))
            angle += math.pi / 2
            i += 1
    return rods


FIRE_ELEMENTAL = Model("fire_elemental", (64, 64), [
    G("head", (0, 24, 0), [B("head", (-4, 20, -4), (4, 28, 4), uv=(0, 0)), B("flame1", (-1, 28, -1), (1, 32, 1)),
                           B("flame2", (-3.5, 28, -2), (-1.5, 31, 0)), B("flame3", (1.5, 28, 0), (3.5, 31, 2))]),
    G("core", (0, 24, 0), [B("core", (-2.5, 11, -2), (2.5, 19, 2)), B("ember", (-1.5, 7, -1.5), (1.5, 11, 1.5))]),
] + _fire_rods(), "Fire elemental: flame-crested head, molten core, twelve orbiting flame tongues (BlazeModel parts).")

# Gelatinous cubes (jelly, blob, ooze, pudding): SlimeModel inner parts; the translucent shell is the vanilla slime outer geometry drawn with the cube texture (MobRenderers.slime)
# (its 8x8x8 box UV at 0,0 is reserved). Inside float a small core, a swallowed skull and bones; the face stays vanilla-placed.
CUBE = Model("gelatinous_cube", (64, 32), [
    G("cube", (0, 0, 0), [B("shell", (-4, 0, -4), (4, 8, 4), uv=(0, 0), java=False), B("core", (-2, 2.5, -1), (2, 6.5, 3)),
                          B("skull", (-3, 1, 0), (-1, 3, 2)), B("bone1", (0, 1, -1), (3, 2, 0)), B("bone2", (1, 4, 1), (2, 7, 2))]),
    G("right_eye", (0, 0, 0), [B("eye R", (1.25, 4, -3.5), (3.25, 6, -1.5))]),
    G("left_eye", (0, 0, 0), [B("eye L", (-3.25, 4, -3.5), (-1.25, 6, -1.5))]),
    G("mouth", (0, 0, 0), [B("mouth", (-1, 2, -3.5), (0, 3, -2.5))]),
], "Gelatinous cubes: small core, swallowed skull and bones inside the vanilla translucent shell.")

# MITE bats (vampire bat, giant vampire bat, nightwing): BatModel parts (keyframe animations by part name); a snouted head,
# taller ears, larger wing membranes, a furry ruff.
BAT = Model("bat", (64, 64), [
    G("body", (0, 7, 0), [B("body", (-1.5, 2, -1), (1.5, 7, 1)), B("ruff", (-2, 5, -1.5), (2, 7, 1.5))], [
        G("right_wing", (1.5, 7, 0), [B("wing R", (1.5, 2, 0), (3.5, 9, 0))], [
            G("right_wing_tip", (3.5, 7, 0), [B("wing tip R", (3.5, 0, 0), (11.5, 9, 0))])]),
        G("left_wing", (-1.5, 7, 0), [B("wing L", (-3.5, 2, 0), (-1.5, 9, 0), mirror=True)], [
            G("left_wing_tip", (-3.5, 7, 0), [B("wing tip L", (-11.5, 0, 0), (-3.5, 9, 0), mirror=True)])]),
        G("feet", (0, 2, 0), [B("feet", (-1.5, 0, 0), (1.5, 2, 0))])]),
    G("head", (0, 7, 0), [B("head", (-2, 7, -1), (2, 10, 1)), B("snout", (-1, 7, -2), (1, 9, -1))], [
        G("right_ear", (1.5, 9, 0), [B("ear R", (1, 8, 0), (4, 14, 0))]),
        G("left_ear", (-1.1, 10, 0), [B("ear L", (-4.1, 8, 0), (-1.1, 14, 0), mirror=True)])]),
], "MITE bats: snouted head, tall ears, wide wing membranes, furry ruff (BatModel parts).")


# MITE silverfish (copperspine, netherspawn, hoary silverfish): SilverfishModel segments and layers with mandibles and dorsal spines.
def _silverfish():
    sizes = [(3, 2, 2), (4, 3, 2), (6, 4, 3), (3, 3, 3), (2, 2, 3), (2, 1, 2), (1, 1, 2)]
    zs, z = [], -3.5
    for i in range(7):
        zs.append(z)
        if i < 6:
            z += (sizes[i][2] + sizes[i + 1][2]) * 0.5
    parts = []
    for i, (w, h, d) in enumerate(sizes):
        cubes = [B(f"seg{i}", (-w / 2, 0, zs[i] - d / 2), (w / 2, h, zs[i] + d / 2))]
        if i == 0:
            cubes += [B("mandible R", (0.5, 0, zs[0] - 2), (1.5, 1, zs[0] - 1)), B("mandible L", (-1.5, 0, zs[0] - 2), (-0.5, 1, zs[0] - 1), mirror=True)]
        parts.append(G(f"segment{i}", (0, h, zs[i]), cubes))
    parts += [
        G("layer0", (0, 8, zs[2]), [B("scales0", (-5, 0, zs[2] - 1.5), (5, 8, zs[2] + 1.5)), B("spine0", (-0.5, 8, zs[2] - 0.5), (0.5, 11, zs[2] + 0.5)),
                                    B("spine1", (-3, 8, zs[2] - 0.5), (-2, 10, zs[2] + 0.5)), B("spine2", (2, 8, zs[2] - 0.5), (3, 10, zs[2] + 0.5))]),
        G("layer1", (0, 4, zs[4]), [B("scales1", (-3, 0, zs[4] - 1.5), (3, 4, zs[4] + 1.5)), B("spine3", (-0.5, 4, zs[4] - 0.5), (0.5, 6, zs[4] + 0.5))]),
        G("layer2", (0, 5, zs[1]), [B("scales2", (-3, 0, zs[1] - 1.5), (3, 5, zs[1] + 0.5))]),
    ]
    return parts


SILVERFISH = Model("silverfish", (64, 64), _silverfish(), "MITE silverfish: segments with mandibles, scaled layers with dorsal spines.")

MODELS3D = [GHOUL, GOLEM, WRAITH, REVENANT, SKELETON, BONE_LORD, DIRE_WOLF, SPIDER, INFERNAL_CREEPER, FIRE_ELEMENTAL, CUBE, BAT, SILVERFISH]
for _m in MODELS3D:
    pack(_m)
    register_uv(_m)
# Wolf armour on the dire wolf model: the same boxes (and UVs, so dire_wolf_armor.png shares the skin layout) inflated.
ARMOR_PARTS = ("body", "mane", "ridge", "right_hind", "left_hind", "right_front", "left_front")  # painted by mobs3d.py
MODELS3D.append(inflated(DIRE_WOLF, "dire_wolf_armor", 0.4, set(ARMOR_PARTS), "Wolf armour layer of the dire wolf model."))

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "java"
    if cmd == "java":
        print(java(MODELS3D))
    elif cmd == "js":
        m = next(m for m in MODELS3D if m.name == sys.argv[2])
        tex = pix.TEXTURES / "entity" / f"{m.name}.png"
        print(js(m, tex if tex.exists() else None))
