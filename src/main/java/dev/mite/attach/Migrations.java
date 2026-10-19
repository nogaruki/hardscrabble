package dev.mite.attach;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import dev.mite.MiteMod;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Schema versions of the saved attachments and SavedData (ENF-SAV-01, docs/02 §6.3.4, ET-Q-11). Each saved value carries
 * {@code version:int}; data without it is version 1 (saved before versioning). Reading upgrades older data step by step, so a
 * structure change ships with its step here or it is a blocking bug.
 */
public final class Migrations {
    private Migrations() {}

    static final String VERSION = "version";

    /** Schema id -> upgrade steps; step {@code i} turns version {@code i + 1} into {@code i + 2}. Current = steps + 1. */
    private static final Map<String, List<UnaryOperator<Dynamic<?>>>> STEPS = Map.of(
            // v2: the player map moved under "entries" so the version field can sit next to it.
            "curses", List.of(d -> d.emptyMap().set("entries", d)));

    public static int current(String schema) {
        return STEPS.getOrDefault(schema, List.of()).size() + 1;
    }

    /** {@code codec} plus the version field, upgrading older data on read. */
    public static <A> MapCodec<A> versioned(String schema, MapCodec<A> codec) {
        int current = current(schema);
        List<UnaryOperator<Dynamic<?>>> steps = STEPS.getOrDefault(schema, List.of());
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.concat(codec.keys(ops), Stream.of(ops.createString(VERSION)));
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T v = input.get(VERSION);
                int version = v == null ? 1 : ops.getNumberValue(v).map(Number::intValue).result().orElse(1);
                if (version > current) MiteMod.LOGGER.warn("{} data has schema version {} (this build reads up to {})", schema, version, current);
                if (version >= current) return codec.decode(ops, input);
                Dynamic<?> data = new Dynamic<>(ops, ops.createMap(input.entries())).remove(VERSION); // steps see the data only
                for (int i = version - 1; i < steps.size(); i++) data = steps.get(i).apply(data);
                MiteMod.LOGGER.debug("Migrated {} data from schema version {} to {}", schema, version, current);
                return ops.getMap(data.convert(ops).getValue()).flatMap(map -> codec.decode(ops, map));
            }

            @Override
            public <T> RecordBuilder<T> encode(A value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return codec.encode(value, ops, prefix).add(VERSION, ops.createInt(current));
            }

            @Override
            public String toString() {
                return "Versioned[" + schema + " v" + current + ", " + codec + "]";
            }
        };
    }
}
