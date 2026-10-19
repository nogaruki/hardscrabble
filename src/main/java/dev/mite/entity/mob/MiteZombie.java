package dev.mite.entity.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Zombie-derived MITE mob (EF-MOB-01/02/05): no baby variant, no drowned conversion, no reinforcements, XP = multiplier x zombie XP.
 * No leader bonus (spec stats only); inherited vanilla gear is rusted by {@code MobGear} (EF-MET-09).
 */
public abstract class MiteZombie extends Zombie {
    protected MiteZombie(EntityType<? extends Zombie> type, Level level, int xpMultiplier) {
        super(type, level);
        this.xpReward *= xpMultiplier;
    }

    /** Zombie attributes with the spec health, damage and speed; follow range 40 for every new mob (§6.10). */
    static AttributeSupplier.Builder attributes(double health, double damage, double speed) {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void randomizeReinforcementsChance() {
        getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0);
    }

    /** No vanilla leader bonus (max health x2 to x5), reinforcement chance or follow range bonus: spec stats only (EF-MOB-01..05). */
    @Override
    protected void handleAttributes(float difficultyModifier, EntitySpawnReason spawnReason) {}

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        return super.finalizeSpawn(level, difficulty, reason, data == null ? new ZombieGroupData(false, false) : data);
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
