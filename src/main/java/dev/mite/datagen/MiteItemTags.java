package dev.mite.datagen;

import dev.mite.MiteMod;
import dev.mite.combat.CombatData;
import dev.mite.crafting.CraftingData;
import dev.mite.enchanting.EnchantingData;
import dev.mite.underworld.UnderworldData;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

/** The only item tags provider: two providers writing the same tag file silently overwrite each other. */
public final class MiteItemTags extends ItemTagsProvider {
    public MiteItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, MiteMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        CraftingData.itemTags(this::tag);
        CombatData.itemTags(this::tag);
        EnchantingData.itemTags(this::tag);
        UnderworldData.itemTags(this::tag);
        dev.mite.equipment.EquipmentData.itemTags(this::tag);
        dev.mite.metallurgy.MetallurgyData.itemTags(this::tag);
        dev.mite.progression.MiteAdvancements.itemTags(this::tag);
    }

    /** The registry key of an item, for tag entries. */
    public static ResourceKey<Item> itemKey(ItemLike item) {
        return BuiltInRegistries.ITEM.getResourceKey(item.asItem()).orElseThrow();
    }

    @Override
    public String getName() {
        return "Tags - hardscrabble items";
    }
}
