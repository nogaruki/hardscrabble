package dev.mite.player;

import dev.mite.core.Formulas;

/** Pure MITE XP arithmetic on the total XP (EF-JOU-02, 03, 05), kept free of game imports for unit tests. */
public final class XpCurve {
    private XpCurve() {}

    /** Cumulative XP at the max level (205 000). Total XP stays in [-800, MAX_XP]. */
    public static final int MAX_XP = Formulas.xpForLevel(Formulas.MAX_LEVEL);

    public static int clamp(long xp) {
        return (int) Math.clamp(xp, Formulas.MIN_XP, MAX_XP);
    }

    public static int add(int xp, int amount) {
        return clamp((long) xp + amount);
    }

    /** Points from the start of a level to the next: 30 + 10n from level 0, 20 below. */
    public static int span(int level) {
        return Formulas.xpForLevel(level + 1) - Formulas.xpForLevel(level);
    }

    public static int pointsInLevel(int xp) {
        return xp - Formulas.xpForLevel(Formulas.levelForXp(xp));
    }

    /** Fill of the XP bar in the current level, negative levels included. */
    public static float progress(int xp) {
        return pointsInLevel(xp) / (float) span(Formulas.levelForXp(xp));
    }

    /** Whole-level gain or cost (enchanting, anvil, /xp add levels), keeping the points inside the level when they fit. */
    public static int addLevels(int xp, int levels) {
        int target = (int) Math.clamp((long) Formulas.levelForXp(xp) + levels, Formulas.MIN_LEVEL, Formulas.MAX_LEVEL);
        return clamp(Formulas.xpForLevel(target) + Math.min(pointsInLevel(xp), span(target) - 1));
    }

    /** Death rule, config {@code player.death_penalty} (EF-JOU-05, Q-05). */
    public enum DeathPenalty {
        /** R196 code: 0 XP from 30 points, otherwise xp - 30 (floor -800). No orb. */
        R196_CODE,
        /** Wiki: a third of the XP drops as orbs and the player respawns at 0; at level 0 or below, -1 level. */
        WIKI_THIRD,
        /** mcmod.cn: -1 level, no orb. */
        MCMOD_LEVEL;

        public int xpAfterDeath(int xp) {
            return switch (this) {
                case R196_CODE -> Formulas.xpAfterDeath(xp);
                case WIKI_THIRD -> Formulas.levelForXp(xp) > 0 ? 0 : loseLevel(xp);
                case MCMOD_LEVEL -> loseLevel(xp);
            };
        }

        public int droppedXp(int xp) {
            return this == WIKI_THIRD && Formulas.levelForXp(xp) > 0 ? xp / 3 : 0;
        }

        private static int loseLevel(int xp) {
            return Formulas.xpForLevel(Math.max(Formulas.MIN_LEVEL, Formulas.levelForXp(xp) - 1));
        }
    }
}
