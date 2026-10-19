package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mite.entity.ai.SpawnSurfaces;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Changelog l.696 (R123) / l.703 (R122): monsters spawn on bottom slabs and stairs, lily pads and thin snow. The floor test is a
 * per-block property read by the spawn placement and Mob#checkMobSpawnRules alike; NeoForge has no hook for it.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @ModifyReturnValue(method = "isValidSpawn", at = @At("RETURN"))
    private boolean mite$extraFloor(boolean valid, BlockGetter level, BlockPos pos, EntityType<?> type) {
        return valid || SpawnSurfaces.extraFloor((BlockState) (Object) this, level, pos, type);
    }
}
