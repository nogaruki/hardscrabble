package dev.mite.entity.mob;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import dev.mite.equipment.Buckets;
import dev.mite.equipment.EquipmentContent.BucketSet;
import dev.mite.metabolism.MetabolismContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * EF-MOB-14 Fire Elemental: melee blaze kin born in deep lava. Hurt only by water (blaze water sensitivity, and bowls or
 * buckets of water or milk thrown on it, changelog l.546 R130), snowballs (3 damage, as a blaze) and enchanted weapons
 * (ET-J-03). Fewer spawns in shallow or flowing lava (l.504, R134) and never a crowd (l.547, R130). Health 20 [NON VÉRIFIÉ]
 * (Q-23); XP x3 of the base monster XP.
 */
public class FireElemental extends Blaze {
    /** "Deep" lava [INFÉRÉ]: below y 0, the converted R196 y 32 of the deep mobs. */
    public static final int MAX_SPAWN_Y = 0;
    /** l.504: 1 spawn in 4 kept in shallow (one block deep) or flowing lava [INFÉRÉ: "reduced"]. */
    public static final int SHALLOW_ODDS = 4;
    /** l.547: no spawn with this many fire elementals within {@link #CAP_RADIUS} blocks [INFÉRÉ: "large numbers prevented"]. */
    public static final int CAP = 3;
    public static final double CAP_RADIUS = 32;
    /** l.546: damage of a bucket and of a bowl of liquid [INFÉRÉ; a snowball does 3]. */
    public static final float BUCKET_DAMAGE = 6, BOWL_DAMAGE = 2;

    public FireElemental(EntityType<? extends FireElemental> type, Level level) {
        super(type, level);
        this.xpReward = 5 * 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static boolean hurtBy(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return source.is(DamageTypes.DROWN) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.getDirectEntity() instanceof Snowball
                || weapon != null && weapon.isEnchanted();
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return super.isInvulnerableTo(level, source) || !hurtBy(source);
    }

    /** Lava is its home: spawns and strolls there (as the strider). */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getFluidState(pos).is(FluidTags.LAVA) ? 10 : super.getWalkTargetValue(pos, level);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /**
     * EF-MOB-14: in lava below {@link #MAX_SPAWN_Y}; light is ignored (lava is bright). Shallow or flowing lava keeps 1 spawn in
     * {@link #SHALLOW_ODDS} (l.504); never more than {@link #CAP} nearby (l.547).
     */
    public static boolean checkSpawnRules(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL && pos.getY() < MAX_SPAWN_Y && lavaAllows(level, pos, random);
    }

    /** The lava, shallowness and crowd part of the spawn rules. */
    public static boolean lavaAllows(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (!level.getFluidState(pos).is(FluidTags.LAVA)) return false;
        boolean deepSource = level.getFluidState(pos).isSource() && level.getFluidState(pos.below()).is(FluidTags.LAVA);
        if (!deepSource && random.nextInt(SHALLOW_ODDS) != 0) return false;
        return level.getEntitiesOfClass(FireElemental.class, new AABB(pos).inflate(CAP_RADIUS)).size() < CAP;
    }

    /** l.546: the damage a liquid container does to a fire elemental, and the container left once poured; null if not a liquid. */
    public static @Nullable Pour pour(ItemStack stack) {
        if (stack.is(MetabolismContent.WATER_BOWL.get()) || stack.is(MetabolismContent.MILK_BOWL.get())) return new Pour(BOWL_DAMAGE, Items.BOWL);
        BucketSet set = Buckets.of(stack);
        if (set != null && (stack.is(set.water().get()) || stack.is(set.milk().get()))) return new Pour(BUCKET_DAMAGE, set.empty().get());
        return null;
    }

    public record Pour(float damage, Item empty) {}

    /** l.546: using a bowl or bucket of water or milk on a fire elemental pours it: drowning damage, the container empties. */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Pour pour = pour(event.getItemStack());
        if (pour == null || !(event.getTarget() instanceof FireElemental elemental)) return;
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel level) {
            elemental.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.DROWN), player), pour.damage());
            elemental.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 1, 1);
            player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(event.getItemStack(), player, new ItemStack(pour.empty())));
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
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
