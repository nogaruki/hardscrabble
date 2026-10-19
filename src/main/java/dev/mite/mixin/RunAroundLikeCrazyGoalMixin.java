package dev.mite.mixin;

import dev.mite.farming.livestock.HorseTaming;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changelog l.296 (R156): a wild horse that throws off its rider refuses riders for a while. The mount event cannot tell a
 * throw from a voluntary dismount, hence the mixin on the throw itself.
 */
@Mixin(RunAroundLikeCrazyGoal.class)
abstract class RunAroundLikeCrazyGoalMixin {
    @Shadow @Final private AbstractHorse horse;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/equine/AbstractHorse;ejectPassengers()V"))
    private void mite$thrown(CallbackInfo ci) {
        HorseTaming.onRiderThrown(horse);
    }
}
