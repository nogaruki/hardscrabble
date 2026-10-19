package dev.mite.entity.mob;

import dev.mite.entity.ai.AiContent;
import dev.mite.entity.ai.DigThroughGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;

/**
 * EF-MOB-15 Earth Elemental: stone golem, 30 health, 12 damage, speed 0.2, follow range 20, XP x3. Hurt only by a tool
 * efficient against its material (stone: a pickaxe) or by an iron golem's blow (changelog l.281, R157); knockback resistant,
 * digs stone fast and smashes wooden doors quickly (l.540, R130); never calls reinforcements. Lightning turns it into its
 * heated counterpart (l.282, R157). {@link ClayGolem} is the clay variant.
 */
public class EarthElemental extends MiteZombie {
    /** Ticks per hardness point between two digs, against 300 for zombies (stone 1.5 -> 30 ticks) [INFÉRÉ: "vite"]. */
    public static final double DIG_COOLDOWN_PER_HARDNESS = 20;
    /** Ticks to smash a wooden door, against 240 for a zombie [INFÉRÉ: "quickly"]. */
    public static final int DOOR_BREAK_TICKS = 60;
    /** Seconds a heated elemental sets its victim on fire [INFÉRÉ]. */
    public static final float HEATED_IGNITE_SECONDS = 4;
    private static final EntityDataAccessor<Boolean> DATA_HEATED = SynchedEntityData.defineId(EarthElemental.class, EntityDataSerializers.BOOLEAN);

    public EarthElemental(EntityType<? extends EarthElemental> type, Level level) {
        super(type, level, 3);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return attributes(30, 12, 0.2).add(Attributes.FOLLOW_RANGE, 20).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        goalSelector.addGoal(1, new DigThroughGoal(this, EarthElemental::canDig, DIG_COOLDOWN_PER_HARDNESS));
        goalSelector.addGoal(1, new SmashDoorGoal(this));
        getNavigation().setCanOpenDoors(true);
    }

    /** Always a door smasher (its own fast goal): the vanilla slow door goal of the zombie is never added. */
    @Override
    public boolean canBreakDoors() {
        return true;
    }

    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {}

    /** Zombie-diggable blocks plus natural stone and cobblestone. */
    public static boolean canDig(BlockState state) {
        return state.is(AiContent.ZOMBIE_DIGGABLE) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.BlockItems.COBBLESTONES.block());
    }

    /** The block its body is made of: only a tool efficient against it hurts. */
    protected BlockState material() {
        return Blocks.STONE.defaultBlockState();
    }

    public static boolean hurtBy(DamageSource source, BlockState material) {
        ItemStack weapon = source.getWeaponItem();
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.getEntity() instanceof IronGolem
                || weapon != null && weapon.getDestroySpeed(material) > 1;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(level, source) || !hurtBy(source, material());
    }

    // ---- l.282: heated counterpart ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_HEATED, false);
    }

    public boolean isHeated() {
        return entityData.get(DATA_HEATED);
    }

    public void setHeated(boolean heated) {
        entityData.set(DATA_HEATED, heated);
    }

    /** l.282: lightning heats it instead of hurting it. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
        setHeated(true);
        clearFire();
    }

    /** [INFÉRÉ] The heated body ignores fire and lava, glows with flames, and water cools it back. */
    @Override
    public boolean fireImmune() {
        return isHeated() || super.fireImmune();
    }

    @Override
    public boolean displayFireAnimation() {
        return isHeated() || super.displayFireAnimation();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && isHeated() && isInWater()) {
            setHeated(false);
            playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 1, 1);
        }
    }

    /** [INFÉRÉ] A heated elemental sets its victim on fire. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && isHeated() && target instanceof LivingEntity) target.igniteForSeconds(HEATED_IGNITE_SECONDS);
        return hit;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Heated", isHeated());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setHeated(input.getBooleanOr("Heated", false));
    }

    /** l.540: smashes a wooden door in {@link #DOOR_BREAK_TICKS} on any difficulty but Peaceful. */
    static final class SmashDoorGoal extends BreakDoorGoal {
        SmashDoorGoal(EarthElemental mob) {
            super(mob, d -> d != Difficulty.PEACEFUL);
        }

        @Override
        protected int getDoorBreakTime() {
            return DOOR_BREAK_TICKS;
        }
    }
}
