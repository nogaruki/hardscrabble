package dev.mite.worldgen.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Material;
import dev.mite.core.MoonCalendar;
import dev.mite.core.ToolType;
import dev.mite.harvest.ToolStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Locked villages (EF-MON-05, ET-L-04, L06). Villages generate in new chunks only once a player of the world has crafted an
 * iron (or better) pickaxe or war hammer (R165), and optionally once the world is {@code village_lock_min_days} old. The state lives in
 * the server-wide SavedData {@code mite_world_progress}; worldgen has no level, so {@code hardscrabble:gated_spread} with
 * {@code village_lock} reads the cached {@link #open()}. Chunks generated while locked never get their village afterwards, and
 * {@code /locate} does not generate any chunk while locked.
 * Villages generate without villagers (R170): the villager template pools are emptied by plain JSON overrides.
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class VillageLock {
    private VillageLock() {}

    private static volatile boolean open;

    /** {@code mite_world_progress} (docs/02 §6.3.3). */
    public static final class Progress extends SavedData {
        public static final Codec<Progress> CODEC = dev.mite.attach.Migrations.versioned("world_progress", RecordCodecBuilder.<Progress>mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("villagesUnlocked", false).forGetter(p -> p.villagesUnlocked),
                Codec.LONG.optionalFieldOf("firstVillageUnlockDay", 0L).forGetter(p -> p.firstVillageUnlockDay)).apply(i, Progress::new))).codec();
        public static final SavedDataType<Progress> TYPE = new SavedDataType<>(MiteMod.id("mite_world_progress"), Progress::new, CODEC);

        public boolean villagesUnlocked;
        public long firstVillageUnlockDay;

        Progress() {}

        Progress(boolean unlocked, long day) {
            villagesUnlocked = unlocked;
            firstVillageUnlockDay = day;
        }
    }

    public static Progress progress(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(Progress.TYPE);
    }

    /** True when {@code hardscrabble:gated_spread} with {@code village_lock} may place villages. */
    public static boolean open() {
        return open;
    }

    /** R165: an iron pickaxe or war hammer "(or better)": iron, ancient metal, mithril or adamantium. */
    public static boolean unlocks(ItemStack stack) {
        return ToolStats.of(stack).map(t -> (t.type() == ToolType.WAR_HAMMER || t.type() == ToolType.PICKAXE)
                && t.material().toolHarvestLevel() >= Material.IRON.toolHarvestLevel()).orElse(false);
    }

    private static long day(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld == null ? 1 : MoonCalendar.day(overworld.getDefaultClockTime());
    }

    static boolean lockEnabled() {
        return !MiteConfig.SPEC.isLoaded() || MiteConfig.VILLAGE_LOCK.getAsBoolean();
    }

    private static int minDays() {
        return MiteConfig.intOrDefault(MiteConfig.VILLAGE_LOCK_MIN_DAYS);
    }

    /** Recomputes {@link #open()} from the world state, the day and the config. */
    public static void refresh(MinecraftServer server) {
        open = !lockEnabled() || progress(server).villagesUnlocked && day(server) >= minDays();
    }

    /** Records the R165 condition (idempotent) and refreshes the cache. */
    public static void unlock(MinecraftServer server) {
        Progress p = progress(server);
        if (!p.villagesUnlocked) {
            p.villagesUnlocked = true;
            p.firstVillageUnlockDay = day(server);
            p.setDirty();
        }
        refresh(server);
    }

    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && unlocks(event.getCrafting())) unlock(player.level().getServer());
    }

    @SubscribeEvent
    static void onLevelLoad(LevelEvent.Load event) {
        // Once the overworld exists (its clock gives the real day) and before the spawn chunks generate.
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) refresh(level.getServer());
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) refresh(event.getServer()); // day condition and config reloads
    }

    /** {@code /villages} (EF-REG-04): the remaining unlock conditions. */
    public static int describe(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        refresh(server);
        if (!lockEnabled()) {
            source.sendSystemMessage(Component.translatable("hardscrabble.command.villages.unlocked"));
            return 1;
        }
        boolean crafted = progress(server).villagesUnlocked;
        source.sendSystemMessage(Component.translatable(crafted ? "hardscrabble.command.villages.tool_done" : "hardscrabble.command.villages.tool_missing"));
        if (minDays() > 0) source.sendSystemMessage(Component.translatable("hardscrabble.command.villages.days", day(server), minDays()));
        source.sendSystemMessage(Component.translatable(open ? "hardscrabble.command.villages.unlocked" : "hardscrabble.command.villages.locked"));
        return open ? 1 : 0;
    }
}
