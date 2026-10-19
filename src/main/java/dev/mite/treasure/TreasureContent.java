package dev.mite.treasure;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Material;
import dev.mite.player.ProgressionService;
import dev.mite.progression.MiteTriggers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * "Could" extras: silver horse armor (EF-CBT-12, G12), emerald "lesser" enchanting table (EF-ENC-03, H03), coins (EF-ENC-07, H08)
 * and Father Funzang's lore books (EF-UI-09, P05). The silver and lava armor rules (G07, G08) live in {@code combat.ArmorHazards}.
 */
public final class TreasureContent {
    private TreasureContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    // ---- G12: silver horse armor, copper stats (silver = copper protection, EF-CBT-03), asset hardscrabble:silver (horse_body layer) ----

    private static final ArmorMaterial SILVER_HORSE = new ArmorMaterial(ArmorMaterials.COPPER.durability(), ArmorMaterials.COPPER.defense(),
            Material.SILVER.enchantability, ArmorMaterials.COPPER.equipSound(), 0, 0, ArmorMaterials.COPPER.repairIngredient(),
            ResourceKey.create(EquipmentAssets.ROOT_ID, MiteMod.id("silver")));
    public static final DeferredItem<Item> SILVER_HORSE_ARMOR = ITEMS.registerItem("silver_horse_armor", p -> new Item(p.horseArmor(SILVER_HORSE)));

    // ---- H03: emerald enchanting table ----

    /** EF-ENC-03 "reduced caps" [NON VÉRIFIÉ]: the lesser table never offers more than level 15 (diamond table: 30) [INFÉRÉ]. */
    public static final int LESSER_MAX_LEVEL = 15;

    public static final DeferredBlock<LesserEnchantingTable> EMERALD_ENCHANTING_TABLE = BLOCKS.registerBlock("emerald_enchanting_table",
            LesserEnchantingTable::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE));

    static {
        ITEMS.registerSimpleBlockItem(EMERALD_ENCHANTING_TABLE);
    }

    /** The vanilla table (block entity, book renderer, menu) with its own block; only the menu validity check names the block. */
    public static class LesserEnchantingTable extends EnchantingTableBlock {
        public LesserEnchantingTable(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
            return new SimpleMenuProvider((id, inv, player) -> new Menu(id, inv, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.enchant"));
        }
    }

    /** Same menu type as vanilla (the client opens a plain EnchantmentMenu); only the server-side distance/block check differs. */
    static class Menu extends EnchantmentMenu {
        private final ContainerLevelAccess access;

        Menu(int id, Inventory inventory, ContainerLevelAccess access) {
            super(id, inventory, access);
            this.access = access;
        }

        @Override
        public boolean stillValid(Player player) {
            return stillValid(access, player, EMERALD_ENCHANTING_TABLE.get());
        }
    }

    static void onLevelSet(EnchantmentLevelSetEvent event) {
        if (event.getLevel().getBlockState(event.getPos()).is(EMERALD_ENCHANTING_TABLE.get()))
            event.setEnchantLevel(Math.min(event.getEnchantLevel(), LESSER_MAX_LEVEL));
    }

    // ---- H08: coins ----

    /**
     * EF-ENC-07: XP value per metal, which is also its minting cost (changelog R166: crafting a coin needs enough XP), so coins
     * store XP and never create it (RE-04); a crafter cannot mint them ({@code CrafterRules}). Ancient metal 500 (changelog R152 coins line).
     */
    public record Coin(Material material, int value) {}

    public static final List<Coin> COIN_TABLE = List.of(new Coin(Material.COPPER, 5), new Coin(Material.SILVER, 25), new Coin(Material.GOLD, 100),
            new Coin(Material.ANCIENT_METAL, 500), new Coin(Material.MITHRIL, 2500), new Coin(Material.ADAMANTIUM, 10000));
    public static final Map<Material, DeferredItem<CoinItem>> COINS = new LinkedHashMap<>();

    static {
        for (Coin c : COIN_TABLE)
            COINS.put(c.material(), ITEMS.registerItem(c.material().id() + "_coin", p -> new CoinItem(p, c)));
    }

    /** Right click: the coin is spent for its XP value (EF-ENC-07, ET-H-08). */
    public static class CoinItem extends Item {
        public final Coin coin;

        public CoinItem(Properties properties, Coin coin) {
            super(properties);
            this.coin = coin;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!MiteConfig.COINS_ENABLED.get()) return InteractionResult.PASS;
            if (!level.isClientSide()) {
                player.getItemInHand(hand).consume(1, player);
                ProgressionService.addXp(player, coin.value());
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Lang key refusing to mint {@code result} (called by TimedCrafting), null if allowed. Minting = crafting a coin. */
    public static @Nullable String mintRefusal(Player player, ItemStack result) {
        if (!(result.getItem() instanceof CoinItem c)) return null;
        if (!MiteConfig.COINS_ENABLED.get()) return "hardscrabble.coin.disabled";
        return ProgressionService.xp(player) < c.coin.value() * result.getCount() ? "hardscrabble.coin.not_enough_xp" : null;
    }

    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getCrafting().getItem() instanceof CoinItem c)
            ProgressionService.setXp(p, ProgressionService.xp(p) - c.coin.value() * event.getCrafting().getCount());
    }

    // ---- P05: reading a lore book grants enlightenment ----

    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && LoreBooks.isLore(event.getItemStack())) MiteTriggers.EVENT.get().trigger(p, LoreBooks.ACHIEVEMENT);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BlockEntityTypeAddBlocksEvent e) -> e.modify(BlockEntityTypes.ENCHANTING_TABLE, EMERALD_ENCHANTING_TABLE.get()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, TreasureContent::onLevelSet); // after the material caps of EnchantingTable
        NeoForge.EVENT_BUS.addListener(TreasureContent::onCrafted);
        NeoForge.EVENT_BUS.addListener(TreasureContent::onRightClickItem);
    }
}
