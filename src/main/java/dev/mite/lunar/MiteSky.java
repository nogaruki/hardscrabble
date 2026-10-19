package dev.mite.lunar;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.MoonCalendar;
import dev.mite.core.MoonCalendar.Moon;
import dev.mite.dimension.UnderworldContent;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.EasingType;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.FloatModifier;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * Sky and weather rendering data (EF-LUN-04, EF-LUN-05, EF-DIM-01), shared by both sides. The moon phases and the night light
 * come from the {@code minecraft:moon} timeline override; the moon glow, sunrise colours and fogs are environment attribute
 * layers added to every level by EnvironmentAttributeSystemMixin (MX-14b). All of it is computed from the synced clock and
 * config, except the weather forecast, which the server sends ({@link Forecast}).
 */
public final class MiteSky {
    private MiteSky() {}

    private static final int DAY = MoonCalendar.TICKS_PER_DAY;

    // ---- Timeline (data): phases and night light ----

    /**
     * N01 (dom-N-03): the full moon falls on the days {@code d % 8 == 0} (MITE day 32 is a full moon), so the blood, blue and
     * harvest moons and the moon dog are full moons. Vanilla starts the cycle on day 1 with a full moon: every phase moves one
     * day later. CL R133: the outdoor night light follows the phase, from x1 (full moon) to x0.25 (new moon) [INFÉRÉ: factor].
     */
    public static void moonTimeline(BootstrapContext<Timeline> c) {
        c.register(Timelines.MOON, Timeline.builder(c.lookup(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD))
                .setPeriodTicks(DAY * MoonPhase.COUNT)
                .addTrack(EnvironmentAttributes.MOON_PHASE, t -> {
                    for (int slot = 0; slot < MoonPhase.COUNT; slot++) t.addKeyframe(slot * DAY, phase(slot));
                })
                .addModifierTrack(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE, FloatModifier.MAXIMUM, t -> {
                    t.setEasing(EasingType.CONSTANT);
                    for (int slot = 0; slot < MoonPhase.COUNT; slot++) t.addKeyframe(slot * DAY, brightness(slot) * 0.5F);
                })
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_LEVEL, FloatModifier.MULTIPLY, t -> nightLight(t::addKeyframe))
                .addModifierTrack(EnvironmentAttributes.SKY_LIGHT_FACTOR, FloatModifier.MULTIPLY, t -> nightLight(t::addKeyframe))
                .build());
    }

    /** Phase shown on the days {@code (d - 1) % 8 == slot}. */
    static MoonPhase phase(int slot) {
        return MoonPhase.values()[(slot + 1) % MoonPhase.COUNT];
    }

    private static float brightness(int slot) {
        return DimensionType.MOON_BRIGHTNESS_PER_PHASE[phase(slot).index()];
    }

    /** Night light multiplier of a slot: 1 by day, eased in and out with the vanilla dusk and dawn keyframes (11867-13670, 22330-24133). */
    public static float nightLight(int slot) {
        return 0.25F + 0.75F * brightness(slot);
    }

    private static void nightLight(BiConsumer<Integer, Float> keyframe) {
        for (int slot = 0; slot < MoonPhase.COUNT; slot++) {
            int start = slot * DAY;
            float night = nightLight(slot);
            for (int[] k : new int[][] {{133, 0}, {11867, 0}, {13670, 1}, {22330, 1}}) keyframe.accept(start + k[0], k[1] == 1 ? night : 1F);
        }
    }

    // ---- Attribute layers (code): glow, sunrise, fog ----

    /** EF-LUN-04: colour of the special moons, for the moon itself and its glow on the land (CL R133 "redish glow"). */
    public static Vector3fc moonTint(Moon moon) {
        return switch (moon) {
            case BLOOD -> new Vector3f(1.0F, 0.25F, 0.2F);
            case BLUE -> new Vector3f(0.45F, 0.6F, 1.0F);
            case HARVEST -> new Vector3f(1.0F, 0.85F, 0.45F);
            default -> null;
        };
    }

    /** Strength of the moon glow on the night sky light colour [INFÉRÉ]. */
    static final float GLOW = 0.6F;
    /** Weather announced by the next sunrise (CL R132): 0 clear, 1 rain, 2 storm. Client copy, set by {@link Forecast}. */
    public static volatile int forecast;
    private static final Vector3fc[] SUNRISE_TINT = {new Vector3f(1, 1, 1), new Vector3f(0.75F, 0.78F, 0.9F), new Vector3f(1.0F, 0.4F, 0.35F)};
    /** CL R189: thick morning fog on 1 day in 8, not on blood or blue moon days [INFÉRÉ: frequency, hours and distance]. */
    static final float MORNING_FOG_END = 24;

    /** Called by EnvironmentAttributeSystemMixin after the vanilla layers of a level. */
    public static void addLayers(EnvironmentAttributeSystem.Builder b, Level level) {
        if (level.dimension() == Level.OVERWORLD) {
            b.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (c, id) -> glow(c, Lunar.moon(level), level.getDefaultClockTime()));
            b.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (c, id) -> sunrise(c, level.getDefaultClockTime(), level.getRainLevel(1)));
            b.addTimeBasedLayer(EnvironmentAttributes.FOG_START_DISTANCE, (d, id) -> Mth.lerp(morningFog(level.getDefaultClockTime()), d, 0F));
            b.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (d, id) -> Mth.lerp(morningFog(level.getDefaultClockTime()), d, MORNING_FOG_END));
        } else if (level.dimension() == UnderworldContent.LEVEL) {
            underworldLayers(b, level);
        }
    }

    /** EF-DIM-01 fog layers. M-1: the Underworld has no clock of its own (its default clock time reads 0): the cycle follows the Overworld days. */
    public static void underworldLayers(EnvironmentAttributeSystem.Builder b, Level level) {
        b.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (c, id) -> {
            float gray = 0.18F + 0.22F * underworldFog(level.getOverworldClockTime());
            return new Vector3f(gray, gray, gray);
        });
        b.addTimeBasedLayer(EnvironmentAttributes.FOG_END_DISTANCE, (d, id) -> Mth.lerp(underworldFog(level.getOverworldClockTime()), d, 48F));
    }

    /** 0 by day, 1 at night, eased over 1500 ticks at dusk (12000) and dawn (24000). */
    static float night(long ticks) {
        int t = MoonCalendar.timeOfDay(ticks);
        return t < 12000 ? 0 : t < 13500 ? (t - 12000) / 1500F : t < 22500 ? 1 : (DAY - t) / 1500F;
    }

    static Vector3fc glow(Vector3fc color, Moon moon, long ticks) {
        Vector3fc tint = moonTint(moon);
        return tint == null ? color : new Vector3f(color).lerp(tint, GLOW * night(ticks));
    }

    /**
     * CL R132: the sunrise colour announces the weather of the day (clear, rain, storm); sunsets vary from day to day; neither
     * shows under an overcast sky.
     */
    static Vector4fc sunrise(Vector4fc color, long ticks, float rain) {
        if (color.w() <= 0) return color;
        int t = MoonCalendar.timeOfDay(ticks);
        Vector3fc tint;
        if (t >= 18000 || t < 6000) {
            tint = SUNRISE_TINT[Mth.clamp(forecast, 0, 2)];
        } else {
            int h = Mth.murmurHash3Mixer((int) MoonCalendar.day(ticks));
            tint = new Vector3f(1, 0.75F + (h & 0xFF) / 1020F, 0.6F + (h >>> 8 & 0xFF) / 637F);
        }
        return new Vector4f(color.x() * tint.x(), color.y() * tint.y(), color.z() * tint.z(), color.w() * (1 - rain));
    }

    /** CL R189 fog events: intensity 0..1, from 23000 (the day before) to 5000, thickest 1000-3000. */
    public static float morningFog(long ticks) {
        long shifted = ticks + 1000;
        long day = MoonCalendar.day(shifted);
        int t = MoonCalendar.timeOfDay(shifted);
        if (t >= 6000 || !fogDay(day)) return 0;
        return t < 2000 ? t / 2000F : t < 4000 ? 1 : (6000 - t) / 2000F;
    }

    static boolean fogDay(long day) {
        Moon moon = Lunar.moon(day);
        return moon != Moon.BLOOD && moon != Moon.BLUE && (Mth.murmurHash3Mixer((int) day) & 7) == 0;
    }

    /** EF-DIM-01: Underworld fog density 0..1 on a cycle of {@code blood_moon_period} days, densest on the blood moon day. */
    public static float underworldFog(long ticks) {
        int period = MiteConfig.SPEC.isLoaded() ? MiteConfig.BLOOD_MOON_PERIOD.getAsInt() : 32;
        double days = (double) ticks / DAY + 1;
        return (float) (0.5 + 0.5 * Math.cos(2 * Math.PI * days / period));
    }

    // ---- Forecast (server -> client) ----

    /**
     * CL R132: weather of the coming daylight (until the next noon): 0 clear, 1 rain, 2 storm. Blood moon days are stormy, blue
     * moon days clear (EF-LUN-02/03); otherwise the vanilla weather timers are read ahead (a second change before noon is ignored).
     */
    public static int forecast(ServerLevel level) {
        long ticks = level.getDefaultClockTime();
        int untilNoon = MoonCalendar.timeOfDay(6000 - ticks);
        Moon moon = Lunar.moon(MoonCalendar.day(ticks + untilNoon));
        if (moon == Moon.BLOOD) return 2;
        if (moon == Moon.BLUE) return 0;
        WeatherData w = level.getWeatherData();
        if (w.getClearWeatherTime() > untilNoon) return 0;
        boolean rain = w.isRaining() != (w.getRainTime() <= untilNoon);
        boolean thunder = w.isThundering() != (w.getThunderTime() <= untilNoon);
        return !rain ? 0 : thunder ? 2 : 1;
    }

    public record Forecast(byte weather) implements CustomPacketPayload {
        public static final Type<Forecast> TYPE = new Type<>(MiteMod.id("forecast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Forecast> STREAM_CODEC = ByteBufCodecs.BYTE.<RegistryFriendlyByteBuf>cast()
                .map(Forecast::new, Forecast::weather);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
