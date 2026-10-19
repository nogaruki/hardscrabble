package dev.mite.metallurgy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Smelting furnace with the vanilla menu; heat gating is shared with vanilla furnaces in {@link FurnaceHeat}. */
public class MiteFurnaceBlockEntity extends AbstractFurnaceBlockEntity {
    public MiteFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(Metallurgy.FURNACE_ENTITY.get(), pos, state, RecipeType.SMELTING);
    }

    boolean foodOnly() {
        return getBlockState().is(Metallurgy.CLAY_FURNACE.get());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) && (slot == 0 ? FurnaceHeat.accepts(this, stack) : slot != 1 || FurnaceHeat.acceptsFuel(this, stack));
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FurnaceMenu(containerId, inventory, this, this.dataAccess);
    }
}
