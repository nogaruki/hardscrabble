package dev.mite.mixin;

import dev.mite.worldgen.DirtGravity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MX-09 (docs/02 §5.2): dirt is a plain {@code Block} and NeoForge has no "entity walks on block" event. Filtered by the tag
 * {@code hardscrabble:gravity_affected} in {@link DirtGravity} (EF-MON-04).
 */
@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "stepOn", at = @At("HEAD"))
    private void mite$stepOn(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        DirtGravity.onStep(level, pos, entity);
    }
}
