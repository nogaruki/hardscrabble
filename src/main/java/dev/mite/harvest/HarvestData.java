package dev.mite.harvest;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.harvest.HarvestContent.ToolEntry;
import dev.mite.harvest.HarvestRules.Hardness;
import dev.mite.material.MaterialItems;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.data.CachedOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.common.data.JsonCodecProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootPredicates;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.common.conditions.NeverCondition;
import net.neoforged.neoforge.common.data.DataMapProvider;

/** Harvest datagen: tool models and recipes, vanilla recipe removal, block hardness table, gravel loot. */
public final class HarvestData {
    private HarvestData() {}

    public static final class Models extends ModelProvider {
        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            HarvestContent.TOOLS.forEach(t -> items.generateFlatItem(t.item().get(), ModelTemplates.FLAT_HANDHELD_ITEM));
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.empty();
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return HarvestContent.TOOLS.stream().map(ToolEntry::item);
        }

        @Override
        public String getName() {
            return "Harvest tool models";
        }
    }

    /** Tool recipes: one unit per component, plus sticks (EF-OUT-08). Every recipe fits a 2x2 grid when it has at most 2 rows of 2. */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            for (ToolEntry t : HarvestContent.TOOLS) {
                Material m = t.info().material();
                String[] pattern = switch (t.info().type()) {
                    case PICKAXE -> new String[]{"XXX", " # ", " # "};
                    case AXE -> new String[]{"XX", "X#", " #"};
                    case SHOVEL -> new String[]{"X", "#", "#"};
                    case HOE -> new String[]{"XX", " #", " #"};
                    case SWORD, CLUB -> new String[]{"X", "X", "#"};
                    case HATCHET -> new String[]{"X#", " #"};
                    // EF-OUT-09: ingots = components, + 2 sticks [shapes INFÉRÉ, Q-13].
                    case BATTLE_AXE -> new String[]{"X X", "X#X", " # "};
                    case WAR_HAMMER -> new String[]{"XXX", "X#X", " # "};
                    case MATTOCK -> new String[]{"XXX", "X# ", " # "};
                    case SCYTHE -> new String[]{"XX ", "  #", "  #"};
                    case SHEARS -> new String[]{" X", "X "}; // vanilla shape, no stick
                    default -> new String[]{"X", "#"}; // knife, dagger, cudgel
                };
                boolean lashable = m == Material.FLINT || m == Material.OBSIDIAN;
                // EF-OUT-12: with tool_lashing on, the plain recipe is left out (flint hatchet included, as in HarvestRules, dom-D-06).
                save(t, pattern, null, lashable);
                if (lashable) {
                    String[] lashed = switch (t.info().type()) {
                        case HATCHET -> new String[]{"X#", "L#"};
                        case AXE -> new String[]{"XX", "X#", "L#"};
                        case SHOVEL -> new String[]{"XL", "# ", "# "};
                        default -> new String[]{"XL", "# "}; // knife
                    };
                    save(t, lashed, "_lashed", false);
                }
            }
        }

        private void save(ToolEntry t, String[] pattern, String suffix, boolean needsLashingOff) {
            Material m = t.info().material();
            ToolType type = t.info().type();
            boolean combat = type == ToolType.SWORD || type == ToolType.DAGGER || type == ToolType.BATTLE_AXE || type == ToolType.WAR_HAMMER;
            ShapedRecipeBuilder b = shaped(combat ? RecipeCategory.COMBAT : RecipeCategory.TOOLS, t.item().get());
            for (String row : pattern) b.pattern(row);
            if (type != ToolType.SHEARS) b.define('#', Items.STICK);
            if (suffix != null) b.define('L', HarvestContent.LASHINGS);
            if (m == Material.WOOD) b.define('X', ItemTags.PLANKS).unlockedBy("has_planks", has(ItemTags.PLANKS));
            else b.define('X', unit(m)).unlockedBy("has_material", has(unit(m)));
            var out = needsLashingOff ? output.withConditions(new dev.mite.crafting.CraftingContent.LashingDisabled()) : output;
            if (suffix == null) b.save(out);
            else b.save(out, MiteMod.MODID + ":" + t.item().getId().getPath() + suffix);
        }

        /** The crafting unit of a material: flint, obsidian shard, or ingot. */
        private static ItemLike unit(Material m) {
            return switch (m) {
                case FLINT -> Items.FLINT;
                case OBSIDIAN -> MaterialItems.OBSIDIAN_SHARD.get();
                default -> MaterialItems.ingot(m);
            };
        }
    }

    /**
     * Overrides the vanilla recipes of removed tools, and their unlock advancements, with a never-loaded file (EF-OUT-07).
     * Datapack registry patches cannot replace vanilla entries in 26.3 datagen, hence plain JSON files.
     */
    public static final class Removals extends JsonCodecProvider<Unit> {
        private final boolean advancements;

        public Removals(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, boolean advancements) {
            super(output, PackOutput.Target.DATA_PACK, advancements ? "advancement" : "recipe", MapCodec.unit(Unit.INSTANCE).codec(), lookup, "minecraft");
            this.advancements = advancements;
        }

        @Override
        protected void gather() {
            // Changelog R121 (l.759): the sandstone crafting recipe is removed.
            for (String path : java.util.List.of("sandstone", "red_sandstone"))
                conditionally(Identifier.withDefaultNamespace(advancements ? "recipes/building_blocks/" + path : path),
                        b -> b.addCondition(NeverCondition.INSTANCE).withCarrier(Unit.INSTANCE));
            for (Item item : HarvestContent.REMOVED) {
                if (item == Items.DIAMOND_HORSE_ARMOR || item == Items.DIAMOND_NAUTILUS_ARMOR) continue; // no vanilla recipe
                String path = BuiltInRegistries.ITEM.getKey(item).getPath();
                if (advancements) path = "recipes/" + (path.matches(".*_(sword|spear|helmet|chestplate|leggings|boots)") ? "combat/" : "tools/") + path;
                conditionally(Identifier.withDefaultNamespace(path), b -> b.addCondition(NeverCondition.INSTANCE).withCarrier(Unit.INSTANCE));
            }
        }

        @Override
        public String getName() {
            return "Harvest removed " + directory;
        }
    }

    /** {@code hardscrabble:block_hardness} from the EF-OUT-02 table. Unlisted blocks follow {@link HarvestRules#hardness}. */
    public static final class BlockHardness extends DataMapProvider {
        public BlockHardness(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void gather(HolderLookup.Provider provider) {
            var b = builder(HarvestRules.BLOCK_HARDNESS);
            b.add(BlockTags.SAND, new Hardness(0.4f, 0), false);
            b.add(BlockTags.DIRT, new Hardness(0.5f, 0), false);
            b.add(BlockTags.PLANKS, new Hardness(0.8f, 0), false);
            b.add(BlockTags.LOGS, new Hardness(1.2f, 1), false);
            add(b, 0.8f, 0, Blocks.GRAVEL);
            // D-1 / changelog l.761 (R119): sandstone needs a tool; flint hatchets and axes harvest it (HarvestContent.SANDSTONE).
            add(b, 1.0f, 1, HarvestContent.SANDSTONE.toArray(Block[]::new));
            // Thin snow breaks by hand (no-tool block); the snow block is shovel-mined from the flint age (fallback: level 2).
            add(b, Blocks.SNOW.defaultDestroyTime(), 0, Blocks.SNOW);
            add(b, Blocks.SNOW_BLOCK.defaultDestroyTime(), 1, Blocks.SNOW_BLOCK);
            add(b, 1.2f, 2, Blocks.COAL_ORE);
            add(b, 2.0f, 2, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
            // Tuff, calcite, basalt, blackstone and the stone variants are tied to stone [HORS-MITE].
            add(b, 2.4f, 2, Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
                    Blocks.TUFF, Blocks.CALCITE, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.GOLD_ORE, Blocks.NETHER_GOLD_ORE);
            add(b, 2.4f, 3, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);
            add(b, 2.5f, 2, Blocks.COPPER_ORE);
            add(b, 3.0f, 2, Blocks.IRON_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.NETHER_QUARTZ_ORE);
            add(b, 3.0f, 3, Blocks.EMERALD_ORE);
            add(b, 3.0f, 4, Blocks.DIAMOND_ORE);
            add(b, 1.6f, 2, Blocks.NETHERRACK);
            // Deepslate variants: stone equivalent x1.25, same level [HORS-MITE].
            add(b, 2.4f * 1.25f, 2, Blocks.DEEPSLATE);
            add(b, 2.0f * 1.25f, 2, Blocks.COBBLED_DEEPSLATE);
            add(b, 1.2f * 1.25f, 2, Blocks.DEEPSLATE_COAL_ORE);
            add(b, 2.4f * 1.25f, 2, Blocks.DEEPSLATE_GOLD_ORE);
            add(b, 2.5f * 1.25f, 2, Blocks.DEEPSLATE_COPPER_ORE);
            add(b, 3.0f * 1.25f, 2, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
            add(b, 3.0f * 1.25f, 3, Blocks.DEEPSLATE_EMERALD_ORE);
            add(b, 3.0f * 1.25f, 4, Blocks.DEEPSLATE_DIAMOND_ORE);
            // Metal blocks: vanilla hardness, a tool one rank above the metal (EF-OUT-02); adamantium (level 6) is unbreakable.
            Blocks.COPPER_BLOCK.forEach(c -> add(b, c.defaultDestroyTime(), Material.COPPER.toolHarvestLevel() + 1, c));
            add(b, Blocks.GOLD_BLOCK.defaultDestroyTime(), Material.GOLD.toolHarvestLevel() + 1, Blocks.GOLD_BLOCK);
            add(b, Blocks.IRON_BLOCK.defaultDestroyTime(), Material.IRON.toolHarvestLevel() + 1, Blocks.IRON_BLOCK);
            // MITE ores (F01): deepslate x1.25, same level; storage blocks at the level of their metal.
            for (var o : dev.mite.worldgen.WorldgenContent.ORES) {
                add(b, o.hardness(), o.level(), o.stone().get());
                add(b, o.hardness() * 1.25f, o.level(), o.deepslate().get());
            }
            for (var s : dev.mite.worldgen.WorldgenContent.STORAGE) add(b, s.block().get().defaultDestroyTime(), s.level() + 1, s.block().get());
            // The obsidian furnace needs the obsidian tool level (iron pickaxe); the other stone furnaces follow the fallback (level 2).
            Block obsidianFurnace = dev.mite.metallurgy.Metallurgy.OBSIDIAN_FURNACE.get();
            add(b, obsidianFurnace.defaultDestroyTime(), 3, obsidianFurnace);
            // EF-ENC-08: a strongbox needs a tool one level above its metal.
            dev.mite.enchanting.EnchantingContent.STRONGBOXES.forEach((m, box) -> add(b, box.get().defaultDestroyTime(), m.toolHarvestLevel() + 1, box.get()));
        }

        @Override
        public String getName() {
            return "Harvest block hardness";
        }

        private static void add(Builder<Hardness, Block> b, float hardness, int level, Block... blocks) {
            for (Block block : blocks) b.add(BuiltInRegistries.BLOCK.wrapAsHolder(block), new Hardness(hardness, level), false);
        }
    }

    /** Gravel drops (EF-MET-04): silk touch gives gravel, otherwise one weighted roll. Weights are the table over 209 952 = 2^5 x 3^8. */
    public static final class GravelLoot extends JsonCodecProvider<LootTable> {
        private HolderLookup.Provider registries;

        public GravelLoot(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, PackOutput.Target.DATA_PACK, "loot_table", LootTable.DIRECT_CODEC, lookup, "minecraft");
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return lookupProvider.thenCompose(r -> {
                registries = r;
                return super.run(cache);
            });
        }

        @Override
        protected void gather() {
            Holder<LootItemCondition> silk = registries.lookupOrThrow(Registries.PREDICATE).getOrThrow(LootPredicates.TOOL_CAN_SILK_TOUCH);
            Identifier id = Blocks.GRAVEL.getLootTable().orElseThrow().identifier();
            unconditional(id, LootTable.lootTable().setParamSet(LootContextParamSets.BLOCK).setRandomSequence(id)
                    .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).when(silk).add(LootItem.lootTableItem(Blocks.GRAVEL)))
                    .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                            .when(InvertedLootItemCondition.invert(silk)).when(ExplosionCondition.survivesExplosion())
                            .add(LootItem.lootTableItem(Blocks.GRAVEL).setWeight(157_464))                      // 3/4
                            .add(LootItem.lootTableItem(MaterialItems.FLINT_SHARD.get()).setWeight(32_805))     // 5/32
                            .add(LootItem.lootTableItem(Items.COPPER_NUGGET).setWeight(11_664))                // 1/18
                            .add(LootItem.lootTableItem(MaterialItems.SILVER_NUGGET.get()).setWeight(3_888))    // 1/54
                            .add(LootItem.lootTableItem(Items.FLINT).setWeight(2_187))                         // 1/96
                            .add(LootItem.lootTableItem(Items.GOLD_NUGGET).setWeight(1_296))                   // 1/162
                            .add(LootItem.lootTableItem(MaterialItems.OBSIDIAN_SHARD.get()).setWeight(432))     // 1/486
                            .add(LootItem.lootTableItem(MaterialItems.EMERALD_SHARD.get()).setWeight(144))      // 1/1458
                            .add(LootItem.lootTableItem(MaterialItems.DIAMOND_SHARD.get()).setWeight(48))       // 1/4374
                            .add(LootItem.lootTableItem(MaterialItems.MITHRIL_NUGGET.get()).setWeight(16))      // 1/13122
                            .add(LootItem.lootTableItem(MaterialItems.ADAMANTIUM_NUGGET.get()).setWeight(8)))   // 1/26244
                    .build());
        }

        @Override
        public String getName() {
            return "Harvest gravel loot";
        }
    }
}
