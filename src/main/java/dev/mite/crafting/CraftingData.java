package dev.mite.crafting;

import static dev.mite.datagen.MiteItemTags.itemKey;

import java.util.function.Function;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;
import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.material.MaterialItems;
import dev.mite.worldgen.WorldgenContent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

/** Crafting datagen: workbench models, loot and recipes, difficulty and material data maps, inventory tag. */
public final class CraftingData {
    private CraftingData() {}

    /** One row per metal: ingot, nugget, storage block (null when the block does not exist yet). */
    private record Metal(Material material, ItemLike ingot, ItemLike nugget, ItemLike block) {}

    /** The vanilla metals, then the MITE ones from {@link WorldgenContent#STORAGE}, in material order. */
    private static List<Metal> metals() {
        return Stream.concat(Stream.of(
                new Metal(Material.COPPER, Items.COPPER_INGOT, Items.COPPER_NUGGET, Items.COPPER_BLOCK.weathering().unaffected()),
                new Metal(Material.GOLD, Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK),
                new Metal(Material.IRON, Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK)),
                WorldgenContent.STORAGE.stream().map(s -> new Metal(s.metal(), s.ingot(), s.nugget(), s.block())))
                .sorted(java.util.Comparator.comparing(Metal::material)).toList();
    }

    private static ItemLike ingotOf(Workbench bench) {
        return metals().stream().filter(m -> m.material() == bench.material).findFirst().orElseThrow().ingot();
    }

    private static Holder<Item> holder(ItemLike item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item.asItem());
    }

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            CraftingContent.workbenches().forEach(b -> blocks.createTrivialCube(b.get()));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return CraftingContent.workbenches().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return CraftingContent.workbenches().stream().map(b -> holder(b.get()));
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble crafting";
        }
    }

    /** EF-CRA-04 difficulties and the metal of each ingot, nugget and storage block. */
    public static final class DataMaps extends DataMapProvider {
        public DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void gather(HolderLookup.Provider provider) {
            var difficulty = builder(CraftingContent.CRAFT_DIFFICULTY);
            var material = builder(CraftingContent.MATERIAL);
            for (Metal m : metals()) {
                float ingot = m.material().ingotDifficulty();
                difficulty.add(holder(m.ingot()), ingot, false).add(holder(m.nugget()), ingot / 9, false);
                material.add(holder(m.ingot()), m.material(), false).add(holder(m.nugget()), m.material(), false);
                if (m.block() != null) {
                    difficulty.add(holder(m.block()), ingot * 9, false);
                    material.add(holder(m.block()), m.material(), false);
                }
            }
            // dom-E-02: a chain (4 nuggets) carries its metal, so chainmail needs that metal's bench.
            dev.mite.combat.CombatContent.CHAINS.forEach((m, chain) -> {
                difficulty.add(holder(chain.get()), m.ingotDifficulty() * 4 / 9, false);
                material.add(holder(chain.get()), m, false);
            });
            // Gems = multiplier x 100, shards = gem / 9; obsidian counts as a durability-2 gem.
            gem(difficulty, Items.DIAMOND, MaterialItems.DIAMOND_SHARD.get(), 1600);
            gem(difficulty, Items.EMERALD, MaterialItems.EMERALD_SHARD.get(), 800);
            gem(difficulty, Items.QUARTZ, MaterialItems.QUARTZ_SHARD.get(), 900);
            gem(difficulty, Items.OBSIDIAN, MaterialItems.OBSIDIAN_SHARD.get(), Material.OBSIDIAN.ingotDifficulty());
            difficulty.add(holder(Items.FLINT), Material.FLINT.ingotDifficulty(), false)
                    .add(holder(MaterialItems.FLINT_SHARD.get()), 25f, false)
                    .add(holder(Items.STICK), 25f, false)
                    .add(holder(Items.WHEAT), 25f, false)
                    .add(holder(Items.STRING), 25f, false)
                    .add(holder(MaterialItems.SINEW.get()), 50f, false)
                    .add(holder(Items.SUGAR_CANE), 100f, false)
                    // farming: cheese costs 6400 (R196), from 4 bowls or 1 bucket of milk of any metal (changelog l.246).
                    .add(holder(dev.mite.metabolism.MetabolismContent.MILK_BOWL.get()), 1600f, false).add(net.neoforged.neoforge.common.Tags.Items.BUCKETS_MILK, 6400f, false);
        }

        private static void gem(Builder<Float, Item> builder, Item gem, Item shard, float value) {
            builder.add(holder(gem), value, false).add(holder(shard), value / 9, false);
        }

        @Override
        public String getName() {
            return "Data Maps - hardscrabble crafting";
        }
    }

    /** EF-CRA-03 2x2 results (no planks: they need a bench since R149), EF-OUT-12 lashings. Called by MiteItemTags (the only item tags provider). */
    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        var main = tag.apply(CraftingContent.INVENTORY_CRAFTABLE)
                .addOptional(ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, MiteMod.id("flint_hatchet")))
                .addOptional(ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, MiteMod.id("clay_furnace")))
                // Knives: disassembling a flint or obsidian bench gives its knife back (EF-CRA-02 CA-3).
                .addOptional(ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, MiteMod.id("flint_knife")))
                .addOptional(ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, MiteMod.id("obsidian_knife")))
                // Clay: the vanilla 4 balls -> block recipe, so the clay furnace needs no bench (EF-MET-05).
                .add(itemKey(Items.CLAY), itemKey(Items.STICK), itemKey(Items.CRAFTING_TABLE), itemKey(Items.FLINT), itemKey(Items.OBSIDIAN), itemKey(Items.EMERALD),
                        itemKey(Items.DIAMOND), itemKey(Items.QUARTZ), itemKey(Items.GLASS_PANE), itemKey(MaterialItems.SINEW.get()));
        tag.apply(dev.mite.harvest.HarvestContent.LASHINGS).add(itemKey(Items.STRING), itemKey(MaterialItems.SINEW.get())); // EF-OUT-12
        // X8 / EF-PRG-01: only the obsidian bench (like the flint one) is made by hand; metal benches need the previous tier's bench.
        CraftingContent.workbenches().stream().filter(b -> b.get().bench.prerequisite() == null).forEach(b -> main.add(itemKey(b.get())));
        for (Metal m : metals()) main.add(itemKey(m.ingot()), itemKey(m.nugget()));
    }

    /** F-6: the MITE workbenches are wooden blocks mined with an axe, like the vanilla crafting table (flint bench). Called by MiteBlockTags. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        var axe = tag.apply(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE);
        CraftingContent.workbenches().forEach(b -> axe.add(BuiltInRegistries.BLOCK.getResourceKey(b.get()).orElseThrow()));
    }


    public static final class Loot extends BlockLootSubProvider {
        public Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            CraftingContent.workbenches().forEach(b -> dropSelf(b.get()));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return CraftingContent.workbenches().stream().map(b -> (Block) b.get()).toList();
        }
    }

    /**
     * EF-CRA-02 workbench recipes (the crafting table becomes the flint workbench) and EF-CRA-06 material recipes.
     * The vanilla crafting_table and saddle recipes are disabled by static files in data/minecraft/recipe (datagen cannot
     * register a vanilla recipe id twice).
     */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            shaped(RecipeCategory.DECORATIONS, Items.CRAFTING_TABLE).pattern("FS").pattern("/L")
                    .define('F', Items.FLINT).define('S', dev.mite.harvest.HarvestContent.LASHINGS).define('/', Items.STICK).define('L', ItemTags.LOGS)
                    .unlockedBy("has_flint", has(Items.FLINT)).save(output, "hardscrabble:crafting_table");
            knifeBench(Items.CRAFTING_TABLE, "flint", "crafting_table");
            knifeBench(CraftingContent.WORKBENCHES.get(Workbench.OBSIDIAN).get(), "obsidian", Workbench.OBSIDIAN.blockName());
            for (var e : CraftingContent.WORKBENCHES.entrySet()) {
                Workbench bench = e.getKey();
                var recipe = shaped(RecipeCategory.DECORATIONS, e.getValue().get()).define('/', Items.STICK);
                if (bench == Workbench.OBSIDIAN) {
                    recipe.pattern("OS").pattern("/L").define('O', Items.OBSIDIAN).define('S', dev.mite.harvest.HarvestContent.LASHINGS).define('L', ItemTags.LOGS)
                            .unlockedBy("has_obsidian", has(Items.OBSIDIAN));
                } else {
                    ItemLike ingot = ingotOf(bench);
                    recipe.pattern("IH").pattern("/P").define('I', ingot).define('H', Items.LEATHER).define('P', ItemTags.PLANKS)
                            .unlockedBy("has_ingot", has(ingot));
                }
                recipe.save(output);
            }
            // EF-CRA-06 recipes whose items already exist.
            shapeless(RecipeCategory.MISC, Items.WHEAT_SEEDS, 2).requires(Items.WHEAT)
                    .unlockedBy("has_wheat", has(Items.WHEAT)).save(output, "hardscrabble:wheat_seeds_from_wheat");
            // Changelog l.771 (R119): 4 bricks make hardened clay; the vanilla 2x2 bricks recipe is replaced by 8 bricks + sand -> 2
            // (l.772-773, static files data/minecraft/recipe/bricks.json and nether_bricks.json).
            shaped(RecipeCategory.BUILDING_BLOCKS, Items.TERRACOTTA).pattern("BB").pattern("BB").define('B', Items.BRICK)
                    .unlockedBy("has_brick", has(Items.BRICK)).save(output, "hardscrabble:terracotta_from_bricks");
            // X11, changelog l.778 / l.331: leather armor repaired with sinew or string.
            net.minecraft.data.recipes.SpecialRecipeBuilder.special(() -> LeatherRepairRecipe.INSTANCE).save(output, "hardscrabble:leather_armor_repair");
            shaped(RecipeCategory.MISC, Items.IRON_CHAIN).pattern(" N ").pattern("N N").pattern(" N ").define('N', Items.IRON_NUGGET)
                    .unlockedBy("has_iron_nugget", has(Items.IRON_NUGGET)).save(output, "hardscrabble:iron_chain_from_nuggets");
            shapeless(RecipeCategory.MISC, MaterialItems.SINEW.get(), 4).requires(Items.LEATHER)
                    .unlockedBy("has_leather", has(Items.LEATHER)).save(output);
            shapeless(RecipeCategory.TRANSPORTATION, Items.SADDLE).requires(Items.LEATHER, 5).requires(Items.IRON_NUGGET, 2)
                    .unlockedBy("has_leather", has(Items.LEATHER)).save(output, "hardscrabble:saddle");
        }

        /** EF-CRA-02: a knife on a log makes its bench, and the bench comes apart into the knife again (CA-3). */
        private void knifeBench(ItemLike bench, String material, String benchName) {
            Item knife = BuiltInRegistries.ITEM.getValue(MiteMod.id(material + "_knife"));
            shapeless(RecipeCategory.DECORATIONS, bench).requires(knife).requires(ItemTags.LOGS)
                    .unlockedBy("has_knife", has(knife)).save(output, "hardscrabble:" + benchName + "_from_knife");
            shapeless(RecipeCategory.TOOLS, knife).requires(bench)
                    .unlockedBy("has_bench", has(bench)).save(output, "hardscrabble:" + material + "_knife_from_" + benchName);
        }
    }
}
