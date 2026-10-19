package dev.mite.harvest;

import com.google.common.base.Suppliers;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Public API: the MITE tool type and material of an item stack, for vanilla copper/iron/gold tools and every harvest tool. */
public final class ToolStats {
    private ToolStats() {}

    public record ToolInfo(ToolType type, Material material) {}

    /** Vanilla tools kept and re-statted to MITE values (arbitrage n°1, EF-OUT-01 CA-2). The wooden shovel stays (EF-OUT-07). */
    static final Map<Item, ToolInfo> VANILLA = Map.ofEntries(
            Map.entry(Items.WOODEN_SHOVEL, new ToolInfo(ToolType.SHOVEL, Material.WOOD)),
            Map.entry(Items.COPPER_PICKAXE, new ToolInfo(ToolType.PICKAXE, Material.COPPER)),
            Map.entry(Items.COPPER_AXE, new ToolInfo(ToolType.AXE, Material.COPPER)),
            Map.entry(Items.COPPER_SHOVEL, new ToolInfo(ToolType.SHOVEL, Material.COPPER)),
            Map.entry(Items.COPPER_HOE, new ToolInfo(ToolType.HOE, Material.COPPER)),
            Map.entry(Items.COPPER_SWORD, new ToolInfo(ToolType.SWORD, Material.COPPER)),
            Map.entry(Items.IRON_PICKAXE, new ToolInfo(ToolType.PICKAXE, Material.IRON)),
            Map.entry(Items.IRON_AXE, new ToolInfo(ToolType.AXE, Material.IRON)),
            Map.entry(Items.IRON_SHOVEL, new ToolInfo(ToolType.SHOVEL, Material.IRON)),
            Map.entry(Items.IRON_HOE, new ToolInfo(ToolType.HOE, Material.IRON)),
            Map.entry(Items.IRON_SWORD, new ToolInfo(ToolType.SWORD, Material.IRON)),
            Map.entry(Items.GOLDEN_PICKAXE, new ToolInfo(ToolType.PICKAXE, Material.GOLD)),
            Map.entry(Items.GOLDEN_AXE, new ToolInfo(ToolType.AXE, Material.GOLD)),
            Map.entry(Items.GOLDEN_SHOVEL, new ToolInfo(ToolType.SHOVEL, Material.GOLD)),
            Map.entry(Items.GOLDEN_HOE, new ToolInfo(ToolType.HOE, Material.GOLD)),
            Map.entry(Items.GOLDEN_SWORD, new ToolInfo(ToolType.SWORD, Material.GOLD)),
            // M12 / arbitrage n°22: the 1.21.11 spears join the MITE table (wood, stone, diamond, netherite are removed).
            Map.entry(Items.COPPER_SPEAR, new ToolInfo(ToolType.SPEAR, Material.COPPER)),
            Map.entry(Items.IRON_SPEAR, new ToolInfo(ToolType.SPEAR, Material.IRON)),
            Map.entry(Items.GOLDEN_SPEAR, new ToolInfo(ToolType.SPEAR, Material.GOLD)),
            Map.entry(Items.SHEARS, new ToolInfo(ToolType.SHEARS, Material.IRON)));

    private static final Supplier<Map<Item, ToolInfo>> ALL = Suppliers.memoize(() -> {
        Map<Item, ToolInfo> map = new HashMap<>(VANILLA);
        HarvestContent.TOOLS.forEach(t -> map.put(t.item().get(), t.info()));
        return map;
    });

    public static Optional<ToolInfo> of(ItemStack stack) {
        return Optional.ofNullable(ALL.get().get(stack.getItem()));
    }

    /** Every MITE tool item and its stats (attack attributes are set by the combat domain). */
    public static Map<Item, ToolInfo> all() {
        return ALL.get();
    }
}
