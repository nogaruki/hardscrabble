package dev.mite.mixin;

import dev.mite.entity.ai.SilverfishRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.InfestedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changelog l.377 (R147): a silverfish bursting out of an infested block may start a cave-in. No event marks that emergence. */
@Mixin(InfestedBlock.class)
public abstract class InfestedBlockMixin {
    @Inject(method = "spawnInfestation", at = @At("TAIL"))
    private void mite$caveIn(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        SilverfishRules.caveIn(level, pos, level.getRandom());
    }
}
