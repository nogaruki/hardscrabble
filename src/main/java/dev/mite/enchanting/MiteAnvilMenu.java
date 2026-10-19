package dev.mite.enchanting;

import dev.mite.core.Material;
import dev.mite.combat.CombatContent;
import dev.mite.crafting.TimedCrafting;
import dev.mite.harvest.ToolStats;
import dev.mite.material.MaterialItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * MITE anvil (EF-ENC-01, ET-H-01): left = damaged item, right = nuggets of its metal. Full repair, no XP, no combining, no renaming.
 * A disenchanting bottle on the right strips the enchantments of the left item instead (EF-ENC-06).
 * Opened by every metal anvil and, through {@link EnchantingContent#onRightClickBlock}, by the vanilla (iron) anvil.
 * The result is computed on the server only and synced; the nugget cost is synced in a data slot.
 */
public class MiteAnvilMenu extends ItemCombinerMenu {
    /** Wear chance per repair on an iron anvil (vanilla value), scaled by iron durability / anvil durability [NON VÉRIFIÉ, Q04]. */
    static final float IRON_WEAR_CHANCE = 0.12f;

    private final @Nullable Material anvil;
    private final DataSlot cost = DataSlot.standalone();

    /** Client constructor. */
    public MiteAnvilMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL, null);
    }

    public MiteAnvilMenu(int id, Inventory inventory, ContainerLevelAccess access, @Nullable Material anvil) {
        super(EnchantingContent.ANVIL_MENU.get(), id, inventory, access, ItemCombinerMenuSlotDefinition.create()
                .withSlot(0, 27, 47, s -> true).withSlot(1, 76, 47, s -> true).withResultSlot(2, 134, 47).build());
        this.anvil = anvil;
        addDataSlot(cost);
    }

    public static MenuProvider provider(Material anvil, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new MiteAnvilMenu(id, inventory, ContainerLevelAccess.create(level, pos), anvil),
                Component.translatable("container.hardscrabble.anvil"));
    }

    /** Nuggets the current repair consumes, 0 when there is none. */
    public int getCost() {
        return cost.get();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(BlockTags.ANVIL) || state.getBlock() instanceof MiteAnvilBlock;
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return hasItem && cost.get() > 0;
    }

    @Override
    public void createResult() {
        if (anvil == null) return; // client: the server syncs the result slot and the cost
        ItemStack item = inputSlots.getItem(0);
        int n = repairCost(item, inputSlots.getItem(1), anvil);
        cost.set(n);
        if (tooHard(item, anvil) && player instanceof ServerPlayer sp) TimedCrafting.refuse(sp, "hardscrabble.anvil.too_hard"); // EF-PRG-03
        ItemStack result = ItemStack.EMPTY;
        if (n > 0) {
            result = item.copy();
            result.setDamageValue(0);
        } else if (inputSlots.getItem(1).is(EnchantingContent.DISENCHANTING_BOTTLE) && EnchantmentHelper.hasAnyEnchantments(item)) {
            n = 1; // EF-ENC-06: one disenchanting bottle removes every enchantment
            cost.set(n);
            result = item.copy();
            EnchantmentHelper.setEnchantments(result, ItemEnchantments.EMPTY);
            if (result.is(Items.ENCHANTED_BOOK)) result = result.transmuteCopy(Items.BOOK); // a blank enchanted book is invalid (vanilla grindstone)
        }
        resultSlots.setItem(0, result);
        broadcastChanges();
    }

    @Override
    protected void onTake(Player player, ItemStack carried) {
        int n = cost.get();
        ItemStack nuggets = inputSlots.getItem(1);
        nuggets.shrink(n);
        inputSlots.setItem(1, nuggets);
        inputSlots.setItem(0, ItemStack.EMPTY);
        if (!player.hasInfiniteMaterials() && anvil != null) access.execute((level, pos) -> wear(level, pos, anvil, player.getRandom()));
    }

    /**
     * Nuggets needed to fully repair {@code item} with {@code nuggets} on an anvil of metal {@code anvil}: 2 x ingots in the item's recipe
     * (R125 doubling, EF-ENC-01 [À CONFIRMER Q04]). 0 when the item is undamaged, not metal, harder than the anvil, or nuggets are missing.
     */
    public static int repairCost(ItemStack item, ItemStack nuggets, Material anvil) {
        if (!item.isDamaged()) return 0;
        return repairInfo(item).filter(r -> hardness(r.material()) <= hardness(anvil) && nuggets.is(MaterialItems.nugget(r.material())))
                .map(r -> 2 * r.ingots()).filter(c -> nuggets.getCount() >= c).orElse(0);
    }

    /** A damaged metal item harder than the anvil: refused with a message (EF-PRG-03). */
    public static boolean tooHard(ItemStack item, Material anvil) {
        return item.isDamaged() && repairInfo(item).filter(r -> hardness(r.material()) > hardness(anvil) && hardness(r.material()) != Integer.MAX_VALUE).isPresent();
    }

    record RepairInfo(Material material, int ingots) {}

    /**
     * Tools from {@link ToolStats}; armor (X11: every MITE piece, the new metals and chainmails included) from
     * {@link CombatContent#ARMORS}: plate 5/8/7/4 ingots, chainmail 4 nuggets per chain (rounded up to ingots). Leather is not
     * metal: it is repaired with sinew or string in a crafting grid ({@code LeatherRepairRecipe}).
     */
    static Optional<RepairInfo> repairInfo(ItemStack item) {
        var tool = ToolStats.of(item);
        if (tool.isPresent()) return Optional.of(new RepairInfo(tool.get().material(), tool.get().type().components));
        return CombatContent.ARMORS.stream().filter(a -> a.item().get() == item.getItem() && a.material().isMetal()).findFirst()
                .map(a -> new RepairInfo(a.material(), a.chain() ? (a.components() * 4 + 8) / 9 : a.components()));
    }

    /** EF-ENC-01 order: gold < copper = silver < iron < ancient metal < mithril < adamantium. Non-metals cannot be repaired. */
    static int hardness(Material m) {
        return switch (m) {
            case GOLD -> 0;
            case COPPER, SILVER -> 1;
            case IRON, RUSTED_IRON -> 2;
            case ANCIENT_METAL -> 3;
            case MITHRIL -> 4;
            case ADAMANTIUM -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Each repair may wear the anvil one stage; past the last stage it is destroyed (vanilla level events). */
    static void wear(Level level, BlockPos pos, Material anvil, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (random.nextFloat() >= IRON_WEAR_CHANCE * Material.IRON.durability / anvil.durability) {
            level.levelEvent(1030, pos, 0);
            return;
        }
        BlockState next = state.getBlock() instanceof MiteAnvilBlock
                ? state.getValue(MiteAnvilBlock.DAMAGE) < 2 ? state.cycle(MiteAnvilBlock.DAMAGE) : null
                : AnvilBlock.damage(state);
        if (next == null) {
            level.removeBlock(pos, false);
            level.levelEvent(1029, pos, 0);
        } else {
            level.setBlock(pos, next, 2);
            level.levelEvent(1030, pos, 0);
        }
    }
}
