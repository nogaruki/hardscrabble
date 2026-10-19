package dev.mite.entity.ai;

import dev.mite.combat.Archery;
import dev.mite.entity.mob.MobContent;
import dev.mite.gametest.MiteGameTests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/** GameTests of the audit 2 lot H on vanilla mobs: skeletons, endermen, silverfish, iron golems, zombie villagers, spawning rules (K-1..K-7). */
public final class VanillaMobBehaviourTests {
    private VanillaMobBehaviourTests() {}

    public static void register() {
        // l.139: by day a club skeleton chasing a target ignores the sun; an archer, or an idle skeleton, still shuns it.
        MiteGameTests.add("rules_skeleton_melee_sun", 20, h -> {
            // No AI: the armed skeleton must not shoot into neighbouring tests. The weather-dependent half of the goal is vanilla.
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 1, 1, 1);
            skeleton.setNoAi(true);
            Zombie target = h.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
            target.setNoAi(true);
            h.assertTrue(skeleton.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof MobRules.MeleeSunGoal)
                    && skeleton.goalSelector.getAvailableGoals().stream().noneMatch(w -> w.getGoal().getClass() == net.minecraft.world.entity.ai.goal.RestrictSunGoal.class),
                    "sun goal not replaced");
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            skeleton.setTarget(target);
            h.assertFalse(MobRules.chasingWithMelee(skeleton), "archer walks into the sun");
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.getValue(dev.mite.MiteMod.id("wooden_club"))));
            h.assertTrue(MobRules.chasingWithMelee(skeleton), "club skeleton shuns the sun while chasing");
            skeleton.setTarget(null);
            h.assertFalse(MobRules.chasingWithMelee(skeleton), "idle club skeleton walks into the sun");
            h.succeed();
        });
        // l.529: a burning skeleton sets about half of its arrows alight.
        MiteGameTests.add("rules_skeleton_burning_arrows", 20, h -> {
            ServerLevel level = h.getLevel();
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 1, 1, 1);
            skeleton.setNoAi(true);
            skeleton.igniteForSeconds(30);
            int burning = 0;
            for (int i = 0; i < 100; i++) {
                Arrow arrow = new Arrow(level, skeleton, new ItemStack(Items.ARROW), null);
                VanillaMobRules.aim(arrow);
                if (arrow.isOnFire()) burning++;
            }
            h.assertTrue(burning > 25 && burning < 75, "burning arrows " + burning + " / 100");
            h.succeed();
        });
        // l.839: archers rank bows (wood < ancient metal < mithril) and swap for a better one lying near.
        MiteGameTests.add("rules_skeleton_better_bow", 200, h -> {
            h.assertTrue(MobRules.bowRank(new ItemStack(Items.BOW)) < MobRules.bowRank(new ItemStack(Archery.ANCIENT_METAL_BOW.get()))
                    && MobRules.bowRank(new ItemStack(Archery.ANCIENT_METAL_BOW.get())) < MobRules.bowRank(new ItemStack(Archery.MITHRIL_BOW.get())), "bow ranks");
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 1, 1, 1);
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            skeleton.targetSelector.removeAllGoals(g -> true); // never shoots into neighbouring tests
            h.spawnItem(Archery.MITHRIL_BOW.get(), 4.5f, 1, 4.5f);
            h.succeedWhen(() -> h.assertTrue(skeleton.getMainHandItem().is(Archery.MITHRIL_BOW.get()), "skeleton holds " + skeleton.getMainHandItem()));
        });
        // l.591 / l.832: skeletons and ghouls wander slowly; skeletons drop about half the arrows.
        MiteGameTests.add("rules_skeleton_wander_arrows", 20, h -> {
            for (var mob : List.of(h.spawn(EntityTypes.SKELETON, 1, 1, 1), h.spawn(MobContent.GHOUL.get(), 3, 1, 3))) {
                mob.setNoAi(true);
                var strolls = mob.goalSelector.getAvailableGoals().stream().map(WrappedGoal::getGoal).filter(g -> g instanceof RandomStrollGoal).toList();
                h.assertTrue(strolls.size() == 1 && strolls.getFirst() instanceof MobRules.SlowStrollGoal, "fast stroll on " + mob.getType());
            }
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW, 64)),
                    new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.BONE, 2))));
            MobRules.fewerArrows(drops, RandomSource.create(3));
            int arrows = drops.stream().filter(d -> d.getItem().is(Items.ARROW)).mapToInt(d -> d.getItem().getCount()).sum();
            h.assertTrue(arrows > 16 && arrows < 48, "arrows kept " + arrows + " / 64");
            h.assertTrue(drops.stream().anyMatch(d -> d.getItem().is(Items.BONE) && d.getItem().getCount() == 2), "bones touched");
            h.succeed();
        });
        // l.590: an enderman stacks ender pearls in its hand and drops them for sure.
        MiteGameTests.add("rules_enderman_takes_pearls", 20, h -> {
            Enderman enderman = h.spawn(EntityTypes.ENDERMAN, 3, 1, 3);
            ItemEntity pearls = h.spawnItem(Items.ENDER_PEARL, 3.5f, 1, 3.5f);
            pearls.getItem().setCount(3);
            h.assertTrue(MobRules.endermanWants(enderman, pearls.getItem()) && !MobRules.endermanWants(enderman, new ItemStack(Items.DIRT)), "wants");
            MobRules.endermanTakes(enderman, pearls);
            h.assertTrue(enderman.getMainHandItem().is(Items.ENDER_PEARL) && enderman.getMainHandItem().getCount() == 3 && pearls.isRemoved(), "pearls taken");
            h.assertFalse(MobRules.endermanWants(enderman, new ItemStack(Items.ENDER_EYE)), "eye taken into a pearl stack");
            h.succeed();
        });
        // l.302: silverfish hunt animals.
        MiteGameTests.add("rules_silverfish_prey", 200, h -> {
            Silverfish silverfish = h.spawn(EntityTypes.SILVERFISH, 1, 1, 1);
            silverfish.goalSelector.removeAllGoals(g -> g instanceof RandomStrollGoal); // would hide in the stone floor
            h.spawn(EntityTypes.COW, 4, 1, 4);
            h.succeedWhen(() -> h.assertTrue(silverfish.getTarget() instanceof Animal, "silverfish target " + silverfish.getTarget()));
        });
        // l.376-378: an infested block squeaks when mined, an emergence may cave in loose blocks, silverfish ignore falling gravel.
        MiteGameTests.add("rules_silverfish_cave_in", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 1, 1, Blocks.INFESTED_STONE);
            h.assertTrue(SilverfishRules.warn(level, h.absolutePos(new BlockPos(1, 1, 1))), "infested stone silent");
            h.assertFalse(SilverfishRules.warn(level, h.absolutePos(new BlockPos(1, 0, 1))), "plain stone squeaks");
            BlockPos gravel = new BlockPos(4, 2, 3);
            boolean fell = false;
            for (int i = 0; i < 20 && !fell; i++) {
                h.setBlock(gravel, Blocks.GRAVEL);
                SilverfishRules.caveIn(level, h.absolutePos(new BlockPos(3, 1, 3)), level.getRandom());
                fell = h.getBlockState(gravel).isAir();
            }
            h.assertTrue(fell, "no cave-in");
            Silverfish fish = h.spawn(EntityTypes.SILVERFISH, 5, 1, 1);
            h.setBlock(5, 1, 1, Blocks.GRAVEL);
            float health = fish.getHealth();
            fish.hurtServer(level, level.damageSources().inWall(), 2);
            h.assertValueEqual(fish.getHealth(), health, "silverfish smothered by gravel");
            h.succeed();
        });
        // l.433 / l.434: ocelots hunt bats, snow golems swim.
        MiteGameTests.add("rules_ocelot_snow_golem", 20, h -> {
            var ocelot = h.spawn(EntityTypes.OCELOT, 1, 1, 1);
            // Vanilla: chicken and baby turtle goals; MITE adds the bat goal.
            h.assertValueEqual(ocelot.targetSelector.getAvailableGoals().size(), 3, "ocelot target goals");
            var snowman = h.spawn(EntityTypes.SNOW_GOLEM, 5, 1, 5);
            h.assertTrue(snowman.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof net.minecraft.world.entity.ai.goal.FloatGoal), "snow golem float goal");
            h.succeed();
        });
        // l.280: the iron golem reaches 1 block farther than the mob default.
        MiteGameTests.add("rules_iron_golem_reach", 20, h -> {
            IronGolem golem = h.spawn(EntityTypes.IRON_GOLEM, 1.5f, 1, 3.5f);
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 4.0f, 1, 3.5f);
            h.assertFalse(golem.isWithinMeleeAttackRange(zombie), "vanilla reach already enough");
            h.assertTrue(MobRules.reaches(golem, zombie, MobRules.IRON_GOLEM_EXTRA_REACH), "extended reach too short");
            h.assertTrue(golem.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof MobRules.ReachMeleeAttackGoal), "reach goal");
            h.succeed();
        });
        // l.241: an enchanted golden apple cures a weakened zombie villager at once.
        MiteGameTests.add("rules_enchanted_apple_cure", 40, h -> {
            ZombieVillager zombie = h.spawn(EntityTypes.ZOMBIE_VILLAGER, 3, 1, 3);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            MobRules.onEntityInteract(new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, zombie, zombie.position()));
            h.assertTrue(zombie.isConverting() && player.getMainHandItem().isEmpty(), "cure not started");
            h.succeedWhen(() -> h.assertTrue(zombie.isRemoved() && !h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                    zombie.getBoundingBox().inflate(2)).isEmpty(), "not converted"));
        });
        // K-1 / l.696 / l.703: monsters spawn on bottom slabs, stairs, lily pads and in shallow water; animals keep vanilla floors.
        MiteGameTests.add("rules_spawn_surfaces", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos slab = h.absolutePos(new BlockPos(1, 1, 1));
            h.setBlock(1, 1, 1, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            h.assertTrue(level.getBlockState(slab).isValidSpawn(level, slab, EntityTypes.ZOMBIE), "zombie refused on a slab");
            h.assertFalse(level.getBlockState(slab).isValidSpawn(level, slab, EntityTypes.COW), "cow allowed on a slab");
            h.assertTrue(SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, level, slab.above()), "zombie placement refused above a slab");
            h.setBlock(3, 1, 1, Blocks.OAK_STAIRS);
            BlockPos stairs = h.absolutePos(new BlockPos(3, 1, 1));
            h.assertTrue(level.getBlockState(stairs).isValidSpawn(level, stairs, EntityTypes.SKELETON), "skeleton refused on stairs");
            h.assertTrue(Blocks.LILY_PAD.defaultBlockState().isValidSpawn(level, slab, EntityTypes.SPIDER), "spider refused on a lily pad");
            h.setBlock(5, 1, 5, Blocks.WATER);
            BlockPos water = h.absolutePos(new BlockPos(5, 1, 5));
            h.assertTrue(SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, level, water), "zombie refused in shallow water");
            h.setBlock(5, 2, 5, Blocks.WATER);
            h.assertFalse(SpawnPlacements.isSpawnPositionOk(EntityTypes.ZOMBIE, level, water), "zombie allowed in deep water");
            h.succeed();
        });
        // K-2 / l.301 / K-6: spider weight 80, silverfish in any biome and more in mountains, villagers flee elementals and cubes.
        MiteGameTests.add("rules_spawn_weights", 20, h -> {
            var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            var plains = monsters(biomes.getOrThrow(Biomes.PLAINS).value());
            h.assertTrue(plains.stream().anyMatch(w -> w.value().type() == EntityTypes.SPIDER && w.weight() == 80), "plains spider weight: " + plains);
            h.assertFalse(plains.stream().anyMatch(w -> w.value().type() == EntityTypes.SPIDER && w.weight() == 100), "vanilla spider entry left");
            h.assertTrue(plains.stream().anyMatch(w -> w.value().type() == EntityTypes.SILVERFISH), "no silverfish in the plains");
            int hills = monsters(biomes.getOrThrow(Biomes.WINDSWEPT_HILLS).value()).stream()
                    .filter(w -> w.value().type() == EntityTypes.SILVERFISH).mapToInt(w -> w.weight()).sum();
            h.assertTrue(hills > 5, "mountain silverfish weight " + hills);
            for (var type : List.of(MobContent.EARTH_ELEMENTAL.get(), MobContent.JELLY.get(), EntityTypes.SLIME))
                h.assertTrue(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).getData(NeoForgeDataMaps.ACCEPTABLE_VILLAGER_DISTANCES) != null, "villagers ignore " + type);
            h.succeed();
        });
        // l.841: an enchanted spawned weapon keeps its enchantments about 1 time in 4.
        MiteGameTests.add("rules_spawn_gear_disenchanted", 20, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 3, 1, 3);
            RandomSource random = RandomSource.create(5);
            var sharpness = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            int kept = 0;
            for (int i = 0; i < 200; i++) {
                ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
                sword.enchant(sharpness, 1);
                zombie.setItemSlot(EquipmentSlot.MAINHAND, sword);
                MobRules.disenchantGear(zombie, random);
                if (zombie.getMainHandItem().isEnchanted()) kept++;
            }
            h.assertTrue(kept > 25 && kept < 80, "enchanted kept " + kept + " / 200");
            h.succeed();
        });
        // l.841 is about gear rolled at spawn: a zombie drowning into a drowned keeps the enchanted sword it holds (8 tries:
        // without the CombatContent.once tag, all 8 keep it only with odds 0.25^8).
        MiteGameTests.add("rules_conversion_keeps_enchantments", 20, h -> {
            var sharpness = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            for (int i = 0; i < 8; i++) {
                Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 1 + i % 5, 1, 1 + i / 5 * 2);
                ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
                sword.enchant(sharpness, 1);
                zombie.setItemSlot(EquipmentSlot.MAINHAND, sword);
                Drowned drowned = zombie.convertTo(EntityTypes.DROWNED, ConversionParams.single(zombie, true, true), d -> {});
                h.assertTrue(drowned != null && drowned.isAddedToLevel(), "converted");
                h.assertTrue(drowned.getMainHandItem().isEnchanted(), "converted drowned lost the enchantments of " + drowned.getMainHandItem());
                drowned.discard();
            }
            h.succeed();
        });
        // l.703: the spawn listener lets a natural zombie spawn in one block of water in the dark (vanilla refuses any liquid),
        // not in two.
        MiteGameTests.add("rules_shallow_water_spawn_event", 40, h -> {
            for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
                h.setBlock(x, 3, z, Blocks.STONE);
                for (int y = 1; y <= 2; y++) if (x != 3 || z != 3) h.setBlock(x, y, z, Blocks.STONE);
            }
            h.setBlock(3, 1, 3, Blocks.WATER);
            Zombie zombie = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.NATURAL);
            zombie.setPos(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
            h.runAfterDelay(10, () -> {
                h.assertValueEqual(NeoForge.EVENT_BUS.post(new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), EntitySpawnReason.NATURAL, null)).getResult(),
                        MobSpawnEvent.PositionCheck.Result.SUCCEED, "dark shallow water");
                h.setBlock(3, 2, 3, Blocks.WATER);
                h.assertFalse(EventHooks.checkSpawnPosition(zombie, h.getLevel(), EntitySpawnReason.NATURAL), "zombie spawns in deep water");
                h.succeed();
            });
        });
        // l.703 (R122): thin snow is a monster floor over a block monsters spawn on (grass), not over glass.
        MiteGameTests.add("rules_spawn_thin_snow", 20, h -> {
            ServerLevel level = h.getLevel();
            h.setBlock(1, 1, 3, Blocks.GRASS_BLOCK);
            h.setBlock(1, 2, 3, Blocks.SNOW);
            h.setBlock(5, 1, 3, Blocks.GLASS);
            h.setBlock(5, 2, 3, Blocks.SNOW);
            BlockPos onGrass = h.absolutePos(new BlockPos(1, 2, 3)), onGlass = h.absolutePos(new BlockPos(5, 2, 3));
            h.assertTrue(SpawnSurfaces.extraFloor(level.getBlockState(onGrass), level, onGrass, EntityTypes.ZOMBIE), "thin snow on grass refused");
            h.assertFalse(SpawnSurfaces.extraFloor(level.getBlockState(onGlass), level, onGlass, EntityTypes.ZOMBIE), "thin snow on glass allowed");
            h.succeed();
        });
    }

    /** The monster spawn entries of a biome, biome modifiers applied (26.3 keeps them in the natural_mob_spawns attribute). */
    private static List<Weighted<MobSpawnSettings.SpawnerData>> monsters(Biome biome) {
        var entry = biome.getModifiedEnvironmentAttributes().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        return entry == null ? List.of() : ((MobSpawnSettings) entry.argument()).getMobsToSpawn(MobCategory.MONSTER).unwrap();
    }
}
