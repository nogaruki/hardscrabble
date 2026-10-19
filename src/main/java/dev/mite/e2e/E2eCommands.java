package dev.mite.e2e;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.mite.MiteMod;
import dev.mite.dimension.PortalRouting;
import dev.mite.lunar.Lunar;
import dev.mite.worldgen.village.VillageLock;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Inspection commands for the dedicated-server e2e suite (tools/e2e/run_e2e.py). Registered only when the JVM runs with
 * {@code -Dhardscrabble.e2e=true} (the {@code e2eServer} Gradle run); every answer is one JSON line prefixed by {@code E2E }.
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class E2eCommands {
    private E2eCommands() {}

    /** Vanilla blocks the suite also asserts on, next to every hardscrabble: block. */
    private static final java.util.Set<String> WATCHED = java.util.Set.of("minecraft:bedrock", "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore", "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore", "minecraft:spawner", "minecraft:lava", "minecraft:chest");

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("hardscrabble.e2e")) return;
        event.getDispatcher().register(literal("mitee2e")
                .then(literal("scan").then(argument("dimension", DimensionArgument.dimension())
                        .then(argument("cx", IntegerArgumentType.integer()).then(argument("cz", IntegerArgumentType.integer())
                                .then(argument("radius", IntegerArgumentType.integer(0, 16)).executes(c -> reply(c.getSource(),
                                        scan(DimensionArgument.getDimension(c, "dimension"), IntegerArgumentType.getInteger(c, "cx"),
                                                IntegerArgumentType.getInteger(c, "cz"), IntegerArgumentType.getInteger(c, "radius")))))))))
                .then(literal("route").then(argument("dimension", DimensionArgument.dimension())
                        .then(argument("block", StringArgumentType.string()).then(argument("y", IntegerArgumentType.integer())
                                .executes(c -> reply(c.getSource(), route(DimensionArgument.getDimension(c, "dimension"),
                                        StringArgumentType.getString(c, "block"), IntegerArgumentType.getInteger(c, "y"))))))))
                .then(literal("containers").then(argument("dimension", DimensionArgument.dimension())
                        .then(argument("cx", IntegerArgumentType.integer()).then(argument("cz", IntegerArgumentType.integer())
                                .then(argument("radius", IntegerArgumentType.integer(0, 16)).executes(c -> reply(c.getSource(),
                                        containers(DimensionArgument.getDimension(c, "dimension"), IntegerArgumentType.getInteger(c, "cx"),
                                                IntegerArgumentType.getInteger(c, "cz"), IntegerArgumentType.getInteger(c, "radius")))))))))
                .then(literal("dungeons").then(argument("dimension", DimensionArgument.dimension())
                        .then(argument("cx", IntegerArgumentType.integer()).then(argument("cz", IntegerArgumentType.integer())
                                .then(argument("radius", IntegerArgumentType.integer(0, 16)).executes(c -> reply(c.getSource(),
                                        dungeons(DimensionArgument.getDimension(c, "dimension"), IntegerArgumentType.getInteger(c, "cx"),
                                                IntegerArgumentType.getInteger(c, "cz"), IntegerArgumentType.getInteger(c, "radius")))))))))
                .then(literal("world").executes(c -> reply(c.getSource(), world(c.getSource().getLevel()))))
                .then(literal("unlock_villages").executes(c -> {
                    VillageLock.unlock(c.getSource().getServer());
                    return reply(c.getSource(), world(c.getSource().getLevel()));
                }))
                .then(literal("features").then(argument("biome", StringArgumentType.string())
                        .executes(c -> reply(c.getSource(), features(c.getSource().getServer(), StringArgumentType.getString(c, "biome")))))));
    }

    private static int reply(CommandSourceStack source, JsonObject json) {
        String line = "E2E " + json;
        MiteMod.LOGGER.info(line);
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** Generates the (2r+1)² chunks around (cx, cz) and counts every mite block and the watched vanilla ones, with their Y range. */
    static JsonObject scan(ServerLevel level, int cx, int cz, int radius) {
        Map<String, int[]> stats = new TreeMap<>(); // id -> {count, minY, maxY}
        long start = System.nanoTime();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                LevelChunkSection[] sections = chunk.getSections();
                for (int s = 0; s < sections.length; s++) {
                    LevelChunkSection section = sections[s];
                    if (section.hasOnlyAir()) continue;
                    int baseY = level.getSectionYFromSectionIndex(s) << 4;
                    for (int i = 0; i < 4096; i++) {
                        BlockState state = section.getBlockState(i & 15, i >> 8, (i >> 4) & 15);
                        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        String key = id.toString();
                        if (!id.getNamespace().equals(MiteMod.MODID) && !WATCHED.contains(key)) continue;
                        int y = baseY + (i >> 8);
                        int[] v = stats.computeIfAbsent(key, k -> new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE});
                        v[0]++;
                        v[1] = Math.min(v[1], y);
                        v[2] = Math.max(v[2], y);
                    }
                }
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("dimension", level.dimension().identifier().toString());
        out.addProperty("chunks", (2 * radius + 1) * (2 * radius + 1));
        out.addProperty("minY", level.getMinY());
        out.addProperty("maxY", level.getMaxY());
        out.addProperty("millis", (System.nanoTime() - start) / 1_000_000);
        JsonObject blocks = new JsonObject();
        stats.forEach((k, v) -> {
            JsonObject b = new JsonObject();
            b.addProperty("count", v[0]);
            b.addProperty("minY", v[1]);
            b.addProperty("maxY", v[2]);
            blocks.add(k, b);
        });
        out.add("blocks", blocks);
        return out;
    }

    static JsonObject route(ServerLevel level, String blockId, int y) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId));
        JsonObject out = new JsonObject();
        out.addProperty("from", level.dimension().identifier().toString());
        out.addProperty("to", PortalRouting.destination(level.dimension(), block.defaultBlockState(), y, level.getMinY()).identifier().toString());
        return out;
    }

    /** Spawner entity types and chest loot tables found in the given chunks (already generated by a scan). */
    static JsonObject containers(ServerLevel level, int cx, int cz, int radius) {
        Map<String, Integer> found = new TreeMap<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (var be : level.getChunk(x, z).getBlockEntities().values()) {
                    String key = null;
                    if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
                        var entity = spawner.getSpawner().getOrCreateDisplayEntity(level, be.getBlockPos());
                        key = "spawner:" + (entity == null ? "none" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                    } else if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
                        key = "chest:" + (chest.getLootTable() == null ? "none" : chest.getLootTable().identifier().toString());
                    }
                    if (key != null) found.merge(key, 1, Integer::sum);
                }
            }
        }
        JsonObject out = new JsonObject();
        found.forEach(out::addProperty);
        return out;
    }

    /**
     * Spawners in the given chunks (already generated), split by whether the air around them reaches beyond a dungeon room:
     * an open room connects to a cave a player can walk in, a sealed one sits inside solid rock.
     */
    static JsonObject dungeons(ServerLevel level, int cx, int cz, int radius) {
        int open = 0, sealed = 0;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (var be : level.getChunk(x, z).getBlockEntities().values()) {
                    if (!(be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity)) continue;
                    // Flood the air from the spawner; a vanilla room holds at most 7x4x7 = 196 air blocks.
                    var seen = new java.util.HashSet<net.minecraft.core.BlockPos>();
                    var queue = new java.util.ArrayDeque<net.minecraft.core.BlockPos>();
                    queue.add(be.getBlockPos().above());
                    while (!queue.isEmpty() && seen.size() <= 400) {
                        var pos = queue.poll();
                        if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !seen.add(pos)) continue;
                        for (var dir : net.minecraft.core.Direction.values()) queue.add(pos.relative(dir));
                    }
                    if (seen.size() > 400) open++;
                    else sealed++;
                }
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("open", open);
        out.addProperty("sealed", sealed);
        return out;
    }

    /** Placed features of a biome after biome modifiers, per generation step. */
    static JsonObject features(net.minecraft.server.MinecraftServer server, String biomeId) {
        var biome = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, Identifier.parse(biomeId)));
        JsonObject out = new JsonObject();
        var steps = biome.value().getGenerationSettings().features();
        for (int i = 0; i < steps.size(); i++) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            steps.get(i).forEach(h -> arr.add(h.unwrapKey().map(k -> k.identifier().toString()).orElse("inline")));
            out.add(String.valueOf(i), arr);
        }
        return out;
    }

    static JsonObject world(ServerLevel level) {
        JsonObject out = new JsonObject();
        out.addProperty("difficulty", level.getDifficulty().getSerializedName());
        out.addProperty("difficultyLocked", level.getServer().getWorldData().isDifficultyLocked());
        out.addProperty("villagesOpen", VillageLock.open());
        out.addProperty("moon", Lunar.moon(level).name());
        out.addProperty("underworldLoaded", level.getServer().getLevel(dev.mite.dimension.UnderworldContent.LEVEL) != null);
        out.addProperty("netherLoaded", level.getServer().getLevel(Level.NETHER) != null);
        out.addProperty("spawn", level.getRespawnData().pos().toShortString());
        return out;
    }
}
