package dev.mite.metabolism;

import static net.minecraft.world.level.biome.Biomes.*;

import dev.mite.MiteMod;
import dev.mite.core.Formulas.Climate;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * EF-NUT-03 biome classes, as biome tags so packs can reclassify. Every vanilla 26.3 biome sits in exactly one set
 * (GameTest metabolism_biomes_classified); biomes from other mods are temperate unless tagged. Modern biomes are classed by analogy [HORS-MITE].
 */
public final class BiomeClimate {
    private BiomeClimate() {}

    public static final TagKey<Biome> FROZEN = TagKey.create(Registries.BIOME, MiteMod.id("frozen"));
    public static final TagKey<Biome> HOT_WATER = TagKey.create(Registries.BIOME, MiteMod.id("hot_water"));

    /** Snowy and cold biomes, taigas and windswept hills (vanilla temperature 0.3 or less). */
    static final Set<ResourceKey<Biome>> FROZEN_BIOMES = Set.of(SNOWY_PLAINS, ICE_SPIKES, SNOWY_TAIGA, SNOWY_BEACH, SNOWY_SLOPES, GROVE, FROZEN_PEAKS,
            JAGGED_PEAKS, FROZEN_RIVER, FROZEN_OCEAN, DEEP_FROZEN_OCEAN, COLD_OCEAN, DEEP_COLD_OCEAN, TAIGA, OLD_GROWTH_PINE_TAIGA,
            OLD_GROWTH_SPRUCE_TAIGA, WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS, WINDSWEPT_FOREST);
    /** Deserts, badlands and savannas (EF-NUT-03), plus the Nether where water evaporates. */
    static final Set<ResourceKey<Biome>> HOT_BIOMES = Set.of(DESERT, BADLANDS, ERODED_BADLANDS, WOODED_BADLANDS, SAVANNA, SAVANNA_PLATEAU,
            WINDSWEPT_SAVANNA, NETHER_WASTES, WARPED_FOREST, CRIMSON_FOREST, SOUL_SAND_VALLEY, BASALT_DELTAS);
    /** Explicitly temperate (EF-NUT-03 CA-2). */
    static final Set<ResourceKey<Biome>> TEMPERATE_BIOMES = Set.of(THE_VOID, PLAINS, SUNFLOWER_PLAINS, SWAMP, MANGROVE_SWAMP, FOREST, FLOWER_FOREST,
            BIRCH_FOREST, DAPPLED_FOREST, DARK_FOREST, PALE_GARDEN, OLD_GROWTH_BIRCH_FOREST, JUNGLE, SPARSE_JUNGLE, BAMBOO_JUNGLE, MEADOW,
            CHERRY_GROVE, STONY_PEAKS, RIVER, BEACH, STONY_SHORE, WARM_OCEAN, LUKEWARM_OCEAN, DEEP_LUKEWARM_OCEAN, OCEAN, DEEP_OCEAN,
            MUSHROOM_FIELDS, DRIPSTONE_CAVES, LUSH_CAVES, DEEP_DARK, SULFUR_CAVES, THE_END, END_HIGHLANDS, END_MIDLANDS, SMALL_END_ISLANDS,
            END_BARRENS);

    public static Climate of(Holder<Biome> biome) {
        return biome.is(FROZEN) ? Climate.FROZEN : biome.is(HOT_WATER) ? Climate.HOT : Climate.TEMPERATE;
    }

    /** Called by the shared biome tags provider. */
    public static void biomeTags(Function<TagKey<Biome>, TagAppender<Biome>> tag) {
        tag.apply(FROZEN).addAll(FROZEN_BIOMES.stream().sorted());
        tag.apply(HOT_WATER).addAll(HOT_BIOMES.stream().sorted());
    }
}
