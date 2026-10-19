package dev.mite.entity.mob;

import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.player.ProgressionService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** GameTests for EF-MOB-01/02/05/08: attributes and XP (CA-1), Wight immunity (CA-2), depth spawns (CA-3), behaviours, loot. */
public final class MobTests {
    private MobTests() {}

    public static void register() {
        // CA-1: health, damage, speed, follow range and XP (zombie and spider base XP 5).
        MiteGameTests.add("mob_attributes", 20, h -> {
            stats(h, MobContent.GHOUL.get(), 20, 5, 0.28, 40, 10);
            stats(h, MobContent.WIGHT.get(), 20, 5, 0.25, 40, 10);
            stats(h, MobContent.REVENANT.get(), 30, 7, 0.26, 40, 15);
            stats(h, MobContent.WOOD_SPIDER.get(), 6, 1, 0.3, 16, 5);
            h.succeed();
        });
        // EF-MOB-01: a Ghoul hit gives Slowness VI for 50 ticks.
        // Every MITE mob speaks with its own voice (MobSounds): ambient, hurt and death events, routed by the mob classes.
        MiteGameTests.add("mob_voices", 20, h -> {
            for (var type : MobContent.MOBS) for (MobSounds.Kind kind : MobSounds.Kind.values())
                h.assertTrue(MobSounds.get(type.get(), kind, null) != null, "no " + kind + " sound for " + type.getId());
            var ghoul = h.spawnWithNoFreeWill(MobContent.GHOUL.get(), 3, 1, 3);
            h.assertTrue(((MiteZombie) ghoul).getHurtSound(h.getLevel().damageSources().generic()) == MobSounds.holder("ghoul", MobSounds.Kind.HURT).get(), "ghoul hurt sound");
            h.succeed();
        });

        MiteGameTests.add("mob_ghoul_paralysis", 20, h -> {
            Ghoul ghoul = h.spawn(MobContent.GHOUL.get(), 2, 1, 2);
            Villager villager = h.spawn(EntityTypes.VILLAGER, 3, 1, 2);
            h.assertTrue(ghoul.doHurtTarget(h.getLevel(), villager), "ghoul hit");
            MobEffectInstance slow = villager.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() == Ghoul.PARALYSIS_AMPLIFIER && slow.getDuration() == Ghoul.PARALYSIS_TICKS, "paralysis " + slow);
            h.succeed();
        });
        // EF-MOB-02 CA-2: an iron sword does nothing, fire and a silver sword hurt. XP drain, blocked by silver armor.
        MiteGameTests.add("mob_wight_immunity", 20, h -> {
            ServerLevel level = h.getLevel();
            Wight wight = h.spawn(MobContent.WIGHT.get(), 2, 1, 2);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(wight.hurtServer(level, level.damageSources().playerAttack(p), 4), "iron sword hurts the wight");
            h.assertValueEqual(wight.getHealth(), 20f, "wight health after the iron sword");
            h.assertTrue(wight.hurtServer(level, level.damageSources().inFire(), 1), "fire spares the wight");
            float afterFire = wight.getHealth();
            wight.setInvulnerableTime(0);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.getValue(MiteMod.id("silver_sword"))));
            h.assertTrue(wight.hurtServer(level, level.damageSources().playerAttack(p), 4), "silver sword spares the wight");
            h.assertTrue(afterFire < 20 && wight.getHealth() < afterFire, "wight health " + afterFire + " -> " + wight.getHealth());

            p.giveExperiencePoints(750); // level 10: drains max(11 x 10, 20) = 110
            h.assertTrue(Wight.drainXp(p), "drain");
            h.assertValueEqual(ProgressionService.xp(p), 640, "XP after drain");
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(BuiltInRegistries.ITEM.getValue(MiteMod.id("silver_helmet"))));
            h.assertFalse(Wight.drainXp(p), "drain through silver armor");
            h.assertValueEqual(Wight.drainAmount(-5), 20, "minimum drain");
            h.succeed();
        });
        // EF-MOB-02: the Wight always drops a silver nugget.
        MiteGameTests.add("mob_wight_loot", 40, h -> {
            h.spawn(MobContent.WIGHT.get(), 2, 1, 2).kill(h.getLevel());
            h.succeedWhen(() -> h.assertItemEntityPresent(MaterialItems.SILVER_NUGGET.get()));
        });
        // EF-MOB-05: a naturally spawned Revenant wears armor.
        MiteGameTests.add("mob_revenant_armor", 20, h -> {
            Revenant revenant = h.spawn(MobContent.REVENANT.get(), new BlockPos(2, 1, 2), EntitySpawnReason.NATURAL);
            h.assertTrue(revenant.getItemBySlot(EquipmentSlot.CHEST).is(dev.mite.combat.CombatContent.armorItem(dev.mite.core.Material.RUSTED_IRON,
                    net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, true)), "revenant rusted chainmail chestplate (EF-MET-09)");
            h.assertFalse(revenant.isBaby(), "baby revenant");
            h.succeed();
        });
        // EF-MOB-08: fits a one-block hole, poisons, spawns under leaves near a log only.
        MiteGameTests.add("mob_wood_spider", 20, h -> {
            WoodSpider spider = h.spawn(MobContent.WOOD_SPIDER.get(), 2, 1, 2);
            h.assertTrue(spider.getBbWidth() < 1 && spider.getBbHeight() < 1, "wood spider fits a one-block hole");
            Villager villager = h.spawn(EntityTypes.VILLAGER, 3, 1, 2);
            h.assertTrue(spider.doHurtTarget(h.getLevel(), villager) && villager.hasEffect(MobEffects.POISON), "poison on hit");
            h.setBlock(1, 4, 1, Blocks.OAK_LEAVES);
            h.setBlock(1, 1, 2, Blocks.OAK_LOG);
            h.assertTrue(woodSpiderSpawn(h, new BlockPos(1, 1, 1)), "spawn under leaves near a log");
            h.assertFalse(woodSpiderSpawn(h, new BlockPos(2, 1, 1)), "spawn near a log in the open");
            h.setBlock(4, 4, 2, Blocks.OAK_LEAVES);
            h.setBlock(5, 4, 2, Blocks.OAK_LEAVES);
            h.assertTrue(woodSpiderSpawn(h, new BlockPos(4, 1, 2)), "spawn under leaves, log 3 blocks away");
            h.assertFalse(woodSpiderSpawn(h, new BlockPos(5, 1, 2)), "spawn under leaves, log 4 blocks away");
            h.succeed();
        });
        // CA-3: every depth-limited mob spawns naturally in a dark stone pocket at the converted depth, never one block higher.
        MiteGameTests.add("mob_depth_spawns", 60, h -> {
            ServerLevel level = h.getLevel();
            Map<EntityType<?>, Integer> depths = new LinkedHashMap<>();
            depths.put(MobContent.GHOUL.get(), MobContent.GHOUL_MAX_Y);
            depths.put(MobContent.WIGHT.get(), MobContent.WIGHT_MAX_Y);
            depths.put(MobContent.REVENANT.get(), MobContent.REVENANT_MAX_Y);
            depths.put(MobContent.SHADOW.get(), MobContent.SHADOW_MAX_Y);
            depths.put(MobContent.INVISIBLE_STALKER.get(), MobContent.INVISIBLE_STALKER_MAX_Y);
            depths.put(MobContent.HELLHOUND.get(), MobContent.HELLHOUND_MAX_Y);
            depths.put(MobContent.DEMON_SPIDER.get(), MobContent.DEMON_SPIDER_MAX_Y);
            depths.put(MobContent.EARTH_ELEMENTAL.get(), MobContent.EARTH_ELEMENTAL_MAX_Y);
            depths.put(MobContent.BONE_LORD.get(), MobContent.BONE_LORD_MAX_Y);
            depths.put(MobContent.PHASE_SPIDER.get(), MobContent.PHASE_SPIDER_MAX_Y);
            depths.put(MobContent.BLOB.get(), MobContent.BLOB_MAX_Y);
            depths.put(MobContent.OOZE.get(), MobContent.OOZE_MAX_Y);
            depths.put(MobContent.PUDDING.get(), MobContent.PUDDING_MAX_Y);
            depths.put(MobContent.VAMPIRE_BAT.get(), MobContent.VAMPIRE_BAT_MAX_Y);
            depths.put(MobContent.GIANT_VAMPIRE_BAT.get(), MobContent.VAMPIRE_BAT_MAX_Y);
            depths.put(MobContent.NIGHTWING.get(), MobContent.NIGHTWING_MAX_Y);
            // one closed 3x4x3 stone box per depth in the arena's own columns: air at absolute y top above (1, 1), at top + 1 above (5, 5)
            Map<BlockPos, BlockState> saved = new HashMap<>();
            BlockPos low = h.absolutePos(new BlockPos(1, 0, 1)), high = h.absolutePos(new BlockPos(5, 0, 5));
            for (int top : new HashSet<>(depths.values().stream().map(Formulas::mapDepth).toList())) {
                pocket(level, low.atY(top), saved);
                pocket(level, high.atY(top + 1), saved);
            }
            h.runAfterDelay(20, () -> { // the light engine darkens the pockets
                try {
                    depths.forEach((type, maxY) -> {
                        int top = Formulas.mapDepth(maxY);
                        h.assertTrue(spawnsIn(level, type, low.atY(top)), type.toShortString() + " refused at y=" + top);
                        h.assertFalse(spawnsIn(level, type, high.atY(top + 1)), type.toShortString() + " spawned at y=" + (top + 1));
                    });
                } finally {
                    saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
                }
                h.succeed();
            });
        });
    }

    /** Stone floor, walls and roof around a two-block air pocket at {@code inside}; the replaced states go to {@code saved}. */
    private static void pocket(ServerLevel level, BlockPos inside, Map<BlockPos, BlockState> saved) {
        for (BlockPos p : BlockPos.betweenClosed(inside.offset(-1, -1, -1), inside.offset(1, 2, 1))) {
            BlockPos pos = p.immutable();
            saved.putIfAbsent(pos, level.getBlockState(pos));
            boolean air = pos.getX() == inside.getX() && pos.getZ() == inside.getZ() && pos.getY() >= inside.getY() && pos.getY() <= inside.getY() + 1;
            level.setBlock(pos, (air ? Blocks.AIR : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Natural spawn rules over a few draws (bats refuse half their spawns at random). */
    private static boolean spawnsIn(ServerLevel level, EntityType<?> type, BlockPos pos) {
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 16; i++)
            if (SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)) return true;
        return false;
    }

    private static boolean woodSpiderSpawn(GameTestHelper h, BlockPos pos) {
        return MobContent.checkWoodSpiderSpawnRules(MobContent.WOOD_SPIDER.get(), h.getLevel(), EntitySpawnReason.NATURAL, h.absolutePos(pos), RandomSource.create());
    }

    static void stats(GameTestHelper h, EntityType<? extends Mob> type, double health, double damage, double speed, double follow, int xp) {
        Mob mob = h.spawn(type, 3, 1, 3);
        String name = type.toShortString();
        h.assertValueEqual(mob.getAttributeValue(Attributes.MAX_HEALTH), health, name + " max health");
        h.assertValueEqual(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, name + " attack damage");
        h.assertTrue(Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1e-6, name + " speed");
        h.assertValueEqual(mob.getAttributeValue(Attributes.FOLLOW_RANGE), follow, name + " follow range");
        h.assertValueEqual(mob.getExperienceReward(h.getLevel(), null), xp, name + " XP");
    }
}
