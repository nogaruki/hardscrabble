package dev.mite.e2e;

import static dev.mite.e2e.Showcase.Scene.gif;
import static dev.mite.e2e.Showcase.Scene.shot;
import static dev.mite.e2e.Showcase.run;

import dev.mite.core.MoonCalendar;
import dev.mite.dimension.UnderworldContent;
import dev.mite.farming.FarmingContent.DeadCropBlock;
import dev.mite.lunar.MiteSky;
import dev.mite.worldgen.village.VillageLock;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * README showcase, group "world": moons, Underworld, portals, Nether core, villages, runegate, ore distribution, morning fog.
 * Positions are found on the integrated server thread (open caverns, dungeons, villages), so every scene works on any seed.
 * The player flies in Survival (strict mode refuses Spectator) and is invulnerable.
 */
final class ShowcaseWorld {
    private ShowcaseWorld() {}

    private static final String G = "world";
    private static final String UNDERWORLD = "hardscrabble:underworld";
    /** Overworld vantage point and portal room, set by the first scene. */
    private static int sx, sy, sz;
    /** Underworld camera with the widest view, set by the landscape scene. */
    private static String underworldView = "0 -60 0 0 10";
    /** Underworld camera with rock at medium range (about 40 blocks all around), for the fog scenes. */
    private static String fogView = "0 -60 0 0 10";
    /** Centre of the village found by the village scene. */
    private static volatile BlockPos villageCenter;

    /** Runs on the integrated server thread, after the commands queued before it. */
    private static void server(Consumer<MinecraftServer> task) {
        MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> task.accept(s));
    }

    private static void cmd(MinecraftServer s, String format, Object... args) {
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), String.format(Locale.ROOT, format, args));
        if (format.contains("tp @p")) fly(s); // a dimension change may drop flying
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().getFirst();
    }

    private static void fly(MinecraftServer s) {
        ServerPlayer p = player(s);
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
    }

    /** Keeps the camera still during a GIF: flying again (a dimension change may drop it), no pause menu (focus loss). */
    private static void hold() {
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        server(ShowcaseWorld::fly);
    }

    private static void nightVision(boolean on) {
        run(on ? "effect give @p minecraft:night_vision infinite 0 true" : "effect clear @p minecraft:night_vision");
    }

    /** Day {@code day} (1-based) at {@code time} of day. */
    private static void time(long day, int time) {
        run("time set " + ((day - 1) * MoonCalendar.TICKS_PER_DAY + time));
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static boolean ore(BlockState state) {
        return id(state).endsWith("_ore");
    }

    /** Replaces everything but {@code keep} with air in the box, without neighbour updates (lava and sand stay put). */
    private static void cutaway(ServerLevel level, BlockPos a, BlockPos b, Predicate<BlockState> keep) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !keep.test(state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Invisible light blocks every {@code step} blocks in the air of the box: lit without night vision, which whitens the fog. */
    private static void light(ServerLevel level, BlockPos a, BlockPos b, int step) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b))
            if (pos.getX() % step == 0 && pos.getY() % step == 0 && pos.getZ() % step == 0 && level.getBlockState(pos).isAir())
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Free blocks from {@code pos} along {@code dir}, up to {@code max}. */
    private static int ray(ServerLevel level, BlockPos pos, Direction dir, int max) {
        int d = 0;
        while (d < max && level.getBlockState(pos.relative(dir, d + 1)).isAir()) d++;
        return d;
    }

    /** Moon scenes: narrower field of view (the moon looks bigger) and no clouds; {@code false} restores the defaults. */
    private static void skyCamera(boolean on) {
        var options = Minecraft.getInstance().options;
        options.fov().set(on ? 50 : 70);
        options.cloudStatus().set(on ? CloudStatus.OFF : CloudStatus.FANCY);
    }

    private static void moon(String name, long day) {
        Showcase.add(G, shot(name, 60, () -> {
            Minecraft.getInstance().options.fov().set(name.equals("moon_dog") ? 70 : 50); // the halo is wide
            run("weather clear");
            time(day, 13200);
            run(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @p %d %d %d -90 -8", sx, sy, sz));
        }));
    }

    /** The biggest {@code ore} vein near the spawn (or the Underworld origin), the ground around it cut away and lit, seen from 4 blocks. */
    private static void oreCloseup(String name, String ore, ResourceKey<Level> dimension, int minY, int maxY) {
        Showcase.add(G, shot(name, 200, () -> {
            nightVision(false); // lit by light blocks
            server(s -> {
                ServerLevel o = s.getLevel(dimension);
                boolean overworld = dimension == Level.OVERWORLD;
                int cx = overworld ? sx : 0, cz = overworld ? sz : 0;
                Predicate<BlockPos> isOre = p -> id(o.getBlockState(p)).contains(ore);
                BlockPos best = null;
                int bestCount = 0;
                for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(cx - 80, minY, cz - 80), new BlockPos(cx + 80, maxY, cz + 80)))
                    if (isOre.test(pos)) { // the biggest vein: most ore blocks around
                        int count = 0;
                        for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) if (isOre.test(q)) count++;
                        if (count > bestCount) {
                            bestCount = count;
                            best = pos.immutable();
                        }
                    }
                if (best == null) return;
                BlockPos a = best.offset(-6, -4, -6), b = best.offset(6, 4, 6);
                cutaway(o, a, b, st -> id(st).contains(ore) || st.is(Blocks.BEDROCK) || st.is(UnderworldContent.MANTLE.get()));
                light(o, a, b, 3);
                cmd(s, "execute in %s run tp @p %d %d %d facing %.1f %.1f %.1f", dimension.identifier(), best.getX() + 4, best.getY() + 1, best.getZ() + 4,
                        best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5);
            });
        }));
    }

    static {
        // ---- Moons: the same view of the eastern sky at 13200 (moon 18 degrees high), one full moon of each kind ----
        Showcase.add(G, shot("moon_normal", 200, () -> {
            Showcase.hud(false);
            skyCamera(true);
            for (String rule : new String[] {"send_command_feedback", "show_advancement_messages", "advance_time", "advance_weather", "spawn_mobs"})
                run("gamerule " + rule + " false");
            run("effect give @p minecraft:resistance infinite 255 true");
            run("effect give @p minecraft:saturation infinite 0 true");
            run("kill @e[type=!player]");
            run("weather clear");
            time(8, 13200); // day 8: a normal full moon (full moons fall on d % 8 == 0)
            server(s -> {
                fly(s);
                ServerLevel o = s.overworld();
                BlockPos spawn = o.getRespawnData().pos();
                sx = spawn.getX();
                sz = spawn.getZ();
                sy = o.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) + 14;
                cmd(s, "execute in minecraft:overworld run tp @p %d %d %d -90 -8", sx, sy, sz);
            });
        }));
        moon("moon_harvest", 24);  // 8 days before a blood moon
        moon("moon_blood", 32);    // every 32 days
        moon("moon_dog", 120);     // 8 days before a blue moon: haloed full moon
        moon("moon_blue", 128);    // every 128 days
        Showcase.add(G, gif("moon_rise", 40, () -> {
            Minecraft.getInstance().options.fov().set(50);
            time(32, 12300);
            run(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @p %d %d %d -90 -8", sx, sy, sz));
        }, 40, 2, () -> {
            hold();
            run("time add 30");
        }));

        // ---- Overworld weather: thick morning fog (1 day in 8) ----
        Showcase.add(G, shot("morning_fog", 80, () -> {
            skyCamera(false);
            long day = 1;
            while (MiteSky.morningFog((day - 1) * MoonCalendar.TICKS_PER_DAY + 2000) < 1) day++;
            time(day, 2000);
            run(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @p %d %d %d 90 8", sx, sy - 10, sz));
        }));

        // ---- Underworld: widest open view near the origin, clear fog (day 16) then dense (blood moon day) ----
        Showcase.add(G, shot("underworld_landscape", 300, () -> {
            time(16, 6000);
            nightVision(false); // the Underworld is pitch dark, but night vision would turn its grey fog white: lit by light blocks instead
            run("execute in " + UNDERWORLD + " run forceload add -96 -96 96 96");
            server(s -> {
                ServerLevel u = s.getLevel(UnderworldContent.LEVEL);
                int best = -1, bestMid = -1;
                for (int x = -80; x <= 80; x += 8)
                    for (int z = -80; z <= 80; z += 8)
                        for (int y = -96; y <= 80; y += 8) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (!u.getBlockState(pos).isAir() || !u.getBlockState(pos.above()).isAir()) continue;
                            int score = ray(u, pos, Direction.DOWN, 48), mid = 0;
                            Direction far = Direction.NORTH, midDir = Direction.NORTH;
                            int farD = -1, midD = 999;
                            for (Direction d : Direction.Plane.HORIZONTAL) {
                                int r = ray(u, pos, d, 96);
                                score += r;
                                mid += r < 96 ? Math.min(r, 40) : 0; // rock all around, within the fog's reach
                                if (Math.abs(r - 36) < Math.abs(midD - 36)) {
                                    midD = r;
                                    midDir = d;
                                }
                                if (r > farD) {
                                    farD = r;
                                    far = d;
                                }
                            }
                            if (score > best) {
                                best = score;
                                underworldView = String.format(Locale.ROOT, "%d %d %d %.0f 15", x, y, z, far.toYRot());
                            }
                            if (mid > bestMid) {
                                bestMid = mid;
                                fogView = String.format(Locale.ROOT, "%d %d %d %.0f 10", x, y, z, midDir.toYRot());
                            }
                        }
                String[] v = underworldView.split(" ");
                BlockPos cam = new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
                light(u, cam.offset(-64, -48, -64), cam.offset(64, 48, 64), 4);
                v = fogView.split(" ");
                cam = new BlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
                light(u, cam.offset(-48, -32, -48), cam.offset(48, 32, 48), 4);
                cmd(s, "execute in %s run tp @p %s", UNDERWORLD, underworldView);
                fly(s);
            });
        }));
        Showcase.add(G, shot("underworld_fog_dense", 100, () -> {
            time(32, 6000);
            run("execute in " + UNDERWORLD + " run tp @p " + fogView);
        }));
        Showcase.add(G, gif("underworld_fog_cycle", 40, () -> time(16, 6000), 32, 3, () -> {
            hold();
            run("execute in " + UNDERWORLD + " run tp @p " + fogView);
            run("time add 12000");
        }));

        // ---- Underworld bottom: stone removed; far half keeps the bedrock plates and floor, near half only the Mantle under them ----
        Showcase.add(G, shot("underworld_mantle", 300, () -> {
            time(16, 6000);
            nightVision(false);
            server(s -> {
                ServerLevel u = s.getLevel(UnderworldContent.LEVEL);
                cutaway(u, new BlockPos(100, -128, 100), new BlockPos(139, -84, 139), st -> st.is(Blocks.BEDROCK) || st.is(UnderworldContent.MANTLE.get()));
                cutaway(u, new BlockPos(100, -128, 100), new BlockPos(119, -84, 139), st -> st.is(UnderworldContent.MANTLE.get())); // near half: Mantle only
                light(u, new BlockPos(100, -128, 100), new BlockPos(139, -84, 139), 3);
                cmd(s, "execute in %s run tp @p 101 -98 101 facing 125 -118 125", UNDERWORLD);
            });
        }));
        oreCloseup("adamantium_ore", "adamantium_ore", UnderworldContent.LEVEL, -124, -64);

        // ---- Underworld dungeon: a spawner room that opens onto a cave, seen from inside ----
        Showcase.add(G, shot("underworld_dungeon", 300, () -> {
            nightVision(true);
            server(s -> {
                ServerLevel u = s.getLevel(UnderworldContent.LEVEL);
                BlockPos spawner = null;
                int bestAir = -1;
                for (int cx = -6; cx <= 6 && bestAir <= 400; cx++)
                    for (int cz = -6; cz <= 6 && bestAir <= 400; cz++)
                        for (var be : u.getChunk(cx, cz).getBlockEntities().values()) {
                            if (!(be instanceof SpawnerBlockEntity)) continue;
                            var seen = new java.util.HashSet<BlockPos>();
                            var queue = new java.util.ArrayDeque<BlockPos>();
                            queue.add(be.getBlockPos().above());
                            while (!queue.isEmpty() && seen.size() <= 400) {
                                BlockPos pos = queue.poll();
                                if (!u.getBlockState(pos).isAir() || !seen.add(pos)) continue;
                                for (Direction d : Direction.values()) queue.add(pos.relative(d));
                            }
                            if (seen.size() > bestAir) {
                                bestAir = seen.size();
                                spawner = be.getBlockPos();
                            }
                        }
                if (spawner == null) return;
                // the room stays still: no spawn for the shot, and the Longdead already out are removed
                cmd(s, "execute in %s run data merge block %d %d %d {Delay:30000s}", UNDERWORLD, spawner.getX(), spawner.getY(), spawner.getZ());
                cmd(s, "kill @e[type=!player]");
                // camera on the room's edge, opposite the chests
                BlockPos cam = null;
                int bestScore = Integer.MIN_VALUE;
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                        BlockPos c = spawner.offset(dx, 1, dz);
                        if (!u.getBlockState(c).isAir() || !u.getBlockState(c.above()).isAir()) continue;
                        int score = 0;
                        for (BlockPos p : BlockPos.betweenClosed(spawner.offset(-3, 0, -3), spawner.offset(3, 0, 3)))
                            if (u.getBlockEntity(p) instanceof ChestBlockEntity)
                                score += (p.getX() - spawner.getX()) * -dx + (p.getZ() - spawner.getZ()) * -dz;
                        if (score > bestScore) {
                            bestScore = score;
                            cam = c;
                        }
                    }
                if (cam == null) cam = spawner.above();
                cmd(s, "execute in %s run tp @p %.1f %d %.1f facing %.1f %.1f %.1f", UNDERWORLD, cam.getX() + 0.5, cam.getY(), cam.getZ() + 0.5,
                        spawner.getX() + 0.5, spawner.getY() + 0.3, spawner.getZ() + 0.5);
            });
        }));

        // ---- Bedrock-linked portal: a portal standing on Overworld bedrock, then the arrival in the Underworld ----
        Showcase.add(G, shot("portal_bedrock", 200, () -> {
            time(16, 6000);
            nightVision(true);
            server(s -> {
                int x = sx, z = sz - 20;
                cmd(s, "execute in minecraft:overworld run forceload add %d %d", x, z);
                cmd(s, "execute in minecraft:overworld run fill %d -61 %d %d -61 %d minecraft:bedrock", x - 5, z - 4, x + 5, z + 8);
                cmd(s, "execute in minecraft:overworld run fill %d -60 %d %d -53 %d minecraft:air", x - 5, z - 4, x + 5, z + 8);
                cmd(s, "execute in minecraft:overworld run fill %d -60 %d %d -56 %d minecraft:obsidian", x - 2, z, x + 1, z);
                cmd(s, "execute in minecraft:overworld run fill %d -59 %d %d -57 %d minecraft:nether_portal[axis=x]", x - 1, z, x, z);
                cmd(s, "execute in minecraft:overworld run tp @p %d.0 -60 %.1f 180 8", x, z + 6.5);
            });
        }));
        Showcase.add(G, shot("portal_travel", 300, () -> server(s -> {
            ServerPlayer p = player(s);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            cmd(s, "execute in minecraft:overworld run tp @p %d.0 -59 %.1f 180 0", sx, sz - 19.5);
        })));
        Showcase.add(G, shot("portal_arrival", 100, () -> server(s -> {
            ServerPlayer p = player(s);
            ServerLevel level = p.level();
            fly(s);
            BlockPos portal = null;
            for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-6, -4, -6), p.blockPosition().offset(6, 4, 6)))
                if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                    portal = pos.immutable();
                    break;
                }
            if (portal == null) return;
            Direction.Axis axis = level.getBlockState(portal).getValue(NetherPortalBlock.AXIS);
            Direction out = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            if (ray(level, portal, out, 5) < ray(level, portal, out.getOpposite(), 5)) out = out.getOpposite();
            BlockPos cam = portal.relative(out, Math.max(2, ray(level, portal, out, 5)));
            cmd(s, "execute in %s run tp @p %.1f %d %.1f facing %.1f %.1f %.1f", level.dimension().identifier(), cam.getX() + 0.5, portal.getY(), cam.getZ() + 0.5,
                    portal.getX() + 0.5, portal.getY() + 1.0, portal.getZ() + 0.5);
        })));

        // ---- Nether core: Nether floor cut open, bedrock removed on the near half to show the Core layer under it ----
        Showcase.add(G, shot("nether_core", 300, () -> {
            nightVision(true);
            run("execute in minecraft:the_nether run forceload add 0 0 48 48");
            server(s -> {
                ServerLevel n = s.getLevel(Level.NETHER);
                cutaway(n, new BlockPos(0, 1, 0), new BlockPos(47, 22, 47), st -> st.is(Blocks.BEDROCK));
                cutaway(n, new BlockPos(0, 1, 0), new BlockPos(47, 22, 23), st -> false);
                cmd(s, "execute in minecraft:the_nether run tp @p 24 16 1 facing 24 0 30");
            });
        }));

        // ---- Ores: the biggest vein of each MITE metal near the spawn, the ground around it cut away ----
        oreCloseup("mithril_ore", "mithril_ore", Level.OVERWORLD, -60, 20);
        oreCloseup("silver_ore", "silver_ore", Level.OVERWORLD, -60, 20);

        // ---- Villages: locked on a new world; unlocked, they generate abandoned, with dead crops ----
        Showcase.add(G, shot("village", 400, () -> {
            nightVision(false);
            run("weather clear");
            time(40, 5000);
            server(s -> {
                VillageLock.unlock(s);
                ServerLevel o = s.overworld();
                var villages = s.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(StructureTags.VILLAGE);
                var found = o.getChunkSource().getGenerator().findNearestMapStructure(o, villages, new BlockPos(30000, 64, 30000), 100, false);
                if (found == null) return;
                BlockPos c = found.getFirst();
                o.getChunk(c);
                var start = o.structureManager().getStructureAt(c, found.getSecond().value());
                if (start.isValid()) c = start.getBoundingBox().getCenter();
                int y = o.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
                villageCenter = new BlockPos(c.getX(), y, c.getZ());
                cmd(s, "execute in minecraft:overworld run tp @p %d %d %d facing %d %d %d", c.getX() - 30, y + 24, c.getZ() - 30, c.getX(), y, c.getZ());
            });
        }));
        Showcase.add(G, shot("village_dead_crops", 100, () -> server(s -> {
            if (villageCenter == null) return;
            ServerLevel o = s.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(villageCenter.offset(-48, -12, -48), villageCenter.offset(48, 12, 48)))
                if (o.getBlockState(pos).getBlock() instanceof DeadCropBlock) {
                    cmd(s, "execute in minecraft:overworld run tp @p %d %d %d facing %d %d %d", pos.getX() - 4, pos.getY() + 2, pos.getZ() - 4,
                            pos.getX() + 1, pos.getY(), pos.getZ() + 1);
                    return;
                }
        })));

        // ---- Runegate: a portal with a runestone on each corner ----
        Showcase.add(G, shot("runegate", 200, () -> {
            nightVision(false);
            time(16, 6000);
            server(s -> {
                ServerLevel o = s.overworld();
                int x = sx, z = sz + 16;
                o.getChunk(x >> 4, z >> 4);
                int y = o.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                cmd(s, "fill %d %d %d %d %d %d minecraft:air", x - 5, y, z - 3, x + 5, y + 8, z + 9);
                String ground = BuiltInRegistries.BLOCK.getKey(o.getBlockState(new BlockPos(x, y - 1, z)).getBlock()).toString();
                cmd(s, "fill %d %d %d %d %d %d %s", x - 5, y - 1, z - 3, x + 5, y - 1, z + 9, ground);
                cmd(s, "fill %d %d %d %d %d %d minecraft:obsidian", x - 2, y, z, x + 1, y + 4, z);
                for (int[] c : new int[][] {{x - 2, y}, {x + 1, y}, {x - 2, y + 4}, {x + 1, y + 4}})
                    cmd(s, "setblock %d %d %d hardscrabble:mithril_runestone", c[0], c[1], z);
                cmd(s, "fill %d %d %d %d %d %d minecraft:nether_portal[axis=x]", x - 1, y + 1, z, x, y + 3, z);
                cmd(s, "tp @p %d.0 %d %d.5 180 5", x, y + 1, z + 7);
            });
        }));
    }

}
