package dev.mite.entity.mob;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** EF-MOB-19 Longdead Guardian: 24 health, 8 damage, XP x5. Melee with an ancient metal sword, helmet and chestplate [INFÉRÉ]. */
public class LongdeadGuardian extends Longdead {
    public LongdeadGuardian(EntityType<? extends LongdeadGuardian> type, Level level) {
        super(type, level, 5);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(24, 8, 0.29);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        setItemSlot(EquipmentSlot.MAINHAND, ancientMetal("sword"));
        setItemSlot(EquipmentSlot.CHEST, ancientMetal("chestplate"));
    }
}
