package dev.mite.player;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mite.entity.mob.MobContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismService;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.EventHooks;
import dev.mite.config.MiteConfig;
import dev.mite.core.Formulas;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** GameTests for B02 to B05. XP goes through the vanilla entry points, redirected to ProgressionService. */
public final class XpTests {
    private XpTests() {}

    public static void register() {
        // EF-JOU-02: level 10 = 750 cumulative XP, level 35 = 7000; vanilla mirror follows.
        MiteGameTests.add("xp_curve", 20, helper -> {
            ServerPlayer p = MetabolismTests.player(helper);
            p.giveExperiencePoints(749);
            helper.assertValueEqual(PlayerLevel.get(p), 9, "level at 749 XP");
            p.giveExperiencePoints(1);
            helper.assertValueEqual(PlayerLevel.get(p), 10, "level at 750 XP");
            p.giveExperienceLevels(25);
            helper.assertValueEqual(ProgressionService.xp(p), 7000, "XP at level 35");
            helper.assertValueEqual(p.experienceLevel, 35, "mirrored level");
            helper.succeed();
        });

        // audit 2 moderne m7: the sculk family drops no XP (vanilla 1 for sculk, 5 for the catalyst, sensors and shrieker).
        MiteGameTests.add("xp_sculk_none", 20, helper -> {
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
            for (var block : XpRules.NO_XP_BLOCKS) {
                var event = new net.neoforged.neoforge.event.level.BlockDropsEvent(helper.getLevel(), pos, block.defaultBlockState(), null,
                        new java.util.ArrayList<>(), null, net.minecraft.world.item.ItemStack.EMPTY);
                if (block == net.minecraft.world.level.block.Blocks.SCULK_CATALYST) helper.assertValueEqual(event.getDroppedExperience(), 5, "vanilla catalyst XP");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                helper.assertValueEqual(event.getDroppedExperience(), 0, block + " XP");
            }
            helper.succeed();
        });

        // EF-JOU-03 CA-1: losing 100 points from 0 gives level -5; the vanilla mirror stays at 0.
        MiteGameTests.add("xp_negative_levels", 20, helper -> {
            ServerPlayer p = MetabolismTests.player(helper);
            p.giveExperiencePoints(-100);
            helper.assertValueEqual(PlayerLevel.get(p), -5, "level");
            helper.assertValueEqual(p.experienceLevel, 0, "mirrored level");
            p.giveExperiencePoints(-10_000);
            helper.assertValueEqual(ProgressionService.xp(p), -800, "XP floor");
            helper.succeed();
        });

        // EF-JOU-05 CA-1 (R196 default): 750 XP respawns at 0, 20 XP respawns at -10.
        MiteGameTests.add("xp_death_penalty", 20, helper -> {
            int[][] cases = {{750, 0}, {20, -10}};
            for (int[] c : cases) {
                ServerPlayer old = MetabolismTests.player(helper);
                ProgressionService.setXp(old, c[0]);
                ServerPlayer respawned = MetabolismTests.player(helper);
                EventHooks.onPlayerClone(respawned, old, true);
                helper.assertValueEqual(ProgressionService.xp(respawned), c[1], "XP after death at " + c[0]);
            }
            helper.succeed();
        });

        // EF-JOU-01 CA-1 through the XP path: crossing level 5 raises max health 6 -> 8 and health +2.
        MiteGameTests.add("xp_max_health", 20, helper -> {
            ServerPlayer p = MetabolismTests.player(helper);
            MetabolismService.updateMaxHealth(p);
            p.giveExperiencePoints(249);
            helper.assertValueEqual(p.getMaxHealth(), 6f, "max health at level 4");
            helper.assertValueEqual(p.getHealth(), 6f, "health at level 4");
            p.giveExperiencePoints(1);
            helper.assertValueEqual(p.getMaxHealth(), 8f, "max health at level 5");
            helper.assertValueEqual(p.getHealth(), 8f, "health at level 5");
            helper.succeed();
        });

        // EF-JOU-04 CA-2: melee damage x0.2 at level -40.
        MiteGameTests.add("xp_melee_factor", 20, helper -> {
            ServerPlayer p = MetabolismTests.player(helper);
            ProgressionService.setXp(p, -800);
            double ratio = p.getAttributeValue(Attributes.ATTACK_DAMAGE) / p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            helper.assertTrue(Math.abs(ratio - 0.2) < 1e-6, "melee factor " + ratio);
            helper.succeed();
        });

        // EF-JOU-07 CA-1 / M15: a cow gives no XP; hostile wolves and the hoglin (MONSTER category) keep theirs.
        MiteGameTests.add("xp_passive_animals", 20, h -> {
            h.assertValueEqual(drop(h.spawn(EntityTypes.COW, 1, 1, 1), 5), 0, "cow XP");
            h.assertTrue(drop(h.spawn(MobContent.HELLHOUND.get(), 3, 1, 3), 5) > 0, "Hellhound XP");
            h.assertTrue(drop(h.spawn(MobContent.DIRE_WOLF.get(), 5, 1, 5), 5) > 0, "Dire Wolf XP");
            h.assertTrue(drop(h.spawn(EntityTypes.HOGLIN, 1, 1, 5), 5) > 0, "hoglin XP");
            h.succeed();
        });

        // ET-B-05 (EF-IA-04, EF-IA-08): hardscrabble:xp_multiplier on vanilla mobs, zombie untouched.
        MiteGameTests.add("xp_multipliers", 20, h -> {
            h.assertValueEqual(drop(h.spawn(EntityTypes.ZOMBIFIED_PIGLIN, 1, 1, 1), 5), 15, "zombified piglin x3");
            h.assertValueEqual(drop(h.spawn(EntityTypes.GHAST, 3, 1, 3), 5), 10, "ghast x2");
            h.assertValueEqual(drop(h.spawn(EntityTypes.BLAZE, 5, 1, 5), 5), 20, "blaze x4");
            h.assertValueEqual(drop(h.spawn(EntityTypes.WITCH, 1, 1, 5), 5), 20, "witch x4");
            h.assertValueEqual(drop(h.spawn(EntityTypes.ZOMBIE, 5, 1, 1), 5), 5, "zombie x1");
            h.succeed();
        });

        // B02 (EF-JOU-02): /xp query points reads the MITE span (380 at level 35, vanilla 157).
        MiteGameTests.add("xp_query_points", 20, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            ProgressionService.setXp(p, 7000 + 190);
            h.assertValueEqual(p.getXpNeededForNextLevel(), XpCurve.span(35), "points needed at level 35");
            MinecraftServer server = h.getLevel().getServer();
            try {
                int points = server.getCommands().getDispatcher().execute("xp query @s points",
                        server.createCommandSourceStack().withEntity(p).withSuppressedOutput());
                h.assertValueEqual(points, 190, "/xp query points");
            } catch (CommandSyntaxException e) {
                throw h.assertionException(net.minecraft.network.chat.Component.literal(e.getMessage()));
            }
            h.succeed();
        });

        // M6 (EF-JOU-08, RE-04): the emerald ore no longer gets its 100 vanilla tries per chunk.
        MiteGameTests.add("xp_emerald_ore_rare", 20, h -> {
            PlacedFeature emerald = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).getValueOrThrow(OrePlacements.ORE_EMERALD);
            h.assertTrue(emerald.placement().stream().anyMatch(m -> m instanceof RarityFilter), "emerald rarity filter");
            h.assertFalse(emerald.placement().stream().anyMatch(m -> m instanceof CountPlacement), "no vanilla emerald count");
            h.succeed();
        });

        // A-2: in strict mode keepInventory does not cancel the death penalty (the game rule is also forced off: RulesTests).
        MiteGameTests.add("xp_keep_inventory_strict", 20, h -> {
            ServerPlayer old = MetabolismTests.player(h), respawned = MetabolismTests.player(h);
            ProgressionService.setXp(old, 750);
            MinecraftServer server = h.getLevel().getServer();
            boolean prev = server.getGameRules().get(GameRules.KEEP_INVENTORY);
            try {
                server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
                EventHooks.onPlayerClone(respawned, old, true);
            } finally {
                server.getGameRules().set(GameRules.KEEP_INVENTORY, prev, server);
            }
            h.assertValueEqual(ProgressionService.xp(respawned), 0, "XP after death with keepInventory in strict mode");
            h.succeed();
        });

        // A-4: player.death_penalty switched to MCMOD_LEVEL costs one level instead of the whole capital.
        MiteGameTests.add("xp_death_penalty_config", 20, h -> {
            ServerPlayer old = MetabolismTests.player(h), respawned = MetabolismTests.player(h);
            ProgressionService.setXp(old, 750);
            MiteGameTests.withConfig(MiteConfig.DEATH_PENALTY, XpCurve.DeathPenalty.MCMOD_LEVEL, () -> {
                EventHooks.onPlayerClone(respawned, old, true);
                return null;
            });
            h.assertValueEqual(ProgressionService.xp(respawned), Formulas.xpForLevel(9), "XP after death at level 10 (mcmod)");
            h.succeed();
        });

        // B-2: with WIKI_THIRD a third of the XP drops as orbs that only the dead player may pick up.
        MiteGameTests.add("xp_wiki_third_owned_orbs", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h), other = MetabolismTests.player(h);
            p.snapTo(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
            ProgressionService.setXp(p, 750);
            int vanilla = MiteGameTests.withConfig(MiteConfig.DEATH_PENALTY, XpCurve.DeathPenalty.WIKI_THIRD, () -> EventHooks.getExperienceDrop(p, null, 10));
            List<ExperienceOrb> orbs = h.getLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(2));
            h.assertValueEqual(vanilla, 0, "ownerless XP dropped");
            h.assertValueEqual(orbs.stream().mapToInt(ExperienceOrb::getValue).sum(), 250, "owned orb XP");
            for (ExperienceOrb orb : orbs) {
                h.assertTrue(p.getUUID().equals(XpRules.owner(orb)), "orb owner");
                h.assertTrue(NeoForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(other, orb)).isCanceled(), "another player picks the orb up");
                h.assertFalse(NeoForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(p, orb)).isCanceled(), "owner refused his orb");
                orb.discard();
            }
            h.succeed();
        });

        // B-2: owned orbs still merge with orbs of the same owner, never with ownerless ones.
        MiteGameTests.add("xp_owned_orbs_merge_by_owner", 60, h -> {
            UUID owner = UUID.randomUUID();
            ExperienceOrb a = orb(h, owner, null), same = orb(h, owner, a), free = orb(h, null, a);
            h.succeedWhen(() -> {
                h.assertTrue(a.isRemoved() != same.isRemoved(), "orbs of the same owner not merged");
                h.assertFalse(free.isRemoved(), "ownerless orb merged into an owned one");
                free.discard();
                (a.isRemoved() ? same : a).discard();
            });
        });

        // B-4: the level-up sound plays when a gain reaches a multiple of 5 (vanilla rhythm, one heart each), not on losses.
        MiteGameTests.add("xp_level_up_sound", 20, h -> {
            h.assertTrue(ProgressionService.levelUpSound(4, 5) && ProgressionService.levelUpSound(0, 12) && ProgressionService.levelUpSound(9, 10), "gains to 5, 10");
            h.assertFalse(ProgressionService.levelUpSound(5, 9) || ProgressionService.levelUpSound(10, 4) || ProgressionService.levelUpSound(-6, 0), "no multiple of 5 reached");
            h.succeed();
        });

        // B-3 (ENF-SAV-02): a player never tracked by the mod (vanilla level 30) starts from 0; a tracked one keeps his XP.
        MiteGameTests.add("xp_untracked_player_reset", 20, h -> {
            ServerPlayer vanilla = MetabolismTests.player(h), tracked = MetabolismTests.player(h);
            vanilla.experienceLevel = 30;
            vanilla.totalExperience = 1395;
            ProgressionService.setXp(tracked, 750);
            ProgressionService.onLogin(new PlayerEvent.PlayerLoggedInEvent(vanilla));
            ProgressionService.onLogin(new PlayerEvent.PlayerLoggedInEvent(tracked));
            h.assertValueEqual(ProgressionService.xp(vanilla), 0, "XP of a vanilla level 30");
            h.assertValueEqual(vanilla.experienceLevel, 0, "vanilla level mirror");
            h.assertValueEqual(ProgressionService.xp(tracked), 750, "XP of a tracked player");
            h.succeed();
        });

        // Changelog l.47: Slowness (paralysis) slows climbing, Swiftness speeds it up, sprinting does not; other mobs keep 1.
        MiteGameTests.add("player_climb_speed_modifiers", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.assertTrue(Math.abs(PlayerMovement.climbFactor(p) - 1) < 1e-6, "plain climb factor " + PlayerMovement.climbFactor(p));
            p.setSprinting(true);
            h.assertTrue(Math.abs(PlayerMovement.climbFactor(p) - 1) < 1e-6, "sprinting climb factor " + PlayerMovement.climbFactor(p));
            p.setSprinting(false);
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 5)); // Ghoul paralysis
            h.assertTrue(PlayerMovement.climbFactor(p) < 0.2, "paralysed climb factor " + PlayerMovement.climbFactor(p));
            p.removeAllEffects();
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 1));
            h.assertTrue(PlayerMovement.climbFactor(p) > 1.3, "Swiftness II climb factor " + PlayerMovement.climbFactor(p));
            h.assertValueEqual(PlayerMovement.climbFactor(h.spawn(EntityTypes.ZOMBIE, 1, 1, 1)), 1.0, "zombie climb factor");
            h.succeed();
        });

        // Changelog l.602: a fence is 1 block high for players (they hop over it) and stays 1.5 for mobs (pens hold).
        MiteGameTests.add("player_hops_fences", 20, h -> {
            h.setBlock(3, 1, 3, Blocks.OAK_FENCE);
            BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
            BlockState fence = h.getLevel().getBlockState(pos);
            double player = fence.getCollisionShape(h.getLevel(), pos, CollisionContext.of(MetabolismTests.player(h))).max(Direction.Axis.Y);
            double pig = fence.getCollisionShape(h.getLevel(), pos, CollisionContext.of(h.spawn(EntityTypes.PIG, 1, 1, 1))).max(Direction.Axis.Y);
            h.assertValueEqual(player, 1.0, "fence height for a player");
            h.assertValueEqual(pig, 1.5, "fence height for a pig");
            h.succeed();
        });
    }

    private static int drop(LivingEntity entity, int xp) {
        return EventHooks.getExperienceDrop(entity, null, xp);
    }

    /** A still orb of value 5 at the arena centre; with {@code slot}, one whose id passes vanilla's merge test against it (id mod 40). */
    private static ExperienceOrb orb(GameTestHelper h, @Nullable UUID owner, @Nullable ExperienceOrb slot) {
        Vec3 pos = h.absoluteVec(new Vec3(3.5, 1, 3.5));
        ExperienceOrb orb;
        do orb = new ExperienceOrb(h.getLevel(), pos.x, pos.y, pos.z, 5);
        while (slot != null && (orb.getId() - slot.getId()) % 40 != 0);
        orb.setDeltaMovement(Vec3.ZERO);
        if (owner != null) orb.setData(ProgressionService.XP_OWNER, owner);
        h.getLevel().addFreshEntity(orb);
        return orb;
    }
}
