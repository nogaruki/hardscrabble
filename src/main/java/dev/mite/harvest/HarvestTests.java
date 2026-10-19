package dev.mite.harvest;

import dev.mite.gametest.MiteGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** GameTests for EF-OUT-02..07. */
public final class HarvestTests {
    private HarvestTests() {}

    private static final BlockPos POS = new BlockPos(3, 1, 3);

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): tool_lashing=false accepts the unlashed recipe and lets the unlashed recipes into the book.
        MiteGameTests.add("config_tool_lashing", 20, h -> {
            ItemStack hatchet = new ItemStack(tool("flint_hatchet"));
            java.util.List<ItemStack> unlashed = java.util.List.of(new ItemStack(Items.FLINT), new ItemStack(Items.STICK));
            var condition = new dev.mite.crafting.CraftingContent.LashingDisabled();
            boolean refused = MiteGameTests.withConfig(dev.mite.config.MiteConfig.TOOL_LASHING, false, () -> HarvestRules.missingLashing(hatchet, unlashed));
            boolean shown = MiteGameTests.withConfig(dev.mite.config.MiteConfig.TOOL_LASHING, false, () -> condition.test(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY));
            h.assertFalse(refused, "unlashed hatchet refused with tool_lashing=false");
            h.assertTrue(shown, "unlashed recipes hidden with tool_lashing=false");
            h.assertTrue(HarvestRules.missingLashing(hatchet, unlashed), "unlashed hatchet accepted with tool_lashing=true");
            h.assertFalse(condition.test(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY), "unlashed recipes shown with tool_lashing=true");
            h.succeed();
        });

        // EF-OUT-03 CA-1: a bare hand makes no progress on a log, but still digs dirt.
        MiteGameTests.add("harvest_bare_hand_log", 20, h -> {
            ServerPlayer player = player(h, ItemStack.EMPTY);
            h.assertValueEqual(progress(h, player, Blocks.OAK_LOG), 0f, "bare-hand progress on a log");
            h.assertTrue(progress(h, player, Blocks.DIRT) > 0, "bare hand digs dirt");
            h.succeed();
        });
        // EF-OUT-02: a flint hatchet harvests a log and wears by max(1.2 x 100 x 4/3, ...) = 160.
        MiteGameTests.add("harvest_flint_hatchet_log", 20, h -> {
            ServerPlayer player = player(h, new ItemStack(tool("flint_hatchet")));
            h.assertTrue(progress(h, player, Blocks.OAK_LOG) > 0, "flint hatchet mines a log");
            player.gameMode.destroyBlock(h.absolutePos(POS));
            h.assertBlockNotPresent(Blocks.OAK_LOG, POS);
            h.assertItemEntityPresent(Items.OAK_LOG, POS, 2);
            h.assertValueEqual(player.getMainHandItem().getDamageValue(), 160, "flint hatchet wear");
            h.succeed();
        });
        // EF-OUT-06 CA-1: a new copper pickaxe breaks exactly 20 stone blocks and breaks on the 20th.
        MiteGameTests.add("harvest_copper_pickaxe_20_stone", 20, h -> {
            ServerPlayer player = player(h, new ItemStack(Items.COPPER_PICKAXE));
            for (int i = 1; i <= 20; i++) {
                h.assertFalse(player.getMainHandItem().isEmpty(), "pickaxe broke before stone #" + i);
                h.setBlock(POS, Blocks.STONE);
                player.gameMode.destroyBlock(h.absolutePos(POS));
                h.assertBlockNotPresent(Blocks.STONE, POS);
            }
            h.assertTrue(player.getMainHandItem().isEmpty(), "pickaxe should break on the 20th stone");
            h.assertItemEntityPresent(Items.COBBLESTONE, POS, 2);
            h.succeed();
        });
        // EF-OUT-02 CA-1: a tool below the block level gives no drop and cannot mine.
        MiteGameTests.add("harvest_lower_tier_no_drop", 20, h -> {
            ServerPlayer player = player(h, new ItemStack(tool("flint_axe")));
            h.assertValueEqual(progress(h, player, Blocks.STONE), 0f, "flint axe progress on stone");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_PICKAXE));
            h.assertValueEqual(progress(h, player, Blocks.OBSIDIAN), 0f, "copper pickaxe progress on obsidian");
            player.gameMode.destroyBlock(h.absolutePos(POS));
            h.assertItemEntityNotPresent(Items.OBSIDIAN, POS, 2);
            h.succeed();
        });
        // EF-OUT-05: durability x100.
        MiteGameTests.add("harvest_durability", 20, h -> {
            h.assertValueEqual(new ItemStack(Items.COPPER_PICKAXE).getMaxDamage(), 4800, "copper pickaxe");
            h.assertValueEqual(new ItemStack(Items.IRON_SWORD).getMaxDamage(), 6400, "iron sword");
            h.assertValueEqual(new ItemStack(Items.WOODEN_SHOVEL).getMaxDamage(), 200, "wooden shovel");
            h.assertValueEqual(new ItemStack(tool("flint_hatchet")).getMaxDamage(), 400, "flint hatchet");
            h.assertValueEqual(new ItemStack(tool("obsidian_axe")).getMaxDamage(), 2400, "obsidian axe");
            h.assertValueEqual(new ItemStack(tool("wooden_club")).getMaxDamage(), 400, "wooden club");
            h.assertValueEqual(new ItemStack(tool("adamantium_pickaxe")).getMaxDamage(), 307_200, "adamantium pickaxe");
            h.succeed();
        });
        // EF-OUT-02 CA-2 (M14): a cobweb still breaks by hand but drops no string; a sword gets the string.
        MiteGameTests.add("harvest_cobweb_bare_hand", 20, h -> {
            ServerPlayer player = player(h, ItemStack.EMPTY);
            h.assertTrue(progress(h, player, Blocks.COBWEB) > 0, "bare hand breaks a cobweb");
            player.gameMode.destroyBlock(h.absolutePos(POS));
            h.assertItemEntityNotPresent(Items.STRING, POS, 2);
            h.assertTrue(HarvestRules.canHarvest(new ItemStack(Items.IRON_SWORD), Blocks.COBWEB.defaultBlockState()), "sword harvests a cobweb");
            h.succeed();
        });
        // esprit-early_game-03: thin snow is a no-tool block.
        MiteGameTests.add("harvest_snow_layer_bare_hand", 20, h -> {
            ServerPlayer player = player(h, ItemStack.EMPTY);
            h.assertTrue(progress(h, player, Blocks.SNOW) > 0, "bare hand digs thin snow");
            h.assertTrue(HarvestRules.canHarvest(ItemStack.EMPTY, Blocks.SNOW.defaultBlockState()), "bare hand harvests thin snow");
            h.succeed();
        });
        // EF-OUT-10 (M13): an empty hand reaches 1.5 blocks; any held item keeps the longer reach.
        MiteGameTests.add("reach_bare_hand", 20, h -> {
            ServerPlayer player = player(h, ItemStack.EMPTY);
            Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 3.5)); // 1.62 blocks from the eye to the block
            player.setPos(at.x, at.y, at.z);
            h.setBlock(POS, Blocks.DIRT);
            BlockPos target = h.absolutePos(POS);
            h.assertFalse(player.isWithinBlockInteractionRange(target, 0), "dirt in bare-hand reach");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            h.assertTrue(player.isWithinBlockInteractionRange(target, 0), "dirt out of reach with an item");
            h.succeed();
        });
        // EF-OUT-04 (dom-D-08): Mining Fatigue I costs 20 % of the speed, not 70 %.
        MiteGameTests.add("harvest_mining_fatigue", 20, h -> {
            ServerPlayer player = player(h, new ItemStack(Items.COPPER_PICKAXE));
            float base = progress(h, player, Blocks.STONE);
            player.getActiveEffectsMap().put(MobEffects.MINING_FATIGUE, new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 0)); // no packet for a mock player
            h.assertTrue(Math.abs(progress(h, player, Blocks.STONE) / base - 0.8f) < 1e-3, "fatigue I speed ratio");
            h.succeed();
        });
        // dom-D-10 (changelog R156, R127): axes mine clay and terracotta, blades hay and carpets.
        MiteGameTests.add("harvest_axe_clay_blade_hay", 20, h -> {
            var axe = new ItemStack(Items.COPPER_AXE).get(DataComponents.TOOL);
            var sword = new ItemStack(Items.COPPER_SWORD).get(DataComponents.TOOL);
            for (Block b : new Block[]{Blocks.CLAY, Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.asList().get(14)})
                h.assertTrue(axe.getMiningSpeed(b.defaultBlockState()) > axe.defaultMiningSpeed(), "axe not efficient on " + b);
            for (Block b : new Block[]{Blocks.HAY_BLOCK, Blocks.CARPET.asList().get(0)})
                h.assertTrue(sword.getMiningSpeed(b.defaultBlockState()) > sword.defaultMiningSpeed(), "sword not efficient on " + b);
            h.succeed();
        });
        // EF-OUT-02 (dom-D-11): a metal block needs a tool one rank above its metal; adamantium is unbreakable.
        MiteGameTests.add("harvest_metal_blocks", 20, h -> {
            h.assertFalse(HarvestRules.canHarvest(new ItemStack(Items.IRON_PICKAXE), Blocks.IRON_BLOCK.defaultBlockState()), "iron pickaxe on iron block");
            h.assertTrue(HarvestRules.canHarvest(new ItemStack(tool("mithril_pickaxe")), Blocks.IRON_BLOCK.defaultBlockState()), "mithril pickaxe on iron block");
            h.assertFalse(HarvestRules.canHarvest(new ItemStack(tool("adamantium_pickaxe")),
                    dev.mite.worldgen.WorldgenContent.storageBlock(dev.mite.core.Material.ADAMANTIUM).get().defaultBlockState()), "adamantium block breakable");
            h.succeed();
        });
        // EF-OUT-12 (dom-D-06, changelog R150): the flint hatchet needs lashing too.
        MiteGameTests.add("lashing_flint_hatchet", 20, h -> {
            ItemStack hatchet = new ItemStack(tool("flint_hatchet"));
            h.assertTrue(HarvestRules.missingLashing(hatchet, java.util.List.of(new ItemStack(Items.FLINT), new ItemStack(Items.STICK))), "unlashed hatchet accepted");
            h.assertFalse(HarvestRules.missingLashing(hatchet, java.util.List.of(new ItemStack(Items.FLINT), new ItemStack(Items.STRING))), "lashed hatchet refused");
            h.succeed();
        });
        // EF-OUT-07: removed vanilla recipes are gone, MITE tool recipes exist.
        MiteGameTests.add("harvest_recipes", 20, h -> {
            h.assertFalse(hasRecipe(h, "minecraft:stone_pickaxe"), "stone pickaxe recipe should be disabled");
            h.assertFalse(hasRecipe(h, "minecraft:diamond_chestplate"), "diamond chestplate recipe should be disabled");
            h.assertTrue(hasRecipe(h, "minecraft:wooden_shovel"), "wooden shovel recipe stays");
            h.assertTrue(hasRecipe(h, "hardscrabble:flint_hatchet_lashed"), "lashed flint hatchet recipe");
            h.assertValueEqual(hasRecipe(h, "hardscrabble:flint_hatchet"), new dev.mite.crafting.CraftingContent.LashingDisabled().test(ICondition.IContext.EMPTY),
                    "unlashed flint hatchet recipe only without tool_lashing (EF-OUT-12)");
            h.assertFalse(hasRecipe(h, "minecraft:sandstone"), "sandstone crafting recipe should be disabled (R121)");
            h.succeed();
        });
    }

    private static boolean hasRecipe(GameTestHelper h, String id) {
        return h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent();
    }

    private static ServerPlayer player(GameTestHelper h, ItemStack held) {
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    private static float progress(GameTestHelper h, ServerPlayer player, Block block) {
        h.setBlock(POS, block);
        BlockPos abs = h.absolutePos(POS);
        return h.getLevel().getBlockState(abs).getDestroyProgress(player, h.getLevel(), abs);
    }

    private static Item tool(String name) {
        return HarvestContent.TOOLS.stream().filter(t -> t.item().getId().getPath().equals(name)).findFirst().orElseThrow().item().get();
    }
}
