package dev.mite.mixin;

import dev.mite.worldgen.structure.StructureLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changelog l.631-632 and l.817: witch huts hold a chest ({@link StructureLoot#SWAMP_HUT}), against the back wall next to the
 * crafting table [INFÉRÉ: position]. The 26.3 hut is built in code, so no structure processor can add it.
 */
@Mixin(SwampHutPiece.class)
public abstract class SwampHutPieceMixin extends ScatteredFeaturePiece {
    private SwampHutPieceMixin(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
    }

    @Inject(method = "postProcess", at = @At("TAIL"))
    private void mite$hutChest(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
            ChunkPos chunkPos, BlockPos referencePos, CallbackInfo ci) {
        if (this.heightPosition >= 0) this.createChest(level, chunkBB, random, 2, 2, 6, StructureLoot.SWAMP_HUT);
    }
}
