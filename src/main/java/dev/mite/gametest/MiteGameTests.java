package dev.mite.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import dev.mite.MiteMod;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.metallurgy.MetallurgyTests;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Code GameTests. Domains call {@link #add} from a static {@code register()} method invoked in {@link #register(IEventBus)}.
 * Every test runs on {@code hardscrabble:platform}: a 7x5x7 box with a stone floor at y=0.
 */
public final class MiteGameTests {
    private MiteGameTests() {}

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, MiteMod.MODID);
    private static final DeferredRegister<MapCodec<? extends TestEnvironmentDefinition<?>>> ENVIRONMENT_TYPES =
            DeferredRegister.create(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, MiteMod.MODID);
    private static final List<Entry> TESTS = new ArrayList<>();

    private record Entry(String name, int maxTicks, boolean rain, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {}

    /**
     * Creative mock player placed in the player list (vanilla makeMockServerPlayerInLevel, deprecated for removal in 26.3).
     * It leaves the list when the test passes or fails, also after a respawn or relog (same UUID), so no player leaks into
     * later tests (player loops, nearest player, loaded chunks).
     */
    public static ServerPlayer levelPlayer(GameTestHelper h) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        MinecraftServer server = h.getLevel().getServer();
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        Runnable remove = () -> {
            ServerPlayer current = server.getPlayerList().getPlayer(player.getUUID());
            if (current != null) server.getPlayerList().remove(current);
        };
        h.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                remove.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                remove.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
        return player;
    }

    /**
     * Runs {@code test} with a {@link #levelPlayer} (which leaves the player list when the test ends, passed or failed).
     */
    public static void withPlayer(GameTestHelper h, Consumer<ServerPlayer> test) {
        test.accept(levelPlayer(h));
    }

    /**
     * Runs {@code body} with {@code key} set to {@code value} and puts back the value found, also when the body throws.
     * Tests run in parallel: the body must not wait for a later tick.
     */
    public static <T, R> R withConfig(ModConfigSpec.ConfigValue<T> key, T value, Supplier<R> body) {
        T old = key.get();
        key.set(value);
        try {
            return body.get();
        } finally {
            key.set(old);
        }
    }

    /**
     * Fills the platform with {@code biome}. Level#getBiome reads a jittered 4-block cell (BiomeManager zoom: 2 blocks of offset,
     * then the cell or its +1 neighbour), so the fill reaches 2 blocks before and 8 after the platform's origin: every platform
     * block then reads {@code biome}, never the natural biome around (a desert has no rain, a snowy biome snows). Neighbouring
     * platforms start 7 blocks further, so their cells stay apart.
     */
    public static void fillBiome(GameTestHelper h, Holder<Biome> biome) {
        FillBiomeCommand.fill(h.getLevel(), h.absolutePos(new BlockPos(-2, -2, -2)), h.absolutePos(new BlockPos(8, 8, 8)), biome);
    }

    public static void fillBiome(GameTestHelper h, ResourceKey<Biome> biome) {
        fillBiome(h, h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome));
    }

    public static void add(String name, int maxTicks, Consumer<GameTestHelper> test) {
        TESTS.add(new Entry(name, maxTicks, false, FUNCTIONS.register(name, () -> test)));
    }

    /** A test run in its own batch under rain (environment {@code hardscrabble:rain}, sky access: no barrier roof), so the weather does not leak into other tests. */
    public static void addInRain(String name, int maxTicks, Consumer<GameTestHelper> test) {
        TESTS.add(new Entry(name, maxTicks, true, FUNCTIONS.register(name, () -> test)));
    }

    /**
     * The {@code hardscrabble:rain} environment: rain at full level for the whole batch, and dry weather at level 0 after it. Vanilla's
     * weather environment only sets the rain flag, so the rain level ramps 0.01 per tick: tests starting with the batch saw dry
     * weather, the rain lingered about 100 ticks into the next batch (sky light, precipitation), and a following rain batch
     * recorded the lingering rain as the weather to restore, leaving every later batch in the rain.
     */
    record SteadyRain() implements TestEnvironmentDefinition<Unit> {
        static final MapCodec<SteadyRain> CODEC = MapCodec.unit(SteadyRain::new);

        @Override
        public Unit setup(ServerLevel level) {
            level.getServer().setWeatherParameters(0, 100_000, true, false);
            level.setRainLevel(1);
            level.setThunderLevel(0);
            return Unit.INSTANCE;
        }

        @Override
        public void teardown(ServerLevel level, Unit saveData) {
            level.resetWeatherCycle();
            level.setRainLevel(0);
            level.setThunderLevel(0);
        }

        @Override
        public MapCodec<SteadyRain> codec() {
            return CODEC;
        }
    }

    public static void register(IEventBus modBus) {
        add("smoke", 20, helper -> {
            helper.setBlock(1, 1, 1, Blocks.DIRT);
            helper.succeedWhenBlockPresent(Blocks.DIRT, 1, 1, 1);
        });
        // Domain tests register here, one line per domain.
        MetabolismTests.register();
        dev.mite.crafting.CraftingTests.register();
        dev.mite.crafting.CraftingTests2.register();
        dev.mite.harvest.HarvestTests.register();
        MetallurgyTests.register();
        dev.mite.player.XpTests.register();
        dev.mite.combat.CombatTests.register();
        dev.mite.combat.CombatTests2.register();
        dev.mite.worldgen.WorldgenTests.register();
        dev.mite.enchanting.EnchantingTests.register();
        dev.mite.enchanting.EnchantingTests2.register();
        dev.mite.lunar.LunarTests.register();
        dev.mite.entity.ai.AiTests.register();
        dev.mite.entity.mob.MobTests.register();
        dev.mite.entity.mob.HunterMobTests.register();
        dev.mite.entity.mob.EliteMobTests.register();
        dev.mite.entity.mob.DeepMobTests.register();
        dev.mite.entity.mob.MobBehaviourTests.register();
        dev.mite.entity.ai.RangedAiTests.register();
        dev.mite.underworld.UnderworldTests.register();
        dev.mite.dimension.DimensionTests.register();
        dev.mite.rules.RulesTests.register();
        dev.mite.harvest.QualityTests.register();
        dev.mite.farming.livestock.LivestockTests.register();
        dev.mite.worldgen.village.WorldTests.register();
        dev.mite.dimension.runegate.RunegateTests.register();
        dev.mite.farming.FarmingTests.register();
        dev.mite.curse.CurseTests.register();
        dev.mite.progression.ProgressionTests.register();
        dev.mite.entity.ai.MobRulesTests.register();
        dev.mite.entity.ai.VanillaMobBehaviourTests.register();
        dev.mite.equipment.EquipmentTests.register();
        dev.mite.treasure.TreasureTests.register();
        dev.mite.environment.EnvironmentTests.register();
        dev.mite.farming.SoilTests.register();
        dev.mite.trading.TradingTests.register();
        dev.mite.rules.ShortcutTests.register();
        dev.mite.attach.SaveTests.register();
        AuditTests.register();
        FUNCTIONS.register(modBus);
        ENVIRONMENT_TYPES.register("steady_rain", () -> SteadyRain.CODEC);
        ENVIRONMENT_TYPES.register(modBus);
        modBus.addListener(MiteGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(MiteMod.id("default"));
        Holder<TestEnvironmentDefinition<?>> rain = event.registerEnvironment(MiteMod.id("rain"), new SteadyRain());
        for (Entry t : TESTS) {
            event.registerTest(MiteMod.id(t.name()), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, t.function().getId()),
                    new TestData<>(t.rain() ? rain : env, Level.OVERWORLD, MiteMod.id("platform"), t.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, t.rain(), 1)));
        }
    }
}
