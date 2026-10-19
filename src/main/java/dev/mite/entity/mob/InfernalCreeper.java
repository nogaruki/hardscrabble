package dev.mite.entity.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import dev.mite.core.Formulas;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/** EF-MOB-13 Infernal Creeper: creeper with twice the explosion radius (ET-J-05, AT on Creeper#explosionRadius), XP x3. */
public class InfernalCreeper extends Creeper {
    public static final int RADIUS_MULTIPLIER = 2;

    public InfernalCreeper(EntityType<? extends InfernalCreeper> type, Level level) {
        super(type, level);
        this.xpReward *= 3;
        this.explosionRadius *= RADIUS_MULTIPLIER;
    }

    /** EF-MOB-13: half of the creepers below a random depth {@code y < rand(40)} (R196, converted with Formulas.mapDepth) turn infernal. */
    public static boolean replacesCreeper(RandomSource random, double y) {
        return random.nextBoolean() && y < Formulas.mapDepth(random.nextInt(40));
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
