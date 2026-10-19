package dev.mite.metabolism;

import com.mojang.authlib.GameProfile;
import dev.mite.core.Metabolism;
import dev.mite.gametest.MiteGameTests;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.GameType;
import dev.mite.core.Formulas;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import dev.mite.config.MiteConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.storage.TagValueOutput;

/** GameTests for C01, C03, C04, C05, C06, C08, C09, C11, C13, C14 and B01. The food tick goes through FoodData (MX-01). */
public final class MetabolismTests {
    private MetabolismTests() {}

    /** Survival mock player outside the level, with a dummy connection (attachment sync needs one), always damageable. */
    public static ServerPlayer player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        ServerPlayer p = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }

            @Override
            public boolean isInvulnerableTo(ServerLevel l, DamageSource source) {
                return false;
            }
        };
        GameType.SURVIVAL.updatePlayerAbilities(p.getAbilities());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(level.getServer(), connection, p, CommonListenerCookie.createInitial(profile, false));
        return p;
    }

    private static void tick(ServerPlayer p, int ticks) {
        for (int i = 0; i < ticks; i++) p.getFoodData().tick(p);
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): hunger_global_multiplier scales the hunger of actions; 0 leaves only the base metabolism.
        MiteGameTests.add("metabolism_hunger_multiplier_config", 20, helper -> {
            ServerPlayer idle = player(helper), busy = player(helper);
            MetabolismService.get(idle).hunger = MetabolismService.get(busy).hunger = 0;
            MiteGameTests.withConfig(MiteConfig.HUNGER_GLOBAL_MULTIPLIER, 0.0, () -> {
                MetabolismService.tick(idle, idle.getFoodData(), 0.1f, 0);
                return null;
            });
            MetabolismService.tick(busy, busy.getFoodData(), 0.1f, 0);
            float base = MetabolismService.get(idle).hunger, charged = MetabolismService.get(busy).hunger;
            helper.assertTrue(base > 0 && charged > base, "action hunger: " + charged + " with the default multiplier, " + base + " with 0");
            helper.succeed();
        });

        // EF-NUT-01 CA-1: at rest, full 6 + 6 at level 0, one unit lost in 2000 ± 20 ticks.
        MiteGameTests.add("metabolism_resting_rate", 20, helper -> {
            ServerPlayer p = player(helper);
            // Under the arena roof: at the world origin the rain (or a rain batch still fading out) would speed the drain up.
            p.setPos(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            helper.assertValueEqual(MetabolismService.humidity(p), 0f, "humidity under the roof");
            Metabolism m = MetabolismService.get(p);
            tick(p, 1980);
            helper.assertValueEqual(m.satiety, 6, "satiety at 1980");
            tick(p, 40);
            helper.assertValueEqual(m.satiety, 5, "satiety at 2020");
            helper.assertValueEqual(p.getFoodData().getFoodLevel(), 20, "mirrored foodLevel");
            helper.succeed();
        });

        // EF-NUT-06 CA-1: cooked beef (nutrition 10) adds 80 000 protein, through the real consume path.
        MiteGameTests.add("metabolism_eat_cooked_beef", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            m.protein = 10_000;
            m.satiety = 0;
            ItemStack stack = new ItemStack(Items.COOKED_BEEF);
            EventHooks.onItemUseFinish(p, stack.copy(), 0, stack.finishUsingItem(helper.getLevel(), p));
            helper.assertValueEqual(m.protein, 90_000, "protein");
            helper.assertValueEqual(m.satiety, 6, "satiety");
            helper.succeed();
        });

        // EF-NUT-05 CA-2: nutrition 0 deals 1 damage at 500 ticks.
        MiteGameTests.add("metabolism_starvation", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            m.satiety = 0;
            m.nutrition = 0;
            tick(p, 1);
            float health = p.getHealth();
            tick(p, 498);
            helper.assertValueEqual(p.getHealth(), health, "health at 499");
            tick(p, 1);
            helper.assertValueEqual(p.getHealth(), health - 1, "health at 500");
            // EF-NUT-05 CA-1: zero energy walks at 75%.
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) / p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.75) < 1e-6, "walk speed x0.75");
            helper.assertTrue(!MetabolismService.hasEnergy(p), "no energy");
            helper.succeed();
        });

        // EF-JOU-01: max health 6 at level 0, 8 at level 5 with +2 current HP.
        MiteGameTests.add("metabolism_max_health", 20, helper -> {
            ServerPlayer p = player(helper);
            tick(p, 1);
            helper.assertValueEqual(p.getMaxHealth(), 6f, "max health at level 0");
            helper.assertValueEqual(p.getHealth(), 6f, "health capped at level 0");
            p.giveExperienceLevels(5);
            tick(p, 1);
            helper.assertValueEqual(p.getMaxHealth(), 8f, "max health at level 5");
            helper.assertValueEqual(p.getHealth(), 8f, "health +2 at level 5");
            helper.succeed();
        });

        // EF-NUT-09 CA-1: every vanilla food has a hardscrabble:nutrition entry. EF-NUT-10: raw chicken poisons 30%.
        MiteGameTests.add("metabolism_nutrition_table", 20, helper -> {
            BuiltInRegistries.ITEM.listElements()
                    .filter(h -> h.value().components().has(DataComponents.FOOD))
                    .forEach(h -> helper.assertTrue(h.getData(MetabolismContent.NUTRITION) != null, "no hardscrabble:nutrition for " + h.getRegisteredName()));
            var effect = (ApplyStatusEffectsConsumeEffect) Items.CHICKEN.components().get(DataComponents.CONSUMABLE).onConsumeEffects().getFirst();
            helper.assertValueEqual(effect.probability(), 0.3f, "raw chicken poison chance");
            helper.assertTrue(effect.effects().getFirst().is(MobEffects.POISON), "raw chicken poison effect");
            helper.succeed();
        });

        // EF-NUT-03 CA-1: rain + feet in water in plains = 1.375; standing in water in a frozen biome = 1.5. The biome and the rain
        // are passed explicitly: test boxes sit in random biomes under a ceiling, and changing the weather would leak into other tests.
        MiteGameTests.add("metabolism_humidity", 20, helper -> {
            ServerPlayer p = player(helper);
            helper.setBlock(3, 1, 3, Blocks.WATER);
            p.setPos(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            helper.assertValueEqual(Formulas.hungerMultiplier(MetabolismService.humidity(p, biomes.getOrThrow(Biomes.PLAINS), Biome.Precipitation.RAIN, false), false), 1.375f, "rain + feet in water");
            helper.assertValueEqual(Formulas.hungerMultiplier(MetabolismService.humidity(p, biomes.getOrThrow(Biomes.SNOWY_PLAINS), Biome.Precipitation.NONE, false), false), 1.5f, "frozen, feet in water");
            helper.assertValueEqual(MetabolismService.humidity(p, biomes.getOrThrow(Biomes.DESERT), Biome.Precipitation.RAIN, true), 0f, "hot biome");
            // Changelog R118: snow +0 %, and the frozen x2 never applies to rain.
            p.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
            helper.assertValueEqual(MetabolismService.humidity(p, biomes.getOrThrow(Biomes.SNOWY_PLAINS), Biome.Precipitation.SNOW, true), 0f, "snow");
            helper.assertValueEqual(MetabolismService.humidity(p, biomes.getOrThrow(Biomes.SNOWY_PLAINS), Biome.Precipitation.RAIN, false), 0.25f, "rain in a frozen biome");
            helper.succeed();
        });

        // EF-NUT-03 CA-2: every vanilla biome is frozen, hot or explicitly temperate, exactly once, and the tags match the tables.
        // A GameTest rather than JUnit: the unit test classpath has no Minecraft classes.
        MiteGameTests.add("metabolism_biomes_classified", 20, helper -> {
            helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).listElements()
                    .filter(h -> h.key().identifier().getNamespace().equals("minecraft")).forEach(h -> {
                        var key = h.key();
                        int n = (BiomeClimate.FROZEN_BIOMES.contains(key) ? 1 : 0) + (BiomeClimate.HOT_BIOMES.contains(key) ? 1 : 0)
                                + (BiomeClimate.TEMPERATE_BIOMES.contains(key) ? 1 : 0);
                        helper.assertValueEqual(n, 1, key.identifier() + " classifications");
                        Formulas.Climate expected = BiomeClimate.FROZEN_BIOMES.contains(key) ? Formulas.Climate.FROZEN
                                : BiomeClimate.HOT_BIOMES.contains(key) ? Formulas.Climate.HOT : Formulas.Climate.TEMPERATE;
                        helper.assertTrue(BiomeClimate.of(h) == expected, key.identifier() + " tag");
                    });
            helper.succeed();
        });

        // EF-NUT-07 CA-1: 10 apples in a row bring resistance to 48 000, level light.
        MiteGameTests.add("metabolism_insulin_apples", 20, helper -> {
            ServerPlayer p = player(helper);
            for (int i = 0; i < 10; i++) eat(helper, p, Items.APPLE);
            Metabolism m = MetabolismService.get(p);
            helper.assertValueEqual(m.insulin, 48_000, "insulin");
            helper.assertValueEqual(m.insulinLevel, 1, "level light");
            helper.assertTrue(!p.hasEffect(MobEffects.NAUSEA), "no nausea before the resistant state");
            helper.succeed();
        });

        // EF-NUT-07 CA-2: severe resistance + a 1000-sugar food (pumpkin pie stands in for MITE's ice cream) = Poison 100 ticks.
        MiteGameTests.add("metabolism_insulin_severe", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            m.insulin = 150_000;
            m.insulinLevel = 3;
            m.satiety = 0;
            eat(helper, p, Items.PUMPKIN_PIE);
            helper.assertValueEqual(p.getEffect(MobEffects.POISON).getDuration(), 100, "poison ticks");
            helper.assertValueEqual(p.getEffect(MobEffects.NAUSEA).getAmplifier(), 2, "nausea amplifier");
            helper.assertValueEqual(m.satiety, 6, "satiety 10 - 1, capped at 6");
            helper.succeed();
        });

        // EF-NUT-06: a malnourished, full player can still eat the missing nutrient, and only that.
        MiteGameTests.add("metabolism_craving", 20, helper -> {
            ServerPlayer p = player(helper);
            MetabolismService.get(p).phyto = 0;
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(!p.isUsingItem(), "full: bread refused");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(p.isUsingItem(), "full but craving phyto: apple eaten");
            helper.succeed();
        });

        // EF-NUT-12 CA-1: a water bucket emptied into 4 bowls gives 4 bowls of water and an empty bucket; a cow fills a bowl with milk.
        MiteGameTests.add("metabolism_bowls", 20, helper -> {
            ItemStack bowl = new ItemStack(Items.BOWL);
            CraftingInput input = CraftingInput.of(3, 2, List.of(new ItemStack(Items.WATER_BUCKET), bowl.copy(), bowl.copy(), bowl.copy(), bowl.copy(), ItemStack.EMPTY));
            var recipe = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow().value();
            ItemStack out = recipe.assemble(input);
            helper.assertTrue(out.is(MetabolismContent.WATER_BOWL.get()) && out.getCount() == 4, "4 water bowls, got " + out);
            helper.assertTrue(recipe.getRemainingItems(input).stream().anyMatch(s -> s.is(Items.BUCKET)), "empty bucket back");
            ServerPlayer p = player(helper);
            Cow cow = helper.spawn(EntityTypes.COW, 3, 1, 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, bowl.copy());
            p.interactOn(cow, InteractionHand.MAIN_HAND, cow.position());
            helper.assertTrue(p.getMainHandItem().is(MetabolismContent.MILK_BOWL.get()), "milk bowl, got " + p.getMainHandItem());
            helper.succeed();
        });

        // EF-NUT-11 CA-1: 6000 ticks of sleep run the metabolism (6000 ticks = 3 satiety units) and award Well Rested.
        MiteGameTests.add("metabolism_sleep", 20, helper -> {
            ServerPlayer p = player(helper);
            p.setPos(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            p.setSleepingPos(helper.absolutePos(new BlockPos(3, 1, 3))); // no bed needed for the simulation
            Metabolism m = MetabolismService.get(p);
            tick(p, 1); // the player's own tick
            MiteSleep.sleep(p, 6019, 0);
            helper.assertTrue(p.isSleeping(), "still asleep");
            helper.assertValueEqual(m.satiety, 3, "satiety after 6020 ticks");
            helper.assertValueEqual(m.nutrition, 6, "nutrition after 6020 ticks");
            var adv = helper.getLevel().getServer().getAdvancements().get(MiteSleep.WELL_RESTED);
            helper.assertTrue(adv != null && p.getAdvancements().getOrStartProgress(adv).isDone(), "Well Rested");
            // Zero energy wakes the sleeper.
            m.satiety = 0;
            m.nutrition = 0;
            MiteSleep.sleep(p, 2, 0);
            helper.assertTrue(!p.isSleeping(), "woken by hunger");
            helper.succeed();
        });

        // X10 / EF-NUT-09: the Saturation effect of a dandelion suspicious stew (7 ticks) feeds nothing; a cake slice still does.
        MiteGameTests.add("metabolism_saturation_feeds_nothing", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            m.satiety = 0;
            m.nutrition = 0;
            for (int i = 0; i < 7; i++) MobEffects.SATURATION.value().applyEffectTick(helper.getLevel(), p, 0);
            tick(p, 1);
            helper.assertValueEqual(m.satiety, 0, "satiety from Saturation");
            helper.assertValueEqual(m.nutrition, 0, "nutrition from Saturation");
            p.getFoodData().eat(2, 0.1f); // the cake path
            tick(p, 1);
            helper.assertValueEqual(m.satiety, 2, "cake slice satiety");
            helper.succeed();
        });

        // EF-NUT-08 / changelog R118: sugar, seeds and nether wart are eaten; every hardscrabble:nutrition entry is a food.
        MiteGameTests.add("metabolism_junk_food_edible", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            m.satiety = 0;
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SUGAR));
            p.gameMode.useItem(p, helper.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertTrue(p.isUsingItem(), "sugar is eaten");
            eat(helper, p, Items.SUGAR);
            helper.assertValueEqual(m.satiety, 1, "sugar satiety");
            BuiltInRegistries.ITEM.listElements().filter(h -> h.getData(MetabolismContent.NUTRITION) != null)
                    .forEach(h -> helper.assertTrue(h.value().components().has(DataComponents.FOOD),
                            h.getRegisteredName() + " has hardscrabble:nutrition but no food component"));
            helper.succeed();
        });

        // EF-NUT-08: golden apple = Regeneration I 60 ticks, no Absorption; changelog l.593 (R127): no Absorption on the enchanted apple either.
        MiteGameTests.add("metabolism_golden_apples", 20, helper -> {
            var effects = Items.GOLDEN_APPLE.components().get(DataComponents.CONSUMABLE).onConsumeEffects().stream()
                    .flatMap(e -> ((ApplyStatusEffectsConsumeEffect) e).effects().stream()).toList();
            helper.assertValueEqual(effects.size(), 1, "golden apple effects");
            helper.assertTrue(effects.getFirst().is(MobEffects.REGENERATION) && effects.getFirst().getDuration() == 60, "Regeneration 60 ticks");
            helper.assertValueEqual(effects.getFirst().getAmplifier(), 0, "Regeneration I");
            helper.assertTrue(Items.ENCHANTED_GOLDEN_APPLE.components().get(DataComponents.CONSUMABLE).onConsumeEffects().stream()
                    .flatMap(e -> ((ApplyStatusEffectsConsumeEffect) e).effects().stream()).noneMatch(e -> e.is(MobEffects.ABSORPTION)), "no Absorption");
            helper.succeed();
        });

        // EF-NUT-04: the Regeneration effect does not heal a player by itself; it speeds up the MITE healing, which costs hunger.
        MiteGameTests.add("metabolism_regeneration_effect", 20, helper -> {
            ServerPlayer p = player(helper);
            tick(p, 1);
            p.setHealth(2);
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            MobEffects.REGENERATION.value().applyEffectTick(helper.getLevel(), p, 0);
            helper.assertValueEqual(p.getHealth(), 2f, "no free vanilla heal");
            Metabolism m = MetabolismService.get(p);
            m.hunger = 0;
            tick(p, 100);
            helper.assertValueEqual(p.getHealth(), 4f, "2 HP in 100 ticks with Regeneration I");
            helper.assertTrue(m.hunger > 2, "each HP costs a hunger point, hunger " + m.hunger);
            helper.succeed();
        });

        // EF-NUT-05 (project owner's decision): sprint follows the food energy, not the foodLevel mirror; no mining at zero energy.
        MiteGameTests.add("metabolism_sprint_and_mining", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            m.nutrition = 1;
            tick(p, 1);
            helper.assertTrue(p.getFoodData().getFoodLevel() <= 6, "mirrored foodLevel low");
            helper.assertTrue(sprintAllowed(p), "sprint with full satiety and low nutrition");
            m.satiety = 0;
            m.nutrition = 0;
            helper.assertTrue(!sprintAllowed(p), "no sprint at zero energy");
            helper.assertTrue(EventHooks.getBreakSpeed(p, Blocks.DIRT.defaultBlockState(), 1, BlockPos.ZERO) < 0, "no mining at zero energy");
            helper.succeed();
        });

        // Changelog R145: holding a bow drawn consumes hunger.
        MiteGameTests.add("metabolism_bow_hunger", 20, helper -> {
            ServerPlayer p = player(helper);
            Metabolism m = MetabolismService.get(p);
            ItemStack bow = new ItemStack(Items.BOW);
            for (int i = 0; i < 20; i++) EventHooks.onItemUseTick(p, bow, 72000 - i);
            m.hunger = 0;
            tick(p, 1);
            helper.assertTrue(m.hunger > 0.05f, "20 drawn ticks cost hunger, hunger " + m.hunger);
            helper.succeed();
        });

        // EF-NUT-11: a monster farther than 8 blocks wakes the sleeper only with a path to the bed.
        MiteGameTests.add("metabolism_sleep_intruders", 20, helper -> {
            ServerPlayer p = player(helper);
            p.setPos(helper.absoluteVec(new Vec3(6.5, 1, 6.5)));
            BlockPos bed = helper.absolutePos(new BlockPos(6, 1, 6));
            for (int y = 1; y <= 2; y++) for (int[] d : new int[][] {{5, 6}, {7, 6}, {6, 5}, {6, 7}}) helper.setBlock(d[0], y, d[1], Blocks.STONE);
            helper.setBlock(6, 3, 6, Blocks.STONE);
            Zombie far = helper.spawn(EntityTypes.ZOMBIE, 0, 1, 0);
            Zombie near = helper.spawn(EntityTypes.ZOMBIE, 3, 1, 3);
            for (Zombie z : List.of(far, near)) {
                z.setNoAi(true);
                z.setOnGround(true);
            }
            helper.assertTrue(far.distanceToSqr(p) > 64, "far zombie beyond 8 blocks");
            helper.assertTrue(!MiteSleep.comesFor(far, p, bed), "no path: the far zombie does not wake");
            helper.assertTrue(MiteSleep.comesFor(near, p, bed), "within 8 blocks: wakes");
            helper.setBlock(5, 1, 6, Blocks.AIR);
            helper.setBlock(5, 2, 6, Blocks.AIR);
            helper.assertTrue(MiteSleep.comesFor(far, p, bed), "path to the bed: wakes");
            helper.succeed();
        });

        // Changelog R118: a player on the respawn screen does not block the others' sleep.
        MiteGameTests.add("metabolism_sleep_dead_player", 20, helper -> {
            ServerLevel level = helper.getLevel();
            ServerPlayer sleeper = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "sleeper"), ClientInformation.createDefault()) {
                @Override
                public boolean isSleepingLongEnough() {
                    return true;
                }
            };
            ServerPlayer other = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "other"), ClientInformation.createDefault());
            helper.assertTrue(!MiteSleep.enoughSleeping(List.of(sleeper, other), 100), "an awake player blocks");
            other.setHealth(0);
            helper.assertTrue(MiteSleep.enoughSleeping(List.of(sleeper, other), 100), "a dead player does not block");
            helper.succeed();
        });

        // Changelog l.566 (N-2): a night slept at x40 forwards the weather timers by the whole 12 000 ticks, not 300; a timer stops
        // at 1 so vanilla flips the weather. Changelog l.799: only off a dedicated server (the GameTest server is not one).
        MiteGameTests.add("metabolism_sleep_forwards_weather", 20, helper -> {
            var w = new net.minecraft.world.level.saveddata.WeatherData(0, 20000, 30000, false, false);
            for (int tick = 0; tick < 12000 / MiteSleep.RATE; tick++) {
                MiteSleep.forwardWeather(w, MiteSleep.RATE - 1);
                w.setRainTime(w.getRainTime() - 1); // the vanilla tick
                w.setThunderTime(w.getThunderTime() - 1);
            }
            helper.assertTrue(w.getRainTime() == 8000 && w.getThunderTime() == 18000, "timers after a night: " + w.getRainTime() + ", " + w.getThunderTime());
            w = new net.minecraft.world.level.saveddata.WeatherData(0, 20, 30, true, false);
            MiteSleep.forwardWeather(w, MiteSleep.RATE - 1);
            helper.assertTrue(w.getRainTime() == 1 && w.getThunderTime() == 1 && w.isRaining(), "timer overshoot");
            w = new net.minecraft.world.level.saveddata.WeatherData(500, 7, 9, false, false);
            MiteSleep.forwardWeather(w, MiteSleep.RATE - 1);
            helper.assertTrue(w.getClearWeatherTime() == 500 - MiteSleep.RATE + 1 && w.getRainTime() == 7, "clear weather time");
            helper.assertTrue(MiteSleep.forwardsTime(helper.getLevel().getServer()), "no time forwarding off a dedicated server");
            helper.succeed();
        });

        // A-4: metabolism.sleep_min_hour=true refuses bed before 21:00 (tick 15000); false never does. Both sides of the hour are
        // checked on the pure rule, the event wiring at the current clock.
        MiteGameTests.add("metabolism_sleep_min_hour_config", 20, helper -> {
            helper.assertTrue(MiteSleep.tooEarly(14_999) && MiteSleep.tooEarly(24_000 + 6000), "before 21:00");
            helper.assertTrue(!MiteSleep.tooEarly(15_000) && !MiteSleep.tooEarly(23_999) && !MiteSleep.tooEarly(24_000 + 18_000), "from 21:00");
            ServerPlayer p = player(helper);
            BlockPos bed = helper.absolutePos(new BlockPos(3, 1, 3));
            boolean early = MiteSleep.tooEarly(helper.getLevel().getDefaultClockTime());
            var strict = MiteGameTests.withConfig(MiteConfig.SLEEP_MIN_HOUR, true, () -> NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(p, bed, null)).getProblem());
            var lax = MiteGameTests.withConfig(MiteConfig.SLEEP_MIN_HOUR, false, () -> NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(p, bed, null)).getProblem());
            helper.assertTrue((strict == MiteSleep.TOO_EARLY) == early, "too early with sleep_min_hour=true: " + strict + ", early " + early);
            helper.assertTrue(lax != MiteSleep.TOO_EARLY, "too early with sleep_min_hour=false");
            helper.succeed();
        });

        // EF-NUT-11: daytime naps go on (sleep_min_hour=false), but a sleeper whose bed is gone wakes up.
        MiteGameTests.add("metabolism_sleep_bed_broken", 20, helper -> {
            ServerPlayer p = player(helper);
            helper.setBlock(3, 1, 3, Blocks.STRAW_BED);
            p.setSleepingPos(helper.absolutePos(new BlockPos(3, 1, 3)));
            boolean nap = !MiteConfig.SLEEP_MIN_HOUR.get();
            helper.assertValueEqual(EventHooks.canEntityContinueSleeping(p, Player.BedSleepingProblem.OTHER_PROBLEM), nap, "bed still there");
            helper.setBlock(3, 1, 3, Blocks.AIR);
            helper.assertTrue(!EventHooks.canEntityContinueSleeping(p, Player.BedSleepingProblem.OTHER_PROBLEM), "bed gone: wakes");
            p.clearSleepingPos();
            helper.succeed();
        });

        // EF-NUT-11: starvation damage wakes the sleeper and ends the simulated night for them (no extra metabolism ticks awake).
        MiteGameTests.add("metabolism_sleep_starve_wakes", 20, helper -> {
            ServerPlayer p = player(helper);
            p.setPos(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            p.setSleepingPos(helper.absolutePos(new BlockPos(3, 1, 3)));
            Metabolism m = MetabolismService.get(p);
            m.satiety = 3;
            m.nutrition = 0;
            m.starveTimer = Formulas.STARVATION_INTERVAL - 2;
            int protein = m.protein;
            MiteSleep.sleep(p, MiteSleep.RATE, 0);
            helper.assertTrue(!p.isSleeping(), "woken by the starvation damage");
            helper.assertValueEqual(protein - m.protein, 2, "metabolism ticks run (the second one hurts)");
            helper.succeed();
        });

        // C-2: a melee hit that deals damage costs 0.3 (vanilla 0.1 replaced); a hit blocked by the target's i-frames costs nothing.
        MiteGameTests.add("metabolism_attack_cost", 20, helper -> {
            ServerPlayer p = player(helper);
            p.setPos(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
            Cow cow = helper.spawn(EntityTypes.COW, 2, 1, 1);
            float before = exhaustion(p);
            p.attack(cow);
            helper.assertTrue(Math.abs(exhaustion(p) - before - Formulas.HUNGER_ATTACK) < 1e-5, "hit cost " + (exhaustion(p) - before));
            before = exhaustion(p);
            p.attack(cow);
            helper.assertTrue(Math.abs(exhaustion(p) - before) < 1e-6, "no-damage hit cost " + (exhaustion(p) - before));
            helper.succeed();
        });

        // EF-NUT-02: a throw costs its hunger once; the flag travels with the projectile (portal, pearls re-created at login).
        MiteGameTests.add("metabolism_throw_charged_once", 20, helper -> {
            ServerPlayer p = player(helper);
            p.setPos(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            ServerLevel level = helper.getLevel();
            Snowball ball = new Snowball(level, p, new ItemStack(Items.SNOWBALL));
            float before = exhaustion(p);
            level.addFreshEntity(ball);
            helper.assertTrue(Math.abs(exhaustion(p) - before - Formulas.HUNGER_THROW) < 1e-5, "throw cost " + (exhaustion(p) - before));
            Snowball copy = new Snowball(level, p, new ItemStack(Items.SNOWBALL));
            copy.restoreFrom(ball);
            helper.assertTrue(copy.getPersistentData().getBooleanOr(MetabolismService.THROW_CHARGED, false), "the copy keeps the flag");
            Snowball again = new Snowball(level, p, new ItemStack(Items.SNOWBALL));
            again.getPersistentData().putBoolean(MetabolismService.THROW_CHARGED, true);
            before = exhaustion(p);
            level.addFreshEntity(again);
            helper.assertTrue(Math.abs(exhaustion(p) - before) < 1e-6, "re-joining throw cost " + (exhaustion(p) - before));
            ball.discard();
            again.discard();
            helper.succeed();
        });
    }

    /** The vanilla sprint check (protected), patched by PlayerMixin. */
    private static boolean sprintAllowed(Player p) {
        try {
            var method = Player.class.getDeclaredMethod("hasEnoughFoodToDoExhaustiveManoeuvres");
            method.setAccessible(true);
            return (boolean) method.invoke(p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The vanilla exhaustion accrued since the last food tick (FoodData keeps it private). */
    private static float exhaustion(ServerPlayer p) {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        p.getFoodData().addAdditionalSaveData(out);
        return out.buildResult().getFloatOr("foodExhaustionLevel", 0);
    }

    private static void eat(GameTestHelper helper, ServerPlayer p, Item item) {
        ItemStack stack = new ItemStack(item);
        EventHooks.onItemUseFinish(p, stack.copy(), 0, stack.finishUsingItem(helper.getLevel(), p));
    }
}
