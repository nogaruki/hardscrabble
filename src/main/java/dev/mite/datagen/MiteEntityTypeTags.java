package dev.mite.datagen;

import dev.mite.MiteMod;
import dev.mite.entity.ai.AiData;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;

/** The only entity type tags provider (same reason as {@link MiteBlockTags}): every domain contributes here. */
public final class MiteEntityTypeTags extends EntityTypeTagsProvider {
    public MiteEntityTypeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, MiteMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        AiData.entityTypeTags(this::tag);
        dev.mite.entity.mob.MobData.entityTypeTags(this::tag);
        dev.mite.farming.livestock.LivestockService.entityTypeTags(this::tag);
    }

    @Override
    public String getName() {
        return "Tags - hardscrabble entity types";
    }
}
