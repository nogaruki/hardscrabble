package dev.mite.farming;

import dev.mite.config.MiteConfig;
import dev.mite.core.Metabolism;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismService;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

/**
 * GameTests for EF-AGR-05 (sky light), EF-AGR-06 (blight, bone meal), EF-AGR-07 (manure), EF-AGR-10 (dish), EF-AGR-11 (lifespan),
 * R128 (no bone meal growth), R146 (slow saplings) and R165 (dead village crops, onions).
 */
public final class FarmingTests {
    private FarmingTests() {}

    /** Plants wheat of the given age on farmland at (x, 1, z); returns the absolute crop position. */
    static BlockPos wheat(GameTestHelper h, int x, int z, int age) {
        h.setBlock(x, 1, z, Blocks.FARMLAND);
        h.setBlock(x, 2, z, ((CropBlock) Blocks.WHEAT).getStateForAge(age));
        return h.absolutePos(new BlockPos(x, 2, z));
    }

    static int age(GameTestHelper h, BlockPos pos) {
        BlockState state = h.getLevel().getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop ? crop.getAge(state) : -1;
    }

    /** Runs the check at noon of the current day, then restores the clock (synchronous, like LunarTests). */
    private static void atNoon(ServerLevel level, Runnable check) {
        atTime(level, 6000, check);
    }

    /** Runs the check at {@code dayTime} (6000 noon, 18000 midnight) of the current day, then restores the clock. */
    private static void atTime(ServerLevel level, long dayTime, Runnable check) {
        Holder<WorldClock> clock = level.dimensionType().defaultClock().orElseThrow();
        var clocks = level.getServer().clockManager();
        long saved = clocks.getInstance(clock).totalTicks();
        clocks.setTotalTicks(clock, saved - Math.floorMod(saved, 24000L) + dayTime);
        level.updateSkyBrightness();
        try {
            check.run();
        } finally {
            clocks.setTotalTicks(clock, saved);
            level.updateSkyBrightness();
        }
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1, EF-MUL-05): dedicated_blight brings the blood moon blight boost back on a dedicated server.
        MiteGameTests.add("config_dedicated_blight", 20, h -> {
            float boosted = MiteGameTests.withConfig(MiteConfig.DEDICATED_BLIGHT, true, () -> FarmingRules.onsetChance(true, true));
            h.assertValueEqual(boosted, FarmingRules.BLIGHT_ONSET * FarmingRules.BLOOD_MOON_FACTOR, "dedicated blood moon with dedicated_blight=true");
            h.assertValueEqual(FarmingRules.onsetChance(true, true), FarmingRules.BLIGHT_ONSET, "dedicated blood moon by default");
            h.assertValueEqual(FarmingRules.onsetChance(true, false), FarmingRules.BLIGHT_ONSET * FarmingRules.BLOOD_MOON_FACTOR, "integrated blood moon");
            h.succeed();
        });

        // EF-AGR-05 CA-1: wheat under a roof never grows, even lit by glowstone; open wheat, or wheat under glass, can grow at noon
        // and no wheat grows at midnight.
        MiteGameTests.add("farming_sky_light", 20, h -> {
            BlockPos open = wheat(h, 1, 1, 0);
            for (int x = 3; x <= 5; x++) {
                for (int z = 1; z <= 3; z++) h.setBlock(x, 3, z, Blocks.STONE);
            }
            h.setBlock(3, 2, 2, Blocks.GLOWSTONE);
            BlockPos dark = wheat(h, 4, 2, 0);
            BlockPos glazed = wheat(h, 1, 5, 0);
            h.setBlock(1, 3, 5, Blocks.GLASS);
            ServerLevel level = h.getLevel();
            atNoon(level, () -> {
                h.assertTrue(FarmingRules.canGrow(level, open), "open wheat can grow at noon");
                h.assertTrue(FarmingRules.canGrow(level, glazed), "wheat under glass can grow at noon");
                h.assertTrue(!FarmingRules.canGrow(level, dark), "roofed wheat cannot grow");
                h.assertTrue(!CommonHooks.canCropGrow(level, dark, level.getBlockState(dark), true), "growth event refused");
            });
            atTime(level, 18000, () -> {
                h.assertFalse(FarmingRules.canGrow(level, open), "open wheat can grow at midnight");
                h.assertFalse(CommonHooks.canCropGrow(level, open, level.getBlockState(open), true), "growth event allowed at midnight");
            });
            h.succeed();
        });

        // EF-AGR-07: manured farmland gives an extra stage on about half the growth steps.
        MiteGameTests.add("farming_manure", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos manured = wheat(h, 1, 1, 0), plain = wheat(h, 3, 1, 0);
            h.assertTrue(FarmingRules.manure(level, manured), "manure applied");
            h.assertTrue(!FarmingRules.manure(level, manured), "already manured");
            int bonus = 0, control = 0;
            for (int i = 0; i < 40; i++) {
                for (BlockPos pos : new BlockPos[] {manured, plain}) {
                    level.setBlock(pos, ((CropBlock) Blocks.WHEAT).getStateForAge(1), 2);
                    CommonHooks.fireCropGrowPost(level, pos, ((CropBlock) Blocks.WHEAT).getStateForAge(0));
                }
                if (age(h, manured) == 2) bonus++;
                if (age(h, plain) == 2) control++;
            }
            h.assertValueEqual(control, 0, "bonus stages without manure");
            h.assertTrue(bonus >= 8 && bonus <= 32, "bonus stages with manure: " + bonus + "/40");
            h.succeed();
        });

        // EF-AGR-06: bone meal heals a sick crop without growing it, and does nothing to a healthy one.
        MiteGameTests.add("farming_bonemeal_heals", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos pos = wheat(h, 1, 1, 0);
            h.assertTrue(FarmingRules.infect(level, pos), "infected");
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 2);
            h.assertTrue(BoneMealItem.applyBonemeal(meal, level, pos, null), "bone meal used on the sick crop");
            h.assertTrue(!FarmingRules.isBlighted(level, pos), "healed");
            h.assertValueEqual(meal.getCount(), 1, "bone meal consumed");
            h.assertTrue(!BoneMealItem.applyBonemeal(meal, level, pos, null), "no effect on a healthy crop");
            h.assertValueEqual(meal.getCount(), 1, "bone meal kept");
            h.assertValueEqual(age(h, pos), 0, "never grown");
            h.succeed();
        });

        // R128: bone meal no longer grows saplings, berry bushes or stems; nothing is consumed.
        MiteGameTests.add("farming_bonemeal_refused", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 0, 1, Blocks.DIRT);
            h.setBlock(1, 1, 1, Blocks.OAK_SAPLING);
            h.setBlock(3, 0, 1, Blocks.GRASS_BLOCK);
            h.setBlock(3, 1, 1, Blocks.SWEET_BERRY_BUSH);
            h.setBlock(5, 0, 1, Blocks.FARMLAND);
            h.setBlock(5, 1, 1, Blocks.PUMPKIN_STEM);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 8);
            for (int x = 1; x <= 5; x += 2) {
                BlockPos pos = h.absolutePos(new BlockPos(x, 1, 1));
                BlockState before = level.getBlockState(pos);
                h.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "bone meal used on " + before.getBlock());
                h.assertValueEqual(level.getBlockState(pos), before, "bone meal grew " + before.getBlock());
            }
            h.assertValueEqual(meal.getCount(), 8, "bone meal consumed");
            h.succeed();
        });

        // R146: saplings take 4x longer on average: 1 tree attempt in 7 goes through (1 + 7 steps instead of 1 + 1); other
        // features (mushrooms) are untouched.
        MiteGameTests.add("farming_sapling_slow", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 0, 1, Blocks.DIRT);
            h.setBlock(1, 1, 1, Blocks.OAK_SAPLING);
            h.setBlock(3, 1, 3, Blocks.BROWN_MUSHROOM);
            BlockPos sapling = h.absolutePos(new BlockPos(1, 1, 1)), mushroom = h.absolutePos(new BlockPos(3, 1, 3));
            int tries = 7000, kept = 0;
            for (int i = 0; i < tries; i++) {
                if (!EventHooks.fireBlockGrowFeature(level, level.getRandom(), sapling, null).isCanceled()) kept++;
                h.assertFalse(EventHooks.fireBlockGrowFeature(level, level.getRandom(), mushroom, null).isCanceled(), "mushroom growth cancelled");
            }
            double slowdown = (1 + (double) tries / kept) / 2;
            h.assertTrue(slowdown > 3.5 && slowdown < 4.5, "sapling slowdown x" + slowdown);
            h.succeed();
        });

        // EF-MON-05 / R165: the five village farm lists, and the five lists of abandoned (zombie) villages, turn wheat into dead crops,
        // with ripe onions (dom-I-01) on plains and desert farms; zombie villages keep their vanilla rot (mossy cobblestone).
        MiteGameTests.add("farming_village_farms", 20, h -> {
            ServerLevel level = h.getLevel();
            var lists = level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST);
            Set<Block> dead = Set.of(FarmingContent.DEAD_WHEAT.get(), FarmingContent.DEAD_CARROTS.get(), FarmingContent.DEAD_POTATOES.get());
            Set<Block> seen = new HashSet<>();
            boolean mossy = false;
            for (int i = 0; i < 50 && !mossy; i++) {
                BlockPos pos = new BlockPos(i, 64, i * 3);
                var cobble = new StructureTemplate.StructureBlockInfo(pos, Blocks.COBBLESTONE.defaultBlockState(), null);
                var info = cobble;
                for (var processor : lists.getValueOrThrow(ProcessorLists.ZOMBIE_PLAINS).list())
                    info = processor.process(level, pos, BlockPos.ZERO, cobble, info, new StructurePlaceSettings(), null);
                mossy = info.state().is(Blocks.MOSSY_COBBLESTONE);
            }
            h.assertTrue(mossy, "zombie village lost its vanilla rules");
            for (var key : List.of(ProcessorLists.FARM_PLAINS, ProcessorLists.FARM_SAVANNA, ProcessorLists.FARM_SNOWY, ProcessorLists.FARM_TAIGA, ProcessorLists.FARM_DESERT,
                    ProcessorLists.ZOMBIE_PLAINS, ProcessorLists.ZOMBIE_SAVANNA, ProcessorLists.ZOMBIE_SNOWY, ProcessorLists.ZOMBIE_TAIGA, ProcessorLists.ZOMBIE_DESERT)) {
                var processors = lists.getValueOrThrow(key).list();
                for (int i = 0; i < 200; i++) {
                    BlockPos pos = new BlockPos(i, 64, i * 7);
                    var original = new StructureTemplate.StructureBlockInfo(pos, Blocks.WHEAT.defaultBlockState(), null);
                    var info = original;
                    for (var processor : processors) info = processor.process(level, pos, BlockPos.ZERO, original, info, new StructurePlaceSettings(), null);
                    Block out = info.state().getBlock();
                    h.assertTrue(dead.contains(out) || out == FarmingContent.ONIONS.get(), key.identifier() + " left " + out);
                    seen.add(out);
                }
            }
            h.assertTrue(seen.containsAll(dead), "dead crops missing: " + seen);
            h.assertTrue(seen.contains(FarmingContent.ONIONS.get()), "no onion in village farms");
            h.succeed();
        });

        // EF-AGR-06: blight spreads to the neighbouring crop, then withers it into a dead crop.
        MiteGameTests.add("farming_blight_spreads", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos sick = wheat(h, 1, 1, 3), neighbour = wheat(h, 2, 1, 3);
            FarmingRules.infect(level, sick);
            h.assertTrue(FarmingRules.spread(level, sick, level.getRandom()), "spread");
            h.assertTrue(FarmingRules.isBlighted(level, neighbour), "neighbour sick");
            for (int i = 0; i < 1000 && level.getBlockState(neighbour).is(Blocks.WHEAT); i++) {
                FarmingRules.blightTick(level, neighbour, level.getBlockState(neighbour), level.getRandom());
            }
            h.assertTrue(level.getBlockState(neighbour).is(FarmingContent.DEAD_WHEAT.get()), "withered into dead wheat");
            h.succeed();
        });

        // EF-AGR-10 / EF-NUT-08: a sorbet gives 4 satiety, 2 nutrition and returns the bowl.
        MiteGameTests.add("farming_dish_nutrition", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            Metabolism m = MetabolismService.get(p);
            m.satiety = 0;
            m.nutrition = 0;
            ItemStack stack = new ItemStack(FarmingContent.SORBET.get());
            ItemStack left = stack.finishUsingItem(h.getLevel(), p);
            EventHooks.onItemUseFinish(p, new ItemStack(FarmingContent.SORBET.get()), 0, left);
            h.assertValueEqual(m.satiety, 4, "satiety");
            h.assertValueEqual(m.nutrition, 2, "nutrition");
            h.assertTrue(left.is(Items.BOWL), "bowl returned");
            h.succeed();
        });

        // X14 / changelog l.542 (R130): a pumpkin stem lit by glowstone under a roof never grows; an open one can at noon.
        MiteGameTests.add("farming_stem_daylight", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.FARMLAND);
            h.setBlock(1, 2, 1, Blocks.PUMPKIN_STEM);
            for (int x = 3; x <= 5; x++) {
                for (int z = 1; z <= 3; z++) h.setBlock(x, 3, z, Blocks.STONE);
            }
            h.setBlock(3, 2, 2, Blocks.GLOWSTONE);
            h.setBlock(4, 1, 2, Blocks.FARMLAND);
            h.setBlock(4, 2, 2, Blocks.MELON_STEM);
            BlockPos open = h.absolutePos(new BlockPos(1, 2, 1)), dark = h.absolutePos(new BlockPos(4, 2, 2));
            atNoon(h.getLevel(), () -> {
                ServerLevel level = h.getLevel();
                h.assertTrue(CommonHooks.canCropGrow(level, open, level.getBlockState(open), true), "open stem refused at noon");
                h.assertFalse(CommonHooks.canCropGrow(level, dark, level.getBlockState(dark), true), "torch-lit stem allowed");
            });
            atTime(h.getLevel(), 18000, () -> h.assertFalse(CommonHooks.canCropGrow(h.getLevel(), open, h.getLevel().getBlockState(open), true),
                    "open stem allowed at midnight"));
            h.succeed();
        });

        // X19 / changelog l.368 (R148): a crop grows at half rate unless planted in a row of 3 or a group.
        MiteGameTests.add("farming_isolated_crops", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos alone = wheat(h, 1, 1, 0), pairA = wheat(h, 1, 4, 0), pairB = wheat(h, 2, 4, 0);
            BlockPos rowA = wheat(h, 4, 1, 0), rowB = wheat(h, 5, 1, 0), rowC = wheat(h, 6, 1, 0);
            BlockPos lA = wheat(h, 4, 4, 0), lB = wheat(h, 4, 5, 0);
            h.setBlock(5, 1, 5, Blocks.FARMLAND);
            h.setBlock(5, 2, 5, Blocks.CARROTS); // another type does not count
            BlockPos lC = wheat(h, 5, 4, 0);
            h.assertFalse(FarmingRules.inRow(level, alone), "single crop in a row");
            h.assertFalse(FarmingRules.inRow(level, pairA) || FarmingRules.inRow(level, pairB), "pair in a row");
            for (BlockPos p : new BlockPos[] {rowA, rowB, rowC, lA, lB, lC}) h.assertTrue(FarmingRules.inRow(level, p), "row or group refused at " + p);
            atNoon(level, () -> {
                int isolated = 0, grouped = 0;
                for (int i = 0; i < 400; i++) {
                    FarmingRules.heal(level, alone);
                    FarmingRules.heal(level, rowB);
                    if (CommonHooks.canCropGrow(level, alone, level.getBlockState(alone), true)) isolated++;
                    if (CommonHooks.canCropGrow(level, rowB, level.getBlockState(rowB), true)) grouped++;
                }
                h.assertTrue(isolated > 140 && isolated < 260, "isolated growth steps " + isolated + "/400");
                h.assertTrue(grouped > 380, "grouped growth steps " + grouped + "/400");
            });
            h.succeed();
        });

        // EF-AGR-07 / R128: bone meal no longer grows the modern equivalents: azalea, bamboo, nether fungus, glow berries.
        MiteGameTests.add("farming_bonemeal_modern", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 0, 1, Blocks.DIRT);
            h.setBlock(1, 1, 1, Blocks.AZALEA);
            h.setBlock(3, 0, 1, Blocks.DIRT);
            h.setBlock(3, 1, 1, Blocks.BAMBOO_SAPLING);
            h.setBlock(5, 0, 1, Blocks.CRIMSON_NYLIUM);
            h.setBlock(5, 1, 1, Blocks.CRIMSON_FUNGUS);
            h.setBlock(3, 4, 4, Blocks.STONE);
            h.setBlock(3, 3, 4, Blocks.CAVE_VINES);
            ItemStack meal = new ItemStack(Items.BONE_MEAL, 8);
            for (BlockPos rel : new BlockPos[] {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(3, 3, 4)}) {
                BlockPos pos = h.absolutePos(rel);
                BlockState before = level.getBlockState(pos);
                h.assertFalse(BoneMealItem.applyBonemeal(meal, level, pos, null), "bone meal used on " + before.getBlock());
                h.assertValueEqual(level.getBlockState(pos), before, "bone meal grew " + before.getBlock());
            }
            h.assertValueEqual(meal.getCount(), 8, "bone meal consumed");
            h.succeed();
        });

        // Changelog l.762 (R119): placing a blueberry (sweet berry) bush on farmland turns the farmland into dirt.
        MiteGameTests.add("farming_berry_bush_farmland", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(3, 1, 3, Blocks.FARMLAND);
            BlockPos soil = h.absolutePos(new BlockPos(3, 1, 3));
            ItemStack berries = new ItemStack(Items.SWEET_BERRIES, 4);
            p.setItemInHand(InteractionHand.MAIN_HAND, berries);
            berries.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(soil).add(0, 0.5, 0), Direction.UP, soil, false)));
            h.assertBlockPresent(Blocks.SWEET_BERRY_BUSH, 3, 2, 3);
            h.assertBlockPresent(Blocks.DIRT, 3, 1, 3);
            h.succeed();
        });

        // Changelog l.701 (R123): swamp trees drop half as many windfalls.
        MiteGameTests.add("farming_swamp_windfalls", 40, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(3, 3, 3, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
            BlockPos leaves = h.absolutePos(new BlockPos(3, 3, 3));
            int[] drops = new int[2];
            for (int run = 0; run < 2; run++) {
                MiteGameTests.fillBiome(h, run == 0 ? Biomes.SWAMP : Biomes.PLAINS);
                for (int i = 0; i < 400; i++) FarmingRules.windfall(level, leaves, level.getRandom());
                List<ItemEntity> items = h.getEntities(EntityTypes.ITEM);
                drops[run] = items.stream().mapToInt(e -> e.getItem().getCount()).sum();
                items.forEach(ItemEntity::discard);
            }
            h.assertValueEqual(drops[1], 400, "plains windfalls");
            h.assertTrue(drops[0] > 140 && drops[0] < 260, "swamp windfalls " + drops[0] + "/400");
            h.succeed();
        });

        // Changelog l.614 (R127): red mushrooms neither spread nor take manure under an open sky in the rain.
        MiteGameTests.addInRain("farming_red_mushroom_rain", 20, h -> {
            ServerLevel level = h.getLevel();
            MiteGameTests.fillBiome(h, Biomes.PLAINS); // the test site may be a desert
            h.setBlock(1, 1, 1, Blocks.RED_MUSHROOM);
            h.setBlock(3, 1, 1, Blocks.BROWN_MUSHROOM);
            h.setBlock(5, 1, 5, Blocks.RED_MUSHROOM);
            h.setBlock(5, 3, 5, Blocks.STONE);
            BlockPos open = h.absolutePos(new BlockPos(1, 1, 1)), brown = h.absolutePos(new BlockPos(3, 1, 1)), roofed = h.absolutePos(new BlockPos(5, 1, 5));
            h.assertTrue(FarmingRules.rainStopsMushroom(level, open, level.getBlockState(open)), "red mushroom spreads in the rain");
            h.assertFalse(FarmingRules.rainStopsMushroom(level, brown, level.getBlockState(brown)), "brown mushroom stopped");
            h.assertFalse(FarmingRules.rainStopsMushroom(level, roofed, level.getBlockState(roofed)), "roofed red mushroom stopped");
            h.assertFalse(FarmingRules.manureMushroom(level, open), "manure supersized a red mushroom in the rain");
            h.succeed();
        });

        // Changelog l.246 (R164) and l.578 (R127): milk buckets of mixed metals make a batch of cheese; 8 bowls make 2 cheeses.
        MiteGameTests.add("farming_cheese_batches", 20, h -> {
            ServerLevel level = h.getLevel();
            var copper = dev.mite.equipment.EquipmentContent.BUCKETS.get(dev.mite.core.Material.COPPER);
            CraftingInput buckets = CraftingInput.of(2, 1, List.of(new ItemStack(copper.milk().get()), new ItemStack(Items.MILK_BUCKET)));
            var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, buckets, level).orElseThrow().value();
            ItemStack out = recipe.assemble(buckets);
            h.assertTrue(out.is(FarmingContent.CHEESE.get()) && out.getCount() == 2, "2 cheeses from 2 mixed buckets, got " + out);
            var left = recipe.getRemainingItems(buckets);
            h.assertTrue(left.stream().anyMatch(s -> s.is(copper.empty().get())) && left.stream().anyMatch(s -> s.is(Items.BUCKET)), "buckets back: " + left);
            ItemStack milk = new ItemStack(dev.mite.metabolism.MetabolismContent.MILK_BOWL.get());
            CraftingInput bowls = CraftingInput.of(3, 3, List.of(milk, milk, milk, milk, milk, milk, milk, milk, ItemStack.EMPTY));
            var bowlRecipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, bowls, level).orElseThrow().value();
            ItemStack fromBowls = bowlRecipe.assemble(bowls);
            h.assertTrue(fromBowls.is(FarmingContent.CHEESE.get()) && fromBowls.getCount() == 2, "2 cheeses from 8 bowls, got " + fromBowls);
            h.assertValueEqual(bowlRecipe.getRemainingItems(bowls).stream().filter(s -> s.is(Items.BOWL)).count(), 8L, "bowls back");
            // A bowl dish keeps its bowl in the dish: no extra bowl comes back.
            ItemStack water = new ItemStack(dev.mite.metabolism.MetabolismContent.WATER_BOWL.get());
            CraftingInput soup = CraftingInput.of(2, 1, List.of(new ItemStack(Items.PUMPKIN), water));
            var soupRecipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, soup, level).orElseThrow().value();
            h.assertTrue(soupRecipe.assemble(soup).is(FarmingContent.PUMPKIN_SOUP.get()), "pumpkin soup");
            h.assertTrue(soupRecipe.getRemainingItems(soup).stream().allMatch(ItemStack::isEmpty), "pumpkin soup gave a bowl back");
            h.succeed();
        });

        // EF-AGR-06: blight halves the yield of each drop stack with random rounding, so a blighted ripe wheat gives its single wheat
        // half the time; the harvest clears the blight.
        MiteGameTests.add("farming_blight_yield", 20, h -> {
            ServerLevel level = h.getLevel();
            int wheat = 0, runs = 200;
            for (int i = 0; i < runs; i++) {
                BlockPos pos = wheat(h, 3, 3, CropBlock.MAX_AGE);
                h.assertTrue(FarmingRules.infect(level, pos), "infected");
                level.destroyBlock(pos, true);
                h.assertFalse(FarmingRules.heal(level, pos), "harvest left the blight");
                List<ItemEntity> items = h.getEntities(EntityTypes.ITEM);
                wheat += items.stream().filter(e -> e.getItem().is(Items.WHEAT)).mapToInt(e -> e.getItem().getCount()).sum();
                items.forEach(ItemEntity::discard);
            }
            h.assertTrue(wheat > 70 && wheat < 130, "blighted wheat yield " + wheat + "/" + runs);
            h.succeed();
        });

        // EF-AGR-06: a crop removed without drops (snow, acid, creative) leaves no blight behind for the next crop planted there.
        MiteGameTests.add("farming_blight_cleared", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos pos = wheat(h, 2, 2, 3);
            FarmingRules.infect(level, pos);
            h.assertTrue(SoilRules.snowKills(level, pos), "snow kill");
            wheat(h, 2, 2, 0); // a villager replants with setBlock, no place event
            h.assertFalse(FarmingRules.isBlighted(level, pos), "replanted crop born blighted");
            BlockPos other = wheat(h, 4, 4, 3);
            FarmingRules.infect(level, other);
            level.removeBlock(other, false);
            h.assertFalse(FarmingRules.isBlighted(level, other), "blight kept on an empty block");
            h.assertFalse(FarmingRules.heal(level, other), "entry left in the chunk");
            h.succeed();
        });

        // EF-AGR-06 / EF-MUL-05: blood moons make blight 10x more likely, except on a dedicated server unless dedicated_blight.
        MiteGameTests.add("farming_blight_onset", 20, h -> {
            float base = FarmingRules.BLIGHT_ONSET, boosted = base * FarmingRules.BLOOD_MOON_FACTOR;
            h.assertValueEqual(FarmingRules.onsetChance(false, false, false), base, "normal night");
            h.assertValueEqual(FarmingRules.onsetChance(false, true, true), base, "normal night, dedicated");
            h.assertValueEqual(FarmingRules.onsetChance(true, false, false), boosted, "blood moon, integrated server");
            h.assertValueEqual(FarmingRules.onsetChance(true, true, false), base, "blood moon, dedicated server");
            h.assertValueEqual(FarmingRules.onsetChance(true, true, true), boosted, "blood moon, dedicated server with dedicated_blight");
            h.succeed();
        });

        // EF-AGR-07: manure is lost when its farmland turns to dirt, and does not come back with new farmland.
        MiteGameTests.add("farming_manure_lost", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(3, 1, 3, Blocks.FARMLAND);
            BlockPos soil = h.absolutePos(new BlockPos(3, 1, 3));
            h.assertTrue(FarmingRules.manure(level, soil), "manure applied");
            h.setBlock(3, 1, 3, Blocks.DIRT);
            h.assertFalse(FarmingRules.isManured(level, soil), "dirt manured");
            h.setBlock(3, 1, 3, Blocks.FARMLAND);
            h.assertFalse(FarmingRules.isManured(level, soil), "manure came back with the farmland");
            h.succeed();
        });

        // EF-AGR-11: dropped items live item_lifespan_ticks (24 000 by default).
        MiteGameTests.add("farming_item_lifespan", 20, h -> {
            var item = h.spawnItem(Items.STICK, 2, 2, 2);
            h.assertValueEqual(item.lifespan, MiteConfig.ITEM_LIFESPAN_TICKS.getAsInt(), "lifespan");
            h.assertValueEqual(item.lifespan, 24000, "default lifespan");
            var shortLived = MiteGameTests.withConfig(MiteConfig.ITEM_LIFESPAN_TICKS, 600, () -> h.spawnItem(Items.STICK, 4, 2, 4));
            h.assertValueEqual(shortLived.lifespan, 600, "lifespan with item_lifespan_ticks=600");
            h.succeed();
        });
    }
}
