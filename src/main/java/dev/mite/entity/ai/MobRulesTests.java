package dev.mite.entity.ai;

import dev.mite.entity.mob.MobContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.lunar.Lunar;
import dev.mite.lunar.LunarTests;
import dev.mite.metallurgy.Metallurgy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** GameTests for EF-IA-10 (K06, K07, K09, K10, K11), the blood moon leftovers of EF-LUN-02 and fireball leading (EF-IA-03). */
public final class MobRulesTests {
    private MobRulesTests() {}

    public static void register() {
        // K-3 (changelog l.708): the extra monster chance builds up from 0 at 256 blocks to 1 at the stronghold; the superflat
        // test world has none, so the distance there is never within reach.
        MiteGameTests.add("rules_stronghold_density", 20, h -> {
            h.assertValueEqual(MobRules.strongholdBonus(0), 1F, "bonus at the stronghold");
            h.assertValueEqual(MobRules.strongholdBonus(MobRules.STRONGHOLD_REACH / 2), 0.5F, "bonus halfway");
            h.assertValueEqual(MobRules.strongholdBonus(MobRules.STRONGHOLD_REACH * 2), 0F, "bonus out of reach");
            double distance = MobRules.strongholdDistance(h.getLevel(), h.absolutePos(BlockPos.ZERO));
            h.assertValueEqual(MobRules.strongholdBonus(distance), 0F, "bonus near spawn, stronghold at " + distance);
            // The ring lookup on a synthetic ring: the nearest chunk middle wins, no ring is infinitely far.
            List<ChunkPos> ring = List.of(new ChunkPos(10, 0), new ChunkPos(-4, 3), new ChunkPos(0, -20));
            h.assertValueEqual(MobRules.nearest(ring, new BlockPos(-50, 64, 60)), Math.hypot(6, 4), "nearest ring start (-56, 56)");
            h.assertValueEqual(MobRules.nearest(ring, new BlockPos(168, 0, 8)), 0.0, "on a ring start");
            h.assertTrue(MobRules.nearest(List.of(), BlockPos.ZERO) == Double.POSITIVE_INFINITY, "empty ring");
            // Outside fortresses and strongholds a natural monster brings no extra one.
            Zombie zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.NATURAL);
            Vec3 at = h.absoluteVec(new Vec3(3.5, 1, 3.5));
            zombie.setPos(at);
            AABB arena = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(8);
            int before = h.getLevel().getEntities(EntityTypes.ZOMBIE, arena, e -> true).size();
            NeoForge.EVENT_BUS.post(new FinalizeSpawnEvent(zombie, h.getLevel(), at.x, at.y, at.z, h.getLevel().getCurrentDifficultyAt(BlockPos.containing(at)),
                    EntitySpawnReason.NATURAL, null, null));
            h.assertValueEqual(h.getLevel().getEntities(EntityTypes.ZOMBIE, arena, e -> true).size(), before, "zombies around after a natural spawn");
            h.succeed();
        });

        // K06: a zombie hunts animals, a spider hunts chickens.
        MiteGameTests.add("rules_predation_targets", 200, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 1, 1, 1);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            h.spawn(EntityTypes.PIG, 1, 1, 3);
            Spider spider = h.spawn(EntityTypes.SPIDER, 5, 1, 1);
            h.spawn(EntityTypes.CHICKEN, 5, 1, 3);
            h.succeedWhen(() -> {
                h.assertTrue(zombie.getTarget() instanceof Animal, "zombie targets an animal");
                h.assertTrue(spider.getTarget() instanceof Chicken, "spider targets a chicken");
            });
        });
        // K06: a zombie eats the beef at its feet and heals by its nutrition.
        // 300 ticks: FetchItemGoal starts with odds 1/SEARCH_ODDS per check, 100 ticks failed about once in 70 runs
        MiteGameTests.add("rules_zombie_eats_meat", 300, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 3, 1, 3);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zombie.setCanPickUpLoot(false);
            zombie.setHealth(10);
            var beef = h.spawnItem(Items.BEEF, 3.5f, 1, 3.5f);
            h.succeedWhen(() -> {
                h.assertFalse(beef.isAlive(), "beef eaten");
                h.assertTrue(zombie.getHealth() > 10, "zombie healed");
            });
        });
        // K06: rotten flesh (in the vanilla meat tag) is not zombie food, so zombies leave the drops of slain zombies alone.
        MiteGameTests.add("rules_zombie_ignores_rotten_flesh", 20, h -> {
            h.assertTrue(MobRules.zombieFood(new ItemStack(Items.BEEF)), "beef is zombie food");
            h.assertFalse(MobRules.zombieFood(new ItemStack(Items.ROTTEN_FLESH)), "rotten flesh is zombie food");
            h.succeed();
        });
        // A zombie gives up meat it cannot reach (sealed in glass) within FetchItemGoal.GIVE_UP_TICKS and does not go back to it.
        MiteGameTests.add("rules_fetch_gives_up_unreachable", 400, h -> {
            for (int x = 4; x <= 6; x++) for (int z = 2; z <= 4; z++) for (int y = 1; y <= 2; y++) if (x != 5 || z != 3 || y != 1) h.setBlock(x, y, z, Blocks.GLASS);
            var beef = h.spawnItem(Items.BEEF, 5.5f, 1, 3.5f);
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 1, 1, 3);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zombie.setCanPickUpLoot(false);
            zombie.targetSelector.removeAllGoals(g -> true);
            WrappedGoal fetch = zombie.goalSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof FetchItemGoal).findFirst().orElseThrow();
            long[] gaveUp = {-1};
            boolean[] ran = {false};
            h.onEachTick(() -> {
                if (fetch.isRunning()) {
                    ran[0] = true;
                    h.assertTrue(gaveUp[0] < 0, "went back to the unreachable beef");
                } else if (ran[0] && gaveUp[0] < 0) gaveUp[0] = h.getTick();
                if (gaveUp[0] >= 0 && h.getTick() - gaveUp[0] > 100) {
                    h.assertTrue(beef.isAlive(), "beef still there");
                    h.succeed();
                }
            });
        });
        // K07: endermen turn on players carrying a pearl or an eye of ender.
        MiteGameTests.add("rules_enderman_pearls", 20, h -> {
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            h.assertFalse(MobRules.carriesEnderItem(player), "empty inventory");
            player.getInventory().add(new ItemStack(Items.ENDER_EYE));
            h.assertTrue(MobRules.carriesEnderItem(player), "eye of ender");
            h.succeed();
        });
        // K09: about 80 % of ghast spawns refused next to another ghast, none refused far from everything. The ghast is moved
        // 4000 blocks up so that no player or entity of a concurrent test is in range.
        MiteGameTests.add("rules_ghast_rarity", 20, h -> {
            ServerLevel level = h.getLevel();
            var ghast = h.spawn(EntityTypes.GHAST, 3, 2, 3);
            ghast.setPos(ghast.position().add(0, 4000, 0));
            Vec3 near = ghast.position().add(10, 0, 0), far = near.add(0, 1000, 0);
            RandomSource random = RandomSource.create(42);
            int refused = 0;
            for (int i = 0; i < 200; i++) if (MobRules.ghastRefused(level, near, random)) refused++;
            h.assertTrue(refused > 130 && refused < 190, "refused " + refused + " / 200 near a ghast");
            for (int i = 0; i < 50; i++) h.assertFalse(MobRules.ghastRefused(level, far, random), "refused far from any ghast");
            ghast.discard();
            h.succeed();
        });
        // K10: a magma cube shrugs off a sword, a pickaxe hurts it.
        MiteGameTests.add("rules_magma_cube_tools", 20, h -> {
            h.assertTrue(hit(h, EntityTypes.MAGMA_CUBE, Items.IRON_SWORD, 1, 3, 3) == 0, "sword hurts a magma cube");
            h.assertTrue(hit(h, EntityTypes.MAGMA_CUBE, Items.IRON_PICKAXE, 5, 3, 3) > 0, "pickaxe does not hurt a magma cube");
            h.succeed();
        });
        // K11: skeletons are immune to skeleton arrows only.
        MiteGameTests.add("rules_skeleton_arrows", 20, h -> {
            ServerLevel level = h.getLevel();
            Skeleton target = h.spawn(EntityTypes.SKELETON, 1, 1, 1), shooter = h.spawn(EntityTypes.SKELETON, 5, 1, 5);
            target.hurtServer(level, level.damageSources().arrow(new Arrow(level, shooter, new ItemStack(Items.ARROW), null), shooter), 3);
            h.assertValueEqual(target.getHealth(), target.getMaxHealth(), "skeleton hurt by a skeleton arrow");
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            target.hurtServer(level, level.damageSources().arrow(new Arrow(level, player, new ItemStack(Items.ARROW), null), player), 3);
            h.assertTrue(target.getHealth() < target.getMaxHealth(), "skeleton immune to a player arrow");
            h.succeed();
        });
        // K11: a villager killed by an armed zombie stays dead, an unarmed zombie still converts it.
        MiteGameTests.add("rules_villager_weapon_kill", 20, h -> {
            Villager villager = h.spawn(EntityTypes.VILLAGER, 3, 1, 3);
            Zombie armed = h.spawn(EntityTypes.ZOMBIE, 1, 1, 1), bare = h.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
            armed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            bare.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            villager.setLastHurtByMob(armed);
            h.assertFalse(EventHooks.canLivingConvert(villager, EntityTypes.ZOMBIE_VILLAGER, t -> {}), "converted after an armed kill");
            villager.setLastHurtByMob(bare);
            h.assertTrue(EventHooks.canLivingConvert(villager, EntityTypes.ZOMBIE_VILLAGER, t -> {}), "not converted after a bare-handed kill");
            h.succeed();
        });
        // EF-LUN-02: blood moon surface spawns of the Ghoul, Wight (frozen biomes), Shadow (deserts) and Revenant (anywhere).
        MiteGameTests.add("rules_blood_moon_spawns", 20, h -> {
            var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            var plains = biomes.getOrThrow(Biomes.PLAINS);
            var snowy = biomes.getOrThrow(Biomes.SNOWY_PLAINS);
            var desert = biomes.getOrThrow(Biomes.DESERT);
            h.assertTrue(Lunar.bloodMoonSpawn(MobContent.GHOUL.get(), plains, true), "ghoul on the surface");
            h.assertFalse(Lunar.bloodMoonSpawn(MobContent.GHOUL.get(), plains, false), "ghoul under a roof");
            h.assertFalse(Lunar.bloodMoonSpawn(MobContent.WIGHT.get(), plains, true), "wight in the plains");
            h.assertTrue(Lunar.bloodMoonSpawn(MobContent.WIGHT.get(), snowy, true), "wight in the snowy plains");
            h.assertFalse(Lunar.bloodMoonSpawn(MobContent.SHADOW.get(), snowy, true), "shadow in the snowy plains");
            h.assertTrue(Lunar.bloodMoonSpawn(MobContent.SHADOW.get(), desert, true), "shadow in the desert");
            h.assertTrue(Lunar.bloodMoonSpawn(MobContent.REVENANT.get(), plains, false), "revenant anywhere");
            h.assertFalse(Lunar.bloodMoonSpawn(EntityTypes.ZOMBIE, plains, true), "zombie listed");
            BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
            LunarTests.onDay(h, 32, () -> h.assertTrue(Lunar.bloodMoonSpawn(h.getLevel(), MobContent.REVENANT.get(), pos), "revenant on a blood moon"));
            LunarTests.onDay(h, 31, () -> h.assertFalse(Lunar.bloodMoonSpawn(h.getLevel(), MobContent.REVENANT.get(), pos), "revenant on a normal night"));
            h.succeed();
        });
        // EF-IA-01 / EF-LUN-02: frenzied zombies dig blocks below harvest level 2 (log) but not stone nor the clay oven.
        MiteGameTests.add("rules_frenzy_digging", 20, h -> {
            DigThroughGoal dig = AiContent.digGoal(h.spawn(EntityTypes.ZOMBIE, 3, 1, 3));
            h.assertFalse(dig.canDig(Blocks.OAK_LOG.defaultBlockState()), "log dug without frenzy");
            LunarTests.onDay(h, 32, () -> {
                h.assertTrue(dig.canDig(Blocks.OAK_LOG.defaultBlockState()), "log not dug in frenzy");
                h.assertFalse(dig.canDig(Blocks.STONE.defaultBlockState()), "stone dug in frenzy");
                h.assertFalse(dig.canDig(Metallurgy.CLAY_FURNACE.get().defaultBlockState()), "clay oven dug in frenzy");
                h.assertFalse(dig.canDig(Blocks.AIR.defaultBlockState()), "air dug in frenzy");
            });
            h.succeed();
        });
        // EF-LUN-02: frenzied skeletons shoot 33 % faster (bow interval x 0.75), back to vanilla afterwards.
        MiteGameTests.add("rules_frenzy_skeleton_shots", 20, h -> {
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 3, 1, 3);
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            int base = bowInterval(skeleton);
            LunarTests.onDay(h, 32, () -> Lunar.updateFrenzy(skeleton));
            h.assertValueEqual(bowInterval(skeleton), Math.round(base * 0.75f), "frenzied bow interval");
            LunarTests.onDay(h, 33, () -> Lunar.updateFrenzy(skeleton));
            h.assertValueEqual(bowInterval(skeleton), base, "bow interval after the blood moon");
            h.succeed();
        });
        // EF-IA-03: a blaze fireball aimed at a target walking sideways is led toward its motion.
        MiteGameTests.add("rules_fireball_lead", 20, h -> {
            Blaze blaze = h.spawn(EntityTypes.BLAZE, 1, 1, 3);
            Zombie target = h.spawn(EntityTypes.ZOMBIE, 5, 1, 3);
            blaze.setTarget(target);
            target.setDeltaMovement(0, 0, 0.3);
            SmallFireball fireball = new SmallFireball(h.getLevel(), blaze, target.position().subtract(blaze.position()));
            double before = fireball.getDeltaMovement().z;
            VanillaMobRules.aim(fireball);
            h.assertTrue(fireball.getDeltaMovement().z > before + 1e-3, "fireball not led: " + fireball.getDeltaMovement());
            h.succeed();
        });
    }

    /** The health a {@code type} spawned at (x, 1, z) loses to {@code dmg} from a survival player holding {@code weapon} (magma cubes at size 4). */
    static float hit(GameTestHelper h, EntityType<? extends LivingEntity> type, Item weapon, int x, int z, float dmg) {
        LivingEntity mob = h.spawn(type, x, 1, z);
        if (mob instanceof MagmaCube cube) cube.setSize(4, true);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        float before = mob.getHealth();
        mob.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(player), dmg);
        return before - mob.getHealth();
    }

    private static int bowInterval(Skeleton skeleton) {
        for (WrappedGoal w : skeleton.goalSelector.getAvailableGoals()) if (w.getGoal() instanceof RangedBowAttackGoal<?> bow) return bow.attackIntervalMin;
        throw new IllegalStateException("no bow goal");
    }
}
