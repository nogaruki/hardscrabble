package dev.mite.enchanting;

import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.player.ProgressionService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * MITE enchanting table (EF-ENC-02, ET-H-03) and grindstone (EF-ENC-09, ET-H-07). The table keeps the vanilla power (material
 * enchantability from the {@code enchantable} component, bookshelves) but charges MITE XP points. Everything goes through NeoForge
 * events, so MX-05 is not needed: the level cost is capped in {@link EnchantmentLevelSetEvent}, and the vanilla debit (a
 * {@code giveExperienceLevels(-n)} from the open {@link EnchantmentMenu}) is replaced by a points debit.
 */
public final class EnchantingTable {
    private EnchantingTable() {}

    /**
     * EF-ENC-02 caps as total difficulties (iron 80, mithril 150, adamantium 110) [NON VÉRIFIÉ, Q-14]. The announced XP caps
     * (5 300 / 10 000 / 6 500) are all above the table maximum (level 30), so they capped nothing (H-3); the difficulty scales the
     * table level instead: level cap = 30 x difficulty / 150 [INFÉRÉ], i.e. iron 16, adamantium 22, mithril 30. Other materials
     * keep the vanilla maximum.
     */
    static final Map<Material, Integer> DIFFICULTY_CAPS = Map.of(Material.IRON, 80, Material.MITHRIL, 150, Material.ADAMANTIUM, 110);
    static final int MAX_DIFFICULTY = 150, TABLE_MAX_LEVEL = 30;

    /** Highest table level an item of {@code material} can be offered, {@link #TABLE_MAX_LEVEL} when uncapped. */
    public static int levelCap(Material material) {
        Integer d = DIFFICULTY_CAPS.get(material);
        return d == null ? TABLE_MAX_LEVEL : TABLE_MAX_LEVEL * d / MAX_DIFFICULTY;
    }
    /** EF-ENC-02: at most 3 enchantments per item [MCMOD, NON VÉRIFIÉ]. */
    public static final int MAX_ENCHANTMENTS = 3;

    /** XP points an enchantment of table level {@code level} costs: the MITE XP from level 0 to it (Q-15, interim rule of EF-ENC-02). */
    public static int cost(int level) {
        return Formulas.xpForLevel(level);
    }

    static void onLevelSet(EnchantmentLevelSetEvent event) {
        MiteAnvilMenu.repairInfo(event.getItem()).map(r -> levelCap(r.material()))
                .ifPresent(cap -> event.setEnchantLevel(Math.min(event.getEnchantLevel(), cap)));
    }

    /**
     * EF-ENC-02 CA-1: the vanilla table removes 1-3 levels; MITE removes the points of the chosen row instead. The table already
     * checked that the player level reaches the row level, so the debit never goes below 0.
     */
    static void onLevelChange(PlayerXpEvent.LevelChange event) {
        Player player = event.getEntity();
        int row = -event.getLevels() - 1;
        if (!(player.containerMenu instanceof EnchantmentMenu menu) || row < 0 || row > 2 || menu.costs[row] <= 0) return;
        event.setCanceled(true);
        ProgressionService.setXp(player, ProgressionService.xp(player) - cost(menu.costs[row]));
    }

    /**
     * Keeps {@link #MAX_ENCHANTMENTS} of the rolled enchantments in roll order (the item's map is hash-ordered), the one the
     * table showed as its hint ({@code enchantClue}) first, so the promised enchantment is never the one dropped.
     */
    static void onEnchanted(PlayerEnchantItemEvent event) {
        List<Holder<Enchantment>> rolled = new ArrayList<>(event.getEnchantments().stream().map(EnchantmentInstance::enchantment).toList());
        if (rolled.size() <= MAX_ENCHANTMENTS) return;
        if (event.getEntity().containerMenu instanceof EnchantmentMenu menu) {
            IdMap<Holder<Enchantment>> ids = event.getEntity().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
            rolled.stream().filter(e -> Arrays.stream(menu.enchantClue).anyMatch(id -> id == ids.getId(e))).findFirst().ifPresent(hint -> {
                rolled.remove(hint);
                rolled.addFirst(hint);
            });
        }
        Set<Holder<Enchantment>> keep = new HashSet<>(rolled.subList(0, MAX_ENCHANTMENTS));
        EnchantmentHelper.updateEnchantments(event.getEnchantedItem(), m -> m.removeIf(e -> !keep.contains(e)));
    }

    /** EF-ENC-09: the grindstone still disenchants but gives no XP. */
    static void onGrindstoneTake(GrindstoneEvent.OnTakeItem event) {
        event.setXp(0);
    }

    /**
     * X9: the grindstone only disenchants one item. Two items (the vanilla combination repair: durabilities summed + 5 %) give no
     * result, so repairs stay on the anvil and the metal's workbench (decision n°12, changelog l.359 R149).
     */
    static void onGrindstonePlace(GrindstoneEvent.OnPlaceItem event) {
        if (!event.getTopItem().isEmpty() && !event.getBottomItem().isEmpty()) event.setCanceled(true);
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EnchantingTable::onGrindstonePlace);
        NeoForge.EVENT_BUS.addListener(EnchantingTable::onLevelSet);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EnchantingTable::onLevelChange); // before ProgressionService
        NeoForge.EVENT_BUS.addListener(EnchantingTable::onEnchanted);
        NeoForge.EVENT_BUS.addListener(EnchantingTable::onGrindstoneTake);
    }
}
