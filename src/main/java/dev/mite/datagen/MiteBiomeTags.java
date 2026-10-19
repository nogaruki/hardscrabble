package dev.mite.datagen;

import dev.mite.MiteMod;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;

/** The only biome tags provider (same reason as {@link MiteBlockTags}): every domain contributes here. */
public final class MiteBiomeTags extends BiomeTagsProvider {
    public MiteBiomeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, MiteMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        dev.mite.underworld.UnderworldData.biomeTags(this::tag);
        dev.mite.metabolism.BiomeClimate.biomeTags(this::tag);
        dev.mite.lunar.Lunar.biomeTags(this::tag);
    }

    @Override
    public String getName() {
        return "Tags - hardscrabble biomes";
    }
}
