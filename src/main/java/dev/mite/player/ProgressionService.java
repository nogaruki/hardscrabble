package dev.mite.player;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Formulas;
import dev.mite.metabolism.MetabolismService;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import dev.mite.rules.Rules;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * MITE XP (ET-B-02): the total XP in {@code hardscrabble:progression} is the source of truth. Vanilla XP gains and costs are cancelled
 * and redirected here; the vanilla fields are only a mirror (docs/02 §6.3.1): level and total clamped at 0, progress on the
 * MITE curve. Every change refreshes max health (EF-JOU-01) and the melee factor (EF-JOU-04).
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class ProgressionService {
    private ProgressionService() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);

    /** Total XP. Copied on death, the penalty is applied on clone. Synced to the owner only (HUD). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> PROGRESSION = ATTACHMENTS.register("progression",
            () -> AttachmentType.builder(() -> 0).serialize(dev.mite.attach.Migrations.versioned("progression", Codec.INT.fieldOf("xp"))).copyOnDeath()
                    .sync((holder, to) -> holder == to, ByteBufCodecs.VAR_INT).build());

    /** Emeralds sacrificed on {@code day} (audit 2 X1, RE-04): the daily cap of the gem sacrifice. Kept on death. */
    public record EmeraldSacrifices(long day, int count) {
        static final com.mojang.serialization.MapCodec<EmeraldSacrifices> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(EmeraldSacrifices::day), Codec.INT.fieldOf("count").forGetter(EmeraldSacrifices::count))
                .apply(i, EmeraldSacrifices::new));
    }

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EmeraldSacrifices>> EMERALD_SACRIFICES = ATTACHMENTS.register("emerald_sacrifices",
            () -> AttachmentType.builder(() -> new EmeraldSacrifices(0, 0))
                    .serialize(dev.mite.attach.Migrations.versioned("emerald_sacrifices", EmeraldSacrifices.CODEC)).copyOnDeath().build());

    /** B-2: owner of an XP orb dropped by a dead player (WIKI_THIRD rule); absent on ordinary orbs. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<UUID>> XP_OWNER = ATTACHMENTS.register("xp_owner",
            () -> AttachmentType.builder(() -> Util.NIL_UUID).serialize(dev.mite.attach.Migrations.versioned("xp_owner", UUIDUtil.CODEC.fieldOf("owner"))).build());

    static final Identifier MELEE = MiteMod.id("level_melee");

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static int xp(Player player) {
        // getData on a synced attachment syncs on first access, which crashes for connectionless fake players.
        if (player instanceof ServerPlayer sp && sp.connection == null && !player.hasData(PROGRESSION)) return 0;
        return player.getData(PROGRESSION);
    }

    public static int level(Player player) {
        return Formulas.levelForXp(xp(player));
    }

    public static void addXp(Player player, int amount) {
        player.increaseScore(amount);
        setXp(player, XpCurve.add(xp(player), amount));
    }

    public static void addLevels(Player player, int levels) {
        setXp(player, XpCurve.addLevels(xp(player), levels));
    }

    /** Server only; ignored for connectionless fake players. */
    public static void setXp(Player player, int xp) {
        if (!(player instanceof ServerPlayer sp) || sp.connection == null) return;
        int before = level(sp);
        sp.setData(PROGRESSION, XpCurve.clamp(xp));
        refresh(sp);
        if (levelUpSound(before, level(sp))) sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, sp.getSoundSource(), 0.75f, 1);
    }

    /** B-4: like vanilla, the level-up sound plays when a gain reaches a multiple of 5 (every 5 levels is also +1 heart, EF-JOU-01). */
    static boolean levelUpSound(int before, int after) {
        return after > 0 && Math.floorDiv(after, 5) > Math.floorDiv(before, 5);
    }

    private static void refresh(ServerPlayer player) {
        int xp = xp(player), level = Formulas.levelForXp(xp);
        player.experienceLevel = Math.max(0, level);
        player.totalExperience = Math.max(0, xp);
        player.experienceProgress = XpCurve.progress(xp);
        // Vanilla resends only when totalExperience changes, which it does not below 0.
        player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
        MetabolismService.updateMaxHealth(player);
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        double bonus = Formulas.meleeFactor(level) - 1;
        AttributeModifier old = attack.getModifier(MELEE);
        if (bonus == 0) attack.removeModifier(MELEE);
        else if (old == null || old.amount() != bonus)
            attack.addOrReplacePermanentModifier(new AttributeModifier(MELEE, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // Orbs, /xp add points, grindstone and furnace XP all go through giveExperiencePoints.
    @SubscribeEvent
    static void onXpChange(PlayerXpEvent.XpChange event) {
        event.setCanceled(true);
        addXp(event.getEntity(), event.getAmount());
    }

    // Enchanting, anvil and /xp add levels go through giveExperienceLevels; their checks read the mirrored level.
    @SubscribeEvent
    static void onLevelChange(PlayerXpEvent.LevelChange event) {
        event.setCanceled(true);
        addLevels(event.getEntity(), event.getLevels());
    }

    /** EF-JOU-05: after NeoForge copied the attachment, apply the configured death rule (none with keepInventory, outside strict mode: A-2). */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int xp = xp(event.getOriginal());
        boolean keep = !event.isWasDeath() || !Rules.strict(player) && player.level().getGameRules().get(GameRules.KEEP_INVENTORY);
        setXp(player, keep ? xp : MiteConfig.DEATH_PENALTY.get().xpAfterDeath(xp));
    }

    /**
     * B-3 (ENF-SAV-02, RE-04): a player the mod has never tracked (vanilla save, world played without the mod) starts from the
     * default MITE values, as the foreign world warning says: vanilla levels are wiped, not converted into free MITE XP.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.hasData(PROGRESSION)) return;
        setXp(p, 0);
    }

    /**
     * EF-JOU-05 (R159, changelog l.267): the game is saved at the moment of a player's death, so killing the game on the death
     * screen cannot bring back the world from before it. Saved on the next server tick, once the death drops are in the world.
     */
    private static boolean saveAfterDeath;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer) saveAfterDeath = true;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!saveAfterDeath) return;
        saveAfterDeath = false;
        event.getServer().saveEverything(true, false, false);
    }

    /** Vanilla fields written directly (/xp set, other mods, players from before the mod) are imported into the MITE total. */
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.connection == null) return;
        int xp = xp(p), level = Formulas.levelForXp(xp);
        boolean levelChanged = p.experienceLevel != Math.max(0, level);
        if (!levelChanged && p.experienceProgress == XpCurve.progress(xp)) return;
        setXp(p, levelChanged ? Formulas.xpForLevel(Math.min(p.experienceLevel, Formulas.MAX_LEVEL))
                : Formulas.xpForLevel(level) + Math.min(Math.round(p.experienceProgress * p.getXpNeededForNextLevel()), XpCurve.span(level) - 1));
    }
}
