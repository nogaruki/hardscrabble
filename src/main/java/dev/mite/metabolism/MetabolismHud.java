package dev.mite.metabolism;

import dev.mite.MiteMod;
import dev.mite.core.Metabolism;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * EF-UI-01: the food bar shows cap / 2 drumsticks (3 to 10) filled from the synced nutrition. Left of the bar, glyphs (shapes,
 * not only a colour, ENF-ACC-01) flag missing nutrients (P, V), the insulin resistance level (I1 to I3) and zero food energy.
 * Hearts already follow max health.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class MetabolismHud {
    private MetabolismHud() {}

    private static final Identifier EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier EMPTY_HUNGER = Identifier.withDefaultNamespace("hud/food_empty_hunger");
    private static final Identifier HALF_HUNGER = Identifier.withDefaultNamespace("hud/food_half_hunger");
    private static final Identifier FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");

    @SubscribeEvent
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.FOOD_LEVEL, MetabolismHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Hud hud = mc.gui.hud;
        // replaceLayer drops the vanilla survival-only condition, so it is checked here.
        if (hud.isHidden() || !mc.gameMode.canHurtPlayer() || !(mc.getCameraEntity() instanceof Player player)) return;
        if (player.getVehicle() instanceof LivingEntity vehicle && vehicle.showVehicleHealth()) return;

        Metabolism m = MetabolismService.get(player);
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        int drumsticks = MetabolismService.cap(player) / 2;
        int xRight = g.guiWidth() / 2 + 91;
        int y = g.guiHeight() - hud.rightHeight;
        for (int i = 0; i < drumsticks; i++) {
            int x = xRight - i * 8 - 9;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? EMPTY_HUNGER : EMPTY, x, y, 9, 9);
            if (i * 2 + 1 < m.nutrition) g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? FULL_HUNGER : FULL, x, y, 9, 9);
            else if (i * 2 + 1 == m.nutrition) g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? HALF_HUNGER : HALF, x, y, 9, 9);
        }
        String marker = (m.protein == 0 ? Component.translatable("hud.hardscrabble.protein").getString() : "")
                + (m.phyto == 0 ? Component.translatable("hud.hardscrabble.phyto").getString() : "")
                + (m.insulinLevel > 0 ? " " + Component.translatable("hud.hardscrabble.insulin", m.insulinLevel).getString() : "")
                + (m.hasEnergy() ? "" : " " + Component.translatable("hud.hardscrabble.zero_energy").getString());
        marker = marker.strip();
        if (!marker.isEmpty() && dev.mite.config.MiteClientConfig.SHOW_INDICATORS.getAsBoolean()) { // A-3: hud.show_indicators
            g.text(mc.font, marker, xRight - drumsticks * 8 - 11 - mc.font.width(marker), y + 1, 0xFFFF5555);
        }
        hud.rightHeight += 10;
    }
}
