package dev.mite.entity.mob;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * EF-MOB-06 Dire Wolf: snowy taiga wolf that hunts players while wild. Packs come from the vanilla wolf goals (a hurt wolf
 * alerts its kin, spawns in groups). Tamable like a wolf; 16 HP wild, 24 tamed.
 */
public class DireWolf extends Wolf {
    public static final double WILD_HEALTH = 16, TAME_HEALTH = 24;

    public DireWolf(EntityType<? extends DireWolf> type, Level level) {
        super(type, level);
    }

    /** Speed "loup": the vanilla wolf 0.3. */
    public static AttributeSupplier.Builder createAttributes() {
        return Wolf.createAttributes().add(Attributes.MAX_HEALTH, WILD_HEALTH).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(4, new NonTameRandomTargetGoal<>(this, Player.class, false, null));
    }

    @Override
    protected void applyTamingSideEffects() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(isTame() ? TAME_HEALTH : WILD_HEALTH);
        if (isTame()) setHealth((float) TAME_HEALTH);
    }

    /** XP x2 of the wolf's. */
    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 2 * super.getBaseExperienceReward(level);
    }

    /** Breeding would give a vanilla wolf (Wolf#getBreedOffspring); dire wolves do not breed [INFÉRÉ]. */
    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    /** No offspring either from a spawn egg used on an adult (SpawnEggItem calls getBreedOffspring directly). */
    @Override
    public @Nullable Wolf getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /** Wolf armour lists only minecraft:wolf as allowed entity (Item.Properties#wolfArmor): a dire wolf wears what a wolf wears. */
    @Override
    public boolean isEquippableInSlot(ItemStack stack, EquipmentSlot slot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return super.isEquippableInSlot(stack, slot) || equippable != null && slot == EquipmentSlot.BODY && equippable.slot() == slot
                && canUseSlot(slot) && equippable.canBeEquippedBy(EntityTypes.WOLF.builtInRegistryHolder());
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !isTame();
    }

    /** A pet survives Peaceful: Mob#checkDespawn discards a type not allowed in Peaceful before it looks at taming. */
    @Override
    public void checkDespawn() {
        if (!isTame()) super.checkDespawn();
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
