package dev.mite.combat;

import static dev.mite.core.Material.*;

import dev.mite.core.Material;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** GameTests for EF-CBT-04..08, 11, EF-MET-09, EF-IA-05 and the modern weapons (M11, M12). */
public final class CombatTests2 {
    private CombatTests2() {}

    public static void register() {
        // A-4 (EF-CFG-02 CA-1): silver_undead_bonus=0 removes the silver bonus; 1 doubles the damage.
        MiteGameTests.add("config_silver_undead_bonus", 20, h -> {
            float copper = arrowHit(h, Archery.arrow(COPPER), 1);
            float none = MiteGameTests.withConfig(dev.mite.config.MiteConfig.SILVER_UNDEAD_BONUS, 0.0, () -> arrowHit(h, Archery.arrow(SILVER), 3));
            float doubled = MiteGameTests.withConfig(dev.mite.config.MiteConfig.SILVER_UNDEAD_BONUS, 1.0, () -> arrowHit(h, Archery.arrow(SILVER), 5));
            h.assertTrue(Math.abs(none / copper - 1) < 1e-3, "silver bonus at 0: " + none + " / " + copper);
            h.assertTrue(Math.abs(doubled / copper - 2) < 1e-3, "silver bonus at 1: " + doubled + " / " + copper);
            h.succeed();
        });

        // EF-CBT-04 CA-1: an iron chestplate worn to 75 % gives half its protection; a mob gets a fixed 0.5 factor.
        MiteGameTests.add("combat_worn_armor", 40, h -> {
            ArmorStand fresh = h.spawn(EntityTypes.ARMOR_STAND, 1, 1, 1);
            ArmorStand worn = h.spawn(EntityTypes.ARMOR_STAND, 3, 1, 3);
            fresh.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
            stack.setDamageValue(stack.getMaxDamage() * 3 / 4);
            worn.setItemSlot(EquipmentSlot.CHEST, stack);
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
            zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            double zombieBase = zombie.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            h.succeedWhen(() -> {
                double full = CombatFormulas.pieceProtection(CombatFormulas.CHESTPLATE, IRON, false);
                h.assertTrue(Math.abs(fresh.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - full) < 1e-6, "fresh chestplate");
                h.assertTrue(Math.abs(worn.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - full / 2) < 1e-6, "75 % worn chestplate");
                double mob = zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) - zombieBase;
                h.assertTrue(Math.abs(mob - full / 2) < 1e-6, "zombie chestplate " + mob);
            });
        });
        // EF-CBT-05 CA-1: a full silver chainmail set gives 5 points, and every metal has its 4 chainmail pieces with a recipe.
        MiteGameTests.add("combat_chainmail", 40, h -> {
            ArmorStand stand = h.spawn(EntityTypes.ARMOR_STAND, 2, 1, 2);
            for (ArmorType t : CombatContent.PIECES) stand.setItemSlot(t.getSlot(), new ItemStack(CombatContent.armorItem(SILVER, t, true)));
            var recipes = h.getLevel().getServer().getRecipeManager().getRecipes();
            for (Material m : List.of(COPPER, SILVER, GOLD, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) {
                for (ArmorType t : CombatContent.PIECES) {
                    Item piece = CombatContent.armorItem(m, t, true);
                    h.assertTrue(recipes.stream().anyMatch(r -> produces(r, h.getLevel(), piece)), "recipe for " + piece);
                }
            }
            h.succeedWhen(() -> h.assertValueEqual(stand.getArmorValue(), 5, "silver chainmail set armor"));
        });
        // EF-CBT-08: arrow base damage = 0.5 + tip x 0.5 (flint is the vanilla arrow).
        MiteGameTests.add("combat_arrow_damage", 20, h -> {
            h.assertValueEqual(baseDamage(h, Items.ARROW), 1.0, "flint arrow");
            h.assertValueEqual(baseDamage(h, Archery.arrow(OBSIDIAN)), 1.5, "obsidian arrow");
            h.assertValueEqual(baseDamage(h, Archery.arrow(COPPER)), 2.0, "copper arrow");
            h.assertValueEqual(baseDamage(h, Archery.arrow(IRON)), 2.5, "iron arrow");
            h.assertValueEqual(baseDamage(h, Archery.arrow(ANCIENT_METAL)), 2.5, "ancient metal arrow");
            h.assertValueEqual(baseDamage(h, Archery.arrow(ADAMANTIUM)), 3.5, "adamantium arrow");
            h.assertTrue(new ItemStack(Archery.arrow(MITHRIL)).is(net.minecraft.tags.ItemTags.ARROWS), "mithril arrow is an arrow");
            h.succeed();
        });
        // EF-CBT-07 CA-1: a mithril bow fires x1.25 faster than a wooden bow; bow durabilities.
        MiteGameTests.add("combat_bows", 20, h -> {
            Zombie shooter = h.spawn(EntityTypes.ZOMBIE, 3, 1, 3);
            Arrow wood = new Arrow(h.getLevel(), 0, 2, 0, new ItemStack(Items.ARROW), null);
            Arrow mithril = new Arrow(h.getLevel(), 0, 2, 0, new ItemStack(Items.ARROW), null);
            Arrow ancient = new Arrow(h.getLevel(), 0, 2, 0, new ItemStack(Items.ARROW), null);
            wood.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0, 3, 0);
            Archery.MITHRIL_BOW.get().shootProjectile(shooter, mithril, 0, 3, 0, 0, null);
            Archery.ANCIENT_METAL_BOW.get().shootProjectile(shooter, ancient, 0, 3, 0, 0, null);
            double base = wood.getDeltaMovement().length();
            h.assertTrue(Math.abs(mithril.getDeltaMovement().length() / base - 1.25) < 1e-3, "mithril bow speed");
            h.assertTrue(Math.abs(ancient.getDeltaMovement().length() / base - 1.1) < 1e-3, "ancient metal bow speed");
            h.assertValueEqual(new ItemStack(Items.BOW).getMaxDamage(), 32, "wooden bow durability");
            h.assertValueEqual(new ItemStack(Archery.MITHRIL_BOW.get()).getMaxDamage(), 128, "mithril bow durability");
            h.succeed();
        });
        // EF-CBT-06 / EF-CBT-08: a silver arrow deals +25 % to undead.
        MiteGameTests.add("combat_silver_arrow", 20, h -> {
            float silver = arrowHit(h, Archery.arrow(SILVER), 1), copper = arrowHit(h, Archery.arrow(COPPER), 4);
            h.assertTrue(Math.abs(silver / copper - 1.25f) < 1e-3, "silver arrow bonus " + silver + " / " + copper);
            h.succeed();
        });
        // EF-MET-09 / EF-IA-05: mob iron gear spawns rusted, skeletons get rusted blades after day 10, and no recipe makes rusted iron.
        MiteGameTests.add("combat_rusted_iron", 20, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            MobGear.equip(zombie, 1, RandomSource.create(0));
            h.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(CombatContent.armorItem(RUSTED_IRON, ArmorType.HELMET, false)), "rusted helmet");
            h.assertTrue(zombie.getItemBySlot(EquipmentSlot.CHEST).is(CombatContent.armorItem(RUSTED_IRON, ArmorType.CHESTPLATE, true)), "rusted chainmail");
            h.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).is(CombatContent.RUSTED_IRON_SWORD.get()), "rusted sword");
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 4, 1, 4);
            RandomSource random = RandomSource.create(1);
            boolean early = false, late = false, club = false;
            Item woodenClub = dev.mite.harvest.HarvestContent.item(dev.mite.core.ToolType.CLUB, WOOD);
            for (int i = 0; i < 400; i++) {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                MobGear.equip(skeleton, MobGear.SKELETON_BLADE_DAY, random);
                early |= !skeleton.getMainHandItem().is(Items.BOW) && !skeleton.getMainHandItem().is(woodenClub);
                club |= skeleton.getMainHandItem().is(woodenClub);
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                MobGear.equip(skeleton, MobGear.SKELETON_BLADE_DAY + 1, random);
                late |= skeleton.getMainHandItem().is(CombatContent.RUSTED_IRON_DAGGER.get()) || skeleton.getMainHandItem().is(CombatContent.RUSTED_IRON_SWORD.get());
            }
            h.assertFalse(early, "no blade before day 11");
            h.assertTrue(club, "melee skeletons carry a wooden club (K-4, l.115)");
            h.assertTrue(late, "a skeleton gets a rusted blade after day 10");
            List<Item> rusted = CombatContent.rustedItems();
            var bad = h.getLevel().getServer().getRecipeManager().getRecipes().stream()
                    .filter(r -> !(r.value() instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe)) // a trim keeps its input piece
                    .filter(r -> rusted.stream().anyMatch(i -> produces(r, h.getLevel(), i))).map(r -> r.id().identifier().toString()).toList();
            h.assertTrue(bad.isEmpty(), "no recipe makes rusted iron: " + bad);
            h.succeed();
        });
        // EF-CBT-11 CA-1 / dom-G-06: the vanilla shield recipe (1 ingot) is gone, the MITE one costs more iron than 9 nuggets.
        MiteGameTests.add("combat_shield", 20, h -> {
            var recipes = h.getLevel().getServer().getRecipeManager();
            h.assertTrue(recipes.byKey(recipe("minecraft:shield")).isEmpty(), "vanilla shield recipe removed");
            var shield = (net.minecraft.world.item.crafting.ShapedRecipe) recipes.byKey(recipe("hardscrabble:shield")).orElseThrow().value();
            int nuggets = shield.getIngredients().stream().flatMap(java.util.Optional::stream)
                    .mapToInt(i -> i.test(new ItemStack(Items.IRON_INGOT)) ? 9 : i.test(new ItemStack(Items.IRON_NUGGET)) ? 1 : 0).sum();
            h.assertTrue(nuggets > 9, "shield iron cost " + nuggets + " nuggets");
            h.succeed();
        });
        // dom-G-11: the vanilla 4-arrow recipe is gone; flint + stick + feather = 1 arrow, recycled into its flint.
        MiteGameTests.add("combat_flint_arrow", 20, h -> {
            var recipes = h.getLevel().getServer().getRecipeManager();
            h.assertTrue(recipes.byKey(recipe("minecraft:arrow")).isEmpty(), "vanilla arrow recipe removed");
            var arrow = recipes.byKey(recipe("hardscrabble:arrow")).orElseThrow().value();
            ItemStack result = arrow.display().getFirst().result().resolveForFirstStack(
                    net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(h.getLevel()));
            h.assertTrue(result.is(Items.ARROW) && result.getCount() == 1, "1 flint arrow per craft: " + result);
            h.assertTrue(recipes.byKey(recipe("hardscrabble:flint_from_arrow")).isPresent(), "flint arrow recycling");
            h.succeed();
        });
        // M12: the iron spear has MITE durability and damage, the trident hits like it, the crossbow never outlasts a metal bow.
        MiteGameTests.add("combat_modern_weapons", 20, h -> {
            h.assertTrue(dev.mite.harvest.ToolStats.of(new ItemStack(Items.IRON_SPEAR)).isPresent(), "iron spear in the MITE table");
            h.assertValueEqual(new ItemStack(Items.IRON_SPEAR).getMaxDamage(),
                    dev.mite.core.Formulas.toolDurability(dev.mite.core.ToolType.SPEAR, IRON, dev.mite.core.Quality.AVERAGE), "iron spear durability");
            h.assertValueEqual(new ItemStack(Items.IRON_SPEAR).getMaxDamage(), 3_200, "iron spear durability x100");
            h.assertValueEqual(CombatTests.damage(Items.IRON_SPEAR), 6.0, "iron spear damage");
            h.assertValueEqual(CombatTests.damage(Items.COPPER_SPEAR), 5.0, "copper spear damage");
            h.assertValueEqual(CombatTests.damage(Items.TRIDENT), 6.0, "trident damage");
            h.assertTrue(new ItemStack(Items.CROSSBOW).getMaxDamage() <= new ItemStack(Archery.ANCIENT_METAL_BOW.get()).getMaxDamage(), "crossbow durability");
            h.succeed();
        });
        // M11 / EF-CBT-06 CA-2: a silver arrow hurts a Shadow; a flint arrow from an enchanted bow does not.
        MiteGameTests.add("combat_silver_arrow_shadow", 20, h -> {
            var level = h.getLevel();
            var shadow = h.spawn(dev.mite.entity.mob.MobContent.SHADOW.get(), 2, 1, 2);
            shadow.setNoAi(true);
            h.setBlock(2, 3, 2, net.minecraft.world.level.block.Blocks.STONE); // shade from the sun
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER), 1);
            Arrow flint = new Arrow(level, 0, 0, 0, new ItemStack(Items.ARROW), bow);
            h.assertFalse(shadow.hurtServer(level, level.damageSources().arrow(flint, null), 4), "flint arrow from an enchanted bow hurts the shadow");
            Arrow silver = new Arrow(level, 0, 0, 0, new ItemStack(Archery.arrow(SILVER)), new ItemStack(Items.BOW));
            h.assertTrue(shadow.hurtServer(level, level.damageSources().arrow(silver, null), 4), "silver arrow spares the shadow");
            h.succeed();
        });
        // EF-CBT-08 CA-1: 1000 flint arrows hitting a block (impact event) keep about 30 %; a survivor that lands again (its block
        // broken) is not rolled twice; unpickable arrows and tridents never break; tipped and spectral arrows count as flint.
        MiteGameTests.add("combat_arrow_block_impact", 20, h -> {
            var level = h.getLevel();
            BlockHitResult floor = new BlockHitResult(h.absoluteVec(new Vec3(2.5, 1, 2.5)), Direction.UP, h.absolutePos(new BlockPos(2, 0, 2)), false);
            int broken = 0, brokenOnSecondLanding = 0;
            for (int i = 0; i < 1000; i++) {
                Arrow arrow = new Arrow(level, 0, 0, 0, new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.ALLOWED;
                EventHooks.onProjectileImpact(arrow, floor);
                if (arrow.isRemoved()) {
                    broken++;
                    continue;
                }
                EventHooks.onProjectileImpact(arrow, floor);
                if (arrow.isRemoved()) brokenOnSecondLanding++;
            }
            h.assertTrue(Math.abs(broken / 1000f - (1 - CombatFormulas.arrowRecovery(FLINT))) < 0.05f, "flint arrows broken on a block " + broken + "/1000");
            h.assertValueEqual(brokenOnSecondLanding, 0, "survivors rolled again on a second landing");
            for (int i = 0; i < 100; i++) {
                Arrow arrow = new Arrow(level, 0, 0, 0, new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                EventHooks.onProjectileImpact(arrow, floor);
                h.assertFalse(arrow.isRemoved(), "an unpickable arrow broke");
                ThrownTrident trident = new ThrownTrident(level, 0, 0, 0, new ItemStack(Items.TRIDENT));
                trident.pickup = AbstractArrow.Pickup.ALLOWED;
                EventHooks.onProjectileImpact(trident, floor);
                h.assertFalse(trident.isRemoved(), "a trident broke on a block");
            }
            for (Item item : List.of(Items.TIPPED_ARROW, Items.SPECTRAL_ARROW)) {
                h.assertTrue(Archery.tip(new ItemStack(item)) == FLINT, item + " counts as flint");
                h.assertValueEqual(baseDamage(h, item), CombatFormulas.arrowDamage(FLINT), item + " base damage");
            }
            h.succeed();
        });
        // EF-IA-05: the spawn rules run once per mob. A portal trip (copy rebuilt from the save, joining as new) or a conversion
        // keeps a picked-up iron sword; a thrown trident is worn once even if it crosses a portal.
        MiteGameTests.add("combat_spawn_rules_once", 20, h -> {
            var level = h.getLevel();
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
            zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); // picked up from a dead player
            Zombie travelled = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.DIMENSION_TRAVEL);
            travelled.restoreFrom(zombie); // what Entity#teleportCrossDimension does before adding the copy
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(travelled, level, false));
            h.assertTrue(travelled.getMainHandItem().is(Items.IRON_SWORD), "iron sword after a portal trip, got " + travelled.getMainHandItem());
            Drowned drowned = zombie.convertTo(EntityTypes.DROWNED, ConversionParams.single(zombie, true, true), d -> EventHooks.onLivingConvert(zombie, d));
            h.assertTrue(drowned != null && drowned.getMainHandItem().is(Items.IRON_SWORD), "iron sword after drowning");
            ThrownTrident trident = new ThrownTrident(level, MetabolismTests.player(h), new ItemStack(Items.TRIDENT));
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(trident, level, false));
            int worn = trident.getPickupItemStackOrigin().getDamageValue();
            h.assertTrue(worn > 0, "a throw wears the trident");
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(trident, level, false));
            h.assertValueEqual(trident.getPickupItemStackOrigin().getDamageValue(), worn, "trident worn again after a portal");
            h.succeed();
        });
        // EF-IA-05: the loot ingot of a zombie becomes a nugget; a stack it picked up drops whole.
        MiteGameTests.add("combat_zombie_ingot_drops", 20, h -> {
            var level = h.getLevel();
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
            Vec3 at = zombie.position();
            ItemEntity picked = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.IRON_INGOT, 10));
            ItemEntity loot = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.IRON_INGOT));
            MobGear.onDrops(new LivingDropsEvent(zombie, level.damageSources().generic(), new ArrayList<>(List.of(picked, loot)), true));
            h.assertTrue(picked.getItem().is(Items.IRON_INGOT) && picked.getItem().getCount() == 10, "picked-up stack kept, got " + picked.getItem());
            h.assertTrue(List.of(Items.COPPER_NUGGET, dev.mite.material.MaterialItems.SILVER_NUGGET.get(), Items.IRON_NUGGET).contains(loot.getItem().getItem()),
                    "loot ingot became a nugget, got " + loot.getItem());
            h.succeed();
        });
        // EF-CBT-10: fire ignores metal armor (full iron takes the whole hit) but not leather; on a mob, a metal piece takes away
        // only what it gave (half its value), so a leather helmet protects the same with or without a rusted chestplate.
        MiteGameTests.add("combat_fire_ignores_metal", 20, h -> {
            ServerPlayer iron = CombatTests.wearing(h, IRON, 0), leather = CombatTests.wearing(h, LEATHER, 0);
            h.assertValueEqual(fireHit(h, iron), 4f, "full iron in fire");
            h.assertTrue(fireHit(h, leather) < 4f, "leather protects from fire");
            Zombie helmet = h.spawn(EntityTypes.ZOMBIE, 1, 1, 1), both = h.spawn(EntityTypes.ZOMBIE, 5, 1, 5);
            for (Zombie z : List.of(helmet, both)) {
                z.setNoAi(true);
                z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            }
            both.setItemSlot(EquipmentSlot.CHEST, new ItemStack(CombatContent.armorItem(RUSTED_IRON, ArmorType.CHESTPLATE, false)));
            h.runAfterDelay(2, () -> { // the mobs pick up their armor modifiers on tick
                h.assertTrue(both.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                        > helmet.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR), "the chestplate counts against blows");
                h.assertValueEqual(fireHit(h, both), fireHit(h, helmet), "fire on a zombie with and without the metal chestplate");
                h.succeed();
            });
        });
        // EF-CBT-10 (G07): 4 silver pieces always ward off poison, none never does.
        MiteGameTests.add("combat_silver_poison_ward", 20, h -> {
            ArmorStand silver = h.spawn(EntityTypes.ARMOR_STAND, 1, 1, 1), bare = h.spawn(EntityTypes.ARMOR_STAND, 5, 1, 5);
            for (ArmorType t : CombatContent.PIECES) silver.setItemSlot(t.getSlot(), new ItemStack(CombatContent.armorItem(SILVER, t, t == ArmorType.HELMET)));
            h.assertValueEqual(ArmorHazards.silverPieces(silver), 4, "silver pieces, chainmail included");
            for (int i = 0; i < 50; i++) {
                h.assertValueEqual(poison(silver), MobEffectEvent.Applicable.Result.DO_NOT_APPLY, "poison on 4 silver pieces");
                h.assertTrue(poison(bare) != MobEffectEvent.Applicable.Result.DO_NOT_APPLY, "poison on no silver");
            }
            h.succeed();
        });
        // M12: a drowned's thrown trident hits a player like an iron spear at every difficulty (Easy 4, Normal 6, Hard 9).
        MiteGameTests.add("combat_trident_difficulty", 20, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            Drowned drowned = h.spawn(EntityTypes.DROWNED, 2, 1, 2);
            DamageSource source = level.damageSources().trident(new ThrownTrident(level, drowned, new ItemStack(Items.TRIDENT)), drowned);
            ServerPlayer player = MetabolismTests.player(h);
            h.assertValueEqual(Archery.tridentDamage(drowned, source, 8), 6f, "trident on a mob");
            Difficulty before = level.getDifficulty();
            try {
                float[][] cases = {{Difficulty.EASY.ordinal(), 5, 4}, {Difficulty.NORMAL.ordinal(), 8, 6}, {Difficulty.HARD.ordinal(), 12, 9}};
                for (float[] c : cases) {
                    server.setDifficulty(Difficulty.values()[(int) c[0]], true);
                    h.assertValueEqual(Archery.tridentDamage(player, source, c[1]), c[2], "trident on a player, " + level.getDifficulty());
                }
            } finally {
                server.setDifficulty(before, true); // global state: restored in the same tick
            }
            h.succeed();
        });
        // EF-CBT-08 / dom-G-10: 1000 flint arrows wounding a mob leave about 30 % of them on the ground.
        MiteGameTests.add("combat_arrow_recovered_from_mob", 20, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
            zombie.setNoAi(true);
            Arrow arrow = new Arrow(h.getLevel(), 0, 0, 0, new ItemStack(Items.ARROW), null);
            arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.ALLOWED;
            for (int i = 0; i < 1000; i++) {
                zombie.damageCooldownTime = 0;
                zombie.setHealth(zombie.getMaxHealth());
                zombie.hurtServer(h.getLevel(), h.getLevel().damageSources().arrow(arrow, null), 1);
            }
            int dropped = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, zombie.getBoundingBox().inflate(3))
                    .stream().filter(e -> e.getItem().is(Items.ARROW)).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(dropped >= 240 && dropped <= 360, "flint arrows recovered " + dropped + "/1000");
            h.succeed();
        });
        // M12 / arbitrage n°18: the ominous vault's heavy core only drops for a player who killed the Dragon.
        MiteGameTests.add("combat_heavy_core_post_dragon", 40, h -> {
            var server = h.getLevel().getServer();
            var table = server.reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE);
            var player = MiteGameTests.levelPlayer(h);
            java.util.function.BooleanSupplier core = () -> {
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(h.getLevel())
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, h.absoluteVec(new Vec3(2, 1, 2)))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, player)
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.VAULT);
                for (int i = 0; i < 200; i++) if (table.getRandomItems(params).stream().anyMatch(s -> s.is(Items.HEAVY_CORE))) return true;
                return false;
            };
            h.assertFalse(core.getAsBoolean(), "heavy core before the Dragon");
            var dragon = server.getAdvancements().get(net.minecraft.resources.Identifier.withDefaultNamespace("end/kill_dragon"));
            dragon.value().criteria().keySet().forEach(c -> player.getAdvancements().award(dragon, c));
            h.assertTrue(core.getAsBoolean(), "heavy core after the Dragon");
            h.succeed();
        });
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipe(String id) {
        return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.parse(id));
    }

    private static boolean produces(RecipeHolder<?> recipe, net.minecraft.server.level.ServerLevel level, Item item) {
        return recipe.value().display().stream().anyMatch(d -> d.result().resolveForFirstStack(
                net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level)).is(item));
    }

    /** Spawns an ownerless arrow of that item and reads its saved base damage (no getter in 26.3). */
    private static double baseDamage(net.minecraft.gametest.framework.GameTestHelper h, Item item) {
        Vec3 pos = h.absoluteVec(new Vec3(2, 3, 2));
        Arrow arrow = new Arrow(h.getLevel(), pos.x, pos.y, pos.z, new ItemStack(item), null);
        arrow.setNoGravity(true);
        h.getLevel().addFreshEntity(arrow);
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        arrow.saveWithoutId(out);
        arrow.discard();
        return out.buildResult().getDoubleOr("damage", -1);
    }

    /** Health lost to a 4-point fire hit (in fire: no difficulty scaling). */
    private static float fireHit(GameTestHelper h, LivingEntity e) {
        e.setHealth(e.getMaxHealth());
        e.damageCooldownTime = 0;
        float before = e.getHealth();
        e.hurtServer(h.getLevel(), h.getLevel().damageSources().inFire(), 4);
        return before - e.getHealth();
    }

    /** What the poison ward decides for a poison effect on {@code e}. */
    private static MobEffectEvent.Applicable.Result poison(LivingEntity e) {
        var event = new MobEffectEvent.Applicable(e, new MobEffectInstance(MobEffects.POISON, 100), null);
        ArmorHazards.onEffect(event);
        return event.getResult();
    }

    /** Health a fresh zombie loses to 4 damage from an arrow of that item. */
    private static float arrowHit(net.minecraft.gametest.framework.GameTestHelper h, Item item, int x) {
        Zombie zombie = h.spawn(EntityTypes.ZOMBIE, x, 1, 2);
        Arrow arrow = new Arrow(h.getLevel(), 0, 0, 0, new ItemStack(item), null);
        float before = zombie.getHealth();
        zombie.hurtServer(h.getLevel(), h.getLevel().damageSources().arrow(arrow, null), 4);
        return before - zombie.getHealth();
    }
}
