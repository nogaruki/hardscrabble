package dev.mite.dimension.runegate;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.player.ProgressionService;
import dev.mite.progression.MiteTriggers;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Runestones and runegates (EF-DIM-06, ET-M-04, M06). A runegate is a lit Nether portal with a runestone on each of the four
 * frame corners. It sends a player who carries nothing to a far point and wipes their positive XP ("amnesia"); anyone
 * carrying an item is not teleported. The destination depends only on the world seed, the four runes and the metal, so the
 * same runes always lead to the same place. Mithril: within {@code runegate_mithril_range} of the world origin; adamantium
 * (all four stones): 8 times that radius and at least 20 km from the origin. Called by NetherPortalBlockMixin (MX-06);
 * works in dimensions without a ceiling, elsewhere the portal stays a plain portal.
 */
public final class Runegate {
    private Runegate() {}

    /** EF-DIM-06: adamantium destinations are at least this far from the origin (R154). */
    public static final int ADAMANTIUM_MIN_DISTANCE = 20000;
    /** EF-DIM-06: adamantium range multiplier over mithril (R152). */
    public static final int ADAMANTIUM_FACTOR = 8;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    public static final DeferredBlock<RunestoneBlock> MITHRIL_RUNESTONE = runestone("mithril_runestone", false);
    public static final DeferredBlock<RunestoneBlock> ADAMANTIUM_RUNESTONE = runestone("adamantium_runestone", true);
    public static final List<DeferredBlock<RunestoneBlock>> RUNESTONES = List.of(MITHRIL_RUNESTONE, ADAMANTIUM_RUNESTONE);

    private static DeferredBlock<RunestoneBlock> runestone(String name, boolean adamantium) {
        // Obsidian strength: the stone is an obsidian block with metal runes.
        DeferredBlock<RunestoneBlock> block = BLOCKS.registerBlock(name, p -> new RunestoneBlock(adamantium, p),
                p -> p.mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(50, 1200).lightLevel(s -> 4));
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    /** Called by MiteBlockTags: mined like obsidian. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        for (var b : RUNESTONES) {
            tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(b.unwrapKey().orElseThrow());
            tag.apply(BlockTags.NEEDS_DIAMOND_TOOL).add(b.unwrapKey().orElseThrow());
        }
    }

    /** A runegate: the 16-bit rune code of its corners (bottom-left, bottom-right, top-left, top-right) and its metal. */
    public record Gate(int runes, boolean adamantium) {}

    /** The runegate around the portal block {@code portal}, if its four frame corners are runestones. */
    public static Optional<Gate> find(Level level, BlockPos portal) {
        BlockState state = level.getBlockState(portal);
        if (!state.is(Blocks.NETHER_PORTAL)) return Optional.empty();
        Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
        var rect = BlockUtil.getLargestRectangleAround(portal, axis, 21, Direction.Axis.Y, 21, p -> level.getBlockState(p) == state);
        Direction right = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        BlockPos bottomLeft = rect.minCorner.relative(right, -1).below(), bottomRight = rect.minCorner.relative(right, rect.axis1Size).below();
        int up = rect.axis2Size + 1, runes = 0;
        boolean adamantium = true;
        List<BlockPos> corners = List.of(bottomLeft, bottomRight, bottomLeft.above(up), bottomRight.above(up));
        for (int i = 0; i < 4; i++) {
            BlockState corner = level.getBlockState(corners.get(i));
            if (!(corner.getBlock() instanceof RunestoneBlock stone)) return Optional.empty();
            runes |= corner.getValue(RunestoneBlock.RUNE) << (4 * i);
            adamantium &= stone.adamantium;
        }
        return Optional.of(new Gate(runes, adamantium));
    }

    /**
     * Routing function: x/z of the destination (y = 0). Uniform over the allowed ring around the origin: radius
     * [0, range] for mithril, [20 000, max(8 x range, 20 000)] for adamantium.
     */
    public static BlockPos destination(long seed, Gate gate, int mithrilRange) {
        RandomSource random = RandomSource.create(seed ^ gate.runes() * 0x9E3779B97F4A7C15L ^ (gate.adamantium() ? 0xADA0L : 0L));
        double min = gate.adamantium() ? ADAMANTIUM_MIN_DISTANCE : 0;
        double max = gate.adamantium() ? Math.max((double) mithrilRange * ADAMANTIUM_FACTOR, min) : mithrilRange;
        double distance = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min)), angle = random.nextDouble() * Math.PI * 2;
        return BlockPos.containing(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    static int range() {
        return MiteConfig.intOrDefault(MiteConfig.RUNEGATE_MITHRIL_RANGE);
    }

    /** MX-06 hook: the runegate at {@code portal}, or null where vanilla portal rules apply (not a runegate, or a ceiling). */
    public static @Nullable Gate at(ServerLevel level, BlockPos portal) {
        return level.dimensionType().hasCeiling() ? null : find(level, portal).orElse(null);
    }

    /** MX-06 hook: the transition through {@code gate}, null (no teleport) for non-players and players carrying anything. */
    public static @Nullable TeleportTransition teleport(ServerLevel level, Entity entity, Gate gate) {
        if (!(entity instanceof ServerPlayer player)) return null;
        if (!player.getInventory().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("hardscrabble.runegate.carrying"));
            return null;
        }
        // Like vanilla portal exits, never outside the world border (it caps the adamantium ring on small worlds).
        BlockPos xz = level.getWorldBorder().clampToBounds(destination(level.getSeed(), gate, range()));
        BlockPos target = landing(level, xz.getX(), xz.getZ());
        return new TeleportTransition(level, Vec3.atBottomCenterOf(target), Vec3.ZERO, player.getYRot(), player.getXRot(), Set.of(),
                TeleportTransition.PLAY_PORTAL_SOUND.then(e -> {
                    amnesia(player);
                    MiteTriggers.EVENT.get().trigger(player, "runegate");
                }));
    }

    /**
     * B1: a safe landing spot in column x/z: feet and head free of collision and fluid, standing on a sturdy block that is
     * not bedrock. The chunk is generated first (an unloaded chunk's heightmap reads minY). Under a ceiling, scans down from
     * below the logical height. Elsewhere it never goes below the surface (a cave is no landing): a surface that is not a safe
     * spot (low leaves, bush, fence, cactus) or a fluid (ocean, lava lake) gets a cobblestone platform on it, and a column
     * without ground (End void) one at sea level.
     */
    public static BlockPos landing(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos seaLevel = new BlockPos(x, Math.clamp(level.getSeaLevel(), level.getMinY() + 1, level.getMaxY() - 3), z);
        if (level.dimensionType().hasCeiling()) {
            for (BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getMinY() + level.getLogicalHeight() - 2, z); p.getY() > level.getMinY(); p.move(Direction.DOWN))
                if (safe(level, p)) return p.immutable();
            return platform(level, seaLevel);
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= level.getMinY()) return platform(level, seaLevel);
        BlockPos surface = new BlockPos(x, top, z);
        return level.getFluidState(surface.below()).isEmpty() && safe(level, surface) ? surface : platform(level, surface);
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return free(level, feet) && free(level, feet.above()) && !level.getBlockState(floor).is(Blocks.BEDROCK)
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    /**
     * A 5x5 floor under {@code feet}, three blocks of air above it (the End platform shape). M-4: cobblestone, not obsidian, so a
     * fixed destination is no farm of free obsidian (runestones and portals need it).
     */
    private static BlockPos platform(ServerLevel level, BlockPos feet) {
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2)))
            level.setBlock(p, p.getY() < feet.getY() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return feet;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** Amnesia: positive XP is lost, negative XP stays [INFÉRÉ: amount not specified]. */
    static void amnesia(ServerPlayer player) {
        ProgressionService.setXp(player, Math.min(0, ProgressionService.xp(player)));
    }
}
