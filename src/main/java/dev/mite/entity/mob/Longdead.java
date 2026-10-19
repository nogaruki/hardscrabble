package dev.mite.entity.mob;

import dev.mite.MiteMod;
import dev.mite.underworld.UnderworldContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * EF-MOB-19 Longdead: Underworld skeleton, 12 health, 6 damage, speed 0.29, XP x3. Bow shooting ancient metal arrows and an
 * ancient metal helmet [INFÉRÉ: gear not detailed by the spec].
 */
public class Longdead extends AbstractSkeleton {
    public Longdead(EntityType<? extends Longdead> type, Level level) {
        this(type, level, 3);
    }

    protected Longdead(EntityType<? extends Longdead> type, Level level, int xpMultiplier) {
        super(type, level);
        this.xpReward *= xpMultiplier;
    }

    /** Skeleton attributes with the spec health, damage and speed (0.29 for both Longdeads); follow range 40 for every new mob (§6.10). */
    static AttributeSupplier.Builder attributes(double health, double damage, double speed) {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.FOLLOW_RANGE, 40);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(12, 6, 0.29);
    }

    static ItemStack ancientMetal(String item) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(MiteMod.id("ancient_metal_" + item)));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        setItemSlot(EquipmentSlot.HEAD, ancientMetal("helmet"));
    }

    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        ItemStack projectile = super.getProjectile(weapon);
        return projectile.is(Items.ARROW) ? new ItemStack(UnderworldContent.ANCIENT_METAL_ARROW.get()) : projectile;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.get(getType(), MobSounds.Kind.AMBIENT, SoundEvents.SKELETON_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.get(getType(), MobSounds.Kind.HURT, SoundEvents.SKELETON_HURT);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.get(getType(), MobSounds.Kind.DEATH, SoundEvents.SKELETON_DEATH);
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }
}
