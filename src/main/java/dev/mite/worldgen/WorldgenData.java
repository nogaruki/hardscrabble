package dev.mite.worldgen;

import static dev.mite.core.Formulas.mapDepth;

import dev.mite.MiteMod;
import dev.mite.worldgen.WorldgenContent.Ore;
import dev.mite.worldgen.WorldgenContent.Storage;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.material.NetherMaterialRules;
import net.minecraft.data.worldgen.features.OreFeatures;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.core.Direction;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.VegetationPatchFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.OffsetPlacement;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.templatesystem.HeightMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Worldgen datagen: ore and storage block models, tags, loot and recipes; EF-MON-02 ore features and biome modifiers. */
public final class WorldgenData {
    private WorldgenData() {}

    // ---- EF-MON-02 Overworld ore table. Y values are MITE 1.6.4 heights, converted with Formulas.mapDepth (EF-MON-01). ----

    private static final RuleTest NATURAL_STONE = new TagMatchTest(BlockTags.BASE_STONE_OVERWORLD);
    // Same stone / deepslate split as the vanilla ores.
    private static final RuleTest STONE = RuleTest.either(new TagMatchTest(BlockTags.HEIGHT_SPECIFIC_ORE_REPLACEABLES), HeightMatchTest.min(0),
            new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES));
    private static final RuleTest DEEPSLATE = RuleTest.either(new TagMatchTest(BlockTags.HEIGHT_SPECIFIC_ORE_REPLACEABLES), HeightMatchTest.max(8),
            new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES));

    /** Vanilla ore placements replaced by the table. Ancient debris and stone variants stay vanilla [HORS-MITE]; the emerald is overridden by {@link #emeraldOre}. */
    private static final List<ResourceKey<PlacedFeature>> VANILLA_REPLACED = List.of(OrePlacements.ORE_DIRT, OrePlacements.ORE_GRAVEL,
            OrePlacements.ORE_COAL_UPPER, OrePlacements.ORE_COAL_LOWER, OrePlacements.ORE_COPPER, OrePlacements.ORE_COPPER_LARGE,
            OrePlacements.ORE_IRON_UPPER, OrePlacements.ORE_IRON_MIDDLE, OrePlacements.ORE_IRON_SMALL,
            OrePlacements.ORE_GOLD, OrePlacements.ORE_GOLD_LOWER, OrePlacements.ORE_GOLD_EXTRA,
            OrePlacements.ORE_REDSTONE, OrePlacements.ORE_REDSTONE_LOWER, OrePlacements.ORE_DIAMOND, OrePlacements.ORE_DIAMOND_MEDIUM,
            OrePlacements.ORE_DIAMOND_LARGE, OrePlacements.ORE_DIAMOND_BURIED, OrePlacements.ORE_LAPIS, OrePlacements.ORE_LAPIS_BURIED,
            OrePlacements.ORE_INFESTED);

    private static ResourceKey<Feature> featureKey(String name) {
        return ResourceKey.create(Registries.FEATURE, MiteMod.id(name));
    }

    private static List<BlockReplacement> targets(Block stone, Block deepslate) {
        return List.of(BlockReplacement.replace(STONE, stone.defaultBlockState()), BlockReplacement.replace(DEEPSLATE, deepslate.defaultBlockState()));
    }

    /** Metal vein: x1.5 at the bottom of the MITE range, x0.5 at the top. */
    private static Feature metal(Block stone, Block deepslate, int size, int miteMin, int miteMax) {
        return new DepthScaledOreFeature(targets(stone, deepslate), size, mapDepth(miteMin), mapDepth(miteMax));
    }

    public static void features(BootstrapContext<Feature> c) {
        c.register(featureKey("ore_dirt"), new OreFeature(NATURAL_STONE, Blocks.DIRT.defaultBlockState(), 32));
        c.register(featureKey("ore_gravel"), new OreFeature(NATURAL_STONE, Blocks.GRAVEL.defaultBlockState(), 32));
        c.register(featureKey("ore_coal"), new OreFeature(targets(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE), 16));
        c.register(featureKey("ore_copper"), metal(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, 6, 0, 128));
        c.register(featureKey("ore_silver"), metal(WorldgenContent.SILVER_ORE.stone().get(), WorldgenContent.SILVER_ORE.deepslate().get(), 6, 0, 96));
        c.register(featureKey("ore_gold"), metal(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, 4, 0, 48));
        c.register(featureKey("ore_iron"), metal(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 6, 0, 64));
        c.register(featureKey("ore_mithril"), metal(WorldgenContent.MITHRIL_ORE.stone().get(), WorldgenContent.MITHRIL_ORE.deepslate().get(), 3, 0, 32));
        c.register(featureKey("ore_redstone"), new OreFeature(targets(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE), 5));
        c.register(featureKey("ore_diamond"), new OreFeature(targets(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), 3));
        c.register(featureKey("ore_lapis"), new OreFeature(targets(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE), 3));
        c.register(featureKey("ore_infested"), new OreFeature(targets(Blocks.INFESTED_STONE, Blocks.INFESTED_DEEPSLATE), 3));
        // Underworld (M4): vein 3, x16 frequency concentrated near the bottom (analysis/02 §5.2). Height range to revisit with the dimension.
        c.register(featureKey("ore_adamantium"), new OreFeature(targets(WorldgenContent.ADAMANTIUM_ORE.stone().get(), WorldgenContent.ADAMANTIUM_ORE.deepslate().get()), 3));
        // Changelog l.440 (R139): mushroom caverns, a mycelium cave floor dotted with mushrooms [INFÉRÉ: shape].
        c.register(featureKey("cavern_mushrooms"), new SimpleBlockFeature(new WeightedStateProvider(WeightedList.<BlockState>builder()
                .add(Blocks.BROWN_MUSHROOM.defaultBlockState(), 2).add(Blocks.RED_MUSHROOM.defaultBlockState(), 1))));
        c.register(featureKey("mushroom_cavern"), new VegetationPatchFeature(c.lookup(Registries.BLOCK).getOrThrow(BlockTags.MOSS_REPLACEABLE),
                BlockStateProvider.holderOf(Blocks.MYCELIUM), PlacementUtils.inlinePlaced(c.lookup(Registries.FEATURE).getOrThrow(featureKey("cavern_mushrooms"))),
                CaveSurface.FLOOR, ConstantInt.of(1), 0, 5, 0.25F, UniformInt.of(4, 7), 0.3F));
        // Changelog l.200 (R168): patches of exposed stone on the surface: grass and dirt replaced down to the stone [INFÉRÉ: size].
        c.register(featureKey("surface_stone"), new DiskFeature(BlockStateProvider.holderOf(Blocks.STONE),
                BlockPredicate.matchesBlocks(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL), UniformInt.of(2, 5), 3));
    }

    private static VerticalAnchor y(int miteY) {
        return VerticalAnchor.absolute(mapDepth(miteY));
    }

    /** One placed feature: average veins per chunk and height distribution. */
    public record Placement(String name, String feature, float perChunk, HeightProvider height) {
        public ResourceKey<PlacedFeature> key() {
            return ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id(name));
        }
    }

    /** Placed features added to every Overworld biome, in this order. */
    public static final List<Placement> OVERWORLD = List.of(
            new Placement("ore_dirt", "ore_dirt", 20, new BiasedToTopHeight(y(32), y(128))),
            new Placement("ore_gravel", "ore_gravel", 20, new BiasedToTopHeight(y(24), y(128))),
            new Placement("ore_coal", "ore_coal", 5, new BiasedToTopHeight(y(16), y(96))),
            // Copper: 4 veins, half uniform over 40-100 % of the range (MITE 51-128), half biased to the bottom.
            new Placement("ore_copper_upper", "ore_copper", 2, UniformHeight.of(y(51), y(128))),
            new Placement("ore_copper", "ore_copper", 2, BiasedToBottomHeight.of(y(0), y(128), 1)),
            new Placement("ore_silver", "ore_silver", 1, BiasedToBottomHeight.of(y(0), y(96), 1)),
            new Placement("ore_gold", "ore_gold", 2, BiasedToBottomHeight.of(y(0), y(48), 1)),
            new Placement("ore_iron", "ore_iron", 6, BiasedToBottomHeight.of(y(0), y(64), 1)),
            new Placement("ore_mithril", "ore_mithril", 1, BiasedToBottomHeight.of(y(0), y(32), 1)),
            new Placement("ore_redstone", "ore_redstone", 1, BiasedToBottomHeight.of(y(0), y(24), 1)),
            new Placement("ore_diamond", "ore_diamond", 0.5f, BiasedToBottomHeight.of(y(0), y(32), 1)),
            new Placement("ore_lapis", "ore_lapis", 0.5f, TrapezoidHeight.of(y(8), y(40))),
            new Placement("ore_infested", "ore_infested", 0.5f, BiasedToBottomHeight.of(y(0), y(24), 1)));

    /** Underworld only (M4): registered, never added to the Overworld (EF-MON-02 CA-2). */
    public static final Placement ADAMANTIUM = new Placement("ore_adamantium", "ore_adamantium", 8,
            VeryBiasedToBottomHeight.of(VerticalAnchor.bottom(), VerticalAnchor.top(), 8));

    public static final ResourceKey<PlacedFeature> MUSHROOM_CAVERN = ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id("mushroom_cavern"));
    public static final ResourceKey<PlacedFeature> SURFACE_STONE = ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id("surface_stone"));

    public static void placedFeatures(BootstrapContext<PlacedFeature> c) {
        HolderGetter<Feature> features = c.lookup(Registries.FEATURE);
        for (Placement p : OVERWORLD) c.register(p.key(), placed(features, p));
        // Changelog l.440: rare, one cavern every 64 chunks: 48 patches on the cave floors of one chunk below MITE y 48 [INFÉRÉ].
        c.register(MUSHROOM_CAVERN, new PlacedFeature(features.getOrThrow(featureKey("mushroom_cavern")), List.of(RarityFilter.onAverageOnceEvery(64),
                CountPlacement.of(48), InSquarePlacement.spread(), HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(8), y(48)),
                EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.solid(), BlockPredicate.ONLY_IN_AIR_PREDICATE, 12),
                OffsetPlacement.vertical(ConstantInt.of(1)), BiomeFilter.biome())));
        // Changelog l.200: one stone patch every 6 chunks [INFÉRÉ], on the top solid block.
        c.register(SURFACE_STONE, new PlacedFeature(features.getOrThrow(featureKey("surface_stone")), List.of(RarityFilter.onAverageOnceEvery(6),
                InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP_TOP_SOLID, BiomeFilter.biome())));
        c.register(ADAMANTIUM.key(), placed(features, ADAMANTIUM));
        // L01 (EF-MON-02 "plus de lave en profondeur"): extra lava springs below MITE y 16 [count INFÉRÉ].
        c.register(DEEP_LAVA, new PlacedFeature(features.getOrThrow(MiscOverworldFeatures.SPRING_LAVA_OVERWORLD), List.of(CountPlacement.of(8),
                InSquarePlacement.spread(), HeightRangePlacement.of(UniformHeight.of(VerticalAnchor.bottom(), y(16))), BiomeFilter.biome())));
    }

    /**
     * M6 (EF-JOU-08, RE-04): minecraft:ore_emerald keeps its vanilla mountain biomes and vein but loses its 100 tries per chunk:
     * one vein every 2 mountain chunks at diamond depth, like the diamond [INFÉRÉ, balance in Q08]. Generated under minecraft:.
     */
    public static void emeraldOre(BootstrapContext<PlacedFeature> c) {
        c.register(OrePlacements.ORE_EMERALD, new PlacedFeature(c.lookup(Registries.FEATURE).getOrThrow(OreFeatures.ORE_EMERALD), List.of(
                RarityFilter.onAverageOnceEvery(2), InSquarePlacement.spread(), HeightRangePlacement.of(BiasedToBottomHeight.of(y(0), y(32), 1)), BiomeFilter.biome())));
    }

    public static final ResourceKey<PlacedFeature> DEEP_LAVA = ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id("spring_lava_deep"));

    /** A fixed count of veins, or one vein every 1/perChunk chunks below 1. */
    public static PlacedFeature placed(HolderGetter<Feature> features, Placement p) {
        PlacementModifier frequency = p.perChunk() >= 1 ? CountPlacement.of(Math.round(p.perChunk())) : RarityFilter.onAverageOnceEvery(Math.round(1 / p.perChunk()));
        return new PlacedFeature(features.getOrThrow(featureKey(p.feature())),
                List.of(frequency, InSquarePlacement.spread(), HeightRangePlacement.of(p.height()), BiomeFilter.biome()));
    }

    /**
     * Runs a vanilla bootstrap and registers only the entries {@code keep} maps to a value (possibly changed); null drops the
     * entry, which keeps its vanilla file. Used for minecraft-namespace overrides of single entries.
     */
    @SuppressWarnings("deprecation")
    public static <T> void overrideVanilla(BootstrapContext<T> c, Consumer<BootstrapContext<T>> vanillaBootstrap, BiFunction<ResourceKey<T>, T, @Nullable T> keep) {
        vanillaBootstrap.accept(new BootstrapContext<>() {
            @Override
            public Holder.Reference<T> register(ResourceKey<T> key, T value) {
                T kept = keep.apply(key, value);
                return kept == null ? null : c.register(key, kept);
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                return c.lookup(key);
            }

            @Override
            public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
                return c.listContextElements(key);
            }
        });
    }

    public static void biomeModifiers(BootstrapContext<BiomeModifier> c) {
        HolderGetter<PlacedFeature> placed = c.lookup(Registries.PLACED_FEATURE);
        HolderSet<net.minecraft.world.level.biome.Biome> overworld = c.lookup(Registries.BIOME).getOrThrow(BiomeTags.IS_OVERWORLD);
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("remove_vanilla_ores")),
                BiomeModifiers.RemoveFeaturesBiomeModifier.allSteps(overworld, HolderSet.direct(VANILLA_REPLACED.stream().map(placed::getOrThrow).toList())));
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("add_ores")), new BiomeModifiers.AddFeaturesBiomeModifier(
                overworld, HolderSet.direct(OVERWORLD.stream().map(p -> placed.getOrThrow(p.key())).toList()), GenerationStep.Decoration.UNDERGROUND_ORES));
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("add_deep_lava")), new BiomeModifiers.AddFeaturesBiomeModifier(
                overworld, HolderSet.direct(placed.getOrThrow(DEEP_LAVA)), GenerationStep.Decoration.FLUID_SPRINGS));
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("add_mushroom_caverns")), new BiomeModifiers.AddFeaturesBiomeModifier(
                overworld, HolderSet.direct(placed.getOrThrow(MUSHROOM_CAVERN)), GenerationStep.Decoration.UNDERGROUND_DECORATION));
        // Before the trees (vegetal decoration), which then find no soil on the stone.
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("add_surface_stone")), new BiomeModifiers.AddFeaturesBiomeModifier(
                overworld, HolderSet.direct(placed.getOrThrow(SURFACE_STONE)), GenerationStep.Decoration.LOCAL_MODIFICATIONS));
    }

    // ---- EF-DIM-05: Core layer at the bottom of the Nether. minecraft:nether noise settings point at hardscrabble:nether. ----

    public static final ResourceKey<MaterialRule> NETHER_RULE = ResourceKey.create(Registries.MATERIAL_RULE, MiteMod.id("nether"));

    /** EF-MON-02 (M3): the 1.18 copper and iron ore veins of the Overworld rules are overridden with an empty sequence (no block). */
    public static final List<ResourceKey<MaterialRule>> ORE_VEINS = Stream.of("copper", "iron")
            .map(m -> ResourceKey.create(Registries.MATERIAL_RULE, Identifier.withDefaultNamespace("overworld/" + m + "_ore_vein"))).toList();

    public static void materialRules(BootstrapContext<MaterialRule> c) {
        ORE_VEINS.forEach(k -> c.register(k, new SequenceRule(List.of())));
        c.register(NETHER_RULE, MaterialRules.sequence(
                MaterialRules.ifTrue(MaterialRules.not(MaterialRules.yBlockCheck(VerticalAnchor.aboveBottom(1), 0)),
                        MaterialRules.state(WorldgenContent.CORE.get().defaultBlockState())),
                MaterialRules.getRule(c.lookup(Registries.MATERIAL_RULE), NetherMaterialRules.NETHER)));
    }

    /** Vanilla Nether settings with {@link #NETHER_RULE}; generated under minecraft: (MiteDataGen). */
    @SuppressWarnings("deprecation") // disableMobGeneration is a record field copied as is
    public static void netherNoise(BootstrapContext<NoiseGeneratorSettings> c) {
        NoiseGeneratorSettings v = NoiseGeneratorSettings.nether(c);
        c.register(NoiseGeneratorSettings.NETHER, new NoiseGeneratorSettings(v.noiseSettings(), v.defaultBlock(), v.defaultFluid(), v.noiseRouter(),
                c.lookup(Registries.MATERIAL_RULE).getOrThrow(NETHER_RULE), v.spawnTarget(), v.seaLevel(), v.disableMobGeneration(), v.aquifers(),
                v.useLegacyRandomSource(), v.debugFunctions()));
    }

    // ---- Blocks (EF-MET-01) ----

    private static Stream<Block> blocks() {
        return WorldgenContent.BLOCKS.getEntries().stream().map(Holder::value);
    }

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            blocks().forEach(blocks::createTrivialCube);
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return WorldgenContent.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return WorldgenContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble worldgen";
        }
    }

    /** Pickaxe-mined; vanilla needs_* tags matching the MITE level (2 stone, 3 iron, 4+ diamond). Called by MiteBlockTags. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        var pickaxe = tag.apply(BlockTags.MINEABLE_WITH_PICKAXE);
        for (Ore o : WorldgenContent.ORES) {
            for (var b : List.of(o.stone(), o.deepslate())) {
                pickaxe.add(b.unwrapKey().orElseThrow());
                tag.apply(needs(o.level())).add(b.unwrapKey().orElseThrow());
            }
        }
        for (Storage s : WorldgenContent.STORAGE) {
            pickaxe.add(s.block().unwrapKey().orElseThrow());
            tag.apply(needs(s.level())).add(s.block().unwrapKey().orElseThrow());
        }
        // The Core is permanent like bedrock: withers, the dragon and worldgen features do not break or replace it.
        for (TagKey<Block> t : List.of(BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE, BlockTags.FEATURES_CANNOT_REPLACE))
            tag.apply(t).add(WorldgenContent.CORE.unwrapKey().orElseThrow());
    }

    private static TagKey<Block> needs(int level) {
        return level <= 2 ? BlockTags.NEEDS_STONE_TOOL : level == 3 ? BlockTags.NEEDS_IRON_TOOL : BlockTags.NEEDS_DIAMOND_TOOL;
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK);

    /** Ores drop one raw item, no Fortune (EF-MET-01, M4: metals stay scarce), the block itself with silk touch. */
    private static final class Loot extends BlockLootSubProvider {
        Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            for (Ore o : WorldgenContent.ORES) {
                add(o.stone().get(), b -> createSingleItemTableWithSilkTouch(b, o.raw()));
                add(o.deepslate().get(), b -> createSingleItemTableWithSilkTouch(b, o.raw()));
            }
            WorldgenContent.STORAGE.forEach(s -> dropSelf(s.block().get()));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return blocks().toList();
        }
    }

    /** Vanilla metal ores, generated under minecraft: (MiteDataGen): copper gives 1 raw instead of 2-5 and Fortune does nothing (M4). */
    public static final LootTableProvider.SubProviderEntry VANILLA_ORE_LOOT = new LootTableProvider.SubProviderEntry(VanillaOreLoot::new, LootContextParamSets.BLOCK);

    public static final Map<Block, Item> VANILLA_ORES = Map.of(Blocks.COPPER_ORE, Items.RAW_COPPER, Blocks.DEEPSLATE_COPPER_ORE, Items.RAW_COPPER,
            Blocks.IRON_ORE, Items.RAW_IRON, Blocks.DEEPSLATE_IRON_ORE, Items.RAW_IRON, Blocks.GOLD_ORE, Items.RAW_GOLD, Blocks.DEEPSLATE_GOLD_ORE, Items.RAW_GOLD);

    private static final class VanillaOreLoot extends BlockLootSubProvider {
        VanillaOreLoot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            VANILLA_ORES.forEach((b, raw) -> add(b, createSingleItemTableWithSilkTouch(b, raw)));
            add(Blocks.NETHER_GOLD_ORE, b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b,
                    LootItem.lootTableItem(Items.GOLD_NUGGET).apply(SetItemCountFunction.setCount(ContextIntProviders.between(2, 6))))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Stream.concat(VANILLA_ORES.keySet().stream(), Stream.of(Blocks.NETHER_GOLD_ORE)).toList();
        }
    }

    /** Storage block <-> 9 ingots. Ore smelting lives in MetallurgyData with the EF-MET-07 XP. */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            for (Storage s : WorldgenContent.STORAGE) {
                String block = getItemName(s.block().get()), ingot = getItemName(s.ingot());
                nineBlockStorageRecipes(RecipeCategory.MISC, s.ingot(), RecipeCategory.BUILDING_BLOCKS, s.block().get(),
                        "hardscrabble:" + block, null, "hardscrabble:" + ingot + "_from_" + block, ingot);
            }
        }
    }
}
