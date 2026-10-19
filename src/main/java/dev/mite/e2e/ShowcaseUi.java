package dev.mite.e2e;

import static dev.mite.e2e.Showcase.Scene.gif;
import static dev.mite.e2e.Showcase.Scene.shot;
import static dev.mite.e2e.Showcase.run;
import static dev.mite.e2e.Showcase.runAsPlayer;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.Metabolism;
import dev.mite.core.Quality;
import dev.mite.crafting.CraftQuality;
import dev.mite.crafting.TimedCrafting;
import dev.mite.crafting.client.CraftingClient;
import dev.mite.curse.Curse;
import dev.mite.curse.Curses;
import dev.mite.material.MaterialItems;
import dev.mite.metabolism.MetabolismService;
import dev.mite.player.ProgressionService;
import dev.mite.rules.Hints;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * README showcase, group "ui": every player-facing interface. The player stands on a platform at y 200 facing south; screens of
 * blocks are opened by a real right click on a block placed 2 blocks ahead. Hover tooltips are real: the mouse position is
 * forced every frame ({@link #hover}), so a moving real mouse cannot disturb them.
 */
final class ShowcaseUi {
    private ShowcaseUi() {}

    private static final Minecraft MC = Minecraft.getInstance();
    private record Timer(long due, Runnable task) {}

    private static final List<Timer> TIMERS = new ArrayList<>();
    private static long now;
    /** GUI coordinates the mouse is held at; by default parked in the top left corner, away from every widget. */
    private static final int[] PARKED = {1, 1};
    private static int[] hover = PARKED;

    static {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            now++;
            MC.gui.toastManager().clear(); // advancement and recipe toasts would cover the screens
            for (Timer t : List.copyOf(TIMERS)) if (t.due() <= now) {
                TIMERS.remove(t);
                t.task().run();
            }
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Pre e) -> {
            var w = MC.getWindow();
            setMouse(hover[0] * (double) w.getScreenWidth() / w.getGuiScaledWidth() + 1, hover[1] * (double) w.getScreenHeight() / w.getGuiScaledHeight() + 1);
        });

        // ---- HUD ----
        add(shot("hud", 80, () -> {
            run("gamerule send_command_feedback false");
            run("gamerule show_advancement_messages false");
            run("weather clear");
            run("time set 1000");
            run("kill @e[type=!player]");
            run("execute at @p run fill ~-6 199 ~-6 ~6 199 ~6 minecraft:grass_block");
            run("execute at @p run fill ~-6 200 ~-6 ~6 206 ~6 minecraft:air");
            run("execute at @p run tp @p ~ 200 ~ 0 0");
            run("clear @p");
            server(p -> {
                ProgressionService.setXp(p, 0);
                ProgressionService.addLevels(p, 3);
                p.setHealth(p.getMaxHealth() - 3);
                Metabolism m = MetabolismService.get(p);
                m.satiety = m.nutrition = 7;
                m.syncedKey = -1;
                p.getInventory().setItem(0, new ItemStack(item("hardscrabble:flint_hatchet")));
                p.getInventory().setItem(1, new ItemStack(item("minecraft:torch"), 12));
                p.getInventory().setItem(2, new ItemStack(item("minecraft:bread"), 3));
            });
            MC.player.setXRot(10);
        }));
        add(shot("hud_malnourished", 60, () -> server(p -> { // protein and phytonutrients empty: P V marker and the hint in chat
            Metabolism m = MetabolismService.get(p);
            m.protein = m.phyto = 0;
            m.syncedKey = -1;
        })));
        add(shot("hud_curse", 60, () -> server(p -> { // a witch's curse, already active and discovered
            Curses.curse(p, UUID.randomUUID(), Curse.CANT_RUN, p.level().getGameTime() - Curses.DELAY - 40);
            later(30, () -> server(q -> Curses.trigger(q, Curse.CANT_RUN)));
        })));
        add(shot("hud_curse_tooltip", 20, () -> { // in an inventory screen, hovering the icon names the curse
            MC.gui.setScreen(new InventoryScreen(MC.player));
            hover = new int[] {8, 8};
        }));

        // ---- Tooltips (hotbar slots of the inventory screen) ----
        add(shot("tooltip_tool", 30, () -> {
            server(p -> {
                ItemStack pick = new ItemStack(item("hardscrabble:mithril_pickaxe"));
                pick.set(CraftQuality.QUALITY.get(), Quality.EXCELLENT);
                pick.setDamageValue(pick.getMaxDamage() / 3);
                ItemStack chest = new ItemStack(item("hardscrabble:mithril_chestplate"));
                chest.setDamageValue(chest.getMaxDamage() / 5);
                p.getInventory().setItem(9, pick); // first row of the main inventory: room below for the tooltip
                p.getInventory().setItem(10, chest);
                p.getInventory().setItem(11, new ItemStack(item("minecraft:cooked_beef"), 4));
                p.getInventory().setItem(12, new ItemStack(item("minecraft:clock")));
            });
            later(5, () -> hoverSlot(9));
        }));
        add(shot("tooltip_armor", 20, () -> hoverSlot(10)));
        add(shot("tooltip_food", 20, () -> hoverSlot(11)));
        add(shot("tooltip_clock", 20, () -> hoverSlot(12)));

        // ---- Timed crafting ----
        add(gif("craft_inventory", 6, () -> { // 4 clay blocks -> clay furnace in the 2x2 grid, the craft held on the result slot
            server(p -> ProgressionService.addLevels(p, 12));
            MC.gui.setScreen(new InventoryScreen(MC.player));
            craft(List.of("minecraft:clay", "minecraft:clay", "minecraft:clay", "minecraft:clay"));
        }, 24, 4, () -> {}));
        add(gif("craft_workbench", 19, () -> { // copper shovel on a copper workbench, target quality Fine
            openBlock("hardscrabble:copper_workbench");
            later(15, () -> {
                setTarget(Quality.FINE);
                craft(List.of("", "minecraft:copper_ingot", "", "", "minecraft:stick", "", "", "minecraft:stick", ""));
            });
        }, 32, 7, () -> {}));
        add(shot("craft_refused", 25, () -> { // iron pickaxe on the copper workbench: action bar refusal and the workbench hint
            release();
            server(p -> {
                for (int i = 1; i <= 9; i++) p.containerMenu.getSlot(i).set(ItemStack.EMPTY);
            });
            later(5, () -> craft(List.of("minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "", "minecraft:stick", "", "", "minecraft:stick", "")));
        }));
        add(shot("craft_refused_hud", 10, () -> { // the action bar alone (the hint is in chat_hints)
            setTarget(Quality.AVERAGE);
            MC.gui.setScreen(null);
        }));

        // ---- Furnaces, anvil, enchanting, strongbox ----
        add(shot("furnace_clay", 40, () -> { // clay oven: coal is too hot a fuel
            openBlock("hardscrabble:clay_furnace");
            fill(0, "minecraft:beef", 4);
            fill(1, "minecraft:coal", 8);
        }));
        add(shot("furnace_stone", 60, () -> { // stone furnace at heat 2 smelting raw copper
            openBlock("minecraft:furnace");
            fill(0, "minecraft:raw_copper", 8);
            fill(1, "minecraft:coal", 8);
        }));
        add(shot("furnace_too_cold", 40, () -> { // raw mithril needs heat 3
            openBlock("minecraft:furnace");
            fill(0, "hardscrabble:raw_mithril", 4);
            fill(1, "minecraft:coal", 8);
        }));
        add(shot("anvil", 40, () -> { // copper anvil: a worn copper pickaxe repaired with copper nuggets
            openBlock("hardscrabble:copper_anvil");
            later(15, () -> server(p -> {
                ItemStack pick = new ItemStack(item("minecraft:copper_pickaxe"));
                pick.setDamageValue(pick.getMaxDamage() * 2 / 3);
                p.containerMenu.getSlot(0).set(pick);
                p.containerMenu.getSlot(1).set(new ItemStack(MaterialItems.nugget(Material.COPPER), 12));
            }));
        }));
        add(shot("enchanting", 40, () -> { // enchanting table: costs charged in MITE XP points
            run("execute at @p run fill ~-2 200 ~3 ~2 201 ~4 minecraft:bookshelf");
            openBlock("minecraft:enchanting_table");
            later(15, () -> server(p -> {
                p.containerMenu.getSlot(0).set(new ItemStack(item("minecraft:iron_pickaxe")));
                p.containerMenu.getSlot(1).set(new ItemStack(item("minecraft:lapis_lazuli"), 16));
            }));
        }));
        add(shot("strongbox", 40, () -> {
            run("execute at @p run fill ~-2 200 ~3 ~2 201 ~4 minecraft:air");
            openBlock("hardscrabble:silver_strongbox");
            later(15, () -> server(p -> {
                String[] loot = {"minecraft:diamond", "hardscrabble:mithril_ingot", "hardscrabble:silver_coin", "minecraft:emerald", "hardscrabble:raw_mithril"};
                for (int i = 0; i < loot.length; i++) p.containerMenu.getSlot(i).set(new ItemStack(item(loot[i]), 3 + i));
            }));
        }));
        add(shot("strongbox_locked", 30, () -> { // someone else's strongbox: action bar refusal
            MC.gui.setScreen(null);
            server(p -> {
                if (p.level().getBlockEntity(ahead()) instanceof dev.mite.enchanting.StrongboxBlockEntity box) box.setOwner(UUID.randomUUID());
            });
            later(5, () -> click(ahead()));
        }));

        // ---- Chat: commands, hints, curse lifted ----
        add(shot("chat_commands", 40, () -> {
            run("gamerule send_command_feedback true");
            chat();
            runAsPlayer("commands");
            runAsPlayer("villages");
        }));
        add(shot("chat_stats", 40, () -> {
            chat();
            runAsPlayer("stats");
            runAsPlayer("state");
        }));
        add(shot("chat_hints", 50, () -> { // one-time contextual hints, and the disenchanting bottle lifting the curse
            run("gamerule send_command_feedback false");
            chat();
            server(p -> {
                p.getData(Hints.SEEN).clear();
                Hints.show(p, "log");
                Hints.show(p, "gravel");
                Hints.show(p, "night");
                Hints.show(p, "workbench", Component.translatable("block.hardscrabble.iron_workbench"));
                p.getInventory().setItem(8, new ItemStack(dev.mite.enchanting.EnchantingContent.DISENCHANTING_BOTTLE.get()));
            });
            later(10, () -> {
                MC.player.getInventory().setSelectedSlot(8);
                MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
            });
        }));

        // ---- Screens ----
        add(shot("advancements", 40, () -> {
            run("advancement grant @p until hardscrabble:build_pickaxe");
            run("advancement grant @p until hardscrabble:build_furnace");
            run("advancement grant @p only hardscrabble:flint_finder");
            later(10, () -> {
                MC.gui.toastManager().clear();
                var adv = MC.player.connection.getAdvancements();
                MC.gui.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(adv));
                adv.setSelectedTab(adv.get(MiteMod.id("root")), true);
            });
        }));
        add(shot("mods_config", 30, () -> { // Mods > Hardscrabble: the Config button is enabled
            MC.gui.setScreen(net.neoforged.neoforge.client.gui.modlist.ModListScreen.create(null));
            later(3, () -> {
                for (var c : MC.gui.screen().children()) if (c instanceof net.minecraft.client.gui.components.EditBox e) e.setValue("Hardscrabble");
                later(2, () -> {
                    for (var c : MC.gui.screen().children())
                        if (c instanceof net.minecraft.client.gui.components.AbstractSelectionList<?> l && !l.children().isEmpty()) selectFirst(l);
                    later(2, () -> {
                        if (find(MC.gui.screen(), "Config") instanceof net.minecraft.client.gui.components.AbstractWidget w)
                            hover = new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
                    });
                });
            });
        }));
        add(shot("config_root", 20, () -> press(MC.gui.screen(), "Config"))); // the screen that button opens
        add(shot("config", 30, () -> {
            // Config > World Rules... > Rules (first section)
            var root = new net.neoforged.neoforge.client.gui.ConfigurationScreen(ModList.get().getModContainerById(MiteMod.MODID).orElseThrow(), null);
            var type = net.neoforged.fml.config.ModConfig.Type.SYNCED;
            net.neoforged.fml.config.ModConfigs.getConfigSet(type).stream().filter(c -> c.getModId().equals(MiteMod.MODID)).findFirst().ifPresent(c ->
                    MC.gui.setScreen(new net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen(root, type, c, root.translatableConfig(c, ".title", "neoforge.configuration.uitext.title.synced"))));
            later(5, () -> press(MC.gui.screen(), "Edit"));
        }));
        add(shot("create_world", 60, () -> { // world creation starts on Hard (and Large Biomes, World tab)
            MC.gui.setScreen(null);
            net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(MC, () -> MC.gui.setScreen(null));
        }));
        add(shot("death", 60, () -> { // 120 s forced respawn countdown
            MC.gui.setScreen(null);
            run("kill @p");
        }));
        add(shot("death_fr", 200, () -> { // the same screen in French
            MC.options.languageCode = "fr_fr";
            MC.getLanguageManager().setSelected("fr_fr");
            MC.reloadResourcePacks();
        }));
    }

    private static void add(Showcase.Scene s) {
        Showcase.add("ui", new Showcase.Scene(s.name(), s.waitTicks(), () -> {
            hover = PARKED;
            MC.gui.hud.getChat().clearMessages(false);
            s.setup().run();
        }, s.frames(), s.every(), s.tick()));
    }

    private static void later(int ticks, Runnable r) {
        TIMERS.add(new Timer(now + ticks, r));
    }

    private static void server(Consumer<ServerPlayer> action) {
        var server = MC.getSingleplayerServer();
        UUID id = MC.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static BlockPos ahead() {
        return MC.player.blockPosition().south(2);
    }

    /** Replaces the block 2 blocks ahead and right-clicks it with an empty hand. */
    private static void openBlock(String block) {
        release();
        MC.gui.setScreen(null);
        run("execute at @p run setblock ~ ~ ~2 minecraft:air");
        run("execute at @p run setblock ~ ~ ~2 " + block);
        later(8, () -> click(ahead()));
    }

    private static void click(BlockPos pos) {
        MC.player.getInventory().setSelectedSlot(7);
        MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
    }

    /** Puts items in a container block entity ahead (server). */
    private static void fill(int slot, String id, int count) {
        BlockPos pos = ahead();
        server(p -> {
            if (p.level().getBlockEntity(pos) instanceof Container c) c.setItem(slot, new ItemStack(item(id), count));
        });
    }

    /** Fills the crafting grid of the open menu (slots 1..n) and holds the craft action on the result slot. */
    private static void craft(List<String> grid) {
        server(p -> {
            for (int i = 0; i < grid.size(); i++) p.containerMenu.getSlot(i + 1).set(grid.get(i).isEmpty() ? ItemStack.EMPTY : new ItemStack(item(grid.get(i))));
        });
        later(3, () -> {
            server(p -> {
                TimedCrafting.hold(p, false);
                MiteMod.LOGGER.info("SHOWCASE craft ticks={}", TimedCrafting.pendingTicks(p));
            });
            setStatic(CraftingClient.class, "holding", true);
        });
    }

    private static void release() {
        setStatic(CraftingClient.class, "holding", false);
        server(TimedCrafting::release);
    }

    /** Picks the target quality like the button under the result slot would. */
    private static void setTarget(Quality q) {
        try {
            setStatic(CraftingClient.class, "target", q);
            Field f = CraftingClient.class.getDeclaredField("button");
            f.setAccessible(true);
            if (f.get(null) instanceof Button b) b.setMessage(Component.translatable(CraftQuality.langKey(q)));
            var send = CraftingClient.class.getDeclaredMethod("sendTarget");
            send.setAccessible(true);
            send.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void hoverSlot(int index) {
        if (!(MC.gui.screen() instanceof InventoryScreen)) MC.gui.setScreen(new InventoryScreen(MC.player));
        later(2, () -> {
            if (!(MC.gui.screen() instanceof AbstractContainerScreen<?> s)) return;
            var slot = s.getMenu().getSlot(index);
            hover = new int[] {s.getLeftPos() + slot.x + 8, s.getTopPos() + slot.y + 8};
        });
    }

    /** Presses the first button (depth first) whose label contains {@code label}. */
    private static boolean press(net.minecraft.client.gui.components.events.GuiEventListener l, String label) {
        if (!(find(l, label) instanceof net.minecraft.client.gui.components.AbstractButton b)) return false;
        b.onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0));
        return true;
    }

    /** The first button (depth first) whose label contains {@code label}, or null. */
    private static net.minecraft.client.gui.components.AbstractButton find(net.minecraft.client.gui.components.events.GuiEventListener l, String label) {
        if (l instanceof net.minecraft.client.gui.components.AbstractButton b && b.getMessage().getString().contains(label)) return b;
        if (l instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c)
            for (var child : c.children()) if (find(child, label) instanceof net.minecraft.client.gui.components.AbstractButton b) return b;
        return null;
    }

    /** Selects the first entry of a list (reflection: the entry type is protected). */
    private static void selectFirst(net.minecraft.client.gui.components.AbstractSelectionList<?> list) {
        try {
            for (var m : net.minecraft.client.gui.components.AbstractSelectionList.class.getMethods())
                if (m.getName().equals("setSelected") && m.getParameterCount() == 1) m.invoke(list, list.children().getFirst());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void chat() {
        MC.gui.setScreen(null);
        MC.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
    }

    private static void setMouse(double x, double y) {
        setField(MC.mouseHandler, "xpos", x);
        setField(MC.mouseHandler, "ypos", y);
    }

    private static void setStatic(Class<?> c, String name, Object value) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(MouseHandler m, String name, double value) {
        try {
            Field f = MouseHandler.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(m, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
