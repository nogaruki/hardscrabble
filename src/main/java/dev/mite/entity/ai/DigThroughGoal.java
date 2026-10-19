package dev.mite.entity.ai;

import dev.mite.config.MiteConfig;
import dev.mite.harvest.HarvestRules;
import dev.mite.lunar.Lunar;
import dev.mite.metallurgy.Metallurgy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * EF-IA-01 / ET-K-01: a mob that cannot path to its target digs the block in front of its feet or head toward it, or the
 * block below when it holds an efficient tool, within 1.8 blocks. Only blocks of {@code hardscrabble:zombie_diggable}, then a
 * cooldown of {@code 300 x hardness} ticks. Respects {@code mobs.zombie_digging} (K-7, ENF-PERF-08), mobGriefing (EntityMobGriefingEvent) and LivingDestroyBlockEvent.
 * A tool in hand adds the blocks it can harvest (a copper pickaxe digs stone). Frenzied undead ({@code hardscrabble:frenzy_undead}
 * under a blood moon) also dig any solid block below harvest level 2 (EF-LUN-02).
 */
public final class DigThroughGoal extends Goal {
    static final double REACH_SQR = 1.8 * 1.8;
    static final int COOLDOWN_PER_HARDNESS = 300;

    private final Mob mob;
    private final Predicate<BlockState> diggable;
    private final double cooldownPerHardness;
    private final boolean zombie;
    private long nextDig;
    /** The block found by {@link #canUse}, dug by {@link #start} in the same tick. */
    private @Nullable BlockPos pending;

    /** The {@code hardscrabble:digger} goal, switched off by {@code mobs.zombie_digging} (Q-3, ENF-PERF-08). */
    public DigThroughGoal(Mob mob) {
        this(mob, state -> state.is(AiContent.ZOMBIE_DIGGABLE) || frenzied(mob) && frenzyDiggable(state) || toolDiggable(mob, state), COOLDOWN_PER_HARDNESS, true);
    }

    /** EF-IA-01: with an efficient tool in hand, the blocks that need a tool and that this tool can harvest; the clay ovens never. */
    static boolean toolDiggable(Mob mob, BlockState state) {
        HarvestRules.Hardness h = HarvestRules.hardness(state);
        return h.level() > 0 && h.hardness() >= 0 && HarvestRules.canHarvest(mob.getMainHandItem(), state) && !clayOven(state);
    }

    private static boolean clayOven(BlockState state) {
        return state.is(Metallurgy.CLAY_FURNACE.get()) || state.is(Metallurgy.LARGE_CLAY_FURNACE.get());
    }

    static boolean frenzied(Mob mob) {
        return mob.is(AiContent.FRENZY_UNDEAD) && Lunar.isBloodMoon(mob.level());
    }

    /** EF-IA-01: in frenzy, any breakable solid block of harvest level < 2; the clay ovens never. */
    @SuppressWarnings("deprecation") // isSolid has no 26.3 replacement; vanilla features still use it
    static boolean frenzyDiggable(BlockState state) {
        HarvestRules.Hardness h = HarvestRules.hardness(state);
        return h.level() < 2 && h.hardness() >= 0 && state.isSolid() && !clayOven(state);
    }

    boolean canDig(BlockState state) {
        return diggable.test(state);
    }

    /** A digger with its own block list and cooldown (EF-MOB-15 Earth Elemental digs stone fast). */
    public DigThroughGoal(Mob mob, Predicate<BlockState> diggable, double cooldownPerHardness) {
        this(mob, diggable, cooldownPerHardness, false);
    }

    private DigThroughGoal(Mob mob, Predicate<BlockState> diggable, double cooldownPerHardness, boolean zombie) {
        this.mob = mob;
        this.diggable = diggable;
        this.cooldownPerHardness = cooldownPerHardness;
        this.zombie = zombie;
    }

    boolean enabled() {
        return !zombie || !MiteConfig.SPEC.isLoaded() || MiteConfig.ZOMBIE_DIGGING.getAsBoolean();
    }

    /** True while the mob is between two digs (EF-IA-02: no despawn while digging). */
    public boolean isDigging() {
        return mob.level().getGameTime() < nextDig;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || isDigging() || !enabled() || !(mob.level() instanceof ServerLevel level)) return false;
        Path path = mob.getNavigation().getPath();
        if (path != null && path.canReach() && !path.isDone()) return false;
        return EventHooks.canEntityGrief(level, mob) && (pending = findBlock(target)) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        BlockPos pos = pending;
        pending = null;
        if (pos == null) return;
        BlockState state = mob.level().getBlockState(pos);
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        mob.swingForAttack(InteractionHand.MAIN_HAND);
        mob.level().destroyBlock(pos, true, mob);
        nextDig = mob.level().getGameTime() + (long) Math.ceil(cooldownPerHardness * HarvestRules.hardness(state).hardness());
    }

    /** The first diggable candidate toward the target: feet, head, then below (efficient tool only, target lower). */
    @Nullable BlockPos findBlock(LivingEntity target) {
        BlockPos feet = mob.blockPosition();
        Vec3 delta = target.position().subtract(mob.position());
        Direction dir = Direction.getApproximateNearest(delta.x, 0, delta.z);
        List<BlockPos> candidates = new ArrayList<>(3);
        candidates.add(feet.relative(dir));
        candidates.add(feet.relative(dir).above());
        if (delta.y < -1) candidates.add(feet.below());
        Vec3 center = mob.getBoundingBox().getCenter();
        for (BlockPos pos : candidates) {
            BlockState state = mob.level().getBlockState(pos);
            if (!diggable.test(state) || Vec3.atCenterOf(pos).distanceToSqr(center) > REACH_SQR) continue;
            if (pos.getY() < feet.getY() && mob.getMainHandItem().getDestroySpeed(state) <= 1) continue;
            if (mob.level() instanceof ServerLevel level && CommonHooks.canEntityDestroy(level, pos, mob)) return pos;
        }
        return null;
    }
}
