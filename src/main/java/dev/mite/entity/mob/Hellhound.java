package dev.mite.entity.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * EF-MOB-07 Hellhound: fire immune hostile wolf of the depths that breathes fire on a close target. Never tamed or bred;
 * no leather (empty loot table).
 */
public class Hellhound extends Wolf {
    /** Breath reach, cooldown and burn time [INFÉRÉ, the spec only says "souffle de feu"]. */
    public static final double BREATH_RANGE = 4;
    public static final int BREATH_COOLDOWN = 60, BREATH_FIRE_SECONDS = 4;

    private int breathCooldown;

    public Hellhound(EntityType<? extends Hellhound> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(4, new NonTameRandomTargetGoal<>(this, Player.class, false, null));
    }

    /** Sets the target on fire with a jet of flames. */
    public void breathe(ServerLevel level, LivingEntity target) {
        target.igniteForSeconds(BREATH_FIRE_SECONDS);
        Vec3 from = getEyePosition(), step = target.getEyePosition().subtract(from).scale(0.1);
        for (int i = 1; i <= 10; i++) level.sendParticles(ParticleTypes.FLAME, from.x + step.x * i, from.y + step.y * i, from.z + step.z * i, 2, 0.1, 0.1, 0.1, 0.01);
        playSound(SoundEvents.BLAZE_SHOOT);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (--breathCooldown <= 0 && target != null && target.isAlive() && distanceToSqr(target) < BREATH_RANGE * BREATH_RANGE && hasLineOfSight(target)) {
            breathe(level, target);
            breathCooldown = BREATH_COOLDOWN;
        }
    }

    @Override
    protected void applyTamingSideEffects() {}

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    /** No wolf pup from a spawn egg used on a Hellhound either (SpawnEggItem calls getBreedOffspring directly). */
    @Override
    public @Nullable Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /** XP x3 of the wolf's. */
    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 3 * super.getBaseExperienceReward(level);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
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
