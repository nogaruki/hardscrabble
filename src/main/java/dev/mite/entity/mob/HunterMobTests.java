package dev.mite.entity.mob;

import dev.mite.MiteMod;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** GameTests for EF-MOB-03/04/06/07/09/12: attributes and XP (CA-1), Shadow immunity (CA-2), one behaviour per mob. */
public final class HunterMobTests {
    private HunterMobTests() {}

    public static void register() {
        // CA-1: zombie XP 5, spider XP 5; wolves give 1 to 3 times their multiplier.
        MiteGameTests.add("mob_hunter_attributes", 20, h -> {
            MobTests.stats(h, MobContent.SHADOW.get(), 20, 5, 0.23, 40, 10);
            MobTests.stats(h, MobContent.INVISIBLE_STALKER.get(), 20, 4, 0.23, 40, 10);
            MobTests.stats(h, MobContent.DEMON_SPIDER.get(), 18, 5, 0.3, 40, 15);
            wolf(h, MobContent.DIRE_WOLF.get(), 16, 5, 0.3, 2);
            wolf(h, MobContent.HELLHOUND.get(), 20, 4, 0.4, 3);
            h.succeed();
        });
        // EF-MOB-03 CA-2: iron sword and fire do nothing, silver and magic hurt; a hit weakens and darkens; snuffs a torch.
        MiteGameTests.add("mob_shadow", 100, h -> {
            ServerLevel level = h.getLevel();
            Shadow shadow = h.spawn(MobContent.SHADOW.get(), 2, 1, 2);
            shadow.setNoAi(true);
            h.setBlock(2, 3, 2, Blocks.STONE); // shade from the sun
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            h.assertFalse(shadow.hurtServer(level, level.damageSources().playerAttack(p), 4), "iron sword hurts the shadow");
            h.assertFalse(shadow.hurtServer(level, level.damageSources().inFire(), 4), "fire hurts the shadow");
            h.assertValueEqual(shadow.getHealth(), 20f, "shadow health");
            p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BuiltInRegistries.ITEM.getValue(MiteMod.id("silver_sword"))));
            h.assertTrue(shadow.hurtServer(level, level.damageSources().playerAttack(p), 4), "silver sword spares the shadow");
            // within the silver hit's cooldown, only a bigger amount gets through
            h.assertTrue(shadow.hurtServer(level, level.damageSources().magic(), 6), "magic spares the shadow");

            Villager villager = h.spawn(EntityTypes.VILLAGER, 3, 1, 2);
            h.assertTrue(shadow.doHurtTarget(level, villager), "shadow hit");
            MobEffectInstance weakness = villager.getEffect(MobEffects.WEAKNESS);
            h.assertTrue(weakness != null && weakness.getDuration() == Shadow.WEAKNESS_TICKS, "weakness " + weakness);
            h.assertTrue(villager.hasEffect(MobEffects.DARKNESS), "darkness");

            h.setBlock(1, 1, 1, Blocks.TORCH);
            h.setBlock(3, 1, 3, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
            h.setBlock(1, 1, 3, Blocks.BROWN_MUSHROOM);
            // The light engine runs off-thread and lags under the load of a full run (a fixed 10-tick wait once saw the sky still
            // lit): wait until it has shaded the eye, then check the Shadow itself.
            BlockPos eye = BlockPos.containing(shadow.getX(), shadow.getEyeY(), shadow.getZ());
            h.startSequence().thenWaitUntil(() -> h.assertFalse(level.canSeeSky(eye), "the light engine has not shaded the roof yet"))
                    .thenExecute(() -> {
                        h.assertFalse(shadow.inSunlight(), "sunlight under the roof");
                        for (int i = 0; i < 3; i++) shadow.everySecond(level);
                        h.assertBlockNotPresent(Blocks.TORCH, 1, 1, 1);
                        h.assertBlockProperty(new BlockPos(3, 1, 3), CandleBlock.LIT, false); // blown out, not broken
                        h.assertBlockPresent(Blocks.BROWN_MUSHROOM, 1, 1, 3); // glows, yet is no torch or candle
                    }).thenSucceed();
        });
        // EF-MOB-04: invisible, yet drawn translucent for players.
        MiteGameTests.add("mob_invisible_stalker", 20, h -> {
            InvisibleStalker stalker = h.spawn(MobContent.INVISIBLE_STALKER.get(), 2, 1, 2);
            h.assertTrue(stalker.isInvisible(), "stalker invisible");
            h.assertFalse(stalker.isInvisibleTo(MetabolismTests.player(h)), "stalker hidden from players");
            h.succeed();
        });
        // EF-MOB-06: 16 HP wild, 24 tamed; its owner puts wolf armour on it; a pet survives Peaceful, a wild one does not; no pup
        // from a spawn egg (nor for the Hellhound).
        MiteGameTests.add("mob_dire_wolf", 20, h -> {
            ServerLevel level = h.getLevel();
            DireWolf wolf = h.spawn(MobContent.DIRE_WOLF.get(), 2, 1, 2);
            ServerPlayer owner = MetabolismTests.player(h);
            wolf.tame(owner);
            h.assertValueEqual(wolf.getMaxHealth(), 24f, "tamed max health");
            h.assertValueEqual(wolf.getHealth(), 24f, "tamed health");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOLF_ARMOR));
            wolf.mobInteract(owner, InteractionHand.MAIN_HAND);
            h.assertTrue(wolf.isWearingBodyArmor() && wolf.getBodyArmorItem().is(Items.WOLF_ARMOR), "wolf armour equipped");
            DireWolf wild = h.spawn(MobContent.DIRE_WOLF.get(), 4, 1, 2);
            MinecraftServer server = level.getServer();
            Difficulty before = server.getWorldData().getDifficulty();
            server.setDifficulty(Difficulty.PEACEFUL, true);
            try {
                wolf.checkDespawn();
                wild.checkDespawn();
            } finally {
                server.setDifficulty(before, true); // restored in the same tick: tests run in parallel
            }
            h.assertFalse(wolf.isRemoved(), "tamed dire wolf removed in Peaceful");
            h.assertTrue(wild.isRemoved(), "wild dire wolf kept in Peaceful");
            h.assertTrue(wolf.getBreedOffspring(level, wolf) == null, "dire wolf offspring");
            h.assertTrue(h.spawn(MobContent.HELLHOUND.get(), 4, 1, 4).getBreedOffspring(level, wolf) == null, "hellhound offspring");
            h.succeed();
        });
        // EF-MOB-17: a bat drops a target that turned creative (vanilla target goals recheck their conditions).
        MiteGameTests.add("mob_vampire_bat_retarget", 20, h -> {
            VampireBat bat = h.spawn(MobContent.VAMPIRE_BAT.get(), 2, 2, 2);
            ServerPlayer creative = MiteGameTests.levelPlayer(h);
            creative.snapTo(h.absoluteVec(new Vec3(4.5, 1, 2.5)));
            bat.setTarget(creative);
            bat.tickCount = 20;
            bat.customServerAiStep(h.getLevel());
            LivingEntity target = bat.getTarget();
            h.assertTrue(target != creative, "bat still hunts a creative player");
            h.succeed();
        });
        // EF-MOB-07: the breath sets the target on fire; fire does not hurt the Hellhound.
        MiteGameTests.add("mob_hellhound", 20, h -> {
            ServerLevel level = h.getLevel();
            Hellhound hound = h.spawn(MobContent.HELLHOUND.get(), 2, 1, 2);
            Villager villager = h.spawn(EntityTypes.VILLAGER, 4, 1, 2);
            hound.breathe(level, villager);
            h.assertTrue(villager.isOnFire(), "breath ignites");
            h.assertFalse(hound.hurtServer(level, level.damageSources().inFire(), 4), "fire hurts the hellhound");
            h.succeed();
        });
        // EF-MOB-09: poison 480 ticks and Slowness VI 50 ticks.
        MiteGameTests.add("mob_demon_spider", 20, h -> {
            DemonSpider spider = h.spawn(MobContent.DEMON_SPIDER.get(), 2, 1, 2);
            Villager villager = h.spawn(EntityTypes.VILLAGER, 3, 1, 2);
            h.assertTrue(spider.doHurtTarget(h.getLevel(), villager), "demon spider hit");
            MobEffectInstance poison = villager.getEffect(MobEffects.POISON), slow = villager.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(poison != null && poison.getDuration() == DemonSpider.POISON_TICKS, "poison " + poison);
            h.assertTrue(slow != null && slow.getAmplifier() == DemonSpider.SLOWNESS_AMPLIFIER && slow.getDuration() == DemonSpider.SLOWNESS_TICKS, "slowness " + slow);
            h.succeed();
        });
        // EF-MOB-12: every spider throws webs; a web falling on the floor leaves a cobweb.
        MiteGameTests.add("mob_thrown_web", 60, h -> {
            Spider spider = h.spawn(EntityTypes.SPIDER, 5, 1, 5);
            h.assertTrue(spider.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof ThrownWeb.ThrowGoal), "vanilla spider throws webs");
            ThrownWeb web = MobContent.THROWN_WEB.get().create(h.getLevel(), EntitySpawnReason.TRIGGERED);
            Vec3 pos = h.absoluteVec(new Vec3(2.5, 3.5, 2.5));
            web.setPos(pos);
            web.shoot(0, -1, 0, 0.5F, 0);
            h.getLevel().addFreshEntity(web);
            h.succeedWhenBlockPresent(Blocks.COBWEB, 2, 1, 2);
        });
        // EF-MOB-09: a Demon Spider web saved in flight still sets its target on fire once loaded.
        MiteGameTests.add("mob_thrown_web_fiery_saved", 20, h -> {
            ServerLevel level = h.getLevel();
            Villager owner = h.spawn(EntityTypes.VILLAGER, 1, 1, 1), struck = h.spawn(EntityTypes.VILLAGER, 4, 1, 4);
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            new ThrownWeb(level, owner, true).saveWithoutId(out);
            ThrownWeb loaded = MobContent.THROWN_WEB.get().create(level, EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
            loaded.onHitEntity(new EntityHitResult(struck));
            h.assertTrue(struck.isOnFire(), "loaded fiery web ignites");
            h.succeed();
        });
    }

    private static void wolf(GameTestHelper h, EntityType<? extends Mob> type, double health, double damage, double speed, int xpMultiplier) {
        Mob mob = h.spawn(type, 3, 1, 3);
        String name = type.toShortString();
        h.assertValueEqual(mob.getAttributeValue(Attributes.MAX_HEALTH), health, name + " max health");
        h.assertValueEqual(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), damage, name + " attack damage");
        h.assertTrue(Math.abs(mob.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1e-6, name + " speed");
        h.assertValueEqual(mob.getAttributeValue(Attributes.FOLLOW_RANGE), 40.0, name + " follow range");
        int xp = mob.getExperienceReward(h.getLevel(), null);
        h.assertTrue(xp >= xpMultiplier && xp <= 3 * xpMultiplier && xp % xpMultiplier == 0, name + " XP " + xp);
    }
}
