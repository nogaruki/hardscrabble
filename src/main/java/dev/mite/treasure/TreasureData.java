package dev.mite.treasure;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.material.MaterialItems;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Treasure datagen: models, recipes, block loot and tag, and the chest loot pools used by the dungeon tables. */
public final class TreasureData {
    private TreasureData() {}

    /** The vanilla enchanting table model with our own textures. */
    private static final ModelTemplate ENCHANTING_TABLE = new ModelTemplate(Optional.of(Identifier.withDefaultNamespace("block/enchanting_table")),
            Optional.empty(), TextureSlot.PARTICLE, TextureSlot.TOP, TextureSlot.SIDE, TextureSlot.BOTTOM);

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            Block table = TreasureContent.EMERALD_ENCHANTING_TABLE.get();
            var bottom = TextureMapping.getBlockTexture(table, "_bottom");
            Identifier model = ENCHANTING_TABLE.create(table, new TextureMapping().put(TextureSlot.PARTICLE, bottom).put(TextureSlot.BOTTOM, bottom)
                    .put(TextureSlot.TOP, TextureMapping.getBlockTexture(table, "_top")).put(TextureSlot.SIDE, TextureMapping.getBlockTexture(table, "_side")), blocks.modelOutput);
            blocks.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(table, BlockModelGenerators.plainVariant(model)));
            items.generateFlatItem(TreasureContent.SILVER_HORSE_ARMOR.get(), ModelTemplates.FLAT_ITEM);
            TreasureContent.COINS.values().forEach(c -> items.generateFlatItem(c.get(), ModelTemplates.FLAT_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.of(TreasureContent.EMERALD_ENCHANTING_TABLE);
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return TreasureContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble treasure";
        }
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            // EF-CBT-12: no documented recipe; the vanilla leather horse armor shape in silver ingots [INFÉRÉ].
            Item silver = MaterialItems.SILVER_INGOT.get();
            shaped(RecipeCategory.COMBAT, TreasureContent.SILVER_HORSE_ARMOR.get()).pattern("X X").pattern("XXX").pattern("X X")
                    .define('X', silver).unlockedBy("has_silver", has(silver)).save(output);
            // EF-ENC-03: the vanilla table with emeralds instead of diamonds.
            shaped(RecipeCategory.DECORATIONS, TreasureContent.EMERALD_ENCHANTING_TABLE.get()).pattern(" B ").pattern("E#E").pattern("###")
                    .define('B', Items.BOOK).define('E', Items.EMERALD).define('#', Items.OBSIDIAN).unlockedBy("has_emerald", has(Items.EMERALD)).save(output);
            // EF-ENC-07 (analysis/02): 1 nugget <-> 1 coin; the minting XP cost is charged by TreasureContent.
            TreasureContent.COINS.forEach((m, coin) -> {
                Item nugget = MaterialItems.nugget(m);
                shapeless(RecipeCategory.MISC, coin.get()).requires(nugget).unlockedBy("has_nugget", has(nugget)).save(output);
                shapeless(RecipeCategory.MISC, nugget).requires(coin.get()).unlockedBy("has_coin", has(coin.get()))
                        .save(output, MiteMod.id(m.id() + "_nugget_from_coin").toString());
            });
        }
    }

    public static final class Loot extends BlockLootSubProvider {
        public Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            dropSelf(TreasureContent.EMERALD_ENCHANTING_TABLE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Set.of(TreasureContent.EMERALD_ENCHANTING_TABLE.get());
        }
    }

    /** Called by MiteBlockTags. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(TreasureContent.EMERALD_ENCHANTING_TABLE.unwrapKey().orElseThrow());
    }

    /** One coin entry for a chest pool [weights INFÉRÉ]. */
    /** A chest loot entry: {@code item}, weight, count between min and max. */
    public static UniformContainerBase.Builder<?> item(Item item, int weight, int min, int max) {
        return LootItem.lootTableItem(item).setWeight(weight).apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)));
    }

    public static UniformContainerBase.Builder<?> coin(Material m, int weight, int min, int max) {
        return LootItem.lootTableItem(TreasureContent.COINS.get(m).get()).setWeight(weight).apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)));
    }

    /** P05: one roll, a Funzang book in one chest out of four [INFÉRÉ]. The only definition of a Funzang book (see LoreBooks.isLore). */
    public static LootPool.Builder lorePool() {
        LootPool.Builder pool = LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(EmptyLootItem.emptyItem().setWeight(3 * LoreBooks.TEXT.length));
        for (int i = 0; i < LoreBooks.TEXT.length; i++) {
            pool.add(LootItem.lootTableItem(Items.WRITTEN_BOOK)
                    .apply(SetComponentsFunction.setComponent(DataComponents.WRITTEN_BOOK_CONTENT, LoreBooks.content(i)))
                    .apply(SetComponentsFunction.setComponent(DataComponents.ITEM_NAME, Component.translatable(LoreBooks.titleKey(i)))));
        }
        return pool;
    }
}
