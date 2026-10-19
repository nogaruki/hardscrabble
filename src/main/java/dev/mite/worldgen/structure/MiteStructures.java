package dev.mite.worldgen.structure;

import com.mojang.datafixers.Products.P9;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.worldgen.village.VillageLock;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure placements of L05 (EF-MON-03). The vanilla structure sets are overridden by plain JSON in
 * {@code data/minecraft/worldgen/structure_set}: temples use {@code hardscrabble:gated_spread}, villages {@code hardscrabble:gated_spread} with
 * {@code village_lock} (EF-MON-05), strongholds {@code hardscrabble:scaled_rings}. Changelog l.718 (R121): witch huts, jungle and
 * desert temples and villages are generated further apart: {@code spacing} and {@code separation} doubled ({@link #SPREAD_FACTOR}).
 */
public final class MiteStructures {
    private MiteStructures() {}

    private static final DeferredRegister<MapCodec<? extends StructurePlacement>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MiteMod.MODID);

    static {
        PLACEMENTS.register("gated_spread", () -> GatedSpread.CODEC);
        PLACEMENTS.register("scaled_rings", () -> ScaledRings.CODEC);
    }

    public static void register(IEventBus modBus) {
        PLACEMENTS.register(modBus);
    }

    /** Changelog l.718: spacing of the hut, temple and village sets relative to vanilla [INFÉRÉ: x2], written in their JSON. */
    public static final int SPREAD_FACTOR = 2;

    /**
     * True when the centre of the chunk is at least {@code temple_min_distance} blocks from the origin. The origin stands in
     * for the world spawn: worldgen has no level to ask, and vanilla spawns within a few hundred blocks of it.
     */
    public static boolean farFromSpawn(int chunkX, int chunkZ) {
        long x = chunkX * 16L + 8, z = chunkZ * 16L + 8, min = MiteConfig.intOrDefault(MiteConfig.TEMPLE_MIN_DISTANCE);
        return x * x + z * z >= min * min;
    }

    /**
     * {@code random_spread} that never places within {@code temple_min_distance} of spawn. Same fields as random_spread, plus
     * {@code village_lock}: when true the spawn distance is ignored and the set places only once villages are unlocked
     * (EF-MON-05, {@link VillageLock}).
     */
    public static class GatedSpread extends RandomSpreadStructurePlacement {
        public static final MapCodec<GatedSpread> CODEC = RecordCodecBuilder.<GatedSpread>mapCodec(i -> {
                    var p = placementCodec(i);
                    var r = i.group(
                            Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                            Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                            RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType),
                            Codec.BOOL.optionalFieldOf("village_lock", false).forGetter(GatedSpread::villageLock));
                    return new P9<>(p.t1(), p.t2(), p.t3(), p.t4(), p.t5(), r.t1(), r.t2(), r.t3(), r.t4()).apply(i, GatedSpread::new);
                })
                .validate(p -> p.spacing() <= p.separation() ? DataResult.error(() -> "Spacing has to be larger than separation") : DataResult.success(p));

        private final boolean villageLock;

        @SuppressWarnings("deprecation") // the vanilla super constructor still takes ExclusionZone in 26.3
        public GatedSpread(Vec3i locateOffset, FrequencyReductionMethod reduction, float frequency, int salt, Optional<ExclusionZone> exclusion,
                int spacing, int separation, RandomSpreadType spreadType, boolean villageLock) {
            super(locateOffset, reduction, frequency, salt, exclusion, spacing, separation, spreadType);
            this.villageLock = villageLock;
        }

        public boolean villageLock() {
            return villageLock;
        }

        /**
         * The gate sits in the chunk restrictions, which {@code StructureCheck} also asks before {@code /locate} loads a chunk
         * to STRUCTURE_STARTS: a locked village chunk is never generated, so it keeps its village for later (dom-L-01).
         */
        @Override
        public boolean applyAdditionalChunkRestrictions(int sourceX, int sourceZ, long levelSeed) {
            return (villageLock ? VillageLock.open() : farFromSpawn(sourceX, sourceZ)) && super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"}) // the vanilla return type is invariant; the dispatch codec only needs the right instance
        public MapCodec<RandomSpreadStructurePlacement> codec() {
            return (MapCodec) CODEC;
        }
    }

    /**
     * {@code concentric_rings} whose {@code distance} is a 1.6.4-equivalent distance, multiplied by
     * {@code stronghold_distance_multiplier}. Vanilla reads {@link #distance()} once, when the level loads.
     */
    public static class ScaledRings extends ConcentricRingsStructurePlacement {
        public static final MapCodec<ScaledRings> CODEC = RecordCodecBuilder.mapCodec(i -> {
            var p = placementCodec(i);
            var r = i.group(
                    Codec.intRange(0, 1023).fieldOf("distance").forGetter(ScaledRings::baseDistance),
                    Codec.intRange(0, 1023).fieldOf("spread").forGetter(ConcentricRingsStructurePlacement::spread),
                    Codec.intRange(1, 4095).fieldOf("count").forGetter(ConcentricRingsStructurePlacement::count),
                    RegistryCodecs.holderSet(Registries.BIOME).fieldOf("preferred_biomes").forGetter(ConcentricRingsStructurePlacement::preferredBiomes));
            return new P9<>(p.t1(), p.t2(), p.t3(), p.t4(), p.t5(), r.t1(), r.t2(), r.t3(), r.t4()).apply(i, ScaledRings::new);
        });

        private final int baseDistance;

        @SuppressWarnings("deprecation") // the vanilla super constructor still takes ExclusionZone in 26.3
        public ScaledRings(Vec3i locateOffset, FrequencyReductionMethod reduction, float frequency, int salt, Optional<ExclusionZone> exclusion,
                int distance, int spread, int count, HolderSet<Biome> preferredBiomes) {
            super(locateOffset, reduction, frequency, salt, exclusion, distance, spread, count, preferredBiomes);
            this.baseDistance = distance;
        }

        public int baseDistance() {
            return baseDistance;
        }

        @Override
        public int distance() {
            return baseDistance * MiteConfig.intOrDefault(MiteConfig.STRONGHOLD_DISTANCE_MULTIPLIER);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"}) // the vanilla return type is invariant; the dispatch codec only needs the right instance
        public MapCodec<ConcentricRingsStructurePlacement> codec() {
            return (MapCodec) CODEC;
        }
    }
}
