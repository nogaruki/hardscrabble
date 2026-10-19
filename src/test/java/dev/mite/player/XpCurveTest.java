package dev.mite.player;

import static dev.mite.player.XpCurve.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mite.player.XpCurve.DeathPenalty;
import org.junit.jupiter.api.Test;

class XpCurveTest {
    @Test
    void clampAndSpan() {
        assertEquals(205_000, add(204_990, 1000));
        assertEquals(-800, add(0, Integer.MIN_VALUE));
        assertEquals(30, span(0));
        assertEquals(130, span(10));
        assertEquals(20, span(-1));
        assertEquals(0.5f, progress(15));
        assertEquals(0.5f, progress(-10));
    }

    @Test
    void levels() {
        assertEquals(750, addLevels(0, 10));
        assertEquals(7000, addLevels(750, 25));
        // Points kept inside the level when they fit, capped otherwise.
        assertEquals(500, addLevels(410, 1));
        assertEquals(-61, addLevels(100, -6));
        assertEquals(205_000, addLevels(0, Integer.MAX_VALUE));
        assertEquals(-800, addLevels(0, Integer.MIN_VALUE));
    }

    @Test
    void death() {
        assertEquals(0, DeathPenalty.R196_CODE.xpAfterDeath(750));
        assertEquals(-10, DeathPenalty.R196_CODE.xpAfterDeath(20));
        assertEquals(-800, DeathPenalty.R196_CODE.xpAfterDeath(-790));
        assertEquals(0, DeathPenalty.R196_CODE.droppedXp(750));
        assertEquals(0, DeathPenalty.WIKI_THIRD.xpAfterDeath(750));
        assertEquals(250, DeathPenalty.WIKI_THIRD.droppedXp(750));
        assertEquals(-20, DeathPenalty.WIKI_THIRD.xpAfterDeath(10));
        assertEquals(630, DeathPenalty.MCMOD_LEVEL.xpAfterDeath(750));
        assertEquals(-800, DeathPenalty.MCMOD_LEVEL.xpAfterDeath(-800));
    }
}
