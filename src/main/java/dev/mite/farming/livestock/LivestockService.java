package dev.mite.farming.livestock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.equipment.Buckets;
import dev.mite.farming.FarmingContent;
import dev.mite.metabolism.MetabolismContent;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/** Livestock with needs, fleeing monsters and wild wolves, sheltering from rain (I10, EF-AGR-08, ET-I-06) and faster animals without meat after a fall (I11, EF-AGR-09, ET-I-07). */
public final class LivestockService {
    private LivestockService() {}

    /** Cow, pig, sheep and chicken (docs/02 §6.3.2). */
    public static final TagKey<EntityType<?>> LIVESTOCK = TagKey.create(Registries.ENTITY_TYPE, MiteMod.id("livestock"));

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);

    private static final MapCodec<Livestock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("food", 1f).forGetter(s -> s.food),
            Codec.FLOAT.optionalFieldOf("water", 1f).forGetter(s -> s.water),
            Codec.FLOAT.optionalFieldOf("sunlight", 1f).forGetter(s -> s.sunlight),
            Codec.INT.optionalFieldOf("sickness", 0).forGetter(s -> s.sickness),
            Codec.LONG.optionalFieldOf("last_eval", Long.MIN_VALUE).forGetter(s -> s.lastEvalTick)
    ).apply(i, (food, water, sunlight, sickness, last) -> {
        Livestock s = new Livestock();
        s.food = food;
        s.water = water;
        s.sunlight = sunlight;
        s.sickness = sickness;
        s.lastEvalTick = last;
        return s;
    }));

    /** Server-side only: no client display reads it, so it is not synced. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Livestock>> STATE = ATTACHMENTS.register("livestock",
            () -> AttachmentType.builder(Livestock::new).serialize(dev.mite.attach.Migrations.versioned("livestock", CODEC)).build());

    /** ET-I-06: needs are evaluated every 20 ticks, staggered by entity id. */
    static final int EVAL_INTERVAL = 20;
    /** Overcrowding: more than CROWD_LIMIT other livestock within CROWD_RADIUS blocks [NON VÉRIFIÉ]. */
    static final double CROWD_RADIUS = 2;
    static final int CROWD_LIMIT = 6;
    /** EF-AGR-07: a productive animal drops one manure per day on average [NON VÉRIFIÉ]. */
    static final int MANURE_INTERVAL = 24_000;
    /** Changelog l.22 (R193): a chicken makes manure half as often, and sheds a feather every 2 days on average (halved) [INFÉRÉ]. */
    static final int CHICKEN_MANURE_INTERVAL = 2 * MANURE_INTERVAL, FEATHER_INTERVAL = 48_000;
    /**
     * EF-AGR-08, changelog l.836-837 and l.283: grazing eats the grass. One bite per BITE of food eaten: a bitten grass block turns
     * to dirt (as under a vanilla sheep); a cow feeds on tall grass and dandelions and destroys the plant only 1 bite in
     * COW_PLANT_KEEP (l.283: 90 % less often) [INFÉRÉ].
     */
    static final float BITE = 0.5f;
    static final int COW_PLANT_KEEP = 10;
    /** Changelog l.835 and l.819: a walking cow, pig or sheep tramples the plant it walks through, a cow 4x as often [INFÉRÉ]. */
    static final int TRAMPLE_ODDS = 80, COW_TRAMPLE_ODDS = 20;
    static final double MOVING = 1e-4;
    /** Changelog l.515 (R133): a hurt animal spreads panic to the livestock around, whatever their kind [INFÉRÉ radius and time]. */
    static final double PANIC_RADIUS = 8;
    static final int PANIC_TICKS = 100;
    /** Game time until which an animal panics from a neighbour's fright (server thread, transient). */
    private static final Map<Animal, Long> PANIC = new WeakHashMap<>();
    /** EF-AGR-09: +25 % movement speed [NON VÉRIFIÉ: the spec only says the player must sprint to catch them]. */
    static final AttributeModifier SPEED = new AttributeModifier(MiteMod.id("livestock_speed"), 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

    public static Livestock state(Animal animal) {
        return animal.getData(STATE);
    }

    public static void entityTypeTags(Function<TagKey<EntityType<?>>, TagAppender<EntityType<?>>> tag) {
        tag.apply(LIVESTOCK).add(key(EntityTypes.COW), key(EntityTypes.PIG), key(EntityTypes.SHEEP), key(EntityTypes.CHICKEN));
    }

    private static ResourceKey<EntityType<?>> key(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).orElseThrow();
    }

    /** Changelog l.836: cows feed on tall grass (short and tall grass, ferns) and dandelions. */
    static boolean cowFeed(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DANDELION);
    }

    static boolean grazes(Animal a, LevelReader level, BlockPos feet) {
        return level.getBlockState(feet.below()).is(Blocks.GRASS_BLOCK) || a instanceof Cow && cowFeed(level.getBlockState(feet));
    }

    /** One bite of grazing (see {@link #BITE}); nothing is eaten away when mob griefing is off. */
    static void bite(Animal a, ServerLevel level, BlockPos feet) {
        if (!EventHooks.canEntityGrief(level, a)) return;
        if (a instanceof Cow && cowFeed(level.getBlockState(feet))) {
            if (a.getRandom().nextInt(COW_PLANT_KEEP) == 0) level.destroyBlock(feet, false, a);
        } else if (level.getBlockState(feet.below()).is(Blocks.GRASS_BLOCK)) {
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, feet.below(), Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()));
            level.setBlock(feet.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Changelog l.835 and l.819: a walking cow, pig or sheep sometimes tramples the plant it walks through; it drops nothing. */
    static boolean trample(Animal a, ServerLevel level, BlockPos feet) {
        if (a instanceof Chicken || a.getDeltaMovement().horizontalDistanceSqr() < MOVING || !(level.getBlockState(feet).getBlock() instanceof VegetationBlock)
                || a.getRandom().nextInt(a instanceof Cow ? COW_TRAMPLE_ODDS : TRAMPLE_ODDS) != 0 || !EventHooks.canEntityGrief(level, a)) return false;
        return level.destroyBlock(feet, false, a);
    }

    /** Changelog l.515 (R133): a livestock animal hurt by a panic cause frightens the livestock around it. */
    static void spreadPanic(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof Animal a) || a.level().isClientSide() || !a.is(LIVESTOCK) || !e.getSource().is(DamageTypeTags.PANIC_CAUSES)) return;
        long until = a.level().getGameTime() + PANIC_TICKS;
        a.level().getEntitiesOfClass(Animal.class, a.getBoundingBox().inflate(PANIC_RADIUS), o -> o != a && o.is(LIVESTOCK)).forEach(o -> PANIC.put(o, until));
    }

    public static boolean panicking(Animal a) {
        Long until = PANIC.get(a);
        return until != null && until > a.level().getGameTime();
    }

    /** One needs evaluation: decay, grazing and drinking, sickness, neglect damage, trampling, egg, manure and feather gating. */
    public static void evaluate(Animal a) {
        ServerLevel level = (ServerLevel) a.level();
        long now = level.getGameTime();
        Livestock s = state(a);
        long dt = s.lastEvalTick == Long.MIN_VALUE ? 0 : now - s.lastEvalTick;
        s.lastEvalTick = now;
        BlockPos feet = a.blockPosition();
        if (!needs()) { // Q-3: needs off = always satisfied; manure keeps coming
            s.food = s.water = s.sunlight = 1;
            s.sickness = 0;
        } else {
            boolean grazing = grazes(a, level, feet);
            float damage = s.update(dt, grazing, a.isInWater() || nearWater(level, feet), level.isBrightOutside() && level.canSeeSky(feet), crowded(a));
            if (grazing && a.getRandom().nextFloat() < s.eaten / BITE) bite(a, level, feet);
            if (damage > 0) a.hurtServer(level, a.damageSources().starve(), damage);
        }
        trample(a, level, feet);
        if (!s.productive()) {
            if (a instanceof Chicken c) c.eggTime = Math.max(c.eggTime, 2 * EVAL_INTERVAL); // EF-AGR-08: no eggs
        } else if (dt > 0) {
            boolean chicken = a instanceof Chicken;
            if (a.getRandom().nextFloat() < (float) dt / (chicken ? CHICKEN_MANURE_INTERVAL : MANURE_INTERVAL))
                a.spawnAtLocation(level, new ItemStack(FarmingContent.MANURE.get()));
            if (chicken && a.getRandom().nextFloat() < (float) dt / FEATHER_INTERVAL) a.spawnAtLocation(level, new ItemStack(Items.FEATHER));
        }
    }

    /** Config {@code farming.livestock_needs} (Q-3, ENF-PERF-08). */
    static boolean needs() {
        return !dev.mite.config.MiteConfig.SPEC.isLoaded() || dev.mite.config.MiteConfig.LIVESTOCK_NEEDS.getAsBoolean();
    }

    static boolean crowded(Animal a) {
        return a.level().getEntitiesOfClass(Animal.class, a.getBoundingBox().inflate(CROWD_RADIUS), o -> o != a && o.is(LIVESTOCK)).size() > CROWD_LIMIT;
    }

    /** Water beside the feet, or one block lower (a pond edge). */
    static boolean nearWater(LevelReader level, BlockPos feet) {
        for (Direction d : Direction.Plane.HORIZONTAL)
            if (level.getFluidState(feet.relative(d)).is(FluidTags.WATER) || level.getFluidState(feet.below().relative(d)).is(FluidTags.WATER)) return true;
        return false;
    }

    /** Sick or overcrowded animals do not breed (EF-AGR-08). */
    static boolean canBreed(Mob mob) {
        return !(mob instanceof Animal a) || !a.is(LIVESTOCK) || !needs() || !state(a).sick() && !crowded(a);
    }

    /** Hungry animals eat their food; water bowls and buckets water them; neglected ones refuse milking and shearing. */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !(e.getTarget() instanceof Animal a) || !a.is(LIVESTOCK)) return;
        Player p = e.getEntity();
        ItemStack stack = e.getItemStack();
        Livestock s = state(a);
        boolean bowl = stack.is(MetabolismContent.WATER_BOWL.get());
        if (a.isFood(stack) && s.feed()) {
            stack.consume(1, p);
            a.playSound(SoundEvents.GENERIC_EAT.value(), 1, 1);
        } else if ((bowl || stack.is(Tags.Items.BUCKETS_WATER)) && s.water < 1) { // water buckets of any metal (C-3)
            s.water = 1;
            Item empty = bowl ? Items.BOWL : Buckets.of(stack).empty().get();
            p.setItemInHand(e.getHand(), ItemUtils.createFilledResult(stack, p, new ItemStack(empty)));
            a.playSound(SoundEvents.GENERIC_DRINK.value(), 1, 1);
        } else if (!s.productive() && (a instanceof Cow && (stack.is(Tags.Items.BUCKETS_EMPTY) || stack.is(Items.BOWL)) || a instanceof Sheep && stack.is(Tags.Items.TOOLS_SHEAR))) {
            p.sendOverlayMessage(Component.translatable("hardscrabble.livestock.unproductive"));
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        } else return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Animal a) || !a.is(LIVESTOCK)) return;
        a.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(SPEED);
        if (a.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof SeekGoal)) return;
        a.goalSelector.addGoal(1, new SpreadPanicGoal(a));
        a.goalSelector.addGoal(2, new AvoidEntityGoal<>(a, Monster.class, 8, 1.2, 1.5));
        a.goalSelector.addGoal(2, new AvoidEntityGoal<>(a, Wolf.class, w -> !((Wolf) w).isTame(), 8, 1.2, 1.5, EntitySelector.NO_CREATIVE_OR_SPECTATOR));
        a.goalSelector.addGoal(3, new ShelterGoal(a));
        a.goalSelector.addGoal(4, new SeekGoal(a, s -> s.water, (level, pos) -> nearWater(level, pos.above())));
        a.goalSelector.addGoal(4, new SeekGoal(a, s -> s.food, (level, pos) -> grazes(a, level, pos.above())));
    }

    /** Changelog l.515: flees like a hurt animal while frightened by a neighbour (vanilla panic speed 2.0). */
    static final class SpreadPanicGoal extends PanicGoal {
        SpreadPanicGoal(Animal animal) {
            super(animal, 2.0);
        }

        @Override
        protected boolean shouldPanic() {
            return panicking((Animal) mob);
        }
    }

    /** EF-AGR-08: in the rain, walks to a roofed standable spot within 8 blocks (FleeSunGoal with rain instead of burning). */
    static final class ShelterGoal extends FleeSunGoal {
        ShelterGoal(PathfinderMob mob) {
            super(mob, 1.2);
        }

        @Override
        public boolean canUse() {
            return mob.level().isRainingAt(mob.blockPosition()) && setWantedPos();
        }

        @Override
        protected @Nullable Vec3 getHidePos() {
            for (int i = 0; i < 10; i++) {
                BlockPos p = mob.blockPosition().offset(mob.getRandom().nextInt(17) - 8, mob.getRandom().nextInt(5) - 2, mob.getRandom().nextInt(17) - 8);
                if (!mob.level().canSeeSky(p) && mob.level().isEmptyBlock(p) && mob.getNavigation().isStableDestination(p)) return Vec3.atBottomCenterOf(p);
            }
            return null;
        }
    }

    /** Walks to a block that satisfies a need once it falls under half, until it is met (EF-AGR-08). */
    static final class SeekGoal extends MoveToBlockGoal {
        private final Animal animal;
        private final ToDoubleFunction<Livestock> need;
        private final BiPredicate<LevelReader, BlockPos> target;

        SeekGoal(Animal animal, ToDoubleFunction<Livestock> need, BiPredicate<LevelReader, BlockPos> target) {
            super(animal, 1.0, 8);
            this.animal = animal;
            this.need = need;
            this.target = target;
        }

        @Override
        public boolean canUse() {
            return need.applyAsDouble(state(animal)) < 0.5 && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return need.applyAsDouble(state(animal)) < 1 && super.canContinueToUse();
        }

        @Override
        protected boolean isValidTarget(LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() && target.test(level, pos);
        }
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((EntityTickEvent.Post e) -> {
            if (e.getEntity() instanceof Animal a && (a.tickCount + a.getId()) % EVAL_INTERVAL == 0 && !a.level().isClientSide() && a.is(LIVESTOCK)) evaluate(a);
        });
        bus.addListener(EventPriority.HIGH, LivestockService::onInteract); // before the metabolism milk bowl
        bus.addListener(LivestockService::onJoin);
        bus.addListener((BabyEntitySpawnEvent e) -> {
            if (!canBreed(e.getParentA()) || !canBreed(e.getParentB())) e.setCanceled(true);
        });
        bus.addListener(LivestockService::onDrops);
        bus.addListener(LivestockService::spreadPanic);
        HorseTaming.register(bus);
    }

    /**
     * EF-AGR-09: an animal killed by a fall drops no meat; nor does sick livestock (changelog l.570, R128). Chickens drop
     * half as many feathers (changelog l.831, R117: vanilla 0-2 becomes 0-1).
     */
    static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof Animal a)) return;
        if (e.getSource().is(DamageTypeTags.IS_FALL) || a.is(LIVESTOCK) && state(a).sick()) e.getDrops().removeIf(i -> i.getItem().is(ItemTags.MEAT));
        if (a instanceof Chicken) {
            for (var drop : e.getDrops()) {
                ItemStack stack = drop.getItem();
                if (stack.is(Items.FEATHER)) stack.setCount(stack.getCount() / 2 + (stack.getCount() % 2 == 1 && a.getRandom().nextBoolean() ? 1 : 0));
            }
            e.getDrops().removeIf(i -> i.getItem().isEmpty());
        }
    }
}
