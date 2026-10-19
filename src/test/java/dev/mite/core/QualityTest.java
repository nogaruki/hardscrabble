package dev.mite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** EF-CRA-05: XP cost, material cap, XP cap. */
class QualityTest {
    private static final float IRON_PICKAXE = 3 * 800 + 2 * 25;

    @Test
    void cost() {
        assertEquals(980, Quality.FINE.xpCost(IRON_PICKAXE));
        assertEquals(0, Quality.AVERAGE.xpCost(IRON_PICKAXE));
        assertEquals(1960, Quality.EXCELLENT.xpCost(IRON_PICKAXE));
    }

    @Test
    void resolve() {
        assertEquals(Quality.EXCELLENT, Quality.resolve(Quality.LEGENDARY, Material.COPPER.maxQuality, 1250, 1_000_000));
        assertEquals(Quality.FINE, Quality.resolve(Quality.MASTERWORK, Material.IRON.maxQuality, IRON_PICKAXE, 1000));
        assertEquals(Quality.AVERAGE, Quality.resolve(Quality.FINE, Material.IRON.maxQuality, IRON_PICKAXE, 0));
        assertEquals(Quality.POOR, Quality.resolve(Quality.FINE, Material.RUSTED_IRON.maxQuality, IRON_PICKAXE, 1_000_000));
    }
}
