package dev.mite.metabolism;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.DataMapProvider;
import dev.mite.MiteMod;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;

/** Generates {@code hardscrabble:nutrition}: satiety / nutrition / sugar / protein / phyto per food item. */
public final class MetabolismData extends DataMapProvider {
    private Builder<Nutrition, Item> b;

    public MetabolismData(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    private void put(Item item, int satiety, int nutrition, int sugar, boolean protein, boolean phyto) {
        b.add(BuiltInRegistries.ITEM.getKey(item), new Nutrition(satiety, nutrition, sugar, protein, phyto), false);
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        b = builder(MetabolismContent.NUTRITION);
        boolean P = true, V = true, o = false;
        // EF-NUT-08 (R196 table). Land meat counts as protein (Q-24).
        put(Items.APPLE, 2, 1, 1000, o, V);
        put(Items.SWEET_BERRIES, 1, 1, 1000, o, V);
        put(Items.MELON_SLICE, 1, 1, 1000, o, V);
        put(Items.BREAD, 8, 2, 0, o, o);
        put(Items.COOKIE, 3, 1, 250, o, o);
        put(Items.SUGAR, 1, 0, 1000, o, o);
        put(Items.PUMPKIN_PIE, 10, 6, 1000, P, V);
        put(Items.PORKCHOP, 4, 4, 0, P, o);
        put(Items.COOKED_PORKCHOP, 8, 8, 0, P, o);
        put(Items.BEEF, 5, 5, 0, P, o);
        put(Items.COOKED_BEEF, 10, 10, 0, P, o);
        put(Items.CHICKEN, 3, 3, 0, P, o);
        put(Items.COOKED_CHICKEN, 6, 6, 0, P, o);
        put(Items.MUTTON, 3, 3, 0, P, o);
        put(Items.COOKED_MUTTON, 6, 6, 0, P, o);
        put(Items.COD, 3, 3, 0, P, o);
        put(Items.COOKED_COD, 6, 6, 0, P, o);
        put(Items.SALMON, 5, 5, 0, P, o);
        put(Items.COOKED_SALMON, 10, 10, 0, P, o);
        put(Items.ROTTEN_FLESH, 2, 1, 0, o, o);
        put(Items.SPIDER_EYE, 0, 1, 0, P, o);
        put(Items.WHEAT_SEEDS, 1, 0, 0, o, o);
        put(Items.PUMPKIN_SEEDS, 1, 2, 0, o, o);
        put(Items.MELON_SEEDS, 1, 1, 0, o, o);
        put(Items.NETHER_WART, 1, 1, 0, o, o);
        put(Items.CARROT, 1, 2, 0, o, o);
        put(Items.GOLDEN_CARROT, 1, 2, 0, o, V);
        put(Items.POTATO, 3, 1, 0, o, o);
        put(Items.BAKED_POTATO, 6, 2, 0, o, o);
        put(Items.POISONOUS_POTATO, 2, 0, 0, o, o);
        put(Items.GOLDEN_APPLE, 2, 1, 0, o, o);
        put(Items.MUSHROOM_STEW, 2, 4, 0, o, o);
        // EF-NUT-09 modern foods, by analogy [HORS-MITE].
        put(Items.ENCHANTED_GOLDEN_APPLE, 2, 1, 0, o, o);
        put(Items.BEETROOT, 1, 2, 0, o, V);
        put(Items.BEETROOT_SOUP, 6, 6, 0, o, V);
        put(Items.RABBIT, 3, 3, 0, P, o);
        put(Items.COOKED_RABBIT, 6, 6, 0, P, o);
        put(Items.RABBIT_STEW, 10, 10, 0, P, V);
        put(Items.TROPICAL_FISH, 1, 1, 0, P, o);
        put(Items.PUFFERFISH, 1, 1, 0, P, o);
        // 26.3 fish buckets carry their fish's food component: same values as the raw fish.
        put(Items.COD_BUCKET, 3, 3, 0, P, o);
        put(Items.SALMON_BUCKET, 5, 5, 0, P, o);
        put(Items.TROPICAL_FISH_BUCKET, 1, 1, 0, P, o);
        put(Items.PUFFERFISH_BUCKET, 1, 1, 0, P, o);
        put(Items.DRIED_KELP, 1, 1, 0, o, V);
        put(Items.HONEY_BOTTLE, 3, 1, 2000, o, o);
        put(Items.GLOW_BERRIES, 1, 1, 1000, o, V);
        put(Items.CHORUS_FRUIT, 2, 1, 1000, o, V);
        put(Items.SUSPICIOUS_STEW, 2, 4, 0, o, o);
        // EF-NUT-08 / EF-NUT-12 bowl of milk.
        put(MetabolismContent.MILK_BOWL.get(), 0, 1, 0, P, o);
        // farming: EF-NUT-08 values of the new foods and dishes.
        dev.mite.farming.FarmingContent.FOODS.forEach((item, n) -> b.add(item.getId(), n, false));
        // equipment: EF-NUT-08 raw / cooked worm.
        put(dev.mite.equipment.EquipmentContent.WORM.get(), 0, 1, 0, o, o);
        put(dev.mite.equipment.EquipmentContent.COOKED_WORM.get(), 1, 1, 0, o, o);
    }

    @Override
    public String getName() {
        return "Data maps - hardscrabble nutrition";
    }

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            MetabolismContent.ITEMS.getEntries().forEach(i -> items.generateFlatItem(i.get(), ModelTemplates.FLAT_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return MetabolismContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Metabolism item models";
        }
    }

    /** EF-NUT-12: a water bucket fills 4 bowls (the bucket comes back as its crafting remainder). */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            // C-3: any water bucket (c:buckets/water: iron and the 6 metal buckets), each giving its empty bucket back.
            shapeless(RecipeCategory.FOOD, MetabolismContent.WATER_BOWL.get(), 4).requires(net.neoforged.neoforge.common.Tags.Items.BUCKETS_WATER).requires(Items.BOWL, 4)
                    .unlockedBy("has_bowl", has(Items.BOWL)).save(output);
        }
    }
}
