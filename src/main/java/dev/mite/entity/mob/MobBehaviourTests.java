package dev.mite.entity.mob;

import dev.mite.equipment.EquipmentContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismContent;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** GameTests of the audit 2 lot H on the MITE mobs: elementals (l.281-282, 504, 540, 546-547), cubes (R134-R143), bats (l.414), Phase Spider (l.109). */
public final class MobBehaviourTests {
    private MobBehaviourTests() {}

    public static void register() {
        // l.540 / l.281 / l.282: knockback resistant, smashes doors fast, hurt by an iron golem's fist, heated by lightning.
        MiteGameTests.add("mob_earth_elemental_rules", 20, h -> {
            ServerLevel level = h.getLevel();
            EarthElemental earth = h.spawn(MobContent.EARTH_ELEMENTAL.get(), 1, 1, 1);
            h.assertValueEqual(earth.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1.0, "earth elemental knockback resistance");
            h.assertValueEqual(h.spawn(MobContent.CLAY_GOLEM.get(), 5, 1, 1).getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 1.0, "clay golem knockback resistance");
            h.assertTrue(earth.canBreakDoors() && earth.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal)
                    .anyMatch(g -> g instanceof EarthElemental.SmashDoorGoal), "fast door goal");
            IronGolem golem = h.spawn(EntityTypes.IRON_GOLEM, 5, 1, 5);
            h.assertTrue(hurts(earth, level.damageSources().mobAttack(golem)), "iron golem fist spares the earth elemental");
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 3, 1, 5);
            h.assertFalse(hurts(earth, level.damageSources().mobAttack(zombie)), "a zombie fist hurts the earth elemental");
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            float health = earth.getHealth();
            earth.thunderHit(level, bolt);
            h.assertTrue(earth.isHeated() && earth.fireImmune() && earth.displayFireAnimation(), "heated by lightning");
            h.assertValueEqual(earth.getHealth(), health, "lightning hurts the earth elemental");
            h.succeed();
        });
        // l.546: bowls and buckets of water or milk hurt a fire elemental and are emptied; lava does nothing.
        MiteGameTests.add("mob_fire_elemental_pour", 20, h -> {
            FireElemental fire = h.spawn(MobContent.FIRE_ELEMENTAL.get(), 3, 1, 3);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            var copper = EquipmentContent.BUCKETS.get(dev.mite.core.Material.COPPER);
            h.assertTrue(FireElemental.pour(new ItemStack(copper.milk().get())).empty() == copper.empty().get(), "copper milk bucket");
            h.assertTrue(FireElemental.pour(new ItemStack(MetabolismContent.WATER_BOWL.get())).empty() == Items.BOWL, "water bowl");
            h.assertTrue(FireElemental.pour(new ItemStack(Items.LAVA_BUCKET)) == null, "lava bucket pours");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            float before = fire.getHealth();
            var event = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, fire, fire.position());
            FireElemental.onEntityInteract(event);
            h.assertTrue(event.isCanceled() && fire.getHealth() < before, "water bucket did not hurt: " + fire.getHealth());
            h.assertTrue(player.getMainHandItem().is(Items.BUCKET), "bucket not emptied: " + player.getMainHandItem());
            h.succeed();
        });
        // l.504 / l.547: deep source lava always takes a spawn, one-block-deep lava about 1 in 4; 3 fire elementals around refuse more.
        MiteGameTests.add("mob_fire_elemental_spawn", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 1, 1, Blocks.LAVA);
            h.setBlock(1, 2, 1, Blocks.LAVA);
            h.setBlock(5, 1, 5, Blocks.LAVA);
            BlockPos deep = h.absolutePos(new BlockPos(1, 2, 1)), shallow = h.absolutePos(new BlockPos(5, 1, 5));
            RandomSource random = RandomSource.create(7);
            h.assertTrue(FireElemental.lavaAllows(level, deep, random), "deep lava refused");
            int accepted = 0;
            for (int i = 0; i < 400; i++) if (FireElemental.lavaAllows(level, shallow, random)) accepted++;
            h.assertTrue(accepted > 60 && accepted < 140, "shallow lava accepted " + accepted + " / 400");
            // the whole rule on deep lava just under the depth cap, refused on the same lava at the cap (columns restored this tick)
            Map<BlockPos, BlockState> saved = new HashMap<>();
            BlockPos under = lavaColumn(level, h.absolutePos(new BlockPos(1, 0, 5)).atY(FireElemental.MAX_SPAWN_Y - 1), saved);
            BlockPos atCap = lavaColumn(level, h.absolutePos(new BlockPos(5, 0, 1)).atY(FireElemental.MAX_SPAWN_Y), saved);
            boolean spawnsUnder = FireElemental.checkSpawnRules(MobContent.FIRE_ELEMENTAL.get(), level, EntitySpawnReason.NATURAL, under, random);
            boolean spawnsAtCap = FireElemental.checkSpawnRules(MobContent.FIRE_ELEMENTAL.get(), level, EntitySpawnReason.NATURAL, atCap, random);
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
            h.assertTrue(spawnsUnder, "deep lava under y " + FireElemental.MAX_SPAWN_Y + " refused");
            h.assertFalse(spawnsAtCap, "spawned in lava at y " + FireElemental.MAX_SPAWN_Y);
            for (int i = 0; i < FireElemental.CAP; i++) h.spawn(MobContent.FIRE_ELEMENTAL.get(), 3, 1, 3);
            h.assertFalse(FireElemental.lavaAllows(level, deep, random), "crowd not capped");
            h.succeed();
        });
        // l.481-482 / l.472: Jelly > slime, Blob > Jelly; XP by kind.
        MiteGameTests.add("mob_cube_damage", 20, h -> {
            h.assertValueEqual(cube(h, MobContent.JELLY.get(), 1).getAttributeValue(Attributes.ATTACK_DAMAGE), 6.0, "size 4 jelly damage");
            GelatinousCube blob = cube(h, MobContent.BLOB.get(), 3);
            h.assertValueEqual(blob.getAttributeValue(Attributes.ATTACK_DAMAGE), 8.0, "size 4 blob damage");
            h.assertValueEqual(blob.getExperienceReward(h.getLevel(), null), 12, "size 4 blob XP");
            h.succeed();
        });
        // l.491 / l.492 / l.483 / l.402: the striking sword is corroded, arrows dissolve in an Ooze, acid eats the held sword, sheep lose wool.
        MiteGameTests.add("mob_cube_acid", 20, h -> {
            ServerLevel level = h.getLevel();
            GelatinousCube ooze = cube(h, MobContent.OOZE.get(), 1);
            Arrow arrow = new Arrow(level, ooze.getX(), ooze.getY() + 3, ooze.getZ(), new ItemStack(Items.ARROW), null);
            level.addFreshEntity(arrow);
            h.assertTrue(EventHooks.onProjectileImpact(arrow, new EntityHitResult(ooze)) && arrow.isRemoved(), "arrow not dissolved");
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2.5f, 1, 1.5f);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            ooze.dealDamage(zombie);
            h.assertTrue(zombie.getMainHandItem().getDamageValue() > 0, "held iron sword not corroded");
            ItemStack gold = new ItemStack(Items.GOLDEN_SWORD);
            h.assertFalse(ooze.dissolves(gold), "acid eats gold");
            Sheep sheep = h.spawn(EntityTypes.SHEEP, 5, 1, 5);
            Slime slime = h.spawn(EntityTypes.SLIME, 5, 1, 3);
            sheep.hurtServer(level, level.damageSources().mobAttack(slime), 1);
            h.assertTrue(sheep.isSheared(), "sheep keeps its wool");
            h.succeed();
        });
        // l.494 / l.457: a slime hunts animals and hurts them on contact.
        MiteGameTests.add("mob_cube_hunts_animals", 200, h -> {
            Slime slime = h.spawn(EntityTypes.SLIME, 1, 1, 1);
            slime.setSize(2, true);
            Cow cow = h.spawn(EntityTypes.COW, 2, 1, 1);
            h.succeedWhen(() -> {
                h.assertTrue(slime.getTarget() instanceof Animal, "slime has no animal target: " + slime.getTarget());
                h.assertTrue(cow.getHealth() < cow.getMaxHealth(), "cow unhurt");
            });
        });
        // l.490: a cube bumping a wall torch knocks it down.
        MiteGameTests.add("mob_cube_torches", 20, h -> {
            h.setBlock(3, 1, 4, Blocks.STONE);
            h.setBlock(new BlockPos(3, 1, 3), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            Slime slime = h.spawn(EntityTypes.SLIME, 3, 1, 3);
            slime.setSize(2, true);
            CubeRules.knockTorches(h.getLevel(), slime);
            h.assertTrue(h.getBlockState(new BlockPos(3, 1, 3)).isAir(), "wall torch still there");
            h.succeed();
        });
        // l.414: the Vampire Bat bites livestock once, never under 1 health, then is sated; the Giant still kills.
        MiteGameTests.add("mob_vampire_bat_feeds", 20, h -> {
            ServerLevel level = h.getLevel();
            VampireBat bat = h.spawn(MobContent.VAMPIRE_BAT.get(), 1, 2, 1);
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            cow.setHealth(1.5f);
            bat.feed(level, cow);
            h.assertTrue(cow.isAlive() && cow.getHealth() >= 1, "cow bitten to death: " + cow.getHealth());
            h.assertTrue(bat.isSated() && bat.getTarget() == null, "bat not sated");
            h.assertFalse(h.spawn(MobContent.GIANT_VAMPIRE_BAT.get(), 5, 2, 5).feedsOnLivestock(), "giant spares livestock");
            h.succeed();
        });
        // l.109: the Phase Spider blinks away from blows.
        MiteGameTests.add("mob_phase_spider_dodge", 20, h -> {
            ServerLevel level = h.getLevel();
            PhaseSpider spider = h.spawn(MobContent.PHASE_SPIDER.get(), 3, 1, 3);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            int dodged = 0;
            for (int i = 0; i < 60; i++) {
                spider.snapTo(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
                spider.setHealth(spider.getMaxHealth());
                spider.damageCooldownTime = 0;
                if (!spider.hurtServer(level, level.damageSources().playerAttack(player), 1) && spider.position().distanceTo(h.absoluteVec(new Vec3(3.5, 1, 3.5))) > 1)
                    dodged++;
            }
            h.assertTrue(dodged > 5 && dodged < 40, "dodged " + dodged + " / 60");
            h.succeed();
        });
    }

    private static GelatinousCube cube(net.minecraft.gametest.framework.GameTestHelper h, net.minecraft.world.entity.EntityType<GelatinousCube> type, int x) {
        GelatinousCube cube = h.spawn(type, x, 1, 1);
        cube.setSize(4, true);
        return cube;
    }

    private static boolean hurts(LivingEntity entity, DamageSource source) {
        entity.damageCooldownTime = 0;
        float before = entity.getHealth();
        entity.hurtServer((ServerLevel) entity.level(), source, 3);
        return entity.getHealth() < before;
    }

    /** Two lava sources, {@code top} and the block under it; the replaced states go to {@code saved}. Returns {@code top}. */
    private static BlockPos lavaColumn(ServerLevel level, BlockPos top, Map<BlockPos, BlockState> saved) {
        for (BlockPos pos : new BlockPos[] {top, top.below()}) {
            saved.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return top;
    }
}
