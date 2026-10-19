package dev.mite.e2e;

import dev.mite.MiteMod;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * README showcase (tools/showcase/run_showcase.py), active only with {@code -Dhardscrabble.showcase=<group,group|all>}: once the
 * player is in the quick-play world it plays the scenes of the chosen groups and saves run/screenshots/showcase/<group>/<scene>.png,
 * or, for a scene with frames, one PNG per frame in run/screenshots/showcase/<group>/<scene>/ (the script turns them into a GIF).
 * Logs {@code SHOWCASE <group>/<scene> OK} per scene and {@code SHOWCASE DONE}, then quits. Scenes live in one class per group
 * (ShowcaseMobs, ShowcaseItems...), each registering into {@link #GROUPS} from a static block listed in {@link #load}.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class Showcase {
    private Showcase() {}

    /**
     * One scene: {@code setup} runs, then after {@code waitTicks} a screenshot is taken; with {@code frames > 0}, {@code frames}
     * screenshots are taken every {@code every} ticks instead (a GIF), {@code tick} running before each frame (camera moves...).
     */
    public record Scene(String name, int waitTicks, Runnable setup, int frames, int every, Runnable tick) {
        public static Scene shot(String name, int waitTicks, Runnable setup) {
            return new Scene(name, waitTicks, setup, 0, 1, () -> {});
        }

        public static Scene gif(String name, int waitTicks, Runnable setup, int frames, int every, Runnable tick) {
            return new Scene(name, waitTicks, setup, frames, every, tick);
        }
    }

    public static final Map<String, List<Scene>> GROUPS = new LinkedHashMap<>();

    public static void add(String group, Scene scene) {
        GROUPS.computeIfAbsent(group, g -> new ArrayList<>()).add(scene);
    }

    private static List<String[]> queue; // {group, index}
    private static int pos = -1, wait = 200, frame, untilFrame;

    private static void load() {
        // one line per scene class: touching the class runs its static registration
        for (String cls : new String[] {"ShowcaseMobs", "ShowcaseItems", "ShowcaseUi", "ShowcaseWorld", "ShowcaseMechanics"}) {
            try {
                Class.forName("dev.mite.e2e." + cls);
            } catch (ClassNotFoundException e) {
                // group not written yet
            }
        }
        String wanted = System.getProperty("hardscrabble.showcase", "all");
        queue = new ArrayList<>();
        GROUPS.forEach((g, scenes) -> {
            if (wanted.equals("all") || List.of(wanted.split(",")).contains(g))
                for (int i = 0; i < scenes.size(); i++) queue.add(new String[] {g, String.valueOf(i)});
        });
    }

    /** Runs a command as the integrated server console. */
    public static void run(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    /** Runs a command as the player (its position and rotation, e.g. "execute at @s"). */
    public static void runAsPlayer(String command) {
        run("execute as @p at @s run " + command);
    }

    /** Shows or hides the HUD (F1) for the next screenshots. */
    public static void hud(boolean shown) {
        var h = Minecraft.getInstance().gui.hud;
        if (h.isHidden() == shown) h.toggle();
    }

    private static Scene current() {
        String[] q = queue.get(pos);
        return GROUPS.get(q[0]).get(Integer.parseInt(q[1]));
    }

    private static void grab(String file) {
        Minecraft mc = Minecraft.getInstance();
        new File(mc.gameDirectory, "screenshots/" + file).getParentFile().mkdirs();
        Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (System.getProperty("hardscrabble.showcase") == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.level == null) return;
        if (queue == null) load();
        if (--wait > 0) return;
        if (pos >= 0) {
            Scene s = current();
            String id = queue.get(pos)[0] + "/" + s.name();
            if (s.frames() > 0 && frame < s.frames()) {
                if (untilFrame-- > 0) return;
                s.tick().run();
                grab("showcase/" + id + String.format(java.util.Locale.ROOT, "/f%03d.png", frame++));
                untilFrame = s.every() - 1;
                if (frame < s.frames()) return;
            } else if (s.frames() == 0) {
                grab("showcase/" + id + ".png");
            }
            MiteMod.LOGGER.info("SHOWCASE {} OK every={}", id, s.every());
        }
        if (++pos >= queue.size()) {
            MiteMod.LOGGER.info("SHOWCASE DONE");
            mc.stop();
            return;
        }
        Scene s = current();
        frame = 0;
        untilFrame = 0;
        s.setup().run();
        wait = Math.max(1, s.waitTicks());
    }
}
