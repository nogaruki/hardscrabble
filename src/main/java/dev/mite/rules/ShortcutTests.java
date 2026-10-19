package dev.mite.rules;

import dev.mite.combat.Archery;
import dev.mite.combat.CombatContent;
import dev.mite.combat.CombatFormulas;
import dev.mite.combat.MobGear;
import dev.mite.combat.PostDragon;
import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.core.Quality;
import dev.mite.core.ToolType;
import dev.mite.farming.FarmingRules;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.metallurgy.Campfires;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** GameTests of audit 2 lot B: modern vanilla shortcuts (golems, bees, happy ghast, portals, spear, trident, crossbow, totem, campfire). */
public final class ShortcutTests {
    private ShortcutTests() {}

    /** Items dropped by {@code rolls} deaths of {@code victim}, killed by {@code killer} (null: not by a player). */
    private static int count(GameTestHelper h, LivingEntity victim, ServerPlayer killer, Item item, int rolls) {
        LootTable table = h.getLevel().getServer().reloadableRegistries().getLootTable(victim.getLootTable().orElseThrow());
        LootParams.Builder b = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position()).withParameter(LootContextParams.DAMAGE_SOURCE, h.getLevel().damageSources().generic());
        if (killer != null) b.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
        LootParams params = b.create(LootContextParamSets.ENTITY);
        int n = 0;
        for (int i = 0; i < rolls; i++) for (ItemStack s : table.getRandomItems(params)) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void killDragon(ServerPlayer player) {
        var dragon = player.level().getServer().getAdvancements().get(PostDragon.KILL_DRAGON);
        dragon.value().criteria().keySet().forEach(c -> player.getAdvancements().award(dragon, c));
    }

    public static void register() {
        // X4 (owner decision): one iron nugget per golem, and only for a player kill (no lava or fall farm).
        MiteGameTests.add("shortcut_golem_one_nugget", 20, h -> {
            var golem = h.spawn(EntityTypes.IRON_GOLEM, 2, 1, 2);
            ServerPlayer p = MetabolismTests.player(h);
            h.assertValueEqual(count(h, golem, p, Items.IRON_NUGGET, 50), 50, "nuggets of 50 player-killed golems");
            h.assertValueEqual(count(h, golem, null, Items.IRON_NUGGET, 50), 0, "nuggets of 50 golems killed by the environment");
            h.assertValueEqual(count(h, golem, p, Items.IRON_INGOT, 50), 0, "ingots");
            h.succeed();
        });
        // m6 / arbitrage n°20 ("evokers, rare"): the totem drops for a player kill only, about 1 time in 4.
        MiteGameTests.add("shortcut_evoker_totem", 20, h -> {
            var evoker = h.spawn(EntityTypes.EVOKER, 2, 1, 2);
            ServerPlayer p = MetabolismTests.player(h);
            int totems = count(h, evoker, p, Items.TOTEM_OF_UNDYING, 400);
            h.assertTrue(totems >= 60 && totems <= 140, "totems of 400 player-killed evokers: " + totems);
            h.assertValueEqual(count(h, evoker, null, Items.TOTEM_OF_UNDYING, 100), 0, "totems without a player kill");
            h.succeed();
        });
        // X6: bees no longer grow what bone meal may not grow (R128): crops, onions, stems, berries; cave vines stay.
        MiteGameTests.add("shortcut_bees_respect_r128", 20, h -> {
            BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.BEE_GROWABLES).forEach(b -> h.assertFalse(FarmingRules.refusesBonemeal(b.value()),
                    b.getRegisteredName() + " grown by bees"));
            for (Block b : List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.MELON_STEM, Blocks.SWEET_BERRY_BUSH, dev.mite.farming.FarmingContent.ONIONS.get()))
                h.assertFalse(b.defaultBlockState().is(BlockTags.BEE_GROWABLES), b + " is a bee growable");
            h.assertFalse(Blocks.CAVE_VINES.defaultBlockState().is(BlockTags.BEE_GROWABLES), "cave vines (glow berries refuse bone meal, R128)");
            h.succeed();
        });
        // X7 (owner decision, like the mace): the dried ghast is post-Dragon loot, craft and ride.
        MiteGameTests.add("shortcut_happy_ghast_post_dragon", 40, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            ItemStack dried = new ItemStack(Items.DRIED_GHAST);
            h.assertTrue(dried.is(CombatContent.POST_DRAGON), "dried ghast in hardscrabble:post_dragon (loot gate)");
            h.assertTrue(PostDragon.craftRefusal(p, dried) != null, "dried ghast crafted before the Dragon");
            h.assertTrue(PostDragon.craftRefusal(p, new ItemStack(Items.GHAST_TEAR)) == null, "other crafts refused");
            var ghast = h.spawn(EntityTypes.HAPPY_GHAST, 3, 2, 3);
            ghast.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.white()));
            h.assertFalse(p.startRiding(ghast, true, false), "happy ghast ridden before the Dragon");
            killDragon(p);
            h.assertTrue(PostDragon.craftRefusal(p, dried) == null, "dried ghast refused after the Dragon");
            h.assertTrue(p.startRiding(ghast, true, false), "happy ghast refused after the Dragon");
            p.stopRiding();
            h.succeed();
        });
        // m2 / G-1 (arbitrage n°22): the spear reaches 3 like the other weapons and its charge follows the MITE table.
        MiteGameTests.add("shortcut_spear_mite", 20, h -> {
            for (var e : List.of(java.util.Map.entry(Items.COPPER_SPEAR, Material.COPPER), java.util.Map.entry(Items.IRON_SPEAR, Material.IRON),
                    java.util.Map.entry(Items.GOLDEN_SPEAR, Material.GOLD))) {
                ItemStack spear = new ItemStack(e.getKey());
                var range = spear.get(DataComponents.ATTACK_RANGE);
                h.assertTrue(range != null && range.maxReach() == CombatFormulas.SPEAR_REACH, e.getKey() + " reach " + range);
                float mult = spear.get(DataComponents.KINETIC_WEAPON).damageMultiplier();
                int charge = (int) CombatFormulas.PLAYER_BASE_DAMAGE + Mth.floor(CombatFormulas.CHARGE_REFERENCE_SPEED * mult);
                int mite = CombatFormulas.damage(ToolType.SPEAR, e.getValue());
                h.assertTrue(charge >= mite && charge <= mite + 1, e.getKey() + " charge " + charge + " for MITE damage " + mite);
                h.assertTrue(charge < CombatFormulas.damage(ToolType.SWORD, e.getValue()), e.getKey() + " charge beats the sword");
            }
            h.succeed();
        });
        // m1: the trident lasts and wears like an iron spear, thrown or not.
        MiteGameTests.add("shortcut_trident_wear", 20, h -> {
            ItemStack trident = new ItemStack(Items.TRIDENT);
            h.assertValueEqual(trident.getMaxDamage(), Formulas.toolDurability(ToolType.SPEAR, Material.IRON, Quality.AVERAGE), "trident durability");
            h.assertValueEqual(trident.get(DataComponents.WEAPON).itemDamagePerAttack(), Formulas.attackWear(ToolType.SPEAR), "trident wear per hit");
            Archery.wearThrown(trident);
            h.assertValueEqual(trident.getDamageValue(), Formulas.attackWear(ToolType.SPEAR) - 1, "wear of a throw");
            trident.setDamageValue(trident.getMaxDamage() - 2);
            Archery.wearThrown(trident);
            h.assertValueEqual(trident.getDamageValue(), trident.getMaxDamage() - 1, "a throw never breaks the trident");
            h.succeed();
        });
        // m3 / G-2: pillagers and piglins never drop their crossbow; m8: the drowned's copper ingot becomes a nugget.
        MiteGameTests.add("shortcut_mob_iron_age_drops", 20, h -> {
            var pillager = h.spawn(EntityTypes.PILLAGER, 2, 1, 2);
            pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            MobGear.equip(pillager, 0, pillager.getRandom());
            h.assertValueEqual(pillager.getDropChances().byEquipment(EquipmentSlot.MAINHAND), 0f, "pillager crossbow drop chance");
            var drowned = h.spawn(EntityTypes.DROWNED, 4, 1, 4);
            var drop = new ItemEntity(h.getLevel(), drowned.getX(), drowned.getY(), drowned.getZ(), new ItemStack(Items.COPPER_INGOT));
            NeoForge.EVENT_BUS.post(new LivingDropsEvent(drowned, drowned.damageSources().generic(), new ArrayList<>(List.of(drop)), true));
            h.assertTrue(drop.getItem().is(Items.COPPER_NUGGET), "drowned copper ingot became " + drop.getItem());
            h.succeed();
        });
        // m5 / arbitrage n°11, changelog l.764 (R119): charcoal campfire; each food added beside others may put it out.
        MiteGameTests.add("shortcut_campfire", 20, h -> {
            var recipes = h.getLevel().getServer().getRecipeManager();
            h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("campfire"))).isEmpty(), "vanilla campfire recipe");
            var recipe = (CraftingRecipe) recipes.byKey(ResourceKey.create(Registries.RECIPE, dev.mite.MiteMod.id("campfire"))).orElseThrow().value();
            ItemStack log = new ItemStack(Items.OAK_LOG), stick = new ItemStack(Items.STICK);
            for (Item fuel : List.of(Items.CHARCOAL, Items.COAL)) {
                CraftingInput grid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, stick, ItemStack.EMPTY, stick, new ItemStack(fuel), stick, log, log, log));
                h.assertValueEqual(recipe.matches(grid, h.getLevel()), fuel == Items.CHARCOAL, "campfire from " + fuel);
            }
            BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
            h.getLevel().setBlockAndUpdate(pos, Blocks.CAMPFIRE.defaultBlockState());
            var campfire = (CampfireBlockEntity) h.getLevel().getBlockEntity(pos);
            h.assertFalse(Campfires.crowd(h.getLevel(), pos, null, 0f), "an empty campfire goes out");
            campfire.placeFood(h.getLevel(), null, new ItemStack(Items.BEEF));
            h.assertFalse(Campfires.crowd(h.getLevel(), pos, null, Campfires.EXTINGUISH_PER_ITEM + 0.01f), "2nd food above its chance");
            campfire.placeFood(h.getLevel(), null, new ItemStack(Items.BEEF));
            h.assertTrue(h.getLevel().getBlockState(pos).getValue(CampfireBlock.LIT), "still lit");
            h.assertTrue(Campfires.crowd(h.getLevel(), pos, null, 2 * Campfires.EXTINGUISH_PER_ITEM - 0.01f), "3rd food within its chance");
            h.assertFalse(h.getLevel().getBlockState(pos).getValue(CampfireBlock.LIT), "fire out");
            h.succeed();
        });
    }
}
