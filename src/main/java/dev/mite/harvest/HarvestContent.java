package dev.mite.harvest;

import net.minecraft.core.registries.BuiltInRegistries;
import static dev.mite.core.Material.*;
import static dev.mite.core.ToolType.*;

import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.core.Quality;
import dev.mite.core.ToolType;
import dev.mite.harvest.ToolStats.ToolInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.component.BlockTransformers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Harvest domain: MITE tools (EF-OUT-08), re-statted vanilla tools (EF-OUT-05) and removed vanilla tools (EF-OUT-07). */
public final class HarvestContent {
    private HarvestContent() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    public record ToolEntry(DeferredItem<Item> item, ToolInfo info) {
        public String langKey() {
            return "item.hardscrabble." + item.getId().getPath();
        }

        public String en() {
            String m = switch (info.material()) {
                case WOOD -> "Wooden";
                case GOLD -> "Golden";
                case ANCIENT_METAL -> "Ancient Metal";
                default -> info.material().name().charAt(0) + info.material().name().substring(1).toLowerCase(Locale.ROOT);
            };
            String t = java.util.Arrays.stream(info.type().name().split("_"))
                    .map(w -> w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.joining(" "));
            return m + " " + t;
        }

        public String fr() {
            String t = switch (info.type()) {
                case PICKAXE -> "Pioche";
                case AXE -> "Hache";
                case SHOVEL -> "Pelle";
                case HOE -> "Houe";
                case SWORD -> "Épée";
                case HATCHET -> "Hachette";
                case DAGGER -> "Dague";
                case KNIFE -> "Couteau";
                case CUDGEL -> "Gourdin";
                case CLUB -> "Massue";
                case BATTLE_AXE -> "Hache de bataille";
                case WAR_HAMMER -> "Marteau de guerre";
                case MATTOCK -> "Hoyau";
                case SCYTHE -> "Faux";
                case SHEARS -> "Cisailles";
                default -> throw new IllegalStateException(info.type().name());
            };
            String m = switch (info.material()) {
                case WOOD -> "bois";
                case FLINT -> "silex";
                case OBSIDIAN -> "obsidienne";
                case COPPER -> "cuivre";
                case IRON -> "fer";
                case GOLD -> "or";
                case SILVER -> "argent";
                case ANCIENT_METAL -> "métal ancien";
                case MITHRIL -> "mithril";
                case ADAMANTIUM -> "adamantium";
                default -> throw new IllegalStateException(info.material().name());
            };
            return t + " en " + m;
        }
    }

    /** Every harvest tool, registered from the ToolType x Material table below (EF-OUT-08). */
    public static final List<ToolEntry> TOOLS = new ArrayList<>();
    public static final List<ToolType> HYBRIDS = List.of(BATTLE_AXE, WAR_HAMMER, MATTOCK, SCYTHE);

    /** String or sinew: the lashing flint and obsidian tool recipes need (EF-OUT-12). */
    public static final TagKey<Item> LASHINGS = TagKey.create(Registries.ITEM, MiteMod.id("lashings"));

    static {
        for (Material m : List.of(FLINT, OBSIDIAN)) for (ToolType t : List.of(HATCHET, KNIFE, AXE, SHOVEL)) tool(t, m);
        tool(CUDGEL, WOOD);
        tool(CLUB, WOOD);
        // Copper, iron and gold reuse the vanilla pickaxe/axe/shovel/hoe/sword (ToolStats.VANILLA).
        for (Material m : List.of(COPPER, IRON, GOLD)) for (ToolType t : List.of(HATCHET, DAGGER)) tool(t, m);
        for (Material m : List.of(SILVER, ANCIENT_METAL, MITHRIL, ADAMANTIUM))
            for (ToolType t : List.of(PICKAXE, AXE, SHOVEL, HOE, SWORD, HATCHET, DAGGER)) tool(t, m);
        // EF-OUT-09 hybrid tools, every metal.
        for (Material m : List.of(COPPER, SILVER, GOLD, IRON, ANCIENT_METAL, MITHRIL, ADAMANTIUM))
            for (ToolType t : HYBRIDS) tool(t, m);
        // EF-OUT-13 / D15: shears in every metal; iron keeps the vanilla shears (ToolStats.VANILLA).
        for (Material m : List.of(COPPER, SILVER, GOLD, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) tool(SHEARS, m);
    }

    /** Wooden (except the shovel), stone and diamond tools and diamond armor: no recipe, hidden from creative tabs (EF-OUT-07). */
    public static final List<Item> REMOVED = List.of(
            Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SWORD, Items.WOODEN_HOE, Items.WOODEN_SPEAR,
            Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_HOE, Items.STONE_SPEAR,
            Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.DIAMOND_HOE, Items.DIAMOND_SPEAR,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.DIAMOND_HORSE_ARMOR, Items.DIAMOND_NAUTILUS_ARMOR,
            Items.FISHING_ROD); // D17: replaced by the hooked rods of the equipment domain

    /** The tool of that type and material registered here (for example the wooden club); throws if there is none. */
    public static Item item(ToolType type, Material material) {
        return TOOLS.stream().filter(t -> t.info().type() == type && t.info().material() == material).findFirst().orElseThrow().item().get();
    }

    static String name(ToolType type, Material material) {
        String m = switch (material) {
            case WOOD -> "wooden";
            case GOLD -> "golden";
            default -> material.name().toLowerCase(Locale.ROOT);
        };
        return m + "_" + type.name().toLowerCase(Locale.ROOT);
    }

    private static void tool(ToolType type, Material material) {
        DeferredItem<Item> item = ITEMS.registerItem(name(type, material), props -> {
            props.durability(Formulas.toolDurability(type, material, Quality.AVERAGE))
                    .component(DataComponents.WEAPON, new Weapon(1, type == AXE || type == BATTLE_AXE ? Weapon.AXE_DISABLES_BLOCKING_FOR_SECONDS : 0));
            switch (type) {
                case AXE, HATCHET, BATTLE_AXE -> props.delayedComponent(DataComponents.BLOCK_TRANSFORMER, c -> c.getOrThrow(BlockTransformers.AXE));
                case SHOVEL -> props.delayedComponent(DataComponents.BLOCK_TRANSFORMER, c -> c.getOrThrow(BlockTransformers.SHOVEL));
                case HOE, MATTOCK -> props.delayedComponent(DataComponents.BLOCK_TRANSFORMER, c -> c.getOrThrow(BlockTransformers.HOE));
                default -> {}
            }
            return type == SHEARS ? new net.minecraft.world.item.ShearsItem(props) : new Item(props);
        });
        TOOLS.add(new ToolEntry(item, new ToolInfo(type, material)));
    }

    /** The natural sandstone blocks of the hardness table (level 1, changelog l.761). */
    public static final List<Block> SANDSTONE = List.of(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
            Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE);

    /** Tool component: MITE speed on the efficient blocks, no vanilla block wear (wear is applied by HarvestRules, EF-OUT-06). */
    static Tool toolComponent(ToolInfo info, HolderLookup.Provider context) {
        HolderGetter<Block> blocks = context.lookupOrThrow(Registries.BLOCK);
        float speed = Formulas.toolSpeed(info.type(), info.material());
        // Hybrids are efficient on the union of their parents' tags (EF-OUT-09).
        List<TagKey<Block>> efficient = switch (info.type()) {
            case PICKAXE, WAR_HAMMER -> List.of(BlockTags.MINEABLE_WITH_PICKAXE);
            case AXE, HATCHET, BATTLE_AXE -> List.of(BlockTags.MINEABLE_WITH_AXE);
            case SHOVEL -> List.of(BlockTags.MINEABLE_WITH_SHOVEL);
            case HOE -> List.of(BlockTags.MINEABLE_WITH_HOE);
            case MATTOCK -> List.of(BlockTags.MINEABLE_WITH_HOE, BlockTags.MINEABLE_WITH_SHOVEL);
            case SCYTHE -> List.of(BlockTags.MINEABLE_WITH_HOE, BlockTags.SWORD_EFFICIENT); // parents [INFÉRÉ]: hoe and the sword's plants
            default -> List.of();
        };
        if (!efficient.isEmpty()) {
            List<Tool.Rule> rules = new ArrayList<>(efficient.stream().map(t -> Tool.Rule.minesAndDrops(blocks.getOrThrow(t), speed)).toList());
            // Changelog R156 (l.292): axes are effective against clay and hardened clay.
            if (efficient.contains(BlockTags.MINEABLE_WITH_AXE)) {
                rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.TERRACOTTA), speed));
                rules.add(Tool.Rule.minesAndDrops(HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.CLAY)), speed));
                // Changelog l.761 (R119): flint hatchets and axes harvest sandstone (level 1 block).
                rules.add(Tool.Rule.minesAndDrops(HolderSet.direct(SANDSTONE.stream().map(BuiltInRegistries.BLOCK::wrapAsHolder).toList()), speed));
            }
            return new Tool(rules, 1, 0, true);
        }
        // Shears and blades: vanilla speeds are the iron values, scaled by the material efficiency (D15, EF-OUT-04).
        float scale = info.material().efficiency / Material.IRON.efficiency;
        if (info.type() == SHEARS) return new Tool(List.of(
                Tool.Rule.minesAndDrops(HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBWEB)), 15 * scale),
                Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SHEARS_EXTREME_BREAKING_SPEED), 15 * scale),
                Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SHEARS_MAJOR_BREAKING_SPEED), 5 * scale),
                Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SHEARS_MINOR_BREAKING_SPEED), 2 * scale)), 1, 0, true);
        if (info.type() == SWORD || info.type() == DAGGER || info.type() == KNIFE) {
            // Changelog R127 (l.608): swords, daggers and knives are effective against hay and carpets.
            return new Tool(List.of(
                    Tool.Rule.minesAndDrops(HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBWEB)), 15 * scale),
                    Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
                    Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5f * scale),
                    Tool.Rule.overrideSpeed(HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.HAY_BLOCK)), 1.5f * scale),
                    Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.WOOL_CARPETS), 1.5f * scale)), 1, 0, false);
        }
        return new Tool(List.of(), 1, 0, true);
    }

    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        ToolStats.all().forEach((item, info) -> event.modify(item, (components, context, _) -> {
            Weapon weapon = components.get(DataComponents.WEAPON);
            components.set(DataComponents.MAX_DAMAGE, Formulas.toolDurability(info.type(), info.material(), Quality.AVERAGE));
            components.set(DataComponents.TOOL, toolComponent(info, context));
            components.set(DataComponents.WEAPON, new Weapon(Formulas.attackWear(info.type()), weapon == null ? 0 : weapon.disableBlockingForSeconds()));
            if (info.material().enchantability > 0) components.set(DataComponents.ENCHANTABLE, new Enchantable(info.material().enchantability));
            if (info.type() != DAGGER && info.type() != HATCHET && info.type() != SPEAR) components.set(DataComponents.BLOCKS_ATTACKS, parry(info.type(), context));
        }));
    }

    /**
     * EF-OUT-11: weapons and tools parry (use key) except the dagger, the hatchet and the spear (its use key is the charge). A parry halves frontal damage, like the 1.6.4
     * sword block [INFÉRÉ: the spec gives no ratio], and wears the item by its attack wear (EF-OUT-06 attack rate).
     */
    static BlocksAttacks parry(ToolType type, HolderLookup.Provider context) {
        return new BlocksAttacks(0, 1, List.of(new BlocksAttacks.DamageReduction(90, Optional.empty(), 0, 0.5f)),
                new BlocksAttacks.ItemDamageFunction(0, Formulas.attackWear(type), 0),
                Optional.of(context.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                Optional.of(SoundEvents.SHIELD_BLOCK), Optional.empty());
    }

    private static void hideRemoved(BuildCreativeModeTabContentsEvent event) {
        event.removeIf(stack -> REMOVED.contains(stack.getItem()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(HarvestContent::modifyComponents);
        modBus.addListener(HarvestContent::hideRemoved);
        modBus.addListener((RegisterDataMapTypesEvent e) -> e.register(HarvestRules.BLOCK_HARDNESS));
        NeoForge.EVENT_BUS.addListener(HarvestRules::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(HarvestRules::onHarvestCheck);
        NeoForge.EVENT_BUS.addListener(HarvestRules::onBlockDrops);
    }
}
