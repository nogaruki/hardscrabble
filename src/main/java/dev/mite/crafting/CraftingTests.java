package dev.mite.crafting;

import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** EF-CRA-01..03 GameTests. Timers are driven by calling {@link TimedCrafting#tick} directly. */
public final class CraftingTests {
    private CraftingTests() {}

    private static final BlockPos BENCH = new BlockPos(3, 1, 3);

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): quality_enabled=false crafts without a quality; on, the result gets one.
        MiteGameTests.add("config_quality_enabled", 20, h -> {
            ServerPlayer p = dev.mite.metabolism.MetabolismTests.player(h);
            java.util.List<ItemStack> grid = java.util.List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.STICK));
            ItemStack off = new ItemStack(Items.COPPER_PICKAXE), on = new ItemStack(Items.COPPER_PICKAXE);
            boolean none = MiteGameTests.withConfig(dev.mite.config.MiteConfig.QUALITY_ENABLED, false, () -> CraftQuality.apply(p, off, grid, 1250) == null);
            h.assertTrue(none && !off.has(CraftQuality.QUALITY.get()), "quality with quality_enabled=false");
            h.assertTrue(CraftQuality.apply(p, on, grid, 1250) != null && on.has(CraftQuality.QUALITY.get()), "no quality with quality_enabled=true");
            h.succeed();
        });

        // EF-CRA-01: copper pickaxe (1250) at level 0 on a copper bench (+0.3) = 293 ticks.
        MiteGameTests.add("crafting_copper_pickaxe_time", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            pickaxe(menu, Items.COPPER_INGOT);
            h.assertTrue(TimedCrafting.pendingTicks(player) == 293, "expected 293 ticks, got " + TimedCrafting.pendingTicks(player));
            for (int i = 0; i < 292; i++) TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "result available before the timer ended");
            TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().is(Items.COPPER_PICKAXE), "no pickaxe after 293 ticks");
            h.succeed();
        }));
        // EF-CRA-02: a metal recipe is refused on the flint bench (vanilla crafting table).
        MiteGameTests.add("crafting_flint_bench_refuses_metal", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, Blocks.CRAFTING_TABLE);
            pickaxe(menu, Items.COPPER_INGOT);
            h.assertTrue(TimedCrafting.pendingTicks(player) == 0, "copper pickaxe accepted on a flint bench");
            TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "result shown on a flint bench");
            h.succeed();
        }));
        // dom-P-08: "needs a better workbench" names the bench the recipe's hardest metal needs.
        MiteGameTests.add("crafting_refusal_names_bench", 20, h -> {
            h.assertValueEqual(((net.minecraft.network.chat.contents.TranslatableContents) TimedCrafting.benchName(dev.mite.core.Material.IRON).getContents()).getKey(),
                    "block.hardscrabble.iron_workbench", "iron recipe");
            h.assertValueEqual(((net.minecraft.network.chat.contents.TranslatableContents) TimedCrafting.benchName(dev.mite.core.Material.COPPER).getContents()).getKey(),
                    "block.hardscrabble.copper_workbench", "copper recipe");
            // Gold (3.0) is softer than copper and silver (4.0): its own bench is the lowest one, not the copper bench declared before it.
            h.assertValueEqual(Workbench.required(dev.mite.core.Material.GOLD), Workbench.GOLD, "gold recipe");
            h.assertValueEqual(Workbench.required(null), Workbench.FLINT, "recipe without metal");
            h.succeed();
        });
        // EF-CRA-03: the 2x2 grid refuses a result outside hardscrabble:inventory_craftable, accepts one inside.
        MiteGameTests.add("crafting_inventory_grid_restricted", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = player.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(Items.COAL));
            menu.getSlot(3).set(new ItemStack(Items.STICK));
            h.assertTrue(TimedCrafting.pendingTicks(player) == 0, "torch accepted in the 2x2 grid");
            menu.getSlot(1).set(ItemStack.EMPTY);
            menu.getSlot(3).set(new ItemStack(Items.OAK_LOG));
            h.assertTrue(TimedCrafting.pendingTicks(player) == 0, "planks accepted in the 2x2 grid (R149)");
            menu.getSlot(3).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "sticks refused in the 2x2 grid");
            h.succeed();
        }));
        // EF-CRA-01 CA-1: the 5 examples (average, bench of the metal, level 0) last 293, 427, 1728, 4819 and 11524 ticks.
        MiteGameTests.add("crafting_five_examples", 20, h -> MiteGameTests.withPlayer(h, player -> {
            example(h, player, Workbench.COPPER, Items.COPPER_INGOT, false, 293);
            example(h, player, Workbench.IRON, Items.IRON_INGOT, false, 427);
            example(h, player, Workbench.MITHRIL, MaterialItems.MITHRIL_INGOT.get(), false, 1728);
            example(h, player, Workbench.ADAMANTIUM, MaterialItems.ADAMANTIUM_INGOT.get(), false, 4819);
            example(h, player, Workbench.ADAMANTIUM, MaterialItems.ADAMANTIUM_INGOT.get(), true, 11524);
            h.succeed();
        }));
        // EF-CRA-01: the craft only progresses while the action is held, and starts over on release.
        MiteGameTests.add("crafting_needs_hold", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            TimedCrafting.release(player);
            pickaxe(menu, Items.COPPER_INGOT);
            for (int i = 0; i < 400; i++) TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "craft progressed without holding");
            TimedCrafting.hold(player, false);
            for (int i = 0; i < 200; i++) TimedCrafting.tick(player);
            TimedCrafting.release(player);
            TimedCrafting.tick(player);
            TimedCrafting.hold(player, false);
            for (int i = 0; i < 292; i++) TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "progress kept after a release");
            TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().is(Items.COPPER_PICKAXE), "no pickaxe after a full hold");
            h.succeed();
        }));
        // EF-CRA-01 CA-4: holding with shift moves each finished result to the inventory and starts the next full craft.
        MiteGameTests.add("crafting_shift_chains", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            TimedCrafting.hold(player, true);
            for (int slot : new int[] {1, 2, 3}) menu.getSlot(slot).set(new ItemStack(Items.COPPER_INGOT, 2));
            menu.getSlot(5).set(new ItemStack(Items.STICK, 2));
            menu.getSlot(8).set(new ItemStack(Items.STICK, 2));
            for (int i = 0; i < 293; i++) TimedCrafting.tick(player);
            h.assertTrue(player.getInventory().countItem(Items.COPPER_PICKAXE) == 1, "first pickaxe not moved to the inventory");
            h.assertTrue(TimedCrafting.pendingTicks(player) == 293, "second craft not started with its full time");
            for (int i = 0; i < 293; i++) TimedCrafting.tick(player);
            h.assertTrue(player.getInventory().countItem(Items.COPPER_PICKAXE) == 2, "second pickaxe missing");
            h.succeed();
        }));
        // R165 (village unlock): a finished craft taken by the shift chain or a plain pickup posts ItemCraftedEvent and the crafted stat with the item, never air.
        MiteGameTests.add("crafting_take_reports_crafted_item", 20, h -> MiteGameTests.withPlayer(h, player -> {
            java.util.List<ItemStack> crafted = new java.util.ArrayList<>();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent> listener = e -> {
                if (e.getEntity() == player) crafted.add(e.getCrafting().copy());
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try {
                for (boolean shift : new boolean[] {true, false}) {
                    AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.IRON).get());
                    TimedCrafting.hold(player, shift);
                    pickaxe(menu, Items.IRON_INGOT);
                    for (int i = TimedCrafting.pendingTicks(player); i > 0; i--) TimedCrafting.tick(player);
                    if (!shift) menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
                    h.assertTrue(crafted.size() == 1 && crafted.getFirst().is(Items.IRON_PICKAXE), (shift ? "shift" : "pickup") + " take reported " + crafted);
                    crafted.clear();
                }
                h.assertValueEqual(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(Items.IRON_PICKAXE)), 2, "crafted stat");
                h.assertValueEqual(player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(Items.AIR)), 0, "air crafted stat");
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            }
            h.succeed();
        }));
        // R149 / EF-CRA-02: combination repair of metal tools needs a bench of their metal.
        MiteGameTests.add("crafting_repair_needs_bench", 20, h -> MiteGameTests.withPlayer(h, player -> {
            for (Block bench : new Block[] {Blocks.CRAFTING_TABLE, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get()}) {
                AbstractContainerMenu menu = openBench(h, player, bench);
                for (int slot : new int[] {1, 2}) {
                    ItemStack pick = new ItemStack(Items.COPPER_PICKAXE);
                    pick.setDamageValue(1000);
                    menu.getSlot(slot).set(pick);
                }
                boolean flint = bench == Blocks.CRAFTING_TABLE;
                h.assertTrue((TimedCrafting.pendingTicks(player) > 0) != flint, flint ? "repair accepted on a flint bench" : "repair refused on a copper bench");
                for (int slot : new int[] {1, 2}) menu.getSlot(slot).set(ItemStack.EMPTY);
            }
            h.succeed();
        }));
        // EF-CRA-02: flint bench from sinew; knife on a log; the bench comes apart into the knife (CA-3). All in the 2x2 grid.
        MiteGameTests.add("crafting_flint_bench_variants", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = player.inventoryMenu;
            Item knife = BuiltInRegistries.ITEM.getValue(dev.mite.MiteMod.id("flint_knife"));
            grid(menu, Items.FLINT, MaterialItems.SINEW.get(), Items.STICK, Items.OAK_LOG);
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "flint bench with sinew refused");
            grid(menu, knife, Items.OAK_LOG, null, null);
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "flint bench from a knife refused");
            grid(menu, Items.CRAFTING_TABLE, null, null, null);
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 25, "bench disassembly ticks");
            // Changelog l.341: the bench gives a new knife back, so a damaged knife must not go into one (free repair for a log).
            ItemStack worn = new ItemStack(knife);
            worn.setDamageValue(worn.getMaxDamage() - 1);
            menu.getSlot(1).set(worn);
            menu.getSlot(2).set(new ItemStack(Items.OAK_LOG));
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 0, "flint bench from a damaged knife");
            h.succeed();
        }));
        // EF-CRA-03 / EF-MET-05: 4 clay balls make a clay block, and 4 blocks a clay furnace, without a bench.
        MiteGameTests.add("crafting_clay_furnace_by_hand", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = player.inventoryMenu;
            grid(menu, Items.CLAY_BALL, Items.CLAY_BALL, Items.CLAY_BALL, Items.CLAY_BALL);
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "clay block refused in the 2x2 grid");
            grid(menu, Items.CLAY, Items.CLAY, Items.CLAY, Items.CLAY);
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "clay furnace refused in the 2x2 grid");
            h.succeed();
        }));
        // dom-E-02: chains carry their metal, so chainmail is refused on the flint bench.
        MiteGameTests.add("crafting_chain_material", 20, h -> {
            h.assertFalse(Workbench.FLINT.accepts(TimedCrafting.hardestMetal(java.util.List.of(new ItemStack(Items.IRON_CHAIN)))), "iron chain accepted on a flint bench");
            h.succeed();
        });
        // EF-CRA-08 / EF-OUT-12: a crafter set to an unlashed flint axe produces nothing, while the same crafter does craft a
        // metal-free recipe. With tool_lashing on, the unlashed recipe is not even loaded; the grid is still refused as unlashed.
        MiteGameTests.add("crafter_refuses_unlashed", 20, h -> {
            h.setBlock(BENCH, Blocks.CRAFTER);
            BlockPos abs = h.absolutePos(BENCH);
            var crafter = (net.minecraft.world.level.block.entity.CrafterBlockEntity) h.getLevel().getBlockEntity(abs);
            Item axe = BuiltInRegistries.ITEM.getValue(dev.mite.MiteMod.id("flint_axe"));
            for (int slot : new int[] {0, 1, 3}) crafter.setItem(slot, new ItemStack(Items.FLINT));
            crafter.setItem(4, new ItemStack(Items.STICK));
            crafter.setItem(7, new ItemStack(Items.STICK));
            h.assertTrue(TimedCrafting.missingLashing(new ItemStack(axe), crafter.getItems()), "unlashed axe grid not missing a lashing");
            h.getLevel().getBlockState(abs).tick(h.getLevel(), abs, h.getLevel().getRandom());
            h.assertTrue(crafter.getItem(0).is(Items.FLINT), "crafter consumed the flint");
            h.assertItemEntityNotPresent(axe, BENCH, 3);
            crafter.setItem(2, new ItemStack(MaterialItems.SINEW.get()));
            h.assertFalse(TimedCrafting.missingLashing(new ItemStack(axe), crafter.getItems()), "lashed axe grid missing a lashing");
            for (int slot = 0; slot < 9; slot++) crafter.setItem(slot, ItemStack.EMPTY);
            crafter.setItem(0, new ItemStack(Items.OAK_PLANKS));
            crafter.setItem(3, new ItemStack(Items.OAK_PLANKS));
            h.getLevel().getBlockState(abs).tick(h.getLevel(), abs, h.getLevel().getRandom());
            h.assertTrue(crafter.getItem(0).isEmpty() && crafter.getItem(3).isEmpty(), "crafter did not craft sticks: " + crafter.getItem(0));
            h.succeed();
        });
        // A player who leaves mid-craft is released by the crafting maps (their values hold the menu, which reaches the player).
        MiteGameTests.add("crafting_logout_releases_player", 20, h -> {
            ServerPlayer player = player(h);
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            pickaxe(menu, Items.COPPER_INGOT);
            boolean pending = TimedCrafting.pendingTicks(player) > 0 && TimedCrafting.tracks(player);
            h.getLevel().getServer().getPlayerList().remove(player); // the logout under test
            h.assertTrue(pending, "no craft pending");
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 0, "pending ticks after logout");
            h.assertFalse(TimedCrafting.tracks(player), "player still kept by the crafting maps");
            h.succeed();
        });
        // Re-sending the same target quality (crafting screen re-init) must not restart the craft in progress.
        MiteGameTests.add("crafting_same_target_keeps_progress", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            pickaxe(menu, Items.COPPER_INGOT);
            int ticks = TimedCrafting.pendingTicks(player);
            for (int i = 0; i < ticks - 1; i++) TimedCrafting.tick(player);
            CraftQuality.setTarget(player, CraftQuality.target(player));
            TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().is(Items.COPPER_PICKAXE), "craft restarted by an unchanged target");
            h.succeed();
        }));
        // EF-CRA-01 CA-2: closing the menu mid-craft consumes nothing and produces nothing.
        MiteGameTests.add("crafting_interrupted_consumes_nothing", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            pickaxe(menu, Items.COPPER_INGOT);
            for (int i = 0; i < 100; i++) TimedCrafting.tick(player);
            player.closeContainer();
            TimedCrafting.tick(player);
            h.assertTrue(TimedCrafting.pendingTicks(player) == 0, "craft still pending after closing");
            var inv = player.getInventory();
            h.assertTrue(inv.countItem(Items.COPPER_INGOT) == 3 && inv.countItem(Items.STICK) == 2 && inv.countItem(Items.COPPER_PICKAXE) == 0,
                    "ingredients consumed or pickaxe produced");
            h.succeed();
        }));
    }

    private static ServerPlayer player(GameTestHelper h) {
        return MiteGameTests.levelPlayer(h);
    }

    /** Pickaxe (or a 9-ingot storage block) on {@code bench}: asserts the craft time, then empties the grid. */
    private static void example(GameTestHelper h, ServerPlayer player, Workbench bench, Item ingot, boolean block, int ticks) {
        AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(bench).get());
        if (block) for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(new ItemStack(ingot));
        else pickaxe(menu, ingot);
        h.assertValueEqual(TimedCrafting.pendingTicks(player), ticks, bench + (block ? " block" : " pickaxe") + " ticks");
        for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
    }

    /** Fills the 2x2 inventory grid (slots 1..4), null = empty. */
    private static void grid(AbstractContainerMenu menu, Item... items) {
        for (int i = 0; i < 4; i++) menu.getSlot(i + 1).set(items[i] == null ? ItemStack.EMPTY : new ItemStack(items[i]));
    }

    private static AbstractContainerMenu openBench(GameTestHelper h, ServerPlayer player, Block block) {
        h.setBlock(BENCH, block);
        ContainerLevelAccess access = ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BENCH));
        AbstractContainerMenu menu = block instanceof WorkbenchBlock bench
                ? new WorkbenchBlock.Menu(1, player.getInventory(), access, bench)
                : new CraftingMenu(1, player.getInventory(), access);
        player.containerMenu = menu;
        TimedCrafting.hold(player, false);
        return menu;
    }

    /** XXX / .|. / .|. in a 3x3 grid (slots 1..9). */
    private static void pickaxe(AbstractContainerMenu menu, Item head) {
        for (int slot : new int[] {1, 2, 3}) menu.getSlot(slot).set(new ItemStack(head));
        menu.getSlot(5).set(new ItemStack(Items.STICK));
        menu.getSlot(8).set(new ItemStack(Items.STICK));
    }

}
