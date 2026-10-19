package dev.mite.rules;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Formulas;
import dev.mite.core.Metabolism;
import dev.mite.core.MoonCalendar;
import dev.mite.lunar.Lunar;
import dev.mite.metabolism.MetabolismService;
import dev.mite.player.PlayerLevel;
import dev.mite.player.ProgressionService;
import dev.mite.curse.Curse;
import dev.mite.curse.Curses;
import dev.mite.worldgen.village.VillageLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import java.util.function.ToIntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Player commands /stats /state (also strict mode and lunar day, dom-A-11) /villages /commands (EF-REG-04, permission 0, read-only) and developer commands
 * /mitedev heal|nourish|malnourish|killmobs|grow|dungeon (EF-REG-05, operators with the developer profile). Output is translated
 * on the client.
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class MiteCommands {
    private MiteCommands() {}

    static final List<String> PLAYER_COMMANDS = List.of("stats", "state", "villages", "commands");

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(literal("stats").executes(c -> stats(c.getSource().getPlayerOrException())));
        d.register(literal("state").executes(c -> state(c.getSource().getPlayerOrException())));
        d.register(literal("villages").executes(c -> VillageLock.describe(c.getSource())));
        d.register(literal("commands").executes(c -> {
            PLAYER_COMMANDS.forEach(name -> say(c.getSource(), Component.translatable("hardscrabble.command.help." + name)));
            return PLAYER_COMMANDS.size();
        }));
        d.register(literal("mitedev").requires(s -> Rules.devProfile() && Commands.LEVEL_GAMEMASTERS.check(s.permissions()))
                .then(literal("heal").executes(c -> dev(c, "heal", p -> { p.setHealth(p.getMaxHealth()); return 1; })))
                .then(literal("nourish").executes(c -> dev(c, "nourish", MiteCommands::nourish)))
                .then(literal("malnourish").executes(c -> dev(c, "malnourish", MiteCommands::malnourish)))
                .then(literal("killmobs").executes(c -> dev(c, "killmobs", MiteCommands::killMobs)))
                .then(literal("grow").executes(c -> dev(c, "grow", MiteCommands::grow)))
                .then(literal("dungeon").executes(c -> dungeon(c.getSource()))));
    }

    private static int say(CommandSourceStack source, Component message) {
        source.sendSystemMessage(message);
        return 1;
    }

    private static int dev(CommandContext<CommandSourceStack> c, String name, ToIntFunction<ServerPlayer> action) throws CommandSyntaxException {
        int n = action.applyAsInt(c.getSource().getPlayerOrException());
        c.getSource().sendSuccess(() -> Component.translatable("hardscrabble.command.dev." + name, n), true);
        return n;
    }

    static int stats(ServerPlayer p) {
        int xp = ProgressionService.xp(p), level = PlayerLevel.get(p);
        int toNext = level >= Formulas.MAX_LEVEL ? 0 : Formulas.xpForLevel(level + 1) - xp;
        say(p.createCommandSourceStack(), Component.translatable("hardscrabble.command.stats.level", level, xp, toNext));
        say(p.createCommandSourceStack(), Component.translatable("hardscrabble.command.stats.health", Formulas.maxHealth(level), Formulas.foodCap(level)));
        say(p.createCommandSourceStack(), Component.translatable("hardscrabble.command.stats.bonus",
                percent(Formulas.meleeFactor(level) - 1), percent(Formulas.levelSpeedBonus(level))));
        Path file = writeStats(p, level, xp, toNext);
        if (file != null) say(p.createCommandSourceStack(), Component.translatable("hardscrabble.command.stats.saved", "players/stats/" + file.getFileName()));
        return level;
    }

    /**
     * Changelog l.594 (A-5): /stats also writes the character's statistics to {@code players/stats/<player>.txt} in the world
     * folder, next to the vanilla statistics.
     * Plain English, like the server log: a dedicated server has no mod translations.
     */
    static @Nullable Path writeStats(ServerPlayer p, int level, int xp, int toNext) {
        Metabolism m = MetabolismService.get(p);
        Curse curse = Curses.known(p);
        String text = String.join(System.lineSeparator(),
                "Player: " + p.getGameProfile().name(),
                "World day: " + MoonCalendar.day(p.level().getOverworldClockTime()),
                "Level: " + level + " (" + xp + " XP, " + toNext + " to the next level)",
                "Max health: " + Formulas.maxHealth(level) + ", food cap: " + Formulas.foodCap(level),
                "Melee damage: " + percent(Formulas.meleeFactor(level) - 1) + ", mining and crafting speed: " + percent(Formulas.levelSpeedBonus(level)),
                "Satiety: " + m.satiety + ", nutrition: " + m.nutrition + ", protein: " + m.protein + ", phytonutrients: " + m.phyto
                        + ", insulin resistance: " + m.insulin,
                "Curse: " + (curse == null ? "none known" : curse.id()), "");
        Path file = p.level().getServer().getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(p.getGameProfile().name() + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
            return file;
        } catch (IOException e) {
            MiteMod.LOGGER.warn("Could not write {}", file, e);
            return null;
        }
    }

    /** Locale-free (Q-6): the client reads the same digits whatever the server's locale. */
    private static String percent(float f) {
        return String.format(Locale.ROOT, "%+.1f%%", f * 100);
    }

    /** Raw server counters (EF-REG-04 CA-1): food, nutrients, insulin resistance and the curse once known. */
    static int state(ServerPlayer p) {
        stateLines(p).forEach(line -> say(p.createCommandSourceStack(), line));
        return MetabolismService.get(p).nutrition;
    }

    public static List<Component> stateLines(ServerPlayer p) {
        Metabolism m = MetabolismService.get(p);
        int cap = MetabolismService.cap(p);
        List<Component> lines = new ArrayList<>(List.of(
                Component.translatable("hardscrabble.command.state.food", m.satiety, cap, m.nutrition, cap),
                Component.translatable("hardscrabble.command.state.nutrients", m.protein, m.phyto, Formulas.NUTRIENT_MAX),
                Component.translatable("hardscrabble.command.state.insulin", m.insulin, Formulas.INSULIN_MAX, m.insulinLevel)));
        if (m.malnourished()) lines.add(Component.translatable("hardscrabble.command.state.malnourished"));
        Curse curse = Curses.known(p);
        if (curse != null) lines.add(Component.translatable("hardscrabble.curse.hud", Component.translatable(curse.langKey())));
        lines.add(Component.translatable("hardscrabble.command.state.rules", onOff(Rules.strict(p)), onOff(MiteConfig.LOCK_DIFFICULTY.getAsBoolean())));
        // The world day, also read from the Underworld, the Nether and the End (no clock of their own: day 1 there).
        long day = MoonCalendar.day(p.level().getOverworldClockTime());
        lines.add(Component.translatable("hardscrabble.command.state.moon", day,
                Component.translatable("moon.hardscrabble." + Lunar.moon(day).name().toLowerCase(Locale.ROOT))));
        return lines;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    /** Fills satiety, nutrition, protein and phytonutrients. */
    public static int nourish(ServerPlayer p) {
        Metabolism m = MetabolismService.get(p);
        m.satiety = m.nutrition = MetabolismService.cap(p);
        m.protein = m.phyto = Formulas.NUTRIENT_MAX;
        m.hunger = 0;
        m.syncedKey = -1; // resync on the next metabolism tick
        return 1;
    }

    public static int malnourish(ServerPlayer p) {
        Metabolism m = MetabolismService.get(p);
        m.protein = m.phyto = 0;
        m.syncedKey = -1;
        return 1;
    }

    /** Kills every loaded hostile mob of the player's dimension. */
    static int killMobs(ServerPlayer p) {
        ServerLevel level = p.level();
        List<? extends Mob> mobs = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m instanceof Enemy && m.isAlive());
        mobs.forEach(m -> m.kill(level));
        return mobs.size();
    }

    /** Chunks around the player that /mitedev dungeon searches, among those already loaded (the largest view distance). */
    static final int DUNGEON_RADIUS = 32;

    /** The nearest spawner (in the Underworld, a dungeon's Longdead one) with a click that fills in a /tp onto it. */
    static int dungeon(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        BlockPos spawner = nearestSpawner(p.level(), p.blockPosition(), DUNGEON_RADIUS);
        if (spawner == null) {
            source.sendFailure(Component.translatable("hardscrabble.command.dev.dungeon.none", DUNGEON_RADIUS));
            return 0;
        }
        BlockPos top = spawner.above();
        Component coordinates = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", top.getX(), top.getY(), top.getZ()))
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + top.getX() + " " + top.getY() + " " + top.getZ()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.coordinates.tooltip"))));
        int distance = Mth.floor(Math.sqrt(p.blockPosition().distSqr(spawner)));
        source.sendSuccess(() -> Component.translatable("hardscrabble.command.dev.dungeon", coordinates, distance), false);
        return distance;
    }

    /** Only chunks already loaded are read: nothing is generated or read from disk. */
    static @Nullable BlockPos nearestSpawner(ServerLevel level, BlockPos from, int radius) {
        BlockPos best = null;
        int cx = SectionPos.blockToSectionCoord(from.getX()), cz = SectionPos.blockToSectionCoord(from.getZ());
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values())
                    if (be instanceof SpawnerBlockEntity && (best == null || be.getBlockPos().distSqr(from) < best.distSqr(from))) best = be.getBlockPos();
            }
        }
        return best;
    }

    /** Grows to maturity every crop, sapling and other growing plant within 8 blocks (grass-like spreaders excluded). */
    static int grow(ServerPlayer p) {
        ServerLevel level = p.level();
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-8, -4, -8), p.blockPosition().offset(8, 4, 8))) {
            boolean any = false;
            for (int i = 0; i < 32; i++) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof BonemealableBlock b) || b.getType() != BonemealableBlock.Type.GROWER
                        || !b.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)) break;
                b.performBonemeal(level, level.getRandom(), pos, state, BonemealSource.INTERACTION);
                any = true;
            }
            if (any) grown++;
        }
        return grown;
    }
}
