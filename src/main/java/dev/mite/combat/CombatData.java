package dev.mite.combat;

import static dev.mite.datagen.MiteItemTags.itemKey;

import java.util.function.Function;
import net.minecraft.data.tags.TagAppender;
import dev.mite.MiteMod;
import dev.mite.combat.CombatContent.ArmorEntry;
import dev.mite.material.MaterialItems;
import dev.mite.core.Material;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** Combat datagen: models, tags and recipes of armor (EF-CBT-03, EF-CBT-05), bows and arrows (EF-CBT-07..09), shield (EF-CBT-11). */
public final class CombatData {
    private CombatData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            CombatContent.NEW_ARMOR.forEach(i -> items.generateFlatItem(i.get(), ModelTemplates.FLAT_ITEM));
            CombatContent.NEW_CHAINS.forEach(i -> items.generateFlatItem(i.get(), ModelTemplates.FLAT_ITEM));
            Archery.NEW_ARROWS.forEach(i -> items.generateFlatItem(i.get(), ModelTemplates.FLAT_ITEM));
            items.generateFlatItem(CombatContent.RUSTED_IRON_SWORD.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
            items.generateFlatItem(CombatContent.RUSTED_IRON_DAGGER.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
            for (Item bow : List.of(Archery.ANCIENT_METAL_BOW.get(), Archery.MITHRIL_BOW.get())) {
                items.createFlatItemModel(bow, ModelTemplates.BOW);
                items.generateBow(bow);
            }
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return Stream.of(CombatContent.NEW_ARMOR, CombatContent.NEW_CHAINS, Archery.NEW_ARROWS,
                    List.of(CombatContent.RUSTED_IRON_SWORD, CombatContent.RUSTED_IRON_DAGGER, Archery.ANCIENT_METAL_BOW, Archery.MITHRIL_BOW))
                    .flatMap(List::stream);
        }

        @Override
        public String getName() {
            return "Combat armor models";
        }
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            for (ArmorEntry a : CombatContent.ARMORS) {
                // Rusted iron: mob-only, no recipe (EF-MET-09). Vanilla chainmail (iron) gets its recipe too (EF-CBT-05).
                if (a.material() == Material.RUSTED_IRON || !a.chain() && !CombatContent.NEW_ARMOR.contains(a.item())) continue;
                ItemLike ingot = a.chain() ? CombatContent.CHAINS.get(a.material()).get() : MaterialItems.ingot(a.material());
                String[] pattern = switch (a.type()) {
                    case HELMET -> new String[]{"XXX", "X X"};
                    case CHESTPLATE -> new String[]{"X X", "XXX", "XXX"};
                    case LEGGINGS -> new String[]{"XXX", "X X", "X X"};
                    default -> new String[]{"X X", "X X"};
                };
                ShapedRecipeBuilder b = shaped(RecipeCategory.COMBAT, a.item().get());
                for (String row : pattern) b.pattern(row);
                b.define('X', ingot).unlockedBy("has_ingot", has(ingot));
                if (a.chain() && a.material() == Material.IRON) b.save(output, "hardscrabble:" + BuiltInRegistries.ITEM.getKey(a.item().get()).getPath());
                else b.save(output);
            }
            // EF-CRA-06: chain = 4 nuggets in a diamond (iron's lives in the crafting domain).
            CombatContent.CHAINS.forEach((m, chain) -> {
                if (m == Material.IRON) return;
                ItemLike nugget = MaterialItems.nugget(m);
                shaped(RecipeCategory.MISC, chain.get()).pattern(" N ").pattern("N N").pattern(" N ").define('N', nugget)
                        .unlockedBy("has_nugget", has(nugget)).save(output, "hardscrabble:" + BuiltInRegistries.ITEM.getKey(chain.get()).getPath() + "_from_nuggets");
            });
            // EF-CBT-08: tip + stick + feather = 1 arrow, recycled back into its tip. Flint (vanilla) and ancient metal (underworld) exist.
            // dom-G-11: the flint arrow too, 1 flint = 1 arrow (the vanilla 4-arrow recipe is removed by a file).
            Archery.ARROWS.forEach((m, arrowItem) -> {
                if (m == Material.ANCIENT_METAL) return;
                Item arrow = arrowItem.get();
                ItemLike tip = switch (m) {
                    case FLINT -> Items.FLINT;
                    case OBSIDIAN -> MaterialItems.OBSIDIAN_SHARD.get();
                    default -> MaterialItems.nugget(m); // rusted iron: iron nugget
                };
                String name = BuiltInRegistries.ITEM.getKey(arrow).getPath();
                ShapedRecipeBuilder b = shaped(RecipeCategory.COMBAT, arrow).pattern("N").pattern("S").pattern("F").define('N', tip).define('S', Items.STICK)
                        .define('F', Items.FEATHER).unlockedBy("has_tip", has(tip));
                if (m == Material.FLINT) b.save(output, "hardscrabble:arrow");
                else if (m != Material.RUSTED_IRON) b.save(output);
                shapeless(RecipeCategory.MISC, tip).requires(arrow).unlockedBy("has_arrow", has(arrow)).save(output, "hardscrabble:" + BuiltInRegistries.ITEM.getKey(tip.asItem()).getPath() + "_from_" + name);
            });
            // EF-CBT-09: sinew (MaterialItems, 1 leather = 4) strings a bow like string.
            Item sinew = MaterialItems.SINEW.get();
            shaped(RecipeCategory.COMBAT, Items.BOW).pattern(" /S").pattern("/ S").pattern(" /S").define('/', Items.STICK).define('S', sinew)
                    .unlockedBy("has_sinew", has(sinew)).save(output, "hardscrabble:bow_from_sinew");
            // EF-CBT-07: metal bows, the wooden frame reinforced with two ingots [INFÉRÉ: recipe unspecified].
            for (Archery.MiteBow bow : List.of(Archery.ANCIENT_METAL_BOW.get(), Archery.MITHRIL_BOW.get())) {
                ItemLike ingot = MaterialItems.ingot(bow.material);
                shaped(RecipeCategory.COMBAT, bow).pattern("I/S").pattern("/ S").pattern("I/S").define('I', ingot).define('/', Items.STICK)
                        .define('S', Ingredient.of(Items.STRING, sinew)).unlockedBy("has_ingot", has(ingot)).save(output);
            }
            // EF-CBT-11 [HORS-MITE] (dom-G-06): heavier than vanilla, 1 iron ingot + 4 nuggets = 13 nuggets of iron against 9 (vanilla removed by a file).
            shaped(RecipeCategory.COMBAT, Items.SHIELD).pattern("PNP").pattern("NIN").pattern("PNP").define('P', ItemTags.PLANKS).define('N', Items.IRON_NUGGET)
                    .define('I', Items.IRON_INGOT).unlockedBy("has_iron_ingot", has(Items.IRON_INGOT)).save(output, "hardscrabble:shield");
        }
    }

    /** Armor slot tags for new armor, bow tags, post-Dragon loot. Called by MiteItemTags (the only item tags provider). */
    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        for (ArmorEntry a : CombatContent.ARMORS) {
            if (!CombatContent.NEW_ARMOR.contains(a.item())) continue;
            TagKey<Item> slot = switch (a.type()) {
                case HELMET -> ItemTags.HEAD_ARMOR;
                case CHESTPLATE -> ItemTags.CHEST_ARMOR;
                case LEGGINGS -> ItemTags.LEG_ARMOR;
                default -> ItemTags.FOOT_ARMOR;
            };
            tag.apply(slot).add(itemKey(a.item().get()));
        }
        for (Item bow : List.of(Archery.ANCIENT_METAL_BOW.get(), Archery.MITHRIL_BOW.get())) {
            tag.apply(ItemTags.BOW_ENCHANTABLE).add(itemKey(bow));
            tag.apply(ItemTags.DURABILITY_ENCHANTABLE).add(itemKey(bow));
            tag.apply(Tags.Items.TOOLS_BOW).add(itemKey(bow));
        }
        tag.apply(CombatContent.POST_DRAGON).add(itemKey(Items.HEAVY_CORE));
        tag.apply(CombatContent.POST_DRAGON).add(itemKey(Items.DRIED_GHAST)); // X7, arbitrage n°7: no happy ghast flight before the Dragon
    }


    /** minecraft:arrows entries, written by the underworld domain's provider (one writer per tag file). */
    public static List<Item> arrowTag() {
        return Archery.NEW_ARROWS.stream().map(i -> (Item) i.get()).toList();
    }
}
