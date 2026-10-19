package dev.mite.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** EF-MOB-20 Ancient Bone Lord: Underworld Bone Lord, 24 health, 8 damage, speed 0.27, XP x2; summons Longdeads [INFÉRÉ]. */
public class AncientBoneLord extends BoneLord {
    public AncientBoneLord(EntityType<? extends AncientBoneLord> type, Level level) {
        super(type, level, 2);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Longdead.attributes(24, 8, 0.27);
    }

    @Override
    protected EntityType<? extends Mob> minion() {
        return MobContent.LONGDEAD.get();
    }
}
