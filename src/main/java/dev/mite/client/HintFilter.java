package dev.mite.client;

import dev.mite.MiteMod;
import dev.mite.config.MiteClientConfig;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** EF-UI-06 / EF-CFG-04: drops the server's help messages ({@code hardscrabble.hint.*}) when the client turned them off. */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class HintFilter {
    private HintFilter() {}

    @SubscribeEvent
    static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (event.getMessage().getContents() instanceof TranslatableContents t && t.getKey().startsWith("hardscrabble.hint.")
                && !MiteClientConfig.CONTEXTUAL_HINTS.getAsBoolean()) event.setCanceled(true);
    }
}
