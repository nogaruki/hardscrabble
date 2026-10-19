package dev.mite.farming.livestock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** EF-AGR-08 needs math. */
class LivestockTest {
    @Test
    void decayAndRefill() {
        Livestock s = new Livestock();
        assertEquals(0, s.update(24_000, false, false, false, false));
        assertEquals(0.5f, s.food, 1e-4);
        assertEquals(0, s.water);
        assertEquals(0.5f, s.sunlight, 1e-4);
        s.update(2_400, true, true, true, false);
        assertEquals(1, s.food);
        assertEquals(1, s.water);
        assertEquals(1, s.sunlight);
    }

    @Test
    void sicknessAndRecovery() {
        Livestock s = new Livestock();
        s.update(Livestock.SICK, true, true, true, true); // crowded alone makes it sick
        assertTrue(s.sick());
        assertFalse(s.productive());
        s.update(Livestock.SICK, true, true, true, false);
        assertEquals(0, s.sickness);
        assertTrue(s.productive());
    }

    @Test
    void neglectHurtsOnlyWhenDyingAndStarving() {
        Livestock s = new Livestock();
        s.sickness = Livestock.DYING;
        assertEquals(0, s.update(400, false, true, true, true)); // crowded but fed and watered
        s.food = 0;
        assertEquals(1f, s.update(400, false, true, true, true));
    }

    @Test
    void feedingAndGapCap() {
        Livestock s = new Livestock();
        assertFalse(s.feed()); // sated: the item goes to breeding
        s.food = 0.2f;
        assertTrue(s.feed());
        assertEquals(0.7f, s.food, 1e-6);
        s.update(1_000_000, false, false, true, false);
        assertEquals(0.2f, s.food, 1e-4); // at most one day applied
        s.update(-5, false, false, true, false);
        assertEquals(0.2f, s.food, 1e-4);
    }
}
