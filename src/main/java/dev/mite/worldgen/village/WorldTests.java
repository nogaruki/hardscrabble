package dev.mite.worldgen.village;

import dev.mite.MiteMod;
import dev.mite.gametest.MiteGameTests;
import dev.mite.material.MaterialItems;
import dev.mite.worldgen.WorldgenContent;
import dev.mite.worldgen.WorldgenData;
import dev.mite.worldgen.structure.MiteStructures.GatedSpread;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** GameTests for L01 (deep lava), L06 (EF-MON-05), L07 (EF-MON-04), L09 (EF-MON-07) and M05 (EF-DIM-05). */
public final class WorldTests {
    private WorldTests() {}

    private static ItemStack mite(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(MiteMod.id(id)));
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): structures.village_lock_min_days keeps villages closed before that day, even once unlocked.
        MiteGameTests.add("config_village_lock_min_days", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            VillageLock.Progress progress = VillageLock.progress(server);
            boolean before = progress.villagesUnlocked;
            progress.villagesUnlocked = true;
            boolean early = MiteGameTests.withConfig(dev.mite.config.MiteConfig.VILLAGE_LOCK_MIN_DAYS, 100000, () -> {
                VillageLock.refresh(server);
                return VillageLock.open();
            });
            VillageLock.refresh(server);
            boolean open = VillageLock.open();
            progress.villagesUnlocked = before;
            VillageLock.refresh(server);
            h.assertFalse(early, "villages open before village_lock_min_days");
            h.assertTrue(open, "villages closed once unlocked with village_lock_min_days=0");
            h.succeed();
        });

        // EF-MON-05: the village set is locked until an iron (or better) pickaxe or war hammer is crafted (R165); /locate and new
        // chunks see no village before, and /locate generates no chunk (dom-L-01).
        MiteGameTests.add("village_lock_unlocks_on_iron_pickaxe", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            VillageLock.Progress progress = VillageLock.progress(server);
            boolean before = progress.villagesUnlocked;
            long dayBefore = progress.firstVillageUnlockDay;
            ServerPlayer player = MiteGameTests.levelPlayer(h);
            // Shared state (SavedData, cached lock, player list) is restored even when an assertion fails.
            try {
                progress.villagesUnlocked = false;
                VillageLock.refresh(server);
                var set = h.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
                        .getValue(Identifier.withDefaultNamespace("villages"));
                h.assertTrue(set != null && set.placement() instanceof GatedSpread g && g.villageLock(), "villages use hardscrabble:gated_spread with village_lock");
                var placement = (GatedSpread) set.placement();
                var state = h.getLevel().getChunkSource().getGeneratorState();
                ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), 0, 0);
                h.assertFalse(placement.isStructureChunk(state, chunk.x(), chunk.z()), "locked: no village chunk");
                // dom-L-01: /locate asks StructureCheck, which must answer "no start" without loading the chunk to STRUCTURE_STARTS.
                var plains = h.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(BuiltinStructures.VILLAGE_PLAINS);
                for (int sector = 0; sector < 8; sector++) {
                    ChunkPos far = placement.getPotentialStructureChunk(state.getLevelSeed(), 3000 + sector * placement.spacing(), 3000);
                    h.assertValueEqual(h.getLevel().structureManager().checkStructurePresence(far, plains, placement, false), StructureCheckResult.START_NOT_PRESENT,
                            "locked: locate check of chunk " + far);
                    h.assertTrue(h.getLevel().getChunkSource().getChunk(far.x(), far.z(), ChunkStatus.STRUCTURE_STARTS, false) == null, "locked: chunk " + far + " generated");
                }

                h.assertFalse(VillageLock.unlocks(new ItemStack(Items.COPPER_PICKAXE)), "a copper pickaxe does not unlock");
                h.assertFalse(VillageLock.unlocks(mite("copper_war_hammer")), "a copper war hammer unlocks");
                h.assertTrue(VillageLock.unlocks(mite("iron_war_hammer")), "an iron war hammer does not unlock");
                h.assertTrue(VillageLock.unlocks(mite("mithril_pickaxe")), "a mithril pickaxe does not unlock");
                h.assertTrue(VillageLock.unlocks(mite("adamantium_war_hammer")), "an adamantium war hammer does not unlock");
                NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.COPPER_PICKAXE), new SimpleContainer(9)));
                h.assertFalse(VillageLock.open(), "still locked after a copper pickaxe");
                NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.IRON_PICKAXE), new SimpleContainer(9)));
                h.assertTrue(progress.villagesUnlocked && VillageLock.open(), "unlocked after an iron pickaxe");
                h.assertTrue(placement.isStructureChunk(state, chunk.x(), chunk.z()), "unlocked: village chunk");
            } finally {
                progress.villagesUnlocked = before;
                progress.firstVillageUnlockDay = dayBefore;
                VillageLock.refresh(server);
            }
            h.succeed();
        });

        // A-4: structures.village_lock=false opens villages whatever the progress; the run's own setting is restored.
        MiteGameTests.add("village_lock_config", 20, h -> {
            MinecraftServer server = h.getLevel().getServer();
            boolean open = MiteGameTests.withConfig(dev.mite.config.MiteConfig.VILLAGE_LOCK, false, () -> {
                VillageLock.refresh(server);
                return VillageLock.open() && !VillageLock.lockEnabled();
            });
            boolean enabled = MiteGameTests.withConfig(dev.mite.config.MiteConfig.VILLAGE_LOCK, true, VillageLock::lockEnabled);
            VillageLock.refresh(server);
            h.assertTrue(open, "villages locked with village_lock=false");
            h.assertTrue(enabled, "lock off with village_lock=true");
            h.succeed();
        });

        // EF-MON-05 / R170: every village (and zombie village) villager pool is empty.
        MiteGameTests.add("village_no_villagers", 20, h -> {
            var pools = h.getLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
            for (String biome : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
                for (String path : List.of("village/" + biome + "/villagers", "village/" + biome + "/zombie/villagers")) {
                    var pool = pools.getValue(Identifier.withDefaultNamespace(path));
                    h.assertTrue(pool != null && pool.getTemplates().isEmpty(), path + " should hold no villager");
                }
            }
            h.succeed();
        });

        // EF-MON-04: a suspended dirt block falls when walked on; grass does not.
        MiteGameTests.add("dirt_falls_when_walked_on", 100, h -> {
            h.setBlock(3, 3, 3, Blocks.DIRT);
            h.setBlock(1, 3, 1, Blocks.GRASS_BLOCK);
            h.assertBlockPresent(Blocks.DIRT, 3, 3, 3);
            h.spawn(EntityTypes.PIG, 3, 4, 3);
            h.spawn(EntityTypes.PIG, 1, 4, 1);
            h.succeedWhen(() -> {
                h.assertBlockPresent(Blocks.DIRT, 3, 1, 3);
                h.assertBlockPresent(Blocks.AIR, 3, 3, 3);
                h.assertBlockPresent(Blocks.GRASS_BLOCK, 1, 3, 1);
            });
        });

        // EF-MON-04 CA-1: removing the block under dirt makes it fall.
        MiteGameTests.add("dirt_falls_when_dug_under", 60, h -> {
            h.setBlock(5, 1, 5, Blocks.STONE);
            h.setBlock(5, 2, 5, Blocks.DIRT);
            h.setBlock(5, 1, 5, Blocks.AIR);
            h.succeedWhen(() -> {
                h.assertBlockPresent(Blocks.DIRT, 5, 1, 5);
                h.assertBlockPresent(Blocks.AIR, 5, 2, 5);
            });
        });

        // EF-MON-07: dungeon chests hold MITE nuggets, never golden apples or ingots.
        MiteGameTests.add("dungeon_loot_mite", 20, h -> {
            var table = h.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
            LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                    .create(LootContextParamSets.CHEST);
            boolean nugget = false;
            int nuggetStacks = 0, coinStacks = 0;
            RandomSource random = RandomSource.create(1); // seeded: the coin/nugget ratio check is statistical
            for (int i = 0; i < 50; i++) {
                for (ItemStack stack : table.getRandomItems(params, random)) {
                    h.assertFalse(stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT),
                            stack + " in a MITE dungeon");
                    nugget |= stack.is(Items.COPPER_NUGGET) || stack.is(MaterialItems.SILVER_NUGGET.get());
                    if (stack.is(Items.COPPER_NUGGET) || stack.is(MaterialItems.SILVER_NUGGET.get()) || stack.is(Items.IRON_NUGGET) || stack.is(Items.GOLD_NUGGET)) nuggetStacks++;
                    if (stack.getItem() instanceof dev.mite.treasure.TreasureContent.CoinItem) coinStacks++;
                }
            }
            h.assertTrue(nugget, "no MITE nugget in 50 dungeon chests");
            // L-6 (changelog l.373, R147): coins instead of nuggets.
            h.assertTrue(coinStacks > 2 * nuggetStacks, "coin stacks " + coinStacks + " vs nugget stacks " + nuggetStacks);
            h.succeed();
        });

        // EF-DIM-05: the Nether uses the MITE material rule (Core layer at the bottom); L01: extra deep lava springs.
        MiteGameTests.add("nether_core_and_deep_lava", 20, h -> {
            var access = h.getLevel().registryAccess();
            var nether = access.lookupOrThrow(Registries.NOISE_SETTINGS).getValue(NoiseGeneratorSettings.NETHER);
            h.assertTrue(nether != null && nether.materialRule().is(WorldgenData.NETHER_RULE), "minecraft:nether should use hardscrabble:nether");
            h.assertTrue(access.lookupOrThrow(Registries.MATERIAL_RULE).get(WorldgenData.NETHER_RULE).isPresent(), "hardscrabble:nether material rule missing");
            h.assertTrue(WorldgenContent.CORE.get().defaultBlockState().getDestroySpeed(h.getLevel(), BlockPos.ZERO) < 0, "Core is unbreakable");
            var placed = access.lookupOrThrow(Registries.PLACED_FEATURE);
            h.assertTrue(access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value().getGenerationSettings()
                    .hasFeature(placed.getValue(WorldgenData.DEEP_LAVA)), "deep lava springs missing from plains");
            h.succeed();
        });
    }
}
