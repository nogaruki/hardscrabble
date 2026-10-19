package dev.mite.entity.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * EF-MOB-17 Vampire Bat and Giant Vampire Bat: hostile bats (3 x scale health, 1 x scale damage, scale as the SCALE attribute)
 * that hunt players, or livestock when no player is near, and heal by the health they drain. XP 5, 10 for the bigger ones.
 * The normal sized Vampire Bat feeds on livestock periodically instead of biting it to death (changelog l.414, R142): one
 * bite that never takes the animal under 1 health, then it is sated for {@link #FEED_COOLDOWN} ticks.
 */
public class VampireBat extends Bat implements Enemy {
    /** Hunting range and ticks between two bites [INFÉRÉ: not given]. */
    public static final double HUNT_RANGE = 16;
    public static final int BITE_COOLDOWN = 20;
    /** l.414: ticks between two feeds on livestock [INFÉRÉ: "periodically", one Minecraft minute]. */
    public static final int FEED_COOLDOWN = 1200;
    private static final TargetingConditions HUNT = TargetingConditions.forCombat().range(HUNT_RANGE);
    private int biteCooldown;
    private int satedTicks;

    public VampireBat(EntityType<? extends VampireBat> type, Level level, int xp) {
        super(type, level);
        this.xpReward = xp;
    }

    public static AttributeSupplier.Builder createAttributes(double scale) {
        return Bat.createAttributes().add(Attributes.MAX_HEALTH, 3 * scale).add(Attributes.ATTACK_DAMAGE, scale).add(Attributes.SCALE, scale)
                .add(Attributes.FOLLOW_RANGE, HUNT_RANGE);
    }

    /** l.414: only the normal sized Vampire Bat spares livestock. */
    public boolean feedsOnLivestock() {
        return getType() == MobContent.VAMPIRE_BAT.get();
    }

    public boolean isSated() {
        return satedTicks > 0;
    }

    private @Nullable LivingEntity findPrey(ServerLevel level) {
        LivingEntity player = level.getNearestPlayer(HUNT, this);
        if (player != null || feedsOnLivestock() && isSated()) return player;
        return level.getNearestEntity(Animal.class, HUNT, this, getX(), getY(), getZ(), getBoundingBox().inflate(HUNT_RANGE));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (biteCooldown > 0) biteCooldown--;
        if (satedTicks > 0) satedTicks--;
        LivingEntity prey = getTarget();
        if (prey instanceof Animal && feedsOnLivestock() && isSated()) setTarget(prey = null);
        if (tickCount % 20 == 0 && (prey == null || !HUNT.test(level, this, prey))) setTarget(prey = findPrey(level)); // alive, in range, seen, not creative
        if (prey == null || !prey.isAlive()) {
            super.customServerAiStep(level);
            return;
        }
        setResting(false);
        Vec3 d = prey.getEyePosition().subtract(position());
        Vec3 m = getDeltaMovement();
        Vec3 move = m.add((Math.signum(d.x) * 0.5 - m.x) * 0.1, (Math.signum(d.y) * 0.7 - m.y) * 0.1, (Math.signum(d.z) * 0.5 - m.z) * 0.1);
        setDeltaMovement(move);
        setYRot(getYRot() + Mth.wrapDegrees((float) (Mth.atan2(move.z, move.x) * Mth.RAD_TO_DEG) - 90 - getYRot()));
        zza = 0.5F;
        if (biteCooldown == 0 && isWithinMeleeAttackRange(prey)) {
            biteCooldown = BITE_COOLDOWN;
            if (prey instanceof Animal animal && feedsOnLivestock()) feed(level, animal);
            else doHurtTarget(level, prey);
        }
    }

    /** l.414: one bite on an animal, never under 1 health, healing what it drained; then sated for {@link #FEED_COOLDOWN} ticks. */
    public void feed(ServerLevel level, Animal animal) {
        float before = animal.getHealth();
        float bite = Math.min((float) getAttributeValue(Attributes.ATTACK_DAMAGE), before - 1);
        if (bite > 0 && animal.hurtServer(level, damageSources().mobAttack(this), bite)) heal(Math.max(0, before - animal.getHealth()));
        satedTicks = FEED_COOLDOWN;
        setTarget(null);
    }

    /** EF-MOB-17: heals by the health the bite took. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) heal(Math.max(0, before - living.getHealth()));
        return hit;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Sated", satedTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        satedTicks = input.getIntOr("Sated", 0);
    }

    @Override
    public @org.jspecify.annotations.Nullable SoundEvent getAmbientSound() {
        return MobSounds.get(getType(), MobSounds.Kind.AMBIENT, super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.get(getType(), MobSounds.Kind.HURT, super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.get(getType(), MobSounds.Kind.DEATH, super.getDeathSound());
    }
}
