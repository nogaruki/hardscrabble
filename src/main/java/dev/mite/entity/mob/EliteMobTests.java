package dev.mite.entity.mob;

import dev.mite.entity.ai.VanillaMobRules;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** GameTests for EF-MOB-13/14/15/20 (CA-1 attributes and XP, CA-2 immunities, behaviours) and EF-IA-09 (creeper radius). */
public final class EliteMobTests {
    private EliteMobTests() {}

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): creeper_explosion_radius sets the creeper radius, the Infernal creeper keeps its x2.
        MiteGameTests.add("config_creeper_explosion_radius", 20, h -> {
            Creeper creeper = h.spawn(EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.COMMAND);
            InfernalCreeper infernal = h.spawn(MobContent.INFERNAL_CREEPER.get(), new BlockPos(5, 1, 5), EntitySpawnReason.COMMAND);
            int radius = MiteGameTests.withConfig(dev.mite.config.MiteConfig.CREEPER_EXPLOSION_RADIUS, 5, () -> VanillaMobRules.radius(creeper));
            int infernalRadius = MiteGameTests.withConfig(dev.mite.config.MiteConfig.CREEPER_EXPLOSION_RADIUS, 5, () -> VanillaMobRules.radius(infernal));
            h.assertValueEqual(radius, 5, "creeper radius at 5");
            h.assertValueEqual(infernalRadius, 10, "infernal creeper radius at 5");
            h.assertValueEqual(VanillaMobRules.radius(creeper), 3, "default creeper radius");
            h.succeed();
        });

        // CA-1: health, damage, speed, follow range, XP (monster base 5: creeper x3, elementals x3, Bone Lord x3, Ancient x2).
        MiteGameTests.add("mob_elite_attributes", 20, h -> {
            stats(h, MobContent.INFERNAL_CREEPER.get(), 20, 2, 0.25, 16, 15);
            stats(h, MobContent.FIRE_ELEMENTAL.get(), 20, 5, 0.25, 40, 15);
            stats(h, MobContent.EARTH_ELEMENTAL.get(), 30, 12, 0.2, 20, 15);
            stats(h, MobContent.BONE_LORD.get(), 20, 5, 0.26, 40, 15);
            stats(h, MobContent.ANCIENT_BONE_LORD.get(), 24, 8, 0.27, 40, 10);
            h.succeed();
        });
        // EF-MOB-13 / EF-IA-09: vanilla radius 3 by default, Infernal x2; replaces half the creepers below the random depth only.
        MiteGameTests.add("mob_infernal_creeper", 20, h -> {
            Creeper creeper = h.spawn(EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.COMMAND);
            InfernalCreeper infernal = h.spawn(MobContent.INFERNAL_CREEPER.get(), new BlockPos(5, 1, 5), EntitySpawnReason.COMMAND);
            h.assertValueEqual(creeper.explosionRadius, 3, "creeper radius");
            h.assertValueEqual(infernal.explosionRadius, 6, "infernal creeper radius");
            h.assertValueEqual(VanillaMobRules.radius(infernal), 2 * VanillaMobRules.radius(creeper), "radius rule");
            RandomSource random = RandomSource.create(7);
            int deep = 0;
            for (int i = 0; i < 1000; i++) {
                h.assertFalse(InfernalCreeper.replacesCreeper(random, 14), "replaced at y 14");
                if (InfernalCreeper.replacesCreeper(random, -64)) deep++;
            }
            h.assertTrue(deep > 400 && deep < 560, "replaced at y -64: " + deep + " / 1000");
            h.succeed();
        });
        // EF-MOB-14 CA-2: an iron sword does nothing; water, a snowball and an enchanted sword hurt. Spawns in lava only.
        MiteGameTests.add("mob_fire_elemental_immunity", 20, h -> {
            ServerLevel level = h.getLevel();
            FireElemental fire = h.spawn(MobContent.FIRE_ELEMENTAL.get(), 2, 1, 2);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(fire.hurtServer(level, level.damageSources().playerAttack(p), 4), "iron sword hurts");
            h.assertTrue(hurts(fire, level.damageSources().drown()), "water spares");
            DamageSource snowball = level.damageSources().thrown(new Snowball(level, p, new ItemStack(Items.SNOWBALL)), p);
            h.assertFalse(fire.isInvulnerableTo(level, snowball), "snowball immunity");
            h.assertTrue(hurts(fire, snowball), "snowball spares");
            ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
            enchanted.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
            p.setItemSlot(EquipmentSlot.MAINHAND, enchanted);
            h.assertTrue(hurts(fire, level.damageSources().playerAttack(p)), "enchanted sword spares");
            // the lava and depth parts are tested in mob_fire_elemental_spawn
            h.assertFalse(spawns(h, h.absolutePos(new BlockPos(1, 1, 5))), "spawn on stone");
            h.succeed();
        });
        // EF-MOB-15 CA-2: an iron sword does nothing, a pickaxe hurts.
        MiteGameTests.add("mob_earth_elemental_immunity", 20, h -> {
            ServerLevel level = h.getLevel();
            EarthElemental earth = h.spawn(MobContent.EARTH_ELEMENTAL.get(), 2, 1, 2);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(earth.hurtServer(level, level.damageSources().playerAttack(p), 4), "iron sword hurts");
            h.assertFalse(earth.hurtServer(level, level.damageSources().inFire(), 4), "fire hurts");
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(hurts(earth, level.damageSources().playerAttack(p)), "pickaxe spares");
            h.assertTrue(EarthElemental.canDig(Blocks.STONE.defaultBlockState()) && !EarthElemental.canDig(Blocks.OBSIDIAN.defaultBlockState()), "dig list");
            h.succeed();
        });
        // EF-MOB-20: the Bone Lord summons 3 skeletons at a seen player; its aura heals and hastens nearby skeletons only (l.150).
        MiteGameTests.add("mob_bone_lord", 60, h -> {
            BoneLord lord = h.spawn(MobContent.BONE_LORD.get(), 1, 1, 1);
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 3, 1, 1);
            zombie.setHealth(10);
            var skeleton = h.spawn(MobContent.LONGDEAD.get(), 1, 1, 3); // not a vanilla skeleton: those are counted as summons below
            skeleton.setHealth(10);
            lord.aura();
            h.assertTrue(skeleton.getHealth() > 10 && skeleton.hasEffect(MobEffects.SPEED), "aura on the skeleton");
            h.assertTrue(zombie.getHealth() == 10 && !zombie.hasEffect(MobEffects.SPEED), "aura on the zombie");
            ServerPlayer p = MetabolismTests.player(h);
            p.snapTo(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
            lord.setTarget(p);
            h.succeedWhen(() -> h.assertTrue(h.getLevel().getEntities(EntityTypes.SKELETON, lord.getBoundingBox().inflate(16), LivingEntity::isAlive).size() == BoneLord.MINIONS,
                    "summoned skeletons"));
        });
    }

    private static boolean hurts(LivingEntity entity, DamageSource source) {
        entity.damageCooldownTime = 0;
        float before = entity.getHealth();
        entity.hurtServer((ServerLevel) entity.level(), source, 3);
        return entity.getHealth() < before;
    }

    private static boolean spawns(GameTestHelper h, BlockPos pos) {
        return FireElemental.checkSpawnRules(MobContent.FIRE_ELEMENTAL.get(), h.getLevel(), EntitySpawnReason.NATURAL, pos, RandomSource.create(1));
    }

    private static void stats(GameTestHelper h, EntityType<? extends Mob> type, double health, double damage, double speed, double follow, int xp) {
        Mob mob = h.spawn(type, 3, 1, 3);
        String name = type.toShortString();
        h.assertValueEqual(mob.getAttributeValue(Attributes.MAX_HEALTH), health, name + " max health");
        h.assertValueEqual(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, name + " attack damage");
        h.assertTrue(Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1e-6, name + " speed");
        h.assertValueEqual(mob.getAttributeValue(Attributes.FOLLOW_RANGE), follow, name + " follow range");
        h.assertValueEqual(mob.getExperienceReward(h.getLevel(), null), xp, name + " XP");
    }
}
