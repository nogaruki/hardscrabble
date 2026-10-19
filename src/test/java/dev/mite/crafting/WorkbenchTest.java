package dev.mite.crafting;

import static org.junit.jupiter.api.Assertions.*;

import dev.mite.core.Formulas;
import dev.mite.core.Material;
import org.junit.jupiter.api.Test;

/** EF-CRA-02 bench rules. */
class WorkbenchTest {
    @Test
    void tiers() {
        assertTrue(Workbench.FLINT.accepts(null));
        assertFalse(Workbench.FLINT.accepts(Material.COPPER));
        assertFalse(Workbench.OBSIDIAN.accepts(Material.COPPER));
        assertTrue(Workbench.COPPER.accepts(Material.GOLD));
        assertFalse(Workbench.IRON.accepts(Material.MITHRIL));
        assertTrue(Workbench.MITHRIL.accepts(Material.MITHRIL));
        assertTrue(Workbench.ADAMANTIUM.accepts(Material.MITHRIL));
        // R118 (owner's decision): gold is softer than copper, so a gold bench does not take copper recipes.
        assertEquals(3.0f, Material.GOLD.durability);
        assertFalse(Workbench.GOLD.accepts(Material.COPPER));
    }

    @Test
    void benchChain() {
        // X8 / EF-PRG-01: a metal bench is made on the previous tier's bench.
        assertNull(Workbench.OBSIDIAN.prerequisite());
        assertTrue(Workbench.FLINT.accepts(Workbench.COPPER.prerequisite()));
        assertFalse(Workbench.OBSIDIAN.accepts(Workbench.IRON.prerequisite()));
        assertTrue(Workbench.GOLD.accepts(Workbench.IRON.prerequisite()));
        assertFalse(Workbench.IRON.accepts(Workbench.MITHRIL.prerequisite()));
        assertTrue(Workbench.ANCIENT_METAL.accepts(Workbench.MITHRIL.prerequisite()));
        assertFalse(Workbench.ANCIENT_METAL.accepts(Workbench.ADAMANTIUM.prerequisite()));
    }

    @Test
    void bonuses() {
        assertEquals(0.2f, Workbench.ADAMANTIUM.bonusFor(null));
        assertEquals(0.3f, Workbench.COPPER.bonusFor(Material.COPPER));
        // Copper pickaxe on a copper bench: 3 ingots + 2 sticks.
        float difficulty = 3 * Material.COPPER.ingotDifficulty() + 2 * 25;
        assertEquals(293, Formulas.craftTicks(difficulty, 0, Workbench.COPPER.bonusFor(Material.COPPER), false));
    }
}
