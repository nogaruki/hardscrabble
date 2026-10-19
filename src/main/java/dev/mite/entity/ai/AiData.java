package dev.mite.entity.ai;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.mite.MiteMod;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.RegisteredCondition;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.AcceptableVillagerDistance;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/** Datagen of the AI domain: diggable blocks, digger / frenzy tags, depth spawn thresholds. */
public final class AiData {
    private AiData() {}

    /** EF-IA-01: blocks that need no tool (dirt, sand, gravel, grass, snow, leaves, glass...). The clay furnace never is. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(AiContent.ZOMBIE_DIGGABLE)
                .addTag(BlockTags.DIRT).addTag(BlockTags.SAND).addTag(BlockTags.LEAVES).addTag(Tags.BlockItems.GLASS_BLOCKS.block()).addTag(Tags.BlockItems.GLASS_PANES.block())
                .add(key(Blocks.GRAVEL), key(Blocks.CLAY), key(Blocks.FARMLAND), key(Blocks.DIRT_PATH), key(Blocks.SOUL_SAND), key(Blocks.SOUL_SOIL),
                        key(Blocks.SNOW), key(Blocks.SNOW_BLOCK));
    }

    /**
     * EF-IA-07: zombie, husk and zombie villager dig; with stray, bogged and skeleton they are frenzy undead. The drowned is in
     * neither [HORS-MITE]. MITE diggers (EF-IA-01) are optional entries, live once the mob domain registers them.
     */
    public static void entityTypeTags(Function<TagKey<EntityType<?>>, TagAppender<EntityType<?>>> tag) {
        tag.apply(AiContent.DIGGER).add(key(EntityTypes.ZOMBIE), key(EntityTypes.HUSK), key(EntityTypes.ZOMBIE_VILLAGER))
                .addOptional(mob("ghoul")).addOptional(mob("revenant")).addOptional(mob("earth_elemental"));
        tag.apply(AiContent.FRENZY_UNDEAD).addTag(AiContent.DIGGER)
                .add(key(EntityTypes.SKELETON), key(EntityTypes.STRAY), key(EntityTypes.BOGGED));
    }

    /** EF-IA-06 (analysis/03 §6.3): R196 max spawn Y per MITE mob. Entries apply once the mob is registered. */
    static final Map<String, Integer> SPAWN_DEPTHS = Map.ofEntries(
            Map.entry("ghoul", 56), Map.entry("wight", 48), Map.entry("revenant", 44), Map.entry("invisible_stalker", 40),
            Map.entry("earth_elemental", 40), Map.entry("demon_spider", 32), Map.entry("phase_spider", 32), Map.entry("hellhound", 32),
            Map.entry("shadow", 32), Map.entry("bone_lord", 32), Map.entry("nightwing", 32), Map.entry("vampire_bat", 48),
            Map.entry("blob", 40), Map.entry("ooze", 32), Map.entry("pudding", 24));

    /** Changelog l.435 (R140) and l.457 (R137): villagers flee earth elementals and gelatinous cubes [INFÉRÉ: within 10 and 8 blocks]. */
    static final Map<String, Integer> VILLAGER_FLEES = Map.of("earth_elemental", 10, "clay_golem", 10, "jelly", 8, "blob", 8, "ooze", 8, "pudding", 8);

    public static final class DataMaps extends DataMapProvider {
        public DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void gather(HolderLookup.Provider provider) {
            var depth = builder(AiContent.SPAWN_DEPTH);
            SPAWN_DEPTHS.forEach((name, y) -> optional(depth, name, new AiContent.SpawnDepth(y)));
            var xp = builder(dev.mite.player.XpRules.XP_MULTIPLIER); // ET-B-05
            dev.mite.player.XpRules.VANILLA_MULTIPLIERS.forEach((type, m) -> xp.add(key(type), m, false));
            var flee = builder(NeoForgeDataMaps.ACCEPTABLE_VILLAGER_DISTANCES);
            VILLAGER_FLEES.forEach((name, distance) -> optional(flee, name, new AcceptableVillagerDistance(distance)));
            flee.add(key(EntityTypes.SLIME), new AcceptableVillagerDistance(8), false);
        }

        /** An entry for a MITE mob, live once the mob domain registers it. */
        private static <T> void optional(Builder<T, EntityType<?>> builder, String name, T value) {
            var key = mob(name);
            builder.add(key, value, false, new RegisteredCondition<>(key));
        }

        @Override
        public String getName() {
            return "Data maps - hardscrabble ai";
        }
    }

    private static ResourceKey<Block> key(Block block) {
        return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
    }

    private static ResourceKey<EntityType<?>> key(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).orElseThrow();
    }

    private static ResourceKey<EntityType<?>> mob(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE, MiteMod.id(name));
    }
}
