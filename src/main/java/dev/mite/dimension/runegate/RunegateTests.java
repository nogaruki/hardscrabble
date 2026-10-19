package dev.mite.dimension.runegate;

import dev.mite.MiteMod;
import dev.mite.gametest.MiteGameTests;
import dev.mite.player.ProgressionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** GameTests for EF-DIM-06 (runestones, runegate routing and teleport rules) and the EF-CRA-06 runestone recipes. */
public final class RunegateTests {
    private RunegateTests() {}

    private static final BlockPos PORTAL = new BlockPos(2, 2, 3);

    /** A 2x3 portal on the X axis at z = 3, obsidian frame from y 0 to 4, corners from {@code corners}. */
    private static void gate(GameTestHelper h, Block bottomLeft, Block others) {
        for (int x = 1; x <= 4; x++) for (int y = 0; y <= 4; y++) {
            boolean corner = (x == 1 || x == 4) && (y == 0 || y == 4), inside = x > 1 && x < 4 && y > 0 && y < 4;
            BlockState state = corner ? (x == 1 && y == 0 ? bottomLeft : others).defaultBlockState()
                    : inside ? Blocks.NETHER_PORTAL.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, 3)), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): runegate_mithril_range is the radius a mithril runegate sends to.
        MiteGameTests.add("config_runegate_mithril_range", 20, h -> {
            ServerPlayer player = dev.mite.metabolism.MetabolismTests.player(h);
            Runegate.Gate gate = new Runegate.Gate(0x1234, false);
            var transition = MiteGameTests.withConfig(dev.mite.config.MiteConfig.RUNEGATE_MITHRIL_RANGE, 100, () -> Runegate.teleport(h.getLevel(), player, gate));
            BlockPos expected = Runegate.destination(h.getLevel().getSeed(), gate, 100);
            h.assertTrue(transition != null && (int) Math.floor(transition.position().x) == expected.getX()
                    && (int) Math.floor(transition.position().z) == expected.getZ(), "destination with runegate_mithril_range=100");
            h.succeed();
        });

        // EF-CRA-06 CA-1: obsidian + 4 mithril or adamantium nuggets.
        MiteGameTests.add("runestone_recipes", 20, h -> {
            var recipes = h.getLevel().getServer().getRecipeManager();
            for (String id : new String[]{"mithril_runestone", "adamantium_runestone"})
                h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, MiteMod.id(id))).isPresent(), id + " recipe missing");
            h.succeed();
        });

        // EF-DIM-06: four runestone corners make a runegate; runes and metal pick a fixed destination in the allowed ring.
        MiteGameTests.add("runegate_routing", 20, h -> {
            gate(h, Runegate.MITHRIL_RUNESTONE.get(), Runegate.ADAMANTIUM_RUNESTONE.get());
            var level = h.getLevel();
            Runegate.Gate mixed = Runegate.find(level, h.absolutePos(PORTAL)).orElse(null);
            h.assertTrue(mixed != null && !mixed.adamantium() && mixed.runes() == 0, "mixed runegate found: " + mixed);
            h.getLevel().setBlock(h.absolutePos(new BlockPos(4, 4, 3)), Runegate.MITHRIL_RUNESTONE.get().defaultBlockState().setValue(RunestoneBlock.RUNE, 5), 18);
            h.assertValueEqual(Runegate.find(level, h.absolutePos(PORTAL)).orElseThrow().runes(), 5 << 12, "top-right rune");
            h.getLevel().setBlock(h.absolutePos(new BlockPos(4, 4, 3)), Blocks.OBSIDIAN.defaultBlockState(), 18);
            h.assertTrue(Runegate.find(level, h.absolutePos(PORTAL)).isEmpty(), "three runestones are not a runegate");

            int range = 5000;
            Runegate.Gate mithril = new Runegate.Gate(0x1234, false), adamantium = new Runegate.Gate(0x1234, true);
            for (int runes = 0; runes < 64; runes++) {
                double m = Math.sqrt(Runegate.destination(1L, new Runegate.Gate(runes, false), range).distSqr(BlockPos.ZERO));
                double a = Math.sqrt(Runegate.destination(1L, new Runegate.Gate(runes, true), range).distSqr(BlockPos.ZERO));
                h.assertTrue(m <= range + 1, "mithril within range: " + m);
                h.assertTrue(a >= Runegate.ADAMANTIUM_MIN_DISTANCE - 1 && a <= range * Runegate.ADAMANTIUM_FACTOR + 1, "adamantium ring: " + a);
            }
            h.assertValueEqual(Runegate.destination(7L, mithril, range), Runegate.destination(7L, mithril, range), "same runes, same place");
            h.assertFalse(Runegate.destination(7L, mithril, range).equals(Runegate.destination(7L, new Runegate.Gate(0x1235, false), range)), "other runes elsewhere");
            h.assertFalse(Runegate.destination(7L, mithril, range).equals(Runegate.destination(7L, adamantium, range)), "metal matters");
            h.succeed();
        });

        // EF-DIM-06 CA-1: a player carrying an item is not teleported; an empty-handed one goes to the destination and loses XP.
        MiteGameTests.add("runegate_teleport_rules", 200, h -> {
            gate(h, Runegate.ADAMANTIUM_RUNESTONE.get(), Runegate.ADAMANTIUM_RUNESTONE.get());
            var level = h.getLevel();
            var portal = (NetherPortalBlock) Blocks.NETHER_PORTAL;
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            player.getInventory().add(new ItemStack(Items.DIRT));
            h.assertTrue(portal.getPortalDestination(level, player, h.absolutePos(PORTAL)) == null, "carrying an item: no teleport");

            player.getInventory().clearContent();
            ProgressionService.setXp(player, 100);
            var gate = Runegate.at(level, h.absolutePos(PORTAL));
            h.assertTrue(gate != null && gate.adamantium(), "adamantium runegate");
            var transition = portal.getPortalDestination(level, player, h.absolutePos(PORTAL));
            BlockPos expected = level.getWorldBorder().clampToBounds(Runegate.destination(level.getSeed(), gate, Runegate.range()));
            h.assertTrue(transition != null && transition.newLevel() == level && (int) Math.floor(transition.position().x) == expected.getX()
                    && (int) Math.floor(transition.position().z) == expected.getZ(), "teleport to the routed destination");
            BlockPos landed = BlockPos.containing(transition.position());
            h.assertTrue(landed.getY() > level.getMinY() + 3 && !level.getBlockState(landed.below()).is(Blocks.BEDROCK), "landed on the surface: " + landed);
            transition.postTeleportTransition().onTransition(player);
            h.assertValueEqual(ProgressionService.xp(player), 0, "amnesia");
            var runegate = level.getServer().getAdvancements().get(dev.mite.MiteMod.id("runegate"));
            h.assertTrue(runegate != null && player.getAdvancements().getOrStartProgress(runegate).isDone(), "runegate achievement");
            h.succeed();
        });

        // Like vanilla portal exits, a runegate never sends the player beyond the world border (adamantium ring on a small world).
        MiteGameTests.add("runegate_world_border", 100, h -> {
            var level = h.getLevel();
            var border = level.getWorldBorder();
            double size = border.getSize();
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            try {
                border.setSize(2000); // restored in the same tick
                var transition = Runegate.teleport(level, player, new Runegate.Gate(0x1234, true));
                h.assertTrue(transition != null && border.isWithinBounds(BlockPos.containing(transition.position())), "runegate exit outside the border");
            } finally {
                border.setSize(size);
            }
            h.succeed();
        });

        // B1: the landing reads the heightmap of a chunk that is not loaded yet (flat test world: grass at minY + 3), never
        // stands on bedrock, and never goes below the surface: a bedrock surface gets a cobblestone platform, not the air pocket
        // (the test enclosure, a cave) under it.
        MiteGameTests.add("runegate_safe_landing", 100, h -> {
            var level = h.getLevel();
            int x = 1_000_008, z = -1_000_008;
            h.assertFalse(level.hasChunk(x >> 4, z >> 4), "destination chunk not loaded");
            BlockPos far = Runegate.landing(level, x, z);
            h.assertTrue(far.getY() > level.getMinY() + 3 && !level.getBlockState(far.below()).is(Blocks.BEDROCK)
                    && level.getBlockState(far).isAir() && level.getBlockState(far.above()).isAir(), "unloaded destination: " + far);

            BlockPos column = h.absolutePos(new BlockPos(3, 0, 3));
            int roof = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
            level.setBlockAndUpdate(column.atY(roof), Blocks.BEDROCK.defaultBlockState());
            BlockPos landed = Runegate.landing(level, column.getX(), column.getZ());
            h.assertValueEqual(landed, column.atY(roof + 1), "landing on the bedrock surface");
            h.assertTrue(level.getBlockState(landed.below()).is(Blocks.COBBLESTONE), "cobblestone platform over bedrock");
            h.succeed();
        });

        // Low leaves on the surface (bush, azalea): a platform on the surface, not a landing down in a cave (the test enclosure).
        MiteGameTests.add("runegate_landing_under_leaves", 20, h -> {
            var level = h.getLevel();
            BlockPos column = h.absolutePos(new BlockPos(3, 0, 3));
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            level.setBlockAndUpdate(column.atY(top), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            BlockPos landed = Runegate.landing(level, column.getX(), column.getZ());
            h.assertValueEqual(landed, column.atY(top), "landing on the surface");
            h.assertTrue(level.getBlockState(landed.below()).is(Blocks.COBBLESTONE) && level.getBlockState(landed).isAir(), "cobblestone platform");
            h.succeed();
        });

        // A water (or lava) surface: a cobblestone platform on the fluid (M-4: no free obsidian), not a cave under the sea floor.
        MiteGameTests.add("runegate_landing_on_fluid", 20, h -> {
            var level = h.getLevel();
            BlockPos column = h.absolutePos(new BlockPos(3, 0, 3));
            int roof = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
            for (int y = 1; y <= 3; y++) level.setBlock(column.atY(column.getY() + y), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(column.atY(roof), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS); // the surface of the column
            BlockPos landed = Runegate.landing(level, column.getX(), column.getZ());
            h.assertValueEqual(landed, column.atY(roof + 1), "landing on the water surface");
            h.assertTrue(level.getBlockState(landed.below()).is(Blocks.COBBLESTONE) && level.getBlockState(landed).isAir(), "cobblestone platform");
            h.succeed();
        });
    }
}
