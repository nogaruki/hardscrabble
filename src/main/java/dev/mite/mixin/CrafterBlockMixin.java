package dev.mite.mixin;

import dev.mite.crafting.CrafterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MX-04 (docs/02 §5.2): the crafter only runs metal-free recipes, after the MITE craft time at level 0 (EF-CRA-08).
 * 26.3 has no crafter event: the trigger delay (4 ticks) and the dispense are only reachable here.
 */
@Mixin(CrafterBlock.class)
abstract class CrafterBlockMixin {
    @Redirect(method = {"neighborChanged", "setPlacedBy"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;I)V"))
    private void mite$craftTime(Level level, BlockPos pos, Block block, int delay) {
        level.scheduleTick(pos, block, CrafterRules.delay(level, pos, delay));
    }

    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void mite$metalFree(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (CrafterRules.refuse(level, pos)) ci.cancel();
    }
}
