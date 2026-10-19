package dev.mite.lunar;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.MoonCalendar;
import dev.mite.core.MoonCalendar.Moon;
import dev.mite.entity.ai.AiContent;
import dev.mite.entity.mob.MobContent;
import dev.mite.metabolism.BiomeClimate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Moon of a level (N01, EF-LUN-01), blood moon (N02, EF-LUN-02) and blue moon (N03, EF-LUN-03) effects. Nothing is stored
 * or sent: the day comes from the level's default clock, which vanilla syncs, and the periods from the synced config, so {@link #moon} works on both sides.
 */
public final class Lunar {
    private Lunar() {}

    static final Identifier FRENZY = MiteMod.id("blood_moon_frenzy");
    /** EF-LUN-02: frenzied undead move 20 % faster. */
    static final double FRENZY_SPEED = 0.2;
    /** Changelog l.586 (R127): frenzied mobs also inflict extra melee damage, +25 % [INFÉRÉ: amount]. */
    static final double FRENZY_DAMAGE = 0.25;
    /** EF-LUN-02: frenzied skeletons shoot 33 % faster, so their bow interval is divided by 1.33. */
    static final float FRENZY_SHOT_INTERVAL = 0.75f;
    private static final int HALF_DAY = MoonCalendar.TICKS_PER_DAY / 2;

    /** Moon of the current day. Dimensions without a clock (Nether) always see day 1, a normal moon. */
    public static Moon moon(Level level) {
        return moon(MoonCalendar.day(level.getDefaultClockTime()));
    }

    /** Moon of day {@code d} with the configured periods. */
    public static Moon moon(long day) {
        if (!MiteConfig.SPEC.isLoaded()) return Moon.NORMAL;
        return MoonCalendar.moon(day, MiteConfig.BLOOD_MOON_PERIOD.getAsInt(), MiteConfig.BLUE_MOON_PERIOD.getAsInt());
    }

    /** Night: the moon is up [INFÉRÉ: 13 000 to 23 000 ticks, vanilla monsters' night]. */
    static final int NIGHT_START = 13000, NIGHT_END = 23000;

    /** changelog l.316/465/830 "nights of blue moons": the night ({@link #NIGHT_START}-{@link #NIGHT_END}) of a blue moon day
     *  (taming, fishing, hostile spawns). */
    public static boolean isBlueMoonNight(Level level) {
        return moon(level) == Moon.BLUE && isNight(level);
    }

    public static boolean isNight(Level level) {
        int time = MoonCalendar.timeOfDay(level.getDefaultClockTime());
        return time >= NIGHT_START && time < NIGHT_END;
    }

    public static boolean isBloodMoon(Level level) {
        return moon(level) == Moon.BLOOD;
    }

    /**
     * EF-LUN-02 CA-1: undead of {@code hardscrabble:frenzy_undead} (EF-IA-07: not the drowned) get transient +20 % speed and
     * {@link #FRENZY_DAMAGE} melee damage modifiers during a blood moon (changelog l.586), removed afterwards. Skeletons also shoot 33 % faster (bow interval x 0.75), restored by {@code reassessWeaponGoal} after.
     * ponytail: a weapon swap during the frenzy resets the interval to vanilla until the next blood moon.
     */
    public static void updateFrenzy(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || !entity.is(EntityTypeTags.UNDEAD) || !entity.is(AiContent.FRENZY_UNDEAD)) return;
        boolean on = isBloodMoon(entity.level());
        AbstractSkeleton skeleton = entity instanceof AbstractSkeleton s ? s : null;
        AttributeInstance damage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (on && !speed.hasModifier(FRENZY)) {
            speed.addTransientModifier(new AttributeModifier(FRENZY, FRENZY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (damage != null) damage.addOrUpdateTransientModifier(new AttributeModifier(FRENZY, FRENZY_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (skeleton != null) for (WrappedGoal w : skeleton.goalSelector.getAvailableGoals()) {
                if (w.getGoal() instanceof RangedBowAttackGoal<?> bow) bow.setMinAttackInterval(Math.round(bow.attackIntervalMin * FRENZY_SHOT_INTERVAL));
            }
        } else if (!on && speed.hasModifier(FRENZY)) {
            speed.removeModifier(FRENZY);
            if (damage != null) damage.removeModifier(FRENZY);
            if (skeleton != null) skeleton.reassessWeaponGoal();
        }
    }

    /** EF-MOB-02: biomes where Wights reach the surface on a blood moon (frozen biomes). */
    public static final TagKey<Biome> WIGHT_SPAWN = TagKey.create(Registries.BIOME, MiteMod.id("wight_spawn"));

    public static void biomeTags(Function<TagKey<Biome>, TagAppender<Biome>> tag) {
        tag.apply(WIGHT_SPAWN).addTag(BiomeClimate.FROZEN);
    }

    /** True when a blood moon lets this mob spawn here despite its depth threshold (EF-LUN-02, Overworld only). */
    public static boolean bloodMoonSpawn(Level level, EntityType<?> type, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD && isBloodMoon(level) && bloodMoonSpawn(type, level.getBiome(pos), level.canSeeSky(pos));
    }

    /**
     * EF-MOB-01/02/03/05/17/20 on a blood moon: Ghoul on any surface, Wight on the surface of a {@link #WIGHT_SPAWN} biome, Shadow
     * on a desert surface; Revenant, Bone Lord, Vampire Bat and Nightwing anywhere. Light, difficulty and weights stay the vanilla monster rules.
     */
    public static boolean bloodMoonSpawn(EntityType<?> type, Holder<Biome> biome, boolean sky) {
        if (type == MobContent.REVENANT.get() || type == MobContent.BONE_LORD.get() || type == MobContent.VAMPIRE_BAT.get() || type == MobContent.NIGHTWING.get()) return true;
        return sky && (type == MobContent.GHOUL.get() || type == MobContent.WIGHT.get() && biome.is(WIGHT_SPAWN)
                || type == MobContent.SHADOW.get() && biome.is(Tags.Biomes.IS_DESERT));
    }

    /**
     * EF-LUN-03 (changelog R154): no natural hostile spawn wherever the blue moon shines, i.e. under the open sky at night.
     * Caves and daytime keep their spawns.
     */
    public static boolean blueMoonForbids(Level level, EntityType<?> type, EntitySpawnReason reason, boolean sky) {
        return sky && reason == EntitySpawnReason.NATURAL && type.getCategory() == MobCategory.MONSTER && isBlueMoonNight(level);
    }

    /**
     * EF-LUN-02: thunderstorm by day, clear sky at night during a blood moon. EF-LUN-03: clear sky all blue moon day.
     * Respects the advance_weather game rule.
     */
    static void updateWeather(ServerLevel level) {
        if (!level.canHaveWeather() || !level.getGameRules().get(GameRules.ADVANCE_WEATHER)) return;
        moonWeather(moon(level), MoonCalendar.timeOfDay(level.getDefaultClockTime()), level.getWeatherData());
    }

    /** The weather rules of {@link #updateWeather} at {@code time} of a {@code moon} day, applied to {@code weather}. */
    static void moonWeather(Moon moon, int time, WeatherData weather) {
        if (moon == Moon.BLUE || moon == Moon.BLOOD && time >= HALF_DAY) {
            clear(weather, MoonCalendar.TICKS_PER_DAY - time);
        } else if (moon == Moon.BLOOD && !(weather.isRaining() && weather.isThundering())) {
            // The visible storm: vanilla runs the thunder flag on its own timer, so it may be on without rain.
            set(weather, 0, HALF_DAY - time, true, true);
        }
    }

    /** Clear until {@code ticks} from now: stops the rain, and keeps it from starting before then. */
    private static void clear(WeatherData weather, int ticks) {
        if (weather.isRaining()) set(weather, ticks, 0, false, false);
        else if (weather.getClearWeatherTime() < ticks) weather.setClearWeatherTime(ticks);
    }

    /** Same as {@code MinecraftServer.setWeatherParameters}, on any weather data. */
    private static void set(WeatherData weather, int clear, int rain, boolean raining, boolean thundering) {
        weather.setClearWeatherTime(clear);
        weather.setRainTime(rain);
        weather.setThunderTime(rain);
        weather.setRaining(raining);
        weather.setThundering(thundering);
    }

    /** EF-LUN-02, changelog l.524 "increased lightning frequency during blood moon storms": twice the vanilla rate around players [INFÉRÉ: x2]. */
    public static boolean isBloodMoonStorm(Level level) {
        return isBloodMoon(level) && level.isThundering();
    }

    /**
     * One extra vanilla roll (1 in 100 000 per chunk and tick) for the 17x17 chunks around a player, as one roll of 289 in 100 000.
     * ponytail: players close together add their rolls on shared chunks; lightning rods do not attract these bolts.
     */
    static void extraLightning(ServerLevel level, BlockPos around) {
        var random = level.getRandom();
        if (random.nextInt(100000) >= 17 * 17) return;
        int x = around.getX() + random.nextInt(17 * 16) - 8 * 16, z = around.getZ() + random.nextInt(17 * 16) - 8 * 16;
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        if (!level.isRainingAt(pos)) return;
        var bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt == null) return;
        bolt.snapTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(bolt);
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Post e) -> {
            if (e.getEntity() instanceof Mob mob && mob.tickCount % 20 == 0 && !mob.level().isClientSide()) updateFrenzy(mob);
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
            if (level.getGameTime() % 20 == 0) updateWeather(level);
            if (level.getGameTime() % 100 == 0) {
                var forecast = new MiteSky.Forecast((byte) MiteSky.forecast(level));
                level.players().forEach(p -> sendForecast(p, forecast));
            }
            if (isBloodMoonStorm(level)) level.players().forEach(p -> extraLightning(level, p.blockPosition()));
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToClient(MiteSky.Forecast.TYPE, MiteSky.Forecast.STREAM_CODEC, (p, context) -> MiteSky.forecast = p.weather()));
        NeoForge.EVENT_BUS.addListener((MobSpawnEvent.SpawnPlacementCheck e) -> {
            Level level = e.getLevel().getLevel();
            if (blueMoonForbids(level, e.getEntityType(), e.getSpawnType(), e.getLevel().canSeeSky(e.getPos()))) {
                e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
            } else if (!e.getDefaultResult() && e.getSpawnType() == EntitySpawnReason.NATURAL && bloodMoonSpawn(level, e.getEntityType(), e.getPos())
                    && Monster.checkMonsterSpawnRules((EntityType<? extends Mob>) e.getEntityType(), e.getLevel(), e.getSpawnType(), e.getPos(), e.getRandom())) {
                e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
            }
        });
        NeoForge.EVENT_BUS.addListener(Lunar::blueMoonTaming);
        // The client's forecast is reset on logout: a player arriving in the Overworld gets it at once, not up to 5 s later.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> forecastOnArrival(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> forecastOnArrival(e.getEntity()));
    }

    private static void forecastOnArrival(Player player) {
        if (player instanceof ServerPlayer p && p.level().dimension() == Level.OVERWORLD) sendForecast(p, new MiteSky.Forecast((byte) MiteSky.forecast(p.level())));
    }

    private static void sendForecast(ServerPlayer p, MiteSky.Forecast forecast) {
        if (p.connection != null && p.connection.hasChannel(MiteSky.Forecast.TYPE)) PacketDistributor.sendToPlayer(p, forecast);
    }

    /**
     * EF-LUN-03, changelog l.465 (wolves and dire wolves): easier to tame during the night of a blue moon. Half of the bones tame at once,
     * the other half fall back to the vanilla 1 in 3 roll: 2 in 3 overall [INFÉRÉ].
     */
    static final float BLUE_MOON_TAME = 0.5F;

    private static void blueMoonTaming(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Wolf wolf) || wolf.getType() != EntityTypes.WOLF && wolf.getType() != MobContent.DIRE_WOLF.get() || wolf.isTame() || wolf.isAngry() || !e.getItemStack().is(Items.BONE)
                || e.getLevel().isClientSide() || !isBlueMoonNight(e.getLevel()) || e.getLevel().getRandom().nextFloat() >= BLUE_MOON_TAME
                || EventHooks.onAnimalTame(wolf, e.getEntity())) return;
        e.getItemStack().consume(1, e.getEntity());
        wolf.tame(e.getEntity());
        wolf.getNavigation().stop();
        wolf.setTarget(null);
        wolf.setOrderedToSit(true);
        e.getLevel().broadcastEntityEvent(wolf, (byte) 7);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS_SERVER);
    }
}
