package dev.mite.entity.mob;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.Tags;

/**
 * EF-MOB-18 silverfish variants: Copperspine (poison, near copper ore), Netherspawn (explodes on death, Nether) and Hoary
 * (paralysis and poison, Underworld). They never hide in stone (that would breed vanilla silverfish). Stats not extracted
 * (Q-23, ET-J-06): the MITE silverfish 8 health, 3 damage, XP 10 [NON VÉRIFIÉ].
 */
public class SilverfishVariant extends Silverfish {
    /** Poison on hit, Netherspawn blast power, copper search radius [INFÉRÉ: not given]. */
    public static final int POISON_TICKS = 100, COPPER_RADIUS = 4;
    public static final float EXPLOSION_POWER = 1.0F;

    public enum Kind { COPPERSPINE, NETHERSPAWN, HOARY }

    public final Kind kind;

    public SilverfishVariant(EntityType<? extends SilverfishVariant> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.xpReward = 10;
    }

    /** Silverfish 0.6 in 1.6.4 units is the vanilla 0.25. */
    public static AttributeSupplier.Builder createAttributes() {
        return Silverfish.createAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.ATTACK_DAMAGE, 3);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal); // the merge-with-stone goal
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && kind != Kind.NETHERSPAWN) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS), this);
            if (kind == Kind.HOARY) living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Ghoul.PARALYSIS_TICKS, Ghoul.PARALYSIS_AMPLIFIER), this);
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (kind == Kind.NETHERSPAWN && level() instanceof ServerLevel level)
            level.explode(this, getX(), getY(), getZ(), EXPLOSION_POWER, Level.ExplosionInteraction.MOB);
    }

    /** Copperspine: copper ore within {@link #COPPER_RADIUS} blocks. */
    public static boolean checkCopperspineSpawnRules(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && BlockPos.betweenClosedStream(pos.offset(-COPPER_RADIUS, -COPPER_RADIUS, -COPPER_RADIUS),
                pos.offset(COPPER_RADIUS, COPPER_RADIUS, COPPER_RADIUS)).anyMatch(p -> level.getBlockState(p).is(Tags.BlockItems.ORES_COPPER.block()));
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
