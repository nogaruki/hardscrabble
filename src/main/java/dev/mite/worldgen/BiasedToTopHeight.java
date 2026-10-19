package dev.mite.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.HeightProviderType;

/** Mirror of vanilla {@code biased_to_bottom}: most samples near the top of the range (EF-MON-02 "vers le haut"). */
public final class BiasedToTopHeight extends HeightProvider {
    static final MapCodec<BiasedToTopHeight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(h -> h.min),
            VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter(h -> h.max)).apply(i, BiasedToTopHeight::new));

    private final VerticalAnchor min;
    private final VerticalAnchor max;

    public BiasedToTopHeight(VerticalAnchor min, VerticalAnchor max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public int sample(RandomSource random, WorldGenerationContext context) {
        int lo = min.resolveY(context), hi = max.resolveY(context);
        if (hi <= lo) return lo;
        return hi - random.nextInt(random.nextInt(hi - lo + 1) + 1);
    }

    @Override
    public HeightProviderType<?> getType() {
        return WorldgenContent.BIASED_TO_TOP.get();
    }
}
