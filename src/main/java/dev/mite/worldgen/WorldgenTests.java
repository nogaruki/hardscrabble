package dev.mite.worldgen;

import dev.mite.MiteMod;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.metallurgy.FurnaceHeat;
import dev.mite.metallurgy.Metallurgy;
import dev.mite.worldgen.WorldgenContent.Ore;
import dev.mite.worldgen.WorldgenData.Placement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.material.rule.ConditionRule;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.OreVeinRule;
import net.minecraft.world.level.levelgen.material.rule.SequenceRule;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.mite.treasure.TreasureContent;
import dev.mite.worldgen.structure.StructureLoot;
import net.neoforged.neoforge.registries.DeferredBlock;

/** GameTests for EF-MET-01 (ore drops, smelting) and EF-MON-02 (placed features, biome modifiers). */
public final class WorldgenTests {
    private WorldgenTests() {}

    private static final BlockPos OK = new BlockPos(1, 1, 1), LOWER = new BlockPos(5, 1, 5), SILK = new BlockPos(1, 1, 5);

    public static void register() {
        // EF-MET-01 CA-1 / EF-OUT-02: the right pickaxe gives the raw item, a lower tier nothing, silk touch the block.
        drops(WorldgenContent.SILVER_ORE, () -> Items.COPPER_PICKAXE, () -> Items.AIR);
        drops(WorldgenContent.MITHRIL_ORE, () -> Items.IRON_PICKAXE, () -> Items.COPPER_PICKAXE);
        drops(WorldgenContent.ADAMANTIUM_ORE, () -> BuiltInRegistries.ITEM.getValue(MiteMod.id("mithril_pickaxe")), () -> Items.IRON_PICKAXE);

        // EF-MET-07: ores need the heat of their raw item; mithril ore smelts in an obsidian furnace with lava, not with coal.
        MiteGameTests.add("worldgen_ore_smelting", 250, h -> {
            Map.of(WorldgenContent.SILVER_ORE, 2, WorldgenContent.MITHRIL_ORE, 3, WorldgenContent.ADAMANTIUM_ORE, 4).forEach((ore, heat) -> {
                h.assertValueEqual(FurnaceHeat.requiredHeat(new ItemStack(ore.stone().get())), heat, ore.stone().getId() + " heat");
                h.assertValueEqual(FurnaceHeat.requiredHeat(new ItemStack(ore.deepslate().get())), heat, ore.deepslate().getId() + " heat");
                h.assertValueEqual(FurnaceHeat.requiredHeat(new ItemStack(ore.raw())), heat, ore.raw() + " heat");
            });
            var coal = furnace(h, 1, WorldgenContent.MITHRIL_ORE.stone().get(), Items.COAL);
            var lava = furnace(h, 3, WorldgenContent.MITHRIL_ORE.deepslate().get(), Items.LAVA_BUCKET);
            h.runAfterDelay(230, () -> {
                h.assertTrue(coal.getItem(2).isEmpty(), "coal (heat 2) should not smelt mithril ore");
                h.assertTrue(lava.getItem(2).is(MaterialItems.MITHRIL_INGOT.get()), "lava (heat 3) should smelt deepslate mithril ore");
                h.succeed();
            });
        });

        // EF-MON-01/02: every placed feature exists and samples heights inside its converted range.
        MiteGameTests.add("worldgen_placed_features", 20, h -> {
            Map<String, int[]> ranges = Map.ofEntries(
                    Map.entry("ore_dirt", new int[] {0, 128}), Map.entry("ore_gravel", new int[] {-16, 128}),
                    Map.entry("ore_coal", new int[] {-32, 96}), Map.entry("ore_copper", new int[] {-64, 128}),
                    Map.entry("ore_copper_upper", new int[] {38, 128}), Map.entry("ore_silver", new int[] {-64, 96}),
                    Map.entry("ore_gold", new int[] {-64, 32}), Map.entry("ore_iron", new int[] {-64, 64}),
                    Map.entry("ore_mithril", new int[] {-64, 0}), Map.entry("ore_redstone", new int[] {-64, -16}),
                    Map.entry("ore_diamond", new int[] {-64, 0}), Map.entry("ore_lapis", new int[] {-48, 16}),
                    Map.entry("ore_infested", new int[] {-64, -16})); // ore_adamantium: bottom..top of the Underworld (M4)
            var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            var context = new PlacementContext(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), Optional.empty());
            RandomSource random = RandomSource.create(42);
            ranges.forEach((name, range) -> {
                PlacedFeature placed = registry.getValue(MiteMod.id(name));
                h.assertTrue(placed != null, "missing placed feature hardscrabble:" + name);
                var height = placed.placement().stream().filter(HeightRangePlacement.class::isInstance).findFirst().orElseThrow();
                int[] seen = {Integer.MAX_VALUE, Integer.MIN_VALUE};
                for (int i = 0; i < 4000; i++) height.modify(context, random, BlockPos.ZERO, p -> {
                    seen[0] = Math.min(seen[0], p.getY());
                    seen[1] = Math.max(seen[1], p.getY());
                });
                h.assertTrue(seen[0] >= range[0] && seen[1] <= range[1], name + " sampled " + seen[0] + ".." + seen[1] + " outside " + range[0] + ".." + range[1]);
                h.assertTrue(seen[1] - seen[0] >= (range[1] - range[0]) / 2, name + " sampled only " + seen[0] + ".." + seen[1]);
            });
            h.assertTrue(registry.getValue(WorldgenData.ADAMANTIUM.key()) != null, "missing placed feature hardscrabble:ore_adamantium");
            h.succeed();
        });

        // L-4 (EF-MON-02 CA-1): average veins per chunk of every Overworld ore placement, over 2000 simulated chunks (biome filter skipped).
        MiteGameTests.add("worldgen_ore_statistics", 40, h -> {
            var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            var context = new PlacementContext(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), Optional.empty());
            RandomSource random = RandomSource.create(7);
            for (Placement p : WorldgenData.OVERWORLD) {
                List<PlacementModifier> modifiers = registry.getValue(p.key()).placement();
                long veins = 0;
                for (int chunk = 0; chunk < 2000; chunk++) veins += positions(modifiers, 0, context, random, new BlockPos(chunk * 16, 0, 0));
                double average = veins / 2000.0;
                h.assertTrue(Math.abs(average - p.perChunk()) <= 0.1 * p.perChunk() + 0.05, p.name() + ": " + average + " veins per chunk, expected " + p.perChunk());
            }
            h.succeed();
        });

        // Changelog l.79 (R184): torches above the four treasure chests of a desert pyramid, in all four orientations (built far
        // from the test grid).
        MiteGameTests.add("worldgen_pyramid_torches", 100, h -> {
            ServerLevel level = h.getLevel();
            for (Direction orientation : Direction.Plane.HORIZONTAL) {
                BlockPos at = h.absolutePos(BlockPos.ZERO).offset(4000 + 32 * orientation.get2DDataValue(), 0, 0);
                var piece = new DesertPyramidPiece(RandomSource.create(1), at.getX(), at.getZ());
                piece.setOrientation(orientation);
                BoundingBox box = build(level, piece);
                int torches = 0;
                for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() - 16, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    BlockState state = level.getBlockState(p);
                    if (!state.is(Blocks.WALL_TORCH)) continue;
                    h.assertTrue(level.getBlockState(p.below()).is(Blocks.CHEST) && state.canSurvive(level, p), orientation + ": torch at " + p + " not on the wall above a chest");
                    torches++;
                }
                h.assertValueEqual(torches, 4, orientation + ": treasure room torches");
            }
            h.succeed();
        });

        // Changelog l.631 and l.817: a witch hut holds a chest with the MITE hut loot (potions, dandelions, alliums).
        MiteGameTests.add("worldgen_swamp_hut_chest", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(BlockPos.ZERO).offset(4100, 0, 0);
            BoundingBox box = build(level, new SwampHutPiece(RandomSource.create(1), at.getX(), at.getZ()));
            level.getEntitiesOfClass(Mob.class, AABB.of(box).inflate(4)).forEach(Mob::discard);
            List<BlockPos> chests = new java.util.ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
                if (level.getBlockEntity(p) instanceof ChestBlockEntity chest && StructureLoot.SWAMP_HUT.equals(chest.getLootTable())) chests.add(p.immutable());
            h.assertValueEqual(chests.size(), 1, "hut chests");
            boolean flower = false, potion = false;
            var table = level.getServer().reloadableRegistries().getLootTable(StructureLoot.SWAMP_HUT);
            for (int i = 0; i < 100; i++) for (ItemStack stack : table.getRandomItems(chestParams(h))) {
                flower |= stack.is(Items.DANDELION) || stack.is(Items.ALLIUM);
                potion |= stack.is(Items.POTION);
            }
            h.assertTrue(flower && potion, "hut loot flowers " + flower + ", potions " + potion);
            h.succeed();
        });

        // Changelog l.291 (R156, L-3): the village smith chests (the 1.6.4 strongbox) hold coins.
        MiteGameTests.add("worldgen_village_strongbox", 20, h -> {
            var table = h.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.VILLAGE_WEAPONSMITH);
            int coins = 0;
            for (int i = 0; i < 20; i++) for (ItemStack stack : table.getRandomItems(chestParams(h))) if (stack.getItem() instanceof TreasureContent.CoinItem) coins++;
            h.assertTrue(coins >= 10, "coins in 20 weaponsmith chests: " + coins);
            h.succeed();
        });

        // EF-MON-02 CA-2 and arbitrage n°1: MITE ores replace vanilla ones in the Overworld, no adamantium.
        MiteGameTests.add("worldgen_biome_ores", 20, h -> {
            var placed = h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            var settings = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value().getGenerationSettings();
            for (Placement p : WorldgenData.OVERWORLD) h.assertTrue(settings.hasFeature(placed.getValue(p.key())), p.name() + " missing from plains");
            h.assertFalse(settings.hasFeature(placed.getValue(WorldgenData.ADAMANTIUM.key())), "adamantium must not generate in the Overworld");
            for (String vanilla : new String[] {"ore_copper", "ore_iron_upper", "ore_coal_upper", "ore_diamond"}) {
                h.assertFalse(settings.hasFeature(placed.getValue(Identifier.withDefaultNamespace(vanilla))), "vanilla " + vanilla + " should be removed");
            }
            h.assertTrue(settings.hasFeature(placed.getValue(Identifier.withDefaultNamespace("ore_tuff"))), "vanilla stone variants stay");
            h.succeed();
        });

        // EF-MON-02 (M3): the Overworld noise settings hold no 1.18 ore vein rule.
        MiteGameTests.add("worldgen_no_ore_veins", 20, h -> {
            var settings = h.getLevel().registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            h.assertFalse(hasVein(settings.materialRule().value()), "overworld noise settings still have ore veins");
            h.succeed();
        });

        // EF-MET-01 (M4): copper ore gives 1 raw copper and Fortune III adds nothing to any metal ore.
        MiteGameTests.add("worldgen_ore_no_fortune", 20, h -> {
            ItemStack pickaxe = new ItemStack(BuiltInRegistries.ITEM.getValue(MiteMod.id("mithril_pickaxe")));
            pickaxe.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
            Map<Block, Item> ores = new java.util.HashMap<>(WorldgenData.VANILLA_ORES);
            WorldgenContent.ORES.forEach(o -> List.of(o.stone(), o.deepslate()).forEach(b -> ores.put(b.get(), o.raw().asItem())));
            ores.forEach((block, raw) -> {
                for (int i = 0; i < 20; i++) {
                    var drops = Block.getDrops(block.defaultBlockState(), h.getLevel(), h.absolutePos(OK), null, null, pickaxe);
                    h.assertTrue(drops.size() == 1 && drops.getFirst().is(raw) && drops.getFirst().getCount() == 1, block + " dropped " + drops);
                }
            });
            for (int i = 0; i < 20; i++) {
                int n = Block.getDrops(Blocks.NETHER_GOLD_ORE.defaultBlockState(), h.getLevel(), h.absolutePos(OK), null, null, pickaxe).getFirst().getCount();
                h.assertTrue(n >= 2 && n <= 6, "nether gold ore dropped " + n + " nuggets");
            }
            h.succeed();
        });
    }

    /** Positions a placed feature yields from {@code pos}, the biome filter left out (the test world is a superflat plains). */
    private static int positions(List<PlacementModifier> modifiers, int index, PlacementContext context, RandomSource random, BlockPos pos) {
        if (index == modifiers.size()) return 1;
        if (modifiers.get(index) instanceof BiomeFilter) return positions(modifiers, index + 1, context, random, pos);
        int[] count = {0};
        modifiers.get(index).modify(context, random, pos, p -> count[0] += positions(modifiers, index + 1, context, random, p));
        return count[0];
    }

    /** Generates the chunks of a structure piece (height range of the level) and runs its post-processing over them. */
    private static BoundingBox build(ServerLevel level, StructurePiece piece) {
        BoundingBox box = piece.getBoundingBox();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) level.getChunk(cx, cz);
        BoundingBox all = new BoundingBox(box.minX(), level.getMinY(), box.minZ(), box.maxX(), level.getMaxY(), box.maxZ());
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(2), all,
                new ChunkPos(box.getCenter().getX() >> 4, box.getCenter().getZ() >> 4), box.getCenter());
        return piece.getBoundingBox();
    }

    private static LootParams chestParams(GameTestHelper h) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
    }

    private static boolean hasVein(MaterialRule rule) {
        return switch (rule) {
            case OreVeinRule v -> true;
            case MaterialRule.HolderHolder holder -> hasVein(holder.holder().value());
            case SequenceRule s -> s.sequence().stream().anyMatch(WorldgenTests::hasVein);
            case ConditionRule c -> hasVein(c.thenRun());
            default -> false;
        };
    }

    private static void drops(Ore ore, Supplier<Item> tool, Supplier<Item> lower) {
        for (DeferredBlock<Block> deferred : List.of(ore.stone(), ore.deepslate())) {
            MiteGameTests.add("worldgen_drops_" + deferred.getId().getPath(), 20, h -> {
                Block block = deferred.get();
                ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
                breakWith(h, player, block, new ItemStack(tool.get()), OK);
                h.assertItemEntityPresent(ore.raw().asItem(), OK, 1.5);

                breakWith(h, player, block, new ItemStack(lower.get()), LOWER);
                h.assertItemEntityNotPresent(ore.raw().asItem(), LOWER, 1.5);
                h.assertItemEntityNotPresent(block.asItem(), LOWER, 1.5);

                ItemStack silk = new ItemStack(tool.get());
                silk.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
                breakWith(h, player, block, silk, SILK);
                h.assertItemEntityPresent(block.asItem(), SILK, 1.5);
                h.assertItemEntityNotPresent(ore.raw().asItem(), SILK, 1.5);
                h.succeed();
            });
        }
    }

    private static void breakWith(GameTestHelper h, ServerPlayer player, Block block, ItemStack tool, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        h.setBlock(pos, block);
        player.gameMode.destroyBlock(h.absolutePos(pos));
    }

    private static AbstractFurnaceBlockEntity furnace(GameTestHelper h, int x, Block input, Item fuel) {
        BlockPos pos = new BlockPos(x, 1, 3);
        h.setBlock(pos, Metallurgy.OBSIDIAN_FURNACE.get());
        var furnace = h.getBlockEntity(pos, AbstractFurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(input));
        furnace.setItem(1, new ItemStack(fuel));
        return furnace;
    }
}
