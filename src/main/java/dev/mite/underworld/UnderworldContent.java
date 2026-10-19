package dev.mite.underworld;

import com.mojang.serialization.MapCodec;
import dev.mite.MiteMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Underworld content owned by this domain: dungeons (EF-DIM-04), ancient metal arrows (EF-MET-08). The dimension itself lives
 * in {@code dev.mite.dimension}; everything here attaches to it through the biome tag {@link #IS_UNDERWORLD}.
 */
public final class UnderworldContent {
    private UnderworldContent() {}

    /** Biomes that receive the dungeons and the Longdead spawns. */
    public static final TagKey<Biome> IS_UNDERWORLD = TagKey.create(Registries.BIOME, MiteMod.id("is_underworld"));
    public static final ResourceKey<LootTable> DUNGEON_CHEST = ResourceKey.create(Registries.LOOT_TABLE, MiteMod.id("chests/underworld_dungeon"));

    /** EF-CBT-08: arrow damage = 0.5 + material bonus x 0.5, ancient metal bonus 4 [INFÉRÉ]. */
    public static final double ANCIENT_METAL_ARROW_DAMAGE = 0.5 + 4 * 0.5;

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES = DeferredRegister.create(Registries.FEATURE_TYPE, MiteMod.MODID);

    public static final DeferredItem<ArrowItem> ANCIENT_METAL_ARROW = ITEMS.registerItem("ancient_metal_arrow", p -> new ArrowItem(p) {
        @Override
        public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity owner, @Nullable ItemStack weapon) {
            AbstractArrow arrow = super.createArrow(level, stack, owner, weapon);
            arrow.setBaseDamage(ANCIENT_METAL_ARROW_DAMAGE);
            return arrow;
        }
    });

    static {
        FEATURE_TYPES.register("underworld_dungeon", () -> UnderworldDungeonFeature.CODEC);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        FEATURE_TYPES.register(modBus);
    }
}
