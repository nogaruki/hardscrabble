package dev.mite.equipment;

import static dev.mite.datagen.MiteItemTags.itemKey;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.equipment.EquipmentContent.BucketSet;
import dev.mite.equipment.EquipmentContent.Rod;
import dev.mite.harvest.HarvestContent;
import dev.mite.material.MaterialItems;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.Tags;

/** Equipment datagen: models, recipes, door and bars loot, bucket / shears / rod / door tags. */
public final class EquipmentData {
    private EquipmentData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            EquipmentContent.DOORS.values().forEach(d -> blocks.createDoor(d.get()));
            EquipmentContent.BARS.values().forEach(b -> blocks.createBarsAndItem(b.get()));
            EquipmentContent.RODS.forEach(r -> items.generateFishingRod(r.item().get()));
            EquipmentContent.ITEMS.getEntries().stream().map(Holder::value).filter(i -> !(i instanceof BlockItem || i instanceof FishingRodItem))
                    .forEach(i -> items.generateFlatItem(i, ModelTemplates.FLAT_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return EquipmentContent.BLOCKS.getEntries().stream();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return EquipmentContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble equipment";
        }
    }

    /** The hook of a rod: a shard or a nugget (EF-OUT-13). */
    private static ItemLike hook(Material m) {
        return switch (m) {
            case FLINT -> MaterialItems.FLINT_SHARD.get();
            case OBSIDIAN -> MaterialItems.OBSIDIAN_SHARD.get();
            default -> MaterialItems.nugget(m);
        };
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            for (BucketSet s : EquipmentContent.BUCKETS.values()) {
                ItemLike ingot = MaterialItems.ingot(s.metal());
                String n = s.metal().id();
                // Bucket: 3 ingots in a V (EF-OUT-13); iron keeps the vanilla recipe.
                if (s.metal() != Material.IRON) shaped(RecipeCategory.TOOLS, s.empty().get()).pattern("X X").pattern(" X ").define('X', ingot)
                        .unlockedBy("has_ingot", has(ingot)).save(output);
                // analysis/02 §7: a bucket of stone is taken apart into the empty bucket.
                shapeless(RecipeCategory.TOOLS, s.empty().get()).requires(s.stone().get())
                        .unlockedBy("has_stone_bucket", has(s.stone().get())).save(output, "hardscrabble:" + n + "_bucket_from_stone_bucket");
                if (s.metal() == Material.COPPER || s.metal() == Material.IRON) continue;
                // H11 [INFÉRÉ: shapes and counts of the vanilla iron door and bars].
                Block door = EquipmentContent.DOORS.get(s.metal()).get(), bars = EquipmentContent.BARS.get(s.metal()).get();
                shaped(RecipeCategory.REDSTONE, door, 3).pattern("XX").pattern("XX").pattern("XX").define('X', ingot)
                        .unlockedBy("has_ingot", has(ingot)).save(output);
                shaped(RecipeCategory.DECORATIONS, bars, 16).pattern("XXX").pattern("XXX").define('X', ingot)
                        .unlockedBy("has_ingot", has(ingot)).save(output);
            }
            for (Rod r : EquipmentContent.RODS) {
                // Vanilla rod with the hook at the end of the line.
                shaped(RecipeCategory.TOOLS, r.item().get()).pattern("  #").pattern(" #S").pattern("#SH")
                        .define('#', Items.STICK).define('S', Items.STRING).define('H', hook(r.hook()))
                        .unlockedBy("has_hook", has(hook(r.hook()))).save(output);
            }
            // EF-MET-08: cooking a worm gives 0 XP.
            Ingredient worm = Ingredient.of(EquipmentContent.WORM.get());
            Item cooked = EquipmentContent.COOKED_WORM.get();
            SimpleCookingRecipeBuilder.smelting(worm, RecipeCategory.FOOD, CookingBookCategory.FOOD, cooked, 0, 200)
                    .unlockedBy("has_worm", has(EquipmentContent.WORM.get())).save(output);
            SimpleCookingRecipeBuilder.smoking(worm, RecipeCategory.FOOD, cooked, 0, 100)
                    .unlockedBy("has_worm", has(EquipmentContent.WORM.get())).save(output, "hardscrabble:cooked_worm_from_smoking");
            SimpleCookingRecipeBuilder.campfireCooking(worm, RecipeCategory.FOOD, cooked, 0, 600)
                    .unlockedBy("has_worm", has(EquipmentContent.WORM.get())).save(output, "hardscrabble:cooked_worm_from_campfire_cooking");
        }
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK);

    private static final class Loot extends BlockLootSubProvider {
        Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            EquipmentContent.DOORS.values().forEach(d -> add(d.get(), createDoorTable(d.get())));
            EquipmentContent.BARS.values().forEach(b -> dropSelf(b.get()));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return EquipmentContent.BLOCKS.getEntries().stream().map(Holder::value).collect(Collectors.toList());
        }
    }

    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        for (BucketSet s : EquipmentContent.BUCKETS.values()) {
            if (s.metal() == Material.IRON) continue;
            tag.apply(Tags.Items.BUCKETS_EMPTY).add(itemKey(s.empty().get()));
            tag.apply(Tags.Items.BUCKETS_WATER).add(itemKey(s.water().get()));
            tag.apply(Tags.Items.BUCKETS_LAVA).add(itemKey(s.lava().get()));
            tag.apply(Tags.Items.BUCKETS_MILK).add(itemKey(s.milk().get()));
        }
        HarvestContent.TOOLS.stream().filter(t -> t.info().type() == ToolType.SHEARS).forEach(t -> tag.apply(Tags.Items.TOOLS_SHEAR).add(itemKey(t.item().get())));
        for (Rod r : EquipmentContent.RODS) {
            for (TagKey<Item> t : java.util.List.of(Tags.Items.TOOLS_FISHING_ROD, ItemTags.FISHING_ENCHANTABLE, ItemTags.DURABILITY_ENCHANTABLE))
                tag.apply(t).add(itemKey(r.item().get()));
        }
    }

    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        EquipmentContent.BLOCKS.getEntries().forEach(b -> tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(b.unwrapKey().orElseThrow()));
        EquipmentContent.DOORS.values().forEach(d -> tag.apply(BlockTags.DOORS).add(d.unwrapKey().orElseThrow()));
    }

}
