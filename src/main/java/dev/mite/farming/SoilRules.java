package dev.mite.farming;

import dev.mite.config.MiteConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.mite.entity.mob.GelatinousCube;
import dev.mite.equipment.EquipmentContent;
import dev.mite.metabolism.MetabolismContent;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * EF-AGR-12 side rules: soil depletion (I08), snow killing crops and stems and slimes blighting or destroying them (I09),
 * farming mycelium (I12), cocoa only atop natural jungle trunks (I15), chunk fast-forward (I17), worms composting in chests (l.178).
 */
public final class SoilRules {
    private SoilRules() {}

    // ponytail: every rate below is [INFÉRÉ], the spec gives none; tune in playtest.
    /** I08: a crop on depleted farmland skips half its growth steps. */
    static final float DEPLETED_GROWTH = 0.5f;
    /** I12: odds per second for each qualifying manured farmland near a player to sprout a brown mushroom. */
    static final int MYCELIUM_ODDS = 300, DARK = 13;
    /** Radius in chunks around players of the farming mycelium and worm composting simulation. */
    static final int NEAR_PLAYER_CHUNKS = 2;
    /** I15: cocoa only on the top 3 logs. */
    static final int COCOA_TOP = 3;
    /** I17: at most 768 000 ticks caught up; crops grow by day only and a growth step succeeds about 1 in 9 (moist farmland). */
    static final long FAST_FORWARD_MAX = 768_000;
    static final double DAYLIGHT = 0.5, STAGE_CHANCE = 1 / 9.0;
    /** Fast-forward block changes: sent to clients without neighbour or shape updates, which would read (and load) the next chunk. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /**
     * Changelog l.178 (R172): worms in a chest eat plant material and food. Every COMPOST_INTERVAL ticks, each worm in a chest
     * near a player eats one item with odds 1 in WORM_BITE_ODDS (about one item per worm per quarter day), and one eaten item
     * in MANURE_ODDS leaves a manure. [INFÉRÉ: reference/item_composting.txt is not public.]
     */
    static final int COMPOST_INTERVAL = 1200, WORM_BITE_ODDS = 5, MANURE_ODDS = 8;

    /** I17: chunks loaded from disk, fast-forwarded on the next level tick (server thread only). */
    private static final ArrayDeque<LevelChunk> PENDING = new ArrayDeque<>();

    // ---- I08 soil depletion ----

    /** Worn-out farmland; manuring it heals it. */
    public static boolean isDepleted(Level level, BlockPos farmland) {
        if (!FarmingRules.set(level, farmland, FarmingContent.DEPLETED).contains(farmland.asLong())) return false;
        if (level.getBlockState(farmland).is(Blocks.FARMLAND) && !FarmingRules.isManured(level, farmland)) return true;
        FarmingRules.mark(level, farmland, FarmingContent.DEPLETED, false);
        return false;
    }

    /** A mature crop was harvested: it eats the manure of its farmland, or wears the farmland out. */
    public static void harvested(Level level, BlockPos farmland) {
        if (!level.getBlockState(farmland).is(Blocks.FARMLAND)) return;
        if (FarmingRules.isManured(level, farmland)) FarmingRules.mark(level, farmland, FarmingContent.MANURED, false);
        else FarmingRules.mark(level, farmland, FarmingContent.DEPLETED, true);
    }

    // ---- I09 snow and slimes ----

    public static boolean snowedOn(Level level, BlockPos pos) {
        return level.precipitationAt(pos) == Biome.Precipitation.SNOW;
    }

    /**
     * Changelog l.634 (R127): falling snow destroys crops, ripe ones included, and melon and pumpkin stems. Called (via
     * ServerLevelSnowMixin) for the column vanilla picks for its precipitation tick; the plant is the first non-blocking block on top.
     */
    public static boolean snowfall(ServerLevel level, BlockPos column) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        return snowedOn(level, top) && snowKills(level, top);
    }

    /** The snow falling on {@code pos} kills the crop or stem there; no harvest. */
    public static boolean snowKills(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (!(block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock) || !level.destroyBlock(pos, false)) return false;
        FarmingRules.heal(level, pos); // no drops, so no BlockDropsEvent to clear the blight
        return true;
    }

    /** Changelog l.493 (R134): oozes, puddings and magma cubes destroy crops and the other plants they touch. */
    public static boolean destroysPlants(Entity cube) {
        return cube instanceof MagmaCube || cube instanceof GelatinousCube g && g.kind.acidic();
    }

    /**
     * Changelog l.493 (R134): a cube standing in a plant (its feet are in the plant block, just above the soil). Slimes, jellies
     * and blobs blight the crop; oozes, puddings and magma cubes destroy any plant (when mob griefing allows it).
     */
    public static boolean touch(ServerLevel level, AbstractCubeMob cube) {
        BlockPos pos = BlockPos.containing(cube.getX(), cube.getY() + 0.25, cube.getZ());
        if (destroysPlants(cube)) {
            if (!(level.getBlockState(pos).getBlock() instanceof VegetationBlock) || !EventHooks.canEntityGrief(level, cube)
                    || !level.destroyBlock(pos, false, cube)) return false;
            FarmingRules.heal(level, pos); // no drops, so no BlockDropsEvent to clear the blight
            return true;
        }
        return cube instanceof Slime && FarmingRules.infect(level, pos);
    }

    // ---- l.178 worm composting ----

    /** Plant material (composter items) and food (MITE nutrition), but not the worms themselves. */
    public static boolean compostable(ItemStack stack) {
        return !stack.isEmpty() && !stack.is(EquipmentContent.WORM.get()) && !stack.is(EquipmentContent.COOKED_WORM.get())
                && (stack.has(DataComponents.COMPOSTABLE) || stack.typeHolder().getData(MetabolismContent.NUTRITION) != null);
    }

    /** One composting step of a chest: each worm may eat one compostable item; some eaten items leave a manure. */
    public static void compost(ChestBlockEntity chest, RandomSource random) {
        int worms = 0, manure = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(EquipmentContent.WORM.get())) worms += chest.getItem(i).getCount();
        for (int w = 0; w < worms; w++) {
            if (random.nextInt(WORM_BITE_ODDS) != 0) continue;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!compostable(chest.getItem(i))) continue;
                chest.removeItem(i, 1);
                if (random.nextInt(MANURE_ODDS) == 0) manure++;
                break;
            }
        }
        for (int i = 0; i < chest.getContainerSize() && manure > 0; i++) {
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty()) {
                chest.setItem(i, new ItemStack(FarmingContent.MANURE.get(), manure));
                manure = 0;
            } else if (slot.is(FarmingContent.MANURE.get())) {
                int add = Math.min(manure, slot.getMaxStackSize() - slot.getCount());
                slot.grow(add);
                manure -= add;
            }
        }
        if (worms > 0) chest.setChanged();
    }

    /**
     * Each loaded chunk within {@code radius} chunks of a player (spectators excluded) once, however many players are near it,
     * with the first player found near it.
     */
    static void forChunksNearPlayers(ServerLevel level, int radius, BiConsumer<ServerPlayer, LevelChunk> action) {
        LongOpenHashSet seen = new LongOpenHashSet();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            ChunkPos center = player.chunkPosition();
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                    LevelChunk chunk = seen.add(ChunkPos.pack(x, z)) ? level.getChunkSource().getChunkNow(x, z) : null;
                    if (chunk != null) action.accept(player, chunk);
                }
            }
        }
    }

    private static void compostNearPlayers(ServerLevel level) {
        forChunksNearPlayers(level, NEAR_PLAYER_CHUNKS, (player, chunk) -> chunk.getBlockEntities().values().forEach(be -> {
            if (be instanceof ChestBlockEntity chest && chest.getLootTable() == null) compost(chest, level.getRandom());
        }));
    }

    // ---- I12 farming mycelium ----

    /** A brown mushroom sprouts on moist, manured farmland in the dark. */
    public static boolean sprout(Level level, BlockPos farmland) {
        BlockState soil = level.getBlockState(farmland);
        BlockPos above = farmland.above();
        if (!soil.is(Blocks.FARMLAND) || soil.getValue(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE || !level.isEmptyBlock(above)
                || level.getRawBrightness(above, 0) >= DARK || !FarmingRules.isManured(level, farmland)) return false;
        return level.setBlockAndUpdate(above, Blocks.BROWN_MUSHROOM.defaultBlockState());
    }

    /** Mushrooms survive on farmland whatever the light, or the farmland would pop them. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.OVERRIDES_MUSHROOM_LIGHT_REQUIREMENT).add(BuiltInRegistries.BLOCK.getResourceKey(Blocks.FARMLAND).orElseThrow());
    }

    private static void sproutNearPlayers(ServerLevel level) {
        RandomSource random = level.getRandom();
        forChunksNearPlayers(level, NEAR_PLAYER_CHUNKS, (player, chunk) -> {
            if (!chunk.hasData(FarmingContent.MANURED)) return;
            for (long pos : chunk.getData(FarmingContent.MANURED).toLongArray()) {
                if (random.nextInt(MYCELIUM_ODDS) == 0) sprout(level, BlockPos.of(pos));
            }
        });
    }

    // ---- I15 cocoa ----

    /** The log is one of the top 3 of a jungle trunk capped by natural (non-persistent) leaves. */
    public static boolean cocoaAllowed(Level level, BlockPos log) {
        BlockPos.MutableBlockPos p = log.mutable();
        for (int i = 0; i < COCOA_TOP && level.getBlockState(p).is(BlockTags.SUPPORTS_COCOA); i++) {
            BlockState above = level.getBlockState(p.move(Direction.UP));
            if (above.getBlock() instanceof LeavesBlock && !above.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    // ---- I17 chunk fast-forward ----

    static boolean fastForwardEnabled() {
        return MiteConfig.SPEC.isLoaded() && MiteConfig.CHUNK_FAST_FORWARD.getAsBoolean();
    }

    /**
     * Grows the crop at {@code pos} by the stages expected in {@code elapsed} ticks (statistical, stochastic rounding), with the
     * rules of a loaded chunk: a blighted crop never grows and withers on 1 growth attempt in 10; manure gives +50 %.
     */
    public static void fastForward(ServerLevel level, BlockPos pos, long elapsed) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state) || !FarmingRules.underSky(level, pos)) return;
        double randomTicks = Math.min(elapsed, FAST_FORWARD_MAX) * level.getGameRules().get(GameRules.RANDOM_TICK_SPEED) / 4096.0;
        if (FarmingRules.isBlighted(level, pos)) {
            if (level.getRandom().nextDouble() < 1 - Math.pow(1 - FarmingRules.BLIGHT_WITHER, randomTicks * DAYLIGHT)) {
                FarmingRules.heal(level, pos);
                level.setBlock(pos, FarmingContent.deadVariant(crop).defaultBlockState(), QUIET);
            }
            return;
        }
        double stages = randomTicks * DAYLIGHT * STAGE_CHANCE * (isDepleted(level, pos.below()) ? DEPLETED_GROWTH : 1)
                * (FarmingRules.inRow(level, pos) ? 1 : FarmingRules.ISOLATED_GROWTH)
                * (FarmingRules.isManured(level, pos.below()) ? 1 + FarmingRules.MANURE_BONUS : 1);
        int add = (int) stages + (level.getRandom().nextDouble() < stages % 1 ? 1 : 0);
        if (add > 0) level.setBlock(pos, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(state) + add)), QUIET);
    }

    static void fastForward(ServerLevel level, LevelChunk chunk, long elapsed) {
        List<BlockPos> crops = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof CropBlock)) continue;
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).getBlock() instanceof CropBlock) crops.add(chunk.getPos().getBlockAt(x, y0 + y, z));
                    }
                }
            }
        }
        crops.forEach(pos -> fastForward(level, pos, elapsed));
    }

    static void register(IEventBus bus) {
        bus.addListener((CropGrowEvent.Pre e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || !(e.getState().getBlock() instanceof CropBlock)) return;
            if (isDepleted(level, e.getPos().below()) && level.getRandom().nextFloat() < DEPLETED_GROWTH) e.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        });
        bus.addListener((BlockDropsEvent e) -> {
            if (e.getState().getBlock() instanceof CropBlock crop && crop.isMaxAge(e.getState())) harvested(e.getLevel(), e.getPos().below());
        });
        bus.addListener((EntityTickEvent.Post e) -> {
            if (e.getEntity() instanceof AbstractCubeMob cube && cube.tickCount % 10 == 0 && cube.level() instanceof ServerLevel level) touch(level, cube);
        });
        bus.addListener((BlockEvent.EntityPlaceEvent e) -> {
            BlockState placed = e.getPlacedBlock();
            if (placed.getBlock() instanceof CocoaBlock && e.getLevel() instanceof Level level
                    && !cocoaAllowed(level, e.getPos().relative(placed.getValue(CocoaBlock.FACING)))) e.setCanceled(true);
        });
        bus.addListener((ChunkDataEvent.Load e) -> {
            if (fastForwardEnabled() && e.getData().lastUpdateTime() > 0) e.getChunk().setData(FarmingContent.LAST_SAVED, e.getData().lastUpdateTime());
        });
        bus.addListener((ChunkEvent.Load e) -> {
            if (!e.isNewChunk() && !e.getLevel().isClientSide() && e.getChunk().hasData(FarmingContent.LAST_SAVED)) PENDING.add(e.getChunk());
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            // Drained first: a chunk loading while this batch runs is queued for the next tick, not added under the iteration.
            List<LevelChunk> batch = new ArrayList<>();
            PENDING.removeIf(chunk -> chunk.getLevel() == level && batch.add(chunk));
            batch.forEach(chunk -> fastForward(level, chunk, level.getGameTime() - chunk.removeData(FarmingContent.LAST_SAVED)));
            if (level.getGameTime() % 20 == 0) sproutNearPlayers(level);
            if (level.getGameTime() % COMPOST_INTERVAL == 0) compostNearPlayers(level);
        });
    }
}
