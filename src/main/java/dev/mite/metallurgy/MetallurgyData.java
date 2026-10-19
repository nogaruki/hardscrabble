package dev.mite.metallurgy;

import static dev.mite.datagen.MiteItemTags.itemKey;

import net.minecraft.core.registries.BuiltInRegistries;
import static dev.mite.worldgen.WorldgenContent.ADAMANTIUM_ORE;
import static dev.mite.worldgen.WorldgenContent.MITHRIL_ORE;
import static dev.mite.worldgen.WorldgenContent.SILVER_ORE;

import dev.mite.MiteMod;
import dev.mite.material.MaterialItems;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.flag.FeatureFlags;
import java.util.function.Function;
import dev.mite.worldgen.WorldgenContent;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootPredicates;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConditionalValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.common.data.DataMapProvider;

/** Datagen for metallurgy: furnace models, loot and recipes, heat data maps, fuel burn times and smelting XP. */
public final class MetallurgyData {
    private MetallurgyData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            Metallurgy.FURNACES.forEach(b -> blocks.createFurnace(b.get(), TexturedModel.ORIENTABLE_ONLY_TOP));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Metallurgy.FURNACES.stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return Metallurgy.FURNACE_ITEMS.stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble metallurgy";
        }
    }

    public static final class DataMaps extends DataMapProvider {
        public DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void gather(HolderLookup.Provider provider) {
            // EF-MET-06: every other fuel (wood, stick, paper, charcoal, dried kelp block, bamboo) has heat 1.
            var fuel = builder(FurnaceHeat.FUEL_HEAT);
            item(fuel, 2, Items.COAL, Items.COAL_BLOCK);
            // Lava buckets of every metal (D16), the vanilla one included.
            item(fuel, 3, dev.mite.equipment.EquipmentContent.BUCKETS.values().stream().map(s -> s.lava().get()).toArray(Item[]::new));
            item(fuel, 4, Items.BLAZE_ROD);

            // EF-MET-07: everything else needs heat 1. Ores need the heat of their raw item.
            var smelt = builder(FurnaceHeat.SMELT_HEAT);
            item(smelt, 2, Items.RAW_COPPER, Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE, MaterialItems.RAW_SILVER.get(),
                    Items.RAW_GOLD, Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.NETHER_GOLD_ORE,
                    Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE, Items.NETHER_QUARTZ_ORE,
                    Items.EMERALD_ORE, Items.DEEPSLATE_EMERALD_ORE, Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE,
                    Items.REDSTONE_ORE, Items.DEEPSLATE_REDSTONE_ORE, Items.LAPIS_ORE, Items.DEEPSLATE_LAPIS_ORE);
            item(smelt, 2, SILVER_ORE.stone().get().asItem(), SILVER_ORE.deepslate().get().asItem());
            item(smelt, 3, MaterialItems.RAW_MITHRIL.get(), MITHRIL_ORE.stone().get().asItem(), MITHRIL_ORE.deepslate().get().asItem());
            item(smelt, 4, MaterialItems.RAW_ADAMANTIUM.get(), ADAMANTIUM_ORE.stone().get().asItem(), ADAMANTIUM_ORE.deepslate().get().asItem());
            // Sand and sandstone smelt from heat 1; their output depends on the heat (FurnaceHeat.heatedResult): 4 sand -> sandstone
            // at heat 1 (changelog l.760, R119), -> glass from heat 2 (l.709, R121); sandstone -> glass from heat 2 (l.138, R177).
            // Melting tools and armor back into nuggets needs the heat of their metal (vanilla *_nugget_from_smelting inputs).
            for (String metal : List.of("copper", "iron", "golden", "chainmail"))
                for (String piece : List.of("pickaxe", "shovel", "axe", "hoe", "sword", "spear", "helmet", "chestplate", "leggings", "boots", "horse_armor", "nautilus_armor"))
                    BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(metal + "_" + piece)).ifPresent(i -> item(smelt, 2, i));

            // EF-MET-05 and arbitrage n°11: vanilla furnace = stone furnace (2), smoker = oven (1), blast furnace = 2.
            var furnace = builder(FurnaceHeat.FURNACE_HEAT);
            block(furnace, 1, Blocks.SMOKER, Metallurgy.CLAY_FURNACE.get(), Metallurgy.LARGE_CLAY_FURNACE.get(), Metallurgy.SANDSTONE_FURNACE.get());
            block(furnace, 2, Blocks.FURNACE, Blocks.BLAST_FURNACE);
            block(furnace, 3, Metallurgy.OBSIDIAN_FURNACE.get());
            block(furnace, 4, Metallurgy.NETHERRACK_FURNACE.get());
        }

        private static void item(Builder<Integer, Item> map, int heat, Item... items) {
            for (Item i : items) map.add(BuiltInRegistries.ITEM.wrapAsHolder(i), heat, false);
        }

        private static void block(Builder<Integer, Block> map, int heat, Block... blocks) {
            for (Block b : blocks) map.add(BuiltInRegistries.BLOCK.wrapAsHolder(b), heat, false);
        }

        @Override
        public String getName() {
            return "Data Maps - hardscrabble metallurgy";
        }
    }

    /** Furnaces: pickaxe or shovel (dom-F-03); TNT-proof metal ores (EF-MET-10); {@code c:} metal block tags. Called by MiteBlockTags. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.MINEABLE_WITH_SHOVEL).add(key(Metallurgy.CLAY_FURNACE.get()));
        var pickaxe = tag.apply(BlockTags.MINEABLE_WITH_PICKAXE);
        Metallurgy.FURNACES.stream().filter(b -> b != Metallurgy.CLAY_FURNACE).forEach(b -> pickaxe.add(key(b.get())));
        tag.apply(Metallurgy.SPARED_BY_TNT).addTag(cBlock("ores/iron")).addTag(cBlock("ores/copper")).addTag(cBlock("ores/gold"));
        for (var o : WorldgenContent.ORES) {
            TagKey<Block> ores = cBlock("ores/" + o.metal().id());
            tag.apply(ores).add(key(o.stone().get())).add(key(o.deepslate().get()));
            tag.apply(Tags.BlockItems.ORES.block()).addTag(ores);
            tag.apply(Metallurgy.SPARED_BY_TNT).addTag(ores);
        }
        for (var s : WorldgenContent.STORAGE) {
            TagKey<Block> storage = cBlock("storage_blocks/" + s.metal().id());
            tag.apply(storage).add(key(s.block().get()));
            tag.apply(Tags.BlockItems.STORAGE_BLOCKS.block()).addTag(storage);
        }
    }

    /** {@code c:ores|raw_materials|ingots|nuggets/<metal>} for the MITE metals (dom-F-08). Called by MiteItemTags. */
    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        for (var o : WorldgenContent.ORES) {
            TagKey<Item> ores = cItem("ores/" + o.metal().id());
            tag.apply(ores).add(itemKey(o.stone().get().asItem())).add(itemKey(o.deepslate().get().asItem()));
            tag.apply(Tags.BlockItems.ORES.item()).addTag(ores);
            tag.apply(Tags.Items.RAW_MATERIALS).addTag(add(tag, "raw_materials/" + o.metal().id(), o.raw().asItem()));
        }
        for (var s : WorldgenContent.STORAGE) {
            String m = s.metal().id();
            tag.apply(Tags.Items.INGOTS).addTag(add(tag, "ingots/" + m, s.ingot().asItem()));
            tag.apply(Tags.Items.NUGGETS).addTag(add(tag, "nuggets/" + m, s.nugget().asItem()));
        }
    }

    private static TagKey<Item> add(Function<TagKey<Item>, TagAppender<Item>> tag, String path, Item item) {
        TagKey<Item> key = cItem(path);
        tag.apply(key).add(itemKey(item));
        return key;
    }

    private static TagKey<Block> cBlock(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Item> cItem(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static ResourceKey<Block> key(Block b) {
        return BuiltInRegistries.BLOCK.getResourceKey(b).orElseThrow();
    }


    /** Burn times (EF-MET-06) through the vanilla {@code context_int_provider} entries read by the {@code cooking_fuel} component. */
    public static void fuelTimes(BootstrapContext<ContextIntProvider> context) {
        context.register(Metallurgy.TIME_PAPER, cooking(context, 50));
        // Wooden items burn 200 ticks (vanilla: 150 to 1200).
        for (var key : List.of(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS, ContextIntProviders.COOKING_TIME_WOOD_SLABS,
                ContextIntProviders.COOKING_TIME_WOOD_ITEMS_SMALL, ContextIntProviders.COOKING_TIME_HANGING_SIGNS, ContextIntProviders.COOKING_TIME_BOATS)) {
            context.register(key, cooking(context, 200));
        }
        // [HORS-MITE] bamboo and scaffolding burn like a stick, dried kelp block like charcoal.
        context.register(ContextIntProviders.COOKING_TIME_BAMBOO, cooking(context, 100));
        context.register(ContextIntProviders.COOKING_TIME_DRIED_KELP_BLOCK, cooking(context, 1600));
    }

    /** Same shape as the vanilla entries: halved in fast furnaces (smoker, blast furnace). */
    private static ContextIntProvider cooking(BootstrapContext<ContextIntProvider> context, int ticks) {
        HolderGetter<ContextIntProvider> providers = context.lookup(Registries.CONTEXT_INT_PROVIDER);
        HolderGetter<LootItemCondition> predicates = context.lookup(Registries.PREDICATE);
        var divisor = new ConditionalValue(predicates.getOrThrow(LootPredicates.FAST_FURNACE),
                providers.getOrThrow(ContextIntProviders.COOKING_FAST_BURN_TIME_REDUCTION_FACTOR),
                providers.getOrThrow(ContextIntProviders.COOKING_NORMAL_BURN_TIME_REDUCTION_FACTOR));
        return ContextIntProviders.div(ContextIntProviders.exactly(ticks), Holder.direct(divisor)).value();
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK);

    private static final class Loot extends BlockLootSubProvider {
        Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            Metallurgy.FURNACES.forEach(b -> add(b.get(), this::createNameableBlockEntityTable));
            // F-2 / EF-MET-03: broken glass leaves glass shards (silk touch keeps the block) [INFÉRÉ counts: 1-4 per block, 0-2 per pane].
            stained(Blocks.GLASS, "").forEach(b -> add(b, shards(b, MaterialItems.GLASS_SHARD.get(), 1, 4)));
            stained(Blocks.GLASS_PANE, "_pane").forEach(b -> add(b, shards(b, MaterialItems.GLASS_SHARD.get(), 0, 2)));
            // Quartz shards: nether quartz ore breaks into 4-9 shards, Fortune ineffective like every MITE ore (EF-MET-01) [INFÉRÉ].
            add(Blocks.NETHER_QUARTZ_ORE, shards(Blocks.NETHER_QUARTZ_ORE, MaterialItems.QUARTZ_SHARD.get(), 4, 9));
        }

        private net.minecraft.world.level.storage.loot.LootTable.Builder shards(Block block, Item shard, int min, int max) {
            return createSilkTouchDispatchTable(block, applyExplosionDecay(block, net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(shard)
                    .apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Stream.of(Metallurgy.FURNACES.stream().map(b -> (Block) b.get()), stained(Blocks.GLASS, "").stream(), stained(Blocks.GLASS_PANE, "_pane").stream(),
                    Stream.of(Blocks.NETHER_QUARTZ_ORE)).flatMap(s -> s).toList();
        }
    }

    /** Glass (or pane) and its 16 stained variants {@code <color>_stained_glass<suffix>} (tinted glass keeps its vanilla drop). */
    private static List<Block> stained(Block base, String suffix) {
        return Stream.concat(Stream.of(base), java.util.Arrays.stream(net.minecraft.world.item.DyeColor.values())
                .map(c -> BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(c.getSerializedName() + "_stained_glass" + suffix)))).toList();
    }

    /** Registered for the mite and minecraft namespaces: vanilla cooking recipes are regenerated with the MITE XP. */
    public static final class Recipes extends RecipeProvider {
        /** F-1 / §4.3 n°11: the blast furnace is faster (half the time, like vanilla), while it burns its fuel twice as fast. */
        public static final int BLAST_TIME = 100;

        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            // EF-MET-05 furnaces. The vanilla furnace keeps its recipe.
            shaped(RecipeCategory.DECORATIONS, Metallurgy.CLAY_FURNACE.get()).pattern("##").pattern("##").define('#', Items.CLAY)
                    .unlockedBy("has_clay_ball", has(Items.CLAY_BALL)).save(output);
            ring(Metallurgy.LARGE_CLAY_FURNACE.get(), Items.TERRACOTTA);
            ring(Metallurgy.SANDSTONE_FURNACE.get(), Items.SANDSTONE);
            ring(Metallurgy.OBSIDIAN_FURNACE.get(), Items.OBSIDIAN);
            ring(Metallurgy.NETHERRACK_FURNACE.get(), Items.NETHERRACK);
            // Arbitrage n°11 (audit 2 m5): the campfire needs charcoal (vanilla #coals, disabled by a file), so it is no day-1 cooker.
            shaped(RecipeCategory.DECORATIONS, Items.CAMPFIRE).pattern(" S ").pattern("SCS").pattern("LLL").define('S', Items.STICK)
                    .define('C', Items.CHARCOAL).define('L', ItemTags.LOGS).unlockedBy("has_charcoal", has(Items.CHARCOAL)).save(output, "hardscrabble:campfire");

            // EF-MET-07 smelting XP. Blasting stays out for mithril and adamantium: the blast furnace only reaches heat 2.
            ingot(MaterialItems.SILVER_INGOT.get(), 15, true, "hardscrabble:", MaterialItems.RAW_SILVER.get(), SILVER_ORE.stone().get(), SILVER_ORE.deepslate().get());
            ingot(MaterialItems.MITHRIL_INGOT.get(), 40, false, "hardscrabble:", MaterialItems.RAW_MITHRIL.get(), MITHRIL_ORE.stone().get(), MITHRIL_ORE.deepslate().get());
            ingot(MaterialItems.ADAMANTIUM_INGOT.get(), 100, false, "hardscrabble:", MaterialItems.RAW_ADAMANTIUM.get(), ADAMANTIUM_ORE.stone().get(), ADAMANTIUM_ORE.deepslate().get());
            ingot(Items.COPPER_INGOT, 10, true, "", Items.RAW_COPPER, Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE);
            ingot(Items.IRON_INGOT, 10, true, "", Items.RAW_IRON, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE);
            ingot(Items.GOLD_INGOT, 20, true, "", Items.RAW_GOLD, Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE, Items.NETHER_GOLD_ORE);
            ingot(Items.DIAMOND, 30, true, "", Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE);
            ingot(Items.EMERALD, 20, true, "", Items.EMERALD_ORE, Items.DEEPSLATE_EMERALD_ORE);
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(Items.NETHER_QUARTZ_ORE), RecipeCategory.MISC, CookingBookCategory.MISC, Items.QUARTZ, 10, 200)
                    .unlockedBy("has_nether_quartz_ore", has(Items.NETHER_QUARTZ_ORE)).save(output);
            SimpleCookingRecipeBuilder.blasting(Ingredient.of(Items.NETHER_QUARTZ_ORE), RecipeCategory.MISC, CookingBookCategory.MISC, Items.QUARTZ, 10, BLAST_TIME)
                    .unlockedBy("has_nether_quartz_ore", has(Items.NETHER_QUARTZ_ORE)).save(output, getBlastingRecipeName(Items.QUARTZ));

            // EF-MET-07 cooking XP ("big fish" = salmon); rabbit, kelp and other foods keep the vanilla value.
            food(Items.PORKCHOP, Items.COOKED_PORKCHOP, 3);
            food(Items.COD, Items.COOKED_COD, 3);
            food(Items.BEEF, Items.COOKED_BEEF, 4);
            food(Items.CHICKEN, Items.COOKED_CHICKEN, 3);
            food(Items.MUTTON, Items.COOKED_MUTTON, 2);
            food(Items.SALMON, Items.COOKED_SALMON, 4);
            food(Items.POTATO, Items.BAKED_POTATO, 0);
        }

        private void ring(ItemLike furnace, ItemLike material) {
            shaped(RecipeCategory.DECORATIONS, furnace).pattern("###").pattern("# #").pattern("###").define('#', material)
                    .unlockedBy(getHasName(material), has(material)).save(output);
        }

        /** Same ids as vanilla ({@code <result>_from_smelting_<input>}), so vanilla recipes are replaced. */
        private void ingot(ItemLike result, float xp, boolean blasting, String namespace, ItemLike... inputs) {
            for (ItemLike input : inputs) {
                SimpleCookingRecipeBuilder.smelting(Ingredient.of(input), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 200)
                        .group(getItemName(result)).unlockedBy(getHasName(input), has(input))
                        .save(output, namespace + getItemName(result) + "_from_smelting_" + getItemName(input));
                if (blasting) SimpleCookingRecipeBuilder.blasting(Ingredient.of(input), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, BLAST_TIME)
                        .group(getItemName(result)).unlockedBy(getHasName(input), has(input))
                        .save(output, namespace + getItemName(result) + "_from_blasting_" + getItemName(input));
            }
        }

        private void food(ItemLike raw, ItemLike cooked, float xp) {
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(raw), RecipeCategory.FOOD, CookingBookCategory.FOOD, cooked, xp, 200)
                    .unlockedBy(getHasName(raw), has(raw)).save(output);
            simpleCookingRecipe("smoking", SmokingRecipe::new, 200, raw, cooked, xp);
            simpleCookingRecipe("campfire_cooking", CampfireCookingRecipe::new, 600, raw, cooked, xp);
        }
    }
}
