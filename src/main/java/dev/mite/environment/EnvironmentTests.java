package dev.mite.environment;

import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.worldgen.WorldgenContent;
import dev.mite.worldgen.WorldgenData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** GameTests for EF-CRA-09, EF-MET-10, EF-LUN-05 (torches kept) and EF-MON-08. */
public final class EnvironmentTests {
    private EnvironmentTests() {}

    private static boolean opens(ServerPlayer p, ServerLevel level, BlockPos pos) {
        p.closeContainer();
        p.gameMode.useItemOn(p, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return p.containerMenu != p.inventoryMenu;
    }

    private static boolean hasCanyon(BiomeGenerationSettings settings) {
        return java.util.stream.StreamSupport.stream(settings.getCarvers().spliterator(), false).anyMatch(c -> c.is(Carvers.CANYON));
    }

    public static void register() {
        // EF-CRA-09 CA-1: a workbench covered by stone does not open its menu; glass does not obstruct.
        MiteGameTests.add("env_obstructed_workbench", 20, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = MetabolismTests.player(h);
            BlockPos table = h.absolutePos(new BlockPos(2, 1, 2));
            h.setBlock(2, 1, 2, Blocks.CRAFTING_TABLE);
            h.setBlock(2, 2, 2, Blocks.STONE);
            h.assertTrue(!opens(p, level, table), "covered workbench opened");
            h.setBlock(2, 2, 2, Blocks.GLASS);
            h.assertTrue(opens(p, level, table), "workbench under glass did not open");
            h.setBlock(4, 1, 4, Blocks.ANVIL);
            h.setBlock(4, 2, 4, Blocks.STONE);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(4, 1, 4))), "covered anvil usable");
            h.setBlock(4, 1, 2, Blocks.ENCHANTING_TABLE);
            h.setBlock(4, 2, 2, Blocks.STONE);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(4, 1, 2))), "covered enchanting table usable");
            h.succeed();
        });

        // EF-CRA-09: only the block's own function is denied (vanilla blocked chest): a sneaking player still places a block
        // against a covered furnace, and a covered workbench opens nothing.
        MiteGameTests.add("env_obstructed_sneak_place", 20, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = MetabolismTests.player(h);
            BlockPos furnace = h.absolutePos(new BlockPos(2, 1, 2));
            h.setBlock(2, 1, 2, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            h.setBlock(2, 1, 1, Blocks.STONE); // front covered
            h.assertTrue(!opens(p, level, furnace), "covered furnace opened");
            ItemStack stone = new ItemStack(net.minecraft.world.item.Items.STONE);
            p.setItemInHand(InteractionHand.MAIN_HAND, stone);
            p.setShiftKeyDown(true);
            p.gameMode.useItemOn(p, level, stone, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(furnace), Direction.EAST, furnace, false));
            h.assertBlockPresent(Blocks.STONE, 3, 1, 2);
            h.succeed();
        });

        // EF-CRA-09 (E-4): furnaces, dispensers and droppers need a free front (l.652), jukeboxes and cauldrons a free top (l.653, l.613);
        // a liquid (l.702) or thin snow (l.535) on a workbench blocks it.
        MiteGameTests.add("env_obstruction_sides", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 1, 1, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
            h.setBlock(1, 2, 1, Blocks.STONE); // top: no effect
            h.assertFalse(Environment.isObstructed(level, h.absolutePos(new BlockPos(1, 1, 1))), "furnace blocked from the top");
            h.setBlock(1, 1, 2, Blocks.STONE); // front
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(1, 1, 1))), "furnace with a blocked front usable");
            h.setBlock(3, 1, 1, Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, Direction.UP));
            h.setBlock(3, 2, 1, Blocks.STONE);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(3, 1, 1))), "dispenser facing a block usable");
            h.setBlock(5, 1, 1, Blocks.DROPPER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, Direction.EAST));
            h.setBlock(6, 1, 1, Blocks.STONE);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(5, 1, 1))), "dropper facing a block usable");
            h.setBlock(1, 1, 4, Blocks.JUKEBOX);
            h.setBlock(1, 2, 4, Blocks.STONE);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(1, 1, 4))), "covered jukebox usable");
            h.setBlock(3, 1, 4, Blocks.CAULDRON);
            h.setBlock(3, 2, 4, Blocks.STONE);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(3, 1, 4))), "covered cauldron usable");
            h.setBlock(5, 1, 4, Blocks.CRAFTING_TABLE);
            h.setBlock(5, 2, 4, Blocks.SNOW);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(5, 1, 4))), "workbench under thin snow usable");
            h.setBlock(5, 2, 4, Blocks.WATER);
            h.assertTrue(Environment.isObstructed(level, h.absolutePos(new BlockPos(5, 1, 4))), "workbench under water usable");
            h.setBlock(5, 2, 4, Blocks.AIR);
            h.assertFalse(Environment.isObstructed(level, h.absolutePos(new BlockPos(5, 1, 4))), "free workbench blocked");
            h.succeed();
        });

        // EF-MET-10: an opaque block in front of a furnace or flowing water next to it douses it.
        MiteGameTests.add("env_furnace_doused", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos furnace = h.absolutePos(new BlockPos(3, 1, 3));
            h.setBlock(3, 1, 3, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
            h.assertTrue(!Environment.isDoused(level, furnace, level.getBlockState(furnace)), "free furnace doused");
            h.setBlock(3, 1, 4, Blocks.STONE); // behind: no effect
            h.assertTrue(!Environment.isDoused(level, furnace, level.getBlockState(furnace)), "block behind douses");
            h.setBlock(3, 1, 2, Blocks.STONE); // in front
            h.assertTrue(Environment.isDoused(level, furnace, level.getBlockState(furnace)), "block in front does not douse");
            h.setBlock(3, 1, 2, Blocks.AIR);
            h.setBlock(2, 1, 3, Blocks.WATER); // a source does not count
            h.assertTrue(!Environment.isDoused(level, furnace, level.getBlockState(furnace)), "still water douses");
            h.setBlock(2, 1, 3, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 2));
            h.assertTrue(Environment.isDoused(level, furnace, level.getBlockState(furnace)), "flowing water does not douse");
            h.succeed();
        });

        // EF-MET-10 CA-1: TNT leaves metal ores (iron, MITE silver) intact but destroys coal and gems; other explosions are unchanged.
        MiteGameTests.add("env_tnt_spares_metal_ores", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 1, 1, Blocks.IRON_ORE);
            h.setBlock(2, 1, 1, Blocks.COAL_ORE);
            h.setBlock(3, 1, 1, Blocks.DIAMOND_ORE);
            h.setBlock(4, 1, 1, WorldgenContent.SILVER_ORE.stone().get());
            List<BlockPos> ores = List.of(h.absolutePos(new BlockPos(1, 1, 1)), h.absolutePos(new BlockPos(2, 1, 1)),
                    h.absolutePos(new BlockPos(3, 1, 1)), h.absolutePos(new BlockPos(4, 1, 1)));
            Vec3 center = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2)));
            PrimedTnt tnt = new PrimedTnt(level, center.x, center.y, center.z, null);
            var event = new ExplosionEvent.Detonate(level, new ServerExplosion(level, tnt, null, null, center, 4, false, Explosion.BlockInteraction.DESTROY),
                    new ArrayList<>(), new ArrayList<>(ores));
            Environment.spareOres(event);
            h.assertValueEqual(event.getAffectedBlocks(), List.of(ores.get(1), ores.get(2)), "blocks destroyed by TNT");
            var creeper = new ExplosionEvent.Detonate(level, new ServerExplosion(level, null, null, null, center, 4, false, Explosion.BlockInteraction.DESTROY),
                    new ArrayList<>(), new ArrayList<>(ores));
            Environment.spareOres(creeper);
            h.assertValueEqual(creeper.getAffectedBlocks().size(), 4, "blocks destroyed by another explosion");
            h.succeed();
        });

        // EF-LUN-05: a rain that just started is cut to 75 %; a running one is left alone.
        MiteGameTests.add("env_rain_shortened", 20, h -> {
            WeatherData w = new WeatherData(0, 20000, 0, true, false);
            Environment.shortenRain(0, w);
            h.assertValueEqual(w.getRainTime(), 15000, "new rain duration");
            Environment.shortenRain(14000, w);
            h.assertValueEqual(w.getRainTime(), 15000, "running rain shortened again");
            h.succeed();
        });

        // Changelog l.375 and l.444: lava turns grass and mycelium below it to dirt at once, flowing lava too.
        MiteGameTests.add("env_lava_scorches_grass", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.GRASS_BLOCK);
            h.setBlock(3, 1, 3, Blocks.MYCELIUM);
            h.setBlock(1, 2, 1, Blocks.LAVA);
            h.setBlock(3, 2, 3, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
            h.succeedWhen(() -> {
                h.assertBlockPresent(Blocks.DIRT, 1, 1, 1);
                h.assertBlockPresent(Blocks.DIRT, 3, 1, 3);
            });
        });

        // Changelog l.436: mycelium in the blast radius becomes dirt; mycelium out of reach stays.
        MiteGameTests.add("env_blast_mycelium", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(2, 1, 2, Blocks.MYCELIUM);
            h.setBlock(5, 1, 5, Blocks.MYCELIUM);
            Vec3 center = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1)));
            Environment.blastMycelium(new ExplosionEvent.Detonate(level, new ServerExplosion(level, null, null, null, center, 2, false,
                    Explosion.BlockInteraction.KEEP), new ArrayList<>(), new ArrayList<>()));
            h.assertBlockPresent(Blocks.DIRT, 2, 1, 2);
            h.assertBlockPresent(Blocks.MYCELIUM, 5, 1, 5);
            h.succeed();
        });

        // EF-LUN-05 / changelog R123: rain no longer drops torches (R120 rule removed). A torch in the rain stays for 200 ticks.
        MiteGameTests.addInRain("env_rain_keeps_torch", 240, h -> {
            MiteGameTests.fillBiome(h, Biomes.PLAINS); // the test site may be a desert
            h.setBlock(1, 1, 1, Blocks.TORCH);
            h.assertTrue(h.getLevel().isRainingAt(h.absolutePos(new BlockPos(1, 2, 1))), "the torch is not in the rain");
            h.runAfterDelay(200, () -> {
                h.assertBlockPresent(Blocks.TORCH, 1, 1, 1);
                h.succeed();
            });
        });

        // EF-MON-08, changelog l.440-441 (L-1): ravines at half probability and none under the oceans, caves untouched (vanilla
        // probability, R139 "epic caves"), rare mushroom caverns and surface stone patches; no mushroom fields in the overworld.
        MiteGameTests.add("env_worldgen_terrain", 20, h -> {
            var carvers = h.getLevel().registryAccess().lookupOrThrow(Registries.CARVER);
            h.assertValueEqual(((CaveWorldCarver) carvers.getValueOrThrow(Carvers.CAVE)).probability(), 0.15f, "cave probability");
            h.assertValueEqual(((CanyonWorldCarver) carvers.getValueOrThrow(Carvers.CANYON)).probability(), 0.005f, "canyon probability");
            h.assertValueEqual(((CaveWorldCarver) carvers.getValueOrThrow(Carvers.NETHER_CAVE)).probability(), 0.2f, "nether cave probability");
            var biomeRegistry = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            h.assertFalse(hasCanyon(biomeRegistry.getOrThrow(Biomes.DEEP_OCEAN).value().getGenerationSettings()), "ravines under the ocean");
            var plains = biomeRegistry.getOrThrow(Biomes.PLAINS).value().getGenerationSettings();
            h.assertTrue(hasCanyon(plains), "no ravines in the plains");
            var placed = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            h.assertTrue(plains.hasFeature(placed.getValueOrThrow(WorldgenData.MUSHROOM_CAVERN)), "no mushroom caverns");
            h.assertTrue(plains.hasFeature(placed.getValueOrThrow(WorldgenData.SURFACE_STONE)), "no surface stone");
            var biomes = MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD.usedBiomes().toList();
            h.assertTrue(!biomes.contains(Biomes.MUSHROOM_FIELDS), "mushroom fields generated");
            h.assertTrue(biomes.contains(Biomes.DEEP_OCEAN), "deep ocean missing");
            h.succeed();
        });
    }
}
