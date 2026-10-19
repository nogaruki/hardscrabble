package dev.mite.datagen;

import dev.mite.MiteMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.mite.crafting.CraftingData;
import dev.mite.material.MaterialData;
import dev.mite.metabolism.MetabolismData;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import dev.mite.metallurgy.MetallurgyData;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

/** Registers every data provider. Run with {@code ./gradlew runData}; output goes to src/generated/resources. */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class MiteDataGen {
    private MiteDataGen() {}

    @SubscribeEvent
    static void gather(GatherDataEvent.Client event) {
        event.createProvider(output -> new MiteLang(output, "en_us"));
        event.createProvider(output -> new MiteLang(output, "fr_fr"));
        event.createProvider(MaterialData.Models::new);
        // Domain providers register here, one line per provider.
        event.createProvider(MetabolismData::new);
        event.createProvider(MetabolismData.Models::new);
        event.createProvider(CraftingData.Models::new);
        event.createProvider(CraftingData.DataMaps::new);
        event.createProvider(MiteItemTags::new);
        event.createProvider(dev.mite.harvest.HarvestData.Models::new);
        event.createProvider(dev.mite.harvest.HarvestData.BlockHardness::new);
        event.createProvider((output, lookup) -> new dev.mite.harvest.HarvestData.Removals(output, lookup, false));
        event.createProvider((output, lookup) -> new dev.mite.harvest.HarvestData.Removals(output, lookup, true));
        event.createProvider(dev.mite.harvest.HarvestData.GravelLoot::new);
        event.createProvider(MetallurgyData.Models::new);
        event.createProvider(MetallurgyData.DataMaps::new);
        event.createProvider(dev.mite.combat.CombatData.Models::new);
        event.createProvider(dev.mite.worldgen.WorldgenData.Models::new);
        event.createProvider(MiteBlockTags::new);
        event.createProvider(dev.mite.enchanting.EnchantingData.Models::new);
        event.createProvider(dev.mite.enchanting.EnchantingData.EnchantmentTagsGen::new);
        event.createProvider(dev.mite.enchanting.EnchantingData.LootModifiers::new);
        event.createProvider(dev.mite.worldgen.structure.StructureLoot.LootModifiers::new); // worldgen: village strongbox
        event.createProvider((output, lookup) -> new dev.mite.enchanting.EnchantingData.NetheriteRemovals(output, lookup, false));
        event.createProvider((output, lookup) -> new dev.mite.enchanting.EnchantingData.NetheriteRemovals(output, lookup, true));
        event.createProvider(MiteEntityTypeTags::new);
        event.createProvider(dev.mite.entity.ai.AiData.DataMaps::new);
        event.createProvider(dev.mite.entity.mob.MobData.Models::new);
        event.createProvider(dev.mite.entity.mob.MobData.DamageTags::new);
        event.createProvider(dev.mite.underworld.UnderworldData.Models::new);
        event.createProvider(MiteBiomeTags::new);
        event.createProvider(dev.mite.dimension.UnderworldData.Models::new);
        event.createProvider(dev.mite.dimension.runegate.RunegateData.Models::new);
        event.createProvider(dev.mite.farming.FarmingData.Models::new);
        event.createProvider(dev.mite.equipment.EquipmentData.Models::new);
        event.createProvider(dev.mite.treasure.TreasureData.Models::new);
        event.createProvider(dev.mite.treasure.ReferenceTables::new);
        event.createProvider(dev.mite.combat.ArmorAssets::new); // equipment assets (ENF-QUA-01)
        event.createProvider(dev.mite.entity.mob.MobData.Sounds::new); // mob voices
    }

    /** Recipes, loot tables, advancements and other datapack registry entries. */
    @SubscribeEvent
    static void gatherEntries(GatherDataRegistryEntriesEvent event) {
        event.recipe(MaterialData.Recipes::new);
        // Domain entries register here, one line per domain.
        event.recipe(CraftingData.Recipes::new).lootTable(new LootTableProvider.SubProviderEntry(CraftingData.Loot::new, LootContextParamSets.BLOCK));
        event.recipe(dev.mite.harvest.HarvestData.Recipes::new);
        event.recipe(MetabolismData.Recipes::new);
        event.recipe(dev.mite.combat.CombatData.Recipes::new);
        event.gatherFor(MiteMod.MODID, "minecraft").recipe(MetallurgyData.Recipes::new).lootTable(MetallurgyData.LOOT)
                .add(Registries.CONTEXT_INT_PROVIDER, MetallurgyData::fuelTimes); // minecraft: vanilla XP and burn time overrides
        event.recipe(dev.mite.worldgen.WorldgenData.Recipes::new).lootTable(dev.mite.worldgen.WorldgenData.LOOT, dev.mite.worldgen.structure.StructureLoot.LOOT)
                .add(Registries.FEATURE, dev.mite.worldgen.WorldgenData::features).add(Registries.PLACED_FEATURE, dev.mite.worldgen.WorldgenData::placedFeatures)
                .add(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS, dev.mite.worldgen.WorldgenData::biomeModifiers);
        event.recipe(dev.mite.enchanting.EnchantingData.Recipes::new)
                .lootTable(new LootTableProvider.SubProviderEntry(dev.mite.enchanting.EnchantingData.Loot::new, LootContextParamSets.BLOCK),
                        dev.mite.enchanting.EnchantingData.STRUCTURE_LOOT)
                .add(Registries.ENCHANTMENT, dev.mite.enchanting.MiteEnchantments::bootstrap);
        event.lootTable(dev.mite.entity.mob.MobData.LOOT)
                .add(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS, dev.mite.entity.mob.MobData::biomeModifiers);
        event.recipe(dev.mite.underworld.UnderworldData.Recipes::new).lootTable(dev.mite.underworld.UnderworldData.LOOT)
                .add(Registries.FEATURE, dev.mite.underworld.UnderworldData::features).add(Registries.PLACED_FEATURE, dev.mite.underworld.UnderworldData::placedFeatures)
                .add(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS, dev.mite.underworld.UnderworldData::biomeModifiers);
        event.advancement(dev.mite.progression.MiteAdvancements::new); // progression: MITE achievements
        event.gatherFor(MiteMod.MODID, "minecraft").advancement(dev.mite.progression.MiteAdvancements.Story::new); // progression: vanilla story overrides
        event.add(Registries.DIMENSION_TYPE, dev.mite.dimension.UnderworldData::dimensionTypes) // dimension: the Underworld
                .add(Registries.MATERIAL_RULE, dev.mite.dimension.UnderworldData::materialRules).add(Registries.NOISE_SETTINGS, dev.mite.dimension.UnderworldData::noiseSettings)
                .add(Registries.PLACED_FEATURE, dev.mite.dimension.UnderworldData::placedFeatures).add(Registries.BIOME, dev.mite.dimension.UnderworldData::biomes)
                .add(Registries.LEVEL_STEM, dev.mite.dimension.UnderworldData::levelStems);
        event.recipe(dev.mite.dimension.runegate.RunegateData.Recipes::new).lootTable(dev.mite.dimension.runegate.RunegateData.LOOT); // runegate
        event.gatherFor(MiteMod.MODID, "minecraft").lootTable(dev.mite.worldgen.dungeon.DungeonLoot.LOOT, dev.mite.worldgen.WorldgenData.VANILLA_ORE_LOOT) // dungeons, ores, Nether Core, no ore veins
                .add(Registries.MATERIAL_RULE, dev.mite.worldgen.WorldgenData::materialRules).add(Registries.NOISE_SETTINGS, dev.mite.worldgen.WorldgenData::netherNoise);
        event.recipe(dev.mite.farming.FarmingData.Recipes::new).lootTable(dev.mite.farming.FarmingData.LOOT) // farming
                .add(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS, dev.mite.farming.FarmingData::biomeModifiers);
        event.recipe(dev.mite.equipment.EquipmentData.Recipes::new).lootTable(dev.mite.equipment.EquipmentData.LOOT); // equipment
        event.recipe(dev.mite.treasure.TreasureData.Recipes::new) // treasure
                .lootTable(new LootTableProvider.SubProviderEntry(dev.mite.treasure.TreasureData.Loot::new, LootContextParamSets.BLOCK));
        event.gatherFor(MiteMod.MODID, "minecraft").add(Registries.CARVER, dev.mite.environment.Environment::carvers); // environment: ravines -50 %
        event.add(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS, dev.mite.environment.Environment::biomeModifiers); // environment: no ocean ravines
        event.gatherFor(MiteMod.MODID, "minecraft").lootTable(dev.mite.trading.TradingData.GOLEM_LOOT, dev.mite.trading.TradingData.BARTER_LOOT, dev.mite.trading.TradingData.EVOKER_LOOT) // trading: vanilla overrides
                .add(Registries.VILLAGER_TRADE, dev.mite.trading.TradingData::trades).add(Registries.TRADE_SET, dev.mite.trading.TradingData::tradeSets);
        event.gatherFor(MiteMod.MODID, "minecraft").add(Registries.PLACED_FEATURE, dev.mite.worldgen.WorldgenData::emeraldOre); // worldgen: rarer emerald (M6)
        event.gatherFor(MiteMod.MODID, "minecraft").add(Registries.PROCESSOR_LIST, dev.mite.farming.FarmingData::villageFarms); // farming: dead village crops
        event.gatherFor(MiteMod.MODID, "minecraft").add(Registries.TIMELINE, dev.mite.lunar.MiteSky::moonTimeline) // sky: moon phases, Overworld without portal piglins
                .add(Registries.DIMENSION_TYPE, dev.mite.dimension.UnderworldData::overworldType);
    }
}
