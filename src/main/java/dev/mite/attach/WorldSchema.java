package dev.mite.attach;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;

/**
 * ENF-SAV-02: a world first opened with the mod after it was played without it is not supported. It loads with default
 * MITE values (attachment defaults) and every player is warned on login. The marker {@code hardscrabble:schema} is written the first
 * time the mod sees the world; a world without it whose clock already ran comes from elsewhere.
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class WorldSchema extends SavedData {
    static final Codec<WorldSchema> CODEC = Migrations.versioned("schema", RecordCodecBuilder.<WorldSchema>mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("foreign", false).forGetter(s -> s.foreign)).apply(i, WorldSchema::new))).codec();
    static final SavedDataType<WorldSchema> TYPE = new SavedDataType<>(MiteMod.id("schema"), () -> new WorldSchema(false), CODEC);

    /** True when the world existed before the mod first loaded it. */
    public final boolean foreign;

    WorldSchema(boolean foreign) {
        this.foreign = foreign;
    }

    /** The marker, created on first sight: foreign when the overworld clock already ran without it. */
    static WorldSchema mark(MinecraftServer server) {
        @Nullable WorldSchema s = server.getDataStorage().get(TYPE);
        if (s != null) return s;
        s = new WorldSchema(server.overworld().getGameTime() > 0);
        server.getDataStorage().set(TYPE, s);
        s.setDirty();
        if (s.foreign) MiteMod.LOGGER.warn("This world was played without MITE: not supported, players get default MITE values (ENF-SAV-02)");
        return s;
    }

    public static boolean foreign(MinecraftServer server) {
        return mark(server).foreign;
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        mark(event.getServer());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && foreign(player.level().getServer()))
            player.sendSystemMessage(Component.translatable("warning.hardscrabble.foreign_world").withStyle(ChatFormatting.RED));
    }
}
