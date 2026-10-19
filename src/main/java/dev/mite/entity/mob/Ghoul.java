package dev.mite.entity.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** EF-MOB-01 Ghoul: a hit paralyses (Slowness VI for 50 ticks). Villager targeting is inherited from the zombie. */
public class Ghoul extends MiteZombie {
    public static final int PARALYSIS_TICKS = 50, PARALYSIS_AMPLIFIER = 5;

    public Ghoul(EntityType<? extends Ghoul> type, Level level) {
        super(type, level, 2);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(20, 5, 0.28); // health 20 [NON VÉRIFIÉ] (Q-23)
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living)
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PARALYSIS_TICKS, PARALYSIS_AMPLIFIER), this);
        return hit;
    }
}
