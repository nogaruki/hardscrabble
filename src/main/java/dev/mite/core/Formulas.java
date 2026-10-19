package dev.mite.core;

/** MITE balance formulas, kept free of game imports so they can be unit tested (CDC-T P3). */
public final class Formulas {
    private Formulas() {}

    // ---- Player: XP and health (EF-JOU-01..04) ----

    public static final int MIN_LEVEL = -40;
    public static final int MAX_LEVEL = 200;
    public static final int MIN_XP = MIN_LEVEL * 20;

    /** Cumulative XP needed to reach a level: 5n² + 25n above zero, 20 points per negative level. */
    public static int xpForLevel(int level) {
        return level >= 0 ? 5 * level * level + 25 * level : 20 * level;
    }

    public static int levelForXp(int xp) {
        if (xp < 0) return Math.max(MIN_LEVEL, -((-xp + 19) / 20));
        int level = 0;
        while (level < MAX_LEVEL && xpForLevel(level + 1) <= xp) level++;
        return level;
    }

    public static int maxHealth(int level) {
        return Math.clamp(6 + Math.floorDiv(level, 5) * 2, 6, 20);
    }

    /** Hunger and nutrition caps follow the health cap. */
    public static int foodCap(int level) {
        return maxHealth(level);
    }

    /** Mining and crafting speed bonus: ±2% per level. */
    public static float levelSpeedBonus(int level) {
        return 0.02f * level;
    }

    /** Melee damage factor: +0.5% per positive level, -2% per negative level (R196 code over wiki). */
    public static float meleeFactor(int level) {
        return level >= 0 ? 1 + 0.005f * level : 1 + 0.02f * level;
    }

    /** XP after death under the R196 rule: 0 if at least 30, otherwise xp - 30, floored at -800. */
    public static int xpAfterDeath(int xp) {
        return xp >= 30 ? 0 : Math.max(MIN_XP, xp - 30);
    }

    // ---- Tools (EF-OUT-04..06) ----

    public static int toolDurability(ToolType type, Material material, Quality quality) {
        return Math.round(400 * type.components * material.durability * quality.durabilityModifier);
    }

    /** Mining speed on efficient blocks: 4.0 x material efficiency, hatchet x0.5, battle axe / war hammer / mattock x0.75 (EF-OUT-04, 08, 09). */
    public static float toolSpeed(ToolType type, Material material) {
        float factor = switch (type) {
            case HATCHET -> 0.5f;
            case BATTLE_AXE, WAR_HAMMER, MATTOCK -> 0.75f;
            default -> 1f;
        };
        return 4.0f * material.efficiency * factor;
    }

    public static int blockWear(float hardness, ToolType type) {
        float rate = type.blockRate;
        return Math.max(Math.round(Math.max(hardness * 100 * rate, 100 * rate / 20)), 1);
    }

    public static int attackWear(ToolType type) {
        return Math.max(Math.round(100 * type.attackRate), 1);
    }

    // ---- Crafting (EF-CRA-01) ----

    public static final int MIN_CRAFT_TICKS = 25;

    public static int rawCraftPeriod(float difficulty) {
        if (difficulty < 25) return MIN_CRAFT_TICKS;
        if (difficulty <= 100) return Math.round(difficulty);
        return (int) Math.round(Math.pow(difficulty - 100, 0.8)) + 100;
    }

    public static int craftTicks(float difficulty, int playerLevel, float benchBonus, boolean clumsy) {
        int period = rawCraftPeriod(difficulty) * (clumsy ? 2 : 1);
        return Math.max(Math.round(period / (1 + levelSpeedBonus(playerLevel) + benchBonus)), MIN_CRAFT_TICKS);
    }

    // ---- Metabolism (EF-NUT-01, 04, 05, 06) ----

    /** Hunger points accrued per tick at rest; one food unit is consumed per 4 points (= 2000 ticks). */
    public static final float BASE_METABOLISM = 0.002f;
    public static final float HUNGER_PER_UNIT = 4.0f;
    public static final int NUTRIENT_MAX = 160_000;
    public static final int NUTRIENT_PER_FOOD_POINT = 8_000;
    public static final int STARVATION_INTERVAL = 500;

    /**
     * EF-NUT-04: the Regeneration effect no longer heals a player by itself (RegenerationMobEffectMixin); it adds the vanilla
     * rate (1 HP per 50 >> amplifier ticks) to the MITE healing progress, so each HP still costs a hunger point [HORS-MITE, provisoire].
     */
    public static float regenEffectPerTick(int amplifier) {
        return 1f / Math.max(50 >> Math.min(amplifier, 31), 1);
    }

    public static float regenPerTick(int nutrition, boolean malnourished, boolean inBed) {
        float rate = 0.0004f + nutrition * 0.00002f;
        if (malnourished) rate *= 0.25f;
        if (inBed) rate *= 4;
        return rate;
    }

    /** Hunger multiplier: 1 + humidity + malnutrition (EF-NUT-03). */
    public static float hungerMultiplier(float humidity, boolean malnourished) {
        return 1 + humidity + (malnourished ? 0.5f : 0);
    }

    /** Biome classes of EF-NUT-03: frozen doubles humidity, hot cancels it. */
    public enum Climate { TEMPERATE, FROZEN, HOT }

    /**
     * EF-NUT-03 humidity: rain +0.25 (storm +0.5), feet in water +0.25, head under water +0.5; the max of rain and immersion,
     * +0.125 for rain without storm with feet in water. Icy water (frozen biome) doubles the immersion only, warm water (hot
     * biome) cancels everything; snowfall is not rain (changelog R118: "icy water +50 %/+100 %", "snow +0 %").
     */
    public static float humidity(boolean rain, boolean storm, boolean feetInWater, boolean headInWater, Climate climate) {
        if (climate == Climate.HOT) return 0;
        float sky = storm ? 0.5f : rain ? 0.25f : 0;
        float immersion = (headInWater ? 0.5f : feetInWater ? 0.25f : 0) * (climate == Climate.FROZEN ? 2 : 1);
        return Math.max(sky, immersion) + (rain && !storm && feetInWater ? 0.125f : 0);
    }

    // ---- Insulin resistance (EF-NUT-07) ----

    public static final float INSULIN_PER_SUGAR = 4.8f;
    public static final int INSULIN_MAX = 192_000;
    /** Light, moderate, severe thresholds; level 0 below the first. */
    public static final int[] INSULIN_THRESHOLDS = {48_000, 96_000, 144_000};
    public static final int INSULIN_NAUSEA_TICKS = 400;

    /** Raw level 0..3 of a resistance value. */
    public static int insulinLevel(int resistance) {
        int level = 0;
        while (level < INSULIN_THRESHOLDS.length && resistance >= INSULIN_THRESHOLDS[level]) level++;
        return level;
    }

    /** Severe state: Poison for max(response / 48, 100) ticks. */
    public static int insulinPoisonTicks(int response) {
        return Math.max(response / 48, 100);
    }

    /** From the moderate level, a sugary food loses sugar / 1000 satiety (at least 1). */
    public static int insulinSatietyPenalty(int sugar) {
        return Math.max(sugar / 1000, 1);
    }

    /** A second counter drains nutrition alone at 25% of the base rate, whatever the satiety (EF-NUT-01): 1 point / 8000 ticks. */
    public static final float NUTRITION_DRAIN = BASE_METABOLISM * 0.25f;
    /** Zero food energy: walk x0.75; mining and sprint are impossible (EF-NUT-05, project owner's decision). */
    public static final float ZERO_ENERGY_WALK = 0.75f;

    /**
     * EF-NUT-02 MITE action costs absent from vanilla, in exhaustion (1 exhaustion = 1 MITE hunger point) [HORS-MITE, provisoire:
     * the R196 values are not public]. Placing and tilling cost a 1.6.4 block break (0.025), a throw a vanilla attack (0.1),
     * a drawn bow 0.005 per tick (a full 20-tick draw = one attack).
     */
    public static final float HUNGER_PLACE = 0.025f;
    public static final float HUNGER_TILL = 0.025f;
    public static final float HUNGER_THROW = 0.1f;
    public static final float HUNGER_BOW_TICK = 0.005f;
    /** Vanilla 26.3 block break cost, which Endurance also reduces (changelog R145). */
    public static final float VANILLA_BREAK = 0.005f;
    /** Vanilla 26.3 jump costs (walking, sprinting). */
    public static final float VANILLA_JUMP = 0.05f, VANILLA_SPRINT_JUMP = 0.2f;
    /**
     * C-2: the 1.6.4 costs the R117/R118 hunger rates were tuned on (changelog l.833, l.782), replacing the 5x lower 26.3 ones:
     * block break 0.025 (now as dear as placing one), attack 0.3, jump 0.2, sprint jump 0.8 [HORS-MITE base values, Q08 playtest].
     */
    public static final float HUNGER_BREAK = 0.025f, HUNGER_ATTACK = 0.3f, HUNGER_JUMP = 0.2f, HUNGER_SPRINT_JUMP = 0.8f;

    /** Vanilla foodLevel mirrored from MITE nutrition (docs/02 §6.3.1, P7): round(nutrition × 20 / cap), in [0, 20]. */
    public static int foodLevelMirror(int nutrition, int cap) {
        return Math.clamp(Math.round(nutrition * 20f / cap), 0, 20);
    }

    // ---- Furnaces (EF-MET-05) ----

    public static int effectiveHeat(int fuelHeat, int furnaceMaxHeat) {
        return Math.min(fuelHeat, furnaceMaxHeat);
    }

    /** A recipe progresses only if the effective heat reaches the heat required by its input. */
    public static boolean canSmelt(int fuelHeat, int furnaceMaxHeat, int requiredHeat) {
        return effectiveHeat(fuelHeat, furnaceMaxHeat) >= requiredHeat;
    }

    // ---- World (EF-MON-01) ----

    /** Stretches 1.6.4 depths below sea level onto the modern -64..63 range: y' = 2y - 64. */
    public static int mapDepth(int legacyY) {
        return legacyY < 64 ? 2 * legacyY - 64 : legacyY;
    }

    /** Metal veins grow with depth: size x (1.5 - relative height in [minY, maxY]), at least 1 block (EF-MON-02). */
    public static int depthScaledVeinSize(int size, int y, int minY, int maxY) {
        float relative = maxY > minY ? Math.clamp((float) (y - minY) / (maxY - minY), 0f, 1f) : 0f;
        return Math.max(1, Math.round(size * (1.5f - relative)));
    }
}
