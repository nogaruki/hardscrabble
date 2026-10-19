package dev.mite.entity.mob;

import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.harvest.ToolStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.world.BiomeModifier;
import com.mojang.serialization.MapCodec;

/** New mobs J01 Ghoul, J02 Wight, J05 Revenant, J08 Wood Spider, J13 Infernal Creeper, J14/J15 elementals, J19 Longdeads, J20 Bone Lords, J10/J11 spiders, J15 Clay Golem, J16 cubes, J17 bats, J18 silverfish (EF-MOB-01..20): types, attributes, eggs, spawn rules, immunity. */
public final class MobContent {
    private MobContent() {}

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MiteMod.MODID);
    /** EF-IA-06: biome modifier that changes the weight of an existing spawn entry (spider 80). */
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<SpawnWeightModifier>> SPAWN_WEIGHT =
            BIOME_MODIFIERS.register("spawn_weight", () -> SpawnWeightModifier.CODEC);

    public static final DeferredHolder<EntityType<?>, EntityType<Ghoul>> GHOUL = zombie("ghoul", Ghoul::new);
    public static final DeferredHolder<EntityType<?>, EntityType<Wight>> WIGHT = zombie("wight", Wight::new);
    public static final DeferredHolder<EntityType<?>, EntityType<Revenant>> REVENANT = zombie("revenant", Revenant::new);
    public static final DeferredHolder<EntityType<?>, EntityType<WoodSpider>> WOOD_SPIDER = ENTITIES.registerEntityType("wood_spider",
            WoodSpider::new, MobCategory.MONSTER, b -> b.sized(0.7F, 0.5F).eyeHeight(0.45F).clientTrackingRange(8).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<Longdead>> LONGDEAD = skeleton("longdead", Longdead::new);
    public static final DeferredHolder<EntityType<?>, EntityType<LongdeadGuardian>> LONGDEAD_GUARDIAN = skeleton("longdead_guardian", LongdeadGuardian::new);
    public static final DeferredHolder<EntityType<?>, EntityType<InfernalCreeper>> INFERNAL_CREEPER = ENTITIES.registerEntityType("infernal_creeper",
            InfernalCreeper::new, MobCategory.MONSTER, b -> b.sized(0.6F, 1.7F).clientTrackingRange(8).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<FireElemental>> FIRE_ELEMENTAL = ENTITIES.registerEntityType("fire_elemental",
            FireElemental::new, MobCategory.MONSTER, b -> b.sized(0.6F, 1.8F).fireImmune().clientTrackingRange(8).notInPeaceful());
    public static final DeferredHolder<EntityType<?>, EntityType<EarthElemental>> EARTH_ELEMENTAL = zombie("earth_elemental", EarthElemental::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BoneLord>> BONE_LORD = skeleton("bone_lord", BoneLord::new);
    public static final DeferredHolder<EntityType<?>, EntityType<AncientBoneLord>> ANCIENT_BONE_LORD = skeleton("ancient_bone_lord", AncientBoneLord::new);

    // EF-MOB-03/04/06/07/09: Shadow, Invisible Stalker, Dire Wolf, Hellhound, Demon Spider; EF-MOB-12 thrown web.
    public static final DeferredHolder<EntityType<?>, EntityType<Shadow>> SHADOW = zombie("shadow", Shadow::new, true);
    public static final DeferredHolder<EntityType<?>, EntityType<InvisibleStalker>> INVISIBLE_STALKER = zombie("invisible_stalker", InvisibleStalker::new);
    public static final DeferredHolder<EntityType<?>, EntityType<DireWolf>> DIRE_WOLF = wolf("dire_wolf", DireWolf::new, false);
    public static final DeferredHolder<EntityType<?>, EntityType<Hellhound>> HELLHOUND = wolf("hellhound", Hellhound::new, true);
    public static final DeferredHolder<EntityType<?>, EntityType<DemonSpider>> DEMON_SPIDER = spider("demon_spider", DemonSpider::new);
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownWeb>> THROWN_WEB = ENTITIES.registerEntityType("thrown_web", ThrownWeb::new,
            MobCategory.MISC, b -> b.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));

    // EF-MOB-10/11/15/16/17/18: Phase Spider, Black Widow, Clay Golem, gelatinous cubes, vampire bats, silverfish variants.
    public static final DeferredHolder<EntityType<?>, EntityType<PhaseSpider>> PHASE_SPIDER = spider("phase_spider", PhaseSpider::new);
    public static final DeferredHolder<EntityType<?>, EntityType<BlackWidow>> BLACK_WIDOW = spider("black_widow", BlackWidow::new);
    public static final DeferredHolder<EntityType<?>, EntityType<ClayGolem>> CLAY_GOLEM = zombie("clay_golem", ClayGolem::new);
    public static final DeferredHolder<EntityType<?>, EntityType<GelatinousCube>> JELLY = cube("jelly", GelatinousCube.Kind.JELLY);
    public static final DeferredHolder<EntityType<?>, EntityType<GelatinousCube>> BLOB = cube("blob", GelatinousCube.Kind.BLOB);
    public static final DeferredHolder<EntityType<?>, EntityType<GelatinousCube>> OOZE = cube("ooze", GelatinousCube.Kind.OOZE);
    public static final DeferredHolder<EntityType<?>, EntityType<GelatinousCube>> PUDDING = cube("pudding", GelatinousCube.Kind.PUDDING);
    public static final DeferredHolder<EntityType<?>, EntityType<VampireBat>> VAMPIRE_BAT = bat("vampire_bat", (t, l) -> new VampireBat(t, l, 5), false);
    public static final DeferredHolder<EntityType<?>, EntityType<VampireBat>> GIANT_VAMPIRE_BAT = bat("giant_vampire_bat", (t, l) -> new VampireBat(t, l, 10), false);
    public static final DeferredHolder<EntityType<?>, EntityType<Nightwing>> NIGHTWING = bat("nightwing", Nightwing::new, true);
    public static final DeferredHolder<EntityType<?>, EntityType<SilverfishVariant>> COPPERSPINE = silverfish("copperspine", SilverfishVariant.Kind.COPPERSPINE);
    public static final DeferredHolder<EntityType<?>, EntityType<SilverfishVariant>> NETHERSPAWN = silverfish("netherspawn", SilverfishVariant.Kind.NETHERSPAWN);
    public static final DeferredHolder<EntityType<?>, EntityType<SilverfishVariant>> HOARY_SILVERFISH = silverfish("hoary_silverfish", SilverfishVariant.Kind.HOARY);
    /** EF-MOB-16: the coloured gelatinous ball each cube drops when tiny, in the order JELLY, BLOB, OOZE, PUDDING. */
    public static final List<DeferredItem<Item>> GELATINOUS_BALLS = Stream.of(JELLY, BLOB, OOZE, PUDDING)
            .map(type -> ITEMS.registerSimpleItem(type.getId().getPath() + "_ball")).toList();

    public static final List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> MOBS = List.of(GHOUL, WIGHT, REVENANT, WOOD_SPIDER,
            LONGDEAD, LONGDEAD_GUARDIAN, SHADOW, INVISIBLE_STALKER, DIRE_WOLF, HELLHOUND, DEMON_SPIDER,
            INFERNAL_CREEPER, FIRE_ELEMENTAL, EARTH_ELEMENTAL, BONE_LORD, ANCIENT_BONE_LORD,
            PHASE_SPIDER, BLACK_WIDOW, CLAY_GOLEM, JELLY, BLOB, OOZE, PUDDING, VAMPIRE_BAT, GIANT_VAMPIRE_BAT, NIGHTWING, COPPERSPINE, NETHERSPAWN, HOARY_SILVERFISH);
    public static final List<DeferredItem<SpawnEggItem>> EGGS = MOBS.stream()
            .map(type -> ITEMS.registerItem(type.getId().getPath() + "_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(type.get()))).toList();

    /** EF-MOB-02 / ET-J-03: entities hurt only by {@link #HURTS_IMMATERIAL} damage, silver or enchanted weapons. */
    public static final TagKey<EntityType<?>> IMMATERIAL = TagKey.create(Registries.ENTITY_TYPE, MiteMod.id("immaterial"));
    /** Fire, lava, magic and /kill: the damage types that hurt immaterial mobs. */
    public static final TagKey<DamageType> HURTS_IMMATERIAL = TagKey.create(Registries.DAMAGE_TYPE, MiteMod.id("hurts_immaterial"));

    /** Natural spawn depth per mob, as a 1.6.4 Y converted with Formulas.mapDepth (EF-MON-01, EF-MOB). */
    public static final int SHADOW_MAX_Y = 32, INVISIBLE_STALKER_MAX_Y = 40, HELLHOUND_MAX_Y = 32, DEMON_SPIDER_MAX_Y = 32;
    public static final int GHOUL_MAX_Y = 56, WIGHT_MAX_Y = 48, REVENANT_MAX_Y = 44, EARTH_ELEMENTAL_MAX_Y = 40, BONE_LORD_MAX_Y = 32;
    public static final int PHASE_SPIDER_MAX_Y = 32, BLOB_MAX_Y = 40, OOZE_MAX_Y = 32, PUDDING_MAX_Y = 24, VAMPIRE_BAT_MAX_Y = 48, NIGHTWING_MAX_Y = 32;

    private static <E extends Zombie> DeferredHolder<EntityType<?>, EntityType<E>> zombie(String name, EntityType.EntityFactory<E> factory) {
        return zombie(name, factory, false);
    }

    private static <E extends Zombie> DeferredHolder<EntityType<?>, EntityType<E>> zombie(String name, EntityType.EntityFactory<E> factory, boolean fireImmune) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MONSTER, b -> {
            b.sized(0.6F, 1.95F).eyeHeight(1.74F).passengerAttachments(2.0125F).ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful();
            return fireImmune ? b.fireImmune() : b;
        });
    }

    private static <E extends Wolf> DeferredHolder<EntityType<?>, EntityType<E>> wolf(String name, EntityType.EntityFactory<E> factory, boolean fireImmune) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MONSTER, b -> {
            b.sized(0.6F, 0.85F).eyeHeight(0.68F).passengerAttachments(new Vec3(0.0, 0.81875, -0.0625)).clientTrackingRange(10).notInPeaceful();
            return fireImmune ? b.fireImmune() : b;
        });
    }

    private static <E extends AbstractSkeleton> DeferredHolder<EntityType<?>, EntityType<E>> skeleton(String name, EntityType.EntityFactory<E> factory) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MONSTER, b -> b.sized(0.6F, 1.99F).eyeHeight(1.74F)
                .ridingOffset(-0.7F).clientTrackingRange(8).notInPeaceful());
    }

    private static <E extends Spider> DeferredHolder<EntityType<?>, EntityType<E>> spider(String name, EntityType.EntityFactory<E> factory) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MONSTER, b -> b.sized(1.4F, 0.9F).eyeHeight(0.65F).passengerAttachments(0.765F)
                .clientTrackingRange(8).notInPeaceful());
    }

    private static DeferredHolder<EntityType<?>, EntityType<GelatinousCube>> cube(String name, GelatinousCube.Kind kind) {
        return ENTITIES.registerEntityType(name, (t, l) -> new GelatinousCube(t, l, kind), MobCategory.MONSTER, b -> b.sized(0.52F, 0.52F).eyeHeight(0.325F)
                .spawnDimensionsScale(4.0F).clientTrackingRange(10).notInPeaceful());
    }

    /** Hostile bats count as monsters (peaceful, monster cap) rather than ambient creatures. */
    private static <E extends VampireBat> DeferredHolder<EntityType<?>, EntityType<E>> bat(String name, EntityType.EntityFactory<E> factory, boolean fireImmune) {
        return ENTITIES.registerEntityType(name, factory, MobCategory.MONSTER, b -> {
            b.sized(0.5F, 0.9F).eyeHeight(0.45F).clientTrackingRange(5).notInPeaceful();
            return fireImmune ? b.fireImmune() : b;
        });
    }

    private static DeferredHolder<EntityType<?>, EntityType<SilverfishVariant>> silverfish(String name, SilverfishVariant.Kind kind) {
        return ENTITIES.registerEntityType(name, (t, l) -> new SilverfishVariant(t, l, kind), MobCategory.MONSTER, b -> {
            b.sized(0.4F, 0.3F).eyeHeight(0.13F).passengerAttachments(0.2375F).clientTrackingRange(8).notInPeaceful();
            return kind == SilverfishVariant.Kind.NETHERSPAWN ? b.fireImmune() : b; // [INFÉRÉ] Nether-born
        });
    }

    /** EF-CBT-06: magic, enchanted or silver weapons. An arrow counts by its own tip and enchantments, never by the bow (M11). */
    public static boolean hurtsImmaterial(DamageSource source) {
        ItemStack weapon = source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow
                ? arrow.getPickupItemStackOrigin() : source.getWeaponItem();
        return source.is(HURTS_IMMATERIAL) || weapon != null && (weapon.isEnchanted() || dev.mite.combat.Archery.tip(weapon) == Material.SILVER
                || ToolStats.of(weapon).map(i -> i.material() == Material.SILVER).orElse(false));
    }

    /** EF-MOB-12: every spider, vanilla or MITE, throws webs. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Spider spider) spider.goalSelector.addGoal(3, new ThrownWeb.ThrowGoal(spider));
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().is(IMMATERIAL) && !hurtsImmaterial(event.getSource())) event.setCanceled(true);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(GHOUL.get(), Ghoul.createAttributes().build());
        event.put(WIGHT.get(), Wight.createAttributes().build());
        event.put(REVENANT.get(), Revenant.createAttributes().build());
        event.put(WOOD_SPIDER.get(), WoodSpider.createAttributes().build());
        event.put(LONGDEAD.get(), Longdead.createAttributes().build());
        event.put(LONGDEAD_GUARDIAN.get(), LongdeadGuardian.createAttributes().build());
        event.put(SHADOW.get(), Shadow.createAttributes().build());
        event.put(INVISIBLE_STALKER.get(), InvisibleStalker.createAttributes().build());
        event.put(DIRE_WOLF.get(), DireWolf.createAttributes().build());
        event.put(HELLHOUND.get(), Hellhound.createAttributes().build());
        event.put(DEMON_SPIDER.get(), DemonSpider.createAttributes().build());
        event.put(INFERNAL_CREEPER.get(), Creeper.createAttributes().build());
        event.put(FIRE_ELEMENTAL.get(), FireElemental.createAttributes().build());
        event.put(EARTH_ELEMENTAL.get(), EarthElemental.createAttributes().build());
        event.put(BONE_LORD.get(), BoneLord.createAttributes().build());
        event.put(ANCIENT_BONE_LORD.get(), AncientBoneLord.createAttributes().build());
        event.put(PHASE_SPIDER.get(), PhaseSpider.createAttributes().build());
        event.put(BLACK_WIDOW.get(), BlackWidow.createAttributes().build());
        event.put(CLAY_GOLEM.get(), ClayGolem.createAttributes().build());
        for (var cube : List.of(JELLY, BLOB, OOZE, PUDDING)) event.put(cube.get(), Monster.createMonsterAttributes().build());
        // EF-MOB-17: scale 1 for the Vampire Bat, 2 for the Giant [INFÉRÉ] and the Nightwing [INFÉRÉ].
        event.put(VAMPIRE_BAT.get(), VampireBat.createAttributes(1).build());
        event.put(GIANT_VAMPIRE_BAT.get(), VampireBat.createAttributes(2).build());
        event.put(NIGHTWING.get(), VampireBat.createAttributes(2).build());
        for (var fish : List.of(COPPERSPINE, NETHERSPAWN, HOARY_SILVERFISH)) event.put(fish.get(), SilverfishVariant.createAttributes().build());
    }

    /** Monster spawn rules below the converted depth. Blood moon surface spawns belong to the lunar domain (N). */
    private static <T extends Mob> SpawnPlacements.SpawnPredicate<T> belowDepth(int miteMaxY) {
        return (type, level, reason, pos, random) -> pos.getY() <= Formulas.mapDepth(miteMaxY) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    private static <T extends Mob> SpawnPlacements.SpawnPredicate<T> onStoneBelow(int miteMaxY) {
        return (type, level, reason, pos, random) -> level.getBlockState(pos.below()).is(BlockTags.BASE_STONE_OVERWORLD)
                && MobContent.<T>belowDepth(miteMaxY).test(type, level, reason, pos, random);
    }

    private static <T extends VampireBat> SpawnPlacements.SpawnPredicate<T> batBelowDepth(int miteMaxY) {
        return (type, level, reason, pos, random) -> pos.getY() <= Formulas.mapDepth(miteMaxY) && Bat.checkBatSpawnRules(type, level, reason, pos, random);
    }

    /** EF-MOB-16 Jelly: the first solid block within 8 above is natural stone. */
    public static boolean underStone(ServerLevelAccessor level, BlockPos pos) {
        for (int dy = 1; dy <= 8; dy++) {
            BlockState state = level.getBlockState(pos.above(dy));
            if (!state.isAir()) return state.is(BlockTags.BASE_STONE_OVERWORLD);
        }
        return false;
    }

    /** EF-MOB-08: under leaves (up to 8 blocks above) with a log within 3 blocks. Any light: hostile by day. */
    public static boolean checkWoodSpiderSpawnRules(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !Mob.checkMobSpawnRules(type, level, reason, pos, random)) return false;
        boolean leaves = false;
        for (int dy = 1; dy <= 8 && !leaves; dy++) leaves = level.getBlockState(pos.above(dy)).is(BlockTags.LEAVES);
        return leaves && BlockPos.betweenClosedStream(pos.offset(-3, 0, -3), pos.offset(3, 3, 3)).anyMatch(p -> level.getBlockState(p).is(BlockTags.LOGS));
    }

    private static <T extends Mob> void ground(RegisterSpawnPlacementsEvent event, EntityType<T> type, SpawnPlacements.SpawnPredicate<T> predicate) {
        event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        ground(event, GHOUL.get(), belowDepth(GHOUL_MAX_Y));
        ground(event, WIGHT.get(), belowDepth(WIGHT_MAX_Y));
        ground(event, REVENANT.get(), belowDepth(REVENANT_MAX_Y));
        ground(event, WOOD_SPIDER.get(), MobContent::checkWoodSpiderSpawnRules);
        // EF-MOB-19: Underworld only, through the biome modifier; no depth filter outside the Overworld (EF-IA-06).
        ground(event, LONGDEAD.get(), Monster::checkMonsterSpawnRules);
        ground(event, LONGDEAD_GUARDIAN.get(), Monster::checkMonsterSpawnRules);
        ground(event, SHADOW.get(), belowDepth(SHADOW_MAX_Y));
        ground(event, INVISIBLE_STALKER.get(), belowDepth(INVISIBLE_STALKER_MAX_Y));
        ground(event, HELLHOUND.get(), belowDepth(HELLHOUND_MAX_Y));
        ground(event, DEMON_SPIDER.get(), belowDepth(DEMON_SPIDER_MAX_Y));
        // EF-MOB-06: hunts by day and night in the snowy taiga (biome modifier).
        ground(event, DIRE_WOLF.get(),
                (type, level, reason, pos, random) -> level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, reason, pos, random));
        ground(event, ANCIENT_BONE_LORD.get(), Monster::checkMonsterSpawnRules);
        ground(event, EARTH_ELEMENTAL.get(), belowDepth(EARTH_ELEMENTAL_MAX_Y));
        ground(event, BONE_LORD.get(), belowDepth(BONE_LORD_MAX_Y));
        event.register(FIRE_ELEMENTAL.get(), SpawnPlacementTypes.IN_LAVA, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FireElemental::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        // EF-MOB-13: no own spawn entry, the Infernal Creeper replaces natural creepers (onFinalizeSpawn).
        ground(event, INFERNAL_CREEPER.get(), Monster::checkMonsterSpawnRules);
        ground(event, PHASE_SPIDER.get(), belowDepth(PHASE_SPIDER_MAX_Y));
        ground(event, BLACK_WIDOW.get(), BlackWidow::checkSpawnRules);
        ground(event, CLAY_GOLEM.get(), ClayGolem::checkSpawnRules);
        // EF-MOB-16: Jelly under stone, Blob by depth, Ooze and Pudding by depth on stone.
        ground(event, JELLY.get(),
                (type, level, reason, pos, random) -> underStone(level, pos) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random));
        ground(event, BLOB.get(), belowDepth(BLOB_MAX_Y));
        ground(event, OOZE.get(), onStoneBelow(OOZE_MAX_Y));
        ground(event, PUDDING.get(), onStoneBelow(PUDDING_MAX_Y));
        // EF-MOB-17: vanilla bat rules (under the surface, dark: never above lit areas) below the depth. Giant as the Vampire Bat [INFÉRÉ].
        ground(event, VAMPIRE_BAT.get(), batBelowDepth(VAMPIRE_BAT_MAX_Y));
        ground(event, GIANT_VAMPIRE_BAT.get(), batBelowDepth(VAMPIRE_BAT_MAX_Y));
        ground(event, NIGHTWING.get(), batBelowDepth(NIGHTWING_MAX_Y));
        // EF-MOB-18: Copperspine near copper; Netherspawn (Nether) and Hoary (Underworld) placed by biome modifiers.
        ground(event, COPPERSPINE.get(), SilverfishVariant::checkCopperspineSpawnRules);
        ground(event, NETHERSPAWN.get(), Monster::checkMonsterSpawnRules);
        ground(event, HOARY_SILVERFISH.get(), Monster::checkMonsterSpawnRules);
        // Changelog l.301 (R156): natural silverfish (biome modifiers) spawn in the dark like any monster, not at any light as vanilla allows.
        event.register(EntityTypes.SILVERFISH, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.AND);
    }

    /** EF-MOB-13 / ET-J-05: a natural vanilla creeper may be swapped for an Infernal Creeper at the same place. */
    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (mob.getType() != EntityTypes.CREEPER || event.getSpawnType() != EntitySpawnReason.NATURAL || !InfernalCreeper.replacesCreeper(mob.getRandom(), event.getY())) return;
        InfernalCreeper infernal = INFERNAL_CREEPER.get().create(event.getLevel().getLevel(), EntitySpawnReason.NATURAL);
        if (infernal == null) return;
        event.setSpawnCancelled(true);
        infernal.snapTo(event.getX(), event.getY(), event.getZ(), mob.getYRot(), 0);
        EventHooks.finalizeMobSpawn(infernal, event.getLevel(), event.getDifficulty(), EntitySpawnReason.NATURAL, null);
        event.getLevel().addFreshEntityWithPassengers(infernal);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        BIOME_MODIFIERS.register(modBus);
        modBus.addListener(MobContent::createAttributes);
        modBus.addListener(MobContent::spawnPlacements);
        NeoForge.EVENT_BUS.addListener(MobContent::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MobContent::onJoin);
        NeoForge.EVENT_BUS.addListener(MobContent::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(FireElemental::onEntityInteract);
        CubeRules.register();
    }
}
