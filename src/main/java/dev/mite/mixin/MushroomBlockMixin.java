package dev.mite.mixin;

import dev.mite.farming.FarmingRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changelog l.614 (R127): red mushrooms do not spread under an open sky in the rain. No event covers mushroom spreading. */
@Mixin(MushroomBlock.class)
abstract class MushroomBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void mite$rain(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (FarmingRules.rainStopsMushroom(level, pos, state)) ci.cancel();
    }
}
