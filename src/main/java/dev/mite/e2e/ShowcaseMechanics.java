package dev.mite.e2e;

import static dev.mite.e2e.Showcase.Scene.gif;
import static dev.mite.e2e.Showcase.run;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * README showcase, group "mechanics": short GIFs of MITE rules at work. Each scene rebuilds a floating stage at y 200 around
 * the spot where the player first stood, then lets the real game logic play (mob AI on, player input simulated from the client:
 * mining, right-clicks, walking, sneaking). Only the start of an event is scripted (a target, a first blight), never its outcome.
 */
final class ShowcaseMechanics {
    private ShowcaseMechanics() {}

    private static final String G = "mechanics";
    private static int bx, bz;
    private static boolean placed;
    /** Client action run every tick for the current scene (t = ticks since its setup), stopped after its length. */
    private static IntConsumer perTick = t -> {};
    private static int tick, length;

    static {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (Minecraft.getInstance().player == null) return;
            if (placed) { // no tutorial, recipe or advancement toasts, no hint chat
                Minecraft.getInstance().gui.toastManager().clear();
                Minecraft.getInstance().gui.hud.getChat().clearMessages(false);
            }
            if (tick >= length) return;
            perTick.accept(tick++);
            if (tick >= length) release();
        });

        // ---- harvest ----
        Showcase.add(G, gif("no_bare_hand_logs", 40, () -> {
            stage(6000, true);
            log(1, 1);
            tp(0, 0, 0, -45, 15);
            during(115, t -> {
                if (t == 60) run("item replace entity @p weapon.mainhand with hardscrabble:flint_hatchet");
                mine(pos(1, 201, 1), Direction.NORTH);
            });
        }, 38, 3, () -> {}));
        Showcase.add(G, gif("gravel_flint", 10, () -> {
            stage(6000, false);
            run("fill " + fill(-1, 200, 2, 1, 200, 5) + " minecraft:gravel");
            tp(0, 1, 0, 38);
            during(90, t -> {
                if (t % 6 == 0 && t / 6 < 12) run("setblock " + at(t / 6 % 3 - 1, 200, 5 - t / 18) + " minecraft:air destroy");
            });
        }, 32, 3, () -> {}));
        // ---- world ----
        Showcase.add(G, gif("dirt_gravity", 10, () -> {
            stage(6000, false);
            run("fill " + at(-4, 200, 6) + " " + at(-4, 202, 6) + " minecraft:stone_bricks");
            run("fill " + at(4, 200, 6) + " " + at(4, 202, 6) + " minecraft:stone_bricks");
            run("fill " + at(-3, 202, 6) + " " + at(3, 202, 6) + " minecraft:oak_planks");
            run("fill " + at(-3, 203, 6) + " " + at(3, 205, 6) + " minecraft:dirt");
            tp(0, 0, 0, -12);
            during(120, t -> {
                if (t >= 10 && t % 14 == 10 && (t - 10) / 14 < 7) run("setblock " + at((t - 10) / 14 - 3, 202, 6) + " minecraft:air destroy");
            });
        }, 40, 3, () -> {}));
        // ---- mobs ----
        Showcase.add(G, gif("zombies_dig", 90, () -> {
            stage(6000, false);
            run("fill " + at(-4, 200, 5) + " " + at(4, 202, 8) + " minecraft:stone_bricks");
            run("fill " + at(-3, 200, 6) + " " + at(3, 201, 7) + " minecraft:air");
            run("fill " + at(-3, 200, 5) + " " + at(3, 201, 5) + " minecraft:dirt");
            for (int x = -2; x <= 2; x += 2)
                summon("minecraft:zombie", x, 200, 6, "equipment:{head:{id:\"minecraft:leather_helmet\"}}");
            tp(0, 0, -1, 8);
            targetPlayer();
        }, 42, 3, () -> {}));
        Showcase.add(G, gif("shadow_snuffs_lights", 20, () -> {
            stage(18000, false);
            run("fill " + at(-1, 200, 4) + " " + at(1, 202, 6) + " minecraft:barrier");
            run("setblock " + at(0, 200, 5) + " minecraft:air");
            run("setblock " + at(0, 201, 5) + " minecraft:air");
            int[][] lights = {{-2, 3}, {2, 3}, {-3, 5}, {3, 5}, {-2, 7}, {2, 7}};
            for (int i = 0; i < lights.length; i++)
                run("setblock " + at(lights[i][0], 200, lights[i][1]) + (i % 2 == 0 ? " minecraft:candle[candles=3,lit=true]" : " minecraft:torch"));
            summon("hardscrabble:shadow", 0, 200, 5, "");
            tp(0, 0, -1, 25);
        }, 45, 3, () -> {}));
        // ---- farming / food ----
        Showcase.add(G, gif("campfire_goes_out", 10, () -> {
            stage(13000, true);
            run("setblock " + at(0, 200, 1) + " minecraft:campfire[lit=true]");
            run("item replace entity @p weapon.mainhand with minecraft:beef 8");
            tp(0, 0, 0, 55);
            during(110, t -> {
                if (t % 25 != 10) return;
                Minecraft mc = Minecraft.getInstance();
                BlockPos fire = pos(0, 200, 1);
                if (!mc.level.getBlockState(fire).getOptionalValue(net.minecraft.world.level.block.CampfireBlock.LIT).orElse(false)) return;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(fire), Direction.UP, fire, false));
                mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getInteractAnimation(), false);
            });
        }, 36, 3, () -> {}));
        Showcase.add(G, gif("gem_sacrifice", 10, () -> {
            stage(6000, true);
            run("item replace entity @p weapon.mainhand with minecraft:emerald 4");
            tp(0, 0, 0, -10);
            during(130, t -> {
                if (t % 25 != 15) return;
                Minecraft mc = Minecraft.getInstance();
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getInteractAnimation(), false);
            });
        }, 42, 3, () -> {}));
        Showcase.add(G, gif("crop_blight", 10, () -> {
            stage(6000, false);
            run("fill " + fill(-4, 199, 2, 4, 199, 8) + " minecraft:farmland[moisture=7]");
            run("fill " + fill(-4, 199, 5, 4, 199, 5) + " minecraft:water");
            run("fill " + fill(-4, 200, 2, 4, 200, 4) + " minecraft:wheat[age=2]");
            run("fill " + fill(-4, 200, 6, 4, 200, 8) + " minecraft:wheat[age=2]");
            server(level -> {
                dev.mite.farming.FarmingRules.infect(level, pos(-2, 200, 3));
                dev.mite.farming.FarmingRules.infect(level, pos(2, 200, 7));
            });
            run("gamerule random_tick_speed 1000");
            run("setblock " + at(0, 200, 0) + " minecraft:green_concrete");
            tp(0, 1, 0, 50);
        }, 22, 3, () -> {}));
        Showcase.add(G, gif("squid_smashes_boat", 55, () -> {
            stage(6000, false);
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            run("fill " + fill(-7, 194, -3, 7, 199, 15) + " minecraft:stone");
            run("fill " + fill(-6, 195, -2, 6, 199, 14) + " minecraft:water");
            tp(0, 0, 0, 20);
            run("summon minecraft:oak_boat " + at(0, 200, 0) + " {Rotation:[0f,0f]}");
            run("ride @p mount @e[type=minecraft:oak_boat,limit=1]");
            summon("minecraft:squid", 0, 197, 7, "");
        }, 24, 2, () -> {}));
        Showcase.add(G, gif("horse_shies", 10, () -> {
            stage(6000, false);
            summon("minecraft:horse", 0, 200, 7, "Rotation:[90f,0f],Variant:2");
            tp(0, 0, 0, 12);
            during(140, t -> {
                Minecraft mc = Minecraft.getInstance();
                mc.options.keyShift.setDown(t < 75);
                mc.options.keyUp.setDown(t > 10 && t < 60);
            });
        }, 45, 3, () -> {}));
        Showcase.add(G, gif("jelly_digests_items", 10, () -> {
            stage(6000, false);
            // pepsin digests meat and leather; the stick and the iron stay (an Ooze or Pudding would eat wood and metal instead)
            String[] items = {"minecraft:beef", "minecraft:stick", "minecraft:leather", "minecraft:porkchop", "minecraft:iron_ingot", "minecraft:mutton"};
            for (int i = 0; i < items.length; i++)
                run(String.format(Locale.ROOT, "summon minecraft:item %.2f 200.05 %.2f {Item:{id:\"%s\",count:1},PickupDelay:32767,Motion:[0d,0d,0d]}",
                        bx + 0.5 + (i % 3 - 1) * 0.5, bz + 2.5 + i / 3 * 0.7, items[i]));
            summon("hardscrabble:jelly", 0, 200, 7, "Size:2");
            tp(0, 0, -3, 22);
            targetPlayer();
        }, 30, 3, () -> {}));
        Showcase.add(G, gif("earth_elemental_heated", 10, () -> {
            stage(6000, false);
            run("fill " + fill(1, 199, 2, 3, 199, 5) + " minecraft:lava");
            summon("hardscrabble:earth_elemental", -1, 200, 4, "NoAI:1b,Rotation:[160f,0f]");
            tp(0, 0, -1, 15);
            during(150, t -> {
                if (t == 15) run("summon minecraft:lightning_bolt " + at(-1, 200, 4));
                if (t == 60) run("tp @e[type=hardscrabble:earth_elemental] " + at(2, 199, 4) + " 160 0");
            });
        }, 36, 3, () -> {}));
        Showcase.add(G, gif("fire_elemental_water", 10, () -> {
            stage(13000, true);
            run("fill " + fill(-3, 199, 4, 3, 199, 8) + " minecraft:lava");
            summon("hardscrabble:fire_elemental", 0, 200, 2, "NoAI:1b");
            run("item replace entity @p weapon.mainhand with minecraft:water_bucket");
            tp(0, 0, 0, 15);
            during(150, t -> {
                if (t == 60) run("item replace entity @p weapon.mainhand with hardscrabble:water_bowl");
                if (t == 25 || t == 80) interact("fire_elemental");
            });
        }, 38, 3, () -> {}));
    }

    // ---- helpers ----

    /** Right-clicks the nearest mob of {@code hardscrabble:<path>} with the held item, as the use key does. */
    private static void interact(String path) {
        Minecraft mc = Minecraft.getInstance();
        mc.level.getEntitiesOfClass(Mob.class, mc.player.getBoundingBox().inflate(6),
                        m -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath().equals(path))
                .stream().findFirst().ifPresent(m -> {
                    mc.gameMode.interact(mc.player, m, new net.minecraft.world.phys.EntityHitResult(m, m.getBoundingBox().getCenter()), InteractionHand.MAIN_HAND);
                    mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getInteractAnimation(), false);
                });
    }

    /** Block position relative to the stage centre; y is absolute. */
    private static BlockPos pos(int dx, int y, int dz) {
        return new BlockPos(bx + dx, y, bz + dz);
    }

    private static String at(int dx, int y, int dz) {
        BlockPos p = pos(dx, y, dz);
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String fill(int x1, int y1, int z1, int x2, int y2, int z2) {
        return at(x1, y1, z1) + " " + at(x2, y2, z2);
    }

    /** Clears the stage: no mobs or items, an empty sky platform of grass at y 199, the given time, a clean player. */
    private static void stage(int time, boolean hud) {
        Minecraft mc = Minecraft.getInstance();
        if (!placed) {
            bx = mc.player.getBlockX();
            bz = mc.player.getBlockZ();
            placed = true;
            run("gamerule send_command_feedback false");
            run("gamerule show_advancement_messages false");
            run("gamerule spawn_mobs false");
            run("gamerule advance_time false");
            run("gamerule advance_weather false");
            run("gamerule mob_drops false");
            run("gamerule fire_spread_radius_around_player 0");
            run("weather clear");
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        perTick = t -> {};
        length = 0;
        release();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        Showcase.hud(hud);
        run("tp @e[type=!player] " + at(0, -200, 0));
        run("kill @e[type=!player]");
        run("fill " + fill(-16, 194, -16, 16, 213, 16) + " minecraft:air");
        run("fill " + fill(-16, 199, -16, 16, 199, 16) + " minecraft:green_concrete"); // flat colour: textured floors double the GIF size
        run("time set " + time);
        run("gamerule random_tick_speed 3");
        run("clear @p");
        run("effect clear @p");
        run("effect give @p minecraft:resistance infinite 255 true");
        run("effect give @p minecraft:saturation infinite 0 true");
        run("effect give @p minecraft:regeneration infinite 255 true");
    }

    private static void tp(double dx, double dy, double dz, float pitch) {
        tp(dx, dy, dz, 0, pitch);
    }

    private static void tp(double dx, double dy, double dz, float yaw, float pitch) {
        run(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", bx + dx + 0.5, 200 + dy, bz + dz + 0.5, yaw, pitch));
    }

    private static void summon(String type, int dx, int y, int dz, String nbt) {
        run("summon " + type + " " + at(dx, y, dz) + " {PersistenceRequired:1b,Rotation:[180f,0f]" + (nbt.isEmpty() ? "" : "," + nbt) + "}");
    }

    /** A small oak with its trunk at (dx, 200, dz). */
    private static void log(int dx, int dz) {
        run("fill " + fill(dx - 2, 203, dz - 1, dx + 2, 204, dz + 3) + " minecraft:oak_leaves[persistent=true]");
        run("fill " + fill(dx - 1, 205, dz, dx + 1, 205, dz + 2) + " minecraft:oak_leaves[persistent=true]");
        run("fill " + fill(dx, 200, dz, dx, 204, dz) + " minecraft:oak_log");
    }

    /** Runs {@code action} on the server thread with the overworld. */
    private static void server(Consumer<ServerLevel> action) {
        IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> action.accept(s.overworld()));
    }

    /** Every mob on the stage targets the player (they cannot see through walls; a digger then digs toward it). */
    private static void targetPlayer() {
        server(level -> {
            var player = level.players().getFirst();
            level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32)).forEach(m -> m.setTarget(player));
        });
    }

    private static void during(int ticks, IntConsumer action) {
        perTick = action;
        tick = 0;
        length = ticks;
    }

    /** One tick of holding the attack key on {@code pos} (what the client does while the button is down). */
    private static void mine(BlockPos pos, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode.continueDestroyBlock(pos, face)) mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
    }

    private static void release() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUse.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyShift.setDown(false);
        if (mc.gameMode != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
    }
}
