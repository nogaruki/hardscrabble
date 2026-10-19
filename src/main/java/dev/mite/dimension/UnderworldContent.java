package dev.mite.dimension;

import dev.mite.MiteMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import java.util.List;
import java.util.function.Function;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Underworld (EF-DIM-01, M01) and its Mantle block. All registry ids are {@code hardscrabble:underworld}.
 *
 * <p>Height layout (arbitrage of EF-DIM-01 "hauteur à extraire", Q-22 "dimension séparée sous la bedrock"): Y -128..127,
 * 256 blocks like a 1.6.4 world, so a portal on Overworld bedrock (Y ~ -63) arrives at the same Y, in the lower middle
 * of the cave. From the bottom: Mantle mixed into the bedrock floor (-128..-126), bedrock floor (to -123), bedrock patches
 * (to -118), then 2-block bedrock plates every 16 blocks through the stone of the bottom half (to Y 0), bedrock roof (123..127).
 * Stone only, no deepslate. Nether-like 3D cave noise over the whole height; mycelium and mushrooms on cave floors between
 * 24 and 96 blocks above the bottom; nothing spawns on mycelium (changelog l.443).
 */
public final class UnderworldContent {
    private UnderworldContent() {}

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, MiteMod.id("underworld"));
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, MiteMod.id("underworld"));
    public static final ResourceKey<DimensionType> TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, MiteMod.id("underworld"));
    public static final ResourceKey<LevelStem> STEM = ResourceKey.create(Registries.LEVEL_STEM, MiteMod.id("underworld"));
    public static final ResourceKey<NoiseGeneratorSettings> NOISE = ResourceKey.create(Registries.NOISE_SETTINGS, MiteMod.id("underworld"));
    public static final ResourceKey<MaterialRule> MATERIAL_RULE = ResourceKey.create(Registries.MATERIAL_RULE, MiteMod.id("underworld"));

    public static final int MIN_Y = -128, HEIGHT = 256;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    /** Permanent block at the bottom of the Underworld (changelog l.174-175): unbreakable, no light, no fire (dom-M-07). */
    public static final DeferredBlock<Block> MANTLE = BLOCKS.registerSimpleBlock("mantle", p -> p.mapColor(MapColor.FIRE)
            .strength(-1, 3600000).noLootTable().isValidSpawn(Blocks::never));

    /** Changelog l.443 (R139): mobs can no longer spawn on mycelium or mushroom caps. */
    public static final TagKey<Block> NO_SPAWN_ON = TagKey.create(Registries.BLOCK, MiteMod.id("no_spawn_on"));

    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        for (Block b : new Block[] {Blocks.MYCELIUM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK})
            tag.apply(NO_SPAWN_ON).add(BuiltInRegistries.BLOCK.getResourceKey(b).orElseThrow());
        // Permanent like bedrock: withers, the dragon and worldgen features do not break or replace it.
        for (TagKey<Block> t : List.of(BlockTags.WITHER_IMMUNE, BlockTags.DRAGON_IMMUNE, BlockTags.FEATURES_CANNOT_REPLACE))
            tag.apply(t).add(MANTLE.unwrapKey().orElseThrow());
    }

    /** Natural and world generation spawns only: spawners, eggs and commands still work. */
    public static boolean spawnForbidden(BlockState below, EntitySpawnReason reason) {
        return (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) && below.is(NO_SPAWN_ON);
    }

    static {
        ITEMS.registerSimpleBlockItem(MANTLE);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener((MobSpawnEvent.SpawnPlacementCheck e) -> {
            if (spawnForbidden(e.getLevel().getBlockState(e.getPos().below()), e.getSpawnType())) e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        });
    }
}
