package dev.mite.metallurgy;

import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

/** GameTests for EF-MET-05..07 and B08. One smelt takes 200 ticks. */
public final class MetallurgyTests {
    private MetallurgyTests() {}

    private static final int SMELT = 230;

    public static void register() {
        // EF-MET-05 CA-1: raw mithril + coal in an obsidian furnace does not progress; with a lava bucket it gives an ingot.
        MiteGameTests.add("metallurgy_mithril_needs_lava", SMELT + 20, h -> {
            var coal = furnace(h, 1, Metallurgy.OBSIDIAN_FURNACE.get(), MaterialItems.RAW_MITHRIL.get(), Items.COAL);
            var lava = furnace(h, 3, Metallurgy.OBSIDIAN_FURNACE.get(), MaterialItems.RAW_MITHRIL.get(), Items.LAVA_BUCKET);
            h.runAfterDelay(SMELT, () -> {
                assertStuck(h, coal);
                h.assertTrue(lava.getItem(2).is(MaterialItems.MITHRIL_INGOT.get()), "lava bucket should smelt raw mithril");
                h.succeed();
            });
        });
        // EF-MET-05 CA-2: the clay furnace refuses blocks but cooks food.
        MiteGameTests.add("metallurgy_clay_furnace_food_only", SMELT + 20, h -> {
            var block = furnace(h, 1, Metallurgy.CLAY_FURNACE.get(), Items.COBBLESTONE, Items.CHARCOAL);
            var food = furnace(h, 3, Metallurgy.CLAY_FURNACE.get(), Items.BEEF, Items.CHARCOAL);
            h.assertTrue(!block.canPlaceItem(0, new ItemStack(Items.COBBLESTONE)), "clay furnace should refuse a block input");
            h.runAfterDelay(SMELT, () -> {
                assertStuck(h, block);
                h.assertTrue(food.getItem(2).is(Items.COOKED_BEEF), "clay furnace should cook beef");
                h.succeed();
            });
        });
        // EF-MET-05/07: raw iron (heat 2) smelts in the vanilla furnace with coal (2) but not with charcoal (1).
        MiteGameTests.add("metallurgy_iron_needs_coal", SMELT + 20, h -> {
            var coal = furnace(h, 1, Blocks.FURNACE, Items.RAW_IRON, Items.COAL);
            var charcoal = furnace(h, 3, Blocks.FURNACE, Items.RAW_IRON, Items.CHARCOAL);
            h.runAfterDelay(SMELT, () -> {
                h.assertTrue(coal.getItem(2).is(Items.IRON_INGOT), "coal should smelt raw iron");
                assertStuck(h, charcoal);
                h.succeed();
            });
        });
        // dom-F-04: when the coal runs out, a planks fuel (heat 1) is not wasted on raw iron (heat 2). The LIT block state
        // still says lit on that tick: the burning-heat lookup must follow the block entity, not the state.
        MiteGameTests.add("metallurgy_fuel_relay_no_waste", 40, h -> {
            var furnace = furnace(h, 1, Blocks.FURNACE, Items.RAW_IRON, Items.OAK_PLANKS);
            h.setBlock(new BlockPos(1, 1, 1), h.getBlockState(new BlockPos(1, 1, 1)).setValue(AbstractFurnaceBlock.LIT, true));
            furnace = h.getBlockEntity(new BlockPos(1, 1, 1), AbstractFurnaceBlockEntity.class);
            furnace.setItem(0, new ItemStack(Items.RAW_IRON));
            furnace.setItem(1, new ItemStack(Items.OAK_PLANKS));
            furnace.setData(FurnaceHeat.BURNING_HEAT, 2);
            var f = furnace;
            h.runAfterDelay(20, () -> {
                assertStuck(h, f);
                h.succeed();
            });
        });
        // EF-MET-05 (dom-F-05): the clay furnace burns small fuels only.
        MiteGameTests.add("metallurgy_clay_furnace_small_fuels", 20, h -> {
            var clay = furnace(h, 1, Metallurgy.CLAY_FURNACE.get(), Items.BEEF, Items.STICK);
            h.assertFalse(clay.canPlaceItem(1, new ItemStack(Items.LAVA_BUCKET)), "clay furnace accepts a lava bucket");
            h.assertFalse(clay.canPlaceItem(1, new ItemStack(Items.COAL_BLOCK)), "clay furnace accepts a coal block");
            h.assertTrue(clay.canPlaceItem(1, new ItemStack(Items.CHARCOAL)), "clay furnace refuses charcoal");
            clay.setItem(1, new ItemStack(Items.LAVA_BUCKET)); // forced in, like a menu click: it does not burn
            h.assertValueEqual(FurnaceHeat.effectiveHeat(clay), 0, "heat of a refused fuel");
            // EF-UI-04: the screen says the fuel is refused (bit 9) instead of only "heat too low".
            h.assertTrue((FurnaceHeat.statusSlot(clay).get() & 512) != 0, "refused fuel not flagged");
            clay.setItem(1, new ItemStack(Items.CHARCOAL));
            h.assertTrue((FurnaceHeat.statusSlot(clay).get() & 512) == 0, "charcoal flagged as refused");
            h.succeed();
        });
        // M42 (changelog R121): 4 sand -> 1 glass at heat 2; F-4 (l.760, R119): 4 sand -> 1 sandstone at heat 1 (charcoal); 3 sand do nothing.
        MiteGameTests.add("metallurgy_sand_glass_4_to_1", SMELT + 20, h -> {
            var coal = furnace(h, 1, Blocks.FURNACE, Items.SAND, Items.COAL);
            coal.setItem(0, new ItemStack(Items.SAND, 4));
            var charcoal = furnace(h, 3, Blocks.FURNACE, Items.SAND, Items.CHARCOAL);
            charcoal.setItem(0, new ItemStack(Items.SAND, 4));
            var three = furnace(h, 5, Blocks.FURNACE, Items.SAND, Items.COAL);
            three.setItem(0, new ItemStack(Items.SAND, 3));
            h.runAfterDelay(SMELT, () -> {
                h.assertTrue(coal.getItem(2).is(Items.GLASS) && coal.getItem(2).getCount() == 1 && coal.getItem(0).isEmpty(),
                        "4 sand + coal should give 1 glass: " + coal.getItem(0) + " -> " + coal.getItem(2));
                h.assertTrue(charcoal.getItem(2).is(Items.SANDSTONE) && charcoal.getItem(2).getCount() == 1 && charcoal.getItem(0).isEmpty(),
                        "4 sand + charcoal should give 1 sandstone: " + charcoal.getItem(0) + " -> " + charcoal.getItem(2));
                h.assertTrue(three.getItem(2).isEmpty() && three.getItem(1).getCount() == 1, "3 sand should not smelt");
                h.succeed();
            });
        });
        // F-4 (l.138, R177): sandstone melts into glass from heat 2 and keeps smooth sandstone at heat 1; F-5 (l.724, R120): 4 clay
        // balls bake into 4 bricks in one smelt.
        MiteGameTests.add("metallurgy_sandstone_and_clay", SMELT + 20, h -> {
            var glass = furnace(h, 1, Blocks.FURNACE, Items.SANDSTONE, Items.COAL);
            var smooth = furnace(h, 3, Blocks.FURNACE, Items.SANDSTONE, Items.CHARCOAL);
            var clay = furnace(h, 5, Blocks.FURNACE, Items.CLAY_BALL, Items.CHARCOAL);
            clay.setItem(0, new ItemStack(Items.CLAY_BALL, 6));
            h.runAfterDelay(SMELT, () -> {
                h.assertTrue(glass.getItem(2).is(Items.GLASS), "sandstone + coal should give glass: " + glass.getItem(2));
                h.assertTrue(smooth.getItem(2).is(Items.SMOOTH_SANDSTONE), "sandstone + charcoal should give smooth sandstone: " + smooth.getItem(2));
                h.assertTrue(clay.getItem(2).is(Items.BRICK) && clay.getItem(2).getCount() == 4 && clay.getItem(0).getCount() == 2,
                        "6 clay balls should give 4 bricks and keep 2: " + clay.getItem(0) + " -> " + clay.getItem(2));
                h.succeed();
            });
        });
        // F-5 (l.724): a clay batch shrinks to the room left in the output slot instead of stalling the furnace (62 bricks + 4 balls -> 64, 2 left).
        MiteGameTests.add("metallurgy_clay_batch_fits_output", SMELT + 20, h -> {
            var clay = furnace(h, 1, Blocks.FURNACE, Items.CLAY_BALL, Items.CHARCOAL);
            clay.setItem(0, new ItemStack(Items.CLAY_BALL, 4));
            clay.setItem(2, new ItemStack(Items.BRICK, 62));
            h.assertValueEqual(FurnaceHeat.inputCount(clay), 2, "clay balls per smelt with room for 2 bricks");
            h.runAfterDelay(SMELT, () -> {
                h.assertTrue(clay.getItem(2).getCount() == 64 && clay.getItem(0).getCount() == 2,
                        "62 bricks + 4 balls should give 64 bricks and keep 2 balls: " + clay.getItem(0) + " -> " + clay.getItem(2));
                h.succeed();
            });
        });
        // Changelog l.764 (R119): crowding an open fire. A full or unlit campfire never goes out from it, and a sneaking player
        // holding food does not use the campfire (no food placed), so no roll is made.
        MiteGameTests.add("metallurgy_campfire_crowding", 20, h -> {
            BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
            h.getLevel().setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState());
            var campfire = (net.minecraft.world.level.block.entity.CampfireBlockEntity) h.getLevel().getBlockEntity(pos);
            campfire.placeFood(h.getLevel(), null, new ItemStack(Items.BEEF));
            h.assertFalse(Campfires.crowd(h.getLevel(), pos, null, 0.3f), "1 food, roll 0.3 above 25 %");
            h.assertTrue(Campfires.crowd(h.getLevel(), pos, null, 0.2f), "1 food, roll 0.2 within 25 %");
            h.assertFalse(h.getLevel().getBlockState(pos).getValue(net.minecraft.world.level.block.CampfireBlock.LIT), "fire still lit");
            h.assertFalse(Campfires.crowd(h.getLevel(), pos, null, 0f), "an unlit campfire goes out");
            h.getLevel().setBlockAndUpdate(pos, h.getLevel().getBlockState(pos).setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));
            campfire = (net.minecraft.world.level.block.entity.CampfireBlockEntity) h.getLevel().getBlockEntity(pos);
            while (campfire.getItems().stream().anyMatch(ItemStack::isEmpty)) campfire.placeFood(h.getLevel(), null, new ItemStack(Items.BEEF));
            h.assertFalse(Campfires.crowd(h.getLevel(), pos, null, 0f), "a full campfire goes out");
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BEEF));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
            var click = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, net.minecraft.world.InteractionHand.MAIN_HAND, pos, hit);
            h.assertTrue(Campfires.usesBlock(click), "a standing player does not use the campfire");
            player.setShiftKeyDown(true);
            h.assertFalse(Campfires.usesBlock(click), "a sneaking player with food uses the campfire");
            h.succeed();
        });
        // F-1: blasting recipes take half the furnace time (100 ticks); D-1 (l.761): sandstone needs a tool, a flint hatchet does.
        MiteGameTests.add("metallurgy_blast_furnace_and_sandstone_tools", 20, h -> {
            var recipe = h.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse("minecraft:iron_ingot_from_blasting_raw_iron"))).orElseThrow();
            h.assertValueEqual(((AbstractCookingRecipe) recipe.value()).cookingTime(), 100, "blasting time");
            var sandstone = Blocks.SANDSTONE.defaultBlockState();
            h.assertFalse(dev.mite.harvest.HarvestRules.canHarvest(ItemStack.EMPTY, sandstone), "sandstone by hand");
            Item hatchet = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.mite.MiteMod.id("flint_hatchet"));
            h.assertTrue(dev.mite.harvest.HarvestRules.canHarvest(new ItemStack(hatchet), sandstone), "flint hatchet on sandstone");
            h.assertTrue(dev.mite.harvest.HarvestRules.canHarvest(new ItemStack(Items.COPPER_PICKAXE), sandstone), "copper pickaxe on sandstone");
            // F-6: MITE workbenches are axe blocks.
            h.assertTrue(dev.mite.crafting.CraftingContent.WORKBENCHES.get(dev.mite.crafting.Workbench.IRON).get().defaultBlockState()
                    .is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE), "iron workbench mineable with an axe");
            h.succeed();
        });
        // F-2: broken glass leaves glass shards (silk touch keeps the block); nether quartz ore breaks into quartz shards.
        MiteGameTests.add("metallurgy_glass_and_quartz_shards", 20, h -> {
            BlockPos pos = new BlockPos(3, 1, 3);
            var level = h.getLevel();
            int shards = 0;
            for (int i = 0; i < 20; i++) {
                for (ItemStack s : net.minecraft.world.level.block.Block.getDrops(Blocks.GLASS.defaultBlockState(), level, h.absolutePos(pos), null)) {
                    h.assertTrue(s.is(MaterialItems.GLASS_SHARD.get()), "glass dropped " + s);
                    shards += s.getCount();
                }
            }
            h.assertTrue(shards >= 20, "glass shards over 20 blocks: " + shards);
            ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
            silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
            var kept = net.minecraft.world.level.block.Block.getDrops(Blocks.GLASS_PANE.defaultBlockState(), level, h.absolutePos(pos), null, null, silk);
            h.assertTrue(kept.size() == 1 && kept.getFirst().is(Items.GLASS_PANE), "silk touch pane: " + kept);
            var quartz = net.minecraft.world.level.block.Block.getDrops(Blocks.NETHER_QUARTZ_ORE.defaultBlockState(), level, h.absolutePos(pos), null, null, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(quartz.size() == 1 && quartz.getFirst().is(MaterialItems.QUARTZ_SHARD.get()) && quartz.getFirst().getCount() >= 4,
                    "quartz ore drops: " + quartz);
            h.succeed();
        });
        // dom-F-03: the obsidian furnace needs an iron pickaxe; the clay furnace breaks by hand.
        MiteGameTests.add("metallurgy_furnace_needs_pickaxe", 20, h -> {
            var obsidian = Metallurgy.OBSIDIAN_FURNACE.get().defaultBlockState();
            h.assertFalse(dev.mite.harvest.HarvestRules.canHarvest(ItemStack.EMPTY, obsidian), "bare hand harvests an obsidian furnace");
            h.assertFalse(dev.mite.harvest.HarvestRules.canHarvest(new ItemStack(Items.COPPER_PICKAXE), obsidian), "copper pickaxe harvests an obsidian furnace");
            h.assertTrue(dev.mite.harvest.HarvestRules.canHarvest(new ItemStack(Items.IRON_PICKAXE), obsidian), "iron pickaxe cannot harvest an obsidian furnace");
            h.assertTrue(dev.mite.harvest.HarvestRules.canHarvest(ItemStack.EMPTY, Metallurgy.CLAY_FURNACE.get().defaultBlockState()), "clay furnace needs a tool");
            h.succeed();
        });
        // dom-F-07, dom-F-08: melting an iron tool into nuggets needs heat 2; MITE metals carry the c: tags; TNT spares metal ores.
        MiteGameTests.add("metallurgy_recycling_heat_and_tags", 20, h -> {
            h.assertValueEqual(FurnaceHeat.requiredHeat(new ItemStack(Items.IRON_PICKAXE)), 2, "iron pickaxe smelt heat");
            h.assertValueEqual(FurnaceHeat.requiredHeat(new ItemStack(Items.CHAINMAIL_HELMET)), 2, "chainmail smelt heat");
            h.assertTrue(new ItemStack(MaterialItems.MITHRIL_INGOT.get()).is(net.neoforged.neoforge.common.Tags.Items.INGOTS), "mithril ingot in c:ingots");
            h.assertTrue(new ItemStack(MaterialItems.SILVER_NUGGET.get()).is(net.neoforged.neoforge.common.Tags.Items.NUGGETS), "silver nugget in c:nuggets");
            h.assertTrue(new ItemStack(MaterialItems.RAW_ADAMANTIUM.get()).is(net.neoforged.neoforge.common.Tags.Items.RAW_MATERIALS), "raw adamantium in c:raw_materials");
            h.assertTrue(dev.mite.worldgen.WorldgenContent.SILVER_ORE.stone().get().defaultBlockState().is(Metallurgy.SPARED_BY_TNT), "silver ore spared by TNT");
            h.assertTrue(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState().is(Metallurgy.SPARED_BY_TNT), "iron ore spared by TNT");
            h.assertFalse(Blocks.COAL_ORE.defaultBlockState().is(Metallurgy.SPARED_BY_TNT), "coal ore spared by TNT");
            h.succeed();
        });
        // EF-MET-06 CA-1: fuel heat and burn time table.
        MiteGameTests.add("metallurgy_fuel_table", 20, h -> {
            class Probe extends FurnaceBlockEntity {
                Probe() {
                    super(h.absolutePos(BlockPos.ZERO), Blocks.FURNACE.defaultBlockState());
                }

                int burn(Item item) {
                    return getBurnDuration(h.getLevel(), new ItemStack(item));
                }
            }
            var probe = new Probe();
            Map<Item, int[]> table = Map.ofEntries(
                    Map.entry(Items.OAK_PLANKS, new int[] {1, 200}), Map.entry(Items.WOODEN_PICKAXE, new int[] {1, 200}),
                    Map.entry(Items.OAK_BOAT, new int[] {1, 200}), Map.entry(Items.STICK, new int[] {1, 100}),
                    Map.entry(Items.ARROW, new int[] {1, 100}), Map.entry(Items.BAMBOO, new int[] {1, 100}),
                    Map.entry(Items.PAPER, new int[] {1, 50}), Map.entry(Items.CHARCOAL, new int[] {1, 1600}),
                    Map.entry(Items.DRIED_KELP_BLOCK, new int[] {1, 1600}), Map.entry(Items.COAL, new int[] {2, 1600}),
                    Map.entry(Items.COAL_BLOCK, new int[] {2, 16000}), Map.entry(Items.LAVA_BUCKET, new int[] {3, 20000}),
                    Map.entry(Items.BLAZE_ROD, new int[] {4, 2400}));
            table.forEach((item, v) -> {
                h.assertValueEqual(FurnaceHeat.fuelHeat(new ItemStack(item)), v[0], item + " heat");
                h.assertValueEqual(probe.burn(item), v[1], item + " burn time");
            });
            h.succeed();
        });
        // EF-MET-07 CA-1 / B08: smelting and cooking XP.
        MiteGameTests.add("metallurgy_smelting_xp", 20, h -> {
            Map.ofEntries(
                    Map.entry("minecraft:copper_ingot_from_blasting_raw_copper", 10f), Map.entry("minecraft:iron_ingot_from_smelting_raw_iron", 10f),
                    Map.entry("minecraft:gold_ingot_from_smelting_nether_gold_ore", 20f), Map.entry("hardscrabble:silver_ingot_from_smelting_raw_silver", 15f),
                    Map.entry("hardscrabble:mithril_ingot_from_smelting_raw_mithril", 40f), Map.entry("hardscrabble:adamantium_ingot_from_smelting_raw_adamantium", 100f),
                    Map.entry("minecraft:diamond_from_smelting_diamond_ore", 30f), Map.entry("minecraft:emerald_from_blasting_emerald_ore", 20f),
                    Map.entry("minecraft:quartz", 10f), Map.entry("minecraft:cooked_beef", 4f), Map.entry("minecraft:cooked_salmon_from_smoking", 4f),
                    Map.entry("minecraft:cooked_mutton_from_campfire_cooking", 2f), Map.entry("minecraft:baked_potato", 0f)
            ).forEach((id, xp) -> {
                var recipe = h.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).orElseThrow();
                h.assertValueEqual(((AbstractCookingRecipe) recipe.value()).experience(), xp, id + " xp");
            });
            h.succeed();
        });
    }

    private static AbstractFurnaceBlockEntity furnace(GameTestHelper h, int x, Block block, Item input, Item fuel) {
        BlockPos pos = new BlockPos(x, 1, 1);
        h.setBlock(pos, block);
        var furnace = h.getBlockEntity(pos, AbstractFurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(input));
        furnace.setItem(1, new ItemStack(fuel));
        return furnace;
    }

    /** No progress: the input is still there, nothing came out and the fuel was not lit. */
    private static void assertStuck(GameTestHelper h, AbstractFurnaceBlockEntity furnace) {
        h.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(0).getCount() == 1 && furnace.getItem(1).getCount() == 1,
                "furnace should not progress: " + furnace.getItem(0) + " / " + furnace.getItem(1));
    }
}
