package dev.mite.crafting;

import com.mojang.serialization.Codec;
import dev.mite.MiteMod;
import dev.mite.combat.CombatContent;
import dev.mite.config.MiteConfig;
import dev.mite.core.Material;
import dev.mite.core.Quality;
import dev.mite.harvest.ToolStats;
import dev.mite.player.ProgressionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Crafting quality (EF-CRA-05, ET-D-06). Crafted MITE tools and armor carry {@code hardscrabble:quality}; their max damage is the default
 * one x the quality modifier. The player picks a target quality in the crafting screens; the craft gets the target capped by the
 * material and by the XP the player can pay, takes difficulty x 2 per step above average, and the XP is paid when the result is taken.
 * A recipe with a quality ingredient (vanilla tool repair) keeps the best input quality for free (CA-2).
 */
public final class CraftQuality {
    private CraftQuality() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MiteMod.MODID);

    private static final Codec<Quality> CODEC = CraftingContent.lowercaseCodec(Quality.class);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Quality> QUALITY_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> Quality.values()[i], Quality::ordinal);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Quality>> QUALITY = COMPONENTS.registerComponentType("quality",
            b -> b.persistent(CODEC).networkSynchronized(QUALITY_STREAM_CODEC));

    private static final Map<ServerPlayer, Quality> TARGET = new WeakHashMap<>();

    public static Quality target(ServerPlayer player) {
        return TARGET.getOrDefault(player, Quality.AVERAGE);
    }

    /** Sets the target quality and recomputes the open crafting grid when it changed. */
    public static void setTarget(ServerPlayer player, Quality quality) {
        if (target(player) == quality) return; // unchanged: a recompute would restart the craft in progress
        TARGET.put(player, quality);
        if (player.containerMenu instanceof AbstractCraftingMenu menu) menu.slotsChanged(menu.getInputGridSlots().getFirst().container);
    }

    /** The player instance replacing {@code old} (respawn, End return) keeps its target, without recomputing any grid. */
    static void keepTarget(ServerPlayer old, ServerPlayer now) {
        TARGET.put(now, target(old));
    }

    public static String langKey(Quality q) {
        return "quality.hardscrabble." + q.name().toLowerCase(Locale.ROOT);
    }

    /** Material of an item that takes a quality: MITE tools and armor; null for anything else. */
    public static @Nullable Material materialOf(ItemStack stack) {
        return ToolStats.of(stack).map(ToolStats.ToolInfo::material).orElseGet(() -> CombatContent.armorEntry(stack)
                .map(CombatContent.ArmorEntry::material).orElse(null));
    }

    private static @Nullable Quality inherited(List<ItemStack> grid) {
        return grid.stream().map(s -> s.get(QUALITY.get())).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    /** A repair: the grid holds the item being made (combination repair, leather armor and sinew). */
    static boolean isRepair(ItemStack result, List<ItemStack> grid) {
        return grid.stream().anyMatch(s -> !s.isEmpty() && s.is(result.getItem()));
    }

    /** Recipe components (ingots, gems, flint, leather) of a MITE tool or armor piece; a chainmail chain counts 4/9 of an ingot. */
    static Optional<Float> components(ItemStack stack) {
        var tool = ToolStats.of(stack);
        if (tool.isPresent()) return Optional.of((float) tool.get().type().components);
        return CombatContent.armorEntry(stack).map(a -> a.components() * (a.chain() ? 4f / 9 : 1f));
    }

    /**
     * E-3 / changelog l.341 (R151, repair difficulty per item): a tool or armor piece used as an ingredient counts half the
     * difficulty of its recipe (components x one unit of its material), so combining two of them takes about as long as crafting
     * one [INFÉRÉ]. Empty for anything else.
     */
    public static Optional<Float> repairDifficulty(ItemStack stack) {
        Material material = materialOf(stack);
        return material == null ? Optional.empty() : components(stack).map(c -> c * material.ingotDifficulty() / 2);
    }

    /** Sets the quality of a craft result about to be timed; null when the result takes none (or quality is disabled). */
    static @Nullable Quality apply(ServerPlayer player, ItemStack result, List<ItemStack> grid, float difficulty) {
        if (isRepair(result, grid)) noCombineBonus(result, grid);
        Material material = materialOf(result);
        if (!MiteConfig.QUALITY_ENABLED.get() || material == null) return null;
        Quality inherited = inherited(grid);
        if (inherited == null && isRepair(result, grid)) return null; // a repaired item without quality stays without one (no free roll)
        Quality q = inherited != null ? inherited : Quality.resolve(target(player), material.maxQuality, difficulty,
                ProgressionService.xp(player) / (dev.mite.curse.Curses.isClumsy(player) ? 2 : 1));
        result.set(QUALITY.get(), q);
        // Repairs keep the max damage vanilla computed from the inputs.
        if (inherited == null) result.set(DataComponents.MAX_DAMAGE, Math.round(result.getMaxDamage() * q.durabilityModifier));
        return q;
    }

    /**
     * E-3: combining two damaged items adds their remaining durabilities, without the vanilla 5 % bonus of {@code RepairItemRecipe}
     * (MITE repairs never create durability).
     */
    private static void noCombineBonus(ItemStack result, List<ItemStack> grid) {
        List<ItemStack> parts = grid.stream().filter(s -> !s.isEmpty()).toList();
        if (parts.size() != 2 || !parts.stream().allMatch(s -> s.is(result.getItem()) && s.isDamageableItem())) return;
        int remaining = parts.stream().mapToInt(s -> s.getMaxDamage() - s.getDamageValue()).sum();
        result.setDamageValue(Math.max(0, result.getMaxDamage() - remaining));
    }

    /** XP paid for taking {@code crafted} out of {@code grid}, doubled by the Clumsiness curse (EF-MAL-02 #8). Repairs are free. */
    public static int cost(Player player, ItemStack crafted, List<ItemStack> grid) {
        Quality q = crafted.get(QUALITY.get());
        return q == null || inherited(grid) != null || isRepair(crafted, grid) ? 0
                : q.xpCost(TimedCrafting.difficulty(grid)) * (dev.mite.curse.Curses.isClumsy(player) ? 2 : 1);
    }

    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Container c = event.getInventory();
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) grid.add(c.getItem(i));
        int cost = cost(player, event.getCrafting(), grid), xp = ProgressionService.xp(player);
        // ponytail: the quality is priced when the craft starts; XP spent since then caps the payment at what is left (never
        // below 0) instead of refusing the take, which needs a ResultSlot mixin.
        if (cost > 0) ProgressionService.setXp(player, xp - Math.min(cost, Math.max(xp, 0)));
    }

    static void onTooltip(ItemTooltipEvent event) {
        Quality q = event.getItemStack().get(QUALITY.get());
        // Changelog l.596: the durability modifier is shown (crafting output slot included: its item carries the quality).
        if (q != null && !event.getToolTip().isEmpty()) event.getToolTip().add(1, Component.translatable("tooltip.hardscrabble.quality",
                Component.translatable(langKey(q)), Math.round(q.durabilityModifier * 100)).withStyle(ChatFormatting.GRAY));
    }

    /** C2S: target quality chosen in the crafting screen, sent as an ordinal that the server bounds-checks (dom-Q-08). */
    public record Target(int ordinal) implements CustomPacketPayload {
        public static final Type<Target> TYPE = new Type<>(MiteMod.id("craft_quality"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(Target::new, Target::ordinal);

        public Target(Quality quality) {
            this(quality.ordinal());
        }

        static void handle(Target payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) payload.apply(player);
        }

        /** Sets the target; an out-of-range ordinal is ignored and logged. Returns whether it was accepted. */
        public boolean apply(ServerPlayer player) {
            if (ordinal < 0 || ordinal >= Quality.values().length) {
                MiteMod.LOGGER.warn("Ignored craft quality {} from {}: out of range", ordinal, player.getScoreboardName());
                return false;
            }
            setTarget(player, Quality.values()[ordinal]);
            return true;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
