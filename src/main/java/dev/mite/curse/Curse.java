package dev.mite.curse;

import java.util.Locale;

/** The 16 witch curses (EF-MAL-02), in table order. Behaviour lives in {@link CurseEffects}. */
public enum Curse {
    EQUIPMENT_DECAY, CANT_HOLD_BREATH, CANT_RUN, CANT_EAT_ANIMALS, CANT_EAT_PLANTS, CANT_DRINK, ENDERMEN_ENEMY, CLUMSINESS,
    ENTANGLEMENT, CANT_WEAR_ARMOR, CANT_OPEN_CHESTS, CANT_SLEEP, FEAR_OF_SPIDERS, FEAR_OF_WOLVES, FEAR_OF_CREEPERS, FEAR_OF_UNDEAD;

    /** Lowercase id, as in sprites, lang keys and /stats. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "hardscrabble.curse." + id();
    }
}
