package dev.mite.crafting;

import dev.mite.core.Formulas;
import dev.mite.equipment.EquipmentContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.metabolism.MetabolismContent;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.player.PlayerLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Audit 2, lot C GameTests: workbench chain (X8), repairs (X11, E-3), webs (l.114), bricks (l.771-774), water bowls (C-3), hunger (C-2). */
public final class CraftingTests2 {
    private CraftingTests2() {}

    private static final BlockPos BENCH = new BlockPos(3, 1, 3);

    public static void register() {
        // X8 / EF-PRG-01: each metal workbench needs the bench of the previous tier, never the 2x2 grid; flint and obsidian stay by hand.
        MiteGameTests.add("crafting_metal_bench_needs_previous_tier", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu inventory = player.inventoryMenu;
            TimedCrafting.hold(player, false);
            fill(inventory, Items.COPPER_INGOT, Items.LEATHER, Items.STICK, Items.OAK_PLANKS);
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 0, "copper bench in the 2x2 grid");
            fill(inventory, Items.OBSIDIAN, MaterialItems.SINEW.get(), Items.STICK, Items.OAK_LOG);
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "obsidian bench refused in the 2x2 grid");
            fill(inventory, null, null, null, null);
            Object[][] cases = {
                    {Blocks.CRAFTING_TABLE, Items.COPPER_INGOT, true}, {Blocks.CRAFTING_TABLE, Items.GOLD_INGOT, true},
                    {Blocks.CRAFTING_TABLE, Items.IRON_INGOT, false}, {bench(Workbench.OBSIDIAN), Items.IRON_INGOT, false},
                    {bench(Workbench.COPPER), Items.IRON_INGOT, true}, {bench(Workbench.GOLD), Items.IRON_INGOT, true},
                    {bench(Workbench.IRON), MaterialItems.ANCIENT_METAL_INGOT.get(), true}, {bench(Workbench.IRON), MaterialItems.MITHRIL_INGOT.get(), false},
                    {bench(Workbench.ANCIENT_METAL), MaterialItems.MITHRIL_INGOT.get(), true},
                    {bench(Workbench.ANCIENT_METAL), MaterialItems.ADAMANTIUM_INGOT.get(), false},
                    {bench(Workbench.MITHRIL), MaterialItems.ADAMANTIUM_INGOT.get(), true}};
            for (Object[] c : cases) {
                AbstractContainerMenu menu = openBench(h, player, (Block) c[0]);
                menu.getSlot(1).set(new ItemStack((Item) c[1]));
                menu.getSlot(2).set(new ItemStack(Items.LEATHER));
                menu.getSlot(4).set(new ItemStack(Items.STICK));
                menu.getSlot(5).set(new ItemStack(Items.OAK_PLANKS));
                h.assertValueEqual(TimedCrafting.pendingTicks(player) > 0, (boolean) c[2], c[1] + " bench on " + c[0]);
                for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
            }
            h.succeed();
        }));
        // E-3 (R151): combining two copper pickaxes takes the time of half a recipe each (3 ingots x 400 / 2), and adds their
        // remaining durabilities without the vanilla 5 % bonus.
        MiteGameTests.add("crafting_repair_by_combining", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, bench(Workbench.COPPER));
            int max = new ItemStack(Items.COPPER_PICKAXE).getMaxDamage();
            for (int slot : new int[] {1, 2}) {
                ItemStack pick = new ItemStack(Items.COPPER_PICKAXE);
                pick.setDamageValue(max - 100);
                menu.getSlot(slot).set(pick);
            }
            int ticks = Formulas.craftTicks(2 * 3 * 400 / 2f, PlayerLevel.get(player), Workbench.COPPER.bonusFor(dev.mite.core.Material.COPPER), false);
            h.assertValueEqual(TimedCrafting.pendingTicks(player), ticks, "repair ticks");
            for (int i = 0; i < ticks; i++) TimedCrafting.tick(player);
            ItemStack result = menu.getSlot(0).getItem();
            h.assertTrue(result.is(Items.COPPER_PICKAXE), "no repaired pickaxe: " + result);
            h.assertValueEqual(result.getDamageValue(), max - 200, "remaining durability 100 + 100");
            h.succeed();
        }));
        // X11 / l.778, l.331: leather armor + sinew or string on a bench; each restores 1/8 of a chestplate; an extra lashing is refused (l.412).
        MiteGameTests.add("crafting_leather_armor_repair", 20, h -> MiteGameTests.withPlayer(h, player -> {
            AbstractContainerMenu menu = openBench(h, player, Blocks.CRAFTING_TABLE);
            ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
            int per = LeatherRepairRecipe.perLashing(chest);
            h.assertValueEqual(per, Math.round(chest.getMaxDamage() / 8f), "durability per lashing");
            chest.setDamageValue(3 * per + 10);
            menu.getSlot(1).set(chest);
            menu.getSlot(2).set(new ItemStack(MaterialItems.SINEW.get()));
            menu.getSlot(3).set(new ItemStack(Items.STRING));
            int ticks = TimedCrafting.pendingTicks(player);
            h.assertTrue(ticks > 0, "leather repair refused");
            for (int i = 0; i < ticks; i++) TimedCrafting.tick(player);
            h.assertValueEqual(menu.getSlot(0).getItem().getDamageValue(), per + 10, "repaired damage");
            ItemStack light = new ItemStack(Items.LEATHER_CHESTPLATE);
            light.setDamageValue(per / 2);
            menu.getSlot(1).set(light);
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 0, "wasted lashing accepted");
            menu.getSlot(3).set(ItemStack.EMPTY);
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "one lashing refused");
            menu.getSlot(2).set(new ItemStack(Items.IRON_NUGGET));
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 0, "iron nugget repairs leather");
            h.succeed();
        }));
        // Changelog l.114 (R180): no crafting with the upper body in a web.
        MiteGameTests.add("crafting_blocked_in_web", 20, h -> MiteGameTests.withPlayer(h, player -> {
            player.snapTo(h.absoluteVec(new Vec3(1.5, 1, 1.5)), 0, 0);
            h.setBlock(BlockPos.containing(h.relativeVec(player.getEyePosition())), Blocks.COBWEB);
            AbstractContainerMenu menu = openBench(h, player, bench(Workbench.COPPER));
            for (int slot : new int[] {1, 2, 3}) menu.getSlot(slot).set(new ItemStack(Items.COPPER_INGOT));
            menu.getSlot(5).set(new ItemStack(Items.STICK));
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            int ticks = TimedCrafting.pendingTicks(player);
            h.assertTrue(TimedCrafting.inWeb(player), "player not in the web");
            for (int i = 0; i < ticks + 5; i++) TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "crafted in a web");
            h.setBlock(BlockPos.containing(h.relativeVec(player.getEyePosition())), Blocks.AIR);
            for (int i = 0; i < ticks; i++) TimedCrafting.tick(player);
            h.assertTrue(menu.getSlot(0).getItem().is(Items.COPPER_PICKAXE), "no pickaxe out of the web");
            h.succeed();
        }));
        // Changelog l.771-773 (R119): 4 bricks -> hardened clay; 8 bricks + sand -> 2 bricks blocks; 8 nether bricks + soul sand -> 2.
        MiteGameTests.add("crafting_brick_recipes", 20, h -> {
            h.assertTrue(result(h, 2, Items.BRICK, Items.BRICK, Items.BRICK, Items.BRICK).filter(s -> s.is(Items.TERRACOTTA)).isPresent(), "4 bricks -> hardened clay");
            h.assertTrue(result(h, 2, Items.NETHER_BRICK, Items.NETHER_BRICK, Items.NETHER_BRICK, Items.NETHER_BRICK).isEmpty(), "4 nether bricks still craft");
            h.assertTrue(result(h, 3, ring(Items.BRICK, Items.SAND)).filter(s -> s.is(Items.BRICKS) && s.getCount() == 2).isPresent(), "8 bricks + sand");
            h.assertTrue(result(h, 3, ring(Items.NETHER_BRICK, Items.SOUL_SAND)).filter(s -> s.is(Items.NETHER_BRICKS) && s.getCount() == 2).isPresent(),
                    "8 nether bricks + soul sand");
            h.succeed();
        });
        // Changelog l.774 / l.679: a thrown brick breaks thin glass and drops where it lands; throwing uses the brick. The survival
        // player throws the other way, so the only brick that can lie at the pane is the one that broke it.
        MiteGameTests.add("crafting_thrown_brick", 40, h -> {
            h.setBlock(3, 1, 4, Blocks.GLASS_PANE);
            ThrownBrick brick = new ThrownBrick(CraftingContent.THROWN_BRICK.get(), h.getLevel());
            brick.setPos(h.absoluteVec(new Vec3(3.5, 1.5, 1.5)));
            brick.setDeltaMovement(0, 0.1, 0.6);
            h.getLevel().addFreshEntity(brick);
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.snapTo(h.absoluteVec(new Vec3(0.5, 1, 5.5)), 180, 0);
            ItemStack bricks = new ItemStack(Items.BRICK, 3);
            ThrownBrick thrown = ThrownBrick.throwFrom(h.getLevel(), player, bricks);
            h.assertTrue(thrown.isAlive() && thrown.getOwner() == player, "brick not thrown");
            h.assertValueEqual(bricks.getCount(), 2, "bricks left after a throw");
            AABB pane = new AABB(h.absolutePos(new BlockPos(3, 1, 4))).inflate(0.9);
            h.succeedWhen(() -> {
                h.assertBlockPresent(Blocks.AIR, 3, 1, 4);
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, pane).stream().anyMatch(e -> e.getItem().is(Items.BRICK)),
                        "no brick dropped at the pane");
            });
        });
        // C-3 / EF-NUT-12: any water bucket fills 4 bowls and comes back empty.
        MiteGameTests.add("crafting_water_bowls_any_bucket", 20, h -> {
            var copper = EquipmentContent.BUCKETS.get(dev.mite.core.Material.COPPER);
            List<ItemStack> items = new ArrayList<>(List.of(new ItemStack(copper.water().get())));
            for (int i = 0; i < 4; i++) items.add(new ItemStack(Items.BOWL));
            items.add(ItemStack.EMPTY);
            CraftingInput input = CraftingInput.of(3, 2, items);
            RecipeHolder<CraftingRecipe> recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
            ItemStack out = recipe.value().assemble(input);
            h.assertTrue(out.is(MetabolismContent.WATER_BOWL.get()) && out.getCount() == 4, "4 water bowls, got " + out);
            h.assertTrue(recipe.value().getRemainingItems(input).stream().anyMatch(s -> s.is(copper.empty().get())), "copper bucket not given back");
            h.succeed();
        });
        // C-2: breaking a block costs its 1.6.4 hunger (0.025, as much as placing one), not 26.3's 0.005.
        MiteGameTests.add("metabolism_break_costs_164", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(BENCH, Blocks.DIRT);
            float before = exhaustion(p);
            p.gameMode.destroyBlock(h.absolutePos(BENCH));
            h.assertBlockPresent(Blocks.AIR, BENCH);
            h.assertTrue(Math.abs(exhaustion(p) - before - Formulas.HUNGER_BREAK) < 1e-5, "break cost " + (exhaustion(p) - before));
            h.succeed();
        });
    }

    private static Block bench(Workbench w) {
        return CraftingContent.WORKBENCHES.get(w).get();
    }

    private static Item[] ring(Item outer, Item center) {
        Item[] items = new Item[9];
        java.util.Arrays.fill(items, outer);
        items[4] = center;
        return items;
    }

    /** The crafting result of a {@code size} x {@code size} grid, if any recipe matches. */
    private static Optional<ItemStack> result(GameTestHelper h, int size, Item... items) {
        List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(size * size, ItemStack.EMPTY));
        for (int i = 0; i < items.length; i++) stacks.set(i, new ItemStack(items[i]));
        CraftingInput input = CraftingInput.of(size, size, stacks);
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).map(r -> r.value().assemble(input));
    }

    /** The vanilla exhaustion accrued since the last food tick (FoodData keeps it private). */
    private static float exhaustion(ServerPlayer p) {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        p.getFoodData().addAdditionalSaveData(out);
        return out.buildResult().getFloatOr("foodExhaustionLevel", 0);
    }

    /** Fills the 2x2 inventory grid (slots 1..4), null = empty. */
    private static void fill(AbstractContainerMenu menu, Item... items) {
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

}
