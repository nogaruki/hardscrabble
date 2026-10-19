package dev.mite.material;

import dev.mite.MiteMod;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** Datagen for shared materials: item models and nugget/shard conversion recipes (EF-MET-03). */
public final class MaterialData {
    private MaterialData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            MaterialItems.ITEMS.getEntries().forEach(i -> items.generateFlatItem(i.get(), ModelTemplates.FLAT_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return MaterialItems.ITEMS.getEntries().stream();
        }
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            // 9 nuggets <-> 1 ingot; vanilla already covers iron, gold and copper.
            for (var s : dev.mite.worldgen.WorldgenContent.STORAGE) {
                String ingot = getItemName(s.ingot()), nugget = getItemName(s.nugget());
                nineBlockStorageRecipes(RecipeCategory.MISC, s.nugget(), RecipeCategory.MISC, s.ingot(),
                        "hardscrabble:" + ingot + "_from_nuggets", ingot, "hardscrabble:" + nugget + "_from_ingot", nugget);
            }
            // 4 flint shards = 1 flint; 9 shards = 1 unit for obsidian, emerald, diamond and quartz.
            shapeless(RecipeCategory.MISC, Items.FLINT).requires(MaterialItems.FLINT_SHARD, 4)
                    .unlockedBy("has_flint_shard", has(MaterialItems.FLINT_SHARD)).save(output, "hardscrabble:flint_from_shards");
            nineShards(MaterialItems.OBSIDIAN_SHARD.get(), Items.OBSIDIAN);
            nineShards(MaterialItems.EMERALD_SHARD.get(), Items.EMERALD);
            nineShards(MaterialItems.DIAMOND_SHARD.get(), Items.DIAMOND);
            nineShards(MaterialItems.QUARTZ_SHARD.get(), Items.QUARTZ);
            shapeless(RecipeCategory.MISC, Items.GLASS_PANE).requires(MaterialItems.GLASS_SHARD, 6)
                    .unlockedBy("has_glass_shard", has(MaterialItems.GLASS_SHARD)).save(output, "hardscrabble:glass_pane_from_shards");
            nineShards(MaterialItems.GLASS_SHARD.get(), Items.GLASS); // dom-E-08, EF-CRA-06 [INFÉRÉ 9 like the gem shards]
        }

        private void nineShards(ItemLike shard, ItemLike result) {
            shapeless(RecipeCategory.MISC, result).requires(shard, 9)
                    .unlockedBy("has_shard", has(shard)).save(output, "hardscrabble:" + getItemName(result) + "_from_shards");
        }
    }
}
