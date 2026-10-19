package dev.mite.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * EF-MOB-03 Shadow: immaterial (tag {@code hardscrabble:immaterial}) and fire immune (entity type), so only silver, enchanted weapons,
 * magic and the sun hurt it. Killed outright by sunlight, heals in the dark, snuffs nearby torches and candles. A hit gives
 * Weakness 600 ticks and Darkness, unless the target wears silver armor.
 */
public class Shadow extends MiteZombie {
    public static final int WEAKNESS_TICKS = 600;
    /** "Assombrit la vision": Darkness for 5 s [INFÉRÉ, duration not given]. */
    public static final int DARKNESS_TICKS = 100;
    /** Heals 1 HP per second at raw light level <= 4, snuffs light sources within 3 blocks [INFÉRÉ, rates not given]. */
    public static final int DARK_LIGHT = 4, SNUFF_RADIUS = 3;

    public Shadow(EntityType<? extends Shadow> type, Level level) {
        super(type, level, 2);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(20, 5, 0.23); // health 20 [NON VÉRIFIÉ] (Q-23)
    }

    public boolean inSunlight() {
        return inSunlight(this);
    }

    /** The vanilla undead sunburn test, without its random factor (also the Nightwing's, EF-MOB-17). */
    @SuppressWarnings("deprecation") // no replacement for the light value in 26.3; vanilla Mob uses it too
    public static boolean inSunlight(Entity e) {
        return e.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, e.position()) && e.getLightLevelDependentMagicValue() > 0.5F
                && !e.isInWaterOrRain() && e.level().canSeeSky(BlockPos.containing(e.getX(), e.getEyeY(), e.getZ()));
    }

    /** Lit torches (also soul, copper and redstone torches, wall or not) and lit candles; nothing else that glows. */
    static boolean snuffable(BlockState state, Level level, BlockPos pos) {
        return AbstractCandleBlock.isLit(state) || state.getBlock() instanceof BaseTorchBlock && state.getLightEmission(level, pos) > 0;
    }

    /** A candle is blown out, a torch broken (dropping itself). */
    private void snuff(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (AbstractCandleBlock.isLit(state)) AbstractCandleBlock.extinguish(null, state, level, pos);
        else level.destroyBlock(pos, true, this);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 20 == 0) everySecond(level);
    }

    /** Sun death, healing in the dark, snuffing one light source. */
    void everySecond(ServerLevel level) {
        if (inSunlight()) {
            kill(level);
            return;
        }
        if (level.getMaxLocalRawBrightness(blockPosition()) <= DARK_LIGHT) heal(1);
        if (!EventHooks.canEntityGrief(level, this)) return;
        BlockPos.betweenClosedStream(blockPosition().offset(-SNUFF_RADIUS, -1, -SNUFF_RADIUS), blockPosition().offset(SNUFF_RADIUS, 2, SNUFF_RADIUS))
                .filter(p -> snuffable(level.getBlockState(p), level, p)).findFirst().ifPresent(p -> snuff(level, p.immutable()));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living && !Wight.wearsSilver(living)) {
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS), this);
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS), this);
        }
        return hit;
    }
}
