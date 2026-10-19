package dev.mite.farming;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.mite.MiteMod;
import dev.mite.metabolism.MetabolismContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jspecify.annotations.Nullable;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Farming datagen: crop and item models, onion loot, dish recipes, forest blueberries, crop block tags, village farms. */
public final class FarmingData {
    private FarmingData() {}

    private static Stream<Block> deadCrops() {
        return Stream.of(FarmingContent.DEAD_WHEAT, FarmingContent.DEAD_CARROTS, FarmingContent.DEAD_POTATOES, FarmingContent.DEAD_ONIONS).map(Holder::value);
    }

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            // Also writes the onion item model.
            blocks.createCropBlock(FarmingContent.ONIONS.get(), CropBlock.AGE, 0, 0, 1, 1, 2, 2, 2, 3);
            deadCrops().forEach(b -> blocks.createCrossBlock(b, BlockModelGenerators.PlantType.NOT_TINTED));
            FarmingContent.ITEMS.getEntries().stream().filter(i -> i != FarmingContent.ONION)
                    .forEach(i -> items.generateFlatItem(i.get(), ModelTemplates.FLAT_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return FarmingContent.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return FarmingContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble farming";
        }
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK);

    /** Onions drop like carrots; dead crops have no loot table. */
    private static final class Loot extends BlockLootSubProvider {
        Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            Block onions = FarmingContent.ONIONS.get();
            Item onion = FarmingContent.ONION.get();
            add(onions, createCropDrops(onions, onion, onion,
                    MatchBlock.blockMatches(blocks, onions, StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, 7))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Set.of(FarmingContent.ONIONS.get());
        }
    }

    /** Onions keep their farmland and count as crops; dead crops keep the farmland of locked villages. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.CROPS).add(FarmingContent.ONIONS.unwrapKey().orElseThrow());
        // Audit 2 X6: a pollinating bee is mob bone meal (BonemealSource.MOB) that skips CropGrowEvent, hence the sun, blight, soil and
        // snow rules. R128 refuses bone meal on crops, stems and berry bushes, and lot D extends it to cave vines (glow berries), so
        // bees grow nothing.
        tag.apply(BlockTags.BEE_GROWABLES).replace();
        var farmland = tag.apply(BlockTags.MAINTAINS_FARMLAND);
        FarmingContent.BLOCKS.getEntries().forEach(b -> farmland.add(b.unwrapKey().orElseThrow()));
    }

    /** EF-AGR-02: sweet berries (the MITE blueberries) also grow in forests. */
    public static void biomeModifiers(BootstrapContext<BiomeModifier> c) {
        HolderGetter<PlacedFeature> placed = c.lookup(Registries.PLACED_FEATURE);
        HolderGetter<Biome> biomes = c.lookup(Registries.BIOME);
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("forest_blueberries")), new BiomeModifiers.AddFeaturesBiomeModifier(
                biomes.getOrThrow(BiomeTags.IS_FOREST), HolderSet.direct(placed.getOrThrow(VegetationPlacements.PATCH_BERRY_RARE)),
                GenerationStep.Decoration.VEGETAL_DECORATION));
    }

    /**
     * EF-MON-05 / R165: village farms generate with dead crops (overrides the five vanilla {@code farm_*} processor lists, and the
     * five {@code zombie_*} lists that abandoned villages use instead). Onions take the place of the beetroots (absent from 1.6.4)
     * and stay alive and ripe: the source of onions [INFÉRÉ] (dom-I-01). Melon and pumpkin stems are not placed: their wheat dies too.
     */
    public static void villageFarms(BootstrapContext<StructureProcessorList> c) {
        Map<ResourceKey<StructureProcessorList>, StructureProcessorList> vanilla = vanillaLists(c);
        for (var zombie : new boolean[] {false, true}) {
            farm(c, zombie ? ProcessorLists.ZOMBIE_PLAINS : ProcessorLists.FARM_PLAINS, vanilla, 0.3f, 0.2f, 0.1f);
            farm(c, zombie ? ProcessorLists.ZOMBIE_SAVANNA : ProcessorLists.FARM_SAVANNA, vanilla, 0, 0, 0);
            farm(c, zombie ? ProcessorLists.ZOMBIE_SNOWY : ProcessorLists.FARM_SNOWY, vanilla, 0.1f, 0.8f, 0);
            farm(c, zombie ? ProcessorLists.ZOMBIE_TAIGA : ProcessorLists.FARM_TAIGA, vanilla, 0, 0.2f, 0);
            farm(c, zombie ? ProcessorLists.ZOMBIE_DESERT : ProcessorLists.FARM_DESERT, vanilla, 0, 0, 0.2f);
        }
    }

    /** The vanilla processor lists, captured by running their bootstrap into a map. */
    private static Map<ResourceKey<StructureProcessorList>, StructureProcessorList> vanillaLists(BootstrapContext<StructureProcessorList> c) {
        Map<ResourceKey<StructureProcessorList>, StructureProcessorList> lists = new HashMap<>();
        ProcessorLists.bootstrap(new BootstrapContext<>() {
            @Override
            public Holder.Reference<StructureProcessorList> register(ResourceKey<StructureProcessorList> key, StructureProcessorList value) {
                lists.put(key, value);
                return null; // vanilla ignores it
            }

            @Override
            public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                return c.lookup(key);
            }

            @Override
            @Deprecated
            public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
                return c.listContextElements(key);
            }
        });
        return lists;
    }

    /**
     * Vanilla odds per wheat block, tried in order (first match wins); the remaining wheat dies. A zombie list keeps every vanilla
     * processor (mossy cobblestone, cobwebs, brown panes...) after the crop rules, whose vanilla wheat rules then never match.
     */
    private static void farm(BootstrapContext<StructureProcessorList> c, ResourceKey<StructureProcessorList> key,
            Map<ResourceKey<StructureProcessorList>, StructureProcessorList> vanilla, float carrots, float potatoes, float onions) {
        List<ProcessorRule> rules = new ArrayList<>();
        if (carrots > 0) rules.add(wheatTo(carrots, FarmingContent.DEAD_CARROTS.get().defaultBlockState()));
        if (potatoes > 0) rules.add(wheatTo(potatoes, FarmingContent.DEAD_POTATOES.get().defaultBlockState()));
        if (onions > 0) rules.add(wheatTo(onions, FarmingContent.ONIONS.get().getStateForAge(CropBlock.MAX_AGE)));
        rules.add(wheatTo(1, FarmingContent.DEAD_WHEAT.get().defaultBlockState()));
        List<StructureProcessor> processors = new ArrayList<>(List.of(new RuleProcessor(rules)));
        if (key.identifier().getPath().startsWith("zombie_")) processors.addAll(vanilla.get(key).list());
        c.register(key, new StructureProcessorList(processors));
    }

    private static ProcessorRule wheatTo(float chance, BlockState state) {
        return new ProcessorRule(chance >= 1 ? new BlockMatchTest(Blocks.WHEAT) : new RandomBlockMatchTest(Blocks.WHEAT, chance), AlwaysTrueTest.INSTANCE, state);
    }

    /** EF-AGR-10 recipes (analysis/02 §8.3). Vanilla bread, cookie and cake are disabled by static files in data/minecraft. */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        /** A shapeless recipe; one that uses a water or milk bowl without making a bowl dish hands the bowl back. */
        private void dish(ItemLike result, int count, ItemLike... ingredients) {
            var r = shapeless(RecipeCategory.FOOD, result, count);
            for (ItemLike i : ingredients) r.requires(i);
            boolean bowlDish = FarmingContent.DISHES.stream().anyMatch(d -> d.get() == result.asItem());
            boolean bowls = Stream.of(ingredients).anyMatch(i -> i.asItem() == MetabolismContent.WATER_BOWL.get() || i.asItem() == MetabolismContent.MILK_BOWL.get());
            r.unlockedBy("has_" + name(ingredients[0]), has(ingredients[0]))
                    .save(bowls && !bowlDish ? returningBowls() : output, "hardscrabble:" + name(result) + "_from_" + name(ingredients[0]));
        }

        /** Saves the shapeless recipes given to it as {@link FarmingContent.BowlRecipe}s. */
        private RecipeOutput returningBowls() {
            RecipeOutput out = output;
            return new RecipeOutput() {
                @Override
                public void accept(ResourceKey<Recipe<?>> key, Recipe<?> recipe, @Nullable AdvancementHolder advancement, ICondition... conditions) {
                    out.accept(key, new FarmingContent.BowlRecipe((ShapelessRecipe) recipe), advancement, conditions);
                }

                @Override
                public Advancement.Builder advancement() {
                    return out.advancement();
                }

                @Override
                public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
                    return out.lookup(key);
                }

                @Override
                @Deprecated
                public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
                    return out.listContextElements(key);
                }
            };
        }

        private static String name(ItemLike item) {
            return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
        }

        @Override
        protected void buildRecipes() {
            Item water = MetabolismContent.WATER_BOWL.get(), milk = MetabolismContent.MILK_BOWL.get();
            dish(FarmingContent.FLOUR.get(), 1, Items.WHEAT);
            dish(FarmingContent.DOUGH.get(), 1, FarmingContent.FLOUR.get(), water);
            Ingredient dough = Ingredient.of(FarmingContent.DOUGH.get());
            SimpleCookingRecipeBuilder.smelting(dough, RecipeCategory.FOOD, CookingBookCategory.FOOD, Items.BREAD, 0, 200)
                    .unlockedBy("has_dough", has(FarmingContent.DOUGH.get())).save(output, "hardscrabble:bread_from_smelting");
            SimpleCookingRecipeBuilder.smoking(dough, RecipeCategory.FOOD, Items.BREAD, 0, 100)
                    .unlockedBy("has_dough", has(FarmingContent.DOUGH.get())).save(output, "hardscrabble:bread_from_smoking");
            SimpleCookingRecipeBuilder.campfireCooking(dough, RecipeCategory.FOOD, Items.BREAD, 0, 600)
                    .unlockedBy("has_dough", has(FarmingContent.DOUGH.get())).save(output, "hardscrabble:bread_from_campfire_cooking");
            dish(FarmingContent.CHEESE.get(), 1, milk, milk, milk, milk);
            // Changelog l.578 (R127): 8 bowls make 2 cheeses at once. Changelog l.246 (R164): milk buckets of any metal, mixed,
            // make a batch (1 bucket = 4 bowls = 1 cheese; up to 4 buckets [INFÉRÉ]); each bucket comes back empty.
            shapeless(RecipeCategory.FOOD, FarmingContent.CHEESE.get(), 2).requires(milk, 8).unlockedBy("has_milk_bowl", has(milk))
                    .save(returningBowls(), "hardscrabble:cheese_from_8_milk_bowls");
            for (int n = 1; n <= 4; n++) {
                shapeless(RecipeCategory.FOOD, FarmingContent.CHEESE.get(), n).requires(tag(net.neoforged.neoforge.common.Tags.Items.BUCKETS_MILK), n)
                        .unlockedBy("has_milk_bucket", has(net.neoforged.neoforge.common.Tags.Items.BUCKETS_MILK))
                        .save(output, "hardscrabble:cheese_from_" + n + "_milk_bucket" + (n > 1 ? "s" : ""));
            }
            dish(FarmingContent.CHOCOLATE.get(), 1, Items.COCOA_BEANS, Items.SUGAR);
            dish(Items.COOKIE, 4, FarmingContent.DOUGH.get(), FarmingContent.CHOCOLATE.get());
            dish(Items.CAKE, 1, FarmingContent.FLOUR.get(), Items.SUGAR, Items.EGG, milk);
            dish(FarmingContent.SALAD.get(), 1, Items.DANDELION, Items.DANDELION, Items.DANDELION, Items.BOWL);
            dish(FarmingContent.BEEF_STEW.get(), 1, Items.COOKED_BEEF, Items.BROWN_MUSHROOM, Items.POTATO, water);
            dish(FarmingContent.CHICKEN_SOUP.get(), 1, Items.COOKED_CHICKEN, Items.CARROT, FarmingContent.ONION.get(), water);
            dish(FarmingContent.VEGETABLE_SOUP.get(), 1, Items.POTATO, Items.CARROT, FarmingContent.ONION.get(), water);
            dish(FarmingContent.CREAM_OF_VEGETABLE_SOUP.get(), 1, Items.POTATO, Items.CARROT, FarmingContent.ONION.get(), milk);
            dish(FarmingContent.CREAM_OF_MUSHROOM_SOUP.get(), 1, Items.BROWN_MUSHROOM, Items.BROWN_MUSHROOM, milk);
            dish(FarmingContent.PUMPKIN_SOUP.get(), 1, Items.PUMPKIN, water);
            dish(FarmingContent.ICE_CREAM.get(), 1, FarmingContent.CHOCOLATE.get(), milk, Items.SNOWBALL);
            dish(FarmingContent.ICE_CREAM.get(), 1, Items.COCOA_BEANS, Items.SUGAR, milk, Items.SNOWBALL);
            dish(FarmingContent.SORBET.get(), 1, FarmingContent.ORANGE.get(), Items.SUGAR, Items.SNOWBALL, Items.BOWL);
            dish(FarmingContent.MASHED_POTATOES.get(), 1, Items.BAKED_POTATO, FarmingContent.CHEESE.get(), milk);
            dish(FarmingContent.PORRIDGE.get(), 1, Items.WHEAT_SEEDS, Items.SWEET_BERRIES, Items.SUGAR, water);
            dish(FarmingContent.CEREAL.get(), 1, Items.WHEAT, Items.SUGAR, milk);
        }
    }
}
