package dev.mite.core;

/** EF-LUN-01 lunar calendar. Stateless: the same computation runs on the server and on the client (docs/02 §6.3.3). */
public final class MoonCalendar {
    private MoonCalendar() {}

    public static final int TICKS_PER_DAY = 24000;
    /** The harvest moon and the moon dog fall this many days before a blood moon and a blue moon. */
    public static final int HERALD_DAYS = 8;

    public enum Moon { NORMAL, BLOOD, BLUE, HARVEST, MOON_DOG }

    /** Day number d = floor(world ticks / 24000) + 1. */
    public static long day(long ticks) {
        return Math.floorDiv(ticks, TICKS_PER_DAY) + 1;
    }

    /** Time of day, 0 to 23 999. */
    public static int timeOfDay(long ticks) {
        return (int) Math.floorMod(ticks, (long) TICKS_PER_DAY);
    }

    /** Blue moon wins over blood moon on common days (EF-LUN-01 CA-1: day 256). */
    public static Moon moon(long day, int bloodPeriod, int bluePeriod) {
        if (Math.floorMod(day, bluePeriod) == 0) return Moon.BLUE;
        if (Math.floorMod(day, bloodPeriod) == 0) return Moon.BLOOD;
        if (Math.floorMod(day + HERALD_DAYS, bluePeriod) == 0) return Moon.MOON_DOG;
        if (Math.floorMod(day + HERALD_DAYS, bloodPeriod) == 0) return Moon.HARVEST;
        return Moon.NORMAL;
    }
}
