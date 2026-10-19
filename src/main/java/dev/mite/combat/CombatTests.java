package dev.mite.combat;

import static dev.mite.core.Material.*;

import dev.mite.core.Material;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.equipment.ArmorType;
import dev.mite.harvest.HarvestContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.CommonHooks;

/** GameTests for EF-CBT-01..04 (flat armor, wear rules, horse armor) and EF-IA-04. */
public final class CombatTests {
    private CombatTests() {}

    public static void register() {
        // EF-CBT-01 / EF-CBT-02: damage = weapon base + material, attack speed per type.
        MiteGameTests.add("combat_weapon_damage", 20, h -> {
            h.assertValueEqual(damage(Items.COPPER_SWORD), 7.0, "copper sword damage");
            h.assertValueEqual(damage(Items.IRON_SWORD), 8.0, "iron sword damage");
            h.assertValueEqual(damage(Items.GOLDEN_AXE), 5.0, "golden axe damage");
            h.assertValueEqual(damage(tool("adamantium_sword")), 10.0, "adamantium sword damage");
            h.assertValueEqual(damage(tool("flint_knife")), 2.0, "flint knife damage");
            h.assertValueEqual(damage(tool("wooden_cudgel")), 1.0, "wooden cudgel damage");
            h.assertValueEqual(damage(HarvestContent.item(dev.mite.core.ToolType.CLUB, WOOD)), 2.0, "wooden club damage");
            double speed = new ItemStack(Items.IRON_SWORD).get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_SPEED, 4, EquipmentSlot.MAINHAND);
            h.assertTrue(Math.abs(speed - 1.6) < 1e-6, "iron sword attack speed " + speed);
            h.succeed();
        });
        // EF-CBT-03 CA-1 / CA-2: a full iron plate set gives 8 armor points, vanilla chainmail (iron chainmail) 6.
        MiteGameTests.add("combat_armor_sets", 40, h -> {
            ArmorStand plate = h.spawn(EntityTypes.ARMOR_STAND, 2, 1, 2);
            equip(plate, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            ArmorStand chain = h.spawn(EntityTypes.ARMOR_STAND, 4, 1, 4);
            equip(chain, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
            h.assertValueEqual(new ItemStack(Items.IRON_CHESTPLATE).getMaxDamage(), 12_800, "iron chestplate durability");
            h.assertValueEqual(new ItemStack(Items.CHAINMAIL_HELMET).getMaxDamage(), 4_000, "chainmail helmet durability");
            h.succeedWhen(() -> {
                h.assertValueEqual(plate.getArmorValue(), 8, "iron plate set armor");
                h.assertValueEqual(chain.getArmorValue(), 6, "chainmail set armor");
            });
        });
        // EF-CBT-03 / EF-OUT-05: armor wears on the x100 scale (4 damage = 1 vanilla wear point = 100).
        MiteGameTests.add("combat_armor_wear", 20, h -> {
            // The vanilla wear path (LivingEntity#doHurtEquipment) on an armor stand; mock players have no connection to be hurt.
            ArmorStand stand = h.spawn(EntityTypes.ARMOR_STAND, 2, 1, 2);
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            CommonHooks.onArmorHurt(h.getLevel().damageSources().cactus(), new EquipmentSlot[]{EquipmentSlot.HEAD}, 1, stand);
            h.assertValueEqual(stand.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(), 100, "iron helmet wear");
            h.succeed();
        });
        // B2 / EF-CBT-03: flat protection. 5 damage against a full iron set (8): 0 or half a heart (R134); worn to 7/8 (factor 0.25): 5 - 2.
        MiteGameTests.add("combat_flat_armor", 20, h -> {
            ServerPlayer fresh = wearing(h, IRON, 0), worn = wearing(h, IRON, 7);
            int zero = 0, half = 0;
            for (int i = 0; i < 40; i++) {
                float loss = hit(h, fresh, 5);
                h.assertTrue(loss == 0 || loss == 1, "full iron set loss " + loss);
                if (loss == 0) zero++;
                else half++;
            }
            h.assertTrue(zero > 0 && half > 0, "protection >= damage: only a chance of half a heart, " + zero + " / " + half);
            float loss = hit(h, worn, 5);
            h.assertTrue(Math.abs(loss - (5 - 8 * 0.25f)) < 1e-3, "worn iron set loss " + loss);
            ServerPlayer leather = wearing(h, LEATHER, 0);
            h.assertValueEqual(leather.getArmorValue(), 2, "leather set armor (M9)");
            h.assertTrue(Math.abs(hit(h, leather, 5) - 3) < 1e-3, "leather set loss");
            h.assertValueEqual(new ItemStack(Items.LEATHER_CHESTPLATE).getMaxDamage(), 1_600, "leather chestplate durability");
            h.succeed();
        });
        // R118: one hit wears a single piece of a full set; EF-CBT-04: a piece at 0 is not destroyed, except lava (metal) and fire (leather).
        MiteGameTests.add("combat_armor_wear_rules", 20, h -> {
            ServerPlayer p = wearing(h, IRON, 0);
            hit(h, p, 5);
            long worn = CombatContent.PIECES.stream().filter(t -> p.getItemBySlot(t.getSlot()).isDamaged()).count();
            h.assertValueEqual((int) worn, 1, "pieces worn by one hit");
            var sources = h.getLevel().damageSources();
            h.assertValueEqual(standWear(h, Items.IRON_HELMET, 50, sources.cactus()), new ItemStack(Items.IRON_HELMET).getMaxDamage() - 1, "worn out helmet kept");
            h.assertValueEqual(standWear(h, Items.IRON_HELMET, 50, sources.lava()), -1, "lava destroys metal");
            h.assertValueEqual(standWear(h, Items.LEATHER_HELMET, 50, sources.inFire()), -1, "fire destroys leather");
            h.assertValueEqual(standWear(h, Items.IRON_HELMET, 0, sources.inFire()), 0, "fire does not wear metal (R178)");
            h.assertValueEqual(standWear(h, CombatContent.armorItem(ADAMANTIUM, ArmorType.HELMET, false), 0, sources.lava()), 0, "lava does not wear adamantium");
            h.assertValueEqual(standWear(h, Items.LEATHER_HELMET, 0, sources.inFire()), 100, "fire wears leather");
            h.succeed();
        });
        // dom-G-13: horse armor = plate protection x 0.5, turtle shell at the copper helmet level.
        MiteGameTests.add("combat_horse_and_turtle", 20, h -> {
            h.assertTrue(Math.abs(bonus(Items.IRON_HORSE_ARMOR, EquipmentSlot.BODY) - 4) < 1e-6, "iron horse armor");
            h.assertTrue(Math.abs(bonus(Items.LEATHER_HORSE_ARMOR, EquipmentSlot.BODY) - 1) < 1e-6, "leather horse armor");
            h.assertTrue(Math.abs(bonus(Items.TURTLE_HELMET, EquipmentSlot.HEAD) - CombatFormulas.pieceProtection(CombatFormulas.HELMET, COPPER, false)) < 1e-6, "turtle shell");
            h.assertValueEqual(new ItemStack(Items.TURTLE_HELMET).getMaxDamage(), 4_000, "turtle shell durability");
            h.succeed();
        });
        // EF-IA-04: vanilla mob stats.
        MiteGameTests.add("combat_mob_stats", 20, h -> {
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, 2, 1, 2);
            Skeleton skeleton = h.spawn(EntityTypes.SKELETON, 4, 1, 4);
            h.assertValueEqual(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.0, "zombie attack damage");
            h.assertValueEqual(skeleton.getMaxHealth(), 6f, "skeleton max health");
            h.assertValueEqual(skeleton.getHealth(), 6f, "skeleton health");
            h.succeed();
        });
    }

    static double damage(Item item) {
        return new ItemStack(item).get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ATTACK_DAMAGE, CombatFormulas.PLAYER_BASE_DAMAGE, EquipmentSlot.MAINHAND);
    }

    private static double bonus(Item item, EquipmentSlot slot) {
        return new ItemStack(item).get(DataComponents.ATTRIBUTE_MODIFIERS).compute(Attributes.ARMOR, 0, slot);
    }

    /** Survival mock player in a full plate set worn to eighths/8 of its durability (the mock never ticks: modifiers by hand). */
    static ServerPlayer wearing(GameTestHelper h, Material material, int eighths) {
        ServerPlayer p = MetabolismTests.player(h);
        for (ArmorType t : CombatContent.PIECES) {
            ItemStack stack = new ItemStack(CombatContent.armorItem(material, t, false));
            stack.setDamageValue(stack.getMaxDamage() * eighths / 8);
            p.setItemSlot(t.getSlot(), stack);
            stack.forEachModifier(t.getSlot(), (attr, mod) -> p.getAttribute(attr).addOrUpdateTransientModifier(mod));
        }
        return p;
    }

    /** Health lost from one 5-point-like hit (cactus: armor applies, no difficulty scaling). */
    private static float hit(GameTestHelper h, ServerPlayer p, float damage) {
        p.setHealth(p.getMaxHealth());
        p.damageCooldownTime = 0;
        float before = p.getHealth();
        p.hurtServer(h.getLevel(), h.getLevel().damageSources().cactus(), damage);
        return before - p.getHealth();
    }

    /** Damage of a helmet (on an armor stand, worn to {@code left} below max) after one wear point from that source; -1 when destroyed. */
    private static int standWear(GameTestHelper h, Item helmet, int left, DamageSource source) {
        ArmorStand stand = h.spawn(EntityTypes.ARMOR_STAND, 2, 1, 2);
        ItemStack stack = new ItemStack(helmet);
        if (left > 0) stack.setDamageValue(stack.getMaxDamage() - left);
        stand.setItemSlot(EquipmentSlot.HEAD, stack);
        CommonHooks.onArmorHurt(source, new EquipmentSlot[]{EquipmentSlot.HEAD}, 1, stand);
        ItemStack after = stand.getItemBySlot(EquipmentSlot.HEAD);
        stand.discard();
        return after.isEmpty() ? -1 : after.getDamageValue();
    }

    private static void equip(ArmorStand stand, Item head, Item chest, Item legs, Item feet) {
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
    }

    private static Item tool(String name) {
        return HarvestContent.TOOLS.stream().filter(t -> t.item().getId().getPath().equals(name)).findFirst().orElseThrow().item().get();
    }
}
