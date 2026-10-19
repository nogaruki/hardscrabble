# Development guide

Hardscrabble, a MITE-inspired port for Minecraft 26.3 / NeoForge 26.3.0.88, Java 25. Mod id and resource namespace:
`hardscrabble` (`MiteMod.MODID`); the Java packages (`dev.mite...`) and class names (`Mite*`) keep the old name on purpose.
Specs live in `../docs/` (01 functional, 02 technical,
03 roadmap) and `../analysis/` (06 feature matrix). Requirement ids (EF-xxx, ET-xxx, US-xxx) come from there.

## Commands

- `./gradlew build` — compile + JUnit (`src/test`, pure `core` formulas only).
- `./gradlew runData` — regenerate `src/generated/resources` (commit the result, never hand-edit it).
- `./gradlew runGameTestServer` — run all GameTests; result line in `run-gametest/logs/latest.log` ("All N required tests passed").
- Developer profile: `rules.dev_profile = true` in the world's `hardscrabble-synced.toml` (`run/config/` in dev) lifts strict mode
  (EF-REG-02) and enables `/mitedev` for operators (EF-REG-05).
- `bash tools/check-core-imports.sh` (ET-Q-06) and `bash tools/check-cleanroom.sh` (ET-Q-07, hand-written JSON): CI runs them
  with build, `runData` + `git diff --exit-code src/generated` and the GameTests (`.github/workflows/build.yml`).
- API lookup: decompiled 26.3 sources are extracted in `../ref-src/` (Minecraft under `net/`, NeoForge under `nf/`).

## Layout

- `core/` — pure Java formulas (`Formulas`, `Material`, `ToolType`, `Quality`). No `net.minecraft` / `net.neoforged` imports.
- `material/` — shared raw materials (shards, nuggets, ingots). Reuse them, do not duplicate.
- One package per domain (`harvest/`, `crafting/`, `metabolism/`, `metallurgy/`, ...). A domain owns its own
  `DeferredRegister`s and exposes `static void register(IEventBus modBus)`.
- Shared touch points (keep edits to one line or one block each):
  - `MiteMod` constructor: `XxxContent.register(modBus);`
  - `gametest/MiteGameTests.register`: `XxxTests.register();` (calls `MiteGameTests.add(...)`)
  - `datagen/MiteDataGen`: providers (`gather`) and registry entries such as recipes and loot tables (`gatherEntries`)
  - `datagen/MiteLang`: one `// <domain>` block of `t(key, en, fr)` lines
  - `config/MiteConfig`: one `push("<domain>")` / `pop()` section
  - `src/main/resources/hardscrabble.mixins.json`: mixin class names (package `dev.mite.mixin`)

## Rules

- Clean-room: implement from the specs only. Never open or copy the original MITE classes (`../original/`).
- API before mixin (docs/02 §5). Mixins call a static service method; no game rules inside the mixin body.
- Values from the spec tables go in data maps or Java tables, with the EF id in a comment.
- Every gameplay rule gets at least one GameTest (structure `hardscrabble:platform`: 7x5x7, stone floor at y=0).
- Textures: follow `docs/art/STYLE.md` and draw with `tools/pixelart/` (ramps in `palettes.py`, tool shapes in `shapes.py`,
  `pix.py sheet|validate`); who owns which file is in `docs/art/INVENTORY.csv`. `tools/placeholder_textures.py` is legacy.

## Lessons from M1 (read before coding)

- Player level: always `dev.mite.player.PlayerLevel.get(player)`, never `player.experienceLevel`.
- Datagen providers: override `getName()` with a unique name, duplicate names crash `runData`.
- Overriding vanilla data: the registry-entry route refuses duplicate vanilla ids. Either use
  `event.gatherFor(MiteMod.MODID, "minecraft")` in `MiteDataGen.gatherEntries` (as metallurgy does for recipes) or
  write a plain JSON file. To remove a vanilla recipe, override it (and its unlock advancement) with a file holding only
  the condition `neoforge:never` (`neoforge:false` does not exist in 26.3).
- Synced attachments: `getData` syncs on first access and crashes for connectionless fake players. Follow
  `MetabolismService.get` (transient default when `connection == null`).
- Existing services to reuse: `HarvestRules` (hardness data map, canHarvest), `ToolStats` (item -> tool type/material),
  `MetabolismService` (energy, food caps, max health), `TimedCrafting` / `CraftingContent` (craft difficulty, `hardscrabble:material`
  item data map), `FurnaceHeat` (fuel/smelt/furnace heat data maps).
- Tags: block tags go through the single `datagen/MiteBlockTags` provider (static `blockTags(...)` per domain). Two
  providers writing the same tag file silently overwrite each other.
  Entity type tags likewise go through `datagen/MiteEntityTypeTags` (static `entityTypeTags(...)` per domain). Use
  `addOptional` / `RegisteredCondition` to reference another domain's entries that may not be registered yet.
- Underworld attachments: biome modifiers target the biome tag `hardscrabble:is_underworld` (written by `underworld/UnderworldData.BiomeTags`,
  holding `hardscrabble:underworld` as an optional entry), never the biome id, so they load with or without the dimension.
- Global loot modifiers must ignore block loot (`LootContextParams.BLOCK_STATE`) unless they target blocks on purpose.
- The GameTest server builds only the flat preset dimensions: datapack dimensions such as `hardscrabble:underworld`
  (`dev.mite.dimension.UnderworldContent.LEVEL`) do not exist there. Test registry entries, or build a
  `WorldGenerationContext` from the noise settings (see `DimensionTests`), instead of `server.getLevel(...)`.
- The GameTest server's default game mode is Creative: a mock `ServerPlayer` starts in Creative unless the test (or
  `MetabolismTests.player`) switches it. Strict mode refuses `setGameMode(CREATIVE)` and all op commands from players.
- `minecraft:enchantable/*` item tags are written only by `enchanting/EnchantingData.ItemTagsGen` (MITE tools from `ToolStats`,
  armor from `CombatContent.ARMORS`); new tools or armor show up there automatically. Code-driven enchantment effects are
  `hardscrabble:*` effect components holding a `LevelBasedValue`, read with `EnchantmentEffects.value(stack|entity, TYPE)`.
- One provider per tag registry: `MiteBlockTags`, `MiteItemTags`, `MiteEntityTypeTags`, `MiteBiomeTags` in `datagen/`.
  Domains expose a static `blockTags/itemTags/entityTypeTags/biomeTags(Function<TagKey<T>, TagAppender<T>> tag)`.
- Deprecated 26.3 APIs (compiled with `-Xlint:deprecation`, keep it at zero): `BuiltInRegistries.X.wrapAsHolder(x)` /
  `getResourceKey(x)` instead of `builtInRegistryHolder()`, `DeferredHolder.key()` instead of `getKey()`,
  `MiteGameTests.levelPlayer(h)` instead of `makeMockServerPlayerInLevel()`.
- `gametest/AuditTests` checks every hardscrabble item (item definition, hardscrabble models and textures, EN and FR name), block loot tables
  and recipe results: new content must pass it. A block without drops on purpose uses `noLootTable()` or `AuditTests.NO_LOOT`.
- Vanilla villager trades: override the `villager_trade` entry in place (same id, `trading/TradingData`, under `gatherFor("minecraft")`)
  so the vanilla tags stay valid and `trading_registry_audit` sees it. The GameTest server enables the opt-in trade_rebalance experiment.
- Config keys: declare them with `doc(key, en, fr)` in `MiteConfig` (or `MiteClientConfig`) and add the key's EN/FR name to the
  `// config` table of `MiteLang`; `runData` fails on a missing name and the `config_keys_documented` GameTest checks the rest.
- One-time help messages (EF-UI-06): `rules.Hints.show(player, id)` plus a `hardscrabble.hint.<id>` lang key.
- Overlay refusals (EF-PRG-03) go through `TimedCrafting.refuse` (throttled to 2 s, safe for fake players).
- GameTest structures get a barrier roof (no sky access) and sit in random biomes. A weather test uses
  `MiteGameTests.addInRain` (own batch, rain, sky access) and sets its biome with `FillBiomeCommand.fill`.
- Summoned minions (`SummonGoal`) carry the entity tag `hardscrabble.summoned` (`SummonGoal.summoned`): witch wolves are untamable
  and despawn like monsters, Bone Lords recall theirs.
- Environment attributes (sky, fog, moon phase, sky light) are cached per tick: a GameTest that moves the clock calls
  `level.environmentAttributes().invalidateTickCache()` (see `LunarTests.onTicks`). Code-driven layers go through
  `lunar/MiteSky.addLayers` (EnvironmentAttributeSystemMixin), data-driven ones through the `minecraft:moon` timeline override.
- Payloads to clients: check `player.connection.hasChannel(TYPE)` first; GameTest mock players have no channel and
  `PacketDistributor.sendToPlayersInDimension` crashes the server on them.
- Saved data (ENF-SAV-01): every serialized attachment and SavedData codec is wrapped in
  `dev.mite.attach.Migrations.versioned("<schema>", mapCodec)`. Changing a saved structure means adding an upgrade step for
  that schema in `Migrations.STEPS` and a GameTest that decodes the previous version (see `attach/SaveTests`).
- Hand-written JSON: only vanilla overrides under `src/main/resources/data/minecraft/` (recipe removals with `neoforge:never`,
  structure sets, village pools, one villager trade) and `hardscrabble.mixins.json`. Everything else comes from datagen;
  `tools/check-cleanroom.sh` fails otherwise.
- C2S payloads: decode raw values and validate them on the server (ignore and log what is out of range), never index an
  array straight from the buffer (see `CraftQuality.Target`).
