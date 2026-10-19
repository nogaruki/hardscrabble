package dev.mite.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.core.Formulas;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;

/**
 * {@code hardscrabble:depth_scaled_ore}: a vanilla ore vein whose size grows with depth inside [min_y, max_y],
 * x1.5 at the bottom and x0.5 at the top (EF-MON-02, ET-F-04).
 */
public record DepthScaledOreFeature(List<BlockReplacement> targets, int size, int minY, int maxY) implements Feature {
    static final MapCodec<DepthScaledOreFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.list(BlockReplacement.CODEC).fieldOf("targets").forGetter(DepthScaledOreFeature::targets),
            Codec.intRange(1, 64).fieldOf("size").forGetter(DepthScaledOreFeature::size),
            Codec.INT.fieldOf("min_y").forGetter(DepthScaledOreFeature::minY),
            Codec.INT.fieldOf("max_y").forGetter(DepthScaledOreFeature::maxY)).apply(i, DepthScaledOreFeature::new));

    @Override
    public MapCodec<DepthScaledOreFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        return new OreFeature(targets, Formulas.depthScaledVeinSize(size, origin.getY(), minY, maxY)).place(level, generator, random, origin);
    }
}
