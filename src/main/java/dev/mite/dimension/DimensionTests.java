package dev.mite.dimension;

import dev.mite.gametest.MiteGameTests;
import dev.mite.worldgen.WorldgenData;
import dev.mite.dimension.runegate.RunestoneBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/** GameTests for EF-DIM-01 (dimension, Mantle, layout, mycelium), EF-DIM-02 (portal routing, piglins), EF-DIM-03 (ores), EF-DIM-06 (rune names). */
public final class DimensionTests {
    private DimensionTests() {}

    public static void register() {
        // EF-DIM-01: type, terrain settings and biome are registered. The GameTest server only builds the flat preset dimensions,
        // so the level itself (datapack level_stem hardscrabble:underworld) only exists in normal worlds.
        MiteGameTests.add("dimension_underworld_exists", 20, h -> {
            var registries = h.getLevel().registryAccess();
            var type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getValue(UnderworldContent.TYPE);
            h.assertTrue(type != null, "dimension type hardscrabble:underworld missing");
            h.assertTrue(type.minY() == UnderworldContent.MIN_Y && type.height() == UnderworldContent.HEIGHT && type.hasCeiling() && !type.hasSkyLight(), "wrong dimension type");
            h.assertTrue(registries.lookupOrThrow(Registries.NOISE_SETTINGS).get(UnderworldContent.NOISE).isPresent(), "noise settings missing");
            h.assertTrue(registries.lookupOrThrow(Registries.BIOME).get(UnderworldContent.BIOME).isPresent(), "biome missing");
            h.succeed();
        });

        // EF-DIM-02 CA-1: the three destinations.
        MiteGameTests.add("dimension_portal_routing", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.BEDROCK);
            h.setBlock(1, 2, 1, Blocks.OBSIDIAN);
            h.setBlock(1, 3, 1, Blocks.NETHER_PORTAL);
            h.setBlock(1, 4, 1, Blocks.NETHER_PORTAL);
            h.setBlock(5, 2, 5, Blocks.OBSIDIAN);
            h.setBlock(5, 3, 5, Blocks.NETHER_PORTAL);
            h.assertValueEqual(PortalRouting.destination(h.getLevel(), h.absolutePos(new BlockPos(1, 4, 1))), UnderworldContent.LEVEL, "Overworld bedrock");
            h.assertValueEqual(PortalRouting.destination(h.getLevel(), h.absolutePos(new BlockPos(5, 3, 5))), Level.OVERWORLD, "Overworld stone");
            // uneven bedrock: entered through the column over stone, the other bottom column rests on bedrock
            h.setBlock(8, 1, 3, Blocks.BEDROCK);
            h.setBlock(7, 2, 3, Blocks.OBSIDIAN);
            h.setBlock(8, 2, 3, Blocks.OBSIDIAN);
            h.setBlock(7, 3, 3, Blocks.NETHER_PORTAL);
            h.setBlock(8, 3, 3, Blocks.NETHER_PORTAL);
            h.assertValueEqual(PortalRouting.destination(h.getLevel(), h.absolutePos(new BlockPos(7, 3, 3))), UnderworldContent.LEVEL, "partly on bedrock");
            // MX-06 end to end: spawn teleport off bedrock; on bedrock never the vanilla Nether (null here, no Underworld level in GameTests).
            var portal = (NetherPortalBlock) Blocks.NETHER_PORTAL;
            var pig = h.spawn(EntityTypes.PIG, 3, 2, 3);
            TeleportTransition spawn = portal.getPortalDestination(h.getLevel(), pig, h.absolutePos(new BlockPos(5, 3, 5)));
            h.assertTrue(spawn != null && spawn.newLevel().dimension() == Level.OVERWORLD
                    && spawn.position().distanceTo(Vec3.atBottomCenterOf(h.getLevel().getRespawnData().pos())) < 64, "off bedrock: Overworld spawn");
            TeleportTransition down = portal.getPortalDestination(h.getLevel(), pig, h.absolutePos(new BlockPos(1, 4, 1)));
            h.assertTrue(down == null || down.newLevel().dimension() == UnderworldContent.LEVEL, "on bedrock: Underworld");

            int min = UnderworldContent.MIN_Y;
            var mantle = UnderworldContent.MANTLE.get().defaultBlockState();
            h.assertValueEqual(PortalRouting.destination(UnderworldContent.LEVEL, Blocks.BEDROCK.defaultBlockState(), min + 6, min), Level.NETHER, "Underworld bottom bedrock");
            h.assertValueEqual(PortalRouting.destination(UnderworldContent.LEVEL, mantle, min + 2, min), Level.NETHER, "Underworld mantle");
            h.assertValueEqual(PortalRouting.destination(UnderworldContent.LEVEL, Blocks.BEDROCK.defaultBlockState(), min + 100, min), Level.NETHER, "Underworld bedrock plate");
            h.assertValueEqual(PortalRouting.destination(UnderworldContent.LEVEL, Blocks.BEDROCK.defaultBlockState(), 100, min), Level.OVERWORLD, "Underworld roof bedrock");
            h.assertValueEqual(PortalRouting.destination(UnderworldContent.LEVEL, Blocks.STONE.defaultBlockState(), min + 6, min), Level.OVERWORLD, "Underworld stone");
            h.assertValueEqual(PortalRouting.destination(Level.NETHER, Blocks.BEDROCK.defaultBlockState(), 1, 0), Level.OVERWORLD, "Nether bedrock");
            h.succeed();
        });

        // EF-DIM-03: adamantium and the x8 ores are in the biome; CA-1 at least 75 % of adamantium in the lowest quarter.
        MiteGameTests.add("dimension_underworld_ores", 20, h -> {
            var placed = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            var settings = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(UnderworldContent.BIOME).value().getGenerationSettings();
            var adamantium = placed.getValue(WorldgenData.ADAMANTIUM.key());
            h.assertTrue(settings.hasFeature(adamantium), "adamantium missing from the Underworld");
            for (var p : UnderworldData.PLACEMENTS) h.assertTrue(settings.hasFeature(placed.getValue(p.key())), p.name() + " missing");

            var noise = h.getLevel().registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(UnderworldContent.NOISE);
            var biome = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(UnderworldContent.BIOME);
            var context = new WorldGenerationContext(new NoiseBasedChunkGenerator(new FixedBiomeSource(biome), noise),
                    LevelHeightAccessor.create(UnderworldContent.MIN_Y, UnderworldContent.HEIGHT));
            RandomSource random = RandomSource.create(42);
            int[] low = {0};
            int samples = 4000, quarter = UnderworldContent.MIN_Y + UnderworldContent.HEIGHT / 4;
            for (int i = 0; i < samples; i++) if (WorldgenData.ADAMANTIUM.height().sample(random, context) < quarter) low[0]++;
            h.assertTrue(low[0] >= samples * 3 / 4, "adamantium in the lowest quarter: " + low[0] + "/" + samples);
            h.succeed();
        });

        // dom-M-07: the Mantle and the Core are plain permanent blocks: no fire, no light.
        MiteGameTests.add("dimension_mantle_inert", 100, h -> {
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) h.setBlock(x, 1, z, UnderworldContent.MANTLE.get());
            var pig = h.spawn(EntityTypes.PIG, 3, 2, 3);
            h.assertValueEqual(UnderworldContent.MANTLE.get().defaultBlockState().getLightEmission(h.getLevel(), BlockPos.ZERO), 0, "Mantle light");
            h.assertValueEqual(dev.mite.worldgen.WorldgenContent.CORE.get().defaultBlockState().getLightEmission(h.getLevel(), BlockPos.ZERO), 0, "Core light");
            h.runAfterDelay(60, () -> {
                h.assertFalse(pig.isOnFire(), "pig on the Mantle burns");
                h.succeed();
            });
        });

        // dom-M-13: Overworld portals do not spawn zombified piglins (dimension type override).
        MiteGameTests.add("dimension_no_portal_piglins", 20, h -> {
            h.assertFalse(h.getLevel().environmentAttributes().getValue(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO))),
                    "Overworld portals spawn piglins");
            // X20 (changelog l.237, R163): no dimension type at all, overworld_caves included, lets its portals spawn mobs.
            h.getLevel().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).listElements().forEach(t -> h.assertFalse(
                    t.value().attributes().applyModifier(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, false), "portal piglins in " + t.key().identifier()));
            h.succeed();
        });

        // M8 (DECISIONS.md): monsters spawn up to block light 7 in the Overworld and the Underworld (1.6.4 rule). Registry only:
        // the test server has no Underworld level.
        MiteGameTests.add("dimension_monster_block_light", 20, h -> {
            var types = h.getLevel().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
            for (var key : java.util.List.of(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD, UnderworldContent.TYPE))
                h.assertValueEqual(types.getOrThrow(key).value().monsterSettings().monsterSpawnBlockLightLimit(), UnderworldData.MONSTER_BLOCK_LIGHT_LIMIT, key.identifier().toString());
            h.succeed();
        });

        // Changelog l.443 (R139): nothing spawns naturally on mycelium or mushroom caps; spawners still work.
        MiteGameTests.add("dimension_no_spawn_on_mycelium", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.MYCELIUM);
            h.setBlock(3, 1, 3, Blocks.RED_MUSHROOM_BLOCK);
            h.setBlock(5, 1, 5, Blocks.STONE);
            var level = h.getLevel();
            var random = level.getRandom();
            h.assertFalse(SpawnPlacements.checkSpawnRules(EntityTypes.ARMOR_STAND, level, EntitySpawnReason.NATURAL, h.absolutePos(new BlockPos(1, 2, 1)), random), "spawn on mycelium");
            h.assertFalse(SpawnPlacements.checkSpawnRules(EntityTypes.ARMOR_STAND, level, EntitySpawnReason.NATURAL, h.absolutePos(new BlockPos(3, 2, 3)), random), "spawn on a mushroom cap");
            h.assertTrue(SpawnPlacements.checkSpawnRules(EntityTypes.ARMOR_STAND, level, EntitySpawnReason.NATURAL, h.absolutePos(new BlockPos(5, 2, 5)), random), "spawn on stone refused");
            h.assertTrue(SpawnPlacements.checkSpawnRules(EntityTypes.ARMOR_STAND, level, EntitySpawnReason.SPAWNER, h.absolutePos(new BlockPos(1, 2, 1)), random), "spawner on mycelium refused");
            h.succeed();
        });

        // dom-M-03/06/10: bedrock plates over the bottom half, mushrooms only at the mycelium heights.
        MiteGameTests.add("dimension_underworld_layout", 20, h -> {
            h.assertTrue(UnderworldData.PLATES.getLast() < UnderworldContent.HEIGHT / 2 && UnderworldData.PLATES.getLast() >= UnderworldContent.HEIGHT / 2 - 16, "plates span the bottom half");
            var registries = h.getLevel().registryAccess();
            var settings = registries.lookupOrThrow(Registries.BIOME).getOrThrow(UnderworldContent.BIOME).value().getGenerationSettings();
            var placed = registries.lookupOrThrow(Registries.PLACED_FEATURE);
            h.assertTrue(settings.hasFeature(placed.getValue(UnderworldData.BROWN_MUSHROOM)) && settings.hasFeature(placed.getValue(UnderworldData.RED_MUSHROOM)), "Underworld mushrooms missing");
            h.assertFalse(settings.hasFeature(placed.getValue(net.minecraft.data.worldgen.placement.VegetationPlacements.BROWN_MUSHROOM_NETHER)), "full-height Nether mushrooms");
            var range = placed.getValue(UnderworldData.BROWN_MUSHROOM).placement().stream().filter(p -> p instanceof HeightRangePlacement).findFirst();
            h.assertTrue(range.isPresent(), "mushrooms without a height range");
            h.succeed();
        });

        // M34: a runestone names its rune; sneaking reads it without turning it.
        MiteGameTests.add("runestone_shows_rune", 20, h -> {
            h.setBlock(2, 1, 2, dev.mite.dimension.runegate.Runegate.MITHRIL_RUNESTONE.get());
            var player = MiteGameTests.levelPlayer(h);
            BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), player, hit);
            h.assertValueEqual(h.getLevel().getBlockState(pos).getValue(RunestoneBlock.RUNE), 1, "rune after a click");
            player.setShiftKeyDown(true);
            h.getLevel().getBlockState(pos).useWithoutItem(h.getLevel(), player, hit);
            h.assertValueEqual(h.getLevel().getBlockState(pos).getValue(RunestoneBlock.RUNE), 1, "rune after a sneaking click");
            h.assertValueEqual(RunestoneBlock.runeName(15).getString().isEmpty(), false, "rune name");
            h.succeed();
        });
    }
}
