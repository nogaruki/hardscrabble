package dev.mite.enchanting;

import static dev.mite.datagen.MiteItemTags.itemKey;

import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.predicates.entity.PlayerPredicate;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.mite.worldgen.WorldgenContent;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.world.level.block.Block;
import com.mojang.serialization.MapCodec;
import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.harvest.ToolStats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import dev.mite.harvest.HarvestContent;
import dev.mite.material.MaterialItems;
import java.util.List;
import java.util.function.Function;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.tags.EnchantmentTagsProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.conditions.NeverCondition;
import net.neoforged.neoforge.common.loot.AddTableLootModifier;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import net.neoforged.neoforge.common.data.JsonCodecProvider;

/** Enchanting datagen: anvil models, loot and recipes, tags, the loot blacklist modifier and the netherite upgrade removal. */
public final class EnchantingData {
    private EnchantingData() {}

    private static TagKey<Item> storageBlock(Material m) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "storage_blocks/" + m.id()));
    }

    public static final class Models extends ModelProvider {
        private static final TextureSlot BODY = TextureSlot.create("body");

        public Models(PackOutput output) {
            super(output, MiteMod.MODID);
        }

        /** Vanilla anvil shape; every wear stage uses the same placeholder model. */
        @Override
        protected void registerModels(BlockModelGenerators blocks, ItemModelGenerators items) {
            for (var anvil : EnchantingContent.ANVILS.values()) {
                Block block = anvil.get();
                TextureMapping textures = TextureMapping.top(block).putForced(BODY, TextureMapping.getBlockTexture(block))
                        .putForced(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block));
                Identifier model = ModelTemplates.ANVIL.create(block, textures, blocks.modelOutput);
                blocks.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING_ALT));
            }
            EnchantingContent.STRONGBOXES.values().forEach(b -> blocks.createTrivialCube(b.get()));
            items.generateFlatItem(EnchantingContent.DISENCHANTING_BOTTLE.get(), ModelTemplates.FLAT_ITEM);
            items.generateFlatItem(EnchantingContent.ENCHANTING_BOTTLE.get(), ModelTemplates.FLAT_ITEM);
        }

        @Override
        protected Stream<? extends Holder<Block>> getKnownBlocks() {
            return Stream.concat(EnchantingContent.ANVILS.values().stream(), EnchantingContent.STRONGBOXES.values().stream());
        }

        @Override
        protected Stream<? extends Holder<Item>> getKnownItems() {
            return Stream.concat(getKnownBlocks().map(b -> BuiltInRegistries.ITEM.wrapAsHolder(b.value().asItem())),
                    Stream.of(EnchantingContent.DISENCHANTING_BOTTLE, EnchantingContent.ENCHANTING_BOTTLE));
        }

        @Override
        public String getName() {
            return "Model Definitions - hardscrabble enchanting";
        }
    }

    /** Anvils drop themselves with their wear stage, like the chipped and damaged vanilla anvils. */
    public static final class Loot extends BlockLootSubProvider {
        public Loot(LootTableSubProvider.Context context) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
        }

        @Override
        protected void generate() {
            for (var anvil : EnchantingContent.ANVILS.values()) {
                Block block = anvil.get();
                add(block, LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(block).apply(CopyBlockState.copyState(block).copy(MiteAnvilBlock.DAMAGE))))));
            }
            EnchantingContent.STRONGBOXES.values().forEach(b -> dropSelf(b.get()));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Stream.concat(EnchantingContent.ANVILS.values().stream(), EnchantingContent.STRONGBOXES.values().stream()).map(b -> (Block) b.get()).toList();
        }
    }

    /**
     * EF-CRA-06 anvils: 3 storage blocks + 4 ingots, vanilla anvil shape. Blocks go through {@code c:storage_blocks/<metal>}: the silver,
     * ancient metal, mithril and adamantium blocks do not exist yet, so those recipes stay uncraftable until the tag is filled.
     */
    public static final class Recipes extends RecipeProvider {
        public Recipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
            super(recipes, advancements);
        }

        @Override
        protected void buildRecipes() {
            EnchantingContent.ANVILS.forEach((m, anvil) -> {
                ItemLike ingot = MaterialItems.ingot(m);
                TagKey<Item> block = storageBlock(m);
                shaped(RecipeCategory.DECORATIONS, anvil.get()).pattern("BBB").pattern(" I ").pattern("III")
                        .define('B', block).define('I', ingot).unlockedBy("has_ingot", has(ingot)).save(output);
            });
            // EF-CRA-06: strongbox = 8 ingots in a ring; disenchanting bottle = water bottle + nether wart + coal.
            EnchantingContent.STRONGBOXES.forEach((m, box) -> shaped(RecipeCategory.DECORATIONS, box.get()).pattern("III").pattern("I I").pattern("III")
                    .define('I', MaterialItems.ingot(m)).unlockedBy("has_ingot", has(MaterialItems.ingot(m))).save(output));
            shapeless(RecipeCategory.BREWING, EnchantingContent.DISENCHANTING_BOTTLE.get())
                    .requires(DataComponentIngredient.of(false, DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER), Items.POTION))
                    .requires(Items.NETHER_WART).requires(Items.COAL).unlockedBy("has_nether_wart", has(Items.NETHER_WART)).save(output);
            // Enchanting bottle (EF-ENC-06, C): no documented recipe, glass bottle + emerald [HORS-MITE, provisional].
            shapeless(RecipeCategory.BREWING, EnchantingContent.ENCHANTING_BOTTLE.get()).requires(Items.GLASS_BOTTLE).requires(Items.EMERALD)
                    .unlockedBy("has_emerald", has(Items.EMERALD)).save(output);
        }
    }

    /** EF-PRG-04 blacklist, storage blocks, enchantable targets. Called by MiteItemTags (the only item tags provider). */
    public static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        var main = tag.apply(EnchantingContent.PROGRESSION_BLACKLIST);
        Stream.concat(HarvestContent.REMOVED.stream(), Stream.of(Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
                Items.NETHERITE_HOE, Items.NETHERITE_SWORD, Items.NETHERITE_SPEAR, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_HORSE_ARMOR, Items.NETHERITE_NAUTILUS_ARMOR,
                MaterialItems.MITHRIL_INGOT.get(), MaterialItems.ADAMANTIUM_INGOT.get()))
                .forEach(item -> main.add(itemKey(item)));
        // EF-MON-06: netherite and its template leave the loot but keep their recipes, hence a second tag.
        var loot = tag.apply(EnchantingContent.LOOT_BLACKLIST).addTag(EnchantingContent.PROGRESSION_BLACKLIST);
        Stream.of(Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE).forEach(item -> loot.add(itemKey(item)));
        EnchantingContent.ANVILS.keySet().stream().filter(m -> m != Material.COPPER && m != Material.GOLD && m != Material.IRON)
                .forEach(m -> tag.apply(storageBlock(m)).add(itemKey(WorldgenContent.storageBlock(m).asItem())));
        // EF-ENC-04 targets, and the MITE tools and armor in the vanilla enchantable tags so the table and vanilla enchantments see them.
        var tools = new java.util.TreeMap<String, Item>(); // stable file order
        ToolStats.all().keySet().forEach(item -> tools.put(itemKey(item).identifier().toString(), item));
        for (Item item : tools.values()) {
            ToolStats.ToolInfo info = ToolStats.all().get(item);
            MiteEnchantments.TARGETS.forEach((t, p) -> {
                if (p.test(info)) tag.apply(t).add(itemKey(item));
            });
            for (TagKey<Item> t : vanillaTags(info.type())) tag.apply(t).add(itemKey(item));
        }
        for (var a : dev.mite.combat.CombatContent.ARMORS) {
            Item item = a.item().get();
            tag.apply(ItemTags.DURABILITY_ENCHANTABLE).add(itemKey(item));
            tag.apply(ItemTags.ARMOR_ENCHANTABLE).add(itemKey(item));
            tag.apply(switch (a.type()) {
                case HELMET -> ItemTags.HEAD_ARMOR_ENCHANTABLE;
                case CHESTPLATE -> ItemTags.CHEST_ARMOR_ENCHANTABLE;
                case LEGGINGS -> ItemTags.LEG_ARMOR_ENCHANTABLE;
                default -> ItemTags.FOOT_ARMOR_ENCHANTABLE;
            }).add(itemKey(item));
        }
    }


    private static List<TagKey<Item>> vanillaTags(ToolType type) {
        return switch (type) {
            case PICKAXE, SHOVEL, HOE, MATTOCK, SHEARS -> List.of(ItemTags.DURABILITY_ENCHANTABLE, ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE);
            case AXE, HATCHET -> List.of(ItemTags.DURABILITY_ENCHANTABLE, ItemTags.MINING_ENCHANTABLE, ItemTags.MINING_LOOT_ENCHANTABLE, ItemTags.SHARP_WEAPON_ENCHANTABLE);
            case SWORD -> List.of(ItemTags.DURABILITY_ENCHANTABLE, ItemTags.MELEE_WEAPON_ENCHANTABLE, ItemTags.SWEEPING_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE);
            // knives, daggers, clubs, cudgels, war hammers, battle axes
            default -> List.of(ItemTags.DURABILITY_ENCHANTABLE, ItemTags.MELEE_WEAPON_ENCHANTABLE, ItemTags.FIRE_ASPECT_ENCHANTABLE);
        };
    }

    /** Anvils and strongboxes are pickaxe-mined. Called by MiteBlockTags. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        var pickaxe = tag.apply(BlockTags.MINEABLE_WITH_PICKAXE);
        EnchantingContent.ANVILS.values().forEach(b -> pickaxe.add(b.key()));
        EnchantingContent.STRONGBOXES.values().forEach(b -> pickaxe.add(b.key()));
    }

    /** EF-ENC-05: Mending and Infinity are taken out of the table, trades, random loot and mob equipment (NeoForge tag removal). */
    public static final class EnchantmentTagsGen extends EnchantmentTagsProvider {
        private static final List<ResourceKey<Enchantment>> REMOVED = List.of(Enchantments.MENDING, Enchantments.INFINITY);

        public EnchantmentTagsGen(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, MiteMod.MODID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            tag(EnchantingContent.UNOBTAINABLE).addAll(REMOVED);
            // EF-ENC-04: the MITE enchantments come from the table, trades and random loot (all three include non_treasure).
            tag(EnchantmentTags.NON_TREASURE).addAll(MiteEnchantments.ALL);
            for (TagKey<Enchantment> t : List.of(EnchantmentTags.IN_ENCHANTING_TABLE, EnchantmentTags.TRADEABLE, EnchantmentTags.ON_RANDOM_LOOT,
                    EnchantmentTags.ON_TRADED_EQUIPMENT, EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT)) {
                var tag = tag(t);
                REMOVED.forEach(tag::remove);
            }
        }

        @Override
        public String getName() {
            return "Tags - hardscrabble enchanting enchantments";
        }
    }

    /** Structure loot (chests and vaults, archaeology, decorated pots) and Hero of the Village gifts, where metals and gems are
     *  recalibrated (EF-MON-06, B3). */
    public static final List<String> STRUCTURE_TABLES = List.of("chests/", "archaeology/", "pots/", "gameplay/hero_of_the_village/");

    private static ReplaceItemModifier.Rule rule(Item to, int min, int max, Item... from) {
        return new ReplaceItemModifier.Rule(List.of(from), to, min, max);
    }

    /** EF-MON-06 replace_item: one stack becomes min..max of the replacement, whatever its count. Amounts [INFÉRÉ, HORS-MITE]. */
    public static final List<ReplaceItemModifier.Rule> STRUCTURE_RULES = List.of(
            rule(Items.IRON_NUGGET, 1, 3, Items.IRON_INGOT),
            rule(Items.IRON_NUGGET, 4, 9, Items.IRON_BLOCK),
            rule(Items.IRON_NUGGET, 1, 4, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_SWORD, Items.IRON_HOE, Items.IRON_SPEAR,
                    Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_HORSE_ARMOR, Items.IRON_NAUTILUS_ARMOR),
            rule(Items.GOLD_NUGGET, 1, 3, Items.GOLD_INGOT),
            rule(Items.GOLD_NUGGET, 4, 9, Items.GOLD_BLOCK),
            rule(Items.GOLD_NUGGET, 1, 4, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_SWORD, Items.GOLDEN_HOE,
                    Items.GOLDEN_SPEAR, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
                    Items.GOLDEN_HORSE_ARMOR, Items.GOLDEN_NAUTILUS_ARMOR),
            rule(MaterialItems.DIAMOND_SHARD.get(), 1, 2, Items.DIAMOND),
            rule(MaterialItems.DIAMOND_SHARD.get(), 2, 4, Items.DIAMOND_BLOCK),
            // audit 2 X2 / moderne M1: vanilla nugget piles (shipwrecks 1-10, ruined portals 9-18 iron and 4-24 gold) shrink like the ingots.
            rule(Items.IRON_NUGGET, 1, 3, Items.IRON_NUGGET),
            rule(Items.GOLD_NUGGET, 1, 3, Items.GOLD_NUGGET),
            // audit 2 X3 / moderne M2: copper is a MITE tier (abandoned camps, trial chambers): ingots and equipment become nuggets like
            // iron and gold; the iron shears and bucket of the camps become iron nuggets [INFÉRÉ: amounts of the iron equipment rule].
            rule(Items.COPPER_NUGGET, 1, 3, Items.COPPER_INGOT),
            rule(Items.COPPER_NUGGET, 1, 4, Items.COPPER_PICKAXE, Items.COPPER_AXE, Items.COPPER_SHOVEL, Items.COPPER_SWORD, Items.COPPER_HOE,
                    Items.COPPER_SPEAR, Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS,
                    Items.COPPER_HORSE_ARMOR, Items.COPPER_NAUTILUS_ARMOR),
            rule(Items.IRON_NUGGET, 1, 4, Items.SHEARS, Items.BUCKET),
            rule(Items.EMERALD, 1, 1, Items.EMERALD),
            rule(Items.EMERALD, 1, 2, Items.EMERALD_BLOCK),
            rule(Items.IRON_NUGGET, 1, 4, Items.CROSSBOW, Items.SHIELD)); // audit 2 m3: iron-age weapons (outposts, vaults, bastions)

    /** EF-MON-06: a few ancient metal nuggets in Ancient City chests, rolled by neoforge:add_table ([HORS-MITE], weights [INFÉRÉ]). */
    public static final ResourceKey<LootTable> ANCIENT_CITY_METAL = ResourceKey.create(Registries.LOOT_TABLE, MiteMod.id("inject/ancient_city"));
    public static final LootTableProvider.SubProviderEntry STRUCTURE_LOOT = new LootTableProvider.SubProviderEntry(context -> () -> context.accept(
            ANCIENT_CITY_METAL, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                    .add(EmptyLootItem.emptyItem().setWeight(1))
                    .add(LootItem.lootTableItem(MaterialItems.ANCIENT_METAL_NUGGET.get()).setWeight(1)
                            .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 3)))))), LootContextParamSets.CHEST);

    /** L08: the blacklist and the unobtainable enchantments are stripped from every loot table (EF-MON-06, ET-L-07). */
    public static final class LootModifiers extends GlobalLootModifierProvider {
        public LootModifiers(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, MiteMod.MODID);
        }

        @Override
        protected void start() {
            add("progression_blacklist", new ProgressionLootModifier(Optional.empty(), 1000, EnchantingContent.LOOT_BLACKLIST, EnchantingContent.UNOBTAINABLE));
            // the MITE dungeon table (EF-MON-06) is already calibrated: its own nugget piles are left alone.
            add("structure_loot", new ReplaceItemModifier(Optional.of(Holder.direct(LootTableIdCondition.builder(BuiltInLootTables.SIMPLE_DUNGEON.identifier())
                    .invert().build())), 1000, STRUCTURE_TABLES, STRUCTURE_RULES));
            add("ancient_city_metal", new AddTableLootModifier(Optional.of(Holder.direct(
                    LootTableIdCondition.builder(Identifier.withDefaultNamespace("chests/ancient_city")).build())), 1000, ANCIENT_CITY_METAL));
            // combat (M12, arbitrage n°18): the heavy core (ominous vaults) only drops for a player who killed the Dragon.
            add("post_dragon", new ProgressionLootModifier(Optional.of(Holder.direct(LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
                    EntityPredicate.Builder.entity().put(PlayerPredicate.CODEC, PlayerPredicate.Builder.player()
                            .checkAdvancementDone(Identifier.withDefaultNamespace("end/kill_dragon"), true).build())).invert().build())),
                    1000, dev.mite.combat.CombatContent.POST_DRAGON, EnchantingContent.UNOBTAINABLE));
        }

        @Override
        public String getName() {
            return "Global Loot Modifiers - hardscrabble enchanting";
        }
    }

    /** EF-ENC-11: the netherite upgrade smithing recipes and their unlock advancements are overridden with a never-loaded file. */
    public static final class NetheriteRemovals extends JsonCodecProvider<Unit> {
        static final List<String> RECIPES = List.of("combat/netherite_sword", "combat/netherite_spear", "combat/netherite_helmet",
                "combat/netherite_chestplate", "combat/netherite_leggings", "combat/netherite_boots", "combat/netherite_horse_armor",
                "combat/netherite_nautilus_armor", "tools/netherite_axe", "tools/netherite_hoe", "tools/netherite_pickaxe", "tools/netherite_shovel");
        private final boolean advancements;

        public NetheriteRemovals(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, boolean advancements) {
            super(output, PackOutput.Target.DATA_PACK, advancements ? "advancement" : "recipe", MapCodec.unit(Unit.INSTANCE).codec(), lookup, "minecraft");
            this.advancements = advancements;
        }

        @Override
        protected void gather() {
            for (String r : RECIPES) {
                String path = advancements ? "recipes/" + r + "_smithing" : r.substring(r.indexOf('/') + 1) + "_smithing";
                conditionally(Identifier.withDefaultNamespace(path), b -> b.addCondition(NeverCondition.INSTANCE).withCarrier(Unit.INSTANCE));
            }
        }

        @Override
        public String getName() {
            return "Enchanting netherite removals " + directory;
        }
    }
}
