package dev.mite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ET-O-01: same witch + same player = same curse; different witches spread over all curses. */
class CurseSelectorTest {
    @Test
    void deterministicAndSpread() {
        assertEquals(CurseSelector.select(42L, "Steve", 16), CurseSelector.select(42L, "Steve", 16));
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            int c = CurseSelector.select(seed, "Steve", 16);
            assertTrue(c >= 0 && c < 16);
            seen.add(c);
        }
        assertEquals(16, seen.size());
    }
}
