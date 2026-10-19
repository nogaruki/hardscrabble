package dev.mite.combat;

import dev.mite.core.Material;
import dev.mite.core.ToolType;

/** Pure combat formulas: weapon damage and attack speed (EF-CBT-01, EF-CBT-02), armor per material (EF-CBT-03). No game imports. */
public final class CombatFormulas {
    private CombatFormulas() {}

    /** Player attack damage and attack speed without an item (vanilla attribute bases). */
    public static final double PLAYER_BASE_DAMAGE = 1, PLAYER_BASE_SPEED = 4;

    /** Base damage of a weapon type (EF-CBT-01, analysis/02 §2.2). CUDGEL is the 1-component gourdin, CLUB the 2-component massue. */
    public static int baseDamage(ToolType type) {
        return switch (type) {
            case SWORD, BATTLE_AXE -> 4;
            case AXE -> 3;
            case PICKAXE, DAGGER, HATCHET, WAR_HAMMER, CLUB, SPEAR -> 2; // spear [HORS-MITE]: a 1-ingot blade like the dagger
            case SHOVEL, HOE, MATTOCK, SCYTHE, KNIFE, CUDGEL -> 1;
            case SHEARS -> 0;
        };
    }

    /** Damage dealt by a hit with a full cooldown, player bonuses excluded (EF-CBT-01). Wooden club and cudgel add no material damage. */
    public static int damage(ToolType type, Material material) {
        return baseDamage(type) + (material == Material.WOOD ? 0 : material.damage);
    }

    /** Attacks per second with the modern cooldown (EF-CBT-02, [HORS-MITE] initial values, to calibrate in playtest Q08). */
    public static double attacksPerSecond(ToolType type) {
        return switch (type) {
            case DAGGER, KNIFE -> 2.0;
            case SWORD -> 1.6;
            case CUDGEL -> 1.4;
            case HATCHET, PICKAXE -> 1.2;
            case MATTOCK, AXE -> 0.9;
            case WAR_HAMMER -> 0.8;
            default -> 1.0; // battle axe, club, shovel, hoe, shears [INFÉRÉ: shears not listed]
        };
    }

    /** Seconds to deal {@code health} with charged hits, first hit at t = 0 (calibration table, EF-CBT-02 CA-2). */
    public static double timeToKill(ToolType type, Material material, float health) {
        int hits = (int) Math.ceil(health / damage(type, material));
        return (hits - 1) / attacksPerSecond(type);
    }

    // ---- Armor (EF-CBT-03) ----

    /** Armor piece components: helmet 5, chestplate 8, leggings 7, boots 4 (24 per set). */
    public static final int HELMET = 5, CHESTPLATE = 8, LEGGINGS = 7, BOOTS = 4;

    /** Full-set protection of a material, plate or chainmail (chainmail = plate - 2). */
    public static int protection(Material material, boolean chain) {
        int plate = switch (material) {
            case LEATHER -> 2; // no leather chainmail
            case RUSTED_IRON, GOLD -> 6;
            case COPPER, SILVER -> 7;
            case IRON, ANCIENT_METAL -> 8;
            case MITHRIL -> 9;
            case ADAMANTIUM -> 10;
            default -> throw new IllegalArgumentException(material.name());
        };
        return chain ? plate - 2 : plate;
    }

    /**
     * Armor points of one piece: components x protection / 24, rounded up to 1e-4 so that a full set never floors
     * below the material value (vanilla floors the armor attribute).
     */
    public static double pieceProtection(int components, Material material, boolean chain) {
        return Math.ceil(components * protection(material, chain) / 24.0 * 1e4) / 1e4;
    }

    /** Wear multiplier: armor durability is on the x100 scale of tools (EF-OUT-05), so each vanilla wear point counts 100. */
    public static final int ARMOR_WEAR_SCALE = 100;

    /** Durability of one piece, average quality: components x material x 2 (plate) or x 1 (chainmail), x100 scale. */
    public static int armorDurability(int components, Material material, boolean chain) {
        return Math.round(components * material.durability * (chain ? 1 : 2) * ARMOR_WEAR_SCALE);
    }

    /** EF-CBT-04: protection factor of a worn piece, full down to 50 % wear then linear to 0. Non-player wearers use {@link #MOB_ARMOR_FACTOR}. */
    public static double wearFactor(int damage, int maxDamage) {
        return maxDamage <= 0 ? 1 : Math.max(0, Math.min(1, 2 - 2.0 * damage / maxDamage));
    }

    /** EF-CBT-04: fixed protection factor of armor worn by non-player entities. */
    public static final double MOB_ARMOR_FACTOR = 0.5;

    /** Chance of half a heart when protection meets the damage (changelog R134) [INFÉRÉ: value not given, Q08]. */
    public static final float HALF_HEART_CHANCE = 0.5f;

    /**
     * B2 / EF-CBT-03: armor is a flat value taken off the damage, not a percentage (changelog R127). When it meets or exceeds
     * the damage there is only a chance of half a heart (R134, prevails over the CDC "at least 1 point").
     * {@code roll} is uniform in [0, 1).
     */
    public static float damageAfterArmor(float damage, double protection, float roll) {
        if (damage > protection) return (float) (damage - protection);
        return roll < HALF_HEART_CHANCE ? Math.min(1, damage) : 0;
    }

    /** dom-G-13 [INFÉRÉ]: horse armor = plate protection x the non-player factor (changelog R127 "horse armor values decreased"). */
    public static double horseArmor(Material material) {
        return protection(material, false) * MOB_ARMOR_FACTOR;
    }

    // ---- Bows and arrows (EF-CBT-07, EF-CBT-08) ----

    /** EF-CBT-07: bow durability (one point per shot) and arrow speed multiplier, wood / ancient metal / mithril. */
    public static int bowDurability(Material material) {
        return switch (material) {
            case ANCIENT_METAL -> 64;
            case MITHRIL -> 128;
            default -> 32;
        };
    }

    /** [HORS-MITE] crossbow (M12): iron-tier recipe, never outlasts the ancient metal bow. */
    public static final int CROSSBOW_DURABILITY = 64;

    /**
     * [HORS-MITE] spear (arbitrage n°22, audit 2 m2/G-1): survival attack reach of the other weapons (vanilla spear 4.5) and creative 5.
     * The vanilla 2-block minimum reach is dropped [INFÉRÉ], else the spear could only hit between 2 and 3 blocks.
     */
    public static final float SPEAR_REACH = 3, SPEAR_CREATIVE_REACH = 5;

    /** [INFÉRÉ] relative speed (blocks/s) of a charge at a fast horse's top speed, where the charge deals the MITE spear damage. */
    public static final float CHARGE_REFERENCE_SPEED = 14;

    /**
     * Spear charge (KINETIC_WEAPON): vanilla deals {@code player base + floor(relative speed x multiplier)}, 0.82 to 0.95 per block/s
     * whatever the material, so a mounted copper spear outdid an adamantium sword. Scaled to the MITE spear damage of the material:
     * a charge at {@link #CHARGE_REFERENCE_SPEED} deals {@code damage(SPEAR, material)} above the base.
     */
    public static float chargeMultiplier(Material material) {
        return damage(ToolType.SPEAR, material) / CHARGE_REFERENCE_SPEED;
    }

    /** [HORS-MITE] trident (M12): restatted as an iron spear, melee and thrown; vanilla throws for 8. */
    public static final int VANILLA_THROWN_TRIDENT = 8;

    public static float bowVelocity(Material material) {
        return switch (material) {
            case ANCIENT_METAL -> 1.1f;
            case MITHRIL -> 1.25f;
            default -> 1f;
        };
    }

    /** EF-CBT-08: arrow base damage = 0.5 + tip material damage x 0.5 (vanilla then multiplies by the speed). */
    public static double arrowDamage(Material tip) {
        return 0.5 + tip.damage * 0.5;
    }

    /** EF-CBT-08: chance that an arrow survives hitting a block. Ancient metal [NON VÉRIFIÉ]: iron value. */
    public static float arrowRecovery(Material tip) {
        return switch (tip) {
            case FLINT -> 0.3f;
            case OBSIDIAN -> 0.4f;
            case GOLD, RUSTED_IRON -> 0.5f;
            case COPPER, SILVER -> 0.6f;
            case IRON, ANCIENT_METAL -> 0.7f;
            case MITHRIL -> 0.8f;
            case ADAMANTIUM -> 0.9f;
            default -> throw new IllegalArgumentException(tip.name());
        };
    }
}
