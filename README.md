<p align="right"><b>English</b> · <a href="README.fr.md">Français</a></p>

# Hardscrabble

A hardcore survival overhaul for **Minecraft Java 26.3 / NeoForge**: slow, metal-by-metal progression, real hunger,
new monsters, and a dimension under the bedrock.

<p align="center"><img src="docs/media/mobs/lineup.png" width="100%" alt="Every Hardscrabble mob in one line-up"></p>

Hardscrabble is inspired by **MITE** (*Minecraft Is Too Easy*) by Avernite. It is **not affiliated** with MITE or its
author. It is a clean-room reimplementation written from specifications and MITE's public changelog: no original MITE
code or assets are used (see [`PROVENANCE.md`](PROVENANCE.md)). Every texture and model is original.

Version `0.1.0-alpha`. Feature-complete against its roadmap and through a first playtest; wider testing still to come (see [Status](#status-and-known-gaps)).

## Highlights

<table>
<tr>
<td width="25%"><img src="docs/media/mechanics/no_bare_hand_logs.gif" width="100%"><br>No bare-hand logs: start with flint and a hatchet.</td>
<td width="25%"><img src="docs/media/ui/craft_workbench.gif" width="100%"><br>Timed crafting, 9 workbenches gating each metal tier.</td>
<td width="25%"><img src="docs/media/ui/furnace_too_cold.png" width="100%"><br>Furnaces have heat levels; better ores need hotter furnaces.</td>
<td width="25%"><img src="docs/media/ui/hud_malnourished.png" width="100%"><br>Satiety and nutrition: protein and phytonutrients matter.</td>
</tr>
<tr>
<td><img src="docs/media/mobs/dire_wolf_pack.gif" width="100%"><br>29 new mobs with their own models.</td>
<td><img src="docs/media/world/moon_rise.gif" width="100%"><br>Lunar calendar: blood, blue and harvest moons.</td>
<td><img src="docs/media/world/portal_travel.png" width="100%"><br>The Underworld, reached by a portal built on bedrock.</td>
<td><img src="docs/media/ui/death.png" width="100%"><br>Few hearts at low level, death penalty, 120 s forced respawn.</td>
</tr>
</table>

## Contents

[Progression & crafting](#progression--crafting) ·
[Survival](#survival) ·
[Combat & armour](#combat--armour) ·
[Mobs](#mobs) ·
[World](#world) ·
[Interfaces](#interfaces) ·
[Configuration](#configuration) ·
[Install & play](#install--play) ·
[Testing](#testing) ·
[Status](#status-and-known-gaps) ·
[Credits](#credits) ·
[Licences](#licences)

## Progression & crafting

Flint → copper → silver / gold → iron → ancient metal → mithril → adamantium. Logs cannot be broken by hand; gravel gives
flint shards for your first hatchet, knife, axe and shovel. Wooden (except the shovel), stone and diamond tools are removed.

Crafting takes time: hold the result slot until the bar fills. Each metal needs its own workbench, and each workbench
needs the previous one, so no tier can be skipped. A workbench can aim for a higher quality (8 levels) at an XP cost.

<table>
<tr>
<td width="50%"><img src="docs/media/ui/craft_inventory.gif" width="100%"><br>Timed crafting in the 2x2 grid.</td>
<td width="50%"><img src="docs/media/ui/craft_workbench.gif" width="100%"><br>Copper workbench with the target quality button and its XP cost.</td>
</tr>
<tr>
<td><img src="docs/media/ui/craft_refused.png" width="100%"><br>An iron recipe on a copper workbench is refused…</td>
<td><img src="docs/media/ui/craft_refused_hud.png" width="100%"><br>…and the action bar names the workbench needed.</td>
</tr>
</table>

<p align="center"><img src="docs/media/items/tools.png" width="760" alt="Tools per material"><br>
<em>Tools per material: pickaxes, shovels, axes, hatchets, hoes, mattocks, scythes, shears, fishing rods.</em></p>

78 new tools and weapons (hatchets, knives, daggers, clubs, battle axes, war hammers, mattocks, scythes, shears in every
metal…) join the reworked vanilla copper, iron and gold tools, plus a fishing rod per material. Durability is ×100 and
wear depends on block hardness; tools have reach and can parry.

<table>
<tr>
<td width="50%"><img src="docs/media/items/workbenches.png" width="100%"><br>The nine workbenches that gate each metal tier.</td>
<td width="50%"><img src="docs/media/items/spin.gif" width="100%"><br>An adamantium-clad armour stand inside the ring of workbenches.</td>
</tr>
<tr>
<td><img src="docs/media/items/furnaces.png" width="100%"><br>Five heat-level furnaces, from the clay oven to the netherrack furnace.</td>
<td><img src="docs/media/items/metals.png" width="100%"><br>Ore, raw metal, nugget, ingot, coin, chain and block per metal.</td>
</tr>
</table>

<details>
<summary>All item sheets (blocks, materials, food…)</summary>

<table>
<tr>
<td width="33%"><img src="docs/media/items/ores.png" width="100%"><br>Silver, mithril and adamantium ores, stone and deepslate variants</td>
<td width="33%"><img src="docs/media/items/metal_blocks.png" width="100%"><br>Storage blocks of every metal</td>
<td width="33%"><img src="docs/media/items/doors_bars.png" width="100%"><br>Metal doors and bars</td>
</tr>
<tr>
<td><img src="docs/media/items/anvils.png" width="100%"><br>Metal anvils, repaired with nuggets of their own metal</td>
<td><img src="docs/media/items/strongboxes.png" width="100%"><br>Strongboxes in seven metals</td>
<td><img src="docs/media/items/runestones.png" width="100%"><br>Mithril and adamantium runestones and the emerald enchanting table</td>
</tr>
<tr>
<td><img src="docs/media/items/buckets.png" width="100%"><br>Metal buckets, empty or holding water, lava, milk or stone</td>
<td><img src="docs/media/items/coins.png" width="100%"><br>The six coins</td>
<td><img src="docs/media/items/shards.png" width="100%"><br>Shards: flint, obsidian, glass, quartz, emerald, diamond</td>
</tr>
<tr>
<td><img src="docs/media/items/food.png" width="100%"><br>New foods, bowls and soups</td>
<td><img src="docs/media/items/crops.png" width="100%"><br>Onions at every stage, and dead wheat, carrots, potatoes and onions</td>
<td><img src="docs/media/items/misc.png" width="100%"><br>Mob drops, enchanting and disenchanting bottles, manure, silver horse armour</td>
</tr>
<tr>
<td><img src="docs/media/items/mantle_core.png" width="100%"><br>Mantle and Core, the blocks under the Underworld and the Nether</td>
<td></td><td></td>
</tr>
</table>
</details>

## Survival

You start with 3 hearts; maximum health grows with your level up to 10 hearts. The XP curve is MITE's, levels can go
negative, and the food bar is capped by level. Hunger tracks satiety and nutrition, protein and phytonutrients, insulin
and humidity; natural regeneration is slow. Sleep never speeds up time on a dedicated server.

<table>
<tr>
<td width="50%"><img src="docs/media/ui/hud.png" width="100%"><br>HUD at level 3: few hearts, MITE level number, food bar capped by level.</td>
<td width="50%"><img src="docs/media/ui/hud_malnourished.png" width="100%"><br>Malnourished: P and V markers (no protein, no phytonutrients) and the one-time hint.</td>
</tr>
<tr>
<td><img src="docs/media/ui/tooltip_food.png" width="100%"><br>Food tooltip: satiety, nutrition and nutrients.</td>
<td><img src="docs/media/ui/death.png" width="100%"><br>Death screen with the 120 s forced respawn countdown.</td>
</tr>
</table>

Witches can curse you; the curse icon appears once you have discovered it, and a disenchanting bottle lifts it.

<table>
<tr>
<td width="33%"><img src="docs/media/ui/hud_curse.png" width="100%"><br>A witch's curse: icon top left, messages in chat</td>
<td width="33%"><img src="docs/media/ui/hud_curse_tooltip.png" width="100%"><br>Hovering the icon in an inventory names the curse</td>
<td width="33%"><img src="docs/media/ui/chat_hints.png" width="100%"><br>One-time contextual hints, and a disenchanting bottle lifting the curse</td>
</tr>
</table>

### World mechanics

<table>
<tr>
<td width="33%"><img src="docs/media/mechanics/no_bare_hand_logs.gif" width="100%"><br>Punching a log does nothing; a flint hatchet chops it</td>
<td width="33%"><img src="docs/media/mechanics/gravel_flint.gif" width="100%"><br>Gravel mostly drops itself, sometimes flint shards or nuggets</td>
<td width="33%"><img src="docs/media/mechanics/dirt_gravity.gif" width="100%"><br>Remove its support and dirt falls</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/campfire_goes_out.gif" width="100%"><br>Too many foods on a campfire put it out</td>
<td><img src="docs/media/mechanics/gem_sacrifice.gif" width="100%"><br>Right-click gems to sacrifice them for XP, 3 emeralds a day at most</td>
<td><img src="docs/media/mechanics/crop_blight.gif" width="100%"><br>Blight spreads and withers crops (random ticks sped up)</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/zombies_dig.gif" width="100%"><br>Zombies dig through dirt to reach the player</td>
<td><img src="docs/media/mechanics/squid_smashes_boat.gif" width="100%"><br>A squid hunts the boater and smashes the boat</td>
<td><img src="docs/media/mechanics/horse_shies.gif" width="100%"><br>A wild horse lets a sneaking player close, then shies away</td>
</tr>
</table>

Also: crops grow better in rows, livestock eats its pasture, villager trades do not restock and give no XP, monsters
spawn up to block light 7.

## Combat & armour

Weapons have their own damage and speed; armour protection drops as it wears. Nine plate armour sets and eight chainmail
sets, bows with 10 arrow tips, and silver deals extra damage to undead.

<p align="center"><img src="docs/media/items/armour_worn.png" width="640"><br>
<em>Full mithril armour, mithril battle axe and dagger.</em></p>

<table>
<tr>
<td width="50%"><img src="docs/media/items/armour_sets.png" width="100%"><br>The nine plate armour sets, from leather to adamantium</td>
<td width="50%"><img src="docs/media/items/chainmail_sets.png" width="100%"><br>The eight chainmail sets, from copper to adamantium</td>
</tr>
<tr>
<td><img src="docs/media/items/weapons.png" width="100%"><br>Swords, daggers, knives, club and cudgel, battle axes, war hammers, bows and the 10 arrow tips</td>
<td><img src="docs/media/ui/tooltip_tool.png" width="49%"> <img src="docs/media/ui/tooltip_armor.png" width="49%"><br>Tooltips: durability, material, harvest level, quality</td>
</tr>
</table>

## Mobs

29 new mobs, each with its own model (Blockbench sources in `docs/art/models/`). Zombies dig, spiders' eyes glow in the
dark, and deeper levels spawn harder monsters. On a blood moon, frenzied undead, Ghouls and Revenants roam the surface.

<table>
<tr>
<td width="50%"><img src="docs/media/mobs/family_undead.png" width="100%"><br>The undead: bone lords, longdeads, shadow, invisible stalker, revenant, wight, ghoul</td>
<td width="50%"><img src="docs/media/mobs/family_spiders.png" width="100%"><br>Demon spider, phase spider, black widow and wood spider in their cave</td>
</tr>
<tr>
<td><img src="docs/media/mobs/family_elementals.png" width="100%"><br>Clay golem, fire elemental and earth elemental</td>
<td><img src="docs/media/mobs/family_cubes.png" width="100%"><br>The gelatinous cubes: jelly, blob, ooze and pudding</td>
</tr>
<tr>
<td><img src="docs/media/mobs/family_bats.png" width="100%"><br>Nightwing, giant vampire bat and vampire bat</td>
<td><img src="docs/media/mobs/family_silverfish.png" width="100%"><br>Hoary silverfish, netherspawn and copperspine</td>
</tr>
</table>

<table>
<tr>
<td width="33%"><img src="docs/media/mobs/ghoul_walk.gif" width="100%"><br>A ghoul shambles toward you</td>
<td width="33%"><img src="docs/media/mobs/cube_bounce.gif" width="100%"><br>Gelatinous cubes hopping closer</td>
<td width="33%"><img src="docs/media/mobs/fire_elemental_alive.gif" width="100%"><br>A fire elemental, alive</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/shadow_snuffs_lights.gif" width="100%"><br>A Shadow blows out candles and breaks torches</td>
<td><img src="docs/media/mechanics/jelly_digests_items.gif" width="100%"><br>A Jelly digests meat and leather on the ground; iron stays</td>
<td><img src="docs/media/mechanics/earth_elemental_heated.gif" width="100%"><br>Lightning heats an Earth Elemental: it burns and walks in lava</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/fire_elemental_water.gif" width="100%"><br>Water from a bucket or bowl hurts a Fire Elemental</td>
<td><img src="docs/media/mobs/spider_eyes_night.png" width="100%"><br>Spider eyes glowing in the dark</td>
<td></td>
</tr>
</table>

### Dire wolves

Dire wolves hunt in packs. Tamed, they wear a collar and dyeable wolf armour that cracks as it wears out.

<table>
<tr>
<td width="33%"><img src="docs/media/mobs/dire_wolf_pack.gif" width="100%"><br>A dire wolf pack on the hunt</td>
<td width="33%"><img src="docs/media/mobs/dire_wolf_tamed.png" width="100%"><br>Tamed: collar only, dyed armour, sitting</td>
<td width="33%"><img src="docs/media/mobs/dire_wolf_cracked.png" width="100%"><br>Wolf armour cracking as it wears out</td>
</tr>
</table>

### Bestiary

<table>
<tr><th colspan="4">Undead</th></tr>
<tr>
<td width="25%" align="center"><img src="docs/media/mobs/ghoul.png" width="100%"><br>Ghoul</td>
<td width="25%" align="center"><img src="docs/media/mobs/wight.png" width="100%"><br>Wight</td>
<td width="25%" align="center"><img src="docs/media/mobs/revenant.png" width="100%"><br>Revenant (rusted iron armour)</td>
<td width="25%" align="center"><img src="docs/media/mobs/shadow.png" width="100%"><br>Shadow (deep, immaterial)</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/invisible_stalker.png" width="100%"><br>Invisible stalker (outlined)</td>
<td align="center"><img src="docs/media/mobs/longdead.png" width="100%"><br>Longdead</td>
<td align="center"><img src="docs/media/mobs/longdead_guardian.png" width="100%"><br>Longdead guardian</td>
<td align="center"><img src="docs/media/mobs/bone_lord.png" width="100%"><br>Bone lord</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/ancient_bone_lord.png" width="100%"><br>Ancient bone lord</td>
<td></td><td></td><td></td>
</tr>
<tr><th colspan="4">Spiders</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/wood_spider.png" width="100%"><br>Wood spider</td>
<td align="center"><img src="docs/media/mobs/black_widow.png" width="100%"><br>Black widow</td>
<td align="center"><img src="docs/media/mobs/phase_spider.png" width="100%"><br>Phase spider</td>
<td align="center"><img src="docs/media/mobs/demon_spider.png" width="100%"><br>Demon spider</td>
</tr>
<tr><th colspan="4">Elementals and golems</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/clay_golem.png" width="100%"><br>Clay golem</td>
<td align="center"><img src="docs/media/mobs/earth_elemental.png" width="100%"><br>Earth elemental</td>
<td align="center"><img src="docs/media/mobs/fire_elemental.png" width="100%"><br>Fire elemental (Nether)</td>
<td align="center"><img src="docs/media/mobs/infernal_creeper.png" width="100%"><br>Infernal creeper</td>
</tr>
<tr><th colspan="4">Gelatinous cubes</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/jelly.png" width="100%"><br>Jelly</td>
<td align="center"><img src="docs/media/mobs/blob.png" width="100%"><br>Blob</td>
<td align="center"><img src="docs/media/mobs/ooze.png" width="100%"><br>Ooze</td>
<td align="center"><img src="docs/media/mobs/pudding.png" width="100%"><br>Pudding</td>
</tr>
<tr><th colspan="4">Bats and silverfish</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/vampire_bat.png" width="100%"><br>Vampire bat</td>
<td align="center"><img src="docs/media/mobs/giant_vampire_bat.png" width="100%"><br>Giant vampire bat</td>
<td align="center"><img src="docs/media/mobs/nightwing.png" width="100%"><br>Nightwing (Nether)</td>
<td align="center"><img src="docs/media/mobs/copperspine.png" width="100%"><br>Copperspine</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/hoary_silverfish.png" width="100%"><br>Hoary silverfish</td>
<td align="center"><img src="docs/media/mobs/netherspawn.png" width="100%"><br>Netherspawn</td>
<td></td><td></td>
</tr>
<tr><th colspan="4">Wolves</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/dire_wolf.png" width="100%"><br>Dire wolf</td>
<td align="center"><img src="docs/media/mobs/hellhound.png" width="100%"><br>Hellhound (Nether)</td>
<td></td><td></td>
</tr>
</table>

## World

### The Underworld

A cavern dimension under the Overworld bedrock, in grey fog that thickens over a 32-day cycle. A portal built on
Overworld bedrock leads there; a portal anywhere else returns to the Overworld spawn. Adamantium is only found deep in
the Underworld, above an unbreakable Mantle floor. Underworld dungeons hold Longdead spawners and loot.

<p align="center"><img src="docs/media/world/underworld_landscape.png" width="760"></p>

<table>
<tr>
<td width="33%"><img src="docs/media/world/portal_bedrock.png" width="100%"><br>A portal built on Overworld bedrock</td>
<td width="33%"><img src="docs/media/world/portal_travel.png" width="100%"><br>Stepping out into the Underworld</td>
<td width="33%"><img src="docs/media/world/portal_arrival.png" width="100%"><br>The exit portal in the Underworld</td>
</tr>
<tr>
<td><img src="docs/media/world/underworld_fog_cycle.gif" width="100%"><br>The fog thickens over a 32-day cycle</td>
<td><img src="docs/media/world/underworld_fog_dense.png" width="100%"><br>Fog at its densest, on the blood moon day</td>
<td><img src="docs/media/world/underworld_dungeon.png" width="100%"><br>Underworld dungeon: Longdead spawner and loot chest</td>
</tr>
<tr>
<td><img src="docs/media/world/underworld_mantle.png" width="100%"><br>Bottom, stone removed: bedrock plate (back), Mantle floor (front)</td>
<td><img src="docs/media/world/adamantium_ore.png" width="100%"><br>Adamantium ore, deep in the Underworld (rock removed)</td>
<td><img src="docs/media/world/nether_core.png" width="100%"><br>Nether floor cut open: the Core layer under the bedrock</td>
</tr>
</table>

### Moons and weather

Full moons fall every 8 days. A blood moon comes every 32 days, announced by a harvest moon; a blue moon every 128 days,
announced by a moon dog, stops hostile spawns under the open sky. The clock tells you tonight's moon. About one morning in
eight brings thick fog.

<table>
<tr>
<td width="33%"><img src="docs/media/world/moon_rise.gif" width="100%"><br>A blood moon rising at dusk</td>
<td width="33%"><img src="docs/media/world/moon_blood.png" width="100%"><br>Blood moon: frenzied undead roam the surface</td>
<td width="33%"><img src="docs/media/world/moon_blue.png" width="100%"><br>Blue moon: no hostile spawns under the open sky</td>
</tr>
<tr>
<td><img src="docs/media/world/moon_normal.png" width="100%"><br>A normal full moon</td>
<td><img src="docs/media/world/moon_harvest.png" width="100%"><br>Harvest moon, 8 days before a blood moon</td>
<td><img src="docs/media/world/moon_dog.png" width="100%"><br>Moon dog: heralds a blue moon 8 days later</td>
</tr>
<tr>
<td><img src="docs/media/world/morning_fog.png" width="100%"><br>Thick morning fog</td>
<td><img src="docs/media/ui/tooltip_clock.png" width="100%"><br>The clock announces tonight's moon</td>
<td></td>
</tr>
</table>

### Villages, runegates, ores

Villages only generate once someone has crafted an iron pickaxe, and they are abandoned, with dead crops. A runegate (a
portal with a runestone on each corner) sends an empty-handed player far away. Silver, mithril and adamantium ores follow
MITE's depth distribution.

<table>
<tr>
<td width="33%"><img src="docs/media/world/village.png" width="100%"><br>An abandoned village</td>
<td width="33%"><img src="docs/media/world/village_dead_crops.png" width="100%"><br>Village farms hold dead crops</td>
<td width="33%"><img src="docs/media/world/runegate.png" width="100%"><br>Runegate</td>
</tr>
<tr>
<td><img src="docs/media/world/silver_ore.png" width="100%"><br>Silver ore (rock removed)</td>
<td><img src="docs/media/world/mithril_ore.png" width="100%"><br>Mithril ore, deep in the Overworld (rock removed)</td>
<td></td>
</tr>
</table>

## Interfaces

<table>
<tr>
<td width="33%"><img src="docs/media/ui/furnace_clay.png" width="100%"><br>Clay oven: coal is too hot, only sticks or charcoal</td>
<td width="33%"><img src="docs/media/ui/furnace_stone.png" width="100%"><br>Stone furnace smelting raw copper at heat 2/2</td>
<td width="33%"><img src="docs/media/ui/furnace_too_cold.png" width="100%"><br>Raw mithril needs heat 3: too cold</td>
</tr>
<tr>
<td><img src="docs/media/ui/anvil.png" width="100%"><br>Copper anvil: repair paid in copper nuggets</td>
<td><img src="docs/media/ui/enchanting.png" width="100%"><br>Enchanting table: costs paid in MITE XP points</td>
<td><img src="docs/media/ui/advancements.png" width="100%"><br>The mod's advancement tab (49 advancements plus the tab root)</td>
</tr>
<tr>
<td><img src="docs/media/ui/strongbox.png" width="100%"><br>Silver strongbox: only its owner can open it</td>
<td><img src="docs/media/ui/strongbox_locked.png" width="100%"><br>Someone else's strongbox stays shut</td>
<td><img src="docs/media/ui/create_world.png" width="100%"><br>World creation starts on Hard</td>
</tr>
<tr>
<td><img src="docs/media/ui/chat_commands.png" width="100%"><br><code>/commands</code> and <code>/villages</code> (village unlock conditions)</td>
<td><img src="docs/media/ui/chat_stats.png" width="100%"><br><code>/stats</code> and <code>/state</code>: level, bonuses, nutrition, curse, moon</td>
<td><img src="docs/media/ui/death_fr.png" width="100%"><br>Every text is translated (English and French)</td>
</tr>
</table>

The enchanting table has 17 new enchantments; metal anvils repair with nuggets; a disenchanting bottle strips
enchantments.

## Configuration

Open **Mods → Hardscrabble → Config** for the in-game editor, or edit the files.

<table>
<tr>
<td width="33%"><img src="docs/media/ui/mods_config.png" width="100%"><br>Mods → Hardscrabble → Config</td>
<td width="33%"><img src="docs/media/ui/config_root.png" width="100%"><br>Client settings and synced world rules</td>
<td width="33%"><img src="docs/media/ui/config.png" width="100%"><br>Synced rules section</td>
</tr>
</table>

- **Server rules**: `config/hardscrabble-synced.toml` holds the defaults, overridden per world by
  `saves/<world>/syncedconfig/hardscrabble-synced.toml`; it is synced to clients. Every key has an English and a French
  comment.
- **Client**: `config/hardscrabble-client.toml` (`hints.contextual_hints`: one-time help messages, HUD indicators,
  durability as percent).

Useful keys:

| Key | Effect |
|---|---|
| `rules.strict_mode` | No Creative or cheats, keepInventory off, no Spectator (default on) |
| `rules.allowed_ops` | Players exempt from strict mode |
| `rules.lock_difficulty` | Forces Hard difficulty and forbids changing it |
| `rules.profile` | `CUSTOM` (default, keys as written), `STRICT_R196`, `MODERN` (locks off, lighter death penalty), `SERVER` |
| `rules.respawn_timer_seconds` | Forced respawn delay (120) |
| `player.death_penalty` | Death XP rule: `R196_CODE`, `WIKI_THIRD`, `MCMOD_LEVEL` |
| `metabolism.hunger_global_multiplier` | Hunger speed |
| `lunar.blood_moon_period` | Days between blood moons (32) |
| `structures.village_lock` | Villages wait for the first iron pickaxe |
| `equipment.elytra_firework_boost` | Firework rockets boost a gliding elytra (off: cannot fire them while gliding) |

> [!WARNING]
> `rules.dev_profile` lifts strict mode and enables `/mitedev` for operators. It is a developer escape hatch: never
> enable it on a real world.

## Install & play

Requirements: **Minecraft Java 26.3**, **NeoForge 26.3.0.88**, **Java 25**. There is no released jar yet; build it from
source:

```
./gradlew runClient        # client, create a Survival world
./gradlew runServer        # dedicated server (needs run/eula.txt accepted by you)
./gradlew build            # jar in build/libs/
```

### Saves

Saved data carries a schema version and older versions are migrated on load. Opening a world first played without the mod
is not supported: it loads with default values and players get a warning. Removing the mod from a world is not supported.
Worlds created before the mod id became `hardscrabble` (2026-10-19, it was `mite`) do not load: their blocks, items and
saved data use the old namespace. They were dev-only; start a new world.

## Testing

```
python -I tools/test_all.py      # everything below except the client run (add --client for it)
./gradlew build                  # compile + JUnit (pure formulas, asset checks)
./gradlew runGameTestServer      # in-game tests (rules, items, mobs, registry audit)
python -I tools/e2e/run_e2e.py   # dedicated server driven over RCON: worldgen, Underworld, portals, structures,
                                 # tick time, persistence across a restart, log errors (report in run-e2e/)
python -I tools/e2e/run_client_e2e.py  # real client on a copy of that world: HUD, inventory, every mob rendered,
                                       # mob model close-ups ("models" step), blood moon, Underworld, Nether;
                                       # screenshots in run/screenshots/e2e_*.png
```

On 2026-10-19: 42 JUnit tests, 529 GameTests and 36 end-to-end checks on a real dedicated server, all green.

The pictures in this README are produced by a scripted client run. To regenerate them (needs the world from
`run_e2e.py`):

```
python -I tools/showcase/run_showcase.py [group]   # group: mobs, items, ui, world, mechanics (comma-separated) or all
```

Each `docs/media/<group>/CAPTIONS.md` holds the English and French captions.

## Feature overview

| Area | What exists |
|---|---|
| Progression | Flint → copper → silver/gold → iron → ancient metal → mithril → adamantium; no bare-hand logs; wood/stone/diamond tools removed |
| Tools | 78 new tools and weapons plus vanilla copper/iron/gold ones and a fishing rod per material; durability ×100, wear by hardness, quality (8 levels, paid in XP), reach, parry |
| Crafting | Timed crafting (2×2 and benches), 9 workbenches gating metals, restricted 2×2, crafter restriction |
| Metallurgy | Heat-level furnaces (clay → netherrack), fuel heat, MITE ore distribution with depth-scaled veins |
| Player | MITE XP curve, negative levels, max health 6 → 20 by level, death penalty, 120 s forced respawn, gem sacrifice |
| Hunger | Satiety + nutrition, protein/phyto, insulin, humidity, slow regen, starvation, MITE sleep, bowls, HUD |
| Combat | Weapon damage/speed, 9 plate armour sets + 8 chainmail sets, wear lowers protection, bows, 10 arrow tips, silver vs undead |
| Enchanting | MITE table (XP points), 17 enchantments, metal anvils (nugget repair), disenchanting bottle, strongboxes |
| Mobs | 29 new mobs (Ghoul, Wight, Revenant, Shadow, Longdead, elementals, Bone Lords, cubes, bats…) with their own models, digging zombies, depth spawns |
| World | Underworld dimension, bedrock-linked portals, Underworld dungeons, locked villages, runegates, dirt gravity, Nether core |
| Time | Lunar calendar (blood/blue/harvest moons), moon colours, witch curses, 50 advancements (49 plus the tab root) |

## Status and known gaps

Every milestone of the roadmap through M6, plus the post-1.0 backlog, is implemented and the automated tests pass. A first
playtest by the author (2026-10-19) rated every inferred balance value as right and its bugs are fixed. Not done yet:
wider playtests, original audio and a release.

- **No original audio.** Mob voices are vanilla sounds replayed at another pitch and volume, with English and French
  subtitles.
- **Inferred balance values.** Where the specification is silent, values are inferred (checked in the first playtest) and marked `[INFÉRÉ]` or
  `[NON VÉRIFIÉ]` in the code.
- Every texture is original pixel art (`docs/art/`). Mob models come from Blockbench sources in
  `docs/art/models/*.bbmodel`; `tools/pixelart/models3d.py` generates the Java layers and skins.

## Contributing

See [`DEVELOPMENT.md`](DEVELOPMENT.md) for commands, layout and conventions, and [`PROVENANCE.md`](PROVENANCE.md) for
the clean-room rules every contribution must follow.

## Credits

- **Code, textures, mob models, sounds and documentation** were written by **Claude** (Anthropic's AI, Claude Opus 5.5
  through Claude Code), directed, playtested and reviewed by the project owner, [nogaruki](https://github.com/nogaruki).
- **MITE** (*Minecraft Is Too Easy*) by **Avernite** is the inspiration for this mod. Hardscrabble is not affiliated with
  or endorsed by Avernite; no MITE code or assets are included.
- Minecraft is a trademark of Mojang Studios. Hardscrabble is not an official Minecraft product.
- Built on [NeoForge](https://neoforged.net/).

## Licences

- Code: GNU LGPL v3 only (`LGPL-3.0-only`): see [`LICENSE`](LICENSE) and the GPL v3 text it refers to in [`COPYING`](COPYING).
- Assets (textures, structure files, art documentation): [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
  attribution "Hardscrabble authors"; see [`LICENSE-ASSETS`](LICENSE-ASSETS).
- Provenance and clean-room rules: [`PROVENANCE.md`](PROVENANCE.md).
