package dev.mite.mixin;

import dev.mite.farming.SoilRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changelog l.634 (R127): snowfall destroys crops and stems. No event covers the precipitation tick, and a ripe crop gets no
 * growth event any more, so the column vanilla snows on is handed to {@link SoilRules#snowfall}.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSnowMixin {
    @Inject(method = "tickPrecipitation", at = @At("TAIL"))
    private void mite$snowKillsCrops(BlockPos pos, CallbackInfo ci) {
        SoilRules.snowfall((ServerLevel) (Object) this, pos);
    }
}
