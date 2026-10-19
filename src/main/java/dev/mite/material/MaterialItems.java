package dev.mite.material;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Raw materials shared by every domain: shards, nuggets, ingots and raw ores (EF-MET-01, EF-MET-03, EF-MET-08).
 * Copper, iron and gold reuse the vanilla items (arbitrage n°1).
 */
public final class MaterialItems {
    private MaterialItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    public static final DeferredItem<Item> FLINT_SHARD = ITEMS.registerSimpleItem("flint_shard");
    public static final DeferredItem<Item> OBSIDIAN_SHARD = ITEMS.registerSimpleItem("obsidian_shard");
    public static final DeferredItem<Item> EMERALD_SHARD = ITEMS.registerSimpleItem("emerald_shard");
    public static final DeferredItem<Item> DIAMOND_SHARD = ITEMS.registerSimpleItem("diamond_shard");
    public static final DeferredItem<Item> QUARTZ_SHARD = ITEMS.registerSimpleItem("quartz_shard");
    public static final DeferredItem<Item> GLASS_SHARD = ITEMS.registerSimpleItem("glass_shard");

    public static final DeferredItem<Item> SILVER_NUGGET = ITEMS.registerSimpleItem("silver_nugget");
    public static final DeferredItem<Item> SILVER_INGOT = ITEMS.registerSimpleItem("silver_ingot");
    public static final DeferredItem<Item> RAW_SILVER = ITEMS.registerSimpleItem("raw_silver");
    public static final DeferredItem<Item> ANCIENT_METAL_NUGGET = ITEMS.registerSimpleItem("ancient_metal_nugget");
    public static final DeferredItem<Item> ANCIENT_METAL_INGOT = ITEMS.registerSimpleItem("ancient_metal_ingot");
    public static final DeferredItem<Item> MITHRIL_NUGGET = ITEMS.registerSimpleItem("mithril_nugget");
    public static final DeferredItem<Item> MITHRIL_INGOT = ITEMS.registerSimpleItem("mithril_ingot");
    public static final DeferredItem<Item> RAW_MITHRIL = ITEMS.registerSimpleItem("raw_mithril");
    public static final DeferredItem<Item> ADAMANTIUM_NUGGET = ITEMS.registerSimpleItem("adamantium_nugget");
    public static final DeferredItem<Item> ADAMANTIUM_INGOT = ITEMS.registerSimpleItem("adamantium_ingot");
    public static final DeferredItem<Item> RAW_ADAMANTIUM = ITEMS.registerSimpleItem("raw_adamantium");
    /** Sinew: 4 per leather (EF-CRA-06), lashing for flint and obsidian tools (EF-OUT-12). */
    public static final DeferredItem<Item> SINEW = ITEMS.registerSimpleItem("sinew");

    /** The ingot of a metal (rusted iron: the iron ingot); throws for a non-metal. */
    public static Item ingot(Material m) {
        return switch (m) {
            case COPPER -> Items.COPPER_INGOT;
            case SILVER -> SILVER_INGOT.get();
            case GOLD -> Items.GOLD_INGOT;
            case IRON, RUSTED_IRON -> Items.IRON_INGOT;
            case ANCIENT_METAL -> ANCIENT_METAL_INGOT.get();
            case MITHRIL -> MITHRIL_INGOT.get();
            case ADAMANTIUM -> ADAMANTIUM_INGOT.get();
            default -> throw new IllegalArgumentException(m.name());
        };
    }

    /** The nugget of a metal (rusted iron: the iron nugget); throws for a non-metal. */
    public static Item nugget(Material m) {
        return switch (m) {
            case COPPER -> Items.COPPER_NUGGET;
            case SILVER -> SILVER_NUGGET.get();
            case GOLD -> Items.GOLD_NUGGET;
            case IRON, RUSTED_IRON -> Items.IRON_NUGGET;
            case ANCIENT_METAL -> ANCIENT_METAL_NUGGET.get();
            case MITHRIL -> MITHRIL_NUGGET.get();
            case ADAMANTIUM -> ADAMANTIUM_NUGGET.get();
            default -> throw new IllegalArgumentException(m.name());
        };
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
