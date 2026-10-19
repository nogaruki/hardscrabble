package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.mite.environment.Environment;
import dev.mite.metallurgy.FurnaceHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Heat gate of every furnace, smoker and blast furnace (ET-F-03, EF-MET-05). No NeoForge furnace event exists in 26.3:
 * the recipe check of {@code serverTick} is the only place covering vanilla and mod furnaces alike.
 * Rules live in {@link FurnaceHeat}; dousing (EF-MET-10) in {@link Environment#isDoused}.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements FurnaceHeat.Lit {
    @Shadow
    private int litTimeRemaining;

    @Override
    public boolean mite$isLit() {
        return litTimeRemaining > 0;
    }

    /** A doused furnace burns its last tick now, so the vanilla code turns it off; the heat gate keeps it from relighting. */
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void mite$douse(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        AbstractFurnaceBlockEntityMixin self = (AbstractFurnaceBlockEntityMixin) (Object) furnace;
        if (self.litTimeRemaining > 1 && Environment.isDoused(level, pos, state)) self.litTimeRemaining = 1;
    }

    /** A recipe neither progresses nor lights a new fuel while the effective heat is below the required heat. */
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;canBurn(Lnet/minecraft/core/NonNullList;ILnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean mite$heatGate(boolean canBurn, ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        return canBurn && FurnaceHeat.canSmelt(furnace) && !Environment.isDoused(level, pos, state);
    }

    /** Heat-dependent outputs (sand -> sandstone at heat 1, sandstone -> glass from heat 2) and 4 clay balls -> 4 bricks. */
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack mite$heatedResult(ItemStack result, ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        return result.isEmpty() ? result : FurnaceHeat.heatedResult(furnace, result);
    }

    /** Remembers the heat of the fuel being lit, since the item is consumed. */
    @Inject(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;consumeFuel(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void mite$recordFuelHeat(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        FurnaceHeat.onIgnite(furnace);
    }

    /** Recipes eating several input items per smelt (4 sand -> glass). */
    @Inject(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void mite$multiInput(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        FurnaceHeat.onSmelt(furnace);
    }
}
