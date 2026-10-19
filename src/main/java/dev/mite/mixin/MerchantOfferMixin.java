package dev.mite.mixin;

import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Audit 2 X1 (RE-04): trades pop no XP orb, villagers and wandering traders alike (trade XP came with 1.8). No NeoForge event. */
@Mixin(MerchantOffer.class)
abstract class MerchantOfferMixin {
    @Inject(method = "shouldRewardExp", at = @At("HEAD"), cancellable = true)
    private void mite$noTradeXp(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
