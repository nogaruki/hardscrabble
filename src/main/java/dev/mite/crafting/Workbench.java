package dev.mite.crafting;

import dev.mite.core.Material;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Workbench tiers and speed bonuses (EF-CRA-02). The vanilla crafting table is the flint workbench. */
public enum Workbench {
    FLINT(Material.FLINT, 0.2f),
    OBSIDIAN(Material.OBSIDIAN, 0.2f),
    COPPER(Material.COPPER, 0.3f),
    SILVER(Material.SILVER, 0.3f),
    GOLD(Material.GOLD, 0.3f),
    IRON(Material.IRON, 0.4f),
    ANCIENT_METAL(Material.ANCIENT_METAL, 0.5f),
    MITHRIL(Material.MITHRIL, 0.6f),
    ADAMANTIUM(Material.ADAMANTIUM, 0.7f);

    /** Bonus of any workbench for a recipe without metal. */
    public static final float NO_METAL_BONUS = 0.2f;

    public final Material material;
    public final float bonus;

    Workbench(Material material, float bonus) {
        this.material = material;
        this.bonus = bonus;
    }

    /** A recipe's hardest metal must not be more durable than the bench material; no metal is always accepted. */
    public boolean accepts(@Nullable Material hardestMetal) {
        return hardestMetal == null || material.durability >= hardestMetal.durability;
    }

    public float bonusFor(@Nullable Material hardestMetal) {
        return hardestMetal == null ? NO_METAL_BONUS : bonus;
    }

    /**
     * X8 / EF-PRG-01 (owner's decision over EF-CRA-03): a metal workbench is crafted on a bench of the previous tier, never in the
     * 2x2 grid. Returns the metal that bench must accept: flint for copper, silver and gold; gold (any copper, silver or gold
     * bench, gold being the softest at 3.0) for iron; then iron, ancient metal and mithril. Null for the flint and obsidian benches.
     */
    public @Nullable Material prerequisite() {
        return switch (this) {
            case FLINT, OBSIDIAN -> null;
            case COPPER, SILVER, GOLD -> Material.FLINT;
            case IRON -> Material.GOLD;
            case ANCIENT_METAL -> Material.IRON;
            case MITHRIL -> Material.ANCIENT_METAL;
            case ADAMANTIUM -> Material.MITHRIL;
        };
    }

    /** dom-P-08: the lowest bench that accepts a recipe's hardest metal, named in the refusal. */
    public static Workbench required(@Nullable Material hardestMetal) {
        // By bench durability, not declaration order: gold (3.0) is softer than copper and silver (4.0).
        return java.util.Arrays.stream(values()).filter(b -> b.accepts(hardestMetal))
                .min(java.util.Comparator.comparingDouble(b -> b.material.durability)).orElse(ADAMANTIUM);
    }

    public String langKey() {
        return this == FLINT ? "block.minecraft.crafting_table" : "block.hardscrabble." + blockName();
    }

    public String blockName() {
        return name().toLowerCase(Locale.ROOT) + "_workbench";
    }
}
