package dev.mite.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (EF-CFG-04, {@code hardscrabble-client.toml}). No client class here: the server loads this class too. */
public final class MiteClientConfig {
    private MiteClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.push("hints");
    }
    public static final ModConfigSpec.BooleanValue CONTEXTUAL_HINTS = B.comment(
            "EF-UI-06: show the one-time help messages (first bare-hand log, gravel, night, malnutrition, workbench refusal).",
            "EF-UI-06 : affiche les messages d'aide uniques (première bûche à mains nues, gravier, nuit, malnutrition, refus d'établi).")
            .define("contextual_hints", true);
    static {
        B.pop();
        B.push("hud");
    }
    public static final ModConfigSpec.BooleanValue SHOW_INDICATORS = B.comment(
            "EF-UI-01: show the nutrient, insulin and energy glyphs left of the food bar.",
            "EF-UI-01 : affiche les glyphes de nutriments, d'insuline et d'énergie à gauche de la barre de nourriture.")
            .define("show_indicators", true);
    static {
        B.pop();
        B.push("tooltip");
    }
    public static final ModConfigSpec.BooleanValue DURABILITY_AS_PERCENT = B.comment(
            "EF-UI-03: durability line in percent; false shows the remaining and maximum points.",
            "EF-UI-03 : ligne de durabilité en pourcentage ; false affiche les points restants et maximum.")
            .define("durability_as_percent", true);
    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();
}
