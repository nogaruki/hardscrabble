package dev.mite.lunar;

import dev.mite.core.MoonCalendar;
import dev.mite.core.MoonCalendar.Moon;
import dev.mite.gametest.MiteGameTests;
import dev.mite.worldgen.structure.MiteStructures;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.mite.entity.mob.MobContent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** GameTests for EF-LUN-01 (calendar, phases), EF-LUN-02 CA-1 (frenzy), EF-LUN-03/04/05 (sky, fog, blue moon night) and EF-MON-03. */
public final class LunarTests {
    private LunarTests() {}

    /**
     * Runs the check with the overworld clock moved to the middle of the given day, then restores it. Synchronous, so the
     * global clock is never seen modified by other tests or by the tick handlers.
     */
    public static void onDay(GameTestHelper h, long day, Runnable check) {
        onDay(h, day, 6000, check);
    }

    /** Same as {@link #onDay(GameTestHelper, long, Runnable)} at the given time of day. */
    public static void onDay(GameTestHelper h, long day, int timeOfDay, Runnable check) {
        onTicks(h, (day - 1) * MoonCalendar.TICKS_PER_DAY + timeOfDay, check);
    }

    /** Same with an exact clock value; the environment attributes are resampled on both changes. */
    public static void onTicks(GameTestHelper h, long ticks, Runnable check) {
        ServerLevel level = h.getLevel();
        Holder<WorldClock> clock = level.dimensionType().defaultClock().orElseThrow();
        var clocks = level.getServer().clockManager();
        long saved = clocks.getInstance(clock).totalTicks();
        clocks.setTotalTicks(clock, ticks);
        level.environmentAttributes().invalidateTickCache();
        try {
            check.run();
        } finally {
            clocks.setTotalTicks(clock, saved);
            level.environmentAttributes().invalidateTickCache();
        }
    }

    private static final int DAY = MoonCalendar.TICKS_PER_DAY;

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): blood_moon_period and blue_moon_period set the special days (32 and 128 by default).
        MiteGameTests.add("config_moon_periods", 20, h -> {
            Moon blood = MiteGameTests.withConfig(dev.mite.config.MiteConfig.BLOOD_MOON_PERIOD, 10, () -> Lunar.moon(30));
            Moon blue = MiteGameTests.withConfig(dev.mite.config.MiteConfig.BLUE_MOON_PERIOD, 20, () -> Lunar.moon(60));
            h.assertValueEqual(blood, Moon.BLOOD, "day 30 with blood_moon_period=10");
            h.assertValueEqual(blue, Moon.BLUE, "day 60 with blue_moon_period=20");
            h.assertValueEqual(Lunar.moon(30), Moon.NORMAL, "day 30 by default");
            h.assertValueEqual(Lunar.moon(60), Moon.NORMAL, "day 60 by default");
            h.succeed();
        });

        // A-4 (EF-CFG-02 CA-1): temple_min_distance gates the temples, stronghold_distance_multiplier scales the rings.
        MiteGameTests.add("config_structure_distances", 20, h -> {
            boolean near = MiteGameTests.withConfig(dev.mite.config.MiteConfig.TEMPLE_MIN_DISTANCE, 0, () -> MiteStructures.farFromSpawn(0, 0));
            h.assertTrue(near, "chunk at spawn refused with temple_min_distance=0");
            var rings = (MiteStructures.ScaledRings) h.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                    .getValue(Identifier.withDefaultNamespace("strongholds")).placement();
            int scaled = MiteGameTests.withConfig(dev.mite.config.MiteConfig.STRONGHOLD_DISTANCE_MULTIPLIER, 2, rings::distance);
            h.assertValueEqual(scaled, rings.baseDistance() * 2, "stronghold distance with multiplier 2");
            h.assertValueEqual(rings.distance(), rings.baseDistance() * 16, "stronghold distance by default");
            h.succeed();
        });

        MiteGameTests.add("lunar_calendar", 20, h -> {
            Map.of(31L, Moon.NORMAL, 32L, Moon.BLOOD, 64L, Moon.BLOOD, 120L, Moon.MOON_DOG, 128L, Moon.BLUE).forEach((day, moon) ->
                    onDay(h, day, () -> h.assertValueEqual(Lunar.moon(h.getLevel()), moon, "moon of day " + day)));
            h.succeed();
        });

        // dom-N-03: the special moons are full moons (timeline override); CL R133: the night is darker under a new moon.
        MiteGameTests.add("lunar_full_moon_phases", 20, h -> {
            ServerLevel level = h.getLevel();
            Vec3 pos = Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO));
            for (long day : new long[] {24, 32, 120, 128}) onDay(h, day, () -> h.assertValueEqual(
                    level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos), MoonPhase.FULL_MOON, "phase of day " + day));
            onDay(h, 36, () -> h.assertValueEqual(level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos), MoonPhase.NEW_MOON, "phase of day 36"));
            float[] light = new float[3];
            onTicks(h, 31L * DAY + 18000, () -> light[0] = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, pos));
            onTicks(h, 35L * DAY + 18000, () -> light[1] = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, pos));
            onTicks(h, 35L * DAY + 6000, () -> light[2] = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, pos));
            h.assertTrue(Math.abs(light[1] - light[0] * MiteSky.nightLight(3)) < 1e-3 && light[1] < light[0], "new moon night light: " + light[1] + " vs " + light[0]);
            h.assertTrue(light[2] > 14.9F, "day light changed by the moon: " + light[2]);
            h.succeed();
        });

        // M32, M33, dom-N-06: moon glow at night only, sunrise forecast, sunrise hidden by rain, morning fogs, Underworld fog cycle.
        MiteGameTests.add("lunar_sky_layers", 20, h -> {
            Vector3fc white = new Vector3f(1, 1, 1);
            h.assertTrue(MiteSky.glow(white, Moon.BLOOD, 31L * DAY + 18000).y() < 0.7F, "no red glow on a blood moon night");
            h.assertValueEqual(MiteSky.glow(white, Moon.BLOOD, 31L * DAY + 6000).y(), 1F, "glow by day");
            h.assertValueEqual(MiteSky.glow(white, Moon.NORMAL, 30L * DAY + 18000).y(), 1F, "glow on a normal night");
            h.assertValueEqual(MiteSky.sunrise(new Vector4f(1, 1, 1, 1), 0, 1F).w(), 0F, "sunrise visible under rain");
            int fogDays = 0;
            for (long day = 1; day <= 800; day++) {
                long morning = (day - 1) * DAY + 2000;
                boolean fog = MiteSky.morningFog(morning) == 1F;
                if (fog) fogDays++;
                h.assertValueEqual(MiteSky.morningFog(morning + 8000), 0F, "fog in the afternoon of day " + day);
                if (fog) h.assertTrue(Lunar.moon(day) != Moon.BLOOD && Lunar.moon(day) != Moon.BLUE, "fog on special day " + day);
            }
            h.assertTrue(fogDays > 50 && fogDays < 150, "fog days: " + fogDays + "/800");
            h.assertTrue(MiteSky.underworldFog(31L * DAY + 1) > 0.99F && MiteSky.underworldFog(47L * DAY + 1) < 0.01F, "Underworld fog cycle");
            onTicks(h, 31L * DAY, () -> h.assertValueEqual(MiteSky.forecast(h.getLevel()), 2, "blood moon forecast"));
            onTicks(h, 127L * DAY, () -> h.assertValueEqual(MiteSky.forecast(h.getLevel()), 0, "blue moon forecast"));
            h.succeed();
        });

        // M-1: the Underworld fog layers, wired on a level without a clock of its own like the Underworld (the GameTest server
        // only creates the vanilla levels, so the Nether stands in), follow the Overworld days: densest on the blood moon day 32,
        // gone 16 days later.
        MiteGameTests.add("lunar_underworld_fog_in_dimension", 20, h -> {
            var types = h.getLevel().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
            h.assertTrue(types.getValueOrThrow(dev.mite.dimension.UnderworldContent.TYPE).defaultClock().isEmpty(), "the Underworld has a clock");
            ServerLevel clockless = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(clockless.dimensionType().defaultClock().isEmpty(), "the Nether has a clock");
            var builder = net.minecraft.world.attribute.EnvironmentAttributeSystem.builder();
            MiteSky.underworldLayers(builder, clockless);
            var system = builder.build();
            float[] fog = new float[2];
            for (int i = 0; i < 2; i++) {
                int slot = i;
                onTicks(h, (31L + 16 * i) * DAY + 1, () -> {
                    system.invalidateTickCache();
                    fog[slot] = system.getValue(EnvironmentAttributes.FOG_END_DISTANCE, Vec3.ZERO, null);
                });
            }
            h.assertTrue(fog[0] < 49 && fog[1] > fog[0] + 20, "Underworld fog end: " + fog[0] + " on day 32, " + fog[1] + " on day 48");
            h.succeed();
        });

        // N-3 (EF-LUN-02/03): storm by day and clear night on a blood moon, clear blue moon day, other days untouched.
        MiteGameTests.add("lunar_weather_rules", 20, h -> {
            WeatherData w = new WeatherData(0, 5000, 5000, false, false);
            Lunar.moonWeather(Moon.BLOOD, 3000, w);
            h.assertTrue(w.isRaining() && w.isThundering() && w.getRainTime() == 9000, "no storm on a blood moon morning");
            Lunar.moonWeather(Moon.BLOOD, 18000, w);
            h.assertTrue(!w.isRaining() && !w.isThundering() && w.getClearWeatherTime() == 6000, "storm on a blood moon night");
            // vanilla's thunder flag runs on its own timer: on without rain, no storm shows
            w = new WeatherData(0, 5000, 5000, false, true);
            Lunar.moonWeather(Moon.BLOOD, 3000, w);
            h.assertTrue(w.isRaining() && w.isThundering(), "no storm on a blood moon morning with the thunder flag alone");
            w = new WeatherData(0, 100, 100, true, true);
            Lunar.moonWeather(Moon.BLUE, 6000, w);
            h.assertTrue(!w.isRaining() && w.getClearWeatherTime() == 18000, "rain on a blue moon day");
            // a dry day is kept dry: vanilla's rain timer may not run out before the end of the day
            for (Moon moon : new Moon[] {Moon.BLUE, Moon.BLOOD}) {
                w = new WeatherData(100, 5000, 5000, false, false);
                Lunar.moonWeather(moon, 18000, w);
                h.assertTrue(!w.isRaining() && w.getClearWeatherTime() == 6000, "clear time on a dry " + moon + " evening: " + w.getClearWeatherTime());
            }
            w = new WeatherData(9000, 5000, 5000, false, false);
            Lunar.moonWeather(Moon.BLUE, 18000, w);
            h.assertValueEqual(w.getClearWeatherTime(), 9000, "longer clear time shortened");
            w = new WeatherData(0, 100, 100, true, false);
            Lunar.moonWeather(Moon.NORMAL, 6000, w);
            h.assertTrue(w.isRaining() && w.getRainTime() == 100, "weather changed on a normal day");
            h.succeed();
        });

        // dom-N-08/09: blue moon bonuses only during its night, and taming is easier, not guaranteed.
        MiteGameTests.add("lunar_blue_moon_night", 20, h -> {
            onDay(h, 128, () -> h.assertFalse(Lunar.isBlueMoonNight(h.getLevel()), "blue moon night at noon"));
            onTicks(h, 127L * DAY + 18000, () -> h.assertTrue(Lunar.isBlueMoonNight(h.getLevel()), "blue moon night at midnight"));
            onTicks(h, 126L * DAY + 18000, () -> h.assertFalse(Lunar.isBlueMoonNight(h.getLevel()), "blue moon night on day 127"));
            h.assertTrue(Lunar.BLUE_MOON_TAME > 0 && Lunar.BLUE_MOON_TAME < 1, "taming guaranteed");
            h.succeed();
        });

        // EF-LUN-03 (changelog l.465): on a blue moon night half the bones tame a wolf at once (the others fall back to the vanilla
        // roll, not posted here); a Hellhound never.
        MiteGameTests.add("lunar_blue_moon_taming", 20, h -> {
            ServerPlayer p = dev.mite.metabolism.MetabolismTests.player(h);
            Wolf wolf = h.spawn(EntityTypes.WOLF, 2, 1, 2);
            Wolf hellhound = h.spawn(MobContent.HELLHOUND.get(), 4, 1, 4);
            Cow prey = h.spawn(EntityTypes.COW, 1, 1, 4);
            int[] tamed = {0, 0, 0};
            onTicks(h, 127L * DAY + 18000, () -> {
                for (int i = 0; i < 400; i++) {
                    wolf.setTarget(prey); // a hunting wolf drops its prey once tamed, as in vanilla
                    for (Wolf target : new Wolf[] {wolf, hellhound}) {
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
                        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, target, target.position()));
                    }
                    if (wolf.isTame()) {
                        tamed[0]++;
                        if (wolf.getTarget() != null) tamed[2]++;
                        wolf.setTame(false, true);
                    }
                    if (hellhound.isTame()) tamed[1]++;
                }
            });
            h.assertTrue(tamed[0] > 140 && tamed[0] < 260, "wolf tamed " + tamed[0] + "/400, expected ~" + Lunar.BLUE_MOON_TAME * 400);
            h.assertValueEqual(tamed[1], 0, "hellhound tamed");
            h.assertValueEqual(tamed[2], 0, "tamed wolves still on their prey");
            h.succeed();
        });

        // N-3: the moon weather follows the advance_weather game rule (a frozen weather stays frozen).
        MiteGameTests.add("lunar_weather_game_rule", 20, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            WeatherData w = level.getWeatherData();
            int clear = w.getClearWeatherTime(), rain = w.getRainTime(), thunder = w.getThunderTime();
            boolean raining = w.isRaining(), thundering = w.isThundering(), advance = level.getGameRules().get(GameRules.ADVANCE_WEATHER);
            boolean[] storm = new boolean[2];
            try {
                for (int on = 0; on < 2; on++) {
                    level.getGameRules().set(GameRules.ADVANCE_WEATHER, on == 1, server);
                    w.setRaining(false);
                    w.setThundering(false);
                    onDay(h, 32, 3000, () -> Lunar.updateWeather(level));
                    storm[on] = w.isRaining() && w.isThundering();
                }
            } finally {
                level.getGameRules().set(GameRules.ADVANCE_WEATHER, advance, server);
                w.setClearWeatherTime(clear);
                w.setRainTime(rain);
                w.setThunderTime(thunder);
                w.setRaining(raining);
                w.setThundering(thundering);
            }
            h.assertFalse(storm[0], "blood moon storm with advance_weather off");
            h.assertTrue(storm[1], "no blood moon storm with advance_weather on");
            h.succeed();
        });

        MiteGameTests.add("lunar_blood_moon_frenzy", 20, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
            Cow cow = h.spawn(EntityTypes.COW, 4, 1, 4);
            double zombieBase = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), cowBase = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double damageBase = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
            onDay(h, 32, () -> {
                Lunar.updateFrenzy(zombie);
                Lunar.updateFrenzy(cow);
            });
            h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - zombieBase * 1.2) < 1e-6, "zombie speed x1.2 on a blood moon");
            // N-1 (changelog l.586): extra melee damage too.
            h.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) - damageBase * (1 + Lunar.FRENZY_DAMAGE)) < 1e-6,
                    "zombie damage " + zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) + " on a blood moon, base " + damageBase);
            h.assertValueEqual(cow.getAttributeValue(Attributes.MOVEMENT_SPEED), cowBase, "cow speed");
            onDay(h, 33, () -> Lunar.updateFrenzy(zombie));
            h.assertValueEqual(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED), zombieBase, "zombie speed after the blood moon");
            h.assertValueEqual(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), damageBase, "zombie damage after the blood moon");
            h.succeed();
        });

        // EF-LUN-03 (R154): on blue moon day 128 no natural hostile spawn under the sky at night; caves, daytime, spawners,
        // passive mobs and day 127 are unaffected.
        MiteGameTests.add("lunar_blue_moon_spawns", 20, h -> {
            ServerLevel level = h.getLevel();
            onDay(h, 128, 18000, () -> {
                h.assertTrue(Lunar.blueMoonForbids(level, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, true), "zombie spawns under the blue moon");
                h.assertFalse(Lunar.blueMoonForbids(level, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, false), "zombie blocked in a cave");
                h.assertFalse(Lunar.blueMoonForbids(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, true), "spawner blocked");
                h.assertFalse(Lunar.blueMoonForbids(level, EntityTypes.COW, EntitySpawnReason.NATURAL, true), "cow blocked");
            });
            onDay(h, 128, () -> h.assertFalse(Lunar.blueMoonForbids(level, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, true), "zombie blocked by day"));
            onDay(h, 127, 18000, () -> h.assertFalse(Lunar.blueMoonForbids(level, EntityTypes.ZOMBIE, EntitySpawnReason.NATURAL, true), "zombie blocked on day 127"));
            h.succeed();
        });

        MiteGameTests.add("lunar_structure_placement", 20, h -> {
            h.assertFalse(MiteStructures.farFromSpawn(0, 0), "chunk at spawn accepted");
            h.assertFalse(MiteStructures.farFromSpawn(-120, 0), "chunk 1912 blocks from spawn accepted");
            h.assertTrue(MiteStructures.farFromSpawn(125, -10), "chunk 2000+ blocks from spawn rejected");
            var sets = h.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
            for (String temple : new String[] {"desert_pyramids", "jungle_temples", "swamp_huts", "igloos"}) {
                h.assertTrue(sets.getValue(Identifier.withDefaultNamespace(temple)).placement() instanceof MiteStructures.GatedSpread, temple + " not gated");
            }
            // L-2 (changelog l.718): huts, temples and villages twice as far apart as vanilla (32/8, villages 34/8).
            Map.of("desert_pyramids", 32, "jungle_temples", 32, "swamp_huts", 32, "villages", 34).forEach((set, vanilla) -> {
                var spread = (MiteStructures.GatedSpread) sets.getValue(Identifier.withDefaultNamespace(set)).placement();
                h.assertTrue(spread.spacing() == vanilla * MiteStructures.SPREAD_FACTOR && spread.separation() == 8 * MiteStructures.SPREAD_FACTOR,
                        set + " spacing " + spread.spacing() + "/" + spread.separation());
            });
            var strongholds = sets.getValue(Identifier.withDefaultNamespace("strongholds")).placement();
            h.assertTrue(strongholds instanceof MiteStructures.ScaledRings rings && rings.distance() == 15 * 16, "stronghold distance not x16");
            h.succeed();
        });
    }
}
