package dev.mite.dimension;

import static dev.mite.dimension.UnderworldContent.MIN_Y;
import static dev.mite.dimension.UnderworldContent.HEIGHT;

import dev.mite.MiteMod;
import dev.mite.worldgen.WorldgenData;
import dev.mite.worldgen.WorldgenData.Placement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.material.VanillaMaterialConditions;
import net.minecraft.data.worldgen.material.VanillaMaterialRules;
import net.minecraft.data.worldgen.DimensionTypes;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.OffsetPlacement;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Underworld datagen (EF-DIM-01, EF-DIM-03): dimension type, terrain, biome, ore placements, Mantle model; Overworld type override. */
public final class UnderworldData {
    private UnderworldData() {}

    private static final int GRAY_FOG = 0x4A4A4A;
    /** M8 (1.6.4 rule, DECISIONS.md): monsters spawn up to block light 7 (vanilla 26.3: 0), Overworld and Underworld. */
    public static final int MONSTER_BLOCK_LIGHT_LIMIT = 7;

    public static void dimensionTypes(BootstrapContext<DimensionType> c) {
        // Fixed celestial angle, no sky light, ceiling, x1 coordinates (straight below the Overworld), dark like caves.
        c.register(UnderworldContent.TYPE, new DimensionType(true, false, true, false, 1.0, MIN_Y, HEIGHT, HEIGHT,
                c.lookup(Registries.BLOCK).getOrThrow(BlockTags.INFINIBURN_OVERWORLD), 0.0F,
                new DimensionType.MonsterSettings(UniformInt.of(0, 7), MONSTER_BLOCK_LIGHT_LIMIT), DimensionType.Skybox.NONE, CardinalLighting.Type.DEFAULT,
                EnvironmentAttributeMap.builder()
                        .set(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(GRAY_FOG))
                        .set(EnvironmentAttributes.SKY_LIGHT_FACTOR, 0.0F)
                        .set(EnvironmentAttributes.FOG_END_DISTANCE, 128.0F)
                        .build(),
                HolderSet.direct(), Optional.empty()));
    }

    public static void materialRules(BootstrapContext<MaterialRule> c) {
        HolderGetter<MaterialRule> rules = c.lookup(Registries.MATERIAL_RULE);
        MaterialCondition onFloor = MaterialRules.getCondition(c.lookup(Registries.MATERIAL_CONDITION), VanillaMaterialConditions.ON_FLOOR);
        c.register(UnderworldContent.MATERIAL_RULE, MaterialRules.sequence(
                MaterialRules.ifTrue(MaterialRules.verticalGradient("hardscrabble:mantle", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(3)),
                        MaterialRules.state(UnderworldContent.MANTLE.get().defaultBlockState())),
                MaterialRules.getRule(rules, VanillaMaterialRules.BEDROCK_FLOOR),
                MaterialRules.getRule(rules, VanillaMaterialRules.BEDROCK_ROOF),
                // Bedrock patches on the floor, then large irregular plates over the whole bottom half (changelog l.177, dom-M-03).
                MaterialRules.ifTrue(MaterialRules.not(MaterialRules.yBlockCheck(VerticalAnchor.aboveBottom(10), 0)),
                        MaterialRules.ifTrue(MaterialRules.noiseCondition2d(Noises.PATCH, 0.0), BEDROCK)),
                MaterialRules.sequence(PLATES.stream().map(y -> MaterialRules.ifTrue(MaterialRules.yBlockCheck(VerticalAnchor.aboveBottom(y), 0),
                        MaterialRules.ifTrue(MaterialRules.not(MaterialRules.yBlockCheck(VerticalAnchor.aboveBottom(y + PLATE_THICKNESS), 0)),
                                MaterialRules.ifTrue(MaterialRules.noiseCondition3d(Noises.PATCH, PLATE_NOISE), BEDROCK)))).toList()),
                // Mycelium between 24 and 96 above the bottom (R196 "y = 24 to 96 + offset"). Stone everywhere else (no deepslate, dom-M-06).
                MaterialRules.ifTrue(onFloor, MaterialRules.ifTrue(MaterialRules.yBlockCheck(VerticalAnchor.aboveBottom(MYCELIUM_MIN), 0),
                        MaterialRules.ifTrue(MaterialRules.not(MaterialRules.yBlockCheck(VerticalAnchor.aboveBottom(MYCELIUM_MAX + 1), 0)),
                                MaterialRules.state(Blocks.MYCELIUM.defaultBlockState()))))));
    }

    private static final MaterialRule BEDROCK = MaterialRules.state(Blocks.BEDROCK.defaultBlockState());
    /** Heights above the bottom of the bedrock plates: one every 16 blocks up to the middle of the dimension [INFÉRÉ: shape]. */
    public static final List<Integer> PLATES = Stream.iterate(16, y -> y < HEIGHT / 2, y -> y + 16).toList();
    static final int PLATE_THICKNESS = 2;
    static final double PLATE_NOISE = 0.25;
    /** Mycelium and mushrooms (R196 "y = 24 to 96"), blocks above the bottom. */
    public static final int MYCELIUM_MIN = 24, MYCELIUM_MAX = 96;

    public static void noiseSettings(BootstrapContext<NoiseGeneratorSettings> c) {
        // Nether-like 3D cave noise stretched over the whole height, sloped to solid at the bottom and top (vanilla "caves" recipe).
        DensityFunction base = new DensityFunctions.HolderHolder(c.lookup(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("nether/base_3d_noise"))));
        DensityFunction top = DensityFunctions.lerp(DensityFunctions.yClampedGradient(MIN_Y + HEIGHT - 24, MIN_Y + HEIGHT, 1, 0), 0.9375F, base);
        DensityFunction slide = DensityFunctions.lerp(DensityFunctions.yClampedGradient(MIN_Y - 8, MIN_Y + 24, 0, 1), 2.5F, top);
        DensityFunction density = DensityFunctions.add(DensityFunctions.interpolated(
                DensityFunctions.mul(DensityFunctions.blendDensity(slide), DensityFunctions.constant(0.64F)), 4, 8).squeeze(), DensityFunctions.beardifier());
        DensityFunction zero = DensityFunctions.zero();
        c.register(UnderworldContent.NOISE, new NoiseGeneratorSettings(NoiseSettings.create(MIN_Y, HEIGHT), Blocks.STONE.defaultBlockState(),
                Blocks.AIR.defaultBlockState(), new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, density),
                c.lookup(Registries.MATERIAL_RULE).getOrThrow(UnderworldContent.MATERIAL_RULE), List.of(), MIN_Y, false, Optional.empty(), true,
                NoiseGeneratorSettings.DebugFunctions.EMPTY));
    }

    // ---- EF-DIM-03: veins per chunk (x8 the Overworld), 75 % in the lowest quarter, 25 % over the whole height. ----

    private static final List<Ore> ORES = List.of(new Ore("ore_copper", 32), new Ore("ore_silver", 8), new Ore("ore_gold", 16),
            new Ore("ore_iron", 48), new Ore("ore_mithril", 8), new Ore("ore_redstone", 8), new Ore("ore_diamond", 4),
            new Ore("ore_lapis", 4), new Ore("ore_gravel", 30), new Ore("ore_infested", 40));

    private record Ore(String feature, int perChunk) {}

    /** Underworld ore placements; adamantium is WorldgenData.ADAMANTIUM (8 per chunk, very biased to the bottom). */
    public static final List<Placement> PLACEMENTS = ORES.stream().flatMap(o -> {
        int low = Math.round(o.perChunk() * 0.75f);
        return Stream.of(
                new Placement("underworld_" + o.feature() + "_low", o.feature(), low, UniformHeight.of(VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(HEIGHT / 4 - 1))),
                new Placement("underworld_" + o.feature(), o.feature(), o.perChunk() - low, UniformHeight.of(VerticalAnchor.bottom(), VerticalAnchor.top())));
    }).toList();

    /** dom-M-10: the vanilla Nether mushroom patches, kept to the mycelium heights instead of the whole height. */
    public static final ResourceKey<PlacedFeature> BROWN_MUSHROOM = ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id("underworld_brown_mushroom"));
    public static final ResourceKey<PlacedFeature> RED_MUSHROOM = ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id("underworld_red_mushroom"));

    public static void placedFeatures(BootstrapContext<PlacedFeature> c) {
        HolderGetter<Feature> features = c.lookup(Registries.FEATURE);
        for (Placement p : PLACEMENTS) c.register(p.key(), WorldgenData.placed(features, p));
        mushroom(c, BROWN_MUSHROOM, features.getOrThrow(VegetationFeatures.BROWN_MUSHROOM));
        mushroom(c, RED_MUSHROOM, features.getOrThrow(VegetationFeatures.RED_MUSHROOM));
    }

    private static void mushroom(BootstrapContext<PlacedFeature> c, ResourceKey<PlacedFeature> key, Holder<Feature> feature) {
        c.register(key, new PlacedFeature(feature, List.of(RarityFilter.onAverageOnceEvery(2), InSquarePlacement.spread(),
                HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(MYCELIUM_MIN), VerticalAnchor.aboveBottom(MYCELIUM_MAX)), BiomeFilter.biome(),
                CountPlacement.of(96), OffsetPlacement.ofTriangle(7, 3), BlockPredicateFilter.forPredicate(BlockPredicate.ONLY_IN_AIR_PREDICATE))));
    }

    /**
     * dom-M-13 (changelog l.418-424, CDC EF-DIM-02): Overworld portals do not spawn zombified piglins. Vanilla's own Overworld
     * dimension type, with only {@code gameplay/nether_portal_spawns_piglin} turned off (minecraft namespace override), and
     * M8: the monster block light limit raised to {@link #MONSTER_BLOCK_LIGHT_LIMIT}. Same for its {@code overworld_caves} variant
     * (audit 2 X20, changelog l.237 R163 "Portals no longer spawn mobs"): no dimension type keeps portal piglins.
     */
    public static void overworldType(BootstrapContext<DimensionType> c) {
        WorldgenData.overrideVanilla(c, DimensionTypes::bootstrap, (key, t) -> {
            if (key != BuiltinDimensionTypes.OVERWORLD && key != BuiltinDimensionTypes.OVERWORLD_CAVES) return null;
            var attributes = EnvironmentAttributeMap.builder().putAll(t.attributes()).set(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, false).build();
            return new DimensionType(t.hasFixedTime(), t.hasSkyLight(), t.hasCeiling(), t.hasEnderDragonFight(), t.coordinateScale(),
                    t.minY(), t.height(), t.logicalHeight(), t.infiniburn(), t.ambientLight(),
                    new DimensionType.MonsterSettings(t.monsterSettings().monsterSpawnLightTest(), MONSTER_BLOCK_LIGHT_LIMIT), t.skybox(),
                    t.cardinalLightType(), attributes, t.timelines(), t.defaultClock());
        });
    }

    public static void biomes(BootstrapContext<Biome> c) {
        HolderGetter<PlacedFeature> placed = c.lookup(Registries.PLACED_FEATURE);
        var generation = new BiomeGenerationSettings.Builder(placed, c.lookup(Registries.CARVER));
        PLACEMENTS.forEach(p -> generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, p.key()));
        generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, WorldgenData.ADAMANTIUM.key())
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, BROWN_MUSHROOM)
                .addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, RED_MUSHROOM);
        // EF-MOB / analysis/03 §5: cave spiders (40); Longdead and Ancient Bone Lord come with their own domain.
        c.register(UnderworldContent.BIOME, new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0.5F).downfall(0.0F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                .setAttribute(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(GRAY_FOG))
                .mobSpawnSettings(new MobSpawnSettings.Builder().addSpawn(EntityTypes.CAVE_SPIDER, 40, 1, 2).build())
                .generationSettings(generation.build()).build());
    }

    public static void levelStems(BootstrapContext<LevelStem> c) {
        c.register(UnderworldContent.STEM, new LevelStem(c.lookup(Registries.DIMENSION_TYPE).getOrThrow(UnderworldContent.TYPE),
                new NoiseBasedChunkGenerator(new FixedBiomeSource(c.lookup(Registries.BIOME).getOrThrow(UnderworldContent.BIOME)),
                        c.lookup(Registries.NOISE_SETTINGS).getOrThrow(UnderworldContent.NOISE))));
    }

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            blocks.createTrivialCube(UnderworldContent.MANTLE.get());
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return UnderworldContent.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return UnderworldContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble dimension";
        }
    }
}
