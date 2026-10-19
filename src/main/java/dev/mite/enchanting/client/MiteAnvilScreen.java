package dev.mite.enchanting.client;

import dev.mite.MiteMod;
import dev.mite.enchanting.EnchantingContent;
import dev.mite.enchanting.MiteAnvilMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** MITE anvil screen: the vanilla anvil background without the name field, and the nugget cost (EF-ENC-01). */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public class MiteAnvilScreen extends ItemCombinerScreen<MiteAnvilMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/anvil.png");
    private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/anvil/error");

    public MiteAnvilScreen(MiteAnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
        this.titleLabelX = 60;
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EnchantingContent.ANVIL_MENU.get(), MiteAnvilScreen::new);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        super.extractLabels(graphics, xm, ym);
        if (menu.getCost() <= 0) return;
        Component line = Component.translatable("gui.hardscrabble.anvil.cost", menu.getCost());
        graphics.text(font, line, imageWidth - 10 - font.width(line), 69, 0xFF80FF20);
    }

    /** anvil.png leaves a red placeholder where vanilla blits the name field sprite: no renaming here, paint it over. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(leftPos + 59, topPos + 20, leftPos + 169, topPos + 36, 0xFFC6C6C6);
    }

    @Override
    protected void extractErrorIcon(GuiGraphicsExtractor graphics, int xo, int yo) {
        if ((menu.getSlot(0).hasItem() || menu.getSlot(1).hasItem()) && !menu.getSlot(menu.getResultSlot()).hasItem()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE, xo + 99, yo + 45, 28, 21);
        }
    }
}
