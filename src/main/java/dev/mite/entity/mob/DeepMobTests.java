package dev.mite.entity.mob;

import dev.mite.core.Material;
import dev.mite.equipment.EquipmentContent;
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
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** GameTests for EF-MOB-10/11/15 (Clay Golem)/16/17/18: attributes and XP (CA-1), immunities and behaviours. */
public final class DeepMobTests {
    private DeepMobTests() {}

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): gelatinous_dissolve_items=false keeps dropped items out of the cubes' reach.
        MiteGameTests.add("config_gelatinous_dissolve_items", 20, h -> {
            ServerLevel level = h.getLevel();
            GelatinousCube ooze = cube(h, MobContent.OOZE.get(), 3);
            ItemEntity item = new ItemEntity(level, ooze.getX(), ooze.getY(), ooze.getZ(), new ItemStack(Items.IRON_INGOT));
            level.addFreshEntity(item);
            MiteGameTests.withConfig(dev.mite.config.MiteConfig.GELATINOUS_DISSOLVE_ITEMS, false, () -> {
                ooze.dissolve(level);
                return null;
            });
            h.assertFalse(item.isRemoved(), "item dissolved with gelatinous_dissolve_items=false");
            ooze.dissolve(level);
            h.assertTrue(item.isRemoved(), "item kept with gelatinous_dissolve_items=true");
            h.succeed();
        });

        // CA-1: health, damage, XP (spider 5: Phase x2, Widow x8/5; zombie 5: Clay Golem x3; silverfish variants 10; bats 5 / 10).
        MiteGameTests.add("mob_deep_attributes", 20, h -> {
            stats(h, MobContent.PHASE_SPIDER.get(), 12, 3, 10);
            stats(h, MobContent.BLACK_WIDOW.get(), 12, 4, 8);
            stats(h, MobContent.CLAY_GOLEM.get(), 30, 6, 15);
            stats(h, MobContent.VAMPIRE_BAT.get(), 3, 1, 5);
            stats(h, MobContent.GIANT_VAMPIRE_BAT.get(), 6, 2, 10);
            stats(h, MobContent.NIGHTWING.get(), 6, 2, 10);
            h.assertValueEqual(h.spawn(MobContent.GIANT_VAMPIRE_BAT.get(), 3, 2, 3).getAttributeValue(Attributes.SCALE), 2.0, "giant scale");
            for (var fish : java.util.List.of(MobContent.COPPERSPINE, MobContent.NETHERSPAWN, MobContent.HOARY_SILVERFISH)) stats(h, fish.get(), 8, 3, 10);
            GelatinousCube cube = h.spawn(MobContent.PUDDING.get(), 3, 1, 3);
            cube.setSize(4, true);
            h.assertValueEqual(cube.getHealth(), 16.0F, "size 4 cube health");
            h.assertValueEqual(cube.getAttributeValue(Attributes.ATTACK_DAMAGE), 4.0, "size 4 cube damage");
            h.succeed();
        });
        // EF-MOB-10: the Phase Spider blinks up to 4 nodes along its path.
        MiteGameTests.add("mob_phase_spider", 20, h -> {
            PhaseSpider spider = h.spawn(MobContent.PHASE_SPIDER.get(), 1, 1, 1);
            BlockPos goal = h.absolutePos(new BlockPos(5, 1, 5));
            h.runAfterDelay(5, () -> { // ground navigation needs the spider on the ground
                Vec3 start = spider.position();
                h.assertTrue(spider.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0), "path found");
                h.assertTrue(spider.phase(), "phased");
                h.assertTrue(spider.position().distanceTo(start) > 2, "blink distance " + spider.position().distanceTo(start));
                h.succeed();
            });
        });
        // EF-MOB-11: half the spawns refused; the bite poisons.
        MiteGameTests.add("mob_black_widow", 20, h -> {
            BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
            RandomSource random = RandomSource.create(11);
            int accepted = 0;
            for (int i = 0; i < 1000; i++)
                if (BlackWidow.checkSpawnRules(MobContent.BLACK_WIDOW.get(), h.getLevel(), EntitySpawnReason.TRIAL_SPAWNER, pos, random)) accepted++;
            h.assertTrue(accepted > 440 && accepted < 560, "accepted " + accepted + " / 1000");
            BlackWidow widow = h.spawn(MobContent.BLACK_WIDOW.get(), 1, 1, 1);
            Cow cow = h.spawn(EntityTypes.COW, 2, 1, 1);
            h.assertTrue(widow.doHurtTarget(h.getLevel(), cow) && cow.hasEffect(MobEffects.POISON), "venomous bite");
            h.succeed();
        });
        // EF-MOB-15: the Clay Golem shrugs off swords and pickaxes, a shovel hurts; spawns next to clay.
        MiteGameTests.add("mob_clay_golem", 20, h -> {
            ServerLevel level = h.getLevel();
            ClayGolem golem = h.spawn(MobContent.CLAY_GOLEM.get(), 2, 1, 2);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(hurts(golem, level.damageSources().playerAttack(p)), "iron sword hurts");
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            h.assertFalse(hurts(golem, level.damageSources().playerAttack(p)), "pickaxe hurts");
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
            h.assertTrue(hurts(golem, level.damageSources().playerAttack(p)), "shovel spares");
            h.setBlock(5, 1, 5, Blocks.CLAY);
            h.assertTrue(clayGolemSpawns(h, new BlockPos(4, 1, 5)), "spawn by clay");
            h.assertFalse(clayGolemSpawns(h, new BlockPos(1, 1, 1)), "spawn far from clay");
            h.succeed();
        });
        // EF-MOB-16 CA-2 (R134): Ooze hurt only by lava and enchanted weapons; Pudding also by fire; Jelly by anything.
        MiteGameTests.add("mob_gelatinous_immunity", 20, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            GelatinousCube ooze = cube(h, MobContent.OOZE.get(), 1);
            GelatinousCube pudding = cube(h, MobContent.PUDDING.get(), 3);
            GelatinousCube jelly = cube(h, MobContent.JELLY.get(), 5);
            h.assertFalse(hurts(ooze, level.damageSources().playerAttack(p)), "sword hurts the ooze");
            h.assertFalse(hurts(ooze, level.damageSources().inFire()), "fire hurts the ooze");
            h.assertFalse(hurts(ooze, level.damageSources().magic()), "magic hurts the ooze");
            h.assertFalse(hurts(ooze, level.damageSources().thrown(new Snowball(level, p, new ItemStack(Items.SNOWBALL)), p)), "snowball hurts the ooze");
            h.assertTrue(hurts(ooze, level.damageSources().lava()), "lava spares the ooze");
            h.assertFalse(hurts(pudding, level.damageSources().playerAttack(p)), "sword hurts the pudding");
            h.assertTrue(hurts(pudding, level.damageSources().inFire()), "fire spares the pudding");
            h.assertTrue(hurts(jelly, level.damageSources().playerAttack(p)), "sword spares the jelly");
            ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
            enchanted.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
            p.setItemSlot(EquipmentSlot.MAINHAND, enchanted);
            h.assertTrue(hurts(ooze, level.damageSources().playerAttack(p)), "enchanted sword spares the ooze");
            h.assertTrue(hurts(pudding, level.damageSources().playerAttack(p)), "enchanted sword spares the pudding");
            h.succeed();
        });
        // EF-MOB-16 (R134): pepsin digests meat, acid dissolves iron (not gold) and doors, never planks; the Blob paralyses.
        MiteGameTests.add("mob_gelatinous_dissolve", 20, h -> {
            ServerLevel level = h.getLevel();
            GelatinousCube blob = cube(h, MobContent.BLOB.get(), 1);
            GelatinousCube ooze = cube(h, MobContent.OOZE.get(), 5);
            h.assertTrue(blob.dissolves(new ItemStack(Items.BEEF)) && !blob.dissolves(new ItemStack(Items.IRON_INGOT)), "pepsin digests meat only");
            h.assertTrue(ooze.dissolves(new ItemStack(Items.IRON_INGOT)) && ooze.dissolves(new ItemStack(Items.STICK)), "acid dissolves metal and wood");
            h.assertFalse(ooze.dissolves(new ItemStack(Items.GOLD_INGOT)) || ooze.dissolves(new ItemStack(Items.COBBLESTONE)) || ooze.dissolves(new ItemStack(Items.BEEF)),
                    "gold, stone and meat resist the acid");
            ItemEntity item = new ItemEntity(level, ooze.getX(), ooze.getY(), ooze.getZ(), new ItemStack(Items.IRON_INGOT));
            level.addFreshEntity(item);
            h.setBlock(6, 1, 4, Blocks.OAK_FENCE);
            h.setBlock(2, 1, 4, Blocks.OAK_PLANKS);
            ooze.dissolve(level);
            blob.dissolve(level);
            h.assertTrue(item.isRemoved(), "item dissolved");
            h.assertBlockPresent(Blocks.AIR, 6, 1, 4);
            h.assertBlockPresent(Blocks.OAK_PLANKS, 2, 1, 4);
            Cow cow = h.spawn(EntityTypes.COW, 1, 1, 5);
            blob.dealDamage(cow);
            h.assertTrue(cow.hasEffect(MobEffects.SLOWNESS), "blob paralysis");
            BlockPos open = h.absolutePos(new BlockPos(1, 1, 1));
            h.assertFalse(MobContent.underStone(level, open), "jelly under the sky");
            h.setBlock(1, 3, 1, Blocks.STONE);
            h.assertTrue(MobContent.underStone(level, open), "jelly under stone");
            h.succeed();
        });
        // l.483-484 (R134): a living Ooze, on its own ticks, dissolves the wood and metal items dropped on it (the dropped forms of
        // the blocks it dissolves too) and leaves gold, stone and meat.
        MiteGameTests.add("mob_ooze_dissolves_dropped", 60, h -> {
            ServerLevel level = h.getLevel();
            GelatinousCube ooze = cube(h, MobContent.OOZE.get(), 3);
            ooze.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); // stays on the items
            Item[] soluble = {Items.IRON_INGOT, Items.STICK, Items.OAK_DOOR, Items.IRON_DOOR, Items.RAIL, Items.LADDER, Items.OAK_FENCE, Items.BUCKET,
                    Items.OAK_BOAT, Items.CHEST, EquipmentContent.BUCKETS.get(Material.SILVER).empty().get()};
            Item[] resistant = {Items.GOLD_INGOT, Items.COBBLESTONE, Items.BEEF, Items.LEATHER, EquipmentContent.BUCKETS.get(Material.GOLD).empty().get()};
            java.util.Map<Item, ItemEntity> drops = new java.util.LinkedHashMap<>();
            for (Item i : soluble) drops.put(i, new ItemEntity(level, ooze.getX(), ooze.getY() + 0.2, ooze.getZ(), new ItemStack(i), 0, 0, 0));
            for (Item i : resistant) drops.put(i, new ItemEntity(level, ooze.getX(), ooze.getY() + 0.2, ooze.getZ(), new ItemStack(i), 0, 0, 0));
            drops.values().forEach(level::addFreshEntity);
            h.succeedWhen(() -> {
                for (Item i : soluble) h.assertTrue(drops.get(i).isRemoved(), i + " not dissolved");
                for (Item i : resistant) h.assertFalse(drops.get(i).isRemoved(), i + " dissolved");
            });
        });
        // EF-MOB-17: a bite heals the bat by the health it drains; the Nightwing is immaterial.
        MiteGameTests.add("mob_vampire_bat", 20, h -> {
            ServerLevel level = h.getLevel();
            VampireBat bat = h.spawn(MobContent.VAMPIRE_BAT.get(), 1, 2, 1);
            Cow cow = h.spawn(EntityTypes.COW, 2, 1, 1);
            bat.setHealth(1);
            h.assertTrue(bat.doHurtTarget(level, cow), "bite");
            h.assertValueEqual(bat.getHealth(), 1 + cow.getMaxHealth() - cow.getHealth(), "healed by the drained health");
            h.assertTrue(bat.getHealth() > 1, "healed");
            Nightwing wing = h.spawn(MobContent.NIGHTWING.get(), 5, 2, 5);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(hurts(wing, level.damageSources().playerAttack(p)), "iron sword hurts the nightwing");
            h.assertFalse(hurts(wing, level.damageSources().inFire()), "fire hurts the nightwing");
            h.assertTrue(hurts(wing, level.damageSources().magic()), "magic spares the nightwing");
            h.succeed();
        });
        // EF-MOB-18: Copperspine poisons, Hoary poisons and paralyses, Netherspawn explodes on death.
        MiteGameTests.add("mob_silverfish_variants", 20, h -> {
            ServerLevel level = h.getLevel();
            Cow cow = h.spawn(EntityTypes.COW, 1, 1, 1);
            h.assertTrue(h.spawn(MobContent.COPPERSPINE.get(), 2, 1, 1).doHurtTarget(level, cow) && cow.hasEffect(MobEffects.POISON), "copperspine poison");
            Cow cow2 = h.spawn(EntityTypes.COW, 1, 1, 5);
            h.assertTrue(h.spawn(MobContent.HOARY_SILVERFISH.get(), 2, 1, 5).doHurtTarget(level, cow2) && cow2.hasEffect(MobEffects.POISON)
                    && cow2.getEffect(MobEffects.SLOWNESS).getAmplifier() == Ghoul.PARALYSIS_AMPLIFIER, "hoary poison and paralysis");
            Cow cow3 = h.spawn(EntityTypes.COW, 5, 1, 3);
            h.spawn(MobContent.NETHERSPAWN.get(), 4, 1, 3).kill(level);
            h.assertTrue(cow3.getHealth() < cow3.getMaxHealth(), "netherspawn blast");
            h.succeed();
        });
    }

    private static GelatinousCube cube(GameTestHelper h, EntityType<GelatinousCube> type, int x) {
        GelatinousCube cube = h.spawn(type, x, 1, 3);
        cube.setSize(4, true);
        return cube;
    }

    private static boolean clayGolemSpawns(GameTestHelper h, BlockPos pos) {
        return ClayGolem.checkSpawnRules(MobContent.CLAY_GOLEM.get(), h.getLevel(), EntitySpawnReason.TRIAL_SPAWNER, h.absolutePos(pos), RandomSource.create());
    }

    private static boolean hurts(LivingEntity entity, DamageSource source) {
        entity.damageCooldownTime = 0;
        float before = entity.getHealth();
        entity.hurtServer((ServerLevel) entity.level(), source, 1);
        return entity.getHealth() < before;
    }

    private static void stats(GameTestHelper h, EntityType<? extends Mob> type, double health, double damage, int xp) {
        Mob mob = h.spawn(type, 3, 2, 3);
        String name = type.toShortString();
        h.assertValueEqual(mob.getAttributeValue(Attributes.MAX_HEALTH), health, name + " max health");
        h.assertValueEqual(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, name + " attack damage");
        h.assertValueEqual(mob.getExperienceReward(h.getLevel(), null), xp, name + " XP");
    }
}
