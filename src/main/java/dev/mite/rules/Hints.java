package dev.mite.rules;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.metabolism.MetabolismService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * EF-UI-06 / EF-PRG-02 CA-2: one-time help messages per world and player, sent as system chat messages
 * {@code hardscrabble.hint.<id>}. The client drops them when {@code hints.contextual_hints} is off ({@code client/HintFilter}).
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class Hints {
    private Hints() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);

    /** Hints already shown. Not synced (safe for fake players), kept on death. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Set<String>>> SEEN = ATTACHMENTS.register("hints_seen",
            () -> AttachmentType.<Set<String>>builder(() -> new HashSet<>())
                    .serialize(dev.mite.attach.Migrations.versioned("hints_seen", Codec.STRING.listOf().<Set<String>>xmap(HashSet::new, ArrayList::new).fieldOf("seen")))
                    .copyOnDeath().build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    /** Shows the hint once; returns whether it was new. */
    public static boolean show(ServerPlayer player, String id, Object... args) {
        if (!player.getData(SEEN).add(id)) return false;
        if (player.connection != null) player.sendSystemMessage(Component.translatable("hardscrabble.hint." + id, args));
        return true;
    }

    @SubscribeEvent
    static void onBreak(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer p && event.getState().is(Blocks.GRAVEL)) show(p, "gravel");
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (p.level().isDarkOutside()) show(p, "night");
        if (MetabolismService.get(p).malnourished()) show(p, "malnutrition");
    }
}
