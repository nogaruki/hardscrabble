package dev.mite.client;

import dev.mite.MiteMod;
import dev.mite.combat.CombatContent;
import dev.mite.config.MiteClientConfig;
import dev.mite.lunar.Lunar;
import dev.mite.rules.RespawnTimer;
import dev.mite.core.Material;
import dev.mite.harvest.ToolStats;
import dev.mite.metabolism.MetabolismContent;
import dev.mite.metabolism.Nutrition;
import dev.mite.metallurgy.FurnaceHeat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Death screen countdown (EF-UI-05, B06) and MITE tooltip lines (EF-UI-03, P02). Events only: no DeathScreen mixin needed. */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class MiteClientUi {
    private MiteClientUi() {}

    /** Seconds left before the forced respawn, as last sent by the server (RespawnTimer.Timer, dom-B-04); -1 = unknown. */
    private static int secondsLeft = -1;

    @SubscribeEvent
    static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(RespawnTimer.Timer.TYPE, (payload, context) -> secondsLeft = payload.seconds());
    }

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DeathScreen) secondsLeft = -1;
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof DeathScreen screen) || secondsLeft < 0) return;
        event.getGuiGraphics().centeredText(mc.font, Component.translatable("hardscrabble.death.respawn_in", secondsLeft), screen.width / 2, 115, 0xFFFFFF55);
    }

    /** Changelog l.101: no "Leave Bed" button, so the health bar stays visible while resting (Escape still wakes up). */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InBedChatScreen)) return;
        List.copyOf(event.getListenersList()).stream()
                .filter(w -> w instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("multiplayer.stopSleeping"))
                .forEach(event::removeListener);
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = new ArrayList<>();
        // Raw durability: the vanilla advanced tooltip line.
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage(), left = max - stack.getDamageValue();
            lines.add(MiteClientConfig.DURABILITY_AS_PERCENT.getAsBoolean() ? line("tooltip.hardscrabble.durability", left * 100 / max)
                    : line("tooltip.hardscrabble.durability_points", left, max)); // A-3: client key tooltip.durability_as_percent
        }
        ToolStats.of(stack).ifPresent(tool -> {
            lines.add(line("tooltip.hardscrabble.material", material(tool.material())));
            if (stack.has(DataComponents.TOOL)) lines.add(line("tooltip.hardscrabble.harvest_level", tool.material().toolHarvestLevel()));
        });
        CombatContent.ARMORS.stream().filter(a -> a.item().get() == stack.getItem()).findFirst()
                .ifPresent(a -> lines.add(line("tooltip.hardscrabble.material", material(a.material()))));
        Integer heat = stack.typeHolder().getData(FurnaceHeat.FUEL_HEAT);
        if (heat != null) lines.add(line("tooltip.hardscrabble.fuel_heat", heat));
        Nutrition n = stack.typeHolder().getData(MetabolismContent.NUTRITION);
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (n == null && food != null) n = new Nutrition(food.nutrition(), food.nutrition(), 0, false, false); // MetabolismService.eat fallback
        if (n != null) {
            lines.add(line("tooltip.hardscrabble.food", n.satiety(), n.nutrition()));
            if (n.protein()) lines.add(line("tooltip.hardscrabble.protein"));
            if (n.phyto()) lines.add(line("tooltip.hardscrabble.phyto"));
            if (n.sugar() > 0) lines.add(line("tooltip.hardscrabble.sugar", n.sugar()));
        }
        // EF-UI-08: the clock announces tonight's moon (same stateless calendar as the server).
        Minecraft mc = Minecraft.getInstance();
        if (stack.is(Items.CLOCK) && mc.level != null)
            lines.add(line("tooltip.hardscrabble.evening_moon", Component.translatable("moon.hardscrabble." + Lunar.moon(mc.level).name().toLowerCase(Locale.ROOT))));
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    private static Component material(Material m) {
        return Component.translatable("material.hardscrabble." + m.id());
    }
}
