package dev.mite.harvest;

import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.core.Quality;
import dev.mite.core.ToolType;
import dev.mite.crafting.CraftQuality;
import dev.mite.crafting.CrafterRules;
import dev.mite.crafting.CraftingContent;
import dev.mite.crafting.TimedCrafting;
import dev.mite.crafting.Workbench;
import dev.mite.crafting.WorkbenchBlock;
import dev.mite.gametest.MiteGameTests;
import dev.mite.player.PlayerLevel;
import dev.mite.player.ProgressionService;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.phys.Vec3;

/** GameTests for crafting quality (EF-CRA-05), hybrid tools (EF-OUT-09), reach (EF-OUT-10), parry (EF-OUT-11), lashing (EF-OUT-12), crafter (EF-CRA-08). */
public final class QualityTests {
    private QualityTests() {}

    private static final BlockPos POS = new BlockPos(3, 1, 3);

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): crafter_restriction=false gives the crafter back its vanilla delay and its metal recipes.
        MiteGameTests.add("config_crafter_restriction", 20, h -> {
            h.setBlock(POS, Blocks.CRAFTER);
            BlockPos abs = h.absolutePos(POS);
            CrafterBlockEntity crafter = (CrafterBlockEntity) h.getLevel().getBlockEntity(abs);
            for (int slot : new int[]{0, 1, 2}) crafter.setItem(slot, new ItemStack(Items.COPPER_INGOT));
            crafter.setItem(4, new ItemStack(Items.STICK));
            crafter.setItem(7, new ItemStack(Items.STICK));
            boolean refused = MiteGameTests.withConfig(dev.mite.config.MiteConfig.CRAFTER_RESTRICTION, false, () -> CrafterRules.refuse(h.getLevel(), abs));
            int delay = MiteGameTests.withConfig(dev.mite.config.MiteConfig.CRAFTER_RESTRICTION, false, () -> CrafterRules.delay(h.getLevel(), abs, 4));
            h.assertFalse(refused, "metal refused with crafter_restriction=false");
            h.assertValueEqual(delay, 4, "crafter delay with crafter_restriction=false");
            h.assertTrue(CrafterRules.refuse(h.getLevel(), abs), "metal accepted with crafter_restriction=true");
            h.assertTrue(CrafterRules.delay(h.getLevel(), abs, 4) > 4, "vanilla delay with crafter_restriction=true");
            h.succeed();
        });

        // EF-CRA-05 CA-1: a fine iron pickaxe costs round(2 x 2450 / 5) = 980 XP, has 14 400 durability, takes difficulty x 2.
        MiteGameTests.add("quality_iron_pickaxe_fine", 20, h -> {
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            ProgressionService.setXp(player, 5000);
            CraftQuality.setTarget(player, Quality.FINE);
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.IRON).get());
            pickaxe(menu, Items.IRON_INGOT);
            int ticks = Formulas.craftTicks(2 * 2450, PlayerLevel.get(player), Workbench.IRON.bonus, false);
            h.assertValueEqual(TimedCrafting.pendingTicks(player), ticks, "fine craft ticks");
            for (int i = 0; i < ticks; i++) TimedCrafting.tick(player);
            Slot slot = menu.getSlot(0);
            h.assertValueEqual(slot.getItem().get(CraftQuality.QUALITY.get()), Quality.FINE, "quality");
            h.assertValueEqual(slot.getItem().getMaxDamage(), 14_400, "fine iron pickaxe durability");
            ItemStack taken = slot.remove(1);
            slot.onTake(player, taken);
            h.assertValueEqual(ProgressionService.xp(player), 5000 - 980, "XP after paying");
            h.succeed();
        });
        // EF-CRA-05: XP spent while the craft runs caps the payment at what is left; crafting never takes XP below 0.
        MiteGameTests.add("quality_payment_capped_at_take", 20, h -> {
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            ProgressionService.setXp(player, 5000);
            CraftQuality.setTarget(player, Quality.FINE);
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.IRON).get());
            pickaxe(menu, Items.IRON_INGOT);
            for (int i = TimedCrafting.pendingTicks(player); i > 0; i--) TimedCrafting.tick(player);
            ProgressionService.setXp(player, 100);
            Slot slot = menu.getSlot(0);
            ItemStack taken = slot.remove(1);
            slot.onTake(player, taken);
            h.assertValueEqual(ProgressionService.xp(player), 0, "XP after a payment larger than what is left");
            h.succeed();
        });
        // EF-CRA-05: the target is capped by the material (copper: excellent).
        MiteGameTests.add("quality_capped_by_material", 20, h -> {
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            ProgressionService.setXp(player, 100_000);
            CraftQuality.setTarget(player, Quality.LEGENDARY);
            AbstractContainerMenu menu = openBench(h, player, CraftingContent.WORKBENCHES.get(Workbench.COPPER).get());
            pickaxe(menu, Items.COPPER_INGOT);
            for (int i = TimedCrafting.pendingTicks(player); i > 0; i--) TimedCrafting.tick(player);
            h.assertValueEqual(menu.getSlot(0).getItem().get(CraftQuality.QUALITY.get()), Material.COPPER.maxQuality, "copper quality cap");
            h.assertValueEqual(menu.getSlot(0).getItem().getMaxDamage(), 9600, "excellent copper pickaxe durability");
            h.succeed();
        });
        // EF-OUT-09 CA-1: each hybrid is efficient on its parents' blocks.
        MiteGameTests.add("hybrid_tools_efficient", 20, h -> {
            Map<ToolType, Block[]> parents = Map.of(
                    ToolType.BATTLE_AXE, new Block[]{Blocks.OAK_LOG},
                    ToolType.WAR_HAMMER, new Block[]{Blocks.STONE},
                    ToolType.MATTOCK, new Block[]{Blocks.DIRT, Blocks.HAY_BLOCK},
                    ToolType.SCYTHE, new Block[]{Blocks.HAY_BLOCK, Blocks.MELON});
            parents.forEach((type, blocks) -> {
                Tool tool = new ItemStack(tool(type, Material.IRON)).get(DataComponents.TOOL);
                for (Block b : blocks)
                    h.assertTrue(tool.getMiningSpeed(b.defaultBlockState()) > tool.defaultMiningSpeed(), type + " not efficient on " + b);
            });
            h.succeed();
        });
        // EF-OUT-10 CA-1: the same pickaxe reaches 0.75 farther on stone (efficient) than on dirt.
        MiteGameTests.add("reach_tool_efficient", 20, h -> {
            ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
            ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
            player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
            h.assertValueEqual(Reach.bonus(pickaxe, Blocks.STONE.defaultBlockState()) - Reach.bonus(pickaxe, Blocks.DIRT.defaultBlockState()), 0.75, "reach bonus difference");
            Vec3 at = h.absoluteVec(new Vec3(0.2, 1, 3.5)); // 3.85 blocks from the eye to the block
            player.setPos(at.x, at.y, at.z);
            BlockPos target = h.absolutePos(new BlockPos(4, 1, 3));
            h.setBlock(new BlockPos(4, 1, 3), Blocks.STONE);
            h.assertTrue(player.isWithinBlockInteractionRange(target, 0), "stone in pickaxe reach");
            h.setBlock(new BlockPos(4, 1, 3), Blocks.DIRT);
            h.assertFalse(player.isWithinBlockInteractionRange(target, 0), "dirt out of pickaxe reach");
            h.succeed();
        });
        // EF-OUT-11 CA-1: a sword parries and wears by its attack rate; the dagger does not parry.
        MiteGameTests.add("parry_weapons", 20, h -> {
            BlocksAttacks parry = new ItemStack(Items.IRON_SWORD).get(DataComponents.BLOCKS_ATTACKS);
            h.assertTrue(parry != null, "iron sword cannot parry");
            h.assertValueEqual(parry.itemDamage().apply(5), Formulas.attackWear(ToolType.SWORD), "parry wear");
            h.assertFalse(new ItemStack(tool(ToolType.DAGGER, Material.IRON)).has(DataComponents.BLOCKS_ATTACKS), "dagger parries");
            h.assertFalse(new ItemStack(tool(ToolType.HATCHET, Material.IRON)).has(DataComponents.BLOCKS_ATTACKS), "hatchet parries");
            h.succeed();
        });
        // EF-OUT-12 CA-1: a flint axe needs string or sinew.
        MiteGameTests.add("lashing_flint_axe", 20, h -> {
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            AbstractContainerMenu menu = openBench(h, player, Blocks.CRAFTING_TABLE);
            for (int slot : new int[]{1, 2, 4}) menu.getSlot(slot).set(new ItemStack(Items.FLINT));
            menu.getSlot(5).set(new ItemStack(Items.STICK));
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            h.assertValueEqual(TimedCrafting.pendingTicks(player), 0, "unlashed flint axe accepted");
            menu.getSlot(7).set(new ItemStack(Items.STRING));
            h.assertTrue(TimedCrafting.pendingTicks(player) > 0, "lashed flint axe refused");
            h.succeed();
        });
        // EF-CRA-08 CA-1: a crafter set to a copper pickaxe produces nothing; a metal-free recipe takes the MITE time at level 0.
        MiteGameTests.add("crafter_refuses_metal", 20, h -> {
            h.setBlock(POS, Blocks.CRAFTER);
            BlockPos abs = h.absolutePos(POS);
            CrafterBlockEntity crafter = (CrafterBlockEntity) h.getLevel().getBlockEntity(abs);
            for (int slot : new int[]{0, 1, 2}) crafter.setItem(slot, new ItemStack(Items.COPPER_INGOT));
            crafter.setItem(4, new ItemStack(Items.STICK));
            crafter.setItem(7, new ItemStack(Items.STICK));
            h.getLevel().getBlockState(abs).tick(h.getLevel(), abs, h.getLevel().getRandom());
            h.assertTrue(crafter.getItem(0).is(Items.COPPER_INGOT), "crafter consumed the copper");
            h.assertItemEntityNotPresent(Items.COPPER_PICKAXE, POS, 3);
            for (int slot = 0; slot < 9; slot++) crafter.setItem(slot, ItemStack.EMPTY);
            crafter.setItem(0, new ItemStack(Items.OAK_PLANKS));
            crafter.setItem(3, new ItemStack(Items.OAK_PLANKS));
            h.assertFalse(CrafterRules.refuse(h.getLevel(), abs), "sticks refused");
            h.assertValueEqual(CrafterRules.delay(h.getLevel(), abs, 4), 50, "crafter delay for 2 planks");
            h.succeed();
        });
    }

    private static Item tool(ToolType type, Material material) {
        return ToolStats.all().entrySet().stream().filter(e -> e.getValue().type() == type && e.getValue().material() == material)
                .findFirst().orElseThrow().getKey();
    }

    private static AbstractContainerMenu openBench(GameTestHelper h, ServerPlayer player, Block block) {
        h.setBlock(POS, block);
        ContainerLevelAccess access = ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS));
        AbstractContainerMenu menu = block instanceof WorkbenchBlock bench
                ? new WorkbenchBlock.Menu(1, player.getInventory(), access, bench)
                : new CraftingMenu(1, player.getInventory(), access);
        player.containerMenu = menu;
        TimedCrafting.hold(player, false);
        return menu;
    }

    /** XXX / .|. / .|. in a 3x3 grid (slots 1..9). */
    private static void pickaxe(AbstractContainerMenu menu, Item head) {
        for (int slot : new int[]{1, 2, 3}) menu.getSlot(slot).set(new ItemStack(head));
        menu.getSlot(5).set(new ItemStack(Items.STICK));
        menu.getSlot(8).set(new ItemStack(Items.STICK));
    }

}
