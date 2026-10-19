package dev.mite.datagen;

import dev.mite.MiteMod;
import dev.mite.enchanting.EnchantingData;
import dev.mite.worldgen.WorldgenData;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/**
 * The only block tags provider: two providers writing the same tag file (e.g. minecraft:mineable/pickaxe) silently
 * overwrite each other, so every domain contributes here.
 */
public final class MiteBlockTags extends BlockTagsProvider {
    public MiteBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, MiteMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        WorldgenData.blockTags(this::tag);
        EnchantingData.blockTags(this::tag);
        dev.mite.entity.ai.AiData.blockTags(this::tag);
        dev.mite.worldgen.DirtGravity.blockTags(this::tag);
        dev.mite.dimension.runegate.Runegate.blockTags(this::tag);
        dev.mite.farming.FarmingData.blockTags(this::tag);
        dev.mite.equipment.EquipmentData.blockTags(this::tag);
        dev.mite.treasure.TreasureData.blockTags(this::tag);
        dev.mite.farming.SoilRules.blockTags(this::tag);
        dev.mite.curse.Curses.blockTags(this::tag);
        dev.mite.metallurgy.MetallurgyData.blockTags(this::tag);
        dev.mite.dimension.UnderworldContent.blockTags(this::tag);
        dev.mite.crafting.CraftingData.blockTags(this::tag);
    }

    @Override
    public String getName() {
        return "Tags - hardscrabble blocks";
    }
}
