package dev.mite.core;

import static dev.mite.core.Formulas.*;

/**
 * Player metabolism state and its per-tick rules (EF-NUT-01, 03, 04, 05, 06, 07), free of game imports.
 * Satiety is hidden and consumed first, then nutrition; both are whole food units capped by {@link Formulas#foodCap}.
 */
public final class Metabolism {
    public int satiety = 6;
    public int nutrition = 6;
    /** Hunger points; every {@link Formulas#HUNGER_PER_UNIT} consumes one food unit. */
    public float hunger;
    /** Nutrition-only drain accumulator (EF-NUT-01, 25% of the base rate). */
    public float drain;
    public int protein = NUTRIENT_MAX;
    public int phyto = NUTRIENT_MAX;
    public int starveTimer;
    public float regen;
    /** Insulin resistance, 0 to {@link Formulas#INSULIN_MAX} (EF-NUT-07). */
    public int insulin;
    /** Displayed resistance level 0..3, with hysteresis. */
    public int insulinLevel;
    /** Ticks slept in the current sleep (EF-NUT-11); not saved. */
    public int sleepTicks;
    /** Last display state sent to the owning client; not saved. */
    public int syncedKey = -1;

    public boolean malnourished() {
        return protein == 0 || phyto == 0;
    }

    /** Food energy: satiety + nutrition above zero (EF-NUT-05). */
    public boolean hasEnergy() {
        return satiety + nutrition > 0;
    }

    public boolean wantsFood(int cap) {
        return satiety < cap || nutrition < cap;
    }

    /**
     * One server tick.
     *
     * @param actionHunger hunger points from actions this tick (converted vanilla exhaustion, EF-NUT-02)
     * @param humidity     environment humidity, see {@link Formulas#humidity} (EF-NUT-03)
     * @param effectRegen  healing progress per tick from the Regeneration effect, see {@link Formulas#regenEffectPerTick}
     * @return health change: +1 healed, -1 starvation damage, 0 otherwise
     */
    public int tick(int cap, float actionHunger, float humidity, boolean hurt, boolean inBed, float effectRegen) {
        satiety = Math.min(satiety, cap);
        nutrition = Math.min(nutrition, cap);
        protein = Math.max(protein - 1, 0);
        phyto = Math.max(phyto - 1, 0);
        insulin = Math.max(insulin - 1, 0);
        // EF-NUT-07 hysteresis: the displayed level only goes down one notch at a time.
        if (insulinLevel > insulinLevel(insulin)) insulinLevel--;
        boolean mal = malnourished();
        hunger += (BASE_METABOLISM + actionHunger) * hungerMultiplier(humidity, mal);
        drain += NUTRITION_DRAIN;

        int delta = 0;
        // EF-NUT-04: slow regen, each healed HP costs one hunger point; none while starving.
        if (hurt && nutrition > 0) {
            regen += regenPerTick(nutrition, mal, inBed) + effectRegen;
            if (regen >= 1) {
                regen -= 1;
                hunger += 1;
                delta = 1;
            }
        } else {
            regen = 0;
        }

        while (hunger >= HUNGER_PER_UNIT) {
            hunger -= HUNGER_PER_UNIT;
            if (satiety > 0) satiety--;
            else if (nutrition > 0) nutrition--;
        }
        if (drain >= HUNGER_PER_UNIT) {
            drain -= HUNGER_PER_UNIT;
            if (nutrition > 0) nutrition--;
        }

        // EF-NUT-05: 1 damage every 500 ticks at nutrition 0.
        if (nutrition == 0) {
            if (++starveTimer >= STARVATION_INTERVAL) {
                starveTimer = 0;
                delta = -1;
            }
        } else {
            starveTimer = 0;
        }
        return delta;
    }

    /** EF-NUT-06: a P (or V) food adds its nutrition × 8000 to the matching counter. */
    public void eat(int foodSatiety, int foodNutrition, boolean isProtein, boolean isPhyto, int cap) {
        satiety = Math.min(satiety + foodSatiety, cap);
        nutrition = Math.min(nutrition + foodNutrition, cap);
        if (isProtein) protein = Math.min(protein + foodNutrition * NUTRIENT_PER_FOOD_POINT, NUTRIENT_MAX);
        if (isPhyto) phyto = Math.min(phyto + foodNutrition * NUTRIENT_PER_FOOD_POINT, NUTRIENT_MAX);
    }

    /**
     * EF-NUT-07: records a sugary meal (response = sugar x 4.8) and returns the level before it, which drives the nausea,
     * poison and satiety penalty of that meal.
     */
    public int eatSugar(int sugar) {
        int before = insulinLevel;
        insulin = Math.min(insulin + Math.round(sugar * INSULIN_PER_SUGAR), INSULIN_MAX);
        insulinLevel = Math.max(insulinLevel, insulinLevel(insulin));
        return before;
    }

    /** What the HUD shows; a change triggers a sync. */
    public int displayKey() {
        return satiety | nutrition << 8 | (protein == 0 ? 1 << 16 : 0) | (phyto == 0 ? 1 << 17 : 0) | insulinLevel << 18;
    }
}
