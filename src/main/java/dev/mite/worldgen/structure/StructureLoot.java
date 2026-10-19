package dev.mite.worldgen.structure;

import static dev.mite.treasure.TreasureData.item;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.material.MaterialItems;
import dev.mite.treasure.TreasureData;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.AddTableLootModifier;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;

/**
 * Structure chests of L05: the village strongbox (changelog l.291, R156: coins and more valuable items) and the witch hut chest
 * placed by SwampHutPieceMixin (changelog l.631: potions, l.817: dandelions and alliums). Contents and weights [INFÉRÉ].
 */
public final class StructureLoot {
    private StructureLoot() {}

    public static final ResourceKey<LootTable> SWAMP_HUT = ResourceKey.create(Registries.LOOT_TABLE, MiteMod.id("chests/swamp_hut"));
    public static final ResourceKey<LootTable> VILLAGE_STRONGBOX = ResourceKey.create(Registries.LOOT_TABLE, MiteMod.id("inject/village_strongbox"));
    /** The 1.6.4 blacksmith chest became these three in 26.3. */
    static final java.util.List<ResourceKey<LootTable>> STRONGBOXES = java.util.List.of(BuiltInLootTables.VILLAGE_WEAPONSMITH,
            BuiltInLootTables.VILLAGE_TOOLSMITH, BuiltInLootTables.VILLAGE_ARMORER);

    private static UniformContainerBase.Builder<?> potion(Holder<Potion> potion, int weight) {
        return LootItem.lootTableItem(Items.POTION).setWeight(weight).apply(SetPotionFunction.setPotion(potion));
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(context -> () -> {
        // Villages unlock with an iron pickaxe (EF-MON-05): copper and silver are no shortcut at that point; no iron, gold or gems.
        context.accept(VILLAGE_STRONGBOX, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 4))
                .add(TreasureData.coin(Material.COPPER, 30, 1, 4))
                .add(TreasureData.coin(Material.SILVER, 15, 1, 3))
                .add(TreasureData.coin(Material.GOLD, 6, 1, 2))
                .add(item(Items.COPPER_NUGGET, 8, 2, 5)) // nuggets, not ingots: structure copper is recalibrated (decision A4)
                .add(item(MaterialItems.SILVER_NUGGET.get(), 4, 1, 3))
                .add(item(Items.EMERALD, 2, 1, 1))));
        context.accept(SWAMP_HUT, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 5))
                .add(item(Items.GLASS_BOTTLE, 10, 1, 3))
                .add(potion(Potions.HEALING, 2)).add(potion(Potions.REGENERATION, 2)).add(potion(Potions.SWIFTNESS, 2))
                .add(potion(Potions.FIRE_RESISTANCE, 2)).add(potion(Potions.POISON, 2)).add(potion(Potions.WEAKNESS, 2))
                .add(potion(Potions.SLOWNESS, 2))
                .add(item(Items.DANDELION, 6, 1, 3))
                .add(item(Items.ALLIUM, 6, 1, 3))
                .add(item(Items.RED_MUSHROOM, 5, 1, 2))
                .add(item(Items.BROWN_MUSHROOM, 5, 1, 2))
                .add(item(Items.SPIDER_EYE, 5, 1, 2))
                .add(item(Items.SUGAR, 5, 1, 3))
                .add(item(Items.STRING, 5, 1, 3))
                .add(item(Items.REDSTONE, 4, 1, 3))
                .add(item(Items.GLOWSTONE_DUST, 3, 1, 2))));
    }, LootContextParamSets.CHEST);

    /** Changelog l.291: the strongbox table is rolled on top of the (recalibrated) vanilla smith chests. */
    public static final class LootModifiers extends GlobalLootModifierProvider {
        public LootModifiers(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup, MiteMod.MODID);
        }

        @Override
        protected void start() {
            add("village_strongbox", new AddTableLootModifier(Optional.of(Holder.direct(AnyOfCondition.anyOf(STRONGBOXES.stream()
                    .map(t -> LootTableIdCondition.builder(t.identifier())).toArray(LootTableIdCondition.Builder[]::new)).build())), 1000, VILLAGE_STRONGBOX));
        }

        @Override
        public String getName() {
            return "Global Loot Modifiers - hardscrabble structures";
        }
    }
}
