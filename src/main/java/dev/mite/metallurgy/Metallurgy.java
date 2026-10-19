package dev.mite.metallurgy;

import dev.mite.MiteMod;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Metallurgy content: heat-level furnaces (EF-MET-05), fuels (EF-MET-06), smelting heat (EF-MET-07). */
public final class Metallurgy {
    private Metallurgy() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MiteMod.MODID);

    // The clay furnace breaks by hand like clay; the others need a pickaxe like the vanilla furnace (block tags in MetallurgyData).
    public static final DeferredBlock<MiteFurnaceBlock> CLAY_FURNACE = furnace("clay_furnace", MapColor.CLAY, 0.6f, false);
    public static final DeferredBlock<MiteFurnaceBlock> LARGE_CLAY_FURNACE = furnace("large_clay_furnace", MapColor.TERRACOTTA_ORANGE, 1.25f, true);
    public static final DeferredBlock<MiteFurnaceBlock> SANDSTONE_FURNACE = furnace("sandstone_furnace", MapColor.SAND, 2.0f, true);
    public static final DeferredBlock<MiteFurnaceBlock> OBSIDIAN_FURNACE = furnace("obsidian_furnace", MapColor.COLOR_BLACK, 10.0f, true);
    public static final DeferredBlock<MiteFurnaceBlock> NETHERRACK_FURNACE = furnace("netherrack_furnace", MapColor.NETHER, 3.5f, true);

    public static final List<DeferredBlock<MiteFurnaceBlock>> FURNACES = List.of(CLAY_FURNACE, LARGE_CLAY_FURNACE, SANDSTONE_FURNACE, OBSIDIAN_FURNACE, NETHERRACK_FURNACE);
    public static final List<DeferredItem<BlockItem>> FURNACE_ITEMS = FURNACES.stream().map(ITEMS::registerSimpleBlockItem).toList();

    static final Supplier<BlockEntityType<MiteFurnaceBlockEntity>> FURNACE_ENTITY = BLOCK_ENTITIES.register("furnace",
            () -> new BlockEntityType<>(MiteFurnaceBlockEntity::new, FURNACES.stream().map(b -> (Block) b.get()).toArray(Block[]::new)));

    /** Paper burns 50 ticks (EF-MET-06); generated in MetallurgyData. */
    public static final ResourceKey<ContextIntProvider> TIME_PAPER = ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, MiteMod.id("cooking/time_paper"));

    /** Metal ores TNT leaves intact (EF-MET-10). */
    public static final TagKey<Block> SPARED_BY_TNT = TagKey.create(Registries.BLOCK, MiteMod.id("spared_by_tnt"));

    private static DeferredBlock<MiteFurnaceBlock> furnace(String name, MapColor color, float strength, boolean needsTool) {
        return BLOCKS.registerBlock(name, MiteFurnaceBlock::new, p -> {
            p.mapColor(color).strength(strength).lightLevel(s -> s.getValue(MiteFurnaceBlock.LIT) ? 13 : 0);
            return needsTool ? p.requiresCorrectToolForDrops() : p;
        });
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FurnaceHeat.ATTACHMENTS.register(modBus);
        Campfires.register();
        modBus.addListener((RegisterDataMapTypesEvent e) -> {
            e.register(FurnaceHeat.FUEL_HEAT);
            e.register(FurnaceHeat.SMELT_HEAT);
            e.register(FurnaceHeat.FURNACE_HEAT);
        });
        // Paper and arrows are not vanilla fuels (EF-MET-06); the arrow burns like a stick.
        modBus.addListener((ModifyDefaultComponentsEvent e) -> {
            e.modify(Items.PAPER, (c, ctx, item) -> c.set(DataComponents.COOKING_FUEL,
                    new CookingFuel(TIME_PAPER, ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER)));
            e.modify(Items.ARROW, (c, ctx, item) -> c.set(DataComponents.COOKING_FUEL,
                    new CookingFuel(ContextIntProviders.COOKING_TIME_DRY_PLANTS, ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER)));
        });
    }
}
