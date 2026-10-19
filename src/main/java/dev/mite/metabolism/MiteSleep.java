package dev.mite.metabolism;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Metabolism;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * EF-NUT-11 MITE sleep. Vanilla's instant night skip is disabled (MX-08, ServerLevelMixin); instead, while enough
 * players sleep at night, the level clock runs {@link #RATE} times faster and each sleeper's metabolism runs as many ticks
 * (ENF-PERF-03: a 12 000-tick night in 15 s, only the sleepers simulated). The weather clock is forwarded by the time slept
 * (changelog l.566, R128). Dedicated servers never forward time (changelog l.799, R118): sleepers lie in bed until sunrise.
 */
public final class MiteSleep {
    private MiteSleep() {}

    /** Simulated ticks per server tick while everyone sleeps: 12 000 / 40 = 300 ticks = 15 s. */
    static final int RATE = 40;
    static final float WAKE_CHANCE = 1 / 1000f;
    static final int WELL_RESTED_TICKS = 6000;
    static final int MIN_HOUR_TICK = 15_000;
    public static final Identifier WELL_RESTED = MiteMod.id("well_rested");

    static final Player.BedSleepingProblem NO_SHELTER = new Player.BedSleepingProblem(Component.translatable("message.hardscrabble.sleep_no_shelter"));
    static final Player.BedSleepingProblem TOO_EARLY = new Player.BedSleepingProblem(Component.translatable("message.hardscrabble.sleep_too_early"));

    private static final Set<ResourceKey<Level>> DARK = new HashSet<>();

    /** Shelter: an opaque block somewhere above the bed [NON VÉRIFIÉ Q04]. */
    static boolean sheltered(Level level, BlockPos bed) {
        for (BlockPos p = bed.above(); p.getY() <= level.getMaxY(); p = p.above()) {
            if (level.getBlockState(p).isSolidRender()) return true;
        }
        return false;
    }

    private static void canSleep(CanPlayerSleepEvent e) {
        Level level = e.getLevel();
        boolean minHour = MiteConfig.SLEEP_MIN_HOUR.get();
        // Daytime naps: drop vanilla's "only at night" refusal.
        if (!minHour && e.getProblem() != null && e.getState().getBlock() instanceof AbstractBedBlock bed) {
            BedRule rule = bed.getBedRule(level, e.getPos());
            if (rule.canSleep() == BedRule.Rule.WHEN_DARK && Objects.equals(e.getProblem(), rule.asProblem())) e.setProblem(null);
        }
        if (e.getProblem() != null) return;
        if (minHour && tooEarly(level.getDefaultClockTime())) e.setProblem(TOO_EARLY);
        else if (!sheltered(level, e.getPos())) e.setProblem(NO_SHELTER);
    }

    /** A-4: with {@code sleep_min_hour}, bed is refused before 21:00 (tick 15000 of the day). */
    static boolean tooEarly(long clockTime) {
        return clockTime % 24000 < MIN_HOUR_TICK;
    }

    private static void tickLevel(ServerLevel level) {
        boolean dark = level.isDarkOutside();
        List<ServerPlayer> sleepers = level.players().stream().filter(LivingEntity::isSleeping).toList();
        // Sleep ends at sunrise.
        if (!dark && DARK.remove(level.dimension())) sleepers.forEach(p -> p.stopSleepInBed(false, true));
        if (dark) DARK.add(level.dimension());
        boolean fast = dark && forwardsTime(level.getServer()) && enoughSleeping(level.players(), level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE));
        level.dimensionType().defaultClock().ifPresent(clock -> {
            var clocks = level.getServer().clockManager();
            float rate = clocks.getInstance(clock).rate();
            if (fast && rate != RATE) clocks.setRate(clock, RATE);
            else if (!fast && rate == RATE) clocks.setRate(clock, 1);
            if (fast && level.canHaveWeather() && level.getGameRules().get(GameRules.ADVANCE_WEATHER)) forwardWeather(level.getWeatherData(), RATE - 1);
        });
        for (ServerPlayer p : sleepers) {
            if (p.isSleeping()) sleep(p, fast ? RATE : 1, WAKE_CHANCE);
        }
    }

    /** Changelog l.799 (R118): no time forwarding on a dedicated server, to prevent easy farming. */
    static boolean forwardsTime(MinecraftServer server) {
        return !server.isDedicatedServer();
    }

    /**
     * Changelog l.566 (R128): the weather timers run {@code ticks} more ticks, on top of the vanilla tick of this server tick. A
     * timer stops at 1, so the next vanilla tick still flips the weather and samples the new duration (and EF-LUN-05 shortens it).
     * ponytail: at most one weather change per server tick, the overshoot (< RATE ticks) is dropped.
     */
    static void forwardWeather(WeatherData w, int ticks) {
        if (w.getClearWeatherTime() > 0) {
            w.setClearWeatherTime(Math.max(1, w.getClearWeatherTime() - ticks));
            return;
        }
        if (w.getRainTime() > 0) w.setRainTime(Math.max(1, w.getRainTime() - ticks));
        if (w.getThunderTime() > 0) w.setThunderTime(Math.max(1, w.getThunderTime() - ticks));
    }

    /**
     * Vanilla's rule: playersSleepingPercentage of the active players sleep long enough (over 100 disables it). Spectators and
     * players on the respawn screen are not active (changelog R118).
     */
    static boolean enoughSleeping(List<? extends Player> players, int percentage) {
        if (percentage > 100) return false;
        List<? extends Player> active = players.stream().filter(p -> !p.isSpectator() && !p.isDeadOrDying()).toList();
        long deep = active.stream().filter(Player::isSleepingLongEnough).count();
        return !active.isEmpty() && deep >= Math.max(1, Mth.ceil(active.size() * percentage / 100f));
    }

    /** EF-NUT-11: a monster within 8 blocks, or within 48 with a path to the bed; else one spawned 8-48 blocks away that has one. */
    static @Nullable Monster intruder(ServerPlayer p) {
        BlockPos bed = p.getSleepingPos().orElse(p.blockPosition());
        for (Monster m : p.level().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(48))) {
            if (comesFor(m, p, bed)) return m;
        }
        return spawnIntruder(p, bed);
    }

    static boolean comesFor(Monster m, ServerPlayer p, BlockPos bed) {
        return m.distanceToSqr(p) < 64 || reaches(m, bed);
    }

    static boolean reaches(Mob mob, BlockPos bed) {
        Path path = mob.getNavigation().createPath(bed, 1);
        return path != null && path.canReach();
    }

    /** One natural spawn attempt (biome and structure monster list, light and placement rules) kept only if it can reach the bed. */
    private static @Nullable Monster spawnIntruder(ServerPlayer p, BlockPos bed) {
        ServerLevel level = p.level();
        RandomSource r = p.getRandom();
        double angle = r.nextDouble() * Mth.TWO_PI, dist = 8 + r.nextDouble() * 40;
        BlockPos pos = BlockPos.containing(p.getX() + Math.cos(angle) * dist, p.getY() + r.nextInt(9) - 4, p.getZ() + Math.sin(angle) * dist);
        if (!level.isPositionEntityTicking(pos)) return null;
        var data = level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.MONSTER, pos).getRandom(r).orElse(null);
        if (data == null || !SpawnPlacements.isSpawnPositionOk(data.type(), level, pos)
                || !SpawnPlacements.checkSpawnRules(data.type(), level, EntitySpawnReason.NATURAL, pos, r)
                || !(data.type().create(level, EntitySpawnReason.NATURAL) instanceof Monster m)) return null;
        m.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360, 0);
        m.setOnGround(true);
        if (!level.noCollision(m) || !reaches(m, bed)) return null;
        EventHooks.finalizeMobSpawn(m, level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
        level.addFreshEntityWithPassengers(m);
        return m;
    }

    /**
     * Advances one sleeper by {@code ticks} simulated ticks; its own player tick already ran the metabolism once, so
     * {@code ticks - 1} more run here. Wakes on zero energy or, with {@code wakeChance} per tick, if an {@link #intruder}
     * comes for the player. Awards Well Rested at 6000 ticks of sleep.
     */
    public static void sleep(ServerPlayer p, int ticks, float wakeChance) {
        Metabolism m = MetabolismService.get(p);
        for (int i = 0; i < ticks; i++) {
            if (i > 0) p.getFoodData().tick(p);
            if (!p.isSleeping()) return; // starvation damage woke the player
            if (++m.sleepTicks == WELL_RESTED_TICKS) {
                AdvancementHolder adv = p.level().getServer().getAdvancements().get(WELL_RESTED);
                if (adv != null) p.getAdvancements().award(adv, "slept");
            }
            if (!m.hasEnergy()) {
                wake(p, "message.hardscrabble.woken_by_hunger");
                return;
            }
            if (wakeChance > 0 && p.getRandom().nextFloat() < wakeChance) {
                Monster mob = intruder(p);
                if (mob != null) {
                    mob.setTarget(p);
                    wake(p, "message.hardscrabble.woken_by_mob");
                    return;
                }
            }
        }
    }

    private static void wake(ServerPlayer p, String message) {
        p.stopSleepInBed(true, true);
        p.sendOverlayMessage(Component.translatable(message));
    }

    static void registerEvents(IEventBus bus) {
        bus.addListener(MiteSleep::canSleep);
        // Naps continue in daylight (vanilla wakes sleepers when the bed rule fails); sunrise is handled in tickLevel. The same
        // OTHER_PROBLEM also means the bed is gone (LivingEntity.checkBedExists): that one still wakes the sleeper.
        bus.addListener((CanContinueSleepingEvent e) -> {
            LivingEntity sleeper = e.getEntity();
            if (e.getProblem() == Player.BedSleepingProblem.OTHER_PROBLEM && !MiteConfig.SLEEP_MIN_HOUR.get() && sleeper.getSleepingPos()
                    .map(pos -> sleeper.level().getBlockState(pos).getBlock() instanceof AbstractBedBlock).orElse(false)) e.setContinueSleeping(true);
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) tickLevel(level);
        });
    }
}
