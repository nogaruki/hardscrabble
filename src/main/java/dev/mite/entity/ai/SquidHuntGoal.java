package dev.mite.entity.ai;

import dev.mite.entity.mob.Ghoul;
import dev.mite.lunar.Lunar;
import dev.mite.mixin.SquidAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * MITE squid: they hunt swimmers and boaters, more eagerly at night, but never into shallow water (changelog l.112), smash
 * the boat of the player they target (R151) and paralyse whoever bumps into them, as the Ghoul. During the night of a blue
 * moon they are peaceful, the bump paralysis excepted (R151). Ticks after the vanilla wander goal, so it steers the swim.
 */
public final class SquidHuntGoal extends Goal {
    /** Hunt range by day and by night [INFÉRÉ: "particularly aggressive at night"]. */
    static final double DAY_RANGE = 8, NIGHT_RANGE = 16;
    /** Movement vector length while hunting (vanilla wander 0.2, flee up to 0.15) [INFÉRÉ]. */
    static final double HUNT_SPEED = 0.25;
    /** Damage that breaks a boat at once (vehicles break above 40, damage counted x10). */
    static final float BOAT_SMASH = 5;

    private final Squid squid;

    public SquidHuntGoal(Squid squid) {
        this.squid = squid;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!(squid.level() instanceof ServerLevel level)) return;
        for (Player bumped : level.getEntitiesOfClass(Player.class, squid.getBoundingBox())) paralyse(bumped);
        Player target = target(squid);
        if (target == null || !squid.isInWater()) return;
        if (target.getVehicle() instanceof AbstractBoat boat && boat.getBoundingBox().intersects(squid.getBoundingBox().inflate(0.2))) {
            boat.hurtServer(level, squid.damageSources().mobAttack(squid), BOAT_SMASH);
        }
        // A hurt squid flees (vanilla SquidFleeGoal) instead of steering back at its attacker.
        if (squid.getLastHurtByMob() != null && squid.distanceToSqr(squid.getLastHurtByMob()) < 100) return;
        ((SquidAccessor) squid).mite$setMovementVector(target.position().subtract(squid.position()).normalize().scale(HUNT_SPEED));
    }

    static void paralyse(Player player) {
        if (!player.isCreative() && !player.isSpectator())
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Ghoul.PARALYSIS_TICKS, Ghoul.PARALYSIS_AMPLIFIER));
    }

    static @Nullable Player target(Squid squid) {
        return squid.level().getNearestPlayer(squid.getX(), squid.getY(), squid.getZ(), NIGHT_RANGE, e -> e instanceof Player p && hunts(squid, p));
    }

    /** A survival player in deep water or in a boat on it, within the day or night range; nobody on a blue moon night. */
    static boolean hunts(Squid squid, Player player) {
        Level level = squid.level();
        if (player.isCreative() || player.isSpectator() || Lunar.isBlueMoonNight(level) || !deep(level, player.blockPosition())) return false;
        double range = Lunar.isNight(level) ? NIGHT_RANGE : DAY_RANGE;
        return squid.distanceToSqr(player) <= range * range;
    }

    /** At least two blocks of water at or just under {@code pos}: a swimmer's feet or a boat's hull (changelog l.112). */
    static boolean deep(Level level, BlockPos pos) {
        return water(level, pos.below()) && (water(level, pos) || water(level, pos.below(2)));
    }

    private static boolean water(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }
}
