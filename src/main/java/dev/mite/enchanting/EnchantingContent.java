package dev.mite.enchanting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.mite.MiteMod;
import dev.mite.core.Material;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Enchanting domain: metal anvils (EF-ENC-01), enchanting table (EF-ENC-02), MITE enchantments (EF-ENC-04), Mending/Infinity removal
 * (EF-ENC-05), bottles (EF-ENC-06), strongboxes (EF-ENC-08), grindstone (EF-ENC-09), loot blacklist (EF-PRG-04, EF-MON-06).
 */
public final class EnchantingContent {
    private EnchantingContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MiteMod.MODID);
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MiteMod.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MiteMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MiteMod.MODID);

    /** XP points held by an enchanting bottle (docs/02 §6.1, ET-H-06). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_XP =
            COMPONENTS.registerComponentType("stored_xp", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** EF-ENC-06: removes the enchantments of an item on an anvil. Recipe EF-CRA-06. */
    public static final DeferredItem<Item> DISENCHANTING_BOTTLE = ITEMS.registerSimpleItem("disenchanting_bottle", p -> p.stacksTo(16));
    public static final DeferredItem<EnchantingBottleItem> ENCHANTING_BOTTLE = ITEMS.registerItem("enchanting_bottle", EnchantingBottleItem::new, p -> p.stacksTo(1));

    /** Metal anvils except iron, which is the vanilla anvil (EF-ENC-01). */
    public static final Map<Material, DeferredBlock<MiteAnvilBlock>> ANVILS = new EnumMap<>(Material.class);

    static {
        for (Material m : List.of(Material.COPPER, Material.SILVER, Material.GOLD, Material.ANCIENT_METAL, Material.MITHRIL, Material.ADAMANTIUM)) {
            DeferredBlock<MiteAnvilBlock> block = BLOCKS.registerBlock(m.id() + "_anvil", p -> new MiteAnvilBlock(m, p),
                    p -> p.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0f, 1200.0f).sound(SoundType.ANVIL).pushReaction(PushReaction.IMMOVEABLE));
            ITEMS.registerSimpleBlockItem(block);
            ANVILS.put(m, block);
        }
    }

    /** EF-ENC-08: one strongbox per metal, iron included. */
    public static final Map<Material, DeferredBlock<StrongboxBlock>> STRONGBOXES = new EnumMap<>(Material.class);

    static {
        for (Material m : List.of(Material.COPPER, Material.SILVER, Material.GOLD, Material.IRON, Material.ANCIENT_METAL, Material.MITHRIL, Material.ADAMANTIUM)) {
            DeferredBlock<StrongboxBlock> block = BLOCKS.registerBlock(m.id() + "_strongbox", StrongboxBlock::new,
                    p -> p.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0f, 1200.0f).sound(SoundType.METAL));
            ITEMS.registerSimpleBlockItem(block);
            STRONGBOXES.put(m, block);
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StrongboxBlockEntity>> STRONGBOX_ENTITY = BLOCK_ENTITIES.register("strongbox",
            () -> new BlockEntityType<>(StrongboxBlockEntity::new, Set.copyOf(STRONGBOXES.values().stream().map(b -> (Block) b.get()).toList())));

    public static final DeferredHolder<MenuType<?>, MenuType<MiteAnvilMenu>> ANVIL_MENU =
            MENUS.register("anvil", () -> new MenuType<>(MiteAnvilMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ProgressionLootModifier>> REMOVE_ITEM =
            LOOT_MODIFIERS.register("remove_item", () -> ProgressionLootModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<ReplaceItemModifier>> REPLACE_ITEM =
            LOOT_MODIFIERS.register("replace_item", () -> ReplaceItemModifier.CODEC);

    /** Items no loot table may yield: diamond, stone, wooden (but the shovel) and netherite gear, mithril and adamantium ingots (EF-PRG-04). */
    public static final TagKey<Item> PROGRESSION_BLACKLIST = TagKey.create(Registries.ITEM, MiteMod.id("progression_blacklist"));
    /** What the remove_item GLM strips: the blacklist plus netherite and its upgrade template, which keep their recipes (EF-MON-06). */
    public static final TagKey<Item> LOOT_BLACKLIST = TagKey.create(Registries.ITEM, MiteMod.id("loot_blacklist"));
    /** Enchantments that cannot be obtained: Mending and Infinity (EF-ENC-05). */
    public static final TagKey<Enchantment> UNOBTAINABLE = TagKey.create(Registries.ENCHANTMENT, MiteMod.id("unobtainable"));

    /** The vanilla anvil (and its chipped/damaged stages) is the iron anvil: it opens the MITE menu (docs/02 §5.2, ET-H-01). */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (event.getUseBlock().isFalse() || !state.is(BlockTags.ANVIL) || player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) return;
        if (!event.getLevel().isClientSide()) player.openMenu(MiteAnvilMenu.provider(Material.IRON, event.getLevel(), event.getPos()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MiteEnchantments.EFFECTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EnchantingContent::onRightClickBlock);
        EnchantingTable.register();
        EnchantmentEffects.register();
    }
}
