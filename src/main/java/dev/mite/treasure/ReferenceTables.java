package dev.mite.treasure;

import com.google.common.hash.Hashing;
import dev.mite.combat.CombatContent;
import dev.mite.combat.CombatFormulas;
import dev.mite.core.Formulas;
import dev.mite.core.Quality;
import dev.mite.harvest.ToolStats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;

/**
 * A08 (MITE's {@code reference/*.txt}): datagen writes Markdown tables under {@code reference/} for the player documentation (Q09).
 * Recipes are not listed: the vanilla recipe book shows them in game.
 */
public final class ReferenceTables implements DataProvider {
    private final PackOutput output;

    public ReferenceTables(PackOutput output) {
        this.output = output;
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static String name(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    /** Armor pieces: protection and durability at average quality (EF-CBT-03, EF-CBT-05). */
    public static String armor() {
        Map<String, String> rows = new TreeMap<>();
        for (CombatContent.ArmorEntry a : CombatContent.ARMORS)
            rows.put(id(a.item().get()), String.format(Locale.ROOT, "| %s | %s | %s | %s | %.2f | %d |", id(a.item().get()), name(a.material()), a.type().getName(),
                    a.chain() ? "chainmail" : "plate", a.protection(), a.durability()));
        return "| Item | Material | Piece | Kind | Protection | Durability |\n|---|---|---|---|---|---|\n" + String.join("\n", rows.values()) + "\n";
    }

    /** Tools and weapons: damage and durability at average quality (EF-CBT-01, EF-OUT-05). */
    public static String tools() {
        Map<String, String> rows = new TreeMap<>();
        ToolStats.all().forEach((item, t) -> rows.put(id(item), "| %s | %s | %s | %d | %d |".formatted(id(item), name(t.type()), name(t.material()),
                CombatFormulas.damage(t.type(), t.material()), Formulas.toolDurability(t.type(), t.material(), Quality.AVERAGE))));
        return "| Item | Type | Material | Damage | Durability |\n|---|---|---|---|---|\n" + String.join("\n", rows.values()) + "\n";
    }

    /** Coins: XP value and minting cost (EF-ENC-07). */
    public static String coins() {
        StringBuilder b = new StringBuilder("| Item | XP value | Minting cost (XP) |\n|---|---|---|\n");
        TreasureContent.COIN_TABLE.forEach(c -> b.append("| %s | %d | %d |\n".formatted(id(TreasureContent.COINS.get(c.material()).get()), c.value(), c.value())));
        return b.toString();
    }

    @Override
    @SuppressWarnings("deprecation") // vanilla data providers hash with Guava's sha1 too (CachedOutput contract)
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.runAsync(() -> Map.of("armor", armor(), "tools", tools(), "coins", coins()).forEach((file, text) -> {
            byte[] bytes = ("# MITE reference: " + file + "\n\n" + text).getBytes(StandardCharsets.UTF_8);
            try {
                cache.writeIfNeeded(output.getOutputFolder().resolve("reference/" + file + ".md"), bytes, Hashing.sha1().hashBytes(bytes));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    @Override
    public String getName() {
        return "Reference tables - hardscrabble";
    }
}
