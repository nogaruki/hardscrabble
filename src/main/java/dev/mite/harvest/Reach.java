package dev.mite.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Block reach by held item and target block (EF-OUT-10). The {@code block_interaction_range} attribute (4.5) is the reach of a tool
 * on a block it is efficient against (+0.75); every other case is shorter: stick or bone +0.5, any other item +0, an empty hand
 * 1.5 blocks (analysis/02 §2.1). Per block, hence mixins (MX-10 server, MX-11 client) rather than an attribute modifier.
 * ponytail: the MITE base reach (player_block_reach, Q04) is not extracted yet; the base stays vanilla [À CONFIRMER Q04].
 */
public final class Reach {
    private Reach() {}

    public static final double TOOL_BONUS = 0.75, STICK_BONUS = 0.5;
    /** Bare hand: 1.5 blocks with the vanilla attribute (4.5 - 0.75 + this). */
    public static final double BARE_HAND_BONUS = 1.5 - (4.5 - TOOL_BONUS);

    public static double bonus(ItemStack held, BlockState state) {
        if (held.isEmpty()) return BARE_HAND_BONUS;
        if (held.is(Items.STICK) || held.is(Items.BONE)) return STICK_BONUS;
        Tool tool = held.get(DataComponents.TOOL);
        return tool != null && tool.getMiningSpeed(state) > tool.defaultMiningSpeed() ? TOOL_BONUS : 0;
    }

    /** Reach on {@code pos} relative to the attribute: 0 with an efficient tool, negative otherwise. */
    public static double offset(Player player, BlockPos pos) {
        return bonus(player.getMainHandItem(), player.level().getBlockState(pos)) - TOOL_BONUS;
    }

    /** MX-10: {@link Player#isWithinBlockInteractionRange} with the per-block offset. */
    public static boolean withinRange(Player player, BlockPos pos, double buffer) {
        double max = player.blockInteractionRange() + buffer + offset(player, pos);
        return new AABB(pos).distanceToSqr(player.getEyePosition()) < Mth.square(max);
    }

    /** MX-11: a picked block beyond its per-block reach becomes a miss, so the outline matches the server rule. */
    public static HitResult filterPick(Player player, HitResult hit, Vec3 eye) {
        if (!(hit instanceof BlockHitResult block) || hit.getType() == HitResult.Type.MISS) return hit;
        if (hit.getLocation().closerThan(eye, player.blockInteractionRange() + offset(player, block.getBlockPos()))) return hit;
        return BlockHitResult.miss(hit.getLocation(), block.getDirection(), block.getBlockPos());
    }
}
