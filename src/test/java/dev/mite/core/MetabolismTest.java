package dev.mite.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** EF-NUT-01, 04, 05, 06 control values. */
class MetabolismTest {
    /** Ticks until the predicate holds, at most max. */
    private static int ticksUntil(Metabolism m, int cap, boolean hurt, java.util.function.BooleanSupplier done, int max) {
        for (int t = 1; t <= max; t++) {
            m.tick(cap, 0, 0, hurt, false, 0);
            if (done.getAsBoolean()) return t;
        }
        return -1;
    }

    @Test
    void restingLosesOneUnitPer2000Ticks() {
        Metabolism m = new Metabolism();
        int t = ticksUntil(m, 6, false, () -> m.satiety == 5, 3000);
        assertTrue(Math.abs(t - 2000) <= 20, "unit lost at tick " + t);
        assertEquals(6, m.nutrition);
    }

    @Test
    void nutritionDrainsEvery8000TicksWhateverTheSatiety() {
        Metabolism m = new Metabolism();
        m.satiety = 20;
        m.nutrition = 20;
        int t = ticksUntil(m, 20, false, () -> m.nutrition == 19, 9000);
        assertTrue(Math.abs(t - 8000) <= 40, "nutrition drained at tick " + t);
    }

    @Test
    void satietyConsumedBeforeNutrition() {
        Metabolism m = new Metabolism();
        m.satiety = 1;
        m.tick(6, 4, 0, false, false, 0);
        assertEquals(0, m.satiety);
        assertEquals(6, m.nutrition);
        m.tick(6, 4, 0, false, false, 0);
        assertEquals(5, m.nutrition);
    }

    @Test
    void regenCostsHunger() {
        Metabolism m = new Metabolism();
        m.satiety = 20;
        m.nutrition = 20;
        int healed = 0;
        for (int t = 0; t < 5000; t++) healed += Math.max(m.tick(20, 0, 0, true, false, 0), 0);
        assertEquals(4, healed, 1); // EF-NUT-04 CA-1
    }

    @Test
    void malnutritionQuartersRegen() {
        Metabolism m = new Metabolism();
        m.nutrition = 20;
        m.satiety = 20;
        m.phyto = 0;
        assertTrue(m.malnourished());
        int healed = 0;
        for (int t = 0; t < 6000; t++) healed += Math.max(m.tick(20, 0, 0, true, false, 0), 0);
        assertEquals(1, healed); // EF-NUT-06 CA-2: one HP per 1250 × 4 ticks
    }

    @Test
    void starvation() {
        Metabolism m = new Metabolism();
        m.satiety = 0;
        m.nutrition = 0;
        int damageTick = -1;
        for (int t = 1; t <= 600 && damageTick < 0; t++) if (m.tick(6, 0, 0, true, false, 0) < 0) damageTick = t;
        assertEquals(500, damageTick);
        assertFalse(m.hasEnergy());
    }

    @Test
    void eatingFeedsCounters() {
        Metabolism m = new Metabolism();
        m.protein = 0;
        m.eat(10, 10, true, false, 6); // cooked beef
        assertEquals(80_000, m.protein);
        assertEquals(6, m.satiety);
        assertEquals(6, m.nutrition);
        assertFalse(m.malnourished());
    }

    @Test
    void humidity() {
        // EF-NUT-03 CA-1: rain + feet in water in plains = 1.375; standing in water in a frozen biome = 1.5.
        assertEquals(1.375f, Formulas.hungerMultiplier(Formulas.humidity(true, false, true, false, Formulas.Climate.TEMPERATE), false), 1e-6);
        assertEquals(1.5f, Formulas.hungerMultiplier(Formulas.humidity(false, false, true, false, Formulas.Climate.FROZEN), false), 1e-6);
        assertEquals(0f, Formulas.humidity(true, true, true, true, Formulas.Climate.HOT));
        assertEquals(0.5f, Formulas.humidity(true, true, true, false, Formulas.Climate.TEMPERATE));
        assertEquals(0.5f, Formulas.humidity(false, false, true, true, Formulas.Climate.TEMPERATE));
        // Changelog R118: the frozen x2 applies to icy water only, never to rain.
        assertEquals(0.25f, Formulas.humidity(true, false, false, false, Formulas.Climate.FROZEN));
        assertEquals(1f, Formulas.humidity(false, false, true, true, Formulas.Climate.FROZEN));
    }

    @Test
    void regenerationEffect() {
        // EF-NUT-04: Regeneration I adds 1 HP / 50 ticks of healing progress, each HP costing a hunger point.
        assertEquals(50, Math.round(1 / Formulas.regenEffectPerTick(0)));
        Metabolism m = new Metabolism();
        int healed = 0;
        for (int t = 0; t < 100; t++) healed += Math.max(m.tick(6, 0, 0, true, false, Formulas.regenEffectPerTick(0)), 0);
        assertEquals(2, healed);
    }

    @Test
    void insulin() {
        Metabolism m = new Metabolism();
        for (int i = 0; i < 10; i++) m.eatSugar(1000); // EF-NUT-07 CA-1: 10 apples
        assertEquals(48_000, m.insulin);
        assertEquals(1, m.insulinLevel);
        m.insulin = 150_000;
        assertEquals(1, m.eatSugar(1000)); // level before the meal
        assertEquals(3, m.insulinLevel);
        assertEquals(100, Formulas.insulinPoisonTicks(4800)); // CA-2
        assertEquals(2, Formulas.insulinSatietyPenalty(2000));
        assertEquals(1, Formulas.insulinSatietyPenalty(250));
        m.insulin = 50_000;
        m.tick(6, 0, 0, false, false, 0);
        assertEquals(2, m.insulinLevel); // one notch at a time
        m.tick(6, 0, 0, false, false, 0);
        assertEquals(1, m.insulinLevel);
        m.tick(6, 0, 0, false, false, 0);
        assertEquals(1, m.insulinLevel);
    }

    @Test
    void foodMirror() {
        assertEquals(20, Formulas.foodLevelMirror(6, 6));
        assertEquals(0, Formulas.foodLevelMirror(0, 6));
        assertEquals(10, Formulas.foodLevelMirror(10, 20));
    }
}
