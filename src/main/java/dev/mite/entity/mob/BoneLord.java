package dev.mite.entity.mob;

import dev.mite.entity.ai.SummonGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * EF-MOB-20 Bone Lord: skeleton mini-boss, 20 health, 5 damage, speed 0.26, XP x3. Summons skeletons and keeps an aura
 * that heals nearby skeletons (1 health per second) and makes them frenzied (Speed I = the +20 % frenzy speed of EF-LUN-02). Minion count, cooldown,
 * aura radius and effect strength are [INFÉRÉ]. Changelog: it never despawns once it has targeted a player, recalls its troops one by one
 * while it has no target (every {@link #RECALL_PERIOD} ticks [INFÉRÉ]) and summons in shallow water (no placement check).
 */
public class BoneLord extends AbstractSkeleton {
    public static final double AURA_RADIUS = 8;
    public static final int AURA_PERIOD = 20, AURA_TICKS = 60, MINIONS = 3, SUMMON_COOLDOWN = 400, RECALL_PERIOD = 200;

    public BoneLord(EntityType<? extends BoneLord> type, Level level) {
        this(type, level, 3);
    }

    protected BoneLord(EntityType<? extends BoneLord> type, Level level, int xpMultiplier) {
        super(type, level);
        this.xpReward *= xpMultiplier;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Longdead.attributes(20, 5, 0.26);
    }

    /** The skeletons it summons. */
    protected EntityType<? extends Mob> minion() {
        return EntityTypes.SKELETON;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new SummonGoal(this, this::minion, MINIONS, SUMMON_COOLDOWN));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && tickCount % AURA_PERIOD == 0) aura();
        if (!level().isClientSide() && getTarget() == null && tickCount % RECALL_PERIOD == 0) recall();
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target instanceof Player) setPersistenceRequired();
    }

    /** Removes one of its idle summoned troops nearby. */
    public void recall() {
        level().getEntities(minion(), getBoundingBox().inflate(SummonGoal.RANGE), m -> SummonGoal.summoned(m) && m.getTarget() == null)
                .stream().findFirst().ifPresent(Mob::discard);
    }

    /**
     * Heals every skeleton within {@link #AURA_RADIUS} (itself included) by 1 and gives Speed I: skeletons only, changelog l.150
     * (R176), which overrides the "undead" of EF-MOB-20. Undead ignore Regeneration, hence the direct heal.
     */
    void aura() {
        for (AbstractSkeleton undead : level().getEntitiesOfClass(AbstractSkeleton.class, getBoundingBox().inflate(AURA_RADIUS))) {
            undead.heal(1);
            undead.addEffect(new MobEffectInstance(MobEffects.SPEED, AURA_TICKS), this);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.get(getType(), MobSounds.Kind.AMBIENT, SoundEvents.SKELETON_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.get(getType(), MobSounds.Kind.HURT, SoundEvents.SKELETON_HURT);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.get(getType(), MobSounds.Kind.DEATH, SoundEvents.SKELETON_DEATH);
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }
}
