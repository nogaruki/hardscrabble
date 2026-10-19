package dev.mite.dimension;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Bedrock-linked portals (EF-DIM-02, ET-M-02), called by NetherPortalBlockMixin (MX-06): the block under the portal frame
 * picks the destination. Overworld bedrock leads to the Underworld, Underworld bottom bedrock or Mantle to the Nether,
 * anything else to the Overworld (from the Overworld: its spawn). Underworld and Nether arrivals use the vanilla exit portal
 * search and creation.
 */
public final class PortalRouting {
    private PortalRouting() {}

    /** Rows above the Underworld bottom counted as "bottom bedrock": the bedrock plates fill the bottom half (changelog l.177). */
    static final int UNDERWORLD_BOTTOM_ROWS = UnderworldContent.HEIGHT / 2;

    /** Destination of a portal whose frame stands on {@code under}, found at height {@code y} in a level starting at {@code minY}. */
    public static ResourceKey<Level> destination(ResourceKey<Level> from, BlockState under, int y, int minY) {
        boolean bedrock = under.is(Blocks.BEDROCK) || under.is(UnderworldContent.MANTLE.get());
        if (bedrock && from == Level.OVERWORLD) return UnderworldContent.LEVEL;
        if (bedrock && from == UnderworldContent.LEVEL && y < minY + UNDERWORLD_BOTTOM_ROWS) return Level.NETHER;
        return Level.OVERWORLD;
    }

    /**
     * Destination of the portal containing {@code portal}: walks down to the bottom row of portal blocks, then reads the blocks
     * under the frame along that row. One column resting on bedrock is enough: bedrock is uneven (Y -64 to -60), so a frame
     * often touches it with only part of its bottom row.
     */
    public static ResourceKey<Level> destination(ServerLevel level, BlockPos portal) {
        BlockPos pos = portal;
        while (level.getBlockState(pos.below()).is(Blocks.NETHER_PORTAL) && pos.getY() > level.getMinY()) pos = pos.below();
        Direction.Axis axis = level.getBlockState(pos).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        BlockPos under = pos.below(2);
        for (int side : new int[] {1, -1}) {
            for (BlockPos p = pos; level.getBlockState(p).is(Blocks.NETHER_PORTAL); p = p.relative(axis, side)) {
                BlockState state = level.getBlockState(p.below(2));
                if (state.is(Blocks.BEDROCK) || state.is(UnderworldContent.MANTLE.get())) under = p.below(2);
            }
        }
        return destination(level.dimension(), level.getBlockState(under), under.getY(), level.getMinY());
    }

    /**
     * Changelog l.424: an Overworld portal off bedrock leads to the world spawn. Underworld and Nether portals bound for the
     * Overworld keep the vanilla linked exit, so the way back comes out near the portal the player came in by.
     */
    public static boolean toSpawn(ServerLevel level, BlockPos portal) {
        return level.dimension() == Level.OVERWORLD && destination(level, portal) == Level.OVERWORLD;
    }

    /** Plain teleport to the Overworld spawn, without exit portal (R141 "téléporteur"). */
    public static TeleportTransition toOverworldSpawn(ServerLevel from, Entity entity) {
        ServerLevel overworld = from.getServer().overworld();
        var spawn = overworld.getRespawnData();
        return new TeleportTransition(overworld, Vec3.atBottomCenterOf(entity.adjustSpawnLocation(overworld, spawn.pos())), Vec3.ZERO,
                spawn.yaw(), spawn.pitch(), Set.of(), TeleportTransition.PLAY_PORTAL_SOUND);
    }
}
