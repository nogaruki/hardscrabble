package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mite.metabolism.MetabolismService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EF-NUT-04: on a player, the Regeneration effect speeds up the MITE healing (which costs hunger) instead of healing for free.
 * LivingHealEvent cannot tell this heal from the others, hence the mixin.
 */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
abstract class RegenerationMobEffectMixin {
    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void mite$heal(LivingEntity mob, float amount, Operation<Void> original) {
        if (MetabolismService.effectHeals(mob)) original.call(mob, amount);
    }
}
