package dev.mite.core;

/** Crafting quality levels (EF-CRA-05). */
public enum Quality {
    WRETCHED(0.5f), POOR(0.75f), AVERAGE(1.0f), FINE(1.5f), EXCELLENT(2.0f), SUPERB(2.5f), MASTERWORK(3.0f), LEGENDARY(3.5f);

    public final float durabilityModifier;

    Quality(float durabilityModifier) {
        this.durabilityModifier = durabilityModifier;
    }

    /** Each step above average doubles the craft difficulty. */
    public float difficultyFactor() {
        return ordinal() <= AVERAGE.ordinal() ? 1f : (float) Math.pow(2, ordinal() - AVERAGE.ordinal());
    }

    /** XP points paid for this quality: round(adjusted difficulty / 5). Average and below are free (Q04 open point, resolved as average). */
    public int xpCost(float difficulty) {
        return ordinal() <= AVERAGE.ordinal() ? 0 : Math.round(difficulty * difficultyFactor() / 5);
    }

    /** Quality obtained: the target, capped by the material (EF-OUT-01), then lowered until the player can pay for it. */
    public static Quality resolve(Quality target, Quality materialMax, float difficulty, int xp) {
        Quality q = target.ordinal() > materialMax.ordinal() ? materialMax : target;
        while (q.ordinal() > AVERAGE.ordinal() && q.xpCost(difficulty) > xp) q = values()[q.ordinal() - 1];
        return q;
    }
}
