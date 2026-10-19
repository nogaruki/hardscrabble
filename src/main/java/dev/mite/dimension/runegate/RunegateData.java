package dev.mite.dimension.runegate;

import dev.mite.MiteMod;
import dev.mite.material.MaterialItems;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Runestone datagen (EF-DIM-06, EF-CRA-06): models, obsidian + 4 nuggets recipes, self drop. */
public final class RunegateData {
    private RunegateData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            Runegate.RUNESTONES.forEach(b -> blocks.createTrivialCube(b.get()));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Runegate.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return Runegate.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble runegate";
        }
    }

    /** EF-CRA-06: obsidian surrounded by 4 mithril or adamantium nuggets. */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            runestone(Runegate.MITHRIL_RUNESTONE.get(), MaterialItems.MITHRIL_NUGGET.get());
            runestone(Runegate.ADAMANTIUM_RUNESTONE.get(), MaterialItems.ADAMANTIUM_NUGGET.get());
        }

        private void runestone(Block stone, Item nugget) {
            shaped(RecipeCategory.DECORATIONS, stone).pattern(" N ").pattern("NON").pattern(" N ").define('N', nugget).define('O', Items.OBSIDIAN)
                    .unlockedBy("has_nugget", has(nugget)).save(output);
        }
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(context -> new BlockLootSubProvider(
            Set.of(), FeatureFlags.REGISTRY.allFlags(), context) {
        @Override
        protected void generate() {
            Runegate.RUNESTONES.forEach(b -> dropSelf(b.get()));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Runegate.BLOCKS.getEntries().stream().map(Holder::value).toList();
        }
    }, LootContextParamSets.BLOCK);
}
