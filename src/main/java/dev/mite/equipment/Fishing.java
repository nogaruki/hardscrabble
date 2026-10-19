package dev.mite.equipment;

import dev.mite.lunar.Lunar;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Public API: MITE fishing pace (changelog l.829-830 R117, l.643 R129, EF-LUN-05/N06), rod casting rules (l.554 R129), worms in
 * the hotbar and worms from grass (l.212-213 R166). The bite timing itself is applied by {@code FishingHookMixin}. Changelog l.555
 * (R129): salmon, the MITE large fish, only bites in open ocean water ({@code data/minecraft/loot_table/gameplay/fishing/fish.json}).
 */
public final class Fishing {
    private Fishing() {}

    /** Changelog l.829 (R117): outside the peak hours a fish bites 1 tick in 2400. */
    public static final float OFF_PEAK_RATE = 1 / 2400f;
    /** Peak hours (dawn and dusk, EF-LUN-05) [INFÉRÉ: R117 only gives the off-peak rate; peak = 3x]. */
    public static final float PEAK_RATE = 1 / 800f;
    /** EF-LUN-05: deep water bites more often [INFÉRÉ: x1.5]. */
    public static final float DEEP_WATER_FACTOR = 1.5f;
    /** Changelog l.830: the catch rate rises during the whole night of a blue moon [INFÉRÉ: x2]. */
    public static final float BLUE_MOON_FACTOR = 2;
    /** Changelog l.212: raw worms in the hotbar double the catch rate (they are not consumed). */
    public static final float WORM_FACTOR = 2;
    /** Changelog l.643 (R129): the bite lasts 20 ticks longer (vanilla nibble 20-40 ticks). */
    public static final int BITE_EXTRA_TICKS = 20;
    /** Water this deep under the bobber counts as deep water (EF-LUN-05) [INFÉRÉ]. */
    public static final int DEEP_WATER = 5;
    /** Changelog l.213: chance of a worm from harvested grass, and when the block is exposed to rain [INFÉRÉ: 1/32, 1/8]. */
    public static final float WORM_CHANCE = 1 / 32f, WORM_CHANCE_RAIN = 1 / 8f;

    /** Hooked rod (EF-OUT-13). Changelog l.554: cannot be cast while swimming, airborne or submerged in a liquid. */
    public static final class RodItem extends FishingRodItem {
        public RodItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player.fishing != null) return super.use(level, player, hand); // reeling in is always allowed
            if (!canCast(player)) return InteractionResult.FAIL;
            if (!(level instanceof ServerLevel server)) return super.use(level, player, hand);
            ItemStack rod = player.getItemInHand(hand);
            int lure = (int) (EnchantmentHelper.getFishingTimeReduction(server, rod, player) * 20);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                    0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            Projectile.spawnProjectile(new FishingHook(player, level, EnchantmentHelper.getFishingLuckBonus(server, rod, player), lure), server, rod);
            player.awardStat(Stats.ITEM_USED.get(this));
            rod.causeUseVibration(player, GameEvent.ITEM_INTERACT_START);
            return InteractionResult.SUCCESS;
        }
    }

    /** Changelog l.554: on the ground (or on a mount or boat, l.553) and out of any liquid. */
    public static boolean canCast(Player player) {
        return !player.isInLiquid() && !player.isSwimming() && (player.onGround() || player.isPassenger());
    }

    /** Changelog l.212: a raw worm anywhere in the hotbar. */
    public static boolean wormInHotbar(Player player) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) if (player.getInventory().getItem(i).is(EquipmentContent.WORM.get())) return true;
        return false;
    }

    /** Dawn (22500-1000 ticks) and dusk (11500-13500) are the peak hours (EF-LUN-05). */
    public static boolean peakHours(long dayTime) {
        int t = (int) Math.floorMod(dayTime, 24000L);
        return t >= 22500 || t < 1000 || t >= 11500 && t < 13500;
    }

    /**
     * Chance per tick that a fish comes to the hook. Rain (+25 %) and a covered sky (-50 %) keep the vanilla effect on the
     * countdown speed (FishingHook#catchingFish), so they are not counted here.
     */
    public static float biteRate(long dayTime, boolean deepWater, boolean blueMoonNight, boolean worm) {
        return (peakHours(dayTime) ? PEAK_RATE : OFF_PEAK_RATE) * (deepWater ? DEEP_WATER_FACTOR : 1) * (blueMoonNight ? BLUE_MOON_FACTOR : 1)
                * (worm ? WORM_FACTOR : 1);
    }

    /** Ticks until the next bite for a per-tick chance {@code rate} (geometric distribution, mean 1 / rate). */
    public static int waitTicks(float rate, RandomSource random) {
        double u = random.nextDouble();
        return 1 + (int) Math.min(Integer.MAX_VALUE / 2, Math.floor(Math.log1p(-u) / Math.log1p(-rate)));
    }

    /**
     * Called by FishingHookMixin for each {@code Mth.nextInt} of FishingHook#catchingFish: the vanilla wait (100-600 ticks) becomes
     * the MITE wait, the bite window (20-40 ticks) is 20 ticks longer, the fish approach (20-80) is kept. Lure still removes its
     * ticks afterwards, never below one tick.
     */
    public static int timing(FishingHook hook, RandomSource random, int min, int max, int vanilla, int lureSpeed) {
        if (min == 20 && max == 40) return vanilla + BITE_EXTRA_TICKS;
        if (min != 100 || max != 600) return vanilla;
        Level level = hook.level();
        Player owner = hook.getPlayerOwner();
        float rate = biteRate(level.getDefaultClockTime(), waterDepth(level, hook.blockPosition()) >= DEEP_WATER, Lunar.isBlueMoonNight(level),
                owner != null && wormInHotbar(owner));
        return Math.max(waitTicks(rate, random), lureSpeed + 1);
    }

    /** Water blocks from {@code pos} down, capped at {@link #DEEP_WATER}. */
    public static int waterDepth(Level level, BlockPos pos) {
        int depth = 0;
        while (depth < DEEP_WATER && level.getFluidState(pos.below(depth)).is(FluidTags.WATER)) depth++;
        return depth;
    }

    /** Changelog l.213: grass in a non-freezing biome may drop a worm when harvested, more often under rain. */
    public static float wormChance(Level level, BlockPos pos) {
        if (level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) return 0;
        return level.isRainingAt(pos.above()) ? WORM_CHANCE_RAIN : WORM_CHANCE;
    }

    static void onBlockDrops(BlockDropsEvent e) {
        if (!(e.getBreaker() instanceof Player) || !e.getState().is(Blocks.GRASS_BLOCK)) return;
        BlockPos p = e.getPos();
        if (e.getLevel().getRandom().nextFloat() >= wormChance(e.getLevel(), p)) return;
        e.getDrops().add(new ItemEntity(e.getLevel(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(EquipmentContent.WORM.get())));
    }
}
