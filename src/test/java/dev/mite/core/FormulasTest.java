package dev.mite.core;

import static dev.mite.core.Formulas.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reference values from the functional spec (docs/01). */
class FormulasTest {
    @Test
    void xpCurve() {
        assertEquals(30, xpForLevel(1));
        assertEquals(250, xpForLevel(5));
        assertEquals(750, xpForLevel(10));
        assertEquals(2500, xpForLevel(20));
        assertEquals(7000, xpForLevel(35));
        assertEquals(205_000, xpForLevel(200));
        assertEquals(10, levelForXp(750));
        assertEquals(9, levelForXp(749));
        assertEquals(-5, levelForXp(-100));
        assertEquals(-40, levelForXp(-800));
    }

    @Test
    void healthAndDeath() {
        assertEquals(6, maxHealth(4));
        assertEquals(8, maxHealth(5));
        assertEquals(20, maxHealth(35));
        assertEquals(6, maxHealth(-40));
        assertEquals(0, xpAfterDeath(750));
        assertEquals(-10, xpAfterDeath(20));
        assertEquals(-800, xpAfterDeath(-790));
        assertEquals(0.2f, meleeFactor(-40), 1e-6);
    }

    @Test
    void durabilityAndWear() {
        assertEquals(4800, toolDurability(ToolType.PICKAXE, Material.COPPER, Quality.AVERAGE));
        assertEquals(512_000, toolDurability(ToolType.WAR_HAMMER, Material.ADAMANTIUM, Quality.AVERAGE));
        assertEquals(200, toolDurability(ToolType.SHOVEL, Material.WOOD, Quality.AVERAGE));
        assertEquals(14_400, toolDurability(ToolType.PICKAXE, Material.IRON, Quality.FINE));
        // A copper pickaxe breaks exactly 20 stone blocks (hardness 2.4).
        assertEquals(240, blockWear(2.4f, ToolType.PICKAXE));
        assertEquals(1, Material.FLINT.toolHarvestLevel());
        assertEquals(2, Material.COPPER.toolHarvestLevel());
        assertEquals(7.0f, toolSpeed(ToolType.PICKAXE, Material.COPPER), 1e-6);
        assertEquals(2.5f, toolSpeed(ToolType.HATCHET, Material.FLINT), 1e-6);
    }

    @Test
    void craftTimes() {
        // Copper pickaxe: 3 ingots + 2 sticks = 1250, copper bench (+0.3).
        assertEquals(293, craftTicks(1250, 0, 0.3f, false));
        assertEquals(191, craftTicks(1250, 35, 0.3f, false));
        assertEquals(427, craftTicks(2450, 0, 0.4f, false)); // iron pickaxe, 21.4 s
        assertEquals(25, craftTicks(10, 0, 0.2f, false));
    }

    @Test
    void metabolism() {
        assertEquals(2000, Math.round(HUNGER_PER_UNIT / BASE_METABOLISM));
        assertEquals(1250, Math.round(1 / regenPerTick(20, false, false)));
        assertEquals(1.375f, hungerMultiplier(0.375f, false), 1e-6);
    }

    @Test
    void furnaceHeat() {
        assertEquals(2, effectiveHeat(4, 2));
        assertEquals(1, effectiveHeat(1, 3));
        // EF-MET-05 CA-1: mithril (3) needs lava (3) in an obsidian furnace (3); coal (2) is not enough.
        assertFalse(canSmelt(2, 3, 3));
        assertTrue(canSmelt(3, 3, 3));
        // Blaze rod in a stone furnace is capped at 2: no adamantium (4).
        assertFalse(canSmelt(4, 2, 4));
        assertTrue(canSmelt(4, 4, 4));
        // Charcoal (1) cannot smelt raw iron (2).
        assertFalse(canSmelt(1, 2, 2));
    }

    @Test
    void depthMapping() {
        assertEquals(48, mapDepth(56));
        assertEquals(0, mapDepth(32));
        assertEquals(-16, mapDepth(24));
        assertEquals(-64, mapDepth(0));
        assertEquals(-48, mapDepth(8));
        assertEquals(16, mapDepth(40));
        assertEquals(96, mapDepth(96)); // unchanged above sea level
    }

    @Test
    void depthScaledVeinSize() {
        assertEquals(9, Formulas.depthScaledVeinSize(6, -64, -64, 64)); // x1.5 at the bottom
        assertEquals(6, Formulas.depthScaledVeinSize(6, 0, -64, 64));
        assertEquals(3, Formulas.depthScaledVeinSize(6, 64, -64, 64)); // x0.5 at the top
        assertEquals(2, Formulas.depthScaledVeinSize(3, 100, -64, 0)); // clamped above the range
        assertEquals(1, Formulas.depthScaledVeinSize(1, 0, -64, 0));
    }
}
