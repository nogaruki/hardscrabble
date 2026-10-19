package dev.mite.entity.ai;

import dev.mite.core.Formulas;
import dev.mite.combat.CombatContent;
import dev.mite.core.Material;
import dev.mite.entity.mob.BoneLord;
import dev.mite.config.MiteConfig;
import dev.mite.entity.mob.DireWolf;
import dev.mite.entity.mob.Ghoul;
import dev.mite.entity.mob.MobContent;
import dev.mite.entity.mob.Revenant;
import dev.mite.lunar.Lunar;
import dev.mite.lunar.LunarTests;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.mixin.TargetGoalAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.event.EventHooks;
import dev.mite.gametest.MiteGameTests;
import dev.mite.harvest.HarvestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** GameTests for EF-IA-01, EF-IA-02, EF-IA-04/05/06/07/08, EF-MOB-09/20 (audit lot 9) and EF-CBT-06. */
public final class AiTests {
    private AiTests() {}

    public static void register() {
        // EF-IA-01 CA-1: walled in, the zombie digs the dirt in front of its feet toward the player, never the stone at its head.
        MiteGameTests.add("ai_squid_hunts_and_paralyses", 20, h -> {
            // R151 / l.112: deep water only, day and night, peaceful on a blue moon night; the bump paralyses.
            for (int x = 1; x <= 5; x++) for (int y = 1; y <= 2; y++) h.setBlock(x, y, 1, Blocks.WATER);
            h.setBlock(1, 1, 3, Blocks.WATER);
            net.minecraft.world.entity.animal.squid.Squid squid = h.spawn(EntityTypes.SQUID, 5, 1, 1);
            Player swimmer = player(h, 1, 1), wader = player(h, 1, 3);
            swimmer.setPos(swimmer.getX(), swimmer.getY() + 1, swimmer.getZ());
            LunarTests.onDay(h, 31, () -> {
                h.assertTrue(SquidHuntGoal.hunts(squid, swimmer), "hunts a swimmer in deep water by day");
                h.assertFalse(SquidHuntGoal.hunts(squid, wader), "shallow water");
            });
            LunarTests.onDay(h, 31, 18000, () -> h.assertTrue(SquidHuntGoal.hunts(squid, swimmer), "hunts at night"));
            LunarTests.onDay(h, 128, 18000, () -> h.assertFalse(SquidHuntGoal.hunts(squid, swimmer), "peaceful on a blue moon night"));
            SquidHuntGoal.paralyse(wader);
            h.assertTrue(wader.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS).getAmplifier() == dev.mite.entity.mob.Ghoul.PARALYSIS_AMPLIFIER, "bump paralysis");
            h.succeed();
        });
        // The dirt sets a 150-tick cooldown: the stone is checked after it, when a second dig could have happened.
        MiteGameTests.add("ai_zombie_digs_diggable_only", 260, h -> {
            requireDigging(h);
            Zombie zombie = cell(h, EntityTypes.ZOMBIE, Blocks.DIRT, Blocks.STONE);
            h.assertFalse(AiContent.digGoal(zombie).canDig(Blocks.STONE.defaultBlockState()), "unarmed zombie may dig stone");
            h.runAfterDelay(200, () -> {
                h.assertTrue(zombie.isAlive(), "zombie alive");
                h.assertBlockNotPresent(Blocks.DIRT, 3, 1, 2);
                h.assertBlockPresent(Blocks.STONE, 3, 2, 2);
                h.succeed();
            });
        });
        // EF-IA-01 (M18): a copper pickaxe in hand adds stone to the diggable blocks.
        MiteGameTests.add("ai_zombie_pickaxe_digs_stone", 60, h -> {
            requireDigging(h);
            Zombie zombie = cell(h, EntityTypes.ZOMBIE, Blocks.STONE, Blocks.STONE);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COPPER_PICKAXE));
            h.succeedWhen(() -> h.assertBlockNotPresent(Blocks.STONE, 3, 1, 2));
        });
        // EF-IA-01 CA-1 (M19): a zombie that acquired a chicken by itself keeps it more than 150 ticks once walled in.
        MiteGameTests.add("ai_digger_keeps_unseen_target", 300, h -> {
            Zombie zombie = cell(h, EntityTypes.ZOMBIE, Blocks.AIR, Blocks.AIR);
            zombie.setTarget(null);
            Chicken chicken = h.spawn(EntityTypes.CHICKEN, 3, 1, 5);
            chicken.setNoAi(true);
            long[] walled = {-1};
            h.onEachTick(() -> {
                if (walled[0] < 0 && zombie.getTarget() == chicken) {
                    h.setBlock(3, 1, 2, Blocks.STONE);
                    h.setBlock(3, 2, 2, Blocks.STONE);
                    walled[0] = h.getTick();
                } else if (walled[0] >= 0 && h.getTick() - walled[0] > 200) {
                    h.assertTrue(zombie.getTarget() == chicken, "target kept 200 ticks unseen");
                    h.succeed();
                }
            });
        });
        // EF-IA-07 / EF-LUN-02 (dom-K-03): the drowned is not in hardscrabble:frenzy_undead, so no blood moon speed.
        MiteGameTests.add("ai_drowned_no_frenzy", 20, h -> {
            Drowned drowned = h.spawn(EntityTypes.DROWNED, 2, 1, 2);
            double base = drowned.getAttributeValue(Attributes.MOVEMENT_SPEED);
            LunarTests.onDay(h, 32, () -> Lunar.updateFrenzy(drowned));
            h.assertValueEqual(drowned.getAttributeValue(Attributes.MOVEMENT_SPEED), base, "drowned speed on a blood moon");
            h.succeed();
        });
        // EF-IA-04 / EF-MOB-09 (dom-J-10, dom-K-05): cave and demon spiders target in full light.
        MiteGameTests.add("ai_spiders_hostile_by_day", 100, h -> {
            IronGolem golem = h.spawn(EntityTypes.IRON_GOLEM, 3, 1, 1);
            golem.setNoAi(true);
            h.setBlock(1, 1, 4, Blocks.LIGHT);
            h.setBlock(5, 1, 4, Blocks.LIGHT);
            Spider cave = h.spawn(EntityTypes.CAVE_SPIDER, 1, 1, 4), demon = h.spawn(MobContent.DEMON_SPIDER.get(), 5, 1, 4);
            h.succeedWhen(() -> {
                for (Spider s : List.of(cave, demon)) h.assertTrue(s.getTarget() == golem, s.getType().toShortString() + " targets in full light");
            });
        });
        // EF-IA-08 (dom-K-09): summoned witch wolves cannot be tamed, a plain wolf can.
        MiteGameTests.add("ai_witch_wolves_untamable", 20, h -> {
            Witch witch = h.spawn(EntityTypes.WITCH, 3, 1, 3);
            ServerPlayer p = MetabolismTests.player(h);
            witch.setTarget(p);
            // Two plain wolves next to the witch (pets of a player) do not count as her minions.
            Wolf plain = h.spawn(EntityTypes.WOLF, 1, 1, 1);
            h.spawn(EntityTypes.WOLF, 5, 1, 1);
            new SummonGoal(witch, () -> EntityTypes.WOLF, 2, 0).start();
            List<Wolf> wolves = h.getLevel().getEntities(EntityTypes.WOLF, witch.getBoundingBox().inflate(4), SummonGoal::summoned);
            h.assertValueEqual(wolves.size(), 2, "summoned wolves");
            for (Wolf w : wolves) h.assertTrue(EventHooks.onAnimalTame(w, p), "witch wolf tamed");
            h.assertFalse(EventHooks.onAnimalTame(plain, p), "plain wolf untamable");
            h.succeed();
        });
        // EF-MOB-20 (dom-J-06, dom-J-11): blood moon spawns anywhere; persistent once it targets a player; recalls its idle troops only.
        MiteGameTests.add("ai_bone_lord_blood_moon", 20, h -> {
            BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
            LunarTests.onDay(h, 32, () -> {
                for (var type : List.of(MobContent.BONE_LORD.get(), MobContent.VAMPIRE_BAT.get(), MobContent.NIGHTWING.get()))
                    h.assertTrue(Lunar.bloodMoonSpawn(h.getLevel(), type, pos), type.toShortString() + " on a blood moon");
            });
            LunarTests.onDay(h, 31, () -> h.assertFalse(Lunar.bloodMoonSpawn(h.getLevel(), MobContent.BONE_LORD.get(), pos), "bone lord on a normal night"));
            BoneLord lord = h.spawn(MobContent.BONE_LORD.get(), 3, 1, 3);
            Skeleton troop = h.spawn(EntityTypes.SKELETON, 1, 1, 1), wild = h.spawn(EntityTypes.SKELETON, 5, 1, 5);
            troop.addTag(SummonGoal.SUMMONED);
            lord.recall();
            h.assertTrue(troop.isRemoved() && !wild.isRemoved(), "troop recalled, wild skeleton kept");
            lord.setTarget(MetabolismTests.player(h));
            h.assertTrue(lord.isPersistenceRequired(), "persistent after targeting a player");
            h.succeed();
        });
        // EF-MOB-06 (M16): a Dire Wolf tamed with bones has 24 health (the tamed 12 of EF-IA-04 is for vanilla wolves).
        MiteGameTests.add("ai_dire_wolf_tamed_health", 20, h -> {
            DireWolf wolf = h.spawn(MobContent.DIRE_WOLF.get(), 3, 1, 3);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE, 64));
            for (int i = 0; i < 64 && !wolf.isTame(); i++) wolf.mobInteract(p, InteractionHand.MAIN_HAND);
            h.assertTrue(wolf.isTame(), "dire wolf tamed");
            h.assertValueEqual(wolf.getMaxHealth(), (float) DireWolf.TAME_HEALTH, "tamed max health");
            h.assertValueEqual(wolf.getHealth(), (float) DireWolf.TAME_HEALTH, "tamed health");
            h.succeed();
        });
        // EF-IA-05 (M17): MITE zombies get no reinforcements and rusted gear (the Revenant's chainmail).
        MiteGameTests.add("ai_mite_zombie_gear", 20, h -> {
            Revenant revenant = MobContent.REVENANT.get().spawn(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), EntitySpawnReason.NATURAL);
            h.assertValueEqual(revenant.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE), 0.0, "reinforcement chance");
            h.assertTrue(revenant.getItemBySlot(EquipmentSlot.HEAD).is(CombatContent.armorItem(Material.RUSTED_IRON, ArmorType.HELMET, true)), "rusted chainmail");
            h.succeed();
        });
        // EF-IA-01: 300 x hardness ticks between two blocks (dirt 0.5 -> 150 ticks).
        MiteGameTests.add("ai_zombie_dig_cooldown", 300, h -> {
            requireDigging(h);
            cell(h, EntityTypes.ZOMBIE, Blocks.DIRT, Blocks.DIRT);
            h.runAfterDelay(100, () -> h.assertTrue(dirt(h, 1) + dirt(h, 2) == 1, "one block dug, the other waits for the cooldown"));
            h.runAfterDelay(260, () -> {
                h.assertTrue(dirt(h, 1) + dirt(h, 2) == 0, "both blocks dug after the cooldown");
                h.succeed();
            });
        });
        // EF-IA-07 CA-1: the drowned is not a digger, the husk is.
        MiteGameTests.add("ai_digger_tag", 20, h -> {
            Drowned drowned = h.spawn(EntityTypes.DROWNED, 2, 1, 2);
            h.assertTrue(AiContent.digGoal(drowned) == null, "drowned does not dig");
            h.assertTrue(AiContent.digGoal(h.spawn(EntityTypes.HUSK, 4, 1, 4)) != null, "husk digs");
            // Q-3 / A-4: mobs.zombie_digging=false switches the digger goal off; the Earth Elemental keeps its own.
            DigThroughGoal zombie = AiContent.digGoal(h.spawn(EntityTypes.ZOMBIE, 2, 1, 4));
            boolean off = MiteGameTests.withConfig(MiteConfig.ZOMBIE_DIGGING, false, () -> !zombie.enabled());
            boolean elemental = MiteGameTests.withConfig(MiteConfig.ZOMBIE_DIGGING, false, () -> new DigThroughGoal(drowned, s -> true, 1).enabled());
            boolean on = MiteGameTests.withConfig(MiteConfig.ZOMBIE_DIGGING, true, zombie::enabled);
            h.assertTrue(off && elemental, "zombie_digging=false: zombie off " + off + ", own digger on " + elemental);
            h.assertTrue(on, "zombie digs with zombie_digging on");
            h.succeed();
        });
        // EF-IA-02: follow range 40; a zombie that sees the player does not despawn, a wall lifts that.
        MiteGameTests.add("ai_no_despawn_when_seen", 40, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 1, 1, 3);
            h.assertValueEqual(zombie.getAttributeValue(Attributes.FOLLOW_RANGE), AiContent.FOLLOW_RANGE, "zombie follow range");
            Player player = player(h, 5, 3);
            h.runAfterDelay(2, () -> {
                h.assertTrue(AiContent.keepsAlive(zombie, player), "seen zombie is kept");
                for (int y = 1; y <= 3; y++) for (int z = 0; z <= 6; z++) h.setBlock(3, y, z, Blocks.STONE);
            });
            h.runAfterDelay(4, () -> {
                h.assertTrue(!AiContent.keepsAlive(zombie, player), "hidden zombie may despawn");
                h.succeed();
            });
        });
        // EF-IA-06 / EF-MOB CA-3: a ghoul (R196 y <= 56) is refused above y 48, allowed at 48, and anywhere outside the Overworld.
        MiteGameTests.add("ai_spawn_depth", 20, h -> {
            // The loaded data map (datagen output, RegisteredCondition, registered map type), not the Java constant.
            AiContent.SpawnDepth ghoul = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(MobContent.GHOUL.get()).getData(AiContent.SPAWN_DEPTH);
            h.assertTrue(ghoul != null && ghoul.maxY() == 56, "loaded ghoul spawn depth " + ghoul);
            h.assertValueEqual(Formulas.mapDepth(ghoul.maxY()), 48, "converted ghoul threshold");
            h.assertTrue(!AiContent.depthAllows(ghoul, Level.OVERWORLD, 49), "ghoul refused at y 49");
            h.assertTrue(AiContent.depthAllows(ghoul, Level.OVERWORLD, 48), "ghoul allowed at y 48");
            h.assertTrue(AiContent.depthAllows(null, Level.OVERWORLD, 200), "unrestricted mob allowed");
            h.assertTrue(AiContent.depthAllows(ghoul, Level.NETHER, 100), "no depth filter in the Nether");
            // The spawn listener itself (AiContent#onPositionCheck): a natural ghoul at y 49 is refused, at y 48 left to vanilla.
            h.assertValueEqual(depthCheck(h, 49), MobSpawnEvent.PositionCheck.Result.FAIL, "natural ghoul at y 49");
            h.assertValueEqual(depthCheck(h, 48), MobSpawnEvent.PositionCheck.Result.DEFAULT, "natural ghoul at y 48");
            h.succeed();
        });
        // EF-CBT-06 CA-1: a silver sword hits a zombie 25 % harder than a copper sword of the same damage (7 -> 8.75).
        MiteGameTests.add("ai_silver_vs_undead", 20, h -> {
            float silver = MobRulesTests.hit(h, EntityTypes.ZOMBIE, sword("silver_sword"), 2, 2, 7);
            float copper = MobRulesTests.hit(h, EntityTypes.ZOMBIE, Items.COPPER_SWORD, 4, 2, 7);
            h.assertTrue(Math.abs(silver / copper - 1.25f) < 1e-3, "silver / copper damage " + silver + " / " + copper);
            h.succeed();
        });
        // EF-IA-02 / EF-IA-08: a summoned wolf far from every player despawns, unless it is persistent (name-tagged, leashed).
        // The wolf (created, not spawned: GameTestHelper#spawn makes mobs persistent) stands 4000 blocks up, beyond 128 blocks
        // of any player; a real player makes sure one exists.
        MiteGameTests.add("ai_summoned_despawn_persistence", 20, h -> {
            MiteGameTests.levelPlayer(h);
            Wolf wolf = EntityTypes.WOLF.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
            wolf.addTag(SummonGoal.SUMMONED);
            wolf.setPos(h.absoluteVec(new Vec3(3.5, 4000, 3.5)));
            h.assertValueEqual(despawn(h, wolf), MobDespawnEvent.Result.ALLOW, "far summoned wolf");
            wolf.setPersistenceRequired();
            h.assertValueEqual(despawn(h, wolf), MobDespawnEvent.Result.DEFAULT, "name-tagged summoned wolf");
            h.succeed();
        });
        // EF-MOB-15 / EF-IA-01: the Earth Elemental (a hardscrabble:digger with its own dig goal) keeps unseen targets as long as the
        // zombies, and gets none of the zombie meat and animal goals.
        MiteGameTests.add("ai_earth_elemental_digger_goals", 20, h -> {
            var earth = h.spawn(MobContent.EARTH_ELEMENTAL.get(), 3, 1, 3);
            for (WrappedGoal w : earth.targetSelector.getAvailableGoals())
                if (w.getGoal() instanceof TargetGoal t)
                    h.assertValueEqual(((TargetGoalAccessor) t).mite$unseenMemoryTicks(), AiContent.DIGGER_UNSEEN_MEMORY, "unseen memory of " + t);
            h.assertFalse(earth.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof FetchItemGoal), "earth elemental fetches meat");
            h.succeed();
        });
    }

    /** EF-IA-01 tests below rely on {@code mobs.zombie_digging}: fail with a clear message when the run turned it off. */
    private static void requireDigging(GameTestHelper h) {
        h.assertTrue(MiteConfig.ZOMBIE_DIGGING.getAsBoolean(), "mobs.zombie_digging is off in this run");
    }

    /** The result of the natural spawn position check for a ghoul at absolute height {@code y} (not added to the level). */
    private static MobSpawnEvent.PositionCheck.Result depthCheck(GameTestHelper h, int y) {
        Ghoul ghoul = MobContent.GHOUL.get().create(h.getLevel(), EntitySpawnReason.NATURAL);
        Vec3 pos = h.absoluteVec(new Vec3(3.5, 1, 3.5));
        ghoul.setPos(pos.x, y, pos.z);
        return NeoForge.EVENT_BUS.post(new MobSpawnEvent.PositionCheck(ghoul, h.getLevel(), EntitySpawnReason.NATURAL, null)).getResult();
    }

    private static MobDespawnEvent.Result despawn(GameTestHelper h, Mob mob) {
        return NeoForge.EVENT_BUS.post(new MobDespawnEvent(mob, h.getLevel())).getResult();
    }

    /** A roofed 1x1 stone cell at (3,1,1) opening south on {@code feet}/{@code head}, holding a {@code type}, a player at (3,1,5) as target. */
    static <T extends Mob> T cell(GameTestHelper h, EntityType<T> type, Block feet, Block head) {
        for (int y = 1; y <= 2; y++) {
            h.setBlock(2, y, 1, Blocks.STONE);
            h.setBlock(4, y, 1, Blocks.STONE);
            h.setBlock(3, y, 0, Blocks.STONE);
        }
        h.setBlock(3, 3, 1, Blocks.STONE);
        h.setBlock(3, 1, 2, feet);
        h.setBlock(3, 2, 2, head);
        T mob = h.spawn(type, 3, 1, 1);
        mob.setTarget(player(h, 3, 5));
        return mob;
    }

    private static Player player(GameTestHelper h, int x, int z) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = h.absoluteVec(new Vec3(x + 0.5, 1, z + 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static int dirt(GameTestHelper h, int y) {
        return h.getBlockState(new BlockPos(3, y, 2)).is(Blocks.DIRT) ? 1 : 0;
    }

    private static Item sword(String name) {
        return HarvestContent.TOOLS.stream().filter(t -> t.item().getId().getPath().equals(name)).findFirst().orElseThrow().item().get();
    }
}
