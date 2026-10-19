package dev.mite.metallurgy;

import dev.mite.MiteMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Furnace screen overlay: effective heat, required heat and the reason a recipe is blocked (EF-UI-04). */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class MetallurgyClient {
    private MetallurgyClient() {}

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractFurnaceScreen<?> screen) || !(screen.getMenu() instanceof FurnaceHeat.Status status)) return;
        int packed = status.mite$heatStatus();
        int effective = packed & 15, required = packed >> 4 & 15;
        if (required == 0) return;
        boolean refused = (packed & 256) != 0, cold = effective < required, fuelRefused = (packed & 512) != 0;
        Component text = refused ? Component.translatable("gui.hardscrabble.furnace.food_only")
                : cold && fuelRefused ? Component.translatable("gui.hardscrabble.furnace.fuel_refused")
                : Component.translatable(cold ? "gui.hardscrabble.furnace.too_cold" : "gui.hardscrabble.furnace.heat", effective, required);
        var font = Minecraft.getInstance().font;
        // Right-aligned under the result slot of the 176 px wide furnace background, wrapped between the fuel slot (x 74) and the
        // right edge; up to 3 lines fit above the inventory slots.
        var lines = font.split(text, 96);
        int y = screen.getTopPos() + (lines.size() > 1 ? 57 : 60);
        for (var line : lines) {
            event.getGuiGraphics().text(font, line, screen.getLeftPos() + 170 - font.width(line), y, refused || cold ? 0xFFAA0000 : 0xFF404040, false);
            y += 9;
        }
    }
}
