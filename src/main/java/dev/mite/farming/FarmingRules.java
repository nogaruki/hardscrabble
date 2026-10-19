package dev.mite.farming;

import dev.mite.config.MiteConfig;
import dev.mite.lunar.Lunar;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.ShelfMushroomBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.mite.progression.MiteTriggers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Farming rules: demanding crops (EF-AGR-05), blight (EF-AGR-06), manure (EF-AGR-07), windfalls (EF-AGR-03), item lifespan (EF-AGR-11),
 * no bone meal growth (R128) and slow saplings (R146).
 */
public final class FarmingRules {
    private FarmingRules() {}

    // ponytail: blight rates are not in the spec [NON VÉRIFIÉ]; tune in playtest. Per crop growth attempt (random tick).
    static final float BLIGHT_ONSET = 0.002f, BLOOD_MOON_FACTOR = 10f, BLIGHT_SPREAD = 0.25f, BLIGHT_WITHER = 0.1f;
    /** EF-AGR-07: +50 % growth = half the growth steps on manured farmland give one more stage. */
    static final float MANURE_BONUS = 0.5f;
    /**
     * R146: saplings take 4x longer on average. A sapling grows in two equally likely steps (stage 0 to 1, then the tree);
     * letting only one tree attempt in 7 through turns 1 + 1 expected steps into 1 + 7, i.e. 4x.
     */
    static final int SAPLING_TREE_ODDS = 7;
    /**
     * ponytail: windfalls sample one random block per chunk near players every second instead of hooking leaf random ticks. Each
     * chunk is sampled once however many players stand near it (spectators do not count), so the rate does not grow with the
     * number of players. [INFÉRÉ] Odds 1 in 144 per sample: the density of the former 4 samples at 1 in 64 per 3x3 chunks.
     */
    static final int WINDFALL_CHUNKS = 1, WINDFALL_HEIGHT = 16, WINDFALL_ODDS = 144;
    /** Changelog l.368 (R148): a crop out of a row or group skips half its growth steps. */
    static final float ISOLATED_GROWTH = 0.5f;

    /**
     * EF-AGR-05 (R130): crops grow only in daylight, under the sky: nothing that dims sky light above them (glass is fine).
     * Scans the blocks instead of reading sky light, which the light engine updates late.
     */
    public static boolean canGrow(Level level, BlockPos pos) {
        return level.isBrightOutside() && underSky(level, pos);
    }

    /** Nothing above the crop dims sky light (the daylight half of {@link #canGrow}). */
    public static boolean underSky(Level level, BlockPos pos) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        for (BlockPos.MutableBlockPos p = pos.mutable().move(Direction.UP); p.getY() < top; p.move(Direction.UP)) {
            if (level.getBlockState(p).getLightDampening() > 0) return false;
        }
        return true;
    }

    /** Changelog l.542 (R130): crops and melon and pumpkin stems need full daylight; torch or lava light is not enough. */
    static boolean needsDaylight(Block block) {
        return block instanceof CropBlock || block instanceof StemBlock;
    }

    /**
     * Changelog l.368 (R148): true when the crop belongs to a row or group of at least 3 crops of its type (side by side):
     * it has two such neighbours, or one that touches another. Otherwise it grows at half rate.
     */
    public static boolean inRow(LevelReader level, BlockPos pos) {
        Block crop = level.getBlockState(pos).getBlock();
        BlockPos neighbour = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!loadedState(level, pos.relative(d)).is(crop)) continue;
            if (neighbour != null) return true;
            neighbour = pos.relative(d);
        }
        if (neighbour == null) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos next = neighbour.relative(d);
            if (!next.equals(pos) && loadedState(level, next).is(crop)) return true;
        }
        return false;
    }

    /**
     * The block at {@code pos}, or air in a chunk that is not loaded: never loads a chunk (the I17 fast-forward reads the
     * neighbours of a chunk on its border while it loads, and a synchronous load there would stall the server tick).
     */
    static BlockState loadedState(LevelReader level, BlockPos pos) {
        if (!(level instanceof Level l)) return level.getBlockState(pos);
        LevelChunk chunk = l.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    /**
     * Changelog l.614 (R127): red mushrooms do not spread, nor take manure, under an open sky in the rain. The sky is scanned as
     * in {@link #underSky} rather than read from sky light ({@code isRainingAt}), which the light engine updates late.
     */
    public static boolean rainStopsMushroom(Level level, BlockPos pos, BlockState state) {
        return state.is(Blocks.RED_MUSHROOM) && level.isRaining() && underSky(level, pos)
                && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
    }

    // ---- per-chunk position sets ----

    static LongOpenHashSet set(Level level, BlockPos pos, Supplier<AttachmentType<LongOpenHashSet>> type) {
        return level.getChunkAt(pos).getData(type);
    }

    static boolean mark(Level level, BlockPos pos, Supplier<AttachmentType<LongOpenHashSet>> type, boolean on) {
        LevelChunk chunk = level.getChunkAt(pos);
        LongOpenHashSet set = chunk.getData(type);
        boolean changed = on ? set.add(pos.asLong()) : set.remove(pos.asLong());
        if (changed) chunk.markUnsaved();
        return changed;
    }

    // ---- EF-AGR-06 blight ----

    public static boolean isBlighted(Level level, BlockPos pos) {
        if (!set(level, pos, FarmingContent.BLIGHT).contains(pos.asLong())) return false;
        if (level.getBlockState(pos).getBlock() instanceof CropBlock) return true;
        heal(level, pos); // snowed on, dissolved or broken without drops: the next crop planted here starts healthy
        return false;
    }

    public static boolean infect(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof CropBlock) || !mark(level, pos, FarmingContent.BLIGHT, true)) return false;
        level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0);
        return true;
    }

    /** Clears the blight at {@code pos}; false when it was not blighted. */
    public static boolean heal(Level level, BlockPos pos) {
        return mark(level, pos, FarmingContent.BLIGHT, false);
    }

    /** Infects one random healthy crop next to {@code pos}; false when there is none. */
    public static boolean spread(ServerLevel level, BlockPos pos, RandomSource random) {
        List<BlockPos> healthy = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (level.getBlockState(n).getBlock() instanceof CropBlock && !isBlighted(level, n)) healthy.add(n);
        }
        return !healthy.isEmpty() && infect(level, healthy.get(random.nextInt(healthy.size())));
    }

    /** EF-AGR-06 / EF-MUL-05: more frequent on blood moons, except on a dedicated server unless {@code dedicated_blight}. */
    static float onsetChance(ServerLevel level) {
        return onsetChance(Lunar.isBloodMoon(level), level.getServer().isDedicatedServer());
    }

    /** The configured {@code dedicated_blight} applied to {@link #onsetChance(boolean, boolean, boolean)}. */
    static float onsetChance(boolean bloodMoon, boolean dedicatedServer) {
        return onsetChance(bloodMoon, dedicatedServer, MiteConfig.SPEC.isLoaded() && MiteConfig.DEDICATED_BLIGHT.getAsBoolean());
    }

    static float onsetChance(boolean bloodMoon, boolean dedicatedServer, boolean dedicatedBlight) {
        return bloodMoon && (!dedicatedServer || dedicatedBlight) ? BLIGHT_ONSET * BLOOD_MOON_FACTOR : BLIGHT_ONSET;
    }

    /** One growth attempt of a crop: returns true when blight stops it from growing. */
    static boolean blightTick(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!isBlighted(level, pos)) return random.nextFloat() < onsetChance(level) && infect(level, pos);
        if (random.nextFloat() < BLIGHT_SPREAD) spread(level, pos, random);
        if (random.nextFloat() < BLIGHT_WITHER) {
            heal(level, pos);
            level.setBlockAndUpdate(pos, FarmingContent.deadVariant(state.getBlock()).defaultBlockState());
        }
        return true;
    }

    // ---- EF-AGR-07 manure ----

    /** Manures the farmland at {@code pos} or under the crop at {@code pos}; false if there is none or it already is. */
    public static boolean manure(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) pos = pos.below();
        if (!level.getBlockState(pos).is(Blocks.FARMLAND) || isManured(level, pos)) return false;
        if (!level.isClientSide()) mark(level, pos, FarmingContent.MANURED, true);
        return true;
    }

    public static boolean isManured(Level level, BlockPos farmland) {
        if (!set(level, farmland, FarmingContent.MANURED).contains(farmland.asLong())) return false;
        if (level.getBlockState(farmland).is(Blocks.FARMLAND)) return true;
        mark(level, farmland, FarmingContent.MANURED, false); // trampled or dug: the manure is gone
        return false;
    }

    static void growBonus(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state) && isManured(level, pos.below())
                && level.getRandom().nextFloat() < MANURE_BONUS) {
            level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
        }
    }

    /**
     * R128: bone meal never grows crops, saplings, cocoa, melon and pumpkin stems, berry bushes or mushrooms. The modern
     * equivalents follow the same rule: azaleas (saplings), cave vines and their glow berries and the other growing vines,
     * bamboo, nether fungi and shelf mushrooms.
     */
    public static boolean refusesBonemeal(Block block) {
        return block instanceof CropBlock || block instanceof SaplingBlock || block instanceof CocoaBlock || block instanceof StemBlock
                || block instanceof SweetBerryBushBlock || block instanceof MushroomBlock || block instanceof PitcherCropBlock
                || block instanceof AzaleaBlock || block instanceof GrowingPlantBlock || block instanceof BambooSaplingBlock
                || block instanceof BambooStalkBlock || block instanceof NetherFungusBlock || block instanceof ShelfMushroomBlock;
    }

    /** R146: true when this tree growth attempt of a sapling is cancelled (see {@link #SAPLING_TREE_ODDS}). */
    static boolean slowsSapling(Level level, BlockPos pos, RandomSource random) {
        return level.getBlockState(pos).getBlock() instanceof SaplingBlock && random.nextInt(SAPLING_TREE_ODDS) != 0;
    }

    /** R128: manure supersizes a mushroom (instead of bone meal); false if it cannot grow here. Server side. */
    public static boolean manureMushroom(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MushroomBlock mushroom && !rainStopsMushroom(level, pos, state)
                && mushroom.growMushroom(level, pos, state, level.getRandom());
    }

    // ---- EF-AGR-03 windfalls ----

    static void windfall(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LeavesBlock) || state.getValue(LeavesBlock.PERSISTENT) || !level.getBlockState(pos.below()).isAir()) return;
        if (level.getBiome(pos).is(Tags.Biomes.IS_SWAMP) && random.nextBoolean()) return; // changelog l.701 (R123): swamp trees -50 %
        ItemStack drop = new ItemStack(Items.STICK);
        if (random.nextBoolean()) {
            if (state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES)) drop = new ItemStack(Items.APPLE);
            else if (state.is(Blocks.JUNGLE_LEAVES)) drop = new ItemStack(random.nextBoolean() ? FarmingContent.ORANGE.get() : FarmingContent.BANANA.get());
        }
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() - 0.3, pos.getZ() + 0.5, drop));
    }

    static void registerEvents(IEventBus bus) {
        bus.addListener((CropGrowEvent.Pre e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || !needsDaylight(e.getState().getBlock())) return;
            boolean crop = e.getState().getBlock() instanceof CropBlock;
            if (crop && blightTick(level, e.getPos(), e.getState(), level.getRandom()) || !canGrow(level, e.getPos())
                    || crop && !inRow(level, e.getPos()) && level.getRandom().nextFloat() < ISOLATED_GROWTH) e.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        });
        bus.addListener((CropGrowEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) growBonus(level, e.getPos(), e.getState());
        });
        bus.addListener((BlockGrowFeatureEvent e) -> {
            if (e.getLevel() instanceof Level level && slowsSapling(level, e.getPos(), e.getRandom())) e.setCanceled(true);
        });
        // Bone meal heals a sick crop and never makes a crop, sapling, stem, bush or mushroom grow (R128). The client does not
        // know the blight (not synced): it lets a crop through and the server decides.
        bus.addListener((BonemealEvent e) -> {
            if (!refusesBonemeal(e.getState().getBlock())) return;
            boolean crop = e.getState().getBlock() instanceof CropBlock;
            if (e.getLevel().isClientSide()) {
                e.setSuccessful(crop);
                return;
            }
            boolean sick = crop && isBlighted(e.getLevel(), e.getPos());
            if (sick) {
                heal(e.getLevel(), e.getPos());
                e.getStack().shrink(1);
                if (e.getPlayer() instanceof ServerPlayer p) MiteTriggers.EVENT.get().trigger(p, "plant_doctor");
                ((ServerLevel) e.getLevel()).sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getPos().getX() + 0.5, e.getPos().getY() + 0.5, e.getPos().getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
            }
            e.setSuccessful(sick);
        });
        // Blight halves the yield (each stack rounded at random, so a single wheat or seed survives half the time); harvesting or
        // replanting clears the position. A player's break removes the block before the drops: the set is read, not the block.
        bus.addListener((BlockDropsEvent e) -> {
            if (!heal(e.getLevel(), e.getPos())) return;
            RandomSource random = e.getLevel().getRandom();
            e.getDrops().forEach(i -> i.getItem().setCount(halve(i.getItem().getCount(), random)));
            e.getDrops().removeIf(i -> i.getItem().isEmpty());
        });
        // A creative break drops nothing: clear the blight so a villager's replant (no place event) is born healthy.
        bus.addListener((BreakBlockEvent e) -> {
            if (e.getPlayer().isCreative() && e.getLevel() instanceof Level level) heal(level, e.getPos());
        });
        bus.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (!(e.getLevel() instanceof Level level)) return;
            heal(level, e.getPos());
            // Changelog l.762 (R119): a blueberry (sweet berry) bush placed on farmland turns it into dirt.
            if (e.getPlacedBlock().getBlock() instanceof SweetBerryBushBlock && level.getBlockState(e.getPos().below()).is(Blocks.FARMLAND))
                level.setBlockAndUpdate(e.getPos().below(), Blocks.DIRT.defaultBlockState());
        });
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (e.getEntity() instanceof ItemEntity item && !e.getLevel().isClientSide() && MiteConfig.SPEC.isLoaded()) {
                item.lifespan = MiteConfig.ITEM_LIFESPAN_TICKS.getAsInt();
            }
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
            RandomSource random = level.getRandom();
            SoilRules.forChunksNearPlayers(level, WINDFALL_CHUNKS, (player, chunk) -> {
                if (random.nextInt(WINDFALL_ODDS) != 0) return;
                int y = player.getBlockY() + random.nextInt(2 * WINDFALL_HEIGHT + 1) - WINDFALL_HEIGHT;
                windfall(level, chunk.getPos().getBlockAt(random.nextInt(16), y, random.nextInt(16)), random);
            });
        });
    }

    /** Half of {@code count}, an odd half rounded up or down at random. */
    static int halve(int count, RandomSource random) {
        return count / 2 + (count % 2 == 1 && random.nextBoolean() ? 1 : 0);
    }
}
