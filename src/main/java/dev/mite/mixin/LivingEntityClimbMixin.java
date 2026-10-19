package dev.mite.mixin;

import dev.mite.player.PlayerMovement;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Changelog l.47: the fixed climbing speed (0.2 per tick up a ladder or vine) follows the player's speed modifiers. */
@Mixin(LivingEntity.class)
abstract class LivingEntityClimbMixin {
    @ModifyConstant(method = "handleRelativeFrictionAndCalculateMovement", constant = @Constant(doubleValue = 0.2))
    private double mite$climbSpeed(double original) {
        return original * PlayerMovement.climbFactor((LivingEntity) (Object) this);
    }
}
