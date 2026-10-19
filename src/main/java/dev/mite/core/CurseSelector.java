package dev.mite.core;

import java.util.SplittableRandom;

/**
 * EF-MAL-01, ET-O-01: deterministic curse choice. The same witch (seed) always gives the same curse to the same player
 * (name hash), on every server and after restarts: {@link SplittableRandom} is a specified algorithm, and it scrambles
 * neighbouring seeds (the first {@code java.util.Random} output does not).
 */
public final class CurseSelector {
    private CurseSelector() {}

    /** Index in [0, count) of the curse a witch with {@code witchSeed} gives to the player named {@code playerName}. */
    public static int select(long witchSeed, String playerName, int count) {
        return new SplittableRandom(witchSeed + playerName.hashCode()).nextInt(count);
    }
}
