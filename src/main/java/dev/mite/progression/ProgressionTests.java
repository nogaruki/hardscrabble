package dev.mite.progression;

import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import dev.mite.core.Metabolism;
import dev.mite.farming.FarmingContent;
import dev.mite.farming.FarmingRules;
import dev.mite.metabolism.MetabolismService;
import dev.mite.player.ProgressionService;
import dev.mite.dimension.UnderworldContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import dev.mite.treasure.LoreBooks;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** EF-UI-07 CA-1 and EF-DIM-02 CA-2: achievements granted by their trigger, and only by it (runegate: RunegateTests). */
public final class ProgressionTests {
    private ProgressionTests() {}

    private static boolean done(GameTestHelper h, ServerPlayer p, String name) {
        var adv = h.getLevel().getServer().getAdvancements().get(MiteMod.id(name));
        h.assertTrue(adv != null, "missing advancement " + name);
        return p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    private static void craft(ServerPlayer p, ItemLike item) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, new ItemStack(item), new SimpleContainer(9)));
    }

    private static void give(ServerPlayer p, ItemLike item) {
        ItemStack stack = new ItemStack(item);
        p.getInventory().add(stack.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(p, p.getInventory(), stack);
    }

    private static void wear(ServerPlayer p, String prefix) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MiteMod.item(prefix + "helmet")));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MiteMod.item(prefix + "chestplate")));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MiteMod.item(prefix + "leggings")));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(MiteMod.item(prefix + "boots")));
        CriteriaTriggers.LOCATION.trigger(p);
    }

    private static void event(ServerPlayer p, String name) {
        MiteTriggers.EVENT.get().trigger(p, name);
    }

    /** The player uses manure on the block at {@code rel}, as a right click would. */
    private static void manure(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos pos = h.absolutePos(rel);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FarmingContent.MANURE.get(), 4));
        FarmingContent.MANURE.get().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    /** dom-P-06: one action per achievement, firing the trigger its criterion listens to (code events: the named event). */
    private static final Map<String, BiConsumer<GameTestHelper, ServerPlayer>> ACTIONS = Map.ofEntries(
            Map.entry("root", (h, p) -> CriteriaTriggers.TICK.trigger(p)),
            Map.entry("stick_picker", (h, p) -> give(p, Items.STICK)),
            Map.entry("seeds", (h, p) -> give(p, Items.WHEAT_SEEDS)),
            Map.entry("eggs", (h, p) -> give(p, Items.EGG)),
            Map.entry("flint_finder", (h, p) -> give(p, Items.FLINT)),
            Map.entry("cutting_edge", (h, p) -> craft(p, MiteMod.item("flint_knife"))),
            Map.entry("mine_wood", (h, p) -> give(p, Items.BIRCH_LOG)),
            Map.entry("build_workbench", (h, p) -> craft(p, MiteMod.item("iron_workbench"))),
            Map.entry("build_club", (h, p) -> craft(p, MiteMod.item("wooden_cudgel"))),
            Map.entry("build_axe", (h, p) -> craft(p, MiteMod.item("silver_hatchet"))),
            Map.entry("build_shovel", (h, p) -> craft(p, MiteMod.item("mithril_shovel"))),
            Map.entry("nuggets", (h, p) -> give(p, Items.COPPER_NUGGET)),
            Map.entry("build_torches", (h, p) -> craft(p, Items.TORCH)),
            Map.entry("build_oven", (h, p) -> craft(p, MiteMod.item("clay_furnace"))),
            Map.entry("make_bread", (h, p) -> give(p, Items.BREAD)),
            Map.entry("flour", (h, p) -> give(p, FarmingContent.FLOUR.get())),
            Map.entry("fine_dining", (h, p) -> CriteriaTriggers.CONSUME_ITEM.trigger(p, new ItemStack(FarmingContent.BEEF_STEW.get()))),
            Map.entry("supersize_me", (h, p) -> event(p, "supersize_me")),
            Map.entry("plant_doctor", (h, p) -> event(p, "plant_doctor")),
            Map.entry("bake_cake", (h, p) -> craft(p, Items.CAKE)),
            Map.entry("build_hoe", (h, p) -> craft(p, MiteMod.item("mithril_hoe"))),
            Map.entry("build_scythe", (h, p) -> craft(p, MiteMod.item("iron_scythe"))),
            Map.entry("soil_enrichment", (h, p) -> {
                h.setBlock(1, 1, 1, Blocks.FARMLAND);
                manure(h, p, new BlockPos(1, 1, 1));
            }),
            Map.entry("kill_cow", (h, p) -> {
                Cow cow = h.spawn(EntityTypes.COW, new BlockPos(2, 1, 2));
                CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(p, cow, p.damageSources().playerAttack(p));
            }),
            Map.entry("build_pickaxe", (h, p) -> craft(p, MiteMod.item("silver_pickaxe"))),
            Map.entry("better_tools", (h, p) -> craft(p, MiteMod.item("copper_workbench"))),
            Map.entry("build_furnace", (h, p) -> craft(p, Items.FURNACE)),
            Map.entry("acquire_iron", (h, p) -> give(p, Items.IRON_INGOT)),
            Map.entry("obsidian_furnace", (h, p) -> craft(p, MiteMod.item("obsidian_furnace"))),
            Map.entry("mithril_ingot", (h, p) -> give(p, MiteMod.item("mithril_ingot"))),
            Map.entry("netherrack_furnace", (h, p) -> craft(p, MiteMod.item("netherrack_furnace"))),
            Map.entry("adamantium_ingot", (h, p) -> give(p, MiteMod.item("adamantium_ingot"))),
            Map.entry("wear_leather", (h, p) -> {
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                CriteriaTriggers.LOCATION.trigger(p);
            }),
            Map.entry("build_chain_mail", (h, p) -> craft(p, MiteMod.item("copper_chainmail_helmet"))),
            Map.entry("wear_all_plate_armor", (h, p) -> wear(p, "silver_")),
            Map.entry("wear_all_adamantium_plate_armor", (h, p) -> wear(p, "adamantium_")),
            Map.entry("fishing_rod", (h, p) -> craft(p, MiteMod.item("flint_fishing_rod"))),
            Map.entry("crystal_breaker", (h, p) -> NeoForge.EVENT_BUS.post(new BreakBlockEvent(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)),
                    Blocks.EMERALD_ORE.defaultBlockState(), p))),
            Map.entry("diamonds", (h, p) -> give(p, Items.DIAMOND)),
            Map.entry("emeralds", (h, p) -> give(p, Items.EMERALD)),
            Map.entry("bookshelves", (h, p) -> craft(p, Items.BOOKSHELF)),
            Map.entry("enchanter", (h, p) -> CriteriaTriggers.ENCHANTED_ITEM.trigger(p, new ItemStack(MiteMod.item("copper_hatchet")), 1)),
            Map.entry("seaworthy", (h, p) -> craft(p, Items.SPRUCE_BOAT)),
            Map.entry("portal", (h, p) -> CriteriaTriggers.CHANGED_DIMENSION.trigger(p, Level.OVERWORLD, UnderworldContent.LEVEL)),
            Map.entry("portal_to_nether", (h, p) -> CriteriaTriggers.CHANGED_DIMENSION.trigger(p, UnderworldContent.LEVEL, Level.NETHER)),
            Map.entry("runegate", (h, p) -> event(p, "runegate")),
            Map.entry("kill_dragon", (h, p) -> CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(p,
                    EntityTypes.ENDER_DRAGON.create(h.getLevel(), EntitySpawnReason.COMMAND), p.damageSources().playerAttack(p))),
            Map.entry("explorer", (h, p) -> {
                BlockPos spawn = h.getLevel().getRespawnData().pos();
                p.setPos(spawn.getX() + 10_001, 100, spawn.getZ());
                MiteAdvancements.checkExplorer(p);
            }),
            Map.entry("well_rested", (h, p) -> event(p, "well_rested")),
            Map.entry("enlightenment", (h, p) -> event(p, LoreBooks.ACHIEVEMENT)));

    public static void register() {
        // dom-P-06: every achievement (EF-UI-07 CA-1), one test each, done by its action and not before.
        for (String[] a : MiteAdvancements.TEXT) {
            String name = a[0];
            MiteGameTests.add("progression_achievement_" + name, 20, h -> {
                var action = ACTIONS.get(name);
                h.assertTrue(action != null, "no test action for " + name);
                ServerPlayer p = MetabolismTests.player(h);
                h.assertFalse(done(h, p, name), name + " before its trigger");
                action.accept(h, p);
                h.assertTrue(done(h, p, name), name + " after its trigger");
                h.succeed();
            });
        }

        // dom-P-02 (changelog l.753): a mattock counts as a hoe; dom-P-05: the rewritten vanilla story advancements.
        MiteGameTests.add("progression_mattock_and_story", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            craft(p, MiteMod.item("copper_mattock"));
            h.assertTrue(done(h, p, "build_hoe"), "build_hoe from a copper mattock");
            var server = h.getLevel().getServer();
            var upgrade = server.getAdvancements().get(Identifier.withDefaultNamespace("story/upgrade_tools"));
            var shiny = server.getAdvancements().get(Identifier.withDefaultNamespace("story/shiny_gear"));
            h.assertTrue(upgrade != null && shiny != null, "story advancements loaded");
            give(p, MiteMod.item("silver_pickaxe"));
            h.assertTrue(p.getAdvancements().getOrStartProgress(upgrade).isDone(), "upgrade_tools from a silver pickaxe");
            h.assertFalse(p.getAdvancements().getOrStartProgress(shiny).isDone(), "shiny_gear before mithril armor");
            give(p, MiteMod.item("mithril_boots"));
            h.assertTrue(p.getAdvancements().getOrStartProgress(shiny).isDone(), "shiny_gear from mithril boots");
            h.succeed();
        });

        MiteGameTests.add("progression_crafting_achievements", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.assertFalse(done(h, p, "build_axe"), "build_axe before crafting");
            craft(p, Items.STICK);
            h.assertFalse(done(h, p, "build_axe"), "build_axe from a stick");
            craft(p, MiteMod.item("flint_hatchet"));
            h.assertTrue(done(h, p, "build_axe"), "build_axe after a flint hatchet");
            craft(p, Items.CRAFTING_TABLE);
            h.assertTrue(done(h, p, "build_workbench"), "build_workbench after a crafting table");
            h.assertFalse(done(h, p, "better_tools"), "better_tools from a flint bench");
            h.succeed();
        });

        MiteGameTests.add("progression_portal_achievements", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            CriteriaTriggers.CHANGED_DIMENSION.trigger(p, Level.OVERWORLD, Level.NETHER);
            h.assertFalse(done(h, p, "portal") || done(h, p, "portal_to_nether"), "portal achievements from the Overworld to the Nether");
            CriteriaTriggers.CHANGED_DIMENSION.trigger(p, Level.OVERWORLD, UnderworldContent.LEVEL);
            h.assertTrue(done(h, p, "portal"), "portal after entering the Underworld");
            CriteriaTriggers.CHANGED_DIMENSION.trigger(p, UnderworldContent.LEVEL, Level.NETHER);
            h.assertTrue(done(h, p, "portal_to_nether"), "portalToNether after Underworld -> Nether");
            h.succeed();
        });

        // flour, fineDining, supersizeMe: obtained, eaten, full at the maximum cap of 20.
        MiteGameTests.add("progression_food_achievements", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ItemStack flour = new ItemStack(FarmingContent.FLOUR.get());
            p.getInventory().add(flour.copy());
            CriteriaTriggers.INVENTORY_CHANGED.trigger(p, p.getInventory(), flour);
            h.assertTrue(done(h, p, "flour"), "flour");
            CriteriaTriggers.CONSUME_ITEM.trigger(p, new ItemStack(Items.BREAD));
            h.assertFalse(done(h, p, "fine_dining"), "fine_dining from bread");
            CriteriaTriggers.CONSUME_ITEM.trigger(p, new ItemStack(FarmingContent.BEEF_STEW.get()));
            h.assertTrue(done(h, p, "fine_dining"), "fine_dining from a beef stew");
            ProgressionService.setXp(p, Formulas.xpForLevel(35)); // food cap 20
            Metabolism m = MetabolismService.get(p);
            m.satiety = m.nutrition = 0;
            MetabolismService.eat(p, new ItemStack(FarmingContent.BEEF_STEW.get()));
            h.assertFalse(done(h, p, "supersize_me"), "supersize_me at 16");
            MetabolismService.eat(p, new ItemStack(FarmingContent.BEEF_STEW.get()));
            h.assertTrue(done(h, p, "supersize_me"), "supersize_me at 20");
            h.succeed();
        });

        // plantDoctor: bone meal on a blighted crop; crystalBreaker: a diamond ore broken, not stone.
        MiteGameTests.add("progression_plant_doctor_crystal_breaker", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(1, 1, 1, Blocks.FARMLAND);
            h.setBlock(1, 2, 1, Blocks.WHEAT);
            BlockPos crop = h.absolutePos(new BlockPos(1, 2, 1));
            BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), h.getLevel(), crop, p);
            h.assertFalse(done(h, p, "plant_doctor"), "plant_doctor on a healthy crop");
            FarmingRules.infect(h.getLevel(), crop);
            BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), h.getLevel(), crop, p);
            h.assertTrue(done(h, p, "plant_doctor"), "plant_doctor on a blighted crop");
            NeoForge.EVENT_BUS.post(new BreakBlockEvent(h.getLevel(), crop, Blocks.STONE.defaultBlockState(), p));
            h.assertFalse(done(h, p, "crystal_breaker"), "crystal_breaker from stone");
            NeoForge.EVENT_BUS.post(new BreakBlockEvent(h.getLevel(), crop, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState(), p));
            h.assertTrue(done(h, p, "crystal_breaker"), "crystal_breaker from a diamond ore");
            h.succeed();
        });

        // P-1 (changelog l.749): Soil Enrichment needs manure on farmland; bone meal or manure on another block does not count.
        MiteGameTests.add("progression_soil_enrichment_manure", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(1, 1, 1, Blocks.GRASS_BLOCK);
            BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)), p);
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(p, h.absolutePos(new BlockPos(1, 1, 1)), new ItemStack(Items.BONE_MEAL));
            manure(h, p, new BlockPos(1, 1, 1));
            h.assertFalse(done(h, p, "soil_enrichment"), "soil_enrichment from bone meal or manure on grass");
            h.setBlock(3, 1, 3, Blocks.FARMLAND);
            h.setBlock(3, 2, 3, Blocks.WHEAT);
            manure(h, p, new BlockPos(3, 2, 3)); // on the crop: manures the farmland under it
            h.assertTrue(done(h, p, "soil_enrichment"), "soil_enrichment from manure on farmland");
            h.succeed();
        });

        MiteGameTests.add("progression_explorer", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            BlockPos spawn = h.getLevel().getRespawnData().pos();
            p.setPos(spawn.getX() + 9000, 100, spawn.getZ() + 4000);
            MiteAdvancements.checkExplorer(p);
            h.assertFalse(done(h, p, "explorer"), "explorer at 9.8 km");
            p.setPos(spawn.getX() + 9000, 100, spawn.getZ() + 4400);
            MiteAdvancements.checkExplorer(p);
            h.assertTrue(done(h, p, "explorer"), "explorer at 10 km");
            h.succeed();
        });
    }
}
