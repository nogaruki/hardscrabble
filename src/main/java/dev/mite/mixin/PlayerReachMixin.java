package dev.mite.mixin;

import dev.mite.harvest.Reach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MX-10 (docs/02 §5.2): block reach depends on the held tool and the target block (EF-OUT-10). The
 * {@code block_interaction_range} attribute cannot depend on the block, and every server block check goes through this method.
 */
@Mixin(Player.class)
abstract class PlayerReachMixin {
    @Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void mite$reach(BlockPos pos, double buffer, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Reach.withinRange((Player) (Object) this, pos, buffer));
    }
}
