package dev.mite.rules;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Global locks (EF-REG-01) and strict mode (EF-REG-02). Sleeping needs 100% of players: vanilla default of
 * players_sleeping_percentage (EF-MUL-02).
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class Rules {
    private Rules() {}

    /** Developer profile (config {@code rules.dev_profile}): lifts strict mode and unlocks /mitedev for operators. */
    public static boolean devProfile() {
        return MiteConfig.DEV_PROFILE.getAsBoolean();
    }

    public static boolean strict() {
        return MiteConfig.STRICT_MODE.getAsBoolean() && !devProfile();
    }

    /** EF-REG-01 CA-1: Hard and locked when {@code lock_difficulty} is on, unlocked otherwise (an already locked world included). */
    static void applyLock(MinecraftServer server) {
        boolean lock = MiteConfig.LOCK_DIFFICULTY.getAsBoolean();
        if (lock) server.setDifficulty(Difficulty.HARD, true);
        server.setDifficultyLocked(lock);
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MiteConfig.PROFILE.get().apply(); // EF-CFG-03 CA-1: the profile applies when the world loads
        applyLock(event.getServer());
        applyStrictRules(event.getServer());
    }

    /**
     * Strict mode turns off the game rules that cancel a MITE rule, whatever set them (world creation screen, console,
     * datapack): keepInventory would cancel the death penalty (A-2, EF-JOU-05, RE-04) and the locator bar is a modern
     * multiplayer aid that hides the XP bar (P-6).
     */
    static void applyStrictRules(MinecraftServer server) {
        if (!strict()) return;
        GameRules rules = server.getGameRules();
        if (rules.get(GameRules.KEEP_INVENTORY)) rules.set(GameRules.KEEP_INVENTORY, false, server);
        if (rules.get(GameRules.LOCATOR_BAR)) rules.set(GameRules.LOCATOR_BAR, false, server);
    }

    /** Catches what the command check cannot see (datapack functions, mods, console): difficulty and strict game rules snap back. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (MiteConfig.LOCK_DIFFICULTY.getAsBoolean() && server.getWorldData().getDifficulty() != Difficulty.HARD) applyLock(server);
        applyStrictRules(server);
    }

    @SubscribeEvent
    static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        if (MiteConfig.LOCK_DIFFICULTY.getAsBoolean() && uses(parse, "difficulty")) {
            event.setCanceled(true);
            source.sendFailure(Component.translatable("hardscrabble.rules.difficulty_locked"));
        } else if (source.getPlayer() != null && strict(source.getPlayer()) && isCheat(parse)) {
            event.setCanceled(true);
            source.sendFailure(Component.translatable("hardscrabble.rules.strict_mode"));
        }
    }

    /** Strict mode for this player: {@code rules.allowed_ops} (name or UUID) are exempt (ET-A-02). */
    public static boolean strict(Player player) {
        if (!strict()) return false;
        List<? extends String> allowed = MiteConfig.ALLOWED_OPS.get();
        return !allowed.contains(player.getGameProfile().name()) && !allowed.contains(player.getUUID().toString());
    }

    /** Whether a parsed node (redirects such as {@code /execute run} included) is the root command {@code name}. */
    static boolean uses(ParseResults<CommandSourceStack> parse, String name) {
        return anyNode(parse, n -> n instanceof LiteralCommandNode<?> literal && literal.getLiteral().equals(name));
    }

    /** A command is a cheat when one of its parsed nodes (redirects included) is out of reach of a player without permissions. */
    static boolean isCheat(ParseResults<CommandSourceStack> parse) {
        CommandSourceStack plain = parse.getContext().getSource().withPermission(PermissionSet.NO_PERMISSIONS);
        return anyNode(parse, n -> !n.canUse(plain));
    }

    private static boolean anyNode(ParseResults<CommandSourceStack> parse, Predicate<CommandNode<CommandSourceStack>> test) {
        for (CommandContextBuilder<CommandSourceStack> c = parse.getContext(); c != null; c = c.getChild()) {
            for (ParsedCommandNode<CommandSourceStack> node : c.getNodes()) if (test.test(node.getNode())) return true;
        }
        return false;
    }

    /**
     * EF-REG-02 (A-1): Creative and Spectator (which sees caves and ores through rock) are refused whatever the trigger: command,
     * F3+F4 / F3+N packet, LAN screen, default game mode. Only a hardcore world's death may still turn a player into a spectator.
     */
    static boolean refusedMode(Player player, GameType mode) {
        return refusedMode(player, mode, player.level().getLevelData().isHardcore());
    }

    static boolean refusedMode(Player player, GameType mode, boolean hardcore) {
        return (mode == GameType.CREATIVE || mode == GameType.SPECTATOR && !hardcore) && strict(player);
    }

    @SubscribeEvent
    static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (refusedMode(event.getEntity(), event.getNewGameMode())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && refusedMode(p, p.gameMode())) p.setGameMode(GameType.SURVIVAL);
    }

    /** EF-DIM-07: with {@code elytra_firework_boost} off, a firework rocket cannot be fired while gliding. */
    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!MiteConfig.ELYTRA_FIREWORK_BOOST.getAsBoolean() && event.getEntity().isFallFlying() && event.getItemStack().is(Items.FIREWORK_ROCKET)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
