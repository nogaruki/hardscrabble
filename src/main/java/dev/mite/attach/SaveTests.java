package dev.mite.attach;

import dev.mite.crafting.CraftQuality;
import dev.mite.curse.Curses;
import dev.mite.gametest.MiteGameTests;
import dev.mite.player.ProgressionService;
import dev.mite.rules.Hints;
import dev.mite.rules.RespawnTimer;
import dev.mite.worldgen.village.VillageLock;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Save schema versions and migrations (ENF-SAV-01/02, ET-Q-11) and payload bounds (dom-Q-08). */
public final class SaveTests {
    private SaveTests() {}

    public static void register() {
        // An attachment saved before versioning (no version field) still loads; a fresh save writes the version.
        MiteGameTests.add("save_attachment_v1_decodes", 20, h -> {
            var pig = h.spawn(EntityTypes.PIG, 2, 1, 2);
            pig.setData(ProgressionService.PROGRESSION, 42);
            pig.setData(RespawnTimer.DEATH_TIME, 7L);
            pig.setData(Hints.SEEN, new java.util.HashSet<>(java.util.Set.of("log")));
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            pig.saveWithoutId(out);
            CompoundTag tag = out.buildResult();
            for (String id : new String[] {"progression", "respawn", "hints_seen"}) {
                CompoundTag saved = tag.getCompoundOrEmpty("neoforge:attachments").getCompoundOrEmpty("hardscrabble:" + id);
                h.assertTrue(saved.getIntOr(Migrations.VERSION, 0) == Migrations.current(id), "saved attachment carries its version: " + id + " " + saved);
                saved.remove(Migrations.VERSION);
            }
            var copy = EntityTypes.PIG.create(h.getLevel(), EntitySpawnReason.LOAD);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
            h.assertTrue(copy.getData(ProgressionService.PROGRESSION) == 42, "v1 attachment decodes: " + copy.getData(ProgressionService.PROGRESSION));
            h.assertTrue(copy.getData(RespawnTimer.DEATH_TIME) == 7L, "v1 respawn decodes: " + copy.getData(RespawnTimer.DEATH_TIME));
            h.assertTrue(copy.getData(Hints.SEEN).equals(java.util.Set.of("log")), "v1 hints_seen decodes: " + copy.getData(Hints.SEEN));
            h.succeed();
        });
        // Curses v1 stored the player map at the top level; v2 moves it under "entries".
        MiteGameTests.add("save_curses_v1_migrates", 20, h -> {
            UUID player = UUID.randomUUID();
            CompoundTag entry = new CompoundTag();
            entry.store("witch", UUIDUtil.STRING_CODEC, UUID.randomUUID());
            entry.putString("curse", dev.mite.curse.Curse.values()[0].name());
            entry.putLong("start", 5);
            entry.putBoolean("notified", false);
            entry.putBoolean("revealed", true);
            CompoundTag v1 = new CompoundTag();
            v1.put(player.toString(), entry);
            Curses.Data data = Curses.Data.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow();
            CompoundTag v2 = (CompoundTag) Curses.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
            h.assertTrue(v2.getIntOr(Migrations.VERSION, 0) == 2, "curses saved as v2: " + v2);
            h.assertTrue(v2.getCompoundOrEmpty("entries").getCompoundOrEmpty(player.toString()).getLongOr("start", -1) == 5, "v1 record kept: " + v2);
            // An explicit older version is not data: the step must not see it as a player entry.
            v1.putInt(Migrations.VERSION, 1);
            var explicit = Curses.Data.CODEC.parse(NbtOps.INSTANCE, v1).flatMap(d -> Curses.Data.CODEC.encodeStart(NbtOps.INSTANCE, d));
            h.assertTrue(explicit.isSuccess() && ((CompoundTag) explicit.getOrThrow()).getCompoundOrEmpty("entries").keySet().equals(java.util.Set.of(player.toString())),
                    "explicit v1 decodes: " + explicit);
            h.succeed();
        });
        MiteGameTests.add("save_world_progress_v1_decodes", 20, h -> {
            CompoundTag v1 = new CompoundTag();
            v1.putBoolean("villagesUnlocked", true);
            v1.putLong("firstVillageUnlockDay", 7);
            VillageLock.Progress p = VillageLock.Progress.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow();
            h.assertTrue(p.villagesUnlocked && p.firstVillageUnlockDay == 7, "v1 world progress decodes");
            Tag saved = VillageLock.Progress.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
            h.assertTrue(((CompoundTag) saved).getIntOr(Migrations.VERSION, 0) == 1, "world progress carries its version");
            h.succeed();
        });
        // ENF-SAV-02: the test world was created with the mod, so it is marked and not foreign.
        MiteGameTests.add("save_world_schema_marked", 20, h -> {
            h.assertFalse(WorldSchema.foreign(h.getLevel().getServer()), "a MITE world is not flagged as foreign");
            h.assertTrue(h.getLevel().getServer().getDataStorage().get(WorldSchema.TYPE) != null, "schema marker written");
            h.succeed();
        });
        // dom-Q-08: an out-of-range quality from a client decodes without throwing and is ignored.
        MiteGameTests.add("craft_quality_payload_bounds", 20, h -> {
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
            buf.writeVarInt(99);
            CraftQuality.Target bad = CraftQuality.Target.STREAM_CODEC.decode(buf);
            h.assertFalse(bad.apply(player), "out-of-range quality refused");
            h.assertFalse(new CraftQuality.Target(-1).apply(player), "negative quality refused");
            h.assertTrue(CraftQuality.target(player) == dev.mite.core.Quality.AVERAGE, "target unchanged");
            h.assertTrue(new CraftQuality.Target(dev.mite.core.Quality.FINE).apply(player), "valid quality accepted");
            h.assertTrue(CraftQuality.target(player) == dev.mite.core.Quality.FINE, "target set");
            h.succeed();
        });
    }
}
