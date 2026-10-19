package dev.mite.worldgen;

import com.mojang.serialization.MapCodec;
import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.material.MaterialItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProviderType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Silver, mithril and adamantium ores, metal storage blocks (EF-MET-01) and the worldgen types behind EF-MON-02. */
public final class WorldgenContent {
    private WorldgenContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES = DeferredRegister.create(Registries.FEATURE_TYPE, MiteMod.MODID);
    static final DeferredRegister<HeightProviderType<?>> HEIGHT_PROVIDERS = DeferredRegister.create(Registries.HEIGHT_PROVIDER_TYPE, MiteMod.MODID);

    static {
        FEATURE_TYPES.register("depth_scaled_ore", () -> DepthScaledOreFeature.CODEC);
    }

    static final Supplier<HeightProviderType<BiasedToTopHeight>> BIASED_TO_TOP = HEIGHT_PROVIDERS.register("biased_to_top", () -> () -> BiasedToTopHeight.CODEC);

    /** An ore in stone and deepslate with the raw item it drops; MITE hardness and harvest level of the stone variant (EF-OUT-02). */
    public record Ore(Material metal, DeferredBlock<Block> stone, DeferredBlock<Block> deepslate, ItemLike raw, float hardness, int level) {}

    /** A MITE metal: its storage block (9 ingots), ingot and nugget, and the block's harvest level, the tool level of the metal. */
    public record Storage(Material metal, DeferredBlock<Block> block, ItemLike ingot, ItemLike nugget) {
        public int level() {
            return metal.toolHarvestLevel();
        }
    }

    public static final Ore SILVER_ORE = ore(Material.SILVER, MaterialItems.RAW_SILVER, 2.5f, 2);
    public static final Ore MITHRIL_ORE = ore(Material.MITHRIL, MaterialItems.RAW_MITHRIL, 3.5f, 3);
    public static final Ore ADAMANTIUM_ORE = ore(Material.ADAMANTIUM, MaterialItems.RAW_ADAMANTIUM, 4.0f, 4);
    public static final List<Ore> ORES = List.of(SILVER_ORE, MITHRIL_ORE, ADAMANTIUM_ORE);

    public static final List<Storage> STORAGE = List.of(
            storage(Material.SILVER, MaterialItems.SILVER_INGOT, MaterialItems.SILVER_NUGGET),
            storage(Material.ANCIENT_METAL, MaterialItems.ANCIENT_METAL_INGOT, MaterialItems.ANCIENT_METAL_NUGGET),
            storage(Material.MITHRIL, MaterialItems.MITHRIL_INGOT, MaterialItems.MITHRIL_NUGGET),
            storage(Material.ADAMANTIUM, MaterialItems.ADAMANTIUM_INGOT, MaterialItems.ADAMANTIUM_NUGGET));

    /** EF-DIM-05 (R172): unbreakable layer at the bottom of the Nether, under the bedrock floor. No light (dom-M-07). */
    public static final DeferredBlock<Block> CORE = BLOCKS.registerSimpleBlock("core", p -> p.mapColor(MapColor.FIRE)
            .strength(-1, 3600000).noLootTable().isValidSpawn(net.minecraft.world.level.block.Blocks::never));

    static {
        ITEMS.registerSimpleBlockItem(CORE);
    }

    public static DeferredBlock<Block> storageBlock(Material metal) {
        return STORAGE.stream().filter(s -> s.metal() == metal).findFirst().orElseThrow().block();
    }

    private static Ore ore(Material metal, DeferredItem<Item> raw, float hardness, int level) {
        DeferredBlock<Block> stone = BLOCKS.registerSimpleBlock(metal.id() + "_ore", p -> p.mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(hardness, 3));
        DeferredBlock<Block> deepslate = BLOCKS.registerSimpleBlock("deepslate_" + metal.id() + "_ore", p -> p.mapColor(MapColor.DEEPSLATE)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(hardness * 1.25f, 3).sound(SoundType.DEEPSLATE));
        ITEMS.registerSimpleBlockItem(stone);
        ITEMS.registerSimpleBlockItem(deepslate);
        return new Ore(metal, stone, deepslate, raw, hardness, level);
    }

    private static Storage storage(Material metal, DeferredItem<Item> ingot, DeferredItem<Item> nugget) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(metal.id() + "_block", p -> p.mapColor(MapColor.METAL)
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops().strength(5, 6).sound(SoundType.IRON));
        ITEMS.registerSimpleBlockItem(block);
        return new Storage(metal, block, ingot, nugget);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURE_TYPES.register(modBus);
        HEIGHT_PROVIDERS.register(modBus);
    }
}
