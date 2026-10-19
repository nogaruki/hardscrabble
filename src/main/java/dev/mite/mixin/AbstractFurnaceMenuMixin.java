package dev.mite.mixin;

import dev.mite.metallurgy.FurnaceHeat;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Syncs one extra data slot (effective heat, required heat, refusal) to the furnace screen (EF-UI-04, EF-MET-05 CA-3).
 * The vanilla {@code ContainerData} has a fixed size of 4 and no API adds a slot to a vanilla menu on both sides.
 */
@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceMenuMixin extends RecipeBookMenu implements FurnaceHeat.Status {
    @Unique
    private DataSlot mite$heat;

    private AbstractFurnaceMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/inventory/RecipeBookType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
            at = @At("TAIL"))
    private void mite$addHeatSlot(MenuType<?> menuType, ResourceKey<?> inputs, RecipeBookType bookType, int containerId, Inventory inventory,
            Container container, ContainerData data, CallbackInfo ci) {
        this.mite$heat = addDataSlot(FurnaceHeat.statusSlot(container));
    }

    @Override
    public int mite$heatStatus() {
        return this.mite$heat.get();
    }
}
