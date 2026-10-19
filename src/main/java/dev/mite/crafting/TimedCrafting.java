package dev.mite.crafting;

import dev.mite.player.PlayerLevel;
import dev.mite.metabolism.MetabolismService;
import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.core.Quality;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.WebBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Timed crafting (EF-CRA-01), grid restrictions (EF-CRA-02, EF-CRA-03, lashing EF-OUT-12) and quality (EF-CRA-05), server side only.
 *
 * <p>Every time a crafting grid (2x2 inventory or 3x3 bench) computes its result, {@link #onResultChanged} hides the result
 * and starts a timer. {@link #tick} advances it while the player holds the action ({@link Hold}, restarting from 0 on release)
 * and the same menu stays open with an unchanged grid, then puts the result in the result slot. Nothing can be taken before
 * that, so an interrupted craft consumes nothing (CA-2) and a modified client cannot speed it up (CA-3). A hold started with
 * shift moves each finished result to the inventory, which starts the next craft (CA-4).
 */
public final class TimedCrafting {
    private TimedCrafting() {}

    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_REFUSAL = new WeakHashMap<>();
    /** Menu on which the player holds the craft action, and whether shift was down (EF-CRA-01, CA-4). */
    private static final Map<ServerPlayer, HeldOn> HOLDING = new WeakHashMap<>();

    private record HeldOn(AbstractContainerMenu menu, boolean shift) {}

    private static final class Pending {
        final AbstractCraftingMenu menu;
        final List<ItemStack> grid;
        final Progress progress;
        int elapsed;

        Pending(AbstractCraftingMenu menu, Progress progress) {
            this.menu = menu;
            this.grid = menu.getInputGridSlots().stream().map(s -> s.getItem().copy()).toList();
            this.progress = progress;
        }

        boolean gridUnchanged() {
            List<Slot> slots = menu.getInputGridSlots();
            for (int i = 0; i < grid.size(); i++) {
                if (!ItemStack.matches(grid.get(i), slots.get(i).getItem())) return false;
            }
            return true;
        }
    }

    /** Called (via mixin) after vanilla computed a crafting grid's result. */
    public static void onResultChanged(AbstractContainerMenu menu, ServerPlayer player, CraftingContainer grid, ResultContainer resultSlots) {
        ItemStack result = resultSlots.getItem(0);
        clear(player);
        if (result.isEmpty() || !(menu instanceof AbstractCraftingMenu craftingMenu)) return;

        Workbench bench = benchOf(menu);
        boolean handCraftable = result.is(CraftingContent.INVENTORY_CRAFTABLE);
        Material metal = hardestMetal(grid.getItems()); // tools count too: combination repair needs their bench (R149)
        // X8 / EF-PRG-01: a metal workbench needs the bench of the previous tier instead of a bench of its own metal.
        Material prerequisite = result.getItem() instanceof BlockItem b && b.getBlock() instanceof WorkbenchBlock w ? w.bench.prerequisite() : null;
        Material needed = prerequisite != null ? prerequisite : metal;
        // Hand-craftable results (flint and obsidian benches, nugget/ingot conversions) skip the bench tier check: any bench can do them.
        String refusal = null;
        if (prerequisite != null) {
            if (bench == null || !bench.accepts(prerequisite)) refusal = "hardscrabble.crafting.needs_better_workbench";
        } else if (!handCraftable && (bench == null ? metal != null : !bench.accepts(metal))) refusal = "hardscrabble.crafting.needs_better_workbench";
        else if (bench == null && !handCraftable) refusal = "hardscrabble.crafting.needs_workbench";
        else if (missingLashing(result, grid.getItems())) refusal = "hardscrabble.crafting.needs_lashing";
        else if (damagedKnifeInBench(result, grid.getItems())) refusal = "hardscrabble.crafting.damaged_knife";
        else refusal = dev.mite.treasure.TreasureContent.mintRefusal(player, result); // EF-ENC-07 minting cost
        if (refusal == null) refusal = dev.mite.combat.PostDragon.craftRefusal(player, result); // X7: dried ghast after the Dragon

        hideResult(menu, player, resultSlots);
        if (refusal != null) {
            refuse(player, refusal, benchName(needed)); // dom-P-08: the bench needed
            if (refusal.equals("hardscrabble.crafting.needs_better_workbench")) dev.mite.rules.Hints.show(player, "workbench", benchName(needed)); // EF-UI-06
            return;
        }
        float difficulty = difficulty(grid.getItems());
        Quality quality = CraftQuality.apply(player, result, grid.getItems(), difficulty);
        float factor = quality == null ? 1 : quality.difficultyFactor();
        int ticks = Formulas.craftTicks(difficulty * factor, PlayerLevel.get(player), bench == null ? 0 : bench.bonusFor(metal), dev.mite.curse.Curses.isClumsy(player));
        Progress progress = new Progress(ticks, result, quality == null ? -1 : quality.ordinal(), CraftQuality.cost(player, result, grid.getItems()));
        PENDING.put(player, new Pending(craftingMenu, progress));
        sync(player, progress);
    }

    public static void tick(ServerPlayer player) {
        Pending p = PENDING.get(player);
        if (p == null) return;
        if (player.containerMenu != p.menu || !p.gridUnchanged()) {
            clear(player);
            return;
        }
        HeldOn held = HOLDING.get(player);
        if (held == null || held.menu != p.menu) {
            p.elapsed = 0; // EF-CRA-01: released, the craft starts over
            return;
        }
        // Changelog l.114 (R180): no crafting with the upper body in a web; like a lack of energy, the craft starts over.
        boolean web = inWeb(player);
        if (web || !MetabolismService.hasEnergy(player)) {
            refuse(player, web ? "hardscrabble.crafting.in_web" : "hardscrabble.crafting.no_energy");
            if (p.elapsed > 0) {
                p.elapsed = 0;
                sync(player, p.progress);
            }
            return;
        }
        if (++p.elapsed >= p.progress.ticks()) {
            PENDING.remove(player);
            p.menu.getResultSlot().set(p.progress.result().copy());
            sync(player, 0, ItemStack.EMPTY);
            if (held.shift) p.menu.clicked(p.menu.getResultSlot().index, 0, ContainerInput.QUICK_MOVE, player);
        }
    }

    /** The player starts holding the craft action on their open menu. */
    public static void hold(ServerPlayer player, boolean shift) {
        HOLDING.put(player, new HeldOn(player.containerMenu, shift));
    }

    public static void release(ServerPlayer player) {
        HOLDING.remove(player);
    }

    /**
     * Drops every entry of a player who logs out or is replaced after death or a dimension return: the map values hold the
     * menu, whose slots reach the player, so the weak keys alone would never be released.
     */
    public static void forget(ServerPlayer player) {
        PENDING.remove(player);
        HOLDING.remove(player);
        LAST_REFUSAL.remove(player);
    }

    /** Whether any crafting state is still kept for the player (GameTests). */
    public static boolean tracks(ServerPlayer player) {
        return PENDING.containsKey(player) || HOLDING.containsKey(player) || LAST_REFUSAL.containsKey(player);
    }

    /** Total duration of the craft in progress, 0 if none. */
    public static int pendingTicks(ServerPlayer player) {
        Pending p = PENDING.get(player);
        return p == null ? 0 : p.progress.ticks();
    }

    /** Changelog l.114 (R180): the block at the player's eyes (upper body) is a web. */
    public static boolean inWeb(ServerPlayer player) {
        return player.level().getBlockState(BlockPos.containing(player.getEyePosition())).getBlock() instanceof WebBlock;
    }

    /** Null for the 2x2 inventory grid; any other crafting menu (vanilla crafting table) is the flint bench. */
    public static @Nullable Workbench benchOf(AbstractContainerMenu menu) {
        if (menu instanceof WorkbenchBlock.Menu m) return m.block.bench;
        return menu instanceof CraftingMenu ? Workbench.FLINT : null;
    }

    /** Recipe difficulty = sum of its ingredients' difficulties (EF-CRA-04). */
    public static float difficulty(List<ItemStack> ingredients) {
        float sum = 0;
        for (ItemStack stack : ingredients) {
            if (stack.isEmpty()) continue;
            Float d = stack.getData(CraftingContent.CRAFT_DIFFICULTY);
            sum += d != null ? d : CraftQuality.repairDifficulty(stack).orElse(CraftingContent.DEFAULT_DIFFICULTY);
        }
        return sum;
    }

    /** EF-OUT-12, except when a flint or obsidian bench comes apart into its knife: the bench already holds the lashing. */
    public static boolean missingLashing(ItemStack result, List<ItemStack> grid) {
        return dev.mite.harvest.HarvestRules.missingLashing(result, grid)
                && grid.stream().noneMatch(s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof CraftingTableBlock);
    }

    /**
     * Changelog l.341 (R151, repair difficulty of flint and obsidian knives): a bench gives its knife back new, so only an undamaged
     * knife may go into one; otherwise knife -> bench -> knife would repair it for a log.
     */
    public static boolean damagedKnifeInBench(ItemStack result, List<ItemStack> grid) {
        return result.getItem() instanceof BlockItem b && b.getBlock() instanceof CraftingTableBlock && grid.stream().anyMatch(ItemStack::isDamaged);
    }

    public static @Nullable Material hardestMetal(List<ItemStack> ingredients) {
        Material hardest = null;
        for (ItemStack stack : ingredients) {
            if (stack.isEmpty()) continue;
            Material m = stack.getData(CraftingContent.MATERIAL);
            if (m == null) m = CraftQuality.materialOf(stack);
            if (m != null && m.isMetal() && (hardest == null || m.durability > hardest.durability)) hardest = m;
        }
        return hardest;
    }

    private static void hideResult(AbstractContainerMenu menu, ServerPlayer player, ResultContainer resultSlots) {
        resultSlots.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        player.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
    }

    private static void clear(ServerPlayer player) {
        if (PENDING.remove(player) != null) sync(player, 0, ItemStack.EMPTY);
    }

    /** dom-P-08: argument of the crafting refusals, the name of the lowest bench that accepts the recipe's hardest metal. */
    static Component benchName(@Nullable Material metal) {
        return Component.translatable(Workbench.required(metal).langKey());
    }

    /** Action bar refusal, at most once every 2 s (EF-PRG-03). Returns whether the message was sent (not a fake player, not throttled). */
    public static boolean refuse(ServerPlayer player, String key, Object... args) {
        if (player.connection == null) return false; // fake players
        long now = player.level().getGameTime();
        Long last = LAST_REFUSAL.get(player);
        if (last != null && now - last < 40) return false;
        LAST_REFUSAL.put(player, now);
        player.sendOverlayMessage(Component.translatable(key, args));
        return true;
    }

    private static void sync(ServerPlayer player, int ticks, ItemStack result) {
        sync(player, new Progress(ticks, result, -1, 0));
    }

    private static void sync(ServerPlayer player, Progress progress) {
        if (player.connection.hasChannel(Progress.TYPE)) PacketDistributor.sendToPlayer(player, progress);
    }

    /** C2S: the player pressed ({@code held}, with {@code shift}) or released the craft action on the result slot (EF-CRA-01). */
    public record Hold(boolean held, boolean shift) implements CustomPacketPayload {
        public static final Type<Hold> TYPE = new Type<>(MiteMod.id("craft_hold"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hold> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Hold::held, ByteBufCodecs.BOOL, Hold::shift, Hold::new);

        static void handle(Hold payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (payload.held()) hold(player, payload.shift());
            else release(player);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * S2C: a craft of {@code ticks} ticks started now (0 = no craft in progress); {@code result} is shown as a preview, with its
     * quality ordinal (-1 = none) and XP cost (EF-UI-02).
     */
    public record Progress(int ticks, ItemStack result, int quality, int xpCost) implements CustomPacketPayload {
        public static final Type<Progress> TYPE = new Type<>(MiteMod.id("craft_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Progress::ticks, ItemStack.OPTIONAL_STREAM_CODEC, Progress::result,
                ByteBufCodecs.VAR_INT, Progress::quality, ByteBufCodecs.VAR_INT, Progress::xpCost, Progress::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
