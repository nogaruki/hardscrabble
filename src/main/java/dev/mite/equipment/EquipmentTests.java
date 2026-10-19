package dev.mite.equipment;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.equipment.EquipmentContent.BucketSet;
import dev.mite.gametest.MiteGameTests;
import dev.mite.harvest.ToolStats;
import dev.mite.metabolism.MetabolismContent;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.metabolism.Nutrition;
import dev.mite.metallurgy.FurnaceHeat;
import dev.mite.player.ProgressionService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jspecify.annotations.Nullable;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** GameTests for D15, D16, D17, I14, N06, H11 and B10. */
public final class EquipmentTests {
    private EquipmentTests() {}

    private static Item mite(String name) {
        return BuiltInRegistries.ITEM.getValue(MiteMod.id(name));
    }

    /** A survival player standing at (x, 1, z) of the platform, looking straight down, holding {@code item}. */
    private static ServerPlayer looker(GameTestHelper h, double x, double z, Item item) {
        ServerPlayer p = MetabolismTests.player(h);
        p.snapTo(h.absoluteVec(new Vec3(x, 1, z)), 0, 90);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        return p;
    }

    /**
     * Runs {@code body}, then puts back the biome found at the platform's centre, even when an assertion fails: FillBiome writes
     * the chunk (in 4-block cells, past the platform) and later batches reuse the same positions.
     */
    private static void inBiomes(GameTestHelper h, Runnable body) {
        Holder<Biome> original = h.getLevel().getBiome(h.absolutePos(new BlockPos(3, 1, 3)));
        try {
            body.run();
        } finally {
            MiteGameTests.fillBiome(h, original);
        }
    }

    /** Worms dropped by one grass block broken by {@code breaker}. */
    private static int worms(GameTestHelper h, BlockPos grass, @Nullable ServerPlayer breaker) {
        List<ItemEntity> drops = new ArrayList<>();
        Fishing.onBlockDrops(new BlockDropsEvent(h.getLevel(), grass, Blocks.GRASS_BLOCK.defaultBlockState(), null, drops, breaker, ItemStack.EMPTY));
        return (int) drops.stream().filter(d -> d.getItem().is(EquipmentContent.WORM.get())).count();
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): fluid_source_xp_cost=0 makes every pour a free source; another cost is what a paid pour takes.
        MiteGameTests.add("config_fluid_source_xp_cost", 20, h -> {
            ServerPlayer free = looker(h, 1.5, 1.5, Items.WATER_BUCKET);
            ProgressionService.setXp(free, 150);
            MiteGameTests.withConfig(dev.mite.config.MiteConfig.FLUID_SOURCE_XP_COST, 0, () -> Buckets.use(free, InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(1, 1, 1))).isSource(), "free pour not a source with fluid_source_xp_cost=0");
            h.assertValueEqual(ProgressionService.xp(free), 150, "XP after a free source");
            ServerPlayer payer = looker(h, 5.5, 5.5, Items.WATER_BUCKET);
            ProgressionService.setXp(payer, 150);
            payer.setShiftKeyDown(true);
            MiteGameTests.withConfig(dev.mite.config.MiteConfig.FLUID_SOURCE_XP_COST, 30, () -> Buckets.use(payer, InteractionHand.MAIN_HAND));
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(5, 1, 5))).isSource(), "paid pour not a source");
            h.assertValueEqual(ProgressionService.xp(payer), 120, "XP after a source at fluid_source_xp_cost=30");
            h.succeed();
        });

        // D15: shears in every metal are MITE tools of type SHEARS and shear sheep.
        MiteGameTests.add("equipment_shears", 20, h -> {
            h.assertTrue(ToolStats.of(new ItemStack(Items.SHEARS)).orElseThrow().material() == Material.IRON, "vanilla shears are iron");
            ItemStack shears = new ItemStack(mite("copper_shears"));
            var info = ToolStats.of(shears).orElseThrow();
            h.assertTrue(info.type() == ToolType.SHEARS && info.material() == Material.COPPER, "copper shears stats " + info);
            ServerPlayer p = MetabolismTests.player(h);
            Sheep sheep = h.spawn(EntityTypes.SHEEP, 3, 1, 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, shears);
            p.interactOn(sheep, InteractionHand.MAIN_HAND, sheep.position());
            h.assertTrue(sheep.isSheared(), "copper shears shear a sheep");
            h.succeed();
        });

        // D16 / EF-OUT-13 CA-1: copper buckets may break on lava, adamantium never; metal buckets keep their metal.
        MiteGameTests.add("equipment_bucket_lava", 20, h -> {
            h.assertTrue(Buckets.lavaBreakChance(Material.COPPER) > 0, "copper can break");
            h.assertValueEqual(Buckets.lavaBreakChance(Material.ADAMANTIUM), 0f, "adamantium break chance");
            BucketSet adamantium = EquipmentContent.BUCKETS.get(Material.ADAMANTIUM);
            ServerPlayer p = looker(h, 3.5, 3.5, adamantium.empty().get());
            p.snapTo(h.absoluteVec(new Vec3(3.5, 2, 3.5)), 0, 90);
            for (int i = 0; i < 50; i++) {
                h.setBlock(3, 1, 3, Blocks.LAVA);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(adamantium.empty().get()));
                Buckets.use(p, InteractionHand.MAIN_HAND);
                h.assertTrue(p.getMainHandItem().is(adamantium.lava().get()), "adamantium lava bucket, got " + p.getMainHandItem());
            }
            // A copper bucket breaks 1 time in 16: in 300 scoops it breaks (miss odds ~4e-9), the bucket and its lava lost.
            Item copper = EquipmentContent.BUCKETS.get(Material.COPPER).empty().get();
            ServerPlayer c = looker(h, 1.5, 1.5, copper);
            c.snapTo(h.absoluteVec(new Vec3(1.5, 2, 1.5)), 0, 90);
            boolean broke = false;
            for (int i = 0; i < 300 && !broke; i++) {
                h.setBlock(1, 1, 1, Blocks.LAVA);
                c.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(copper));
                Buckets.use(c, InteractionHand.MAIN_HAND);
                broke = c.getMainHandItem().isEmpty();
            }
            h.assertTrue(broke, "a copper bucket breaks on lava");
            h.assertTrue(h.getBlockState(new BlockPos(1, 1, 1)).isAir(), "the lava is taken even when the bucket breaks");
            h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(EquipmentContent.BUCKETS.get(Material.COPPER).lava().get()).getData(FurnaceHeat.FUEL_HEAT), 3,
                    "copper lava bucket fuel heat");
            h.succeed();
        });

        // D16: a copper bucket scoops water, pours it, milks a cow; D-2 (R134 l.500): carried lava buckets cool into buckets of stone when submerged.
        MiteGameTests.add("equipment_metal_buckets", 20, h -> {
            BucketSet copper = EquipmentContent.BUCKETS.get(Material.COPPER);
            h.setBlock(3, 1, 3, Blocks.WATER);
            ServerPlayer p = looker(h, 3.5, 3.5, copper.empty().get());
            p.snapTo(h.absoluteVec(new Vec3(3.5, 2, 3.5)), 0, 90);
            Buckets.use(p, InteractionHand.MAIN_HAND);
            h.assertTrue(p.getMainHandItem().is(copper.water().get()), "copper water bucket, got " + p.getMainHandItem());
            Buckets.use(p, InteractionHand.MAIN_HAND);
            h.assertTrue(p.getMainHandItem().is(copper.empty().get()), "emptied copper bucket, got " + p.getMainHandItem());
            Cow cow = h.spawn(EntityTypes.COW, 1, 1, 1);
            p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
            h.assertTrue(p.getMainHandItem().is(copper.milk().get()), "copper milk bucket, got " + p.getMainHandItem());
            ServerPlayer swimmer = looker(h, 5.5, 5.5, copper.lava().get());
            swimmer.getInventory().setItem(9, new ItemStack(Items.LAVA_BUCKET));
            Buckets.onSubmerged(swimmer); // dry: nothing happens
            h.assertTrue(swimmer.getMainHandItem().is(copper.lava().get()), "lava bucket cooled out of the water");
            Buckets.coolLava(swimmer);
            h.assertTrue(swimmer.getMainHandItem().is(copper.stone().get()), "bucket of stone, got " + swimmer.getMainHandItem());
            h.assertTrue(swimmer.getInventory().getItem(9).is(EquipmentContent.BUCKETS.get(Material.IRON).stone().get()), "iron bucket of stone");
            h.succeed();
        });

        // B10 / EF-JOU-09: a free pour flows away; sneaking with 100 XP places a source and costs 100 XP.
        MiteGameTests.add("equipment_paid_fluid_source", 20, h -> {
            ServerPlayer p = looker(h, 1.5, 1.5, Items.WATER_BUCKET);
            Buckets.use(p, InteractionHand.MAIN_HAND);
            FluidState free = h.getLevel().getFluidState(h.absolutePos(new BlockPos(1, 1, 1)));
            h.assertTrue(free.isEmpty() == false && !free.isSource(), "free pour is flowing water, got " + free);
            h.assertTrue(p.getMainHandItem().is(Items.BUCKET), "bucket emptied");
            ServerPlayer payer = looker(h, 5.5, 5.5, Items.WATER_BUCKET);
            ProgressionService.setXp(payer, 150);
            payer.setShiftKeyDown(true);
            Buckets.use(payer, InteractionHand.MAIN_HAND);
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(5, 1, 5))).isSource(), "paid pour is a source");
            h.assertValueEqual(ProgressionService.xp(payer), 50, "XP after paying");
            payer.snapTo(h.absoluteVec(new Vec3(3.5, 1, 5.5)), 0, 90);
            payer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
            Buckets.use(payer, InteractionHand.MAIN_HAND);
            h.assertTrue(!h.getLevel().getFluidState(h.absolutePos(new BlockPos(3, 1, 5))).isSource(), "not enough XP: flowing lava");
            h.assertValueEqual(ProgressionService.xp(payer), 50, "XP unchanged");
            h.succeed();
        });

        // EF-JOU-09: a free pour never makes a source: no waterlogging, fish buckets flow too, and a pond's source stays a source.
        MiteGameTests.add("equipment_free_pour_no_source", 20, h -> {
            // Aimed at the floor beside a fence post (thin outline): vanilla would waterlog the fence with a source.
            h.setBlock(3, 1, 3, Blocks.OAK_FENCE);
            ServerPlayer p = looker(h, 3.1, 3.5, Items.WATER_BUCKET);
            h.assertValueEqual(Buckets.use(p, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "free pour into a fence");
            h.assertFalse(h.getBlockState(new BlockPos(3, 1, 3)).getValue(BlockStateProperties.WATERLOGGED), "fence not waterlogged");
            h.assertTrue(p.getMainHandItem().is(Items.WATER_BUCKET), "bucket kept");
            ServerPlayer fisher = looker(h, 5.5, 5.5, Items.SALMON_BUCKET);
            Buckets.use(fisher, InteractionHand.MAIN_HAND);
            FluidState fish = h.getLevel().getFluidState(h.absolutePos(new BlockPos(5, 1, 5)));
            h.assertTrue(!fish.isEmpty() && !fish.isSource(), "salmon bucket pours flowing water, got " + fish);
            h.assertTrue(fisher.getMainHandItem().is(Items.BUCKET), "salmon bucket emptied");
            h.assertTrue(!h.getEntities(EntityTypes.SALMON).isEmpty(), "the salmon is released");
            h.setBlock(1, 1, 1, Blocks.WATER);
            ServerPlayer pond = looker(h, 1.5, 1.5, Items.WATER_BUCKET);
            Buckets.use(pond, InteractionHand.MAIN_HAND);
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(1, 1, 1))).isSource(), "the pond's source survives a free pour");
            h.succeed();
        });

        // EF-JOU-09: a dispenser empties a water bucket as flowing water and gets the empty bucket back.
        MiteGameTests.add("equipment_dispenser_pour", 40, h -> {
            BlockPos dispenser = new BlockPos(2, 1, 3);
            h.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            h.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(Items.WATER_BUCKET));
            h.setBlock(2, 2, 3, Blocks.REDSTONE_BLOCK);
            h.succeedWhen(() -> {
                FluidState poured = h.getLevel().getFluidState(h.absolutePos(new BlockPos(3, 1, 3)));
                h.assertTrue(!poured.isEmpty() && !poured.isSource(), "dispensed water is flowing, got " + poured);
                h.assertTrue(h.getBlockEntity(dispenser, DispenserBlockEntity.class).getItem(0).is(Items.BUCKET), "empty bucket back");
            });
        });

        // D17, changelog l.554 / l.212: the rod casts only from the ground (or a mount), never airborne; a hotbar worm is not consumed.
        MiteGameTests.add("equipment_fishing_cast", 20, h -> {
            Item rod = mite("copper_fishing_rod");
            h.assertValueEqual(new ItemStack(rod).getMaxDamage(), 256, "copper rod durability");
            ServerPlayer p = looker(h, 3.5, 3.5, rod);
            p.getInventory().setItem(3, new ItemStack(EquipmentContent.WORM.get()));
            h.assertTrue(Fishing.wormInHotbar(p), "worm in the hotbar");
            p.setOnGround(false);
            h.assertValueEqual(p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "airborne cast");
            h.assertTrue(p.fishing == null, "no hook while airborne");
            p.setOnGround(true);
            p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(p.fishing != null, "hook cast from the ground");
            h.assertTrue(p.getInventory().getItem(3).is(EquipmentContent.WORM.get()), "worm kept");
            p.getInventory().setItem(3, ItemStack.EMPTY);
            p.getInventory().setItem(20, new ItemStack(EquipmentContent.WORM.get()));
            h.assertFalse(Fishing.wormInHotbar(p), "worm in the main inventory does not count");
            h.succeed();
        });
        // Changelog l.554: no cast while swimming or standing in a liquid.
        MiteGameTests.add("equipment_fishing_cast_in_water", 20, h -> {
            ServerPlayer swimmer = looker(h, 1.5, 1.5, mite("copper_fishing_rod"));
            swimmer.setOnGround(true);
            swimmer.setSwimming(true);
            h.assertValueEqual(swimmer.getMainHandItem().use(h.getLevel(), swimmer, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "swimming cast");
            h.setBlock(4, 1, 4, Blocks.WATER);
            h.setBlock(4, 2, 4, Blocks.WATER);
            ServerPlayer wader = looker(h, 4.5, 4.5, mite("copper_fishing_rod"));
            wader.baseTick(); // the mock never ticks: update its fluid state once
            wader.setOnGround(true);
            h.assertTrue(wader.isInLiquid(), "wader stands in water");
            h.assertValueEqual(wader.getMainHandItem().use(h.getLevel(), wader, InteractionHand.MAIN_HAND), InteractionResult.FAIL, "cast from the water");
            h.assertTrue(swimmer.fishing == null && wader.fishing == null, "no hook");
            h.succeed();
        });

        // Changelog l.829-830 / l.212 / EF-LUN-05: 1 bite in 2400 ticks off-peak, more at dawn and dusk, in deep water, on blue
        // moon nights and with a worm; the hook waits a geometric time with that mean and bites 20 ticks longer (l.643).
        MiteGameTests.add("equipment_fishing_pace", 20, h -> {
            h.assertValueEqual(Fishing.biteRate(6000, false, false, false), Fishing.OFF_PEAK_RATE, "noon");
            h.assertValueEqual(Fishing.biteRate(12500, false, false, false), Fishing.PEAK_RATE, "dusk");
            h.assertValueEqual(Fishing.biteRate(6000, false, false, true), 2 * Fishing.OFF_PEAK_RATE, "worm doubles");
            h.assertValueEqual(Fishing.biteRate(18000, false, true, false), 2 * Fishing.OFF_PEAK_RATE, "blue moon night");
            h.assertTrue(Fishing.biteRate(6000, true, false, false) > Fishing.OFF_PEAK_RATE, "deep water");
            RandomSource random = RandomSource.create(42);
            FishingHook hook = new FishingHook(EntityTypes.FISHING_BOBBER, h.getLevel());
            hook.setPos(h.absoluteVec(new Vec3(3.5, 2, 3.5)));
            h.assertValueEqual(Fishing.timing(hook, random, 20, 40, 30, 0), 50, "bite window +20 ticks");
            h.assertValueEqual(Fishing.timing(hook, random, 20, 80, 50, 0), 50, "fish approach unchanged");
            float rate = Fishing.biteRate(h.getLevel().getDefaultClockTime(), false, dev.mite.lunar.Lunar.isBlueMoonNight(h.getLevel()), false);
            long sum = 0;
            int n = 4000;
            for (int i = 0; i < n; i++) {
                int wait = Fishing.timing(hook, random, 100, 600, 300, 100);
                h.assertTrue(wait > 100, "lure never brings the wait to zero");
                sum += wait;
            }
            double mean = (double) sum / n;
            h.assertTrue(Math.abs(mean * rate - 1) < 0.1, "mean wait " + mean + " for 1 / " + 1 / rate);
            for (int y = 1; y <= 3; y++) h.setBlock(2, y, 2, Blocks.WATER);
            h.assertValueEqual(Fishing.waterDepth(h.getLevel(), h.absolutePos(new BlockPos(2, 3, 2))), 3, "water depth");
            h.succeed();
        });

        // Changelog l.555: salmon (the MITE large fish) only bites in the open water of an ocean.
        MiteGameTests.add("equipment_fishing_large_fish", 20, h -> {
            var table = h.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_FISH);
            FishingHook hook = new FishingHook(EntityTypes.FISHING_BOBBER, h.getLevel());
            Vec3 at = h.absoluteVec(new Vec3(3.5, 2, 3.5));
            hook.setPos(at);
            java.util.function.BooleanSupplier salmon = () -> {
                LootParams params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, at)
                        .withParameter(LootContextParams.TOOL, new ItemStack(mite("copper_fishing_rod")))
                        .withParameter(LootContextParams.THIS_ENTITY, hook).create(LootContextParamSets.FISHING);
                for (int i = 0; i < 200; i++) if (table.getRandomItems(params).stream().anyMatch(s -> s.is(Items.SALMON))) return true;
                return false;
            };
            inBiomes(h, () -> {
                MiteGameTests.fillBiome(h, Biomes.RIVER);
                h.assertFalse(salmon.getAsBoolean(), "salmon in a river");
                MiteGameTests.fillBiome(h, Biomes.OCEAN);
                h.assertTrue(salmon.getAsBoolean(), "salmon in the open ocean");
            });
            h.succeed();
        });

        // EF-NUT-08 worm values; changelog l.213: grass drops worms in non-freezing biomes, more often under rain.
        MiteGameTests.add("equipment_worm", 20, h -> {
            h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(EquipmentContent.WORM.get()).getData(MetabolismContent.NUTRITION),
                    new Nutrition(0, 1, 0, false, false), "raw worm");
            h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(EquipmentContent.COOKED_WORM.get()).getData(MetabolismContent.NUTRITION),
                    new Nutrition(1, 1, 0, false, false), "cooked worm");
            BlockPos grass = h.absolutePos(new BlockPos(3, 1, 3));
            h.setBlock(3, 1, 3, Blocks.GRASS_BLOCK);
            inBiomes(h, () -> {
                MiteGameTests.fillBiome(h, Biomes.PLAINS);
                h.assertValueEqual(Fishing.wormChance(h.getLevel(), grass), Fishing.WORM_CHANCE, "plains, dry");
                // Only grass broken by a player drops worms: 500 breaks at 1 in 32 (miss odds ~1e-7), none without a player.
                ServerPlayer p = MetabolismTests.player(h);
                int byPlayer = 0, byNobody = 0;
                for (int i = 0; i < 500; i++) {
                    byPlayer += worms(h, grass, p);
                    byNobody += worms(h, grass, null);
                }
                h.assertTrue(byPlayer > 0, "grass broken by a player drops worms");
                h.assertValueEqual(byNobody, 0, "grass broken without a player");
                MiteGameTests.fillBiome(h, Biomes.SNOWY_PLAINS);
                h.assertValueEqual(Fishing.wormChance(h.getLevel(), grass), 0f, "freezing biome");
            });
            h.succeed();
        });
        MiteGameTests.addInRain("equipment_worm_rain", 20, h -> {
            h.setBlock(3, 1, 3, Blocks.GRASS_BLOCK);
            inBiomes(h, () -> {
                MiteGameTests.fillBiome(h, Biomes.PLAINS);
                h.assertValueEqual(Fishing.wormChance(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3))), Fishing.WORM_CHANCE_RAIN, "grass in the rain");
            });
            h.succeed();
        });

        // H11: a silver door opens with redstone; silver bars connect to a neighbor.
        MiteGameTests.add("equipment_doors_bars", 20, h -> {
            DoorBlock door = EquipmentContent.DOORS.get(Material.SILVER).get();
            h.setBlock(3, 1, 3, door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            h.setBlock(3, 2, 3, door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            h.setBlock(4, 1, 3, Blocks.REDSTONE_BLOCK);
            h.setBlock(1, 1, 1, EquipmentContent.BARS.get(Material.SILVER).get());
            h.setBlock(2, 1, 1, Blocks.STONE);
            h.succeedWhen(() -> {
                h.assertTrue(h.getBlockState(new BlockPos(3, 1, 3)).getValue(DoorBlock.OPEN), "powered silver door is open");
                h.assertTrue(h.getBlockState(new BlockPos(1, 1, 1)).getValue(IronBarsBlock.EAST), "silver bars connect east");
            });
        });
    }
}
