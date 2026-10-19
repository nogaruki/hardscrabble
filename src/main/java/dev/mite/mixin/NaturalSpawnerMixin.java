package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mite.entity.ai.SpawnSurfaces;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changelog l.703 (R122): a monster may spawn in shallow water; the vanilla room test refuses any fluid. No NeoForge hook. */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @ModifyReturnValue(method = "isValidEmptySpawnBlock", at = @At("RETURN"))
    private static boolean mite$shallowWater(boolean valid, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid, EntityType<?> type) {
        return valid || SpawnSurfaces.shallowWater(level, pos, state, fluid, type);
    }
}
