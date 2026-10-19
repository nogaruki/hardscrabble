package dev.mite.crafting.client;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Quality;
import dev.mite.crafting.CraftQuality;
import dev.mite.crafting.TimedCrafting;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Craft progress on the inventory and crafting screens (EF-UI-02): hold on the result slot (EF-CRA-01), ghost result, progress bar, remaining seconds, and the target
 * quality button with the quality obtained and its XP cost (EF-CRA-05).
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class CraftingClient {
    private CraftingClient() {}

    private static int ticks;
    private static long start;
    private static ItemStack result = ItemStack.EMPTY;
    private static int quality = -1, xpCost;
    private static Quality target = Quality.AVERAGE;
    private static Button button;
    private static boolean holding;
    /** Target last sent to this server: a screen re-init (resize, GUI scale) must not resend it and restart the craft. */
    private static Quality sentTarget;

    @SubscribeEvent
    static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TimedCrafting.Progress.TYPE, (payload, context) -> {
            ticks = payload.ticks();
            result = payload.result();
            quality = payload.quality();
            xpCost = payload.xpCost();
            start = context.player().level().getGameTime();
        });
    }

    /** The thrown brick (changelog l.774) renders as its item. */
    @SubscribeEvent
    static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.mite.crafting.CraftingContent.THROWN_BRICK.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    private static boolean isCrafting(Object screen) {
        return screen instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof AbstractCraftingMenu;
    }

    /** Target quality button under the result slot; cycles average..legendary. */
    @SubscribeEvent
    static void init(ScreenEvent.Init.Post event) {
        if (!isCrafting(event.getScreen()) || !MiteConfig.QUALITY_ENABLED.get()) return;
        button = Button.builder(Component.translatable(CraftQuality.langKey(target)), b -> {
            target = target == Quality.LEGENDARY ? Quality.AVERAGE : Quality.values()[target.ordinal() + 1];
            b.setMessage(Component.translatable(CraftQuality.langKey(target)));
            sendTarget();
        }).bounds(0, 0, 70, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.hardscrabble.crafting.target_quality"))).build();
        place((AbstractContainerScreen<?>) event.getScreen());
        event.addListener(button);
        sendTarget();
    }

    private static void sendTarget() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(CraftQuality.Target.TYPE) || target == sentTarget) return;
        ClientPacketDistributor.sendToServer(new CraftQuality.Target(target));
        sentTarget = target;
    }

    @SubscribeEvent
    static void loggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        sentTarget = null; // the next server starts from average
    }

    /**
     * Under the result slot, below the craft progress bar, between the crafting grid and the GUI's right edge (beside the GUI it
     * covered the potion effects). Sized for the longest quality name (a longer one scrolls).
     */
    private static void place(AbstractContainerScreen<?> screen) {
        var menu = (AbstractCraftingMenu) screen.getMenu();
        Slot slot = menu.getResultSlot();
        var font = Minecraft.getInstance().font;
        int label = java.util.Arrays.stream(Quality.values()).mapToInt(q -> font.width(Component.translatable(CraftQuality.langKey(q)))).max().orElse(60) + 8;
        // the 2x2 inventory has no room beside its grid: drop below the grid, right of the recipe book button (x 104-124)
        boolean inventory = screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        int left = inventory ? 126 : menu.getInputGridSlots().stream().mapToInt(s -> s.x + 17).max().orElse(0), right = screen.getImageWidth() - 1;
        int w = Math.min(label, right - left);
        int x = Math.max(left, Math.min(slot.x + 8 - w / 2, right - w));
        button.setRectangle(w, 12, screen.getLeftPos() + x, screen.getTopPos() + slot.y + (inventory ? 28 : 20));
    }

    /** EF-CRA-01: pressing on the result slot holds the craft action (with shift: chain crafts), releasing stops it. */
    @SubscribeEvent
    static void press(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!isCrafting(event.getScreen())) return;
        var screen = (AbstractContainerScreen<?>) event.getScreen();
        if (screen.getHoveredSlot() != ((AbstractCraftingMenu) screen.getMenu()).getResultSlot()) return;
        holding = true;
        sendHold(true, event.getMouseButtonEvent().hasShiftDown());
    }

    @SubscribeEvent
    static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (holding) sendHold(false, false);
    }

    @SubscribeEvent
    static void close(ScreenEvent.Closing event) {
        if (holding) sendHold(false, false);
    }

    private static void sendHold(boolean held, boolean shift) {
        holding = held;
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(TimedCrafting.Hold.TYPE)) ClientPacketDistributor.sendToServer(new TimedCrafting.Hold(held, shift));
    }

    // The recipe book moves the GUI without re-initialising the screen.
    @SubscribeEvent
    static void move(ScreenEvent.Render.Pre event) {
        if (button != null && isCrafting(event.getScreen())) place((AbstractContainerScreen<?>) event.getScreen());
    }

    @SubscribeEvent
    static void render(ScreenEvent.Render.Foreground event) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks <= 0 || mc.level == null || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof AbstractCraftingMenu menu)) return;
        if (!holding) start = mc.level.getGameTime(); // the server only counts held ticks
        Slot slot = menu.getResultSlot();
        int x = screen.getLeftPos() + slot.x, y = screen.getTopPos() + slot.y;
        long elapsed = Math.min(ticks, mc.level.getGameTime() - start);
        GuiGraphicsExtractor g = event.getGuiGraphics();
        g.fakeItem(result, x, y);
        g.fill(x, y, x + 16, y + 16, 0xA08B8B8B);
        g.fill(x, y + 17, x + 16, y + 19, 0xFF373737);
        g.fill(x, y + 17, x + (int) (16 * elapsed / ticks), y + 19, 0xFF55FF55);
        // Q-6: centred above the result slot, clear of its frame (the workbench's result slot is the big 26 px one)
        Component seconds = Component.translatable("gui.hardscrabble.crafting.seconds", String.format(Locale.ROOT, "%.1f", (ticks - elapsed) / 20f));
        g.text(mc.font, seconds, x + 8 - mc.font.width(seconds) / 2, y - (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen ? 10 : 14), 0xFF404040, false);
        if (quality >= 0) {
            Component line = Component.translatable("gui.hardscrabble.crafting.quality_cost", Component.translatable(CraftQuality.langKey(Quality.values()[quality])), xpCost);
            g.text(mc.font, line, screen.getLeftPos() + screen.getImageWidth() + 4, y + 20, 0xFFFFFFFF, true);
        }
    }
}
