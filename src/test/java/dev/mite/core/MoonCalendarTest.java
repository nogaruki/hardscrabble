package dev.mite.core;

import static dev.mite.core.MoonCalendar.Moon.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** EF-LUN-01 CA-1. */
class MoonCalendarTest {
    private static MoonCalendar.Moon moon(long day) {
        return MoonCalendar.moon(day, 32, 128);
    }

    @Test
    void calendarDays() {
        assertEquals(HARVEST, moon(24));
        assertEquals(BLOOD, moon(32));
        assertEquals(BLOOD, moon(64));
        assertEquals(MOON_DOG, moon(120));
        assertEquals(BLUE, moon(128));
        assertEquals(BLUE, moon(256));
        assertEquals(NORMAL, moon(1));
        assertEquals(NORMAL, moon(33));
    }

    @Test
    void dayFromTicks() {
        assertEquals(1, MoonCalendar.day(0));
        assertEquals(1, MoonCalendar.day(23999));
        assertEquals(32, MoonCalendar.day(31 * 24000L));
    }

    @Test
    void configurablePeriods() {
        assertEquals(BLOOD, MoonCalendar.moon(10, 10, 100));
        assertEquals(HARVEST, MoonCalendar.moon(12, 10, 100));
        assertEquals(BLUE, MoonCalendar.moon(100, 10, 100));
    }
}
