package dev.mite.rules;


import com.google.gson.JsonObject;
import dev.mite.config.MiteClientConfig;
import dev.mite.config.MiteConfig;
import dev.mite.crafting.TimedCrafting;
import dev.mite.gametest.AuditTests;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import dev.mite.core.Formulas;
import dev.mite.core.MoonCalendar;
import dev.mite.lunar.LunarTests;
import dev.mite.core.Metabolism;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismService;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.player.ProgressionService;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import com.mojang.authlib.GameProfile;
import dev.mite.core.Quality;
import dev.mite.crafting.CraftQuality;
import dev.mite.player.XpCurve;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** GameTests for B06, B09, A01, A02, A10, A11, EF-CFG-02, EF-DIM-07, EF-PRG-03. They assume the default config (strict mode on, no developer profile). */
public final class RulesTests {
    private RulesTests() {}

    /** Logs the player out (saved to disk) and back in through the vanilla path: a new entity loaded from the saved data. */
    private static ServerPlayer relog(GameTestHelper h, ServerPlayer old) {
        MinecraftServer server = h.getLevel().getServer();
        server.getPlayerList().remove(old);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(old.getGameProfile(), false);
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        server.getPlayerList().loadPlayerData(p.nameAndId())
                .map(tag -> TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag)).ifPresent(p::load);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        return p;
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): trading.emerald_sacrifices_per_day sets the daily emerald cap (3 by default).
        MiteGameTests.add("config_emerald_sacrifices_per_day", 20, h -> {
            ServerPlayer capped = MetabolismTests.player(h), free = MetabolismTests.player(h);
            boolean first = MiteGameTests.withConfig(MiteConfig.EMERALD_SACRIFICES_PER_DAY, 1, () -> ItemRules.takeEmeraldSacrifice(capped));
            boolean second = MiteGameTests.withConfig(MiteConfig.EMERALD_SACRIFICES_PER_DAY, 1, () -> ItemRules.takeEmeraldSacrifice(capped));
            h.assertTrue(first && !second, "cap of 1: first " + first + ", second " + second);
            int taken = 0;
            while (taken < 10 && ItemRules.takeEmeraldSacrifice(free)) taken++;
            h.assertValueEqual(taken, 3, "sacrifices allowed by the default cap");
            h.succeed();
        });

        // A-4 (EF-CFG-02 CA-1, EF-REG-05): rules.dev_profile lifts strict mode.
        MiteGameTests.add("config_dev_profile", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            boolean strict = MiteGameTests.withConfig(MiteConfig.DEV_PROFILE, true, () -> !Rules.devProfile() || Rules.strict() || Rules.strict(p));
            h.assertFalse(strict, "strict mode with dev_profile=true");
            h.assertTrue(!Rules.devProfile() && Rules.strict() && Rules.strict(p), "strict mode lifted with dev_profile=false");
            h.succeed();
        });

        // dom-B-04: the countdown is saved on the player, so a reconnection neither resets nor loses it.
        MiteGameTests.add("respawn_timer_relog", 80, h -> {
            MinecraftServer server = h.getLevel().getServer();
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            p.setHealth(0);
            RespawnTimer.tick(server, 100);
            // Under 20 ticks: respawn_timer (tick(server, 20)) runs in parallel and respawns every player dead that long.
            h.runAfterDelay(10, () -> {
                ServerPlayer back = relog(h, p);
                h.assertTrue(back != p && back.isDeadOrDying(), "dead after the relog");
                RespawnTimer.tick(server, 100);
                long left = RespawnTimer.ticksLeft(back, server.overworld().getGameTime(), 100);
                h.assertTrue(left >= 85 && left <= 91, "ticks left after the relog: " + left);
                h.succeed();
            });
        });

        // EF-JOU-08 CA-1: a sacrificed diamond gives exactly 500 XP and is consumed.
        MiteGameTests.add("gem_sacrifice_diamond", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
            p.gameMode.useItem(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.assertValueEqual(ProgressionService.xp(p), 500, "XP");
            h.assertValueEqual(p.getMainHandItem().getCount(), 1, "diamonds left");
            h.succeed();
        });

        // audit 2 X1 (RE-04): at most 3 emeralds sacrificed a day (750 XP); the 4th is refused and kept, the next day allows more.
        MiteGameTests.add("gem_sacrifice_emerald_daily_cap", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 5));
            Runnable use = () -> p.gameMode.useItem(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            long day = MoonCalendar.day(h.getLevel().getServer().overworld().getDefaultClockTime());
            LunarTests.onDay(h, day, () -> {
                for (int i = 0; i < 4; i++) use.run();
                h.assertValueEqual(ProgressionService.xp(p), 750, "XP after 4 emeralds on one day");
                h.assertValueEqual(p.getMainHandItem().getCount(), 2, "emeralds left");
            });
            LunarTests.onDay(h, day + 1, use);
            h.assertValueEqual(ProgressionService.xp(p), 1000, "XP the next day");
            h.assertValueEqual(p.getMainHandItem().getCount(), 1, "emeralds left the next day");
            h.succeed();
        });

        // EF-REG-02 CA-1: in strict mode an operator's /gamemode creative and /give fail; player commands stay allowed.
        MiteGameTests.add("strict_mode_refuses_cheats", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            MinecraftServer server = h.getLevel().getServer();
            p.setGameMode(GameType.SURVIVAL); // the GameTest server defaults to Creative
            CommandSourceStack op = p.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
            server.getCommands().performPrefixedCommand(op, "gamemode creative");
            server.getCommands().performPrefixedCommand(op, "give @s minecraft:diamond");
            h.assertValueEqual(p.gameMode.getGameModeForPlayer(), GameType.SURVIVAL, "game mode");
            h.assertValueEqual(p.getInventory().countItem(Items.DIAMOND), 0, "given diamonds");
            h.assertFalse(Rules.isCheat(server.getCommands().getDispatcher().parse("stats", op)), "/stats is a cheat");
            h.assertFalse(p.setGameMode(GameType.CREATIVE), "direct switch to Creative");
            h.succeed();
        });

        // EF-REG-05: /mitedev is registered and nourish fills the metabolism.
        MiteGameTests.add("mitedev_nourish", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("mitedev") != null, "/mitedev registered");
            Metabolism m = MetabolismService.get(p);
            m.satiety = m.nutrition = m.protein = m.phyto = 0;
            MiteCommands.nourish(p);
            int cap = MetabolismService.cap(p);
            h.assertTrue(m.satiety == cap && m.nutrition == cap, "food filled: " + m.satiety + "/" + m.nutrition);
            h.assertTrue(m.protein == Formulas.NUTRIENT_MAX && m.phyto == Formulas.NUTRIENT_MAX, "nutrients filled");
            h.succeed();
        });

        // /mitedev dungeon: the nearest spawner of the loaded chunks.
        MiteGameTests.add("mitedev_dungeon", 20, h -> {
            BlockPos near = h.absolutePos(new BlockPos(1, 1, 1)), far = h.absolutePos(new BlockPos(4, 1, 4));
            h.getLevel().setBlock(far, Blocks.SPAWNER.defaultBlockState(), 2);
            h.assertValueEqual(MiteCommands.nearestSpawner(h.getLevel(), near, 1), far, "only spawner");
            h.getLevel().setBlock(near, Blocks.SPAWNER.defaultBlockState(), 2);
            h.assertValueEqual(MiteCommands.nearestSpawner(h.getLevel(), near.above(), 1), near, "nearest spawner");
            h.succeed();
        });

        // EF-JOU-06 CA-1 with a short delay: a dead player who does not click respawns once the timer runs out.
        MiteGameTests.add("respawn_timer", 60, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            MinecraftServer server = h.getLevel().getServer();
            UUID id = p.getUUID();
            p.setHealth(0);
            RespawnTimer.tick(server, 20);
            h.assertTrue(server.getPlayerList().getPlayer(id) == p, "respawned before the delay");
            h.runAfterDelay(21, () -> {
                RespawnTimer.tick(server, 20);
                ServerPlayer now = server.getPlayerList().getPlayer(id);
                h.assertTrue(now != null && now != p && now.isAlive(), "respawned after the delay");
                h.succeed();
            });
        });

        // EF-REG-01 CA-1 (dom-A-01): /difficulty behind /execute is refused, and a change made elsewhere snaps back to Hard.
        MiteGameTests.add("difficulty_lock_execute", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute run difficulty peaceful");
            h.assertValueEqual(server.getWorldData().getDifficulty(), Difficulty.HARD, "difficulty after /execute");
            server.setDifficulty(Difficulty.NORMAL, true); // what a datapack function would do
            h.runAfterDelay(2, () -> {
                h.assertValueEqual(server.getWorldData().getDifficulty(), Difficulty.HARD, "difficulty after a tick");
                h.succeed();
            });
        });

        // dom-A-02: lock_difficulty=false unlocks an already locked world.
        MiteGameTests.add("difficulty_lock_config", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            boolean unlocked = MiteGameTests.withConfig(MiteConfig.LOCK_DIFFICULTY, false, () -> {
                Rules.applyLock(server);
                return !server.getWorldData().isDifficultyLocked();
            });
            Rules.applyLock(server);
            h.assertTrue(unlocked, "still locked with lock_difficulty=false");
            h.assertTrue(server.getWorldData().isDifficultyLocked() && server.getWorldData().getDifficulty() == Difficulty.HARD, "locked on Hard again");
            h.succeed();
        });

        // EF-DIM-07: a rocket boosts a gliding player by default, and is refused with elytra_firework_boost=false.
        MiteGameTests.add("elytra_firework_boost", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 2));
            p.startFallFlying();
            MiteGameTests.withConfig(MiteConfig.ELYTRA_FIREWORK_BOOST, false, () -> p.gameMode.useItem(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND));
            h.assertValueEqual(p.getMainHandItem().getCount(), 2, "rockets after a refused boost");
            p.gameMode.useItem(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.assertValueEqual(p.getMainHandItem().getCount(), 1, "rockets after a boost");
            h.getLevel().getEntities(EntityTypes.FIREWORK_ROCKET, e -> true).forEach(e -> e.discard());
            h.succeed();
        });

        // ET-A-02: a player listed in rules.allowed_ops escapes strict mode.
        MiteGameTests.add("strict_mode_allowed_ops", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            CommandSourceStack op = p.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER);
            boolean exempt = MiteGameTests.withConfig(MiteConfig.ALLOWED_OPS, List.of(p.getUUID().toString()), () -> {
                h.getLevel().getServer().getCommands().performPrefixedCommand(op, "give @s minecraft:diamond");
                return !Rules.strict(p);
            });
            h.assertTrue(exempt && Rules.strict(p), "exemption follows allowed_ops");
            h.assertValueEqual(p.getInventory().countItem(Items.DIAMOND), 1, "given diamonds");
            h.succeed();
        });

        // EF-PRG-03 (M25): bare hands on a log are refused with the "tool too weak" message, and the log hint shows once (EF-PRG-02 CA-2).
        MiteGameTests.add("tool_too_weak_message", 20, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            float speed = p.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState(), h.absolutePos(BlockPos.ZERO));
            boolean throttled = !TimedCrafting.refuse(p, "hardscrabble.harvest.tool_too_weak"); // the event already sent it
            boolean hinted = p.getData(Hints.SEEN).contains("log"), again = Hints.show(p, "log");
            h.assertValueEqual(speed, 0f, "bare-hand log speed");
            h.assertTrue(throttled, "refusal message not sent");
            h.assertTrue(hinted && !again, "log hint shown exactly once");
            h.succeed();
        });

        // dom-A-11: /state also shows strict mode and the lunar day.
        MiteGameTests.add("state_rules_and_moon", 20, h -> {
            List<String> keys = MiteCommands.stateLines(MetabolismTests.player(h)).stream()
                    .map(c -> c.getContents() instanceof TranslatableContents t ? t.getKey() : "").toList();
            h.assertTrue(keys.contains("hardscrabble.command.state.rules") && keys.contains("hardscrabble.command.state.moon"), "state lines " + keys);
            // the world day, also from a dimension without a clock (the Nether here, the Underworld too)
            var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            ServerPlayer away = new ServerPlayer(h.getLevel().getServer(), nether, new GameProfile(UUID.randomUUID(), "test-mock-player"),
                    net.minecraft.server.level.ClientInformation.createDefault());
            LunarTests.onDay(h, 32, () -> {
                Object[] moon = MiteCommands.stateLines(away).stream().map(c -> (TranslatableContents) c.getContents())
                        .filter(t -> t.getKey().equals("hardscrabble.command.state.moon")).findFirst().orElseThrow().getArgs();
                h.assertValueEqual(moon[0], (Object) 32L, "day read from the Nether");
                h.assertValueEqual(((TranslatableContents) ((Component) moon[1]).getContents()).getKey(), "moon.hardscrabble.blood", "moon read from the Nether");
            });
            h.succeed();
        });

        // A-1 (EF-REG-02): strict mode refuses Spectator too, also through the F3+F4 / F3+N path (GameModeCommand.setGameMode);
        // A-4: with strict_mode=false both modes are allowed again.
        MiteGameTests.add("strict_mode_refuses_spectator", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setGameMode(GameType.SURVIVAL);
            GameModeCommand.setGameMode(p, GameType.SPECTATOR); // what the change-game-mode packet calls
            h.assertValueEqual(p.gameMode.getGameModeForPlayer(), GameType.SURVIVAL, "game mode after F3+N");
            boolean spectator = MiteGameTests.withConfig(MiteConfig.STRICT_MODE, false, () -> {
                boolean b = p.setGameMode(GameType.SPECTATOR);
                p.setGameMode(GameType.SURVIVAL);
                return b;
            });
            h.assertTrue(spectator, "Spectator refused with strict_mode=false");
            h.assertFalse(p.setGameMode(GameType.SPECTATOR), "Spectator allowed in strict mode");
            h.succeed();
        });

        // EF-REG-02: a player who logs in already in Creative is put back to Survival; a hardcore death may still make a spectator.
        MiteGameTests.add("strict_mode_login_and_hardcore", 20, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h); // gameMode() reads Creative
            p.gameMode.changeGameModeForPlayer(GameType.CREATIVE); // as saved, without the change event
            Rules.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            GameType mode = p.gameMode.getGameModeForPlayer();
            h.assertValueEqual(mode, GameType.SURVIVAL, "game mode after a Creative login");
            h.assertFalse(Rules.refusedMode(p, GameType.SPECTATOR, true), "hardcore death spectator refused");
            h.assertTrue(Rules.refusedMode(p, GameType.SPECTATOR, false), "Spectator allowed outside hardcore");
            h.assertTrue(Rules.refusedMode(p, GameType.CREATIVE, true), "Creative allowed in hardcore");
            h.succeed();
        });

        // A-2, P-6: strict mode turns keepInventory and the locator bar off, whatever set them.
        MiteGameTests.add("strict_mode_game_rules", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
            server.getGameRules().set(GameRules.LOCATOR_BAR, true, server);
            Rules.applyStrictRules(server);
            h.assertFalse(server.getGameRules().get(GameRules.KEEP_INVENTORY), "keepInventory in strict mode");
            h.assertFalse(server.getGameRules().get(GameRules.LOCATOR_BAR), "locator bar in strict mode");
            h.succeed();
        });

        // EF-CFG-03 (A-3): the Modern profile lifts the locks with the mcmod penalty; Server opens strongboxes to operators;
        // Strict R196 restores the defaults.
        MiteGameTests.add("config_profiles", 20, h -> {
            MiteConfig.Profile.MODERN.apply();
            boolean modern = !MiteConfig.STRICT_MODE.get() && !MiteConfig.LOCK_DIFFICULTY.get() && MiteConfig.DEATH_PENALTY.get() == XpCurve.DeathPenalty.MCMOD_LEVEL;
            MiteConfig.Profile.SERVER.apply();
            boolean server = MiteConfig.STRICT_MODE.get() && MiteConfig.STRONGBOX_OP_ACCESS.get() && !MiteConfig.DEDICATED_BLIGHT.get();
            MiteConfig.Profile.STRICT_R196.apply();
            h.assertTrue(modern, "Modern profile");
            h.assertTrue(server, "Server profile");
            h.assertTrue(MiteConfig.STRICT_MODE.get() && MiteConfig.LOCK_DIFFICULTY.get() && !MiteConfig.STRONGBOX_OP_ACCESS.get()
                    && MiteConfig.DEATH_PENALTY.get() == XpCurve.DeathPenalty.R196_CODE, "Strict R196 profile = defaults");
            h.succeed();
        });

        // A-4: rules.respawn_timer_seconds drives the forced respawn delay.
        MiteGameTests.add("respawn_timer_config", 20, h -> {
            int delay = MiteGameTests.withConfig(MiteConfig.RESPAWN_TIMER_SECONDS, 30, RespawnTimer::delayTicks);
            h.assertValueEqual(delay, 600, "delay at 30 s");
            h.assertValueEqual(RespawnTimer.delayTicks(), 2400, "delay at 120 s");
            h.succeed();
        });

        // A-5 (changelog l.594): /stats also writes the statistics to players/stats/<player>.txt.
        MiteGameTests.add("stats_written_to_file", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ProgressionService.setXp(p, 750);
            Path file = MiteCommands.writeStats(p, 10, 750, 300);
            try {
                String text = file == null ? "" : Files.readString(file);
                h.assertTrue(text.contains("Level: 10 (750 XP, 300 to the next level)") && text.contains("Max health: 10"), "stats file: " + text);
            } catch (IOException e) {
                throw h.assertionException(Component.literal(e.toString()));
            }
            h.succeed();
        });

        // X12 / B-1 (changelog l.267, R159): a player's death saves the game, so killing the process on the death screen does not
        // bring back the world from before it.
        MiteGameTests.add("death_saves_game", 40, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            MinecraftServer server = h.getLevel().getServer();
            Path saved = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID() + ".dat");
            h.assertFalse(Files.exists(saved), "player saved before dying");
            p.setHealth(0); // a fresh player is invulnerable for 60 ticks: die directly
            p.die(p.damageSources().genericKill());
            h.runAfterDelay(2, () -> {
                boolean exists = Files.exists(saved);
                h.assertTrue(exists, "game not saved at death");
                h.succeed();
            });
        });

        // P-3 (EF-MUL-01 CA-1): a client without the mod (vanilla negotiation) is refused, because the mod's payloads are required.
        MiteGameTests.add("vanilla_client_refused", 20, h -> {
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            var listener = new ServerConfigurationPacketListenerImpl(h.getLevel().getServer(), connection,
                    CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "vanilla"), false));
            h.assertFalse(NetworkRegistry.initializeOtherConnection(listener), "vanilla client accepted");
            Object sent;
            boolean disconnected = false;
            while ((sent = channel.readOutbound()) != null) disconnected |= sent instanceof ClientboundDisconnectPacket;
            h.assertTrue(disconnected, "no disconnect message sent");
            h.succeed();
        });

        // Changelog l.596: the durability modifier of the quality shows in the tooltip (crafting output slot included).
        MiteGameTests.add("quality_tooltip_durability", 20, h -> {
            ItemStack pick = new ItemStack(BuiltInRegistries.ITEM.getValue(dev.mite.MiteMod.id("silver_pickaxe")));
            pick.set(CraftQuality.QUALITY.get(), Quality.FINE);
            List<Component> lines = new ArrayList<>(List.of(pick.getHoverName()));
            NeoForge.EVENT_BUS.post(new ItemTooltipEvent(pick, null, lines, TooltipFlag.NORMAL, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT));
            boolean shown = lines.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("tooltip.hardscrabble.quality")
                    && t.getArgs().length == 2 && t.getArgs()[1].equals(Math.round(Quality.FINE.durabilityModifier * 100)));
            h.assertTrue(shown, "quality line " + lines);
            h.succeed();
        });

        // EF-CFG-02 CA-1 (dom-A-09): every server and client key has an EN and a FR comment and EN/FR names under the default translation key.
        MiteGameTests.add("config_keys_documented", 20, h -> {
            JsonObject en = AuditTests.json("assets/hardscrabble/lang/en_us.json").getAsJsonObject(), fr = AuditTests.json("assets/hardscrabble/lang/fr_fr.json").getAsJsonObject();
            List<String> problems = new ArrayList<>();
            for (var value : MiteConfig.values(MiteConfig.SPEC, MiteClientConfig.SPEC)) {
                String leaf = value.getPath().getLast(), key = "hardscrabble.configuration." + leaf;
                String comment = value.getSpec().getComment();
                if (comment == null || comment.split("\n").length < 2) problems.add(leaf + ": EN/FR comment");
                for (String k : new String[] {key, key + ".tooltip"}) {
                    if (!en.has(k) || !fr.has(k)) problems.add(leaf + ": lang " + k);
                }
                // Q-7: requirement ids and [INFERRED] marks stay in the TOML comment, not in the player's tooltip.
                for (JsonObject lang : new JsonObject[] {en, fr}) {
                    String tip = lang.has(key + ".tooltip") ? lang.get(key + ".tooltip").getAsString() : "";
                    if (tip.matches(".*(\\b(EF|ET|ENF|PD|Q)-[A-Z0-9]|\\[|INF[EÉ]R).*")) problems.add(leaf + ": internal id in tooltip: " + tip);
                }
            }
            h.assertTrue(problems.isEmpty(), String.join(", ", problems));
            h.succeed();
        });
    }
}
