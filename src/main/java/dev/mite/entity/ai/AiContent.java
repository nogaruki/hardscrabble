package dev.mite.entity.ai;

import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.entity.mob.MobContent;
import dev.mite.harvest.ToolStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;

/** Vanilla mob AI and spawns: digging (K01), detection and despawn (K02), depth spawns (K12), modern variants (K14), silver (G06). Side rules in {@link MobRules}. */
public final class AiContent {
    private AiContent() {}

    /** Blocks a digger may break (EF-IA-01). */
    public static final TagKey<Block> ZOMBIE_DIGGABLE = TagKey.create(Registries.BLOCK, MiteMod.id("zombie_diggable"));
    /** Entity types that get {@link DigThroughGoal} (EF-IA-07). */
    public static final TagKey<EntityType<?>> DIGGER = TagKey.create(Registries.ENTITY_TYPE, MiteMod.id("digger"));
    /** Undead that turn frenzied under the blood moon (EF-IA-07); read by the lunar domain. */
    public static final TagKey<EntityType<?>> FRENZY_UNDEAD = TagKey.create(Registries.ENTITY_TYPE, MiteMod.id("frenzy_undead"));

    /** EF-IA-06: highest spawn Y in R196 coordinates (y <= maxY), converted at runtime by {@link Formulas#mapDepth}. */
    public record SpawnDepth(int maxY) {
        public static final Codec<SpawnDepth> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_y").forGetter(SpawnDepth::maxY)).apply(i, SpawnDepth::new));
    }

    public static final DataMapType<EntityType<?>, SpawnDepth> SPAWN_DEPTH = DataMapType.builder(MiteMod.id("spawn_depth"), Registries.ENTITY_TYPE, SpawnDepth.CODEC).build();

    /** EF-IA-02: follow range of zombies. Modern variants keep their vanilla stats (EF-IA-07). */
    static final double FOLLOW_RANGE = 40;
    /** Beyond this distance vanilla may despawn a mob (MobCategory#getNoDespawnDistance), so only then is sight checked. */
    private static final int NO_DESPAWN_DISTANCE = 32;

    // ---- K01 / K14: diggers ----

    /**
     * EF-IA-01 CA-1: a walled-in digger keeps its target while it digs. Vanilla target goals drop an unseen target after 60
     * ticks, before the 150-tick dirt cooldown [INFÉRÉ: 800, above the 720 ticks of stone with a pickaxe].
     */
    public static final int DIGGER_UNSEEN_MEMORY = 800;

    /** A digger with its own goal (the Earth Elemental) keeps it, but every digger gets the long target memory. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !mob.is(DIGGER)) return;
        if (digGoal(mob) == null) mob.goalSelector.addGoal(1, new DigThroughGoal(mob));
        for (WrappedGoal w : mob.targetSelector.getAvailableGoals()) if (w.getGoal() instanceof TargetGoal t) t.setUnseenMemoryTicks(DIGGER_UNSEEN_MEMORY);
    }

    static @Nullable DigThroughGoal digGoal(Mob mob) {
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) if (w.getGoal() instanceof DigThroughGoal g) return g;
        return null;
    }

    // ---- K02: detection and despawn ----

    private static void modifyAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityTypes.ZOMBIE, Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /**
     * EF-IA-02: no despawn while the mob digs, breaks a door, or while it and the nearest player see each other. Summoned
     * minions (witch wolves, EF-IA-08) despawn like monsters although wolves are animals: beyond 128 blocks, or 1 in 800 beyond 32 once idle 600 ticks.
     */
    private static void onDespawn(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        // Vanilla rules first: persistent mobs never despawn, Peaceful purges hostiles (both checks come after the event in Mob#checkDespawn).
        if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()
                || mob.level().getDifficulty() == Difficulty.PEACEFUL && !mob.getType().isAllowedInPeaceful()) return;
        Player player = mob.level().getNearestPlayer(mob, -1);
        if (player == null || player.distanceToSqr(mob) <= NO_DESPAWN_DISTANCE * NO_DESPAWN_DISTANCE) return;
        if (keepsAlive(mob, player)) event.setResult(MobDespawnEvent.Result.DENY);
        else if (SummonGoal.summoned(mob) && (player.distanceToSqr(mob) > 128 * 128 || mob.getNoActionTime() > 600 && mob.getRandom().nextInt(800) == 0))
            event.setResult(MobDespawnEvent.Result.ALLOW);
    }

    /** ponytail: nearest player only (the one vanilla despawns against); scan all players if a farther watcher matters. */
    static boolean keepsAlive(Mob mob, Player player) {
        DigThroughGoal dig = digGoal(mob);
        return dig != null && dig.isDigging() || breaksDoor(mob) || !player.isSpectator() && mob.getSensing().hasLineOfSight(player);
    }

    /** EF-IA-02 (changelog: zombies breaking doors do not despawn). */
    static boolean breaksDoor(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.isRunning() && w.getGoal() instanceof BreakDoorGoal);
    }

    /** EF-IA-08: summoned minions (witch wolves) cannot be tamed. */
    private static void onTame(AnimalTameEvent event) {
        if (SummonGoal.summoned(event.getAnimal())) event.setCanceled(true);
    }

    // ---- K12: depth spawns ----

    private static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return;
        Mob mob = event.getEntity();
        Level level = event.getLevel().getLevel();
        if (!depthAllows(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()).getData(SPAWN_DEPTH), level.dimension(), event.getY())
                && !(reason == EntitySpawnReason.NATURAL && dev.mite.lunar.Lunar.bloodMoonSpawn(level, mob.getType(), mob.blockPosition()))) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        } else if (mob.getType() == EntityTypes.CREEPER && level.isBrightOutside() && level.canSeeSky(mob.blockPosition()) && mob.getRandom().nextInt(4) != 0) {
            // EF-IA-06: 3 creepers in 4 refused outdoors by day. The light limit already stops most of them: this rule bites on
            // the dark days of thunderstorms (K-8), and is kept for that.
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** EF-IA-06: the depth filter only applies in the Overworld. Blood moon surface spawns are exempt (EF-LUN-02, {@code Lunar.bloodMoonSpawn}). */
    static boolean depthAllows(@Nullable SpawnDepth depth, ResourceKey<Level> dimension, double y) {
        return depth == null || dimension != Level.OVERWORLD || y <= Formulas.mapDepth(depth.maxY());
    }

    // ---- G06: silver ----

    /** EF-CBT-06: silver weapons and arrows deal +{@code silver_undead_bonus} to undead and immaterial mobs. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.is(EntityTypeTags.UNDEAD) && !target.is(MobContent.IMMATERIAL)) return;
        ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon != null && ToolStats.of(weapon).filter(t -> t.material() == Material.SILVER).isPresent()
                || dev.mite.combat.Archery.isSilverArrow(event.getSource().getDirectEntity())) {
            event.setAmount(event.getAmount() * (1 + MiteConfig.SILVER_UNDEAD_BONUS.get().floatValue()));
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterDataMapTypesEvent e) -> e.register(SPAWN_DEPTH));
        modBus.addListener(AiContent::modifyAttributes);
        NeoForge.EVENT_BUS.addListener(AiContent::onJoin);
        NeoForge.EVENT_BUS.addListener(AiContent::onDespawn);
        NeoForge.EVENT_BUS.addListener(AiContent::onTame);
        NeoForge.EVENT_BUS.addListener(AiContent::onPositionCheck);
        NeoForge.EVENT_BUS.addListener(AiContent::onIncomingDamage);
        VanillaMobRules.register();
        MobRules.register();
        SilverfishRules.register();
        SpawnSurfaces.register();
    }
}
