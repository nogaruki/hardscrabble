package dev.mite.enchanting;

import dev.mite.core.Formulas;
import dev.mite.curse.Curses;
import dev.mite.harvest.HarvestRules;
import dev.mite.harvest.ToolStats;
import dev.mite.metabolism.MetabolismService;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.apache.commons.lang3.mutable.MutableFloat;

/** Code side of the MITE enchantments whose effect JSON cannot express (EF-ENC-04, ET-H-04). Public API: {@link #value}, {@link #hungerMultiplier}. */
public final class EnchantmentEffects {
    private EnchantmentEffects() {}

    /** Sum of a {@code hardscrabble:*} effect over the enchantments of one stack, 0 when absent. */
    public static float value(ItemStack stack, Supplier<DataComponentType<LevelBasedValue>> type) {
        MutableFloat sum = new MutableFloat();
        EnchantmentHelper.runIterationOnItem(stack, (e, level) -> add(sum, e, level, type));
        return sum.floatValue();
    }

    /** Sum of a {@code hardscrabble:*} effect over the equipment of an entity, each enchantment counted in its own slots only. */
    public static float value(LivingEntity entity, Supplier<DataComponentType<LevelBasedValue>> type) {
        MutableFloat sum = new MutableFloat();
        EnchantmentHelper.runIterationOnEquipment(entity, (e, level, item) -> add(sum, e, level, type));
        return sum.floatValue();
    }

    private static void add(MutableFloat sum, Holder<Enchantment> e, int level, Supplier<DataComponentType<LevelBasedValue>> type) {
        LevelBasedValue v = e.value().effects().get(type.get());
        if (v != null) sum.add(v.calculate(level));
    }

    /** Endurance (EF-ENC-04): multiplier on the action hunger of a player, 1 - 0.2 x level. */
    public static float hungerMultiplier(Player player) {
        return Math.max(0f, 1f - value(player, MiteEnchantments.ENDURANCE_EFFECT));
    }

    /** Integer part always, fractional part as a chance. */
    static int roll(float value, RandomSource random) {
        int n = (int) value;
        return n + (random.nextFloat() < value - n ? 1 : 0);
    }

    /** Set while felling breaks logs through the game mode, so their own break events do not fell again. */
    private static boolean felling;

    /**
     * Tree felling: breaking a log fells the connected logs (6 faces and diagonals), up to the effect value. H-5: each felled log
     * is a real harvest: it is broken through the player's game mode, so its break event (hunger, protection and claim mods),
     * spawn protection, adventure-mode rules and creative drops apply to it, and the axe wears as on that log (MITE block wear,
     * applied by HarvestRules when its drops spawn with the player as breaker); felling stops before the axe would break, when
     * the axe can no longer harvest the log, or at zero food energy. Runs last and only when the original break went through.
     */
    static void onBreak(BreakBlockEvent event) {
        if (felling || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || !event.getState().is(BlockTags.LOGS)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        int max = (int) value(player.getMainHandItem(), MiteEnchantments.FELLING_EFFECT);
        if (max <= 0) return;
        Set<BlockPos> seen = new HashSet<>(Set.of(event.getPos()));
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(seen);
        int felled = 0;
        felling = true;
        try {
            while (!queue.isEmpty() && felled < max) {
                BlockPos c = queue.poll();
                for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
                    BlockState state = level.getBlockState(p);
                    if (felled >= max || !state.is(BlockTags.LOGS) || !seen.add(p.immutable())) continue;
                    ItemStack axe = player.getMainHandItem();
                    if (!canFell(player, axe, state, wear(player, axe, event.getState()))) return;
                    if (!player.mayInteract(level, p) || !player.gameMode.destroyBlock(p.immutable())) continue;
                    queue.add(p.immutable());
                    felled++;
                }
            }
        } finally {
            felling = false;
        }
    }

    /**
     * H-5: the held axe harvests this log and survives its wear on top of {@code reserved} (the wear of the log the player broke,
     * applied after this event); the player still has food energy.
     */
    static boolean canFell(Player player, ItemStack axe, BlockState log, int reserved) {
        if (!HarvestRules.canHarvest(axe, log) || !MetabolismService.hasEnergy(player)) return false;
        return !axe.isDamageableItem() || player.hasInfiniteMaterials() || axe.getMaxDamage() - axe.getDamageValue() - reserved > wear(player, axe, log);
    }

    /** MITE block wear of {@code tool} on {@code state} (EF-OUT-06), with the Equipment Decay curse. */
    static int wear(Player player, ItemStack tool, BlockState state) {
        return ToolStats.of(tool).map(i -> Formulas.blockWear(HarvestRules.hardness(state).hardness(), i.type())).orElse(1) * Curses.wearFactor(player);
    }

    /** Harvesting (extra crop drops) and fertility (the mature crop replants itself). */
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof CropBlock crop) || !crop.isMaxAge(event.getState())) return;
        RandomSource random = event.getLevel().getRandom();
        float extra = value(event.getTool(), MiteEnchantments.HARVESTING_EFFECT);
        if (extra > 0) event.getDrops().forEach(e -> e.getItem().grow(roll(extra, random)));
        if (roll(value(event.getTool(), MiteEnchantments.FERTILITY_EFFECT), random) > 0 && event.getLevel().getBlockState(event.getPos()).isAir()) {
            event.getLevel().setBlockAndUpdate(event.getPos(), crop.getStateForAge(0));
        }
    }

    /** Vampiric (heals a fraction of the melee damage) and disarming (the victim drops its main hand item); not on Thorns hits. */
    static void onDamage(LivingDamageEvent.Post event) {
        var source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || source.is(DamageTypes.THORNS)) return;
        ItemStack weapon = attacker.getMainHandItem();
        float heal = value(weapon, MiteEnchantments.VAMPIRIC_EFFECT) * event.getInflictedDamage();
        if (heal > 0) attacker.heal(heal);
        LivingEntity victim = event.getEntity();
        if (victim.level() instanceof ServerLevel level && !victim.getMainHandItem().isEmpty()
                && roll(value(weapon, MiteEnchantments.DISARMING_EFFECT), attacker.getRandom()) > 0) {
            victim.spawnAtLocation(level, victim.getMainHandItem().copy());
            victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Butchering: extra meat from a mob killed by the enchanted weapon. */
    static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        float extra = value(killer.getMainHandItem(), MiteEnchantments.BUTCHERING_EFFECT);
        if (extra > 0) event.getDrops().stream().filter(e -> e.getItem().is(ItemTags.MEAT)).forEach(e -> e.getItem().grow(roll(extra, killer.getRandom())));
    }

    /** Regeneration: every heal of the wearer is increased. */
    static void onHeal(LivingHealEvent event) {
        float bonus = value(event.getEntity(), MiteEnchantments.REGENERATION_EFFECT);
        if (bonus > 0) event.setAmount(event.getAmount() * (1 + bonus));
    }

    /** Quickness: full draw in 20 - level ticks instead of 20 (EF-ENC-04), by scaling the charge. */
    static void onArrowLoose(ArrowLooseEvent event) {
        float ticks = Math.min(value(event.getBow(), MiteEnchantments.QUICKNESS_EFFECT), 19);
        if (ticks > 0) event.setCharge(Math.round(event.getCharge() * 20 / (20 - ticks)));
    }

    /** Entity tag of an arrow already boosted by true flight; saved and copied on a dimension change, so the boost applies once. */
    static final String TRUE_FLIGHT_TAG = "hardscrabble.true_flight";

    /** True flight: faster launch, so a flatter trajectory. ponytail: the arrow damage also scales with its speed. */
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof AbstractArrow arrow) || arrow.getWeaponItem() == null) return;
        float bonus = value(arrow.getWeaponItem(), MiteEnchantments.TRUE_FLIGHT_EFFECT);
        if (bonus > 0 && arrow.addTag(TRUE_FLIGHT_TAG)) arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + bonus));
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EnchantmentEffects::onBreak); // after any listener that cancels the break
        NeoForge.EVENT_BUS.addListener(EnchantmentEffects::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(EnchantmentEffects::onDamage);
        NeoForge.EVENT_BUS.addListener(EnchantmentEffects::onDrops);
        NeoForge.EVENT_BUS.addListener(EnchantmentEffects::onHeal);
        NeoForge.EVENT_BUS.addListener(EnchantmentEffects::onArrowLoose);
        NeoForge.EVENT_BUS.addListener(EnchantmentEffects::onJoin);
    }
}
