package dev.mite.entity.mob;

import dev.mite.combat.CombatContent;
import dev.mite.core.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;

/** EF-MOB-05 Revenant: elite zombie. Spawns wearing a rusted chainmail helmet and chestplate
 *  (mobs never carry real iron, EF-MET-09) [HORS-SPEC: armour pieces unspecified]. */
public class Revenant extends MiteZombie {
    public Revenant(EntityType<? extends Revenant> type, Level level) {
        super(type, level, 3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(30, 7, 0.26);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(CombatContent.armorItem(Material.RUSTED_IRON, ArmorType.HELMET, true)));
        setItemSlot(EquipmentSlot.CHEST, new ItemStack(CombatContent.armorItem(Material.RUSTED_IRON, ArmorType.CHESTPLATE, true)));
    }
}
