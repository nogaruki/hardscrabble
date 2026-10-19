package dev.mite.underworld;

import static dev.mite.treasure.TreasureData.item;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.mite.MiteMod;
import dev.mite.material.MaterialItems;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import java.util.function.Function;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Underworld datagen: arrow model, tags and recipes; dungeon chest loot, feature, placement and biome modifier (EF-DIM-04, EF-MET-08). */
public final class UnderworldData {
    private UnderworldData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            items.generateFlatItem(UnderworldContent.ANCIENT_METAL_ARROW.get(), ModelTemplates.FLAT_ITEM);
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return UnderworldContent.ITEMS.getEntries().stream();
        }

        @Override
        public String getName() {
            return "Underworld item models";
        }
    }

    /** Arrows (all tips) in minecraft:arrows. Called by MiteItemTags (the only item tags provider). */
    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        tag.apply(ItemTags.ARROWS).add(BuiltInRegistries.ITEM.getResourceKey(UnderworldContent.ANCIENT_METAL_ARROW.get()).orElseThrow());
        dev.mite.combat.CombatData.arrowTag().forEach(a -> tag.apply(ItemTags.ARROWS).add(BuiltInRegistries.ITEM.getResourceKey(a).orElseThrow())); // combat: the other arrow tips
    }

    /** {@code hardscrabble:is_underworld} holds the dimension's biome. */
    public static void biomeTags(Function<TagKey<Biome>, TagAppender<Biome>> tag) {
        tag.apply(UnderworldContent.IS_UNDERWORLD).addOptional(dev.mite.dimension.UnderworldContent.BIOME);
    }

    /** EF-MET-08: nugget + stick + feather = 1 arrow; recycling gives the nugget back (EF-CBT-08 "arrow -> head"). */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            Item arrow = UnderworldContent.ANCIENT_METAL_ARROW.get(), nugget = MaterialItems.ANCIENT_METAL_NUGGET.get();
            shaped(RecipeCategory.COMBAT, arrow).pattern("N").pattern("S").pattern("F").define('N', nugget).define('S', Items.STICK)
                    .define('F', Items.FEATHER).unlockedBy("has_nugget", has(nugget)).save(output);
            shapeless(RecipeCategory.MISC, nugget).requires(arrow).unlockedBy("has_arrow", has(arrow)).save(output, RECYCLE_RECIPE);
        }
    }

    public static final String RECYCLE_RECIPE = "hardscrabble:ancient_metal_nugget_from_arrow";

    /**
     * EF-DIM-04: ancient metal nuggets and ingots among dungeon junk. Ingot weight 8 of 116 over 2-4 rolls: about one chest in
     * five holds an ingot (19 %; N of CA-1 pending Q04, [INFÉRÉ]).
     */
    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(context -> () -> context.accept(
            UnderworldContent.DUNGEON_CHEST, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 4))
                    .add(item(MaterialItems.ANCIENT_METAL_NUGGET.get(), 25, 1, 4))
                    .add(item(MaterialItems.ANCIENT_METAL_INGOT.get(), 8, 1, 2))
                    .add(item(UnderworldContent.ANCIENT_METAL_ARROW.get(), 10, 2, 6))
                    .add(item(Items.BONE, 20, 1, 4))
                    .add(item(Items.ROTTEN_FLESH, 15, 1, 4))
                    .add(item(Items.STRING, 15, 1, 4))
                    .add(item(Items.GUNPOWDER, 10, 1, 4))
                    .add(dev.mite.treasure.TreasureData.coin(dev.mite.core.Material.SILVER, 10, 1, 3))
                    .add(dev.mite.treasure.TreasureData.coin(dev.mite.core.Material.ANCIENT_METAL, 3, 1, 1))) // EF-ENC-07 coins [INFÉRÉ]
                    .withPool(dev.mite.treasure.TreasureData.lorePool())), LootContextParamSets.CHEST); // EF-UI-09

    static final ResourceKey<Feature> FEATURE = ResourceKey.create(Registries.FEATURE, MiteMod.id("underworld_dungeon"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, MiteMod.id("underworld_dungeon"));

    public static void features(BootstrapContext<Feature> c) {
        c.register(FEATURE, new UnderworldDungeonFeature());
    }

    /** Twice the vanilla Overworld attempts (monster_room 10 + monster_room_deep 4), over the whole height. */
    public static void placedFeatures(BootstrapContext<PlacedFeature> c) {
        c.register(PLACED, new PlacedFeature(c.lookup(Registries.FEATURE).getOrThrow(FEATURE), List.of(CountPlacement.of(2 * (10 + 4)),
                InSquarePlacement.spread(), HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.top()), BiomeFilter.biome())));
    }

    public static void biomeModifiers(BootstrapContext<BiomeModifier> c) {
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("add_underworld_dungeons")), new BiomeModifiers.AddFeaturesBiomeModifier(
                c.lookup(Registries.BIOME).getOrThrow(UnderworldContent.IS_UNDERWORLD), HolderSet.direct(c.lookup(Registries.PLACED_FEATURE).getOrThrow(PLACED)),
                GenerationStep.Decoration.UNDERGROUND_STRUCTURES));
    }
}
