package dev.mite.rules;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * EF-JOU-06 / B06: a dead player respawns on his own after {@code rules.respawn_timer_seconds}. Time keeps running in solo
 * without help: the vanilla DeathScreen is not a pause screen, so the integrated server never pauses on it. The death time is
 * a saved player attachment (survives a relog and a restart) and the server sends the seconds left (EF-UI-05, dom-B-04).
 * The respawn button stays usable: the changelog (l.767) makes the countdown a maximum, not a minimum.
 */
public final class RespawnTimer {
    private RespawnTimer() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);
    /** Overworld game time at which the player was first seen dead, -1 while alive. */
    public static final Supplier<AttachmentType<Long>> DEATH_TIME = ATTACHMENTS.register("respawn",
            () -> AttachmentType.builder(() -> -1L).serialize(dev.mite.attach.Migrations.versioned("respawn", Codec.LONG.fieldOf("death_time"))).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Timer.TYPE, Timer.STREAM_CODEC));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer(), delayTicks()));
    }

    /** Config {@code rules.respawn_timer_seconds} in ticks. */
    static int delayTicks() {
        return MiteConfig.RESPAWN_TIMER_SECONDS.getAsInt() * 20;
    }

    /** Respawns every player dead for at least {@code delayTicks}, through the same path as the death screen button. */
    public static void tick(MinecraftServer server, int delayTicks) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers().toArray(ServerPlayer[]::new)) {
            if (!p.isDeadOrDying()) {
                if (p.hasData(DEATH_TIME)) p.removeData(DEATH_TIME);
                continue;
            }
            if (!p.hasData(DEATH_TIME)) p.setData(DEATH_TIME, now);
            long left = ticksLeft(p, now, delayTicks);
            if (left <= 0) {
                p.removeData(DEATH_TIME);
                p.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            } else if ((now - p.getData(DEATH_TIME)) % 20 == 0 && p.connection.hasChannel(Timer.TYPE)) {
                PacketDistributor.sendToPlayer(p, new Timer((int) ((left + 19) / 20)));
            }
        }
    }

    /** Ticks before the forced respawn of a dead player. */
    public static long ticksLeft(ServerPlayer p, long now, int delayTicks) {
        return delayTicks - (now - p.getData(DEATH_TIME));
    }

    /** S2C {@code hardscrabble:death_timer}: seconds before the forced respawn, sent every second while dead. */
    public record Timer(int seconds) implements CustomPacketPayload {
        public static final Type<Timer> TYPE = new Type<>(MiteMod.id("death_timer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Timer> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Timer::seconds, Timer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
