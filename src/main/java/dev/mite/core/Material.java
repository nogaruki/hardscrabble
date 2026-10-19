package dev.mite.core;

/** Tool/armor materials and their MITE stats (EF-OUT-01). Pure Java: no game imports. */
public enum Material {
    //            durability  ench  maxQuality           dmg  efficiency  toolHarvestLevel
    WOOD(        0.5f,  10, Quality.FINE,        0, 1.0f,  0),
    FLINT(       1.0f,   0, Quality.FINE,        1, 1.25f, 1),
    OBSIDIAN(    2.0f,   0, Quality.FINE,        2, 1.5f,  1),
    RUSTED_IRON( 4.0f,   0, Quality.POOR,        2, 1.25f, 2),
    COPPER(      4.0f,  30, Quality.EXCELLENT,   3, 1.75f, 2),
    SILVER(      4.0f,  30, Quality.EXCELLENT,   3, 1.75f, 2),
    /** Durability 3.0: changelog R118 (l.790 "Gold: 2.0 -> 3.0"), never changed since; owner's decision over the CDC's 4.0. */
    GOLD(        3.0f,  50, Quality.SUPERB,      2, 1.75f, 2),
    IRON(        8.0f,  30, Quality.MASTERWORK,  4, 2.0f,  3),
    ANCIENT_METAL(16.0f, 40, Quality.MASTERWORK, 4, 2.0f,  3),
    MITHRIL(    64.0f, 100, Quality.LEGENDARY,   5, 2.5f,  4),
    ADAMANTIUM(256.0f,  40, Quality.LEGENDARY,   6, 3.0f,  5),
    /** Armor only (EF-CBT-03: multiplier 1). Enchantability and max quality [INFÉRÉ]: vanilla leather, flint. */
    LEATHER(     1.0f,  15, Quality.FINE,        0, 1.0f,  0);

    public final float durability;
    public final int enchantability;
    public final Quality maxQuality;
    public final int damage;
    public final float efficiency;
    private final int level;

    Material(float durability, int enchantability, Quality maxQuality, int damage, float efficiency, int level) {
        this.durability = durability;
        this.enchantability = enchantability;
        this.maxQuality = maxQuality;
        this.damage = damage;
        this.efficiency = efficiency;
        this.level = level;
    }

    /** Lowercase name, as used in ids ("ancient_metal"). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isMetal() {
        return this != WOOD && this != FLINT && this != OBSIDIAN && this != LEATHER;
    }

    /** Harvest level of a tool made of this material (obsidian tools are level 1, like flint). */
    public int toolHarvestLevel() {
        return level;
    }

    /** Craft difficulty of one ingot (or one flint/obsidian unit) (EF-CRA-04). */
    public float ingotDifficulty() {
        return this == FLINT ? 100f : durability * 100f;
    }
}
