package dev.mite.combat;

import static dev.mite.core.Material.*;
import static dev.mite.core.ToolType.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.mite.core.Material;
import java.util.List;
import org.junit.jupiter.api.Test;

/** EF-CBT-01..03 tables. */
class CombatFormulasTest {
    @Test
    void damageTable() {
        // EF-CBT-01 CA-1: every cell of the spec table.
        List<Material> metals = List.of(RUSTED_IRON, COPPER, SILVER, GOLD, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM);
        int[] sword = {6, 7, 7, 6, 8, 8, 9, 10}, axe = {5, 6, 6, 5, 7, 7, 8, 9}, dagger = {4, 5, 5, 4, 6, 6, 7, 8}, shovel = {3, 4, 4, 3, 5, 5, 6, 7};
        for (int i = 0; i < metals.size(); i++) {
            Material m = metals.get(i);
            assertEquals(sword[i], CombatFormulas.damage(SWORD, m));
            assertEquals(sword[i], CombatFormulas.damage(BATTLE_AXE, m));
            assertEquals(axe[i], CombatFormulas.damage(AXE, m));
            for (var t : List.of(DAGGER, HATCHET, PICKAXE, WAR_HAMMER)) assertEquals(dagger[i], CombatFormulas.damage(t, m));
            for (var t : List.of(SHOVEL, HOE, MATTOCK)) assertEquals(shovel[i], CombatFormulas.damage(t, m));
        }
        assertEquals(4, CombatFormulas.damage(AXE, FLINT));
        assertEquals(5, CombatFormulas.damage(AXE, OBSIDIAN));
        assertEquals(3, CombatFormulas.damage(HATCHET, FLINT));
        assertEquals(4, CombatFormulas.damage(HATCHET, OBSIDIAN));
        assertEquals(2, CombatFormulas.damage(KNIFE, FLINT));
        assertEquals(3, CombatFormulas.damage(KNIFE, OBSIDIAN));
        assertEquals(2, CombatFormulas.damage(SHOVEL, FLINT));
        assertEquals(1, CombatFormulas.damage(CUDGEL, WOOD));
        assertEquals(2, CombatFormulas.damage(CLUB, WOOD));
    }

    @Test
    void swordDpsGrowsWithTier() {
        // EF-CBT-02 CA-1 (gold is a side branch, not a tier step).
        double previous = 0;
        for (Material m : List.of(RUSTED_IRON, COPPER, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) {
            double dps = CombatFormulas.damage(SWORD, m) * CombatFormulas.attacksPerSecond(SWORD);
            assertTrue(dps >= previous, m.name());
            previous = dps;
        }
        // EF-CBT-02 CA-2: three charged copper sword hits kill a 20 HP zombie.
        assertEquals(2 / 1.6, CombatFormulas.timeToKill(SWORD, COPPER, 20), 1e-9);
    }

    @Test
    void armor() {
        // EF-CBT-03: a full set gives the material protection, and survives the vanilla floor of the armor attribute.
        for (Material m : List.of(COPPER, SILVER, GOLD, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) {
            for (boolean chain : new boolean[]{false, true}) {
                double set = 0;
                for (int c : new int[]{CombatFormulas.HELMET, CombatFormulas.CHESTPLATE, CombatFormulas.LEGGINGS, CombatFormulas.BOOTS})
                    set += CombatFormulas.pieceProtection(c, m, chain);
                assertEquals(CombatFormulas.protection(m, chain), (int) Math.floor(set), m.name());
            }
        }
        assertEquals(8, CombatFormulas.protection(IRON, false));
        assertEquals(6, CombatFormulas.protection(IRON, true));
        // Durability table (plate, average), x100 scale.
        assertEquals(8_000, CombatFormulas.armorDurability(CombatFormulas.HELMET, IRON, false));
        assertEquals(25_600, CombatFormulas.armorDurability(CombatFormulas.CHESTPLATE, ANCIENT_METAL, false));
        assertEquals(204_800, CombatFormulas.armorDurability(CombatFormulas.BOOTS, ADAMANTIUM, false));
        assertEquals(3_200, CombatFormulas.armorDurability(CombatFormulas.BOOTS, COPPER, true) * 2);
        // M9: leather 2 points, 10/16/14/8 (x100).
        assertEquals(2, CombatFormulas.protection(LEATHER, false));
        assertEquals(1_000, CombatFormulas.armorDurability(CombatFormulas.HELMET, LEATHER, false));
        assertEquals(800, CombatFormulas.armorDurability(CombatFormulas.BOOTS, LEATHER, false));
        assertFalse(LEATHER.isMetal());
        // dom-G-13: horse armor at half the plate protection.
        assertEquals(4, CombatFormulas.horseArmor(IRON));
        assertEquals(1, CombatFormulas.horseArmor(LEATHER));
    }

    @Test
    void flatArmor() {
        // B2 (R127): 5 damage against 8 x 0.25 = 2 points leaves 3; leather (2) leaves 3.
        assertEquals(3f, CombatFormulas.damageAfterArmor(5, 2, 0.99f));
        // R134: protection meets or exceeds the damage, only a chance of half a heart.
        assertEquals(1f, CombatFormulas.damageAfterArmor(5, 8, 0));
        assertEquals(0f, CombatFormulas.damageAfterArmor(5, 8, 0.99f));
        assertEquals(0.5f, CombatFormulas.damageAfterArmor(0.5f, 8, 0));
        assertEquals(4f, CombatFormulas.damageAfterArmor(4, 0, 0.99f));
    }

    @Test
    void armorWear() {
        // EF-CBT-04 CA-1 (ArmorDegradeTest): full to 50 % wear, half at 75 %, 0 when broken.
        assertEquals(1.0, CombatFormulas.wearFactor(0, 100));
        assertEquals(1.0, CombatFormulas.wearFactor(50, 100));
        assertEquals(0.5, CombatFormulas.wearFactor(75, 100));
        assertEquals(0.0, CombatFormulas.wearFactor(100, 100));
    }

    @Test
    void arrows() {
        // EF-CBT-08: 0.5 + material x 0.5 for the 10 tips.
        List<Material> tips = List.of(FLINT, OBSIDIAN, COPPER, SILVER, GOLD, RUSTED_IRON, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM);
        double[] damage = {1, 1.5, 2, 2, 1.5, 1.5, 2.5, 2.5, 3, 3.5};
        for (int i = 0; i < tips.size(); i++) assertEquals(damage[i], CombatFormulas.arrowDamage(tips.get(i)));
        assertEquals(0.3f, CombatFormulas.arrowRecovery(FLINT));
        assertEquals(0.9f, CombatFormulas.arrowRecovery(ADAMANTIUM));
        // EF-CBT-07
        assertEquals(1.25f, CombatFormulas.bowVelocity(MITHRIL));
        assertEquals(64, CombatFormulas.bowDurability(ANCIENT_METAL));
    }
}
