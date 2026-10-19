package dev.mite.farming.livestock;

/**
 * Needs of one farm animal (EF-AGR-08, docs/02 §6.3.2 {@code hardscrabble:livestock}). Pure state and decay math, no game imports.
 * Needs run from 0 (deprived) to 1 (met). Rates and thresholds are [NON VÉRIFIÉ]: the spec gives only CA-1 (20 cows in a
 * 3x3 pen without water fall sick within 3 days), which the crowding rule meets in half a day.
 */
public final class Livestock {
    /** A full stomach empties in 2 days, a full drink in 1, sunlight in 2 days indoors. */
    static final float FOOD_DECAY = 1f / 48_000, WATER_DECAY = 1f / 24_000, SUN_DECAY = 1f / 48_000;
    /** Grazing on grass refills food in 2 minutes, daylight refills sunlight in 100 s. */
    static final float GRAZE = 1f / 2_400, SUN_GAIN = 1f / 2_000;
    /** Ticks of continuous deprivation before the animal is sick, and before it starts dying. */
    static final int SICK = 12_000, DYING = 48_000;
    /** A sick, starving or thirsty animal loses 1 health per this many ticks. */
    static final int TICKS_PER_DAMAGE = 400;
    /** Production (milk, eggs, wool, manure) needs both food and water at least this high. */
    static final float PRODUCTIVE = 0.25f;
    /** Below this food level a fed item is eaten instead of starting love mode; one item adds {@link #FEED}. */
    static final float HUNGRY = 0.9f, FEED = 0.5f;
    /** Longest gap applied in one evaluation (an unloaded chunk catches up at most one day). */
    static final long MAX_GAP = 24_000;

    public float food = 1, water = 1, sunlight = 1;
    /** Ticks of deprivation, recovered one for one while every need is met. */
    public int sickness;
    /** Game time of the last evaluation, {@link Long#MIN_VALUE} before the first one. */
    public long lastEvalTick = Long.MIN_VALUE;
    /**
     * Grass eaten in the last update (transient): grazing makes up for the hunger first, so even a full animal keeps eating its
     * pasture (EF-AGR-08, no endless free grazing).
     */
    public float eaten;

    /** Advances the needs by {@code dt} ticks under the given conditions; returns the health lost. */
    public float update(long dt, boolean grazing, boolean drinking, boolean sun, boolean crowded) {
        dt = Math.clamp(dt, 0, MAX_GAP);
        float hungry = Math.max(0, food - dt * FOOD_DECAY);
        eaten = grazing ? Math.min(1 - hungry, dt * GRAZE) : 0;
        food = hungry + eaten;
        water = drinking ? 1 : Math.max(0, water - dt * WATER_DECAY);
        sunlight = sun ? Math.min(1, sunlight + dt * SUN_GAIN) : Math.max(0, sunlight - dt * SUN_DECAY);
        boolean deprived = food == 0 || water == 0 || sunlight == 0 || crowded;
        sickness = (int) (deprived ? Math.min(DYING, sickness + dt) : Math.max(0, sickness - dt));
        return sickness >= DYING && (food == 0 || water == 0) ? (float) dt / TICKS_PER_DAMAGE : 0;
    }

    public boolean sick() {
        return sickness >= SICK;
    }

    /** Milk, eggs, wool and manure only come from a healthy, fed and watered animal (EF-AGR-08). */
    public boolean productive() {
        return !sick() && food >= PRODUCTIVE && water >= PRODUCTIVE;
    }

    /** Eats one food item if hungry; false when sated (the item then goes to vanilla breeding). */
    public boolean feed() {
        if (food >= HUNGRY) return false;
        food = Math.min(1, food + FEED);
        return true;
    }
}
