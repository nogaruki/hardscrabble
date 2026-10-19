package dev.mite.mixin;

import dev.mite.harvest.Reach;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MX-11 (docs/02 §5.2): client counterpart of MX-10. In 26.3 the crosshair pick moved from GameRenderer to
 * {@link LocalPlayer#raycastHitResult}; a block beyond its per-block reach is turned into a miss so the outline matches the server.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerReachMixin {
    @Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
    private void mite$reach(float a, Entity camera, CallbackInfoReturnable<HitResult> cir) {
        cir.setReturnValue(Reach.filterPick((LocalPlayer) (Object) this, cir.getReturnValue(), camera.getEyePosition(a)));
    }
}
