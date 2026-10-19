package dev.mite.mixin;

import dev.mite.crafting.TimedCrafting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Timed and restricted crafting (ET-E-01..04, replaces MX-03). {@code slotChangedCraftingGrid} is the single place where both
 * the 2x2 inventory grid and every 3x3 crafting menu compute their result; no NeoForge event exposes it, and the inventory
 * menu cannot be replaced. One TAIL injection covers both grids, the recipe book and shift-click without touching slots.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void mite$timedCrafting(AbstractContainerMenu menu, ServerLevel level, Player player, CraftingContainer container,
            ResultContainer resultSlots, RecipeHolder<CraftingRecipe> recipeHint, CallbackInfo ci) {
        TimedCrafting.onResultChanged(menu, (ServerPlayer) player, container, resultSlots);
    }
}
