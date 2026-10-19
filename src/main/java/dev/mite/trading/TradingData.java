package dev.mite.trading;

import dev.mite.MiteMod;
import dev.mite.core.Quality;
import dev.mite.crafting.CraftQuality;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import java.util.Optional;

/**
 * Vanilla trade and barter overrides, generated under minecraft: (MiteDataGen) (EF-PRG-04, PD-06, matrix D09, arbitrations 9 and 21).
 * Villagers sell no stone or diamond item and no enchanted equipment: each such vanilla trade keeps its id, price and level but
 * gives a poor quality copper, silver or plain iron item. Enchanted books stay (their enchantments follow the MITE tags).
 */
public final class TradingData {
    private TradingData() {}

    private record Swap(String trade, Item gives, int emeralds, int maxUses, int xp, float discount) {}

    private static Swap[] swaps() {
        return new Swap[] {
            // stone tools -> copper (the villages open after an iron pickaxe, R165)
            new Swap("toolsmith/1/emerald_stone_axe", Items.COPPER_AXE, 1, 12, 1, 0.2f),
            new Swap("toolsmith/1/emerald_stone_shovel", Items.COPPER_SHOVEL, 1, 12, 1, 0.2f),
            new Swap("toolsmith/1/emerald_stone_pickaxe", Items.COPPER_PICKAXE, 1, 12, 1, 0.2f),
            new Swap("toolsmith/1/emerald_stone_hoe", Items.COPPER_HOE, 1, 12, 1, 0.2f),
            // enchanted iron -> plain iron
            new Swap("toolsmith/3/emerald_enchanted_iron_axe", Items.IRON_AXE, 1, 3, 10, 0.2f),
            new Swap("toolsmith/3/emerald_enchanted_iron_shovel", Items.IRON_SHOVEL, 2, 3, 10, 0.2f),
            new Swap("toolsmith/3/emerald_enchanted_iron_pickaxe", Items.IRON_PICKAXE, 3, 3, 10, 0.2f),
            // audit 2 moderne m4: the novice armorer and weaponsmith sell copper, not iron or iron chainmail; the fletcher's
            // crossbow (an iron item) becomes a bow
            new Swap("weaponsmith/1/emerald_enchanted_iron_sword", Items.COPPER_SWORD, 2, 12, 1, 0.2f),
            new Swap("weaponsmith/1/emerald_iron_axe", Items.COPPER_AXE, 3, 12, 1, 0.2f),
            new Swap("armorer/1/emerald_iron_helmet", Items.COPPER_HELMET, 5, 12, 1, 0.2f),
            new Swap("armorer/1/emerald_iron_chestplate", Items.COPPER_CHESTPLATE, 9, 12, 1, 0.2f),
            new Swap("armorer/1/emerald_iron_leggings", Items.COPPER_LEGGINGS, 7, 12, 1, 0.2f),
            new Swap("armorer/1/emerald_iron_boots", Items.COPPER_BOOTS, 4, 12, 1, 0.2f),
            new Swap("armorer/2/emerald_chainmail_boots", MiteMod.item("copper_chainmail_boots"), 1, 12, 5, 0.2f),
            new Swap("armorer/2/emerald_chainmail_leggings", MiteMod.item("copper_chainmail_leggings"), 3, 12, 5, 0.2f),
            new Swap("armorer/3/emerald_chainmail_chestplate", MiteMod.item("copper_chainmail_chestplate"), 4, 12, 10, 0.2f),
            new Swap("armorer/3/emerald_chainmail_helmet", MiteMod.item("copper_chainmail_helmet"), 1, 12, 10, 0.2f),
            new Swap("fletcher/3/emerald_crossbow", Items.BOW, 3, 12, 10, 0.05f),
            // diamond -> silver (below iron)
            new Swap("toolsmith/3/emerald_diamond_hoe", MiteMod.item("silver_hoe"), 4, 3, 10, 0.2f),
            new Swap("toolsmith/4/emerald_enchanted_diamond_axe", MiteMod.item("silver_axe"), 12, 3, 15, 0.2f),
            new Swap("toolsmith/4/emerald_enchanted_diamond_shovel", MiteMod.item("silver_shovel"), 5, 3, 15, 0.2f),
            new Swap("toolsmith/5/emerald_enchanted_diamond_pickaxe", MiteMod.item("silver_pickaxe"), 13, 3, 30, 0.2f),
            new Swap("weaponsmith/4/emerald_enchanted_diamond_axe", MiteMod.item("silver_battle_axe"), 12, 3, 15, 0.2f),
            new Swap("weaponsmith/5/emerald_enchanted_diamond_sword", MiteMod.item("silver_sword"), 8, 3, 30, 0.2f),
            new Swap("armorer/4/emerald_enchanted_diamond_leggings", MiteMod.item("silver_leggings"), 14, 3, 15, 0.2f),
            new Swap("armorer/4/emerald_enchanted_diamond_boots", MiteMod.item("silver_boots"), 8, 3, 15, 0.2f),
            new Swap("armorer/5/emerald_enchanted_diamond_helmet", MiteMod.item("silver_helmet"), 8, 3, 30, 0.2f),
            new Swap("armorer/5/emerald_enchanted_diamond_chestplate", MiteMod.item("silver_chestplate"), 16, 3, 30, 0.2f),
            // other enchanted equipment -> plain (the vanilla fishing rod is blacklisted: copper rod)
            new Swap("fisherman/3/emerald_enchanted_fishing_rod", MiteMod.item("copper_fishing_rod"), 3, 3, 10, 0.2f),
            new Swap("fletcher/4/emerald_enchanted_bow", Items.BOW, 2, 3, 15, 0.05f),
            new Swap("fletcher/5/emerald_enchanted_crossbow", Items.CROSSBOW, 3, 3, 15, 0.05f),
        };
    }

    public static void trades(BootstrapContext<VillagerTrade> context) {
        DataComponentPatch poor = DataComponentPatch.builder().set(CraftQuality.QUALITY.get(), Quality.POOR).build();
        for (Swap s : swaps()) {
            context.register(ResourceKey.create(Registries.VILLAGER_TRADE, Identifier.withDefaultNamespace(s.trade())),
                    VillagerTrade.builder(new TradeCost(Items.EMERALD, s.emeralds()), new ItemStackTemplate(s.gives(), poor), s.maxUses(), s.xp(), s.discount()).build());
        }
        // M2: the wandering trader's 1-emerald enchanted iron pickaxe (copper and iron ages skipped) becomes 4 copper nuggets.
        context.register(ResourceKey.create(Registries.VILLAGER_TRADE, Identifier.withDefaultNamespace("wandering_trader/emerald_enchanted_iron_pickaxe")),
                VillagerTrade.builder(new TradeCost(Items.EMERALD, 1), new ItemStackTemplate(Items.COPPER_NUGGET, 4), 6, 1, 0.05f).build());
    }

    /** M2: the wandering trader no longer buys potatoes, water or milk for emeralds (250 XP each, B09). */
    public static void tradeSets(BootstrapContext<TradeSet> context) {
        context.register(TradeSets.WANDERING_TRADER_BUYING, new TradeSet(HolderSet.empty(), ContextIntProviders.exactly(0), false, Optional.empty()));
    }

    /**
     * Audit 2 X4 (owner decision): an iron golem drops its poppies and a single iron nugget, and only when a player kills it, so a
     * 1.14 golem farm (villager panic, lava or fall kill) yields no iron.
     */
    public static final LootTableProvider.SubProviderEntry GOLEM_LOOT = new LootTableProvider.SubProviderEntry(context -> () -> context.accept(
            EntityTypes.IRON_GOLEM.getDefaultLootTable().orElseThrow(), LootTable.lootTable()
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Blocks.POPPY).apply(SetItemCountFunction.setCount(ContextIntProviders.between(0, 2)))))
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.IRON_NUGGET)).when(LootItemKilledByPlayerCondition.killedByPlayer()))),
            LootContextParamSets.ENTITY);

    /** [INFÉRÉ] chance that an evoker killed by a player drops its totem (vanilla 100 %, any death). */
    public static final float TOTEM_CHANCE = 0.25f;

    /**
     * Arbitrage n°20 keeps the totem of undying "from evokers, rare" (audit 2 m6): vanilla drops it from every evoker, whatever kills it,
     * which makes raids a totem farm that cancels the R196 death penalty. Only a player kill, 1 time in 4; the emerald pool is vanilla.
     */
    public static final LootTableProvider.SubProviderEntry EVOKER_LOOT = new LootTableProvider.SubProviderEntry(context -> () -> context.accept(
            EntityTypes.EVOKER.getDefaultLootTable().orElseThrow(), LootTable.lootTable()
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.TOTEM_OF_UNDYING))
                            .when(LootItemKilledByPlayerCondition.killedByPlayer()).when(LootItemRandomChanceCondition.randomChance(TOTEM_CHANCE)))
                    .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.EMERALD).apply(SetItemCountFunction.setCount(ContextIntProviders.between(0, 1)))
                                    .apply(EnchantedCountIncreaseFunction.lootingMultiplier(context.lookup(Registries.ENCHANTMENT), ContextFloatProviders.between(0.0F, 1.0F))))
                            .when(LootItemKilledByPlayerCondition.killedByPlayer()))),
            LootContextParamSets.ENTITY);

    /** Piglins barter neither iron, obsidian, enchanted iron boots nor dried ghasts (contournements-07). */
    public static final LootTableProvider.SubProviderEntry BARTER_LOOT = new LootTableProvider.SubProviderEntry(context -> () -> context.accept(
            BuiltInLootTables.PIGLIN_BARTERING, LootTable.lootTable().withPool(LootPool.lootPool()
                    .add(LootItem.lootTableItem(Items.BOOK).setWeight(5)
                            .apply(new EnchantRandomlyFunction.Builder().withEnchantment(context.lookup(Registries.ENCHANTMENT).getOrThrow(Enchantments.SOUL_SPEED))))
                    .add(LootItem.lootTableItem(Items.POTION).setWeight(8).apply(SetPotionFunction.setPotion(Potions.FIRE_RESISTANCE)))
                    .add(LootItem.lootTableItem(Items.SPLASH_POTION).setWeight(8).apply(SetPotionFunction.setPotion(Potions.FIRE_RESISTANCE)))
                    .add(LootItem.lootTableItem(Items.POTION).setWeight(10).apply(SetPotionFunction.setPotion(Potions.WATER)))
                    .add(LootItem.lootTableItem(Items.ENDER_PEARL).setWeight(10).apply(SetItemCountFunction.setCount(ContextIntProviders.between(2, 4))))
                    .add(LootItem.lootTableItem(Items.STRING).setWeight(20).apply(SetItemCountFunction.setCount(ContextIntProviders.between(3, 9))))
                    .add(LootItem.lootTableItem(Items.QUARTZ).setWeight(20).apply(SetItemCountFunction.setCount(ContextIntProviders.between(5, 12))))
                    .add(LootItem.lootTableItem(Items.CRYING_OBSIDIAN).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 3))))
                    .add(LootItem.lootTableItem(Items.FIRE_CHARGE).setWeight(40))
                    .add(LootItem.lootTableItem(Items.LEATHER).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(2, 4))))
                    .add(LootItem.lootTableItem(Items.SOUL_SAND).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(2, 8))))
                    .add(LootItem.lootTableItem(Items.NETHER_BRICK).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(2, 8))))
                    .add(LootItem.lootTableItem(Items.SPECTRAL_ARROW).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(6, 12))))
                    .add(LootItem.lootTableItem(Items.GRAVEL).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(8, 16))))
                    .add(LootItem.lootTableItem(Items.BLACKSTONE).setWeight(40).apply(SetItemCountFunction.setCount(ContextIntProviders.between(8, 16)))))),
            LootContextParamSets.PIGLIN_BARTER);
}
