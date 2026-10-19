package dev.mite.player;

import dev.mite.MiteMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * ET-B-03: the MITE level replaces the vanilla level number, red with its minus sign when negative (EF-JOU-03). The vanilla
 * bar is kept: it reads the mirrored progress, already on the MITE curve, negative levels included.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class XpHud {
    private XpHud() {}

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, XpHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.player == null || !mc.gameMode.hasExperience()) return;
        int level = PlayerLevel.get(mc.player);
        if (level == 0) return;
        Component str = Component.translatable("gui.experience.level", level);
        int x = (g.guiWidth() - mc.font.width(str)) / 2;
        int y = g.guiHeight() - 24 - 9 - 2;
        g.text(mc.font, str, x + 1, y, 0xFF000000, false);
        g.text(mc.font, str, x - 1, y, 0xFF000000, false);
        g.text(mc.font, str, x, y + 1, 0xFF000000, false);
        g.text(mc.font, str, x, y - 1, 0xFF000000, false);
        g.text(mc.font, str, x, y, level < 0 ? 0xFFFF5555 : 0xFF80FF20, false);
    }
}
