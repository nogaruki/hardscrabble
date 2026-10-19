package dev.mite.farming;

import dev.mite.gametest.MiteGameTests;
import net.minecraft.core.BlockPos;
import dev.mite.equipment.EquipmentContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** GameTests for EF-AGR-12: depletion (I08), slimes (I09), farming mycelium (I12), cocoa (I15), chunk fast-forward (I17). */
public final class SoilTests {
    private SoilTests() {}

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): chunk_fast_forward (off by default) switches the saved-time catch-up on.
        MiteGameTests.add("config_chunk_fast_forward", 20, h -> {
            h.assertTrue(MiteGameTests.withConfig(dev.mite.config.MiteConfig.CHUNK_FAST_FORWARD, true, SoilRules::fastForwardEnabled), "fast-forward off with chunk_fast_forward=true");
            h.assertFalse(SoilRules.fastForwardEnabled(), "fast-forward on by default");
            h.succeed();
        });

        // I08: harvesting a mature crop wears out unmanured farmland; manure heals it and is eaten by the next harvest.
        MiteGameTests.add("soil_depletion", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos crop = FarmingTests.wheat(h, 2, 2, 7), farmland = crop.below();
            level.destroyBlock(crop, true);
            h.assertTrue(SoilRules.isDepleted(level, farmland), "harvest did not deplete");
            h.assertTrue(FarmingRules.manure(level, farmland), "manure refused");
            h.assertTrue(!SoilRules.isDepleted(level, farmland), "manure did not heal");
            FarmingTests.wheat(h, 2, 2, 7);
            level.destroyBlock(crop, true);
            h.assertTrue(!FarmingRules.isManured(level, farmland), "manure not eaten by the harvest");
            h.assertTrue(!SoilRules.isDepleted(level, farmland), "depleted despite manure");
            FarmingTests.wheat(h, 4, 4, 3);
            level.destroyBlock(h.absolutePos(new BlockPos(4, 2, 4)), true);
            h.assertTrue(!SoilRules.isDepleted(level, h.absolutePos(new BlockPos(4, 1, 4))), "an unripe crop depleted");
            h.succeed();
        });

        // I09 / changelog l.493 (R134): a slime standing in a crop blights it and leaves it standing.
        MiteGameTests.add("soil_slime_blights_crop", 40, h -> {
            BlockPos crop = FarmingTests.wheat(h, 3, 3, 4);
            h.spawnWithNoFreeWill(EntityTypes.SLIME, 3, 2, 3);
            h.succeedWhen(() -> {
                h.assertTrue(FarmingRules.isBlighted(h.getLevel(), crop), "crop not blighted");
                h.assertBlockPresent(Blocks.WHEAT, 3, 2, 3);
            });
        });

        // Changelog l.493 (R134): magma cubes (and oozes, puddings) destroy crops and other plants; a jelly only blights.
        MiteGameTests.add("soil_magma_cube_destroys_plants", 60, h -> {
            FarmingTests.wheat(h, 1, 1, 4);
            h.setBlock(5, 1, 5, Blocks.POPPY);
            h.spawnWithNoFreeWill(EntityTypes.MAGMA_CUBE, 1, 2, 1).setSize(1, true);
            h.spawnWithNoFreeWill(EntityTypes.MAGMA_CUBE, 5, 1, 5).setSize(1, true);
            h.assertTrue(SoilRules.destroysPlants(h.spawnWithNoFreeWill(EntityTypes.MAGMA_CUBE, 3, 1, 3)), "magma cube spares plants");
            h.assertFalse(SoilRules.destroysPlants(h.spawnWithNoFreeWill(EntityTypes.SLIME, 3, 1, 3)), "slime destroys plants");
            h.succeedWhen(() -> {
                h.assertBlockPresent(Blocks.AIR, 1, 2, 1);
                h.assertBlockPresent(Blocks.AIR, 5, 1, 5);
            });
        });

        // Changelog l.634 (R127): falling snow kills crops, ripe ones included, and stems; a roofed crop is spared.
        MiteGameTests.addInRain("soil_snow_kills_crops", 100, h -> {
            ServerLevel level = h.getLevel();
            MiteGameTests.fillBiome(h, Biomes.SNOWY_PLAINS);
            BlockPos ripe = FarmingTests.wheat(h, 1, 1, 7), roofed = FarmingTests.wheat(h, 5, 5, 7);
            h.setBlock(5, 4, 5, Blocks.STONE);
            h.setBlock(3, 1, 1, Blocks.FARMLAND);
            h.setBlock(3, 2, 1, Blocks.MELON_STEM);
            BlockPos stem = h.absolutePos(new BlockPos(3, 2, 1));
            // Sky access (precipitationAt) needs the arena's sky light, computed some ticks after it is placed: wait for it.
            h.succeedWhen(() -> {
                h.assertTrue(level.canSeeSky(ripe), "no sky light on the open column yet");
                // The vanilla precipitation tick (ServerLevelSnowMixin) may have snowed on the open crops first: gone is a kill too.
                h.assertTrue(level.getBlockState(ripe).isAir() || SoilRules.snowfall(level, ripe), "ripe wheat survived the snowfall");
                h.assertTrue(level.getBlockState(stem).isAir() || SoilRules.snowKills(level, stem), "melon stem survived the snow");
                h.assertFalse(SoilRules.snowedOn(level, roofed), "snow falls under the roof");
                h.assertFalse(SoilRules.snowfall(level, roofed), "roofed wheat killed");
                h.assertBlockPresent(Blocks.AIR, 1, 2, 1);
                h.assertBlockPresent(Blocks.AIR, 3, 2, 1);
                h.assertBlockPresent(Blocks.WHEAT, 5, 2, 5);
            });
        });

        // Changelog l.178 (R172): worms in a chest eat plant material and food (some of it becomes manure), not stone nor worms.
        MiteGameTests.add("soil_worm_compost", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.CHEST);
            h.setBlock(5, 1, 5, Blocks.CHEST);
            ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)));
            ChestBlockEntity plain = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5, 1, 5)));
            chest.setItem(0, new ItemStack(EquipmentContent.WORM.get(), 4));
            chest.setItem(1, new ItemStack(Items.COBBLESTONE, 16));
            chest.setItem(2, new ItemStack(Items.WHEAT_SEEDS, 64));
            plain.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 64));
            for (int i = 0; i < 100; i++) {
                SoilRules.compost(chest, h.getLevel().getRandom());
                SoilRules.compost(plain, h.getLevel().getRandom());
            }
            h.assertValueEqual(chest.getItem(0).getCount(), 4, "worms");
            h.assertValueEqual(chest.getItem(1).getCount(), 16, "cobblestone");
            h.assertTrue(chest.getItem(2).getCount() < 40, "seeds left " + chest.getItem(2).getCount());
            h.assertValueEqual(plain.getItem(0).getCount(), 64, "seeds without worms");
            h.assertTrue(SoilRules.compostable(new ItemStack(Items.COOKED_BEEF)) && !SoilRules.compostable(new ItemStack(EquipmentContent.COOKED_WORM.get())),
                    "compostable items");
            h.succeed();
        });

        // I12: a brown mushroom sprouts on moist manured farmland in the dark, and survives there; not without manure.
        MiteGameTests.add("soil_mycelium", 100, h -> {
            ServerLevel level = h.getLevel();
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) h.setBlock(x, 3, z, Blocks.STONE);
            }
            BlockState moist = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
            h.setBlock(3, 1, 3, moist);
            h.setBlock(2, 1, 4, moist);
            BlockPos manured = h.absolutePos(new BlockPos(3, 1, 3)), plain = h.absolutePos(new BlockPos(2, 1, 4));
            FarmingRules.manure(level, manured);
            h.succeedWhen(() -> {
                h.assertTrue(!SoilRules.sprout(level, plain), "sprouted without manure");
                SoilRules.sprout(level, manured);
                h.assertBlockPresent(Blocks.BROWN_MUSHROOM, 3, 2, 3);
                h.assertTrue(level.getBlockState(manured.above()).canSurvive(level, manured.above()), "mushroom cannot stay on farmland");
            });
        });

        // I15: cocoa only on the top 3 logs of a jungle trunk capped by natural leaves.
        MiteGameTests.add("soil_cocoa_top", 20, h -> {
            ServerLevel level = h.getLevel();
            for (int y = 0; y <= 3; y++) h.setBlock(1, y, 1, Blocks.JUNGLE_LOG);
            h.setBlock(1, 4, 1, Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
            for (int y = 1; y <= 3; y++) h.assertTrue(SoilRules.cocoaAllowed(level, h.absolutePos(new BlockPos(1, y, 1))), "refused at y " + y);
            h.assertTrue(!SoilRules.cocoaAllowed(level, h.absolutePos(new BlockPos(1, 0, 1))), "allowed on the 4th log");
            h.setBlock(1, 4, 1, Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            h.assertTrue(!SoilRules.cocoaAllowed(level, h.absolutePos(new BlockPos(1, 3, 1))), "allowed under placed leaves");
            h.succeed();
        });

        // I17: a crop of a chunk unloaded for 768 000 ticks catches up to maturity; no time, or no sky, no growth.
        MiteGameTests.add("soil_fast_forward", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos open = FarmingTests.wheat(h, 1, 1, 0), fresh = FarmingTests.wheat(h, 3, 1, 0), roofed = FarmingTests.wheat(h, 5, 5, 0);
            h.setBlock(5, 3, 5, Blocks.STONE);
            SoilRules.fastForward(level, open, SoilRules.FAST_FORWARD_MAX);
            SoilRules.fastForward(level, fresh, 0);
            SoilRules.fastForward(level, roofed, SoilRules.FAST_FORWARD_MAX);
            h.assertValueEqual(FarmingTests.age(h, open), 7, "caught-up age");
            h.assertValueEqual(FarmingTests.age(h, fresh), 0, "age without elapsed time");
            h.assertValueEqual(FarmingTests.age(h, roofed), 0, "roofed age");
            h.succeed();
        });

        // I17 with the loaded-chunk rules: manure speeds the catch-up by 50 %; a blighted crop never grows and withers.
        MiteGameTests.add("soil_fast_forward_manure_blight", 20, h -> {
            ServerLevel level = h.getLevel();
            for (int x = 0; x <= 6; x++) if (x != 3) FarmingTests.wheat(h, x, 3, 0); // two rows of 3: no isolation penalty
            BlockPos plain = h.absolutePos(new BlockPos(1, 2, 3)), manured = h.absolutePos(new BlockPos(5, 2, 3));
            FarmingRules.manure(level, manured);
            BlockPos sick = FarmingTests.wheat(h, 1, 5, 0);
            FarmingRules.infect(level, sick);
            // Ticks for exactly 2 stages on plain farmland, so 3 on manured farmland.
            int speed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
            long elapsed = Math.round(2 / (speed / 4096.0 * SoilRules.DAYLIGHT * SoilRules.STAGE_CHANCE));
            SoilRules.fastForward(level, plain, elapsed);
            SoilRules.fastForward(level, manured, elapsed);
            SoilRules.fastForward(level, sick, SoilRules.FAST_FORWARD_MAX);
            h.assertValueEqual(FarmingTests.age(h, plain), 2, "plain catch-up");
            h.assertValueEqual(FarmingTests.age(h, manured), 3, "manured catch-up");
            h.assertBlockPresent(FarmingContent.DEAD_WHEAT.get(), 1, 2, 5);
            h.assertFalse(FarmingRules.isBlighted(level, sick), "withered crop still blighted");
            h.succeed();
        });
    }
}
