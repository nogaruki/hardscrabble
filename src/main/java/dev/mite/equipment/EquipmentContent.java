package dev.mite.equipment;

import static dev.mite.core.Material.*;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Equipment domain: metal buckets (D16), hooked fishing rods (D17), worms (I14), metal doors and bars (H11). Metal shears (D15)
 * are harvest tools ({@code HarvestContent}, {@code ToolType.SHEARS}).
 */
public final class EquipmentContent {
    private EquipmentContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    /** Translations {key, en, fr}, filled while registering; written by MiteLang. */
    public static final List<String[]> LANG = new ArrayList<>();

    /** A metal's buckets (EF-OUT-13); iron keeps the vanilla ones and only adds the stone bucket. */
    public record BucketSet(Material metal, Supplier<? extends Item> empty, Supplier<? extends Item> water, Supplier<? extends Item> lava,
                            Supplier<? extends Item> milk, Supplier<? extends Item> stone) {}

    public record Rod(Material hook, DeferredItem<Fishing.RodItem> item) {}

    public static final Map<Material, BucketSet> BUCKETS = new EnumMap<>(Material.class);
    public static final List<Rod> RODS = new ArrayList<>();
    /** H11: iron and copper keep the vanilla door and bars. */
    public static final Map<Material, DeferredBlock<DoorBlock>> DOORS = new EnumMap<>(Material.class);
    public static final Map<Material, DeferredBlock<IronBarsBlock>> BARS = new EnumMap<>(Material.class);

    // EF-NUT-08 "ver cru / cuit" 0/1 and 1/1 (satiety / nutrition), values in hardscrabble:nutrition (MetabolismData).
    public static final DeferredItem<Item> WORM = food("worm", "Worm", "Ver de terre");
    public static final DeferredItem<Item> COOKED_WORM = food("cooked_worm", "Cooked Worm", "Ver cuit");

    static {
        for (Material m : List.of(COPPER, SILVER, GOLD, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) {
            String n = m.id();
            DeferredItem<Item> stone = item(n + "_stone_bucket", en(m) + " Bucket of Stone", "Seau de pierre en " + fr(m),
                    p -> new Item(p.stacksTo(1)));
            if (m == IRON) {
                BUCKETS.put(m, new BucketSet(m, () -> Items.BUCKET, () -> Items.WATER_BUCKET, () -> Items.LAVA_BUCKET, () -> Items.MILK_BUCKET, stone));
                continue;
            }
            DeferredItem<Item> empty = item(n + "_bucket", en(m) + " Bucket", "Seau en " + fr(m), p -> new Item(p.stacksTo(16)));
            BUCKETS.put(m, new BucketSet(m, empty,
                    item(n + "_water_bucket", en(m) + " Water Bucket", "Seau d'eau en " + fr(m),
                            p -> new BucketItem(Fluids.WATER, p.craftRemainder(empty.get()).stacksTo(1))),
                    item(n + "_lava_bucket", en(m) + " Lava Bucket", "Seau de lave en " + fr(m),
                            p -> new BucketItem(Fluids.LAVA, p.craftRemainder(empty.get()).cookingFuel(ContextIntProviders.COOKING_TIME_LAVA_BUCKET).stacksTo(1))),
                    item(n + "_milk_bucket", en(m) + " Milk Bucket", "Seau de lait en " + fr(m), p -> new Item(p.craftRemainder(empty.get())
                            .component(DataComponents.CONSUMABLE, Consumables.MILK_BUCKET).usingConvertsTo(empty.get()).stacksTo(1))),
                    stone));
            if (m == COPPER) continue;
            // Iron door stats; like the iron door only redstone opens them [INFÉRÉ: the spec gives no behavior].
            DeferredBlock<DoorBlock> door = BLOCKS.registerBlock(n + "_door", p -> new DoorBlock(BlockSetType.IRON, p),
                    p -> p.mapColor(MapColor.METAL).strength(5).noOcclusion().pushReaction(PushReaction.POPPED));
            ITEMS.registerItem(n + "_door", p -> new DoubleHighBlockItem(door.get(), p), Item.Properties::useBlockDescriptionPrefix);
            DOORS.put(m, door);
            LANG.add(new String[]{"block.hardscrabble." + n + "_door", en(m) + " Door", "Porte en " + fr(m)});
            DeferredBlock<IronBarsBlock> bars = BLOCKS.registerBlock(n + "_bars", IronBarsBlock::new,
                    p -> p.requiresCorrectToolForDrops().strength(5, 6).sound(SoundType.IRON).noOcclusion());
            ITEMS.registerSimpleBlockItem(bars);
            BARS.put(m, bars);
            LANG.add(new String[]{"block.hardscrabble." + n + "_bars", en(m) + " Bars", "Barreaux en " + fr(m)});
        }
        // EF-OUT-13: the hook is a nugget or a shard. Durability = vanilla 64 x material multiplier [INFÉRÉ].
        for (Material m : List.of(FLINT, OBSIDIAN, COPPER, SILVER, GOLD, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) {
            RODS.add(new Rod(m, item(m.id() + "_fishing_rod", en(m) + " Fishing Rod", "Canne à pêche (hameçon en " + fr(m) + ")",
                    p -> new Fishing.RodItem(p.durability((int) (64 * m.durability)).enchantable(1)))));
        }
    }

    /** Metal of a bucket (the vanilla iron ones included), metal door or bars of this domain; null for anything else. */
    public static @Nullable Material metalOf(Item item) {
        for (BucketSet s : BUCKETS.values())
            if (Stream.of(s.empty(), s.water(), s.lava(), s.milk(), s.stone()).anyMatch(i -> i.get() == item)) return s.metal();
        for (Material m : DOORS.keySet()) if (DOORS.get(m).get().asItem() == item || BARS.get(m).get().asItem() == item) return m;
        return null;
    }

    private static String en(Material m) {
        return m == ANCIENT_METAL ? "Ancient Metal" : m.name().charAt(0) + m.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static String fr(Material m) {
        return switch (m) {
            case FLINT -> "silex";
            case OBSIDIAN -> "obsidienne";
            case COPPER -> "cuivre";
            case SILVER -> "argent";
            case GOLD -> "or";
            case IRON -> "fer";
            case ANCIENT_METAL -> "métal ancien";
            default -> m.id();
        };
    }

    private static <I extends Item> DeferredItem<I> item(String name, String en, String fr, java.util.function.Function<Item.Properties, I> factory) {
        LANG.add(new String[]{"item.hardscrabble." + name, en, fr});
        return ITEMS.registerItem(name, factory);
    }

    private static DeferredItem<Item> food(String name, String en, String fr) {
        // The food component only drives eating (nutrition 1 for both); MITE values come from hardscrabble:nutrition.
        return item(name, en, fr, p -> new Item(p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0).build())));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(Buckets::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(Buckets::onEntityInteract);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) -> Buckets.onSubmerged(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(Fishing::onBlockDrops);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(Buckets::registerDispensers));
    }
}
