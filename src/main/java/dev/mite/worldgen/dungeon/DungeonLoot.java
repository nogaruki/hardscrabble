package dev.mite.worldgen.dungeon;

import static dev.mite.treasure.TreasureData.item;

import dev.mite.MiteMod;
import dev.mite.core.Quality;
import dev.mite.core.Material;
import dev.mite.crafting.CraftQuality;
import dev.mite.treasure.TreasureData;
import dev.mite.material.MaterialItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * MITE dungeons (EF-MON-07, ET-L-07, L09): {@code minecraft:chests/simple_dungeon} replaced by MITE loot: coins (EF-ENC-07),
 * which changelog l.373 (R147) gives instead of nuggets (L-6: coins 77 % of the metal rolls, a few nuggets left), low quality
 * tools, vanilla junk, Funzang's books (EF-UI-09); no golden apples, diamond horse armor or ingots. Weights [INFÉRÉ]. Generated under minecraft: (MiteDataGen).
 */
public final class DungeonLoot {
    private DungeonLoot() {}

    private static UniformContainerBase.Builder<?> tool(String id, Quality quality, int weight) {
        return LootItem.lootTableItem(BuiltInRegistries.ITEM.getValue(MiteMod.id(id))).setWeight(weight)
                .apply(SetComponentsFunction.setComponent(CraftQuality.QUALITY.get(), quality));
    }

    public static final LootTableProvider.SubProviderEntry LOOT = new LootTableProvider.SubProviderEntry(context -> () -> context.accept(
            BuiltInLootTables.SIMPLE_DUNGEON, LootTable.lootTable()
                    .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(1, 3))
                            .add(TreasureData.coin(Material.COPPER, 40, 1, 4))
                            .add(TreasureData.coin(Material.SILVER, 25, 1, 3))
                            .add(TreasureData.coin(Material.GOLD, 12, 1, 2))
                            .add(item(Items.COPPER_NUGGET, 10, 2, 6))
                            .add(item(MaterialItems.SILVER_NUGGET.get(), 6, 1, 4))
                            .add(item(Items.IRON_NUGGET, 6, 1, 4))
                            .add(item(Items.GOLD_NUGGET, 4, 1, 4)))
                    .withPool(TreasureData.lorePool())
                    .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                            .add(EmptyLootItem.emptyItem().setWeight(20))
                            .add(tool("copper_dagger", Quality.POOR, 10))
                            .add(tool("copper_hatchet", Quality.POOR, 10))
                            .add(tool("rusted_iron_dagger", Quality.WRETCHED, 8))
                            .add(tool("rusted_iron_sword", Quality.WRETCHED, 5))
                            .add(tool("flint_knife", Quality.WRETCHED, 10)))
                    .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 5))
                            .add(item(Items.BONE, 20, 1, 8))
                            .add(item(Items.ROTTEN_FLESH, 20, 1, 8))
                            .add(item(Items.STRING, 15, 1, 8))
                            .add(item(Items.GUNPOWDER, 15, 1, 8))
                            .add(item(Items.WHEAT, 10, 1, 4))
                            .add(item(Items.BREAD, 10, 1, 1))
                            .add(item(Items.COAL, 10, 1, 4))
                            .add(item(Items.SADDLE, 5, 1, 1))
                            .add(item(Items.NAME_TAG, 3, 1, 1))
                            .add(item(Items.MUSIC_DISC_13, 2, 1, 1))
                            .add(item(Items.MUSIC_DISC_CAT, 2, 1, 1)))), LootContextParamSets.CHEST);
}
