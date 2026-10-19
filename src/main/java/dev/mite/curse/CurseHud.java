package dev.mite.curse;

import dev.mite.MiteMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * EF-UI-08 (O-3): active curse indicator, top left: a "?" icon until discovered, then the curse's own icon (a distinct glyph,
 * not only a colour). Hovering it in an inventory screen names the curse; /state names it too.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class CurseHud {
    private CurseHud() {}

    static final int X = 4, Y = 4, SIZE = 9;

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EFFECTS, MiteMod.id("curse"), CurseHud::render);
    }

    /** Sprite {@code hardscrabble:hud/curse/<curse>} once revealed, {@code hardscrabble:hud/curse/unknown} before. */
    static Identifier icon(Curses.View view) {
        return MiteMod.id("hud/curse/" + (view.revealed() ? Curse.values()[view.curse()].id() : "unknown"));
    }

    static Component name(Curses.View view) {
        return Component.translatable("hardscrabble.curse.hud", view.revealed() ? Component.translatable(Curse.values()[view.curse()].langKey()) : Component.literal("?"));
    }

    private static Curses.View view() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getData(Curses.VIEW);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Curses.View view = view();
        if (mc.gui.hud.isHidden() || view == null || view.curse() < 0) return;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon(view), X, Y, SIZE, SIZE);
    }

    /**
     * In inventory screens the icon is drawn above the dimmed background and its name shows on hover. Foreground, not Post: Post
     * fires after the screen has drawn its deferred tooltip, so a tooltip set there was never shown.
     */
    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Foreground event) {
        Curses.View view = view();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>) || view == null || view.curse() < 0) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon(view), X, Y, SIZE, SIZE);
        int mx = event.getMouseX(), my = event.getMouseY();
        if (mx >= X && mx < X + SIZE && my >= Y && my < Y + SIZE) g.setTooltipForNextFrame(name(view), mx, Y + 12); // level with the icon: the default positioner lifts it 12 px and never clamps the top
    }
}
