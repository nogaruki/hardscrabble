package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mite.player.PlayerMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Changelog l.602: for players, a fence collides with its visible shape (1 block high) instead of 1.5, so they can hop over it. */
@Mixin(CrossCollisionBlock.class)
abstract class CrossCollisionBlockMixin {
    @ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
    private VoxelShape mite$fenceHop(VoxelShape original, BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PlayerMovement.hopsFence((Block) (Object) this, context) ? state.getShape(level, pos, context) : original;
    }
}
