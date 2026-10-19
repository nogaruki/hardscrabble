package dev.mite.curse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.CurseSelector;
import dev.mite.enchanting.EnchantingContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * Witch curses (EF-MAL-01, ET-O-01). The truth is world-level saved data ({@code hardscrabble:curses}, one record per player), so a
 * witch's death lifts her curses from offline players too and a curse survives the player's death (CA-1, CA-2). Each owner
 * gets a synced {@link View} for the client-side effects and the HUD indicator (EF-UI-08).
 */
public final class Curses {
    private Curses() {}

    /** EF-MAL-01: a curse takes effect this many ticks after the witch cast it. */
    public static final int DELAY = 6000;
    /** EF-MAL-01: chance to curse when a witch targets a player she can see. */
    public static final float CHANCE = 0.25f;

    record Entry(UUID witch, Curse curse, long start, boolean notified, boolean revealed) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("witch").forGetter(Entry::witch),
                Codec.STRING.xmap(Curse::valueOf, Curse::name).fieldOf("curse").forGetter(Entry::curse),
                Codec.LONG.fieldOf("start").forGetter(Entry::start),
                Codec.BOOL.fieldOf("notified").forGetter(Entry::notified),
                Codec.BOOL.fieldOf("revealed").forGetter(Entry::revealed)).apply(i, Entry::new));

        boolean active(long now) {
            return now - start >= DELAY;
        }

        Entry flags(boolean notified, boolean revealed) {
            return new Entry(witch, curse, start, notified, revealed);
        }
    }

    public static final class Data extends SavedData {
        public static final Codec<Data> CODEC = dev.mite.attach.Migrations.versioned("curses",
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC).fieldOf("entries")).codec().xmap(Data::new, d -> d.entries);
        static final SavedDataType<Data> TYPE = new SavedDataType<>(MiteMod.id("curses"), Data::new, CODEC);
        final Map<UUID, Entry> entries;

        Data() {
            this(Map.of());
        }

        Data(Map<UUID, Entry> entries) {
            this.entries = new HashMap<>(entries);
        }

        void put(UUID player, @Nullable Entry entry) {
            if (entry == null) entries.remove(player);
            else entries.put(player, entry);
            setDirty();
        }
    }

    /** What the owner's client knows: the active curse ordinal (-1 = none) and whether it was discovered. */
    public record View(int curse, boolean revealed) {
        static final View NONE = new View(-1, false);
        static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::curse, ByteBufCodecs.BOOL, View::revealed, View::new);
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);
    /** Transient (rebuilt from the saved data every second), synced to the owner only. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<View>> VIEW = ATTACHMENTS.register("curse_view",
            () -> AttachmentType.builder(() -> View.NONE).sync((holder, to) -> holder == to, View.STREAM_CODEC).build());

    static Data data(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(Data.TYPE);
    }

    private static boolean enabled() {
        return MiteConfig.SPEC.isLoaded() && MiteConfig.CURSES_ENABLED.get();
    }

    /** Active curse of the player, on either side (the client reads its view); null when none or still pending. */
    public static @Nullable Curse active(Player player) {
        if (!enabled()) return null;
        if (player instanceof ServerPlayer sp) {
            Entry e = data(sp.level().getServer()).entries.get(sp.getUUID());
            return e != null && e.active(sp.level().getGameTime()) ? e.curse() : null;
        }
        int c = player.getData(VIEW).curse();
        return c < 0 ? null : Curse.values()[c];
    }

    public static boolean has(Player player, Curse curse) {
        return active(player) == curse;
    }

    /** True if the player suffers {@code curse}; the first time on the server, the player discovers it (EF-MAL-01). */
    public static boolean trigger(Player player, Curse curse) {
        if (!has(player, curse)) return false;
        if (player instanceof ServerPlayer sp) {
            Data data = data(sp.level().getServer());
            Entry e = data.entries.get(sp.getUUID());
            if (!e.revealed()) {
                data.put(sp.getUUID(), e.flags(true, true));
                sp.sendSystemMessage(Component.translatable("hardscrabble.curse.revealed", Component.translatable(curse.langKey())).withStyle(ChatFormatting.DARK_PURPLE));
                sync(sp);
            }
        }
        return true;
    }

    /** Clumsiness doubles craft time and quality XP cost (EF-MAL-02 #8); called by the crafting domain. */
    public static boolean isClumsy(Player player) {
        return trigger(player, Curse.CLUMSINESS);
    }

    /** The active curse once the player has discovered it (EF-UI-08), else null. */
    public static @Nullable Curse known(ServerPlayer player) {
        Curse c = active(player);
        return c != null && data(player.level().getServer()).entries.get(player.getUUID()).revealed() ? c : null;
    }

    /** EF-MAL-02 #3: false while the player suffers Can't Run. Read-only (vanilla may query it outside a sprint): the client
     * blocks sprinting once the curse is revealed, so the server still sees the first sprint and {@link CurseEffects#tickMovement}
     * reveals it (read by {@code PlayerMixin}, MX: hasEnoughFoodToDoExhaustiveManoeuvres). */
    public static boolean canSprint(Player player) {
        if (player instanceof ServerPlayer) return !has(player, Curse.CANT_RUN);
        View v = player.getData(VIEW);
        return !(enabled() && v.revealed() && v.curse() == Curse.CANT_RUN.ordinal());
    }

    /** EF-MAL-02 #1: wear multiplier of the player's tools and weapons (block wear in HarvestRules, attack wear in CurseEffects). */
    public static int wearFactor(Player player) {
        return trigger(player, Curse.EQUIPMENT_DECAY) ? CurseEffects.DECAY_FACTOR : 1;
    }

    /** EF-MAL-02 #9: vegetation that entangles (no water, unlike minecraft:replaceable_by_trees). */
    public static final TagKey<Block> ENTANGLING = TagKey.create(Registries.BLOCK, MiteMod.id("entangling"));

    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        var t = tag.apply(ENTANGLING).addTag(BlockTags.LEAVES).addTag(BlockTags.SMALL_FLOWERS).addTag(BlockTags.CAVE_VINES);
        for (Block b : new Block[] {Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.BUSH,
                Blocks.FIREFLY_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.HANGING_ROOTS,
                Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.PITCHER_PLANT, Blocks.SWEET_BERRY_BUSH,
                Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT,
                Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS}) {
            t.add(BuiltInRegistries.BLOCK.getResourceKey(b).orElseThrow());
        }
    }

    /** Pending or active record, if any (none while curses are disabled). */
    public static @Nullable Curse cursed(ServerPlayer player) {
        if (!enabled()) return null;
        Entry e = data(player.level().getServer()).entries.get(player.getUUID());
        return e == null ? null : e.curse();
    }

    /** Curses the player with {@code curse} from now; it takes effect {@link #DELAY} ticks after {@code start}. */
    public static void curse(ServerPlayer player, UUID witch, Curse curse, long start) {
        data(player.level().getServer()).put(player.getUUID(), new Entry(witch, curse, start, false, false));
    }

    /** The curse a witch gives to a player: deterministic from her UUID and the player's name (ET-O-01). */
    public static Curse select(UUID witch, String playerName) {
        long seed = witch.getMostSignificantBits() ^ witch.getLeastSignificantBits();
        return Curse.values()[CurseSelector.select(seed, playerName, Curse.values().length)];
    }

    /** EF-MAL-01: 1 in 4 when a witch targets a visible player, unless already cursed (pending or active). */
    static void onWitchTarget(Witch witch, ServerPlayer player) {
        if (!enabled() || player.isCreative() || player.isSpectator() || cursed(player) != null || !witch.hasLineOfSight(player)) return;
        if (witch.getRandom().nextFloat() >= CHANCE) return;
        curse(player, witch.getUUID(), select(witch.getUUID(), player.getGameProfile().name()), player.level().getGameTime());
        witch.setPersistenceRequired(); // she must stay killable: a despawned witch would leave an orphan curse
    }

    /** Disenchanting bottle (EF-ENC-06 CA-1): lifts the curse, pending or active. False when there is none. */
    public static boolean lift(ServerPlayer player) {
        if (cursed(player) == null) return false;
        data(player.level().getServer()).put(player.getUUID(), null);
        player.sendSystemMessage(Component.translatable("hardscrabble.curse.lifted").withStyle(ChatFormatting.DARK_PURPLE));
        sync(player);
        return true;
    }

    /** A witch's death (or removal) lifts every curse she cast, offline players included (CA-1); online players who knew they
     * were cursed are told. */
    public static void liftWitch(MinecraftServer server, UUID witch) {
        Data data = data(server);
        data.entries.entrySet().stream().filter(e -> e.getValue().witch().equals(witch)).toList().forEach(e -> {
            data.put(e.getKey(), null);
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) return;
            if (e.getValue().notified() && enabled()) p.sendSystemMessage(Component.translatable("hardscrabble.curse.lifted").withStyle(ChatFormatting.DARK_PURPLE));
            sync(p);
        });
    }

    /** Once a second: "You have been cursed" when the delay ends, and the owner's view. */
    static void tick(ServerPlayer player) {
        Data data = data(player.level().getServer());
        Entry e = data.entries.get(player.getUUID());
        if (enabled() && e != null && !e.notified() && e.active(player.level().getGameTime())) {
            data.put(player.getUUID(), e.flags(true, e.revealed()));
            player.sendSystemMessage(Component.translatable("hardscrabble.curse.cursed").withStyle(ChatFormatting.DARK_PURPLE));
        }
        sync(player);
    }

    static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        Curse c = active(player);
        Entry e = c == null ? null : data(player.level().getServer()).entries.get(player.getUUID());
        View view = e == null ? View.NONE : new View(c.ordinal(), e.revealed());
        if (!view.equals(player.getData(VIEW))) player.setData(VIEW, view);
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((LivingChangeTargetEvent e) -> {
            if (e.getEntity() instanceof Witch w && e.getNewAboutToBeSetTarget() instanceof ServerPlayer p) onWitchTarget(w, p);
        });
        bus.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof Witch w && w.level().getServer() != null) liftWitch(w.level().getServer(), w.getUUID());
        });
        bus.addListener((EntityLeaveLevelEvent e) -> {
            if (e.getEntity() instanceof Witch w && w.getRemovalReason() == Entity.RemovalReason.DISCARDED && w.level().getServer() != null) liftWitch(w.level().getServer(), w.getUUID());
        });
        bus.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (!e.getItemStack().is(EnchantingContent.DISENCHANTING_BOTTLE.get()) || !(e.getEntity() instanceof ServerPlayer p) || !lift(p)) return;
            e.getItemStack().consume(1, p);
            p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 1, 1);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        });
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0) tick(p);
        });
        CurseEffects.register(bus);
    }
}
