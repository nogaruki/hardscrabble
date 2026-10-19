package dev.mite;

import com.mojang.logging.LogUtils;
import dev.mite.config.MiteConfig;
import dev.mite.crafting.CraftingContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.metabolism.MetabolismContent;
import dev.mite.metallurgy.Metallurgy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(MiteMod.MODID)
public class MiteMod {
    public static final String MODID = "hardscrabble";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiteMod(IEventBus modBus, ModContainer container) {
        MiteContent.register(modBus);
        MaterialItems.register(modBus);
        MetabolismContent.register(modBus);
        CraftingContent.register(modBus);
        // Domain content registers here, one line per domain.
        dev.mite.harvest.HarvestContent.register(modBus);
        Metallurgy.register(modBus);
        dev.mite.player.ProgressionService.register(modBus);
        dev.mite.combat.CombatContent.register(modBus);
        dev.mite.worldgen.WorldgenContent.register(modBus);
        dev.mite.enchanting.EnchantingContent.register(modBus);
        dev.mite.lunar.Lunar.register(modBus);
        dev.mite.worldgen.structure.MiteStructures.register(modBus);
        dev.mite.entity.ai.AiContent.register(modBus);
        dev.mite.entity.mob.MobContent.register(modBus);
        dev.mite.entity.mob.MobSounds.register(modBus);
        dev.mite.underworld.UnderworldContent.register(modBus);
        dev.mite.dimension.UnderworldContent.register(modBus);
        dev.mite.farming.livestock.LivestockService.register(modBus);
        dev.mite.dimension.runegate.Runegate.register(modBus);
        dev.mite.farming.FarmingContent.register(modBus);
        dev.mite.curse.Curses.register(modBus);
        dev.mite.progression.MiteAdvancements.register(modBus);
        dev.mite.equipment.EquipmentContent.register(modBus);
        dev.mite.treasure.TreasureContent.register(modBus);
        dev.mite.environment.Environment.register(modBus);
        dev.mite.trading.Trading.register(modBus);
        dev.mite.rules.Hints.register(modBus);
        dev.mite.rules.RespawnTimer.register(modBus);
        MiteGameTests.register(modBus);
        container.registerConfig(ModConfig.Type.SYNCED, MiteConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, dev.mite.config.MiteClientConfig.SPEC);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /** The registered item {@code hardscrabble:<path>}. */
    public static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(id(path));
    }
}
