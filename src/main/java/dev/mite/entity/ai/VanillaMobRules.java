package dev.mite.entity.ai;

import dev.mite.config.MiteConfig;
import dev.mite.entity.mob.InfernalCreeper;
import dev.mite.mixin.ProjectileAccessor;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Vanilla ranged mobs and creepers: skeletons, blazes and ghasts lead their shots (K03), stronger witch (K08), creeper explosion radius (K15), hunting squid (R151). */
public final class VanillaMobRules {
    private VanillaMobRules() {}

    /** EF-IA-08: witch potion reach, up from the vanilla 10 [INFÉRÉ: "plus loin", no figure]. */
    public static final float WITCH_ATTACK_RADIUS = 16;
    /** Launch speed of a far witch potion (vanilla 0.75 reaches ~8 blocks; 1.0 reaches 16+) [INFÉRÉ]. */
    static final double WITCH_POTION_SPEED = 1.0, POTION_GRAVITY = 0.05;
    /** Vanilla witch throws reach about this far; beyond it the throw is re-aimed with {@link #WITCH_POTION_SPEED}. */
    static final double VANILLA_POTION_REACH = 8;
    /** EF-IA-08: witch XP multiplier (applied through the hardscrabble:xp_multiplier data map, AiData). Witch wolves: 2 per summon, every 30 s [INFÉRÉ]. */
    public static final int WITCH_XP_MULTIPLIER = 4, WITCH_WOLVES = 2, WITCH_SUMMON_COOLDOWN = 600;

    /**
     * EF-IA-03: a projectile aimed at where a target stands hits where it will be once the target's horizontal motion is
     * added to its velocity (exact in the target's frame for straight-line motion; drag ignored).
     */
    public static Vec3 lead(Vec3 velocity, Vec3 targetMotion) {
        return velocity.add(targetMotion.x, 0, targetMotion.z);
    }

    /**
     * EF-IA-03: blaze and ghast fireballs keep their (spread) heading but aim ahead by the target's horizontal motion over
     * the flight time, taken at {@link #FIREBALL_SPEED} [INFÉRÉ: fireballs accelerate, ~1 block per tick on average].
     */
    public static Vec3 leadFireball(Vec3 velocity, Vec3 delta, Vec3 targetMotion) {
        double distance = delta.length(), speed = velocity.length();
        if (speed < 1e-6) return velocity;
        Vec3 aim = velocity.scale(distance / speed).add(targetMotion.x * distance / FIREBALL_SPEED, 0, targetMotion.z * distance / FIREBALL_SPEED);
        return aim.normalize().scale(speed);
    }

    static final double FIREBALL_SPEED = 1.0;

    /** Low-arc launch velocity of the given speed reaching {@code delta} under {@code gravity} per tick (45 degrees when out of reach). Drag ignored. */
    public static Vec3 ballistic(Vec3 delta, double speed, double gravity) {
        double d = Math.sqrt(delta.x * delta.x + delta.z * delta.z), v2 = speed * speed;
        double disc = v2 * v2 - gravity * (gravity * d * d + 2 * delta.y * v2);
        double angle = disc < 0 || d < 1e-6 ? Math.PI / 4 : Math.atan2(v2 - Math.sqrt(disc), gravity * d);
        double h = speed * Math.cos(angle) / Math.max(d, 1e-6);
        return new Vec3(delta.x * h, speed * Math.sin(angle), delta.z * h);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Witch witch) {
            witch.goalSelector.removeAllGoals(g -> g instanceof RangedAttackGoal || g instanceof SummonGoal);
            witch.goalSelector.addGoal(2, new RangedAttackGoal(witch, 1.0, 60, WITCH_ATTACK_RADIUS));
            witch.goalSelector.addGoal(2, new SummonGoal(witch, () -> EntityTypes.WOLF, WITCH_WOLVES, WITCH_SUMMON_COOLDOWN));
        } else if (event.getEntity() instanceof Squid squid) {
            squid.goalSelector.addGoal(2, new SquidHuntGoal(squid));
        } else if (event.getEntity() instanceof Projectile projectile && !event.loadedFromDisk() && !((ProjectileAccessor) projectile).mite$hasBeenShot()) {
            // A projectile crossing a portal joins again as a copy, already shot: aimed once only.
            aim(projectile);
        }
    }

    /** Changelog l.529 (R132): a burning skeleton's arrow is set alight 1 time in {@link #BURNING_ARROW_ODDS} [INFÉRÉ: "a chance"]. */
    public static final int BURNING_ARROW_ODDS = 2;

    /** Re-aims a freshly shot projectile: skeleton arrows lead the target (K03), far witch potions fly farther (K08). */
    static void aim(Projectile projectile) {
        if (projectile instanceof AbstractArrow && projectile.getOwner() instanceof AbstractSkeleton skeleton) {
            if (skeleton.isOnFire() && skeleton.getRandom().nextInt(BURNING_ARROW_ODDS) == 0) projectile.igniteForSeconds(100);
            if (skeleton.getTarget() != null) projectile.setDeltaMovement(lead(projectile.getDeltaMovement(), skeleton.getTarget().getKnownMovement()));
        } else if (projectile instanceof AbstractHurtingProjectile && projectile.getOwner() instanceof Mob owner
                && (owner instanceof Blaze || owner instanceof Ghast) && owner.getTarget() != null) {
            projectile.setDeltaMovement(leadFireball(projectile.getDeltaMovement(), owner.getTarget().position().subtract(projectile.position()), owner.getTarget().getKnownMovement()));
        } else if (projectile instanceof AbstractThrownPotion && projectile.getOwner() instanceof Witch witch && witch.getTarget() != null) {
            LivingEntity target = witch.getTarget();
            Vec3 delta = target.position().add(0, target.getBbHeight() / 2, 0).subtract(projectile.position());
            if (delta.horizontalDistance() <= VANILLA_POTION_REACH) return;
            projectile.setDeltaMovement(lead(ballistic(delta, WITCH_POTION_SPEED, POTION_GRAVITY), target.getKnownMovement()));
        }
    }

    /** EF-IA-09 / ET-K-09: every creeper gets the configured radius at spawn, doubled for the Infernal Creeper (EF-MOB-13). */
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity() instanceof Creeper creeper) creeper.explosionRadius = radius(creeper);
    }

    public static int radius(Creeper creeper) {
        return MiteConfig.CREEPER_EXPLOSION_RADIUS.get() * (creeper instanceof InfernalCreeper ? InfernalCreeper.RADIUS_MULTIPLIER : 1);
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(VanillaMobRules::onJoin);
        NeoForge.EVENT_BUS.addListener(VanillaMobRules::onFinalizeSpawn);
    }
}
