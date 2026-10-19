package dev.mite.farming.livestock;

import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** GameTests for I10 (EF-AGR-08) and I11 (EF-AGR-09). */
public final class LivestockTests {
    private LivestockTests() {}

    /** Marks the state as evaluated {@code ticks} ago, so the next evaluation applies that gap. */
    private static void age(Animal animal, long ticks) {
        LivestockService.state(animal).lastEvalTick = animal.level().getGameTime() - ticks;
    }

    public static void register() {
        // EF-AGR-08 (M20): a cow flees a wild wolf.
        MiteGameTests.add("livestock_flees_wolf", 200, h -> {
            Cow cow = h.spawn(EntityTypes.COW, 2, 1, 3);
            Wolf wolf = h.spawn(EntityTypes.WOLF, 4, 1, 3);
            wolf.setNoAi(true);
            // starts 2 blocks away; the 7x7 pen allows about 5 at best, so any clear retreat counts
            h.succeedWhen(() -> h.assertTrue(cow.distanceTo(wolf) > 4, "cow fled, distance " + cow.distanceTo(wolf)));
        });
        // EF-AGR-08 (M20): in the rain, a cow walks under the roof.
        MiteGameTests.addInRain("livestock_shelters_from_rain", 400, h -> {
            MiteGameTests.fillBiome(h, Biomes.PLAINS); // the test site may be a desert
            for (int x = 0; x <= 2; x++) for (int z = 0; z <= 6; z++) h.setBlock(x, 3, z, Blocks.STONE);
            Cow cow = h.spawn(EntityTypes.COW, 5, 1, 3);
            h.succeedWhen(() -> h.assertFalse(h.getLevel().canSeeSky(cow.blockPosition()), "cow sheltered"));
        });
        // EF-AGR-08: a hungry cow gives no milk, a fed one does; a hungry cow eats wheat instead of falling in love.
        MiteGameTests.add("livestock_milk", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            Livestock s = LivestockService.state(cow);
            s.food = 0;
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
            h.assertTrue(p.getMainHandItem().is(Items.BUCKET), "hungry cow: no milk, got " + p.getMainHandItem());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 2));
            p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
            h.assertTrue(s.food == Livestock.FEED && p.getMainHandItem().getCount() == 1 && !cow.isInLove(), "wheat eaten, food " + s.food);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
            h.assertTrue(p.getMainHandItem().is(Items.MILK_BUCKET), "fed cow: milk, got " + p.getMainHandItem());
            h.succeed();
        });

        // EF-AGR-08: food and water decay over time away from grass and water; a water bowl waters the animal.
        MiteGameTests.add("livestock_decay", 20, h -> {
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            Livestock s = LivestockService.state(cow);
            age(cow, 12_000);
            LivestockService.evaluate(cow);
            h.assertTrue(Math.abs(s.food - 0.75f) < 1e-4 && Math.abs(s.water - 0.5f) < 1e-4, "half a day: food " + s.food + ", water " + s.water);
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dev.mite.metabolism.MetabolismContent.WATER_BOWL.get()));
            p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
            h.assertTrue(s.water == 1 && p.getMainHandItem().is(Items.BOWL), "watered, bowl back");
            h.succeed();
        });

        // EF-AGR-08: a neglected (starving, sick) animal loses health, lays no eggs and does not breed.
        MiteGameTests.add("livestock_neglect", 20, h -> {
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            Livestock s = LivestockService.state(cow);
            s.food = 0;
            s.sickness = Livestock.DYING;
            float health = cow.getHealth();
            age(cow, Livestock.TICKS_PER_DAMAGE);
            LivestockService.evaluate(cow);
            h.assertTrue(cow.getHealth() < health && s.sick() && !s.productive(), "neglected cow hurt, health " + cow.getHealth());
            Chicken chicken = h.spawn(EntityTypes.CHICKEN, 1, 1, 1);
            LivestockService.state(chicken).water = 0;
            chicken.eggTime = 1;
            LivestockService.evaluate(chicken);
            h.assertTrue(chicken.eggTime > 1, "thirsty chicken lays no egg");
            h.succeed();
        });

        // EF-AGR-08: healthy animals breed, sick ones do not.
        MiteGameTests.add("livestock_breeding", 20, h -> {
            Cow a = h.spawn(EntityTypes.COW, 1, 1, 1), b = h.spawn(EntityTypes.COW, 2, 1, 1);
            Cow c = h.spawn(EntityTypes.COW, 4, 1, 5), d = h.spawn(EntityTypes.COW, 5, 1, 5);
            LivestockService.state(c).sickness = Livestock.SICK;
            a.spawnChildFromBreeding(h.getLevel(), b);
            c.spawnChildFromBreeding(h.getLevel(), d);
            long babies = h.getEntities(EntityTypes.COW).stream().filter(Cow::isBaby).count();
            h.assertValueEqual(babies, 1L, "babies (healthy pair only)");
            h.succeed();
        });

        // EF-AGR-09: livestock is faster; an animal killed by a fall drops no meat.
        MiteGameTests.add("livestock_fall_speed", 20, h -> {
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            h.assertTrue(cow.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(LivestockService.SPEED.id()), "speed modifier");
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.BEEF)),
                    new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.LEATHER))));
            LivestockService.onDrops(new LivingDropsEvent(cow, cow.damageSources().fall(), drops, false));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(Items.LEATHER), "only leather after a fall");
            h.succeed();
        });

        // Changelog l.570 (R128): sick livestock drops no meat, whatever kills it.
        MiteGameTests.add("livestock_sick_no_meat", 20, h -> {
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            LivestockService.state(cow).sickness = Livestock.SICK;
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.BEEF)),
                    new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.LEATHER))));
            LivestockService.onDrops(new LivingDropsEvent(cow, cow.damageSources().generic(), drops, false));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(Items.LEATHER), "only leather from a sick cow");
            h.succeed();
        });

        // Changelog l.22 (R193) and l.831 (R117): chickens make manure half as often, shed feathers, and drop half as many at death.
        MiteGameTests.add("livestock_chicken_feathers", 20, h -> {
            Chicken chicken = h.spawn(EntityTypes.CHICKEN, 3, 1, 3);
            List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.FEATHER, 2))));
            LivestockService.onDrops(new LivingDropsEvent(chicken, chicken.damageSources().generic(), drops, false));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "2 feathers halved");
            Livestock s = LivestockService.state(chicken);
            for (int i = 0; i < 80; i++) {
                s.food = 1;
                s.water = 1;
                s.sunlight = 1;
                s.sickness = 0;
                age(chicken, 12_000); // a quarter of the feather and chicken manure intervals: odds 1/4 each (water lasts a day)
                LivestockService.evaluate(chicken);
            }
            int feathers = 0, manure = 0;
            for (ItemEntity item : h.getEntities(EntityTypes.ITEM)) {
                if (item.getItem().is(Items.FEATHER)) feathers += item.getItem().getCount();
                if (item.getItem().is(dev.mite.farming.FarmingContent.MANURE.get())) manure += item.getItem().getCount();
            }
            h.assertTrue(feathers >= 8 && feathers <= 34, "feathers shed " + feathers + "/80");
            h.assertTrue(manure >= 8 && manure <= 34, "chicken manure " + manure + "/80 (a cow: 40)");
            h.succeed();
        });

        // EF-AGR-08 / changelog l.836-837, l.283: grazing eats the pasture. A pig turns its grass block to dirt, even when full;
        // a cow feeds on the tall grass instead and destroys it only now and then.
        MiteGameTests.add("livestock_grazing_eats_grass", 20, h -> {
            h.setBlock(1, 0, 1, Blocks.GRASS_BLOCK);
            h.setBlock(5, 0, 5, Blocks.GRASS_BLOCK);
            h.setBlock(3, 0, 1, Blocks.GRASS_BLOCK);
            h.setBlock(3, 1, 1, Blocks.SHORT_GRASS);
            Pig hungry = h.spawn(EntityTypes.PIG, 1, 1, 1), full = h.spawn(EntityTypes.PIG, 5, 1, 5);
            LivestockService.state(hungry).food = 0;
            age(hungry, 2_400);
            LivestockService.evaluate(hungry);
            h.assertTrue(LivestockService.state(hungry).food == 1, "pig grazed, food " + LivestockService.state(hungry).food);
            h.assertBlockPresent(Blocks.DIRT, 1, 0, 1);
            age(full, Livestock.MAX_GAP);
            LivestockService.evaluate(full);
            h.assertBlockPresent(Blocks.DIRT, 5, 0, 5);
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 1);
            Livestock s = LivestockService.state(cow);
            s.food = 0;
            age(cow, 2_400);
            LivestockService.evaluate(cow);
            h.assertTrue(s.food == 1, "cow grazed, food " + s.food);
            h.assertBlockPresent(Blocks.GRASS_BLOCK, 3, 0, 1);
            for (int i = 0; i < 200 && h.getLevel().getBlockState(h.absolutePos(new BlockPos(3, 1, 1))).is(Blocks.SHORT_GRASS); i++) {
                s.food = 0;
                age(cow, 2_400);
                LivestockService.evaluate(cow);
            }
            h.assertBlockNotPresent(Blocks.SHORT_GRASS, 3, 1, 1);
            h.succeed();
        });

        // Changelog l.835 and l.819: a walking cow tramples the flower it walks through, which drops nothing; chickens do not.
        MiteGameTests.add("livestock_trampling", 20, h -> {
            h.setBlock(1, 1, 1, Blocks.POPPY);
            h.setBlock(5, 1, 5, Blocks.POPPY);
            Cow cow = h.spawn(EntityTypes.COW, 1, 1, 1);
            Chicken chicken = h.spawn(EntityTypes.CHICKEN, 5, 1, 5);
            cow.setDeltaMovement(0.2, 0, 0);
            chicken.setDeltaMovement(0.2, 0, 0);
            boolean trampled = false;
            for (int i = 0; i < 400 && !trampled; i++) {
                trampled = LivestockService.trample(cow, h.getLevel(), cow.blockPosition());
                h.assertFalse(LivestockService.trample(chicken, h.getLevel(), chicken.blockPosition()), "chicken trampled");
            }
            h.assertTrue(trampled, "cow never trampled");
            h.assertBlockPresent(Blocks.AIR, 1, 1, 1);
            h.assertItemEntityNotPresent(Items.POPPY);
            h.succeed();
        });

        // Changelog l.515 (R133): a hurt cow spreads its panic to a nearby pig.
        MiteGameTests.add("livestock_panic_spreads", 100, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            Cow cow = h.spawn(EntityTypes.COW, 1, 1, 1);
            Pig pig = h.spawn(EntityTypes.PIG, 4, 1, 4);
            cow.hurtServer(h.getLevel(), cow.damageSources().playerAttack(p), 1);
            h.assertTrue(LivestockService.panicking(pig), "pig not frightened");
            h.succeedWhen(() -> h.assertTrue(pig.goalSelector.getAvailableGoals().stream()
                    .anyMatch(w -> w.isRunning() && w.getGoal() instanceof LivestockService.SpreadPanicGoal), "pig not fleeing"));
        });
        // Changelog l.295: a wild horse accepts a food bribe once every 4000 ticks.
        MiteGameTests.add("livestock_horse_bribe", 20, h -> {
            Horse horse = wildHorse(h);
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 4));
            p.interactOn(horse, InteractionHand.MAIN_HAND, horse.position());
            h.assertValueEqual(horse.getTemper(), 3, "first bribe");
            p.interactOn(horse, InteractionHand.MAIN_HAND, horse.position());
            h.assertValueEqual(horse.getTemper(), 3, "second bribe refused");
            HorseTaming.state(horse).lastBribe -= HorseTaming.BRIBE_COOLDOWN;
            p.interactOn(horse, InteractionHand.MAIN_HAND, horse.position());
            h.assertValueEqual(horse.getTemper(), 6, "bribe after 4000 ticks");
            h.succeed();
        });

        // Changelog l.296: once a wild horse throws its rider (vanilla goal, see RunAroundLikeCrazyGoalMixin), it refuses riders for 4000 ticks.
        MiteGameTests.add("livestock_horse_throw", 20, h -> {
            Horse horse = wildHorse(h);
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            h.assertTrue(p.startRiding(horse), "first ride");
            RunAroundLikeCrazyGoal goal = new RunAroundLikeCrazyGoal(horse, 1.2);
            for (int i = 0; i < 100_000 && p.isPassenger(); i++) goal.tick();
            h.assertFalse(p.isPassenger(), "rider thrown");
            h.assertTrue(HorseTaming.state(horse).refuseUntil > h.getLevel().getGameTime(), "refusal started");
            ServerPlayer other = MiteGameTests.levelPlayer(h); // the thrown rider has a vanilla boarding cooldown
            h.assertFalse(other.startRiding(horse), "ride refused");
            HorseTaming.state(horse).refuseUntil = h.getLevel().getGameTime();
            h.assertTrue(other.startRiding(horse), "ride after the refusal");
            h.succeed();
        });

        // Changelog l.298 / l.284: harm costs a wild horse temper; any horse stops grazing when hurt.
        MiteGameTests.add("livestock_horse_harm", 20, h -> {
            Horse wild = wildHorse(h), tame = h.spawn(EntityTypes.HORSE, 5, 1, 5);
            tame.setTamed(true);
            wild.setTemper(50);
            for (Horse horse : List.of(wild, tame)) {
                horse.setEating(true);
                horse.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 1);
                h.assertFalse(horse.isEating(), "stopped grazing");
            }
            h.assertValueEqual(wild.getTemper(), 50 - HorseTaming.HARM_TEMPER, "wild horse temper");
            h.succeed();
        });

        // Changelog l.297 / l.285: a wild horse shies away from players (not from sneaking or creative ones) and stops grazing meanwhile.
        MiteGameTests.add("livestock_horse_shy", 20, h -> {
            Horse horse = wildHorse(h);
            Player walker = h.makeMockPlayer(GameType.SURVIVAL), sneaker = h.makeMockPlayer(GameType.SURVIVAL);
            sneaker.setShiftKeyDown(true);
            h.assertTrue(HorseTaming.shiesFrom(walker), "shies from a walking player");
            h.assertFalse(HorseTaming.shiesFrom(sneaker), "sneaking player");
            h.assertFalse(HorseTaming.shiesFrom(h.makeMockPlayer(GameType.CREATIVE)), "creative player");
            var shy = horse.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g instanceof HorseTaming.ShyGoal).findFirst();
            h.assertTrue(shy.isPresent(), "shy goal installed");
            horse.setEating(true);
            shy.get().start();
            h.assertFalse(horse.isEating(), "no grazing while shying away");
            h.succeed();
        });

        // Q-3 / A-4: farming.livestock_needs=false keeps livestock satisfied (no sickness, breeding allowed).
        MiteGameTests.add("livestock_needs_config", 20, h -> {
            Cow cow = h.spawn(EntityTypes.COW, 3, 1, 3);
            Livestock s = LivestockService.state(cow);
            s.food = s.water = 0;
            s.sickness = 1_000_000;
            boolean breeds = MiteGameTests.withConfig(dev.mite.config.MiteConfig.LIVESTOCK_NEEDS, false, () -> {
                boolean b = LivestockService.canBreed(cow);
                age(cow, 2000);
                LivestockService.evaluate(cow);
                return b;
            });
            h.assertTrue(breeds, "a sick cow cannot breed with livestock_needs=false");
            h.assertTrue(s.productive() && !s.sick() && s.food == 1, "needs not satisfied with livestock_needs=false");
            s.food = 0;
            s.sickness = 1_000_000;
            h.assertFalse(LivestockService.canBreed(cow), "a sick cow breeds with livestock needs on");
            h.succeed();
        });
    }

    private static Horse wildHorse(net.minecraft.gametest.framework.GameTestHelper h) {
        Horse horse = h.spawn(EntityTypes.HORSE, 3, 1, 3);
        horse.setAge(0);
        horse.setTamed(false);
        horse.setTemper(0);
        return horse;
    }
}
