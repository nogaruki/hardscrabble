package dev.mite.e2e;

import static dev.mite.e2e.Showcase.Scene.gif;
import static dev.mite.e2e.Showcase.Scene.shot;
import static dev.mite.e2e.Showcase.run;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * README showcase, group "mobs": one portrait per Hardscrabble mob, family shots, the lineup, the tamed dire wolf, glowing spider
 * eyes and a few GIFs with AI. Three stages built from scratch so the world around does not matter: a smooth stone platform in
 * the sky of the overworld, a hollow deepslate cave and a hollow netherrack hall in the Nether. The camera is the player,
 * teleported in front of the mob, with a narrow FOV for portraits; a block high above each overworld mob keeps the undead from
 * burning in daylight (they burn only under full sky light) without darkening them.
 */
final class ShowcaseMobs {
    private ShowcaseMobs() {}

    private static final String G = "mobs", OW = "minecraft:overworld", NETHER = "minecraft:the_nether";
    private static final int CAVE_X = 200, CAVE_Y = 180;

    private static String in(String dim) {
        return "execute in " + dim + " run ";
    }

    private static String f(String fmt, Object... args) {
        return String.format(Locale.ROOT, fmt, args);
    }

    /** Clears the stage: other mobs, HUD, effects; the camera is in survival so AI mobs come at it (resistance keeps it alive). */
    private static void reset(boolean nightVision, long time) {
        Showcase.hud(false);
        run("tp @e[type=!player] 0 -150 0"); // not /kill: no poof particles, drops or slime splits in front of the camera
        run("gamemode survival @p");
        run("effect clear @p");
        run("effect give @p minecraft:resistance infinite 255 true");
        run("effect give @p minecraft:saturation infinite 255 true");
        if (nightVision) run("effect give @p minecraft:night_vision infinite 0 true");
        run("time set " + time);
        run("weather clear");
    }

    /** Overworld sky platform, camera usually at (0.5, 200, 0.5) looking south (+z). */
    private static void skyStage(boolean nightVision, long time) {
        skyStage(nightVision, time, "smooth_stone");
    }

    /** GIFs use a concrete grid floor ({@code floor} null): the smooth stone texture costs megabytes once quantized frame by frame. */
    private static void skyStage(boolean nightVision, long time, String floor) {
        reset(nightVision, time);
        run(in(OW) + "fill -3 240 -2 3 240 24 minecraft:smooth_stone"); // shade where the portraits and the walking ghoul stand
        run(in(OW) + "fill -24 200 -12 24 209 40 minecraft:air");
        run(in(OW) + "fill -24 199 -12 24 199 40 minecraft:" + (floor == null ? "light_gray_concrete" : floor));
        if (floor == null)
            for (int i = -24; i <= 40; i += 4) {
                if (i <= 24) run(in(OW) + f("fill %d 199 -12 %d 199 40 minecraft:smooth_stone", i, i));
                run(in(OW) + f("fill -24 199 %d 24 199 %d minecraft:smooth_stone", i, i));
            }
    }

    private static void caveStage() {
        reset(true, 6000);
        run(in(OW) + f("fill %d %d -8 %d %d 22 minecraft:deepslate hollow", CAVE_X - 12, CAVE_Y - 1, CAVE_X + 12, CAVE_Y + 9));
        run(in(OW) + f("fill %d %d -7 %d %d 21 minecraft:air", CAVE_X - 11, CAVE_Y, CAVE_X + 11, CAVE_Y + 8));
        run(in(OW) + f("fill %d %d 14 %d %d 21 minecraft:tuff", CAVE_X - 11, CAVE_Y - 1, CAVE_X + 11, CAVE_Y - 1));
        run(in(OW) + f("fill %d %d 21 %d %d 21 minecraft:cobbled_deepslate", CAVE_X - 11, CAVE_Y, CAVE_X + 11, CAVE_Y + 3));
        for (int[] w : new int[][] {{-5, 8, 20}, {-4, 7, 20}, {-5, 0, 20}, {5, 8, 20}, {6, 7, 19}, {6, 0, 20}, {-8, 2, 12}, {8, 3, 10}})
            run(in(OW) + f("setblock %d %d %d minecraft:cobweb", CAVE_X + w[0], CAVE_Y + w[1], w[2]));
        run(in(OW) + f("setblock %d %d 20 minecraft:lantern", CAVE_X - 3, CAVE_Y));
        run(in(OW) + f("setblock %d %d 20 minecraft:lantern", CAVE_X + 4, CAVE_Y));
    }

    private static void netherStage() {
        netherStage("netherrack");
    }

    private static void netherStage(String walls) {
        reset(walls.equals("netherrack"), 6000); // the GIF hall stays dark: shadows compress better than lit netherrack
        run(in(NETHER) + "fill -12 99 -8 12 109 24 minecraft:" + walls + " hollow");
        run(in(NETHER) + "fill -11 100 -7 11 108 23 minecraft:air");
        run(in(NETHER) + "fill -6 99 16 6 99 21 minecraft:lava");
        run(in(NETHER) + "fill -11 99 10 11 99 13 minecraft:magma_block");
        run(in(NETHER) + "fill -11 100 23 11 103 23 minecraft:nether_bricks");
        for (int x = -9; x <= 9; x += 6) run(in(NETHER) + f("setblock %d 109 %d minecraft:glowstone", x, 12));
    }

    private static void cam(String dim, double x, double y, double z, float yaw, float pitch) {
        run(in(dim) + f("tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
    }

    private static void fov(int degrees) {
        Minecraft.getInstance().options.fov().set(degrees);
    }

    /**
     * Summons {@code id} like a natural spawn (its own gear), then freezes it (NoAI unless {@code ai}) facing {@code yaw}.
     * {@code extra} is merged as NBT (e.g. ",Size:2"). Overworld sky mobs get a roof at y 240 against sun burning.
     */
    private static void mob(String dim, String id, double x, double y, double z, float yaw, boolean ai, String extra) {
        if (dim.equals(OW) && y > 190)
            run(in(OW) + f("fill %d 240 %d %d 240 %d minecraft:smooth_stone", (int) Math.floor(x) - 2, (int) Math.floor(z) - 2,
                    (int) Math.floor(x) + 2, (int) Math.floor(z) + 2));
        run(in(dim) + f("summon %s %.2f %.2f %.2f", id, x, y, z));
        String sel = f("@e[type=%s,limit=1,sort=nearest]", id);
        String at = f("execute in %s positioned %.2f %.2f %.2f run ", dim, x, y, z);
        run(at + f("data merge entity %s {NoAI:%db,PersistenceRequired:1b,IsBaby:0b,CanPickUpLoot:0b,Tags:[\"cast\"]%s}", sel, ai ? 0 : 1, extra));
        run(at + f("tp %s %.2f %.2f %.2f %.1f 0", sel, x, y, z, yaw));
    }

    private static String id(String name) {
        return name.contains(":") ? name : "hardscrabble:" + name;
    }

    /** Portrait framing: telephoto from a few blocks away, {@code y} above the floor (bats fly, tiny mobs stand on a plinth). */
    private record Frame(double dist, float pitch, double y, boolean plinth, float yaw) {}

    private static final Frame TALL = new Frame(5.2, 7, 0, false, 150), BEAST = new Frame(4.0, 14, 0, false, 150),
            CUBE = new Frame(4.8, 11, 0, false, 150), TINY = new Frame(2.4, 12, 1, true, 115), BAT = new Frame(2.7, 0, 1.2, false, 155),
            BIG_BAT = new Frame(4.0, 0, 1.0, false, 155), FLOATING = new Frame(6.0, 2, 0, false, 150);

    /** Sends away whatever came along with the summoned mobs (a bone lord's escort, a spider jockey...). */
    private static void solo() {
        run("tp @e[type=!player,tag=!cast] 0 -150 0");
    }

    private static int frameNo;

    /** GIF tick action: the mobs, frozen while the first frames load, get their AI on the first frame. */
    private static void wake() {
        if (frameNo++ == 0) run("execute at @p as @e[tag=cast,distance=..40] run data merge entity @s {NoAI:0b}");
    }

    private static void portrait(String name, Frame fr, String extra) {
        Showcase.add(G, shot(name, 50, () -> {
            skyStage(true, 6000);
            fov(30);
            cam(OW, 0.5, 200, 0.5, 0, fr.pitch);
            if (fr.plinth) run(in(OW) + f("setblock 0 200 %d minecraft:polished_andesite", (int) Math.floor(0.5 + fr.dist)));
            mob(OW, id(name), 0.5, 200 + fr.y, 0.5 + fr.dist, fr.yaw, false, extra);
            solo();
        }));
    }

    private static void cavePortrait(String name, Frame fr, String extra) {
        Showcase.add(G, shot(name, 50, () -> {
            caveStage();
            fov(30);
            cam(OW, CAVE_X + 0.5, CAVE_Y, 0.5, 0, fr.pitch);
            mob(OW, id(name), CAVE_X + 0.5, CAVE_Y, 0.5 + fr.dist, fr.yaw, false, extra);
            solo();
        }));
    }

    private static void netherPortrait(String name, Frame fr, String extra) {
        Showcase.add(G, shot(name, 60, () -> {
            netherStage();
            fov(30);
            cam(NETHER, 0.5, 100, 0.5, 0, fr.pitch);
            if (fr.plinth) run(in(NETHER) + f("setblock 0 100 %d minecraft:polished_blackstone", (int) Math.floor(0.5 + fr.dist)));
            mob(NETHER, id(name), 0.5, 100 + fr.y, 0.5 + fr.dist, fr.yaw, false, extra);
            solo();
        }));
    }

    /** A row of mobs centred on x 0.5 at depth z, {@code spacing} apart, facing the camera with a slight fan. */
    private static void row(String dim, double y, double z, double spacing, String extra, String... names) {
        for (int i = 0; i < names.length; i++) {
            double off = (i - (names.length - 1) / 2.0) * spacing;
            mob(dim, id(names[i]), 0.5 + off, y, z, (float) (180 - off * 4), false, extra);
        }
    }

    private static String tamed(String extra) {
        int[] o = net.minecraft.core.UUIDUtil.uuidToIntArray(Minecraft.getInstance().player.getUUID());
        return f(",Owner:[I;%d,%d,%d,%d]%s", o[0], o[1], o[2], o[3], extra);
    }

    /** Wolf armour, dyed unless {@code colour < 0}, with {@code damage} points of wear (64 = broken). */
    private static String armour(int colour, int damage) {
        String dye = colour < 0 ? "" : f("\"minecraft:dyed_color\":%d,", colour);
        return f(",equipment:{body:{id:\"minecraft:wolf_armor\",components:{%s\"minecraft:damage\":%d}}}", dye, damage);
    }

    private static final String[] UNDEAD = {"ghoul", "wight", "revenant", "shadow", "invisible_stalker", "longdead", "longdead_guardian", "bone_lord",
            "ancient_bone_lord"};
    private static final String[] SPIDERS = {"wood_spider", "black_widow", "phase_spider", "demon_spider"};
    private static final String[] CUBES = {"jelly", "blob", "ooze", "pudding"};
    private static final String[] BATS = {"vampire_bat", "giant_vampire_bat", "nightwing"};
    private static final String[] SILVERFISH = {"copperspine", "netherspawn", "hoary_silverfish"};
    private static final String FLY = ",NoGravity:1b,BatFlags:0b";

    static {
        // keep advancement / recipe / tutorial toasts off every frame
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (System.getProperty("hardscrabble.showcase") == null || mc.player == null) return;
            mc.gui.toastManager().clear();
            mc.options.tutorialStep = TutorialSteps.NONE;
        });
        Showcase.add(G, shot("setup", 120, () -> {
            for (String rule : new String[] {"send_command_feedback", "show_advancement_messages", "spawn_mobs", "advance_time",
                    "advance_weather", "log_admin_commands", "show_death_messages", "command_block_output"})
                run("gamerule " + rule + " false");
            run(in(OW) + "forceload add -32 -16 32 48");
            run(in(OW) + f("forceload add %d -16 %d 32", CAVE_X - 16, CAVE_X + 16));
            run(in(NETHER) + "forceload add -16 -16 16 32");
            run("advancement grant @p everything");
            run("recipe give @p *");
        }));
        Showcase.add(G, shot("warmup", 80, () -> { // the stage again, now that its chunks are loaded (fill fails silently in unloaded ones)
            skyStage(true, 6000);
            cam(OW, 0.5, 200, 0.5, 0, 10);
        }));

        // --- portraits (the dire wolf first: the shade above the stage needs a moment to darken the sky light) ---
        portrait("dire_wolf", BEAST, "");
        for (String n : new String[] {"ghoul", "wight", "revenant", "shadow", "earth_elemental", "clay_golem", "longdead", "longdead_guardian",
                "bone_lord", "ancient_bone_lord", "infernal_creeper"})
            portrait(n, TALL, "");
        cavePortrait("invisible_stalker", TALL, ",Glowing:1b"); // a faint ghost: the outline and the dark cave make it readable
        for (String n : CUBES) portrait(n, CUBE, ",Size:2");
        portrait("vampire_bat", BAT, FLY);
        portrait("giant_vampire_bat", BIG_BAT, FLY);
        portrait("copperspine", TINY, "");
        portrait("hoary_silverfish", TINY, "");
        for (String n : SPIDERS) cavePortrait(n, BEAST, "");
        netherPortrait("hellhound", BEAST, "");
        netherPortrait("fire_elemental", FLOATING, "");
        netherPortrait("nightwing", BIG_BAT, FLY);
        netherPortrait("netherspawn", TINY, "");

        // --- families ---
        Showcase.add(G, shot("family_undead", 60, () -> {
            skyStage(true, 6000);
            fov(43);
            cam(OW, 0.5, 200, 0.5, 0, 3);
            row(OW, 200, 9, 1.2, "", UNDEAD);
            solo();
        }));
        Showcase.add(G, shot("family_elementals", 60, () -> {
            skyStage(true, 6000);
            fov(40);
            cam(OW, 0.5, 200, 0.5, 0, 4);
            row(OW, 200, 6.5, 1.5, "", "earth_elemental", "fire_elemental", "clay_golem");
        }));
        Showcase.add(G, shot("family_spiders", 60, () -> {
            caveStage();
            fov(45);
            cam(OW, CAVE_X + 0.5, CAVE_Y, 0.5, 0, 14);
            for (int i = 0; i < SPIDERS.length; i++)
                mob(OW, id(SPIDERS[i]), CAVE_X + 0.5 + (i - 1.5) * 1.9, CAVE_Y, 5.5, (float) (180 + (1.5 - i) * 8), false, "");
            solo();
        }));
        Showcase.add(G, shot("family_cubes", 60, () -> {
            skyStage(true, 6000);
            fov(40);
            cam(OW, 0.5, 200, 0.5, 0, 10);
            row(OW, 200, 7, 1.9, ",Size:2", CUBES);
        }));
        Showcase.add(G, shot("family_bats", 60, () -> {
            skyStage(true, 6000);
            fov(40);
            cam(OW, 0.5, 200, 0.5, 0, 0);
            row(OW, 201.1, 5, 1.6, FLY, BATS);
        }));
        Showcase.add(G, shot("family_silverfish", 60, () -> {
            skyStage(true, 6000);
            fov(30);
            cam(OW, 0.5, 200, 0.5, 0, 10);
            run(in(OW) + "fill -1 200 4 1 200 4 minecraft:polished_andesite");
            for (int i = 0; i < SILVERFISH.length; i++) mob(OW, id(SILVERFISH[i]), 1.5 - i, 201, 4.5, 125, false, "");
        }));

        // --- every mob at once ---
        Showcase.add(G, shot("lineup", 80, () -> {
            skyStage(true, 6000);
            fov(55);
            run(in(OW) + "setblock 0 203 -3 minecraft:barrier");
            cam(OW, 0.5, 204, -2.5, 0, 27);
            row(OW, 200, 11, 1.15, "", "infernal_creeper", "earth_elemental", "clay_golem", "ghoul", "wight", "revenant", "shadow", "invisible_stalker",
                    "longdead", "longdead_guardian", "bone_lord", "ancient_bone_lord", "fire_elemental");
            row(OW, 200, 7.6, 1.8, "", "dire_wolf", "hellhound", "wood_spider", "black_widow", "phase_spider", "demon_spider");
            String[] front = {"jelly", "blob", "copperspine", "netherspawn", "hoary_silverfish", "ooze", "pudding"};
            for (int i = 0; i < front.length; i++)
                mob(OW, id(front[i]), 0.5 + (i - 3) * 1.15, 200, 4.5, 180, false, i < 2 || i > 4 ? ",Size:0" : "");
            row(OW, 202.6, 13.5, 2.4, FLY, BATS);
            solo();
        }));

        // --- the tamed dire wolf: MITE collar and dyed wolf armour, then the armour cracking ---
        Showcase.add(G, shot("dire_wolf_tamed", 60, () -> {
            skyStage(true, 6000);
            fov(35);
            cam(OW, 0.5, 200, 0.5, 0, 12);
            mob(OW, "hardscrabble:dire_wolf", 2.4, 200, 6.3, 205, false, tamed(",CollarColor:14b"));
            mob(OW, "hardscrabble:dire_wolf", 0.4, 200, 6, 160, false, tamed(",CollarColor:4b" + armour(3949738, 0)));
            mob(OW, "hardscrabble:dire_wolf", -1.6, 200, 6.3, 150, false, tamed(",CollarColor:11b,Sitting:1b" + armour(11546150, 0)));
        }));
        Showcase.add(G, shot("dire_wolf_cracked", 60, () -> {
            skyStage(true, 6000);
            fov(30);
            cam(OW, 0.5, 200, 0.5, 0, 14);
            for (int i = 0; i < 3; i++) // side view, wear growing from right to left
                mob(OW, "hardscrabble:dire_wolf", 2.3 - i * 1.8, 200, 6 + (i % 2) * 0.6, 120, false, tamed(",CollarColor:14b" + armour(-1, 22 + i * 18)));
        }));

        // --- spider eyes in the dark ---
        Showcase.add(G, shot("spider_eyes_night", 80, () -> {
            skyStage(false, 18000);
            fov(45);
            cam(OW, 0.5, 200, 0.5, 0, 9);
            for (int i = 0; i < SPIDERS.length; i++)
                mob(OW, id(SPIDERS[i]), 0.5 + (i - 1.5) * 1.8, 200, 6.5, (float) (180 + (1.5 - i) * 8), false, "");
            solo();
        }));

        // --- alive: frozen during the wait, AI on at the first frame ---
        Showcase.add(G, gif("ghoul_walk", 40, () -> {
            frameNo = 0;
            skyStage(true, 6000, null);
            fov(50);
            cam(OW, 0.5, 200, 0.5, 0, 6);
            mob(OW, "hardscrabble:ghoul", 0.5, 200, 11, 180, false, "");
            solo();
        }, 34, 2, ShowcaseMobs::wake));
        Showcase.add(G, gif("cube_bounce", 40, () -> {
            frameNo = 0;
            skyStage(true, 6000, null);
            fov(40);
            cam(OW, 0.5, 200, 0.5, 0, 9);
            mob(OW, "hardscrabble:jelly", -2.0, 200, 12, 180, false, ",Size:2");
            mob(OW, "hardscrabble:blob", 0.5, 200, 14, 180, false, ",Size:2");
            mob(OW, "hardscrabble:ooze", 3.0, 200, 12.5, 180, false, ",Size:2");
        }, 40, 2, ShowcaseMobs::wake));
        Showcase.add(G, gif("dire_wolf_pack", 40, () -> {
            frameNo = 0;
            skyStage(true, 6000, null);
            fov(45);
            cam(OW, 0.5, 200, 0.5, 0, 8);
            for (int i = 0; i < 4; i++) mob(OW, "hardscrabble:dire_wolf", -1.5 + i * 1.4, 200, 11 + (i % 2) * 1.5, 180, false, "");
        }, 30, 2, ShowcaseMobs::wake));
        Showcase.add(G, gif("fire_elemental_alive", 60, () -> {
            frameNo = 0;
            netherStage("black_concrete");
            run(in(NETHER) + "fill -11 99 -7 11 99 23 minecraft:black_concrete");
            run(in(NETHER) + "fill -6 99 16 6 99 21 minecraft:lava");
            fov(40);
            cam(NETHER, 0.5, 100, 0.5, 0, 0);
            mob(NETHER, "hardscrabble:fire_elemental", 0.5, 100, 8, 180, false, "");
        }, 30, 2, ShowcaseMobs::wake));
    }
}
