package dev.mite.entity.ai;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Summons minions that attack the summoner's target, while it sees a player target: witch wolves (EF-IA-08), Bone Lord
 * skeletons (EF-MOB-20). Tops the minions within {@link #RANGE} blocks back up to {@code count}, then waits {@code cooldown}
 * ticks. Counts and cooldowns are [INFÉRÉ]: the spec gives no figure. Minions carry the {@link #SUMMONED} entity tag: witch
 * wolves cannot be tamed and despawn like monsters (AiContent), Bone Lords recall theirs.
 */
public final class SummonGoal extends Goal {
    public static final double RANGE = 16;
    /** Saved entity tag (Entity#addTag) of every summoned minion. */
    public static final String SUMMONED = "hardscrabble.summoned";

    private final Mob mob;
    private final Supplier<? extends EntityType<? extends Mob>> type;
    private final int count, cooldown;
    private long next;

    public SummonGoal(Mob mob, Supplier<? extends EntityType<? extends Mob>> type, int count, int cooldown) {
        this.mob = mob;
        this.type = type;
        this.count = count;
        this.cooldown = cooldown;
    }

    public static boolean summoned(Entity entity) {
        return entity.entityTags().contains(SUMMONED);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target instanceof Player && target.isAlive() && mob.level().getGameTime() >= next && mob.getSensing().hasLineOfSight(target)
                && nearby() < count;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    /** Summoned minions only: tamed wolves or wild skeletons around do not count. */
    private int nearby() {
        return mob.level().getEntities(type.get(), mob.getBoundingBox().inflate(RANGE), e -> e.isAlive() && summoned(e)).size();
    }

    @Override
    public void start() {
        next = mob.level().getGameTime() + cooldown;
        if (!(mob.level() instanceof ServerLevel level)) return;
        for (int i = nearby(); i < count; i++) {
            Mob minion = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (minion == null) return;
            minion.snapTo(mob.getX() + mob.getRandom().nextInt(5) - 2, mob.getY(), mob.getZ() + mob.getRandom().nextInt(5) - 2, mob.getYRot(), 0);
            EventHooks.finalizeMobSpawn(minion, level, level.getCurrentDifficultyAt(minion.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            minion.setTarget(mob.getTarget());
            minion.addTag(SUMMONED);
            level.addFreshEntityWithPassengers(minion);
        }
    }
}
