# Hardscrabble art style guide

Every texture under `src/main/resources/assets/hardscrabble/textures/` is redrawn to these rules. Read this file, then use the
toolkit in `tools/pixelart/` and copy the reference tool grids in `tools/pixelart/shapes.py`. The work is split in batches
in `docs/art/INVENTORY.csv` (A tools, weapons and armour, B materials, food and misc items, C blocks, D entities).

## 0. Legal rule (read first)

- Vanilla 26.3 assets (`../ref-src/assets/minecraft/`) may be **studied**: sizes, palette sizes, light direction, outline
  habits, silhouettes, UV layouts. The numbers in this guide come from that study.
- Never copy, trace, pixel-edit, recolour or sample colours from a vanilla texture into our files. Do not open a vanilla
  PNG in the same script that writes our PNGs. Our colours come only from `tools/pixelart/palettes.py`.
- Never open anything under `../original/`. No MITE asset is used either.
- A texture that sits on a vanilla model (zombie, spider, ...) follows the model's UV *layout*, which is geometry, not art.
  The UV maps in `docs/art/uv/` are generated from box sizes only.

## 1. Files and canvas sizes

All PNGs are **8-bit RGBA, non-interlaced** (colour type 6). `TexturesTest` (JUnit, run by `./gradlew build`) decodes them
assuming 4 bytes per pixel, so a palette or grey PNG breaks the build. `pix.save()` always writes the right format.

| Asset | Size | Notes |
|---|---|---|
| item (`textures/item/`) | 16x16 | Six spawn eggs are 16x15 today: fix them to 16x16 |
| block (`textures/block/`) | 16x16 | Animated strip 16x(16*n) only with a `.png.mcmeta` |
| zombie-model mobs (ghoul, wight, revenant, shadow, invisible stalker, earth elemental, clay golem) | **64x64** | Only the top half (y 0-31) is read; leave y 32-63 transparent. All are 64x32 today, which stretches them: this is why the current zombies look like nothing |
| skeleton model (longdead, longdead guardian, bone lord, ancient bone lord) | 64x32 | |
| spider model (phase spider, black widow, demon spider), cave spider model (wood spider) | 64x32 | |
| wolf model (dire wolf, hellhound) | 64x32 | Adult model |
| slime model (jelly, blob, ooze, pudding) | 64x32 | |
| bat model (vampire bat, giant vampire bat, nightwing) | **32x32** | All are 64x32 today: wrong |
| silverfish model (copperspine, netherspawn, hoary silverfish) | 64x32 | |
| creeper model (infernal creeper), blaze model (fire elemental) | 64x32 | |
| armour layer `entity/equipment/humanoid/<material>.png` | 64x32 | Helmet, chestplate and boots |
| armour layer `entity/equipment/humanoid_leggings/<material>.png` | 64x32 | Leggings only |
| horse armour `entity/equipment/horse_body/<material>.png` | 64x64 | Horse skin layout |

26.3 equipment layout: `assets/hardscrabble/equipment/<id>.json` lists layers (`humanoid`, `humanoid_leggings`, `horse_body`), each
naming `hardscrabble:<texture>`, read from `textures/entity/equipment/<layer>/<texture>.png`. The armour *item* icon is a separate
16x16 item texture.

## 2. Light, ramps and colour counts

- **Light comes from the top-left**, everywhere: items, block faces, mob skins. Surfaces facing up or left are lighter,
  surfaces facing down or right darker. On a mob skin: top faces lightest, front next, sides darker, bottom darkest.
- Each material has **one 6-shade ramp** in `palettes.py`, index 0 (darkest) to 5 (highlight), hue-shifted (darks cooler,
  lights warmer). Roles:
  - 0: outline on edges facing down/right, deepest shadow
  - 1: outline on edges facing up/left, cast shadow
  - 2: shaded interior (next to a dark edge)
  - 3: base colour
  - 4: lit interior (next to a lit edge)
  - 5: highlight: 1-4 pixels per item, on the top-left edge or a cutting edge, never a whole area
- An item uses at most **2 ramps plus 1 accent ramp** (for example metal head + wood handle + string lashing).
- Colour counts, measured on vanilla and enforced by `pix.validate`:

| Kind | Vanilla measurement | Our limit |
|---|---|---|
| Tool / weapon | 8-11 colours, 50-85 opaque px | <= 16 colours, aim 9-12 |
| Ingot / bucket | 8 colours, 135-146 opaque px | <= 16 |
| Nugget | 8 colours, ~34 px | <= 16 |
| Raw ore | 8 colours, ~168 px | <= 16 |
| Gem | 10 colours, 96-120 px | <= 16 |
| Armour icon | 7 colours, 78-138 px | <= 16 |
| Block (stone-like) | 4-7 colours | <= 32, aim 4-8 |
| Block (crafted: furnace, table) | 10-18 colours | <= 32 |
| Entity skin | | <= 96 |

- No pure black (0,0,0): the darkest outline is the ramp's shade 0 (luminance 15-60). Vanilla tool outlines are (24,24,24)
  or a dark hue, never black. Pure white only in glass, milk and highlights of white materials.
- No noise, no dithering, no gradients made of single pixels, no anti-aliasing against transparency. Items use alpha 0 or
  255 only (glass and bottles may use partial alpha). Slime outer cubes are the only skins with partial alpha.

## 3. Outlines

- Items: every silhouette edge has a 1 px outline. Edges facing down/right use shade 0, edges facing up/left use shade 1
  (on vanilla tools 48-56 % of edge pixels are the darkest colour, the rest a mid-dark tone). Handles follow the same rule
  with the wood ramp.
- Parts that are only 2 px thick end up all outline (1 over 0): make heads 3 px thick wherever you want the material to
  read, keep 1-2 px for tips only.
- Blocks: no outline (they tile). Crafted blocks (furnace, workbench, strongbox) draw a 1 px frame in shade 0-1 on the face
  border.
- Skins: no outline around faces; separate features (eyes, mouth, ribs) with shade 0-1.

## 4. Tools and weapons

The reference silhouettes are in `tools/pixelart/shapes.py`; `python -I tools/pixelart/shapes.py sheet` renders them in
copper, iron and mithril (see `docs/art/sheets/tools_reference.png`). One silhouette per type, shared by all materials.

Construction rules (all types):
- The handle is a diagonal from the bottom-left corner to the top-right: its core pixels sit on the line `x + y = 15`,
  starting at (1,14). Each row is 3 px: lit edge (wood 1), core (wood 3/2/3 repeating = grain), shadow edge (wood 0); an
  end cap of two shade-0 pixels below the start. `pix.stick(x0, y0, length)` draws it.
- The working part is at the top-right; nothing touches the bottom-right or top-left corners, so every tool reads by its
  head shape at 1x.
- Materials only change the ramp (`digit=`), never the shape. Wood-only weapons (club, cudgel) use the wood ramp.

| Type | Handle | Head / shape | What makes it readable |
|---|---|---|---|
| pickaxe | long, to (11,4) | 3 px thick crescent hugging the top-right corner, symmetric about the handle axis, tips at (2,5) and (10,13) | two long arms |
| axe | long, to (12,3) | broad blade on the upper-left side of the handle, cutting edge parallel to the handle (from (1,7) to (7,0)), small poll on the right | blade wider than its neck, bright edge |
| shovel | to (8,7) | spade blade along the axis, ~4 px wide, square shoulders, rounded tip at (13,1) | flat shoulders |
| hoe | long, to (11,4) | thin 2-3 px blade running left from the handle top, short drop at the end | L shape |
| sword | own grip | 4 px wide blade from the guard to the top-right corner, 2 px thick crossguard 6 px long across the axis, wood grip, 2x2 pommel | full diagonal length |
| hatchet | short, from (3,13) | small axe blade, no poll | short handle, compact |
| knife | own handle | single-edged blade (3 px), wooden handle with string lashing (accent ramp) | lashing, no guard |
| dagger | own grip | short 4 px blade ending at y 4, short guard (4 px), short grip | half a sword |
| club | none | wooden bat widening from a 2 px grip to a 4 px rounded end | wood only |
| cudgel | none | short stick with a round knob | knob |
| battle_axe | long, to (12,3) | two blades, one each side of the handle (mirror about the axis) | double bit |
| war_hammer | to (10,5) | square hammer block on the upper-left, spike on the lower-right | block + spike |
| mattock | long, to (11,4) | pickaxe crescent with a flared adze end on the left arm and a pick point on the right arm | asymmetric arms |
| scythe | long, to (12,3) | long thin blade along the top edge curving down to a point at (1,7) | long curve |
| shears | none | closed scissors: 4 px blade body to the top-right, rivet (shade 5), two ring handles bottom-left | rings |
| bow (+ pulling 0-2) | grip in wood on the stave | 2 px stave curving along the top and right edges, string (string ramp) straight between the tips; pulling: string drawn back to (6,9), (5,10), (4,11) with a flint-headed arrow on the axis | arc + string |
| arrow | 2 px shaft | arrowhead (material) at the top-right, fletching (feather ramp) at the bottom-left | head colour = material |
| fishing_rod / _cast | thin 2 px rod | line (string ramp) hanging from the tip at x 15 to a hook (material) at (13,12); cast: rod only (the line is drawn by the game) | hook = material |
| bucket (+ water, lava, milk, stone) | none | front view, tapered body, dark opening, rim lip in shade 4, handle arc; filled = opening repainted with the contents ramp | contents ramp |

## 5. Material items

Material items use the whole ramp: every face its own shade, a shade-5 glint on the lit edge, so they read as solid
objects next to the tools (user feedback: flat versions were rejected). Grids live in `batch_b.py`.
- Ingot: bar in 3/4 view, ~14x10 px: top face 4 with a 5 glint on its front-left bevel, a 5/4 bevel line between top and
  front, front 3 fading to 2, a recessed stamp (2 over 4), dark end face 1-2 on the right, outline 0 bottom/right.
- Nugget: 3 overlapping lumps, ~50 px, each with its own 5 glint, 4/3 lit side and 2/1 shadow side; the front lump's
  outline separates it from the one behind.
- Raw ore: one chunky irregular lump of 140-170 px (rock: lit 3D bulges in shades 0-3 via `pix.relief`), metal crystals
  (5/4 lit facet, 2 shaded facet) catching the light, pits as a dark pixel with a lit lower lip.
- Shards: crystal shards (diamond, emerald, quartz, obsidian) are a long crystal plus a small fragment, facets 5/4/2/1 and
  glints; glass is two flat panes with white (5) top-left edges; flint is a knapped flake with a bright ridge and ripples.
- Coin: 11 px disc plus a 1-2 px thickness band at the bottom, raised rim (lit top-left, inner wall dark at the top and lit
  at the bottom), embossed square boss with its own lit and dark sides.
- Chain: two face-on oval links shaded as tori (lit outer top-left, dark inner wall at the top-left of the eye, lit at its
  bottom-right) joined by an edge-on rod lying over the lower ring and under the upper one.
- Door icon: 10x16 panel door, frame lit left/top and dark right/bottom, rivets, recessed window and lower panel (shadow on
  the top/left walls, light on the bottom/right), knob. Ancient metal items get verdigris specks (accent ramp).
- Armour icons: helmet, chestplate, leggings, boots in the material ramp, vanilla silhouettes' proportions (helmet 10x6,
  chestplate 12x13, leggings 10x12, boots 2 boots of 4x5); chainmail versions use the same silhouette with a 1 px
  checker of shades 1/3 (rings) inside the outline.

## 6. Blocks

- Faces tile seamlessly: check the 3x3 tiling preview (`pix.contact_sheet([pix.tiled(img)], out)`) before committing.
- Ores: the texture is a full block: an original stone host painted with the `stone` ramp (luminance 100-145, 4-5 colours,
  soft blotches 3-6 px wide, no outline) or `deepslate` ramp (luminance 45-120, horizontal streaks) plus 4-6 ore clusters of
  3-7 px. Vanilla ores change 86-160 of the 256 pixels and use 7-14 overlay colours; each cluster has a shade-0/1 rim and
  1-2 highlight pixels. Never take the host from vanilla stone.
- Workbenches, strongboxes, runestones and metal blocks use `cube_all`: **one texture on all six faces**, so the design
  must read from every side (frame + central motif). Workbench: wooden frame (wood ramp) with the metal as corner plates
  and a tool motif; strongbox: metal plates, rivets in shade 5, a dark keyhole.
- Furnaces (`orientable`): `front`, `front_on`, `side`, `top`. Front and front_on share every pixel except the opening
  (rows 8-14, columns 3-12): unlit opening shade 0, lit opening painted with the `lava` ramp. Side and top: the furnace
  material as bricks or blocks.
- Anvils (`template_anvil`): `*_anvil.png` (body) is sampled piecewise: rows 0-6 for the sides of the striking block,
  rows 11-16 for the base sides, the centre for the waist: paint one continuous metal surface darkening downward.
  `*_anvil_top.png`: only columns 3-12 are used; leave columns 0-2 and 13-15 transparent (vanilla: 96 transparent px).
- Bars (`template_bars_*`): the post and caps use the centre columns 7-8, panes use columns 8-15 and rows 0-7 of the edge:
  vertical bars 2 px wide at columns 2-3, 7-8, 12-13, everything else transparent (vanilla: 140 transparent px).
- Doors: `door_bottom` / `door_top` 16x16; columns 0-2 become the 3 px thick edge and rows 13-15 the top/bottom strips, so
  keep a frame there. The top half may have transparent window panes.
- Enchanting table (`block/enchanting_table`): top 16x16, bottom 16x16, side uses only rows 4-15 (rows 0-3 transparent).
- Crops (`crop`, `cross`): transparent background, stems in 3 shades, the plant grows taller each stage; dead crops use
  dry browns and greys (no green).

## 7. Mob skins

- Use the annotated UV map and the real-size template for the model in `docs/art/uv/` (`<model>.png` and
  `<model>_template.png`; regenerate with `python -I tools/pixelart/uvmaps.py`). Face letters: T top, B bottom, R right,
  F front, L left, K back (the mob's own right and left).
- Draw at real size and preview with `pix.contact_sheet([...], out, scale=8)`; check every face is painted (no stray
  template colours left) and that the front faces carry the identity (eyes, mouth, ribs).
- Shading on a skin: per face, a 3-4 shade ramp; top faces +1 shade, bottom faces -1. A mob skin may use up to 3 ramps
  (skin/flesh, cloth or armour, accent such as glowing eyes).
- Eyes: 1x1 or 2x1 at rows 3-5 of an 8x8 head front, with a shade-0 socket. Glowing eyes (shadow, wight, hellhound) use the
  brightest accent (shade 5 of `lava` or `diamond`).
- Model notes: zombie-model mobs use the top half of a 64x64 texture (arms and legs are mirrored right-to-left); skeleton
  limbs are 2x12x2; the spider renderer adds the vanilla red eye glow on the head front; the blaze is fullbright; slime
  outer cubes are translucent; bat textures are 32x32 with flat (zero-depth) wings and ears; the wolf body and mane boxes
  are rotated 90 degrees (their T/B faces are the chest and rear). MITE zombie-model mobs must stay adults: the baby zombie
  model reads a different layout from the same texture (Revenant has a test; check the others before allowing babies).
- Armour layers: helmet = head + hat boxes, chestplate = body + both arms, boots = lower 4-5 rows of the leg box; the
  leggings texture holds the waist (lower body) and legs. Worn layers carry as much detail as the mob skins: per-face
  curvature from a rounded-box light model, bevelled lames with cast shadows, rivets (lit pixel + shadow), soft 2x1
  dither and scratches, plus the material's character (rust blooms on rims and rivets, copper tarnish, ancient metal
  engraved bands and verdigris, mithril filigree, adamantium facets, polished silver/gold highlights). Chainmail: offset
  ring rows (lit top, side, shadow) over a dark underlayer, a few transparent gaps, leather trims at collar, belt, hem
  and cuffs. All built by `armor.py` `Layer`.

## 8. Workflow for artists

```
python -I tools/pixelart/inventory.py                 # refresh docs/art/INVENTORY.csv, see your batch
python -I tools/pixelart/pix.py swatches out.png      # the ramps
python -I tools/pixelart/pix.py sheet out.png <files or dirs> --scale 8
python -I tools/pixelart/pix.py validate src/main/resources/assets/hardscrabble/textures
./gradlew build                                       # TexturesTest must pass
```

1. Write a small script for your batch (keep it under `tools/pixelart/`) that draws each texture from ASCII grids with
   `pix.render(grids, digit=<material ramp>, lower=..., upper=...)` and saves it with `pix.save`. Share one grid per
   shape across materials, as `shapes.py` does.
2. Render a contact sheet and **look at it with the Read tool** at 8x and at the 1x/2x copies under each preview (the 1x
   copy on slot grey is exactly what the player sees). Iterate until each texture is recognisable at 1x.
3. `pix.validate` must print only `OK` or `WARN` lines for your files.
4. Commit textures and the script together.

## 9. Key decisions

- Light from the top-left, 6-shade hue-shifted ramps, outline shade 0 bottom/right and 1 top/left: matches vanilla so our
  items sit next to vanilla copper and iron tools without looking foreign.
- One silhouette per tool type across all materials: players learn the shape once; the material reads from the ramp.
- Material identities: flint dark grey, obsidian purple-black, copper orange, silver cool white, gold yellow, iron neutral
  grey, rusted iron grey-brown with orange mids, ancient metal olive bronze, mithril pale blue, adamantium deep green.
- Full ore textures with an original stone host (no model change). An overlay model referencing `minecraft:block/stone` at
  runtime would blend better with resource packs; it needs a datagen change and is left as a later option.
