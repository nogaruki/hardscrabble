package dev.mite.equipment;

import com.google.common.base.Suppliers;
import dev.mite.config.MiteConfig;
import dev.mite.core.Material;
import dev.mite.equipment.EquipmentContent.BucketSet;
import dev.mite.player.ProgressionService;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Public API: metal buckets (EF-OUT-13) and paid fluid sources (EF-JOU-09). Every water or lava bucket used by a player, vanilla
 * and fish buckets included, goes through {@link #use}: without paying, the poured fluid is a flowing block that dries up and
 * never waterlogs a block; sneaking with enough XP places a real source for {@code equipment.fluid_source_xp_cost} XP.
 * Dispensers always pour the unpaid way.
 */
public final class Buckets {
    private Buckets() {}

    private static final Supplier<Map<Item, BucketSet>> BY_ITEM = Suppliers.memoize(() -> {
        Map<Item, BucketSet> map = new HashMap<>();
        EquipmentContent.BUCKETS.values().forEach(s -> Stream.of(s.empty(), s.water(), s.lava(), s.milk(), s.stone()).forEach(i -> map.put(i.get(), s)));
        return map;
    });

    /** The bucket set an item belongs to (vanilla iron buckets included), or null. */
    public static @Nullable BucketSet of(ItemStack stack) {
        return BY_ITEM.get().get(stack.getItem());
    }

    /** EF-OUT-13 CA-1: chance that a bucket breaks when it scoops lava [INFÉRÉ: 1 / (4 x durability multiplier)]; adamantium never. */
    public static float lavaBreakChance(Material metal) {
        return metal == Material.ADAMANTIUM ? 0 : 1 / (4 * metal.durability);
    }

    static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide()) return;
        InteractionResult result = use(e.getEntity(), e.getHand());
        if (result != null) {
            e.setCanceled(true);
            e.setCancellationResult(result);
        }
    }

    /**
     * Server side. Null when the item or target is not handled here (vanilla behavior applies). Every bucket holding water or
     * lava pours through {@link #pour}, fish, axolotl and tadpole buckets included (they empty into an iron bucket).
     */
    public static @Nullable InteractionResult use(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BucketSet set = of(stack);
        Item item = stack.getItem();
        if (set != null && item == set.empty().get()) return fill(player, hand, stack, set);
        return pours(item) ? pour(player, hand, stack, set == null ? Items.BUCKET : set.empty().get(), (BucketItem) item) : null;
    }

    /** A bucket that empties water or lava (EF-JOU-09 applies). No fluid tags: they are not bound yet at common setup. */
    private static boolean pours(Item item) {
        return item instanceof BucketItem b && (b.getContent().isSame(Fluids.WATER) || b.getContent().isSame(Fluids.LAVA));
    }

    private static BlockHitResult pick(Player player, ClipContext.Fluid fluid) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluid, player));
    }

    private static @Nullable InteractionResult fill(Player player, InteractionHand hand, ItemStack stack, BucketSet set) {
        Level level = player.level();
        BlockHitResult hit = pick(player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        boolean lava = fluid.is(FluidTags.LAVA);
        // Other pickups (powder snow) stay vanilla, and only for the iron bucket.
        if (!fluid.isSource() || !lava && !fluid.is(FluidTags.WATER) || !(state.getBlock() instanceof BucketPickup pickup)
                || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)
                || pickup.pickupBlock(player, level, pos, state).isEmpty()) return null;
        pickup.getPickupSound(state).ifPresent(s -> player.playSound(s, 1, 1));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        if (lava && level.getRandom().nextFloat() < lavaBreakChance(set.metal())) {
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1, 1);
            stack.consume(1, player); // the bucket and its lava are lost
            return InteractionResult.SUCCESS;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack((lava ? set.lava() : set.water()).get())));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult pour(Player player, InteractionHand hand, ItemStack stack, Item empty, BucketItem bucket) {
        Level level = player.level();
        BlockHitResult hit = pick(player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos(), relative = pos.relative(hit.getDirection());
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(relative, hit.getDirection(), stack)) return InteractionResult.FAIL;
        // EF-JOU-09: MITE uses Ctrl + right click; sneak is the server-visible modifier here (Ctrl is sprint in 26.x).
        int cost = MiteConfig.FLUID_SOURCE_XP_COST.get();
        boolean free = cost == 0 || player.hasInfiniteMaterials();
        boolean source = free || player.isShiftKeyDown() && ProgressionService.xp(player) >= cost;
        BlockState clicked = level.getBlockState(pos);
        BlockPos at = source && clicked.getBlock() instanceof LiquidBlockContainer c && c.canPlaceLiquid(player, level, pos, clicked, bucket.content)
                ? pos : relative; // waterlogging makes a source: paid only
        if (source ? !bucket.emptyContents(player, level, at, hit, stack) : !pourFlowing(player, level, at, bucket, stack)) return InteractionResult.FAIL;
        bucket.checkExtraContent(player, level, stack, at); // the fish of a fish bucket
        if (source && !free) ProgressionService.setXp(player, ProgressionService.xp(player) - cost);
        player.awardStat(Stats.ITEM_USED.get(bucket));
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(empty)));
        return InteractionResult.SUCCESS;
    }

    /**
     * EF-JOU-09: an unpaid pour (player or dispenser) at {@code at}. Refused into a block it would waterlog, since a waterlogged
     * block holds a source; the placed fluid becomes flowing unless a source of the same fluid was already there.
     */
    private static boolean pourFlowing(@Nullable LivingEntity user, Level level, BlockPos at, BucketItem bucket, ItemStack stack) {
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof LiquidBlockContainer c && c.canPlaceLiquid(user, level, at, state, bucket.getContent())) return false;
        boolean keptSource = level.getFluidState(at).isSourceOfType(bucket.getContent());
        if (!bucket.emptyContents(user, level, at, null, stack)) return false;
        if (!keptSource && level.getBlockState(at).getBlock() instanceof LiquidBlock && level.getFluidState(at).isSource() && bucket.getContent() instanceof FlowingFluid f)
            level.setBlock(at, f.getFlowing(7, false).createLegacyBlock(), Block.UPDATE_ALL);
        return true;
    }

    /** Dispensers empty water and lava buckets the unpaid way (EF-JOU-09); the bucket comes back empty, or is dropped if it cannot pour. */
    private static final DispenseItemBehavior DISPENSE_FLOWING = new DefaultDispenseItemBehavior() {
        private final DefaultDispenseItemBehavior drop = new DefaultDispenseItemBehavior();

        @Override
        public ItemStack execute(BlockSource source, ItemStack dispensed) {
            BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            BucketItem bucket = (BucketItem) dispensed.getItem();
            if (!pourFlowing(null, source.level(), target, bucket, dispensed)) return drop.dispense(source, dispensed);
            bucket.checkExtraContent(null, source.level(), dispensed, target);
            BucketSet set = of(dispensed);
            return consumeWithRemainder(source, dispensed, new ItemStack(set == null ? Items.BUCKET : set.empty().get()));
        }
    };

    /** Replaces the vanilla dispenser behaviour of every water and lava bucket (vanilla placed a source). Run on the main thread. */
    static void registerDispensers() {
        BuiltInRegistries.ITEM.stream().filter(Buckets::pours).forEach(item -> DispenserBlock.registerBehavior(item, DISPENSE_FLOWING));
    }

    /**
     * D-2 / changelog l.500 (R134): when the player submerges (water at eye level), every carried lava bucket cools into a bucket of
     * stone of the same metal (l.384 R146 only removed the water filling of l.501). Server side, every tick.
     */
    public static void onSubmerged(Player player) {
        if (!player.level().isClientSide() && player.isUnderWater()) coolLava(player);
    }

    /** Turns the carried lava buckets into buckets of stone of their metal. */
    public static void coolLava(Player player) {
        var inventory = player.getInventory();
        boolean cooled = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            BucketSet set = of(inventory.getItem(i));
            if (set != null && inventory.getItem(i).is(set.lava().get())) {
                inventory.setItem(i, new ItemStack(set.stone().get()));
                cooled = true;
            }
        }
        if (cooled) player.level().playSound(null, player.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 1, 1);
    }

    /** Milking a cow with a non-iron metal bucket. Runs after the livestock check (hungry cows give no milk). */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        ItemStack stack = e.getItemStack();
        BucketSet set = of(stack);
        if (e.getLevel().isClientSide() || set == null || set.metal() == Material.IRON || stack.getItem() != set.empty().get()
                || !(e.getTarget() instanceof AbstractCow cow) || cow.isBaby()) return;
        Player player = e.getEntity();
        player.playSound(SoundEvents.COW_MILK, 1, 1);
        player.setItemInHand(e.getHand(), ItemUtils.createFilledResult(stack, player, new ItemStack(set.milk().get())));
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }
}
