package dev.mite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changelog l.79 (R184): the treasure room has a torch above each chest, so no mob spawns on the pressure plate and fires the
 * TNT trap. A wall torch in the air block over each of the four chest alcoves, on the alcove's back wall. In the piece's local
 * frame +x is east but +z is north (vanilla getWorldZ), hence the facing flip on the z axis.
 */
@Mixin(DesertPyramidPiece.class)
public abstract class DesertPyramidPieceMixin extends ScatteredFeaturePiece {
    private DesertPyramidPieceMixin(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
    }

    @Inject(method = "postProcess", at = @At("TAIL"))
    private void mite$treasureTorches(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
            ChunkPos chunkPos, BlockPos referencePos, CallbackInfo ci) {
        if (this.heightPosition < 0) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Direction facing = d.getAxis() == Direction.Axis.Z ? d : d.getOpposite();
            this.placeBlock(level, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing), 10 + 2 * d.getStepX(), -10, 10 + 2 * d.getStepZ(), chunkBB);
        }
    }
}
