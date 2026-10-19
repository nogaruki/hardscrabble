package dev.mite.entity.ai;

import dev.mite.entity.mob.MobContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/** GameTests for EF-IA-03 (leading shots), EF-IA-08 (witch) and the Earth Elemental digging (EF-MOB-15 / EF-IA-01). */
public final class RangedAiTests {
    private RangedAiTests() {}

    /** Arrow physics per tick (AbstractArrow): move, drag 0.99, gravity 0.05. */
    private static final double ARROW_DRAG = 0.99, ARROW_GRAVITY = 0.05;

    public static void register() {
        // EF-IA-03 CA-1: 100 hard-difficulty shots at a target strafing 0.2 block/tick 12 blocks away; leading hits more.
        MiteGameTests.add("ai_skeleton_lead_hit_rate", 20, h -> {
            RandomSource random = RandomSource.create(3);
            Vec3 target = new Vec3(12, 0, 0), motion = new Vec3(0, 0, 0.2);
            int vanilla = 0, led = 0;
            for (int i = 0; i < 100; i++) {
                // AbstractSkeleton#performRangedAttack: aim at the target's lower third, +20 % of the distance up, speed 1.6, uncertainty 2.
                Vec3 v = new Vec3(12, 0.6 - 1.5 + 12 * 0.2, 0).normalize()
                        .add(random.triangle(0, 0.0172275 * 2), random.triangle(0, 0.0172275 * 2), random.triangle(0, 0.0172275 * 2)).scale(1.6);
                if (hits(v, target, motion)) vanilla++;
                if (hits(VanillaMobRules.lead(v, motion), target, motion)) led++;
            }
            h.assertTrue(led >= vanilla + 50, "hits: led " + led + ", vanilla " + vanilla);
            h.succeed();
        });
        // EF-IA-03: an arrow shot by a skeleton gets its target's motion added on spawn.
        MiteGameTests.add("ai_skeleton_arrow_leads", 20, h -> {
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 1, 1, 1);
            Zombie target = h.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
            target.setDeltaMovement(0, 0, 0.3);
            skeleton.setTarget(target);
            Arrow arrow = EntityTypes.ARROW.create(h.getLevel(), EntitySpawnReason.COMMAND);
            arrow.setOwner(skeleton);
            arrow.setDeltaMovement(1, 0.2, 0);
            VanillaMobRules.aim(arrow);
            h.assertTrue(arrow.getDeltaMovement().distanceTo(new Vec3(1, 0.2, 0.3)) < 1e-6, "arrow velocity " + arrow.getDeltaMovement());
            h.succeed();
        });
        // EF-IA-08: the witch summons, reaches 16 blocks with a ballistic throw, gives 4x XP.
        MiteGameTests.add("ai_witch", 20, h -> {
            Witch witch = h.spawn(EntityTypes.WITCH, 1, 1, 1);
            h.assertTrue(witch.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).anyMatch(g -> g instanceof SummonGoal), "witch summon goal");
            LivingExperienceDropEvent xp = NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(witch, null, 5));
            h.assertValueEqual(xp.getDroppedExperience(), 5 * VanillaMobRules.WITCH_XP_MULTIPLIER, "witch XP");
            Vec3 delta = new Vec3(16, -1, 3);
            Vec3 v = VanillaMobRules.ballistic(delta, VanillaMobRules.WITCH_POTION_SPEED, VanillaMobRules.POTION_GRAVITY);
            h.assertTrue(Math.abs(v.length() - VanillaMobRules.WITCH_POTION_SPEED) < 1e-6, "throw speed");
            double t = delta.horizontalDistance() / v.horizontalDistance(), g = VanillaMobRules.POTION_GRAVITY;
            h.assertTrue(Math.abs(v.y * t - g * t * t / 2 - delta.y) < 1e-6 && Math.abs(v.x * delta.z - v.z * delta.x) < 1e-9, "throw reaches the target");
            h.succeed();
        });
        // EF-MOB-15: walled in by stone, the Earth Elemental digs toward its target within a few ticks.
        MiteGameTests.add("ai_earth_elemental_digs_stone", 80, h -> {
            var earth = AiTests.cell(h, MobContent.EARTH_ELEMENTAL.get(), Blocks.STONE, Blocks.STONE);
            ServerPlayer p = MetabolismTests.player(h);
            p.snapTo(h.absoluteVec(new Vec3(3.5, 1, 5.5)));
            earth.setTarget(p);
            h.succeedWhen(() -> h.assertBlockNotPresent(Blocks.STONE, 3, 1, 2));
        });
    }

    /** Whether an arrow from (0, 1.5, 0) passes within the target's 0.6 x 1.8 box while both move for 40 ticks. */
    private static boolean hits(Vec3 velocity, Vec3 target, Vec3 motion) {
        Vec3 pos = new Vec3(0, 1.5, 0);
        for (int t = 0; t < 40; t++) {
            pos = pos.add(velocity);
            velocity = velocity.scale(ARROW_DRAG).subtract(0, ARROW_GRAVITY, 0);
            target = target.add(motion);
            // x: half an arrow step (0.8), the box is crossed between two ticks.
            if (Math.abs(pos.x - target.x) < 0.8 && Math.abs(pos.z - target.z) < 0.3 && pos.y > target.y && pos.y < target.y + 1.8) return true;
        }
        return false;
    }
}
