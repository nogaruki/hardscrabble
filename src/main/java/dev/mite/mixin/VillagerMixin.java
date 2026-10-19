package dev.mite.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Audit 2 X1 (RE-04, PD-06): trading is 1.6.4-like, a used-up trade stays locked. The 1.14 restock at the job site (twice a
 * day, {@code WorkAtPoi} is the only caller) never happens; new trades only come with the villager's level-ups. The trading
 * screen is told the villager does not restock. No NeoForge event covers it.
 */
@Mixin(Villager.class)
abstract class VillagerMixin {
    @Inject(method = "shouldRestock", at = @At("HEAD"), cancellable = true)
    private void mite$noRestock(ServerLevel level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "canRestock", at = @At("HEAD"), cancellable = true)
    private void mite$canRestock(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
