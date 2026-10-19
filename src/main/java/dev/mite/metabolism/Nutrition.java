package dev.mite.metabolism;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Value of the {@code hardscrabble:nutrition} item data map (EF-NUT-08, EF-NUT-09). */
public record Nutrition(int satiety, int nutrition, int sugar, boolean protein, boolean phyto) {
    public static final Codec<Nutrition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 64).fieldOf("satiety").forGetter(Nutrition::satiety),
            Codec.intRange(0, 64).fieldOf("nutrition").forGetter(Nutrition::nutrition),
            Codec.intRange(0, 100_000).optionalFieldOf("sugar", 0).forGetter(Nutrition::sugar),
            Codec.BOOL.optionalFieldOf("protein", false).forGetter(Nutrition::protein),
            Codec.BOOL.optionalFieldOf("phyto", false).forGetter(Nutrition::phyto)
    ).apply(i, Nutrition::new));
}
