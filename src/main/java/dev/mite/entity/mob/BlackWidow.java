package dev.mite.entity.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * EF-MOB-11 Black Widow: XP x8/5; one natural spawn in two is refused. Stats not extracted (Q-23, ET-J-06): provisional
 * MITE spider values (12 health, 4 damage) and a venomous bite [NON VÉRIFIÉ].
 */
public class BlackWidow extends Spider {
    /** Poison on hit [INFÉRÉ: venom, duration of the vanilla cave spider on Normal]. */
    public static final int POISON_TICKS = 140;

    public BlackWidow(EntityType<? extends BlackWidow> type, Level level) {
        super(type, level);
        this.xpReward = xpReward * 8 / 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS), this);
        return hit;
    }

    /** EF-MOB-11: refused one time in two, then the monster rules. */
    public static boolean checkSpawnRules(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return random.nextBoolean() && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
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
