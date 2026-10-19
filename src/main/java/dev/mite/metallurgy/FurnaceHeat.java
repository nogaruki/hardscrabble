package dev.mite.metallurgy;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * Heat rules shared by every furnace, vanilla ones included (EF-MET-05..07, ET-F-03).
 * Burn time stays in the vanilla {@code cooking_fuel} component; heat levels live in three data maps.
 * Called from {@code AbstractFurnaceBlockEntityMixin} and {@code AbstractFurnaceMenuMixin}.
 */
public final class FurnaceHeat {
    private FurnaceHeat() {}

    private static final Codec<Integer> HEAT = ExtraCodecs.intRange(1, 4);

    /** Heat of a fuel (EF-MET-06). Missing = 1. */
    public static final DataMapType<Item, Integer> FUEL_HEAT = DataMapType.builder(MiteMod.id("fuel_heat"), Registries.ITEM, HEAT).synced(HEAT, false).build();
    /** Heat a recipe input needs (EF-MET-07). Missing = 1. */
    public static final DataMapType<Item, Integer> SMELT_HEAT = DataMapType.builder(MiteMod.id("smelt_heat"), Registries.ITEM, HEAT).synced(HEAT, false).build();
    /** Maximum heat of a furnace block (EF-MET-05). Missing = 2, like the vanilla furnace. */
    public static final DataMapType<Block, Integer> FURNACE_HEAT = DataMapType.builder(MiteMod.id("furnace_heat"), Registries.BLOCK, HEAT).synced(HEAT, false).build();

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MiteMod.MODID);
    /** Heat of the fuel currently burning, recorded when it is lit (the item itself is consumed). */
    static final Supplier<AttachmentType<Integer>> BURNING_HEAT = ATTACHMENTS.register("burning_heat",
            () -> AttachmentType.builder(() -> 0).serialize(dev.mite.attach.Migrations.versioned("burning_heat", Codec.INT.fieldOf("heat"))).build());

    public static int fuelHeat(ItemStack fuel) {
        if (fuel.isEmpty()) return 0;
        Integer heat = fuel.getData(FUEL_HEAT);
        return heat == null ? 1 : heat;
    }

    public static int requiredHeat(ItemStack input) {
        if (input.isEmpty()) return 0;
        Integer heat = input.getData(SMELT_HEAT);
        return heat == null ? 1 : heat;
    }

    public static int maxHeat(BlockState furnace) {
        Integer heat = furnace.getData(FURNACE_HEAT);
        return heat == null ? 2 : heat;
    }

    /** Implemented by AbstractFurnaceBlockEntity through AbstractFurnaceBlockEntityMixin: fuel still burning this tick. */
    public interface Lit {
        boolean mite$isLit();
    }

    /**
     * min(fuel heat, furnace max heat); the fuel is the burning one when lit, otherwise the one waiting in the slot (0 if refused).
     * Lit is the block entity's burn time, not the LIT block state, which lags one tick behind when a fuel runs out.
     */
    public static int effectiveHeat(AbstractFurnaceBlockEntity furnace) {
        ItemStack next = furnace.getItem(1);
        int fuel = ((Lit) furnace).mite$isLit() ? furnace.getData(BURNING_HEAT) : acceptsFuel(furnace, next) ? fuelHeat(next) : 0;
        return Formulas.effectiveHeat(fuel, maxHeat(furnace.getBlockState()));
    }

    private static boolean foodOnly(AbstractFurnaceBlockEntity furnace) {
        return furnace instanceof MiteFurnaceBlockEntity f && f.foodOnly();
    }

    /** The clay furnace only cooks food: blocks and ores are refused (EF-MET-05 CA-2). */
    public static boolean accepts(AbstractFurnaceBlockEntity furnace, ItemStack input) {
        return !foodOnly(furnace) || input.has(DataComponents.FOOD);
    }

    /** The clay furnace burns small fuels only: no block, nothing hotter than heat 1 (EF-MET-05 [INFÉRÉ: "petits combustibles"]). */
    public static boolean acceptsFuel(AbstractFurnaceBlockEntity furnace, ItemStack fuel) {
        return !foodOnly(furnace) || !(fuel.getItem() instanceof BlockItem) && fuelHeat(fuel) <= 1;
    }

    /**
     * Input items one smelt consumes: 4 sand per glass block (changelog R121, l.709) or sandstone (l.760, R119); up to 4 clay
     * balls at once, each giving a brick (l.724, R120), fewer when the output slot has less room (vanilla would not smelt at all).
     */
    public static int inputCount(AbstractFurnaceBlockEntity furnace) {
        ItemStack input = furnace.getItem(0), output = furnace.getItem(2);
        if (input.is(ItemTags.SMELTS_TO_GLASS)) return 4;
        if (!input.is(Items.CLAY_BALL)) return 1;
        int room = output.isEmpty() ? 4 : output.getMaxStackSize() - output.getCount();
        return Math.clamp(Math.min(input.getCount(), room), 1, 4);
    }

    /**
     * Mixin hook: the item one smelt of {@code input} gives at the furnace's effective heat (vanilla recipes have one output). Sand
     * at heat 1 bakes into sandstone (l.760), glass from heat 2; sandstone keeps the smooth sandstone recipe at heat 1 and melts into
     * glass from heat 2 (l.138, R177) [INFÉRÉ: heat 1 output kept]. Clay balls give one brick each (l.724).
     */
    public static ItemStack heatedResult(AbstractFurnaceBlockEntity furnace, ItemStack result) {
        ItemStack input = furnace.getItem(0);
        boolean hot = effectiveHeat(furnace) >= 2;
        if (!hot && input.is(Items.SAND)) return new ItemStack(Items.SANDSTONE);
        if (!hot && input.is(Items.RED_SAND)) return new ItemStack(Items.RED_SANDSTONE);
        if (hot && (input.is(Items.SANDSTONE) || input.is(Items.RED_SANDSTONE))) return new ItemStack(Items.GLASS);
        if (input.is(Items.CLAY_BALL)) return result.copyWithCount(result.getCount() * inputCount(furnace));
        return result;
    }

    /** Mixin hook: may the current recipe progress (and the next fuel be lit)? */
    public static boolean canSmelt(AbstractFurnaceBlockEntity furnace) {
        ItemStack input = furnace.getItem(0);
        return accepts(furnace, input) && input.getCount() >= inputCount(furnace) && effectiveHeat(furnace) >= requiredHeat(input);
    }

    /** Mixin hook: called just before a new fuel item is consumed. */
    public static void onIgnite(AbstractFurnaceBlockEntity furnace) {
        furnace.setData(BURNING_HEAT, fuelHeat(furnace.getItem(1)));
    }

    /** Mixin hook: called just before vanilla shrinks the input by one at the end of a smelt. */
    public static void onSmelt(AbstractFurnaceBlockEntity furnace) {
        furnace.getItem(0).shrink(inputCount(furnace) - 1);
    }

    // ---- Furnace screen (EF-UI-04): one synced data slot = effective | required << 4 | refused << 8 | fuel refused << 9 ----

    /** Implemented by AbstractFurnaceMenu through AbstractFurnaceMenuMixin. */
    public interface Status {
        int mite$heatStatus();
    }

    public static DataSlot statusSlot(Container container) {
        if (!(container instanceof AbstractFurnaceBlockEntity furnace)) return DataSlot.standalone();
        return new DataSlot() {
            @Override
            public int get() {
                ItemStack input = furnace.getItem(0), fuel = furnace.getItem(1);
                return effectiveHeat(furnace) | requiredHeat(input) << 4 | (accepts(furnace, input) ? 0 : 1 << 8)
                        | (fuel.isEmpty() || acceptsFuel(furnace, fuel) ? 0 : 1 << 9);
            }

            @Override
            public void set(int value) {}
        };
    }
}
