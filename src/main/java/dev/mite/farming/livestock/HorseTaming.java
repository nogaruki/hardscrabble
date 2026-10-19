package dev.mite.farming.livestock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Harder horse taming, changelog l.294-298 (R156): a wild horse takes a food bribe once every 4000 ticks, refuses riders for
 * 4000 ticks after throwing one off, shies away from players and loses docility (temper) when harmed. Changelog l.284-285
 * (R157): horses stop grazing when attacked and do not graze while shying away. Llamas (absent from 1.6.4) keep vanilla taming.
 */
public final class HorseTaming {
    private HorseTaming() {}

    /** Changelog l.295: ticks between two accepted bribes. */
    public static final int BRIBE_COOLDOWN = 4000;
    /** Changelog l.296: ticks a wild horse refuses riders after throwing one off. */
    public static final int REFUSE_TICKS = 4000;
    /** Changelog l.298: temper lost per hit [INFÉRÉ: twice the +5 a failed ride gives]. */
    public static final int HARM_TEMPER = 10;
    /** Changelog l.297: shying distance [INFÉRÉ: 8 blocks, the livestock flight distance]. */
    public static final float SHY_DISTANCE = 8;

    /** Game times of the last accepted bribe and of the end of the riding refusal. */
    public static final class State {
        public long lastBribe = Long.MIN_VALUE / 2, refuseUntil;
    }

    private static final MapCodec<State> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.optionalFieldOf("last_bribe", Long.MIN_VALUE / 2).forGetter(s -> s.lastBribe),
            Codec.LONG.optionalFieldOf("refuse_until", 0L).forGetter(s -> s.refuseUntil)
    ).apply(i, (bribe, refuse) -> {
        State s = new State();
        s.lastBribe = bribe;
        s.refuseUntil = refuse;
        return s;
    }));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<State>> STATE = LivestockService.ATTACHMENTS.register("horse_taming",
            () -> AttachmentType.builder(State::new).serialize(dev.mite.attach.Migrations.versioned("horse_taming", CODEC)).build());

    /** An untamed horse, donkey or mule (MITE taming rules apply). */
    public static boolean wild(Entity e) {
        return e instanceof AbstractHorse h && !(h instanceof Llama) && !h.isTamed();
    }

    public static State state(AbstractHorse horse) {
        return horse.getData(STATE);
    }

    /** Changelog l.296, called from RunAroundLikeCrazyGoalMixin when the horse throws its rider off. */
    public static void onRiderThrown(AbstractHorse horse) {
        if (wild(horse)) state(horse).refuseUntil = horse.level().getGameTime() + REFUSE_TICKS;
    }

    /** Changelog l.297 [INFÉRÉ: "tend to"]: a sneaking player can still creep up on the horse. */
    public static boolean shiesFrom(Player player) {
        return !player.isDiscrete() && !player.isSpectator() && !player.isCreative();
    }

    /** Changelog l.297 and l.285: a wild, riderless horse walks away from players and stops grazing while it does. */
    static final class ShyGoal extends AvoidEntityGoal<Player> {
        private final AbstractHorse horse;

        ShyGoal(AbstractHorse horse) {
            super(horse, Player.class, SHY_DISTANCE, 1.0, 1.2, p -> p instanceof Player player && shiesFrom(player));
            this.horse = horse;
        }

        @Override
        public boolean canUse() {
            return wild(horse) && !horse.isVehicle() && !horse.isLeashed() && super.canUse();
        }

        @Override
        public void start() {
            horse.setEating(false);
            super.start();
        }

        @Override
        public void tick() {
            horse.setEating(false);
            super.tick();
        }
    }

    /** Changelog l.295: food given to a wild horse within the cooldown is refused. */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !(e.getTarget() instanceof AbstractHorse h) || !wild(h) || h.isVehicle()
                || !h.isFood(e.getItemStack())) return;
        State s = state(h);
        long now = e.getLevel().getGameTime();
        if (now - s.lastBribe < BRIBE_COOLDOWN) {
            h.makeMad();
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        } else if (h.getTemper() < h.getMaxTemper()) {
            s.lastBribe = now;
        }
    }

    /** Changelog l.296: no riding during the refusal. */
    private static void onMount(EntityMountEvent e) {
        if (e.isMounting() && e.getEntityMounting() instanceof Player && wild(e.getEntityBeingMounted())
                && e.getLevel().getGameTime() < state((AbstractHorse) e.getEntityBeingMounted()).refuseUntil) e.setCanceled(true);
    }

    /** Changelog l.284 (every horse stops grazing) and l.298 (a wild horse loses temper). */
    private static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof AbstractHorse h) || h.level().isClientSide()) return;
        h.setEating(false);
        if (wild(h)) h.modifyTemper(-HARM_TEMPER);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractHorse h) || h instanceof Llama) return;
        if (h.goalSelector.getAvailableGoals().stream().noneMatch(w -> w.getGoal() instanceof ShyGoal)) h.goalSelector.addGoal(2, new ShyGoal(h));
    }

    static void register(IEventBus bus) {
        bus.addListener(HorseTaming::onInteract);
        bus.addListener(HorseTaming::onMount);
        bus.addListener(HorseTaming::onDamage);
        bus.addListener(HorseTaming::onJoin);
    }
}
