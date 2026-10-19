package dev.mite.metabolism;

import dev.mite.player.PlayerLevel;
import dev.mite.progression.MiteTriggers;
import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.Formulas;
import dev.mite.core.Metabolism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import dev.mite.enchanting.EnchantmentEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.BowItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * MITE metabolism replacing vanilla hunger (C01, C02, C04, C05, C06 basics, B01). The state lives in the
 * {@code hardscrabble:metabolism} attachment; vanilla {@link FoodData} is only a mirror (docs/02 P7).
 * Public queries for other domains: {@link #hasEnergy}, {@link #get}.
 */
public final class MetabolismService {
    private MetabolismService() {}

    static final Identifier LEVEL_HEALTH = MiteMod.id("level_health");
    static final Identifier ZERO_ENERGY = MiteMod.id("zero_energy");
    static final String THROW_CHARGED = "hardscrabble:throw_charged";

    public static Metabolism get(Player player) {
        // getData on a synced attachment syncs on first access, which crashes for connectionless fake players.
        if (player instanceof ServerPlayer sp && sp.connection == null && !player.hasData(MetabolismContent.METABOLISM)) {
            return new Metabolism();
        }
        return player.getData(MetabolismContent.METABOLISM);
    }

    /** False at zero food energy: no breaking, placing, attacking or crafting (EF-NUT-05). */
    public static boolean hasEnergy(Player player) {
        return player.getAbilities().invulnerable || get(player).hasEnergy();
    }

    /** EF-NUT-05: sprinting needs food energy, not the vanilla foodLevel > 6 (PlayerMixin, both sides). */
    public static boolean canSprint(Player player) {
        return hasEnergy(player) || player.mayFly();
    }

    /** EF-NUT-04: the Regeneration effect heals players only through the metabolism (RegenerationMobEffectMixin). */
    public static boolean effectHeals(LivingEntity entity) {
        return !(entity instanceof Player);
    }

    /** EF-NUT-02 cost of a MITE action, reduced by Endurance (EF-ENC-04: harvesting, placing, attacking, throwing, tilling, bows). */
    public static void actionHunger(Player player, float exhaustion) {
        player.causeFoodExhaustion(exhaustion * EnchantmentEffects.hungerMultiplier(player));
    }

    public static int cap(Player player) {
        return Formulas.foodCap(PlayerLevel.get(player));
    }

    /** MX-02: the player may eat while satiety or nutrition is below the cap. */
    public static boolean wantsFood(Player player) {
        return get(player).wantsFood(cap(player));
    }

    /**
     * Replaces {@link FoodData#tick} (MX-01).
     *
     * @param exhaustion vanilla exhaustion accrued since the last tick (EF-NUT-02), consumed here only
     * @param extraFood  food units from vanilla calls without an item (cake slice; the Saturation effect gives none, X10)
     */
    public static void tick(ServerPlayer player, FoodData food, float exhaustion, int extraFood) {
        updateMaxHealth(player);
        Metabolism m = get(player);
        int cap = cap(player);
        if (extraFood > 0) m.eat(extraFood, extraFood, false, false, cap);
        if (!player.getAbilities().invulnerable) {
            boolean hadProtein = m.protein > 0, hadPhyto = m.phyto > 0;
            // Endurance is applied per action (actionHunger), not here: running and jumping are not reduced (changelog R145).
            // Its break/attack discount is a negative exhaustion summed with vanilla's charge; floored at 0 in case the action
            // was cancelled after the discount (no hunger refund).
            float actionHunger = Math.max(0, exhaustion) * MiteConfig.HUNGER_GLOBAL_MULTIPLIER.get().floatValue();
            MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
            int delta = m.tick(cap, actionHunger, humidity(player), player.isHurt(), player.isSleeping(),
                    regen == null ? 0 : Formulas.regenEffectPerTick(regen.getAmplifier()));
            if (delta > 0) player.heal(1);
            else if (delta < 0) player.hurtServer(player.level(), player.damageSources().starve(), 1);
            if (hadProtein && m.protein == 0) player.sendOverlayMessage(Component.translatable("message.hardscrabble.crave_protein"));
            if (hadPhyto && m.phyto == 0) player.sendOverlayMessage(Component.translatable("message.hardscrabble.crave_phyto"));
        }
        setModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), ZERO_ENERGY, !m.hasEnergy() && !player.getAbilities().invulnerable,
                Formulas.ZERO_ENERGY_WALK - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (!player.isSleeping()) m.sleepTicks = 0;
        food.setFoodLevel(Formulas.foodLevelMirror(m.nutrition, cap));
        food.setSaturation(0);
        int key = m.displayKey();
        // Fake players (GameTests, machines) have no connection to sync to.
        if (key != m.syncedKey && player instanceof ServerPlayer sp && sp.connection != null) {
            m.syncedKey = key;
            player.syncData(MetabolismContent.METABOLISM);
        }
    }

    /** EF-NUT-03 humidity at the player's position; rain counts when it reaches the eyes, snow never (changelog R118). */
    public static float humidity(Player player) {
        Level level = player.level();
        Biome.Precipitation sky = level.precipitationAt(BlockPos.containing(player.getEyePosition()));
        return humidity(player, level.getBiome(player.blockPosition()), sky, level.isThundering());
    }

    /** Water at the feet and eyes is read from fluid states, so this also works outside the entity tick (fake players). */
    public static float humidity(Player player, Holder<Biome> biome, Biome.Precipitation sky, boolean thunder) {
        Level level = player.level();
        boolean rain = sky == Biome.Precipitation.RAIN;
        return Formulas.humidity(rain, rain && thunder, level.getFluidState(player.blockPosition()).is(FluidTags.WATER),
                level.getFluidState(BlockPos.containing(player.getEyePosition())).is(FluidTags.WATER), BiomeClimate.of(biome));
    }

    /**
     * Applies the {@code hardscrabble:nutrition} entry of an eaten item; items without one use their vanilla food value.
     * EF-NUT-07: sugar raises insulin resistance; in a resistant state the meal gives Nausea (amplifier = level - 1),
     * Poison when severe, and from moderate on loses satiety.
     */
    public static void eat(Player player, ItemStack stack) {
        Nutrition n = stack.typeHolder().getData(MetabolismContent.NUTRITION);
        if (n == null) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) return;
            n = new Nutrition(food.nutrition(), food.nutrition(), 0, false, false);
        }
        Metabolism m = get(player);
        int before = n.sugar() > 0 ? m.eatSugar(n.sugar()) : 0;
        int satiety = before >= 2 ? Math.max(n.satiety() - Formulas.insulinSatietyPenalty(n.sugar()), 0) : n.satiety();
        m.eat(satiety, n.nutrition(), n.protein(), n.phyto(), cap(player));
        int max = Formulas.foodCap(Formulas.MAX_LEVEL);
        if (m.satiety >= max && m.nutrition >= max && player instanceof ServerPlayer sp) MiteTriggers.EVENT.get().trigger(sp, "supersize_me");
        if (before > 0) player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, Formulas.INSULIN_NAUSEA_TICKS, before - 1));
        if (before == 3) player.addEffect(new MobEffectInstance(MobEffects.POISON, Formulas.insulinPoisonTicks(Math.round(n.sugar() * Formulas.INSULIN_PER_SUGAR)), 0));
    }

    /** EF-NUT-06: a malnourished player craves foods carrying the missing nutrient, and may eat them while full. */
    public static boolean craves(Player player, ItemStack stack) {
        Nutrition n = stack.typeHolder().getData(MetabolismContent.NUTRITION);
        Metabolism m = get(player);
        return n != null && (n.protein() && m.protein == 0 || n.phyto() && m.phyto == 0);
    }

    private static void fill(Player player, InteractionHand hand, Item result) {
        player.setItemInHand(hand, ItemUtils.createFilledResult(player.getItemInHand(hand), player, new ItemStack(result)));
    }

    /** EF-JOU-01: max health = Formulas.maxHealth(level); +gain when the cap rises, current HP capped when it drops. */
    public static void updateMaxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        double amount = Formulas.maxHealth(PlayerLevel.get(player)) - attr.getBaseValue();
        AttributeModifier old = attr.getModifier(LEVEL_HEALTH);
        if (old != null && old.amount() == amount) return;
        attr.addOrReplacePermanentModifier(new AttributeModifier(LEVEL_HEALTH, amount, AttributeModifier.Operation.ADD_VALUE));
        if (old != null && amount > old.amount()) player.heal((float) (amount - old.amount()));
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private static void setModifier(AttributeInstance attr, Identifier id, boolean on, double amount, AttributeModifier.Operation op) {
        if (on && !attr.hasModifier(id)) attr.addTransientModifier(new AttributeModifier(id, amount, op));
        else if (!on && attr.hasModifier(id)) attr.removeModifier(id);
    }

    static void registerEvents(IEventBus bus) {
        bus.addListener((LivingEntityUseItemEvent.Finish e) -> {
            if (e.getEntity() instanceof ServerPlayer p) eat(p, e.getItem());
        });
        // canEat (MX-02) does not see the item: craved food starts being eaten here, on both sides, before the vanilla check.
        bus.addListener((PlayerInteractEvent.RightClickItem e) -> {
            Player p = e.getEntity();
            ItemStack stack = e.getItemStack();
            Consumable consumable = stack.get(DataComponents.CONSUMABLE);
            if (consumable != null && !consumable.canConsume(p, stack) && craves(p, stack)) {
                p.startUsingItem(e.getHand());
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.CONSUME);
            } else if (stack.is(Items.BOWL)) {
                // EF-NUT-12: a bowl fills on a water source, like a glass bottle.
                Level level = e.getLevel();
                BlockHitResult hit = Item.getPlayerPOVHitResult(level, p, ClipContext.Fluid.SOURCE_ONLY);
                if (hit.getType() == HitResult.Type.BLOCK && level.mayInteract(p, hit.getBlockPos()) && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
                    level.playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1, 1);
                    fill(p, e.getHand(), MetabolismContent.WATER_BOWL.get());
                    e.setCanceled(true);
                    e.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        });
        // EF-NUT-12: a bowl of milk fills on an adult cow.
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (e.getTarget() instanceof Cow cow && !cow.isBaby() && e.getItemStack().is(Items.BOWL)) {
                Player p = e.getEntity();
                p.playSound(SoundEvents.COW_MILK, 1, 1);
                fill(p, e.getHand(), MetabolismContent.MILK_BOWL.get());
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        // EF-NUT-05 zero energy: no mining at all (project owner's decision), no placing, no attacking; sprint in canSprint.
        bus.addListener((PlayerEvent.BreakSpeed e) -> {
            if (!hasEnergy(e.getEntity())) e.setCanceled(true);
        });
        // EF-NUT-02 / C-2: vanilla already charges breaking (0.005); the difference to the 1.6.4 cost (0.025), with the Endurance
        // discount, is added here. The attack cost (0.3) replaces vanilla's 0.1 on a hit that deals damage (PlayerMixin).
        bus.addListener((BreakBlockEvent e) -> {
            if (!hasEnergy(e.getPlayer())) e.setCanceled(true);
            else {
                actionHunger(e.getPlayer(), Formulas.HUNGER_BREAK);
                e.getPlayer().causeFoodExhaustion(-Formulas.VANILLA_BREAK);
            }
        });
        bus.addListener((AttackEntityEvent e) -> {
            if (!hasEnergy(e.getEntity())) e.setCanceled(true);
        });
        // C-2: jumps cost their 1.6.4 price (0.2, sprinting 0.8) instead of 26.3's (0.05, 0.2); Endurance does not apply (R145).
        bus.addListener((LivingEvent.LivingJumpEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) p.causeFoodExhaustion(p.isSprinting()
                    ? Formulas.HUNGER_SPRINT_JUMP - Formulas.VANILLA_SPRINT_JUMP : Formulas.HUNGER_JUMP - Formulas.VANILLA_JUMP);
        });
        bus.addListener(EventPriority.LOWEST, (BlockEvent.EntityPlaceEvent e) -> {
            if (!(e.getEntity() instanceof Player p)) return;
            if (!hasEnergy(p)) e.setCanceled(true);
            else if (!e.isCanceled()) actionHunger(p, Formulas.HUNGER_PLACE);
        });
        // 26.3 tilling is a data-driven block transformer without an event: its BLOCK_CHANGE game event to farmland is caught instead.
        bus.addListener((VanillaGameEvent e) -> {
            if (e.getVanillaEvent().is(GameEvent.BLOCK_CHANGE.key()) && e.getCause() instanceof Player p && e.getContext().affectedState() != null
                    && e.getContext().affectedState().is(Blocks.FARMLAND)) actionHunger(p, Formulas.HUNGER_TILL);
        });
        // A throw is charged once: the flag (saved, and copied when the projectile crosses a portal or an owner's pearl is
        // re-created at login) stops a re-joining projectile from charging again.
        bus.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && !e.loadedFromDisk() && e.getEntity() instanceof ThrowableProjectile t && t.getOwner() instanceof Player p
                    && !t.getPersistentData().getBooleanOr(THROW_CHARGED, false)) {
                t.getPersistentData().putBoolean(THROW_CHARGED, true);
                actionHunger(p, Formulas.HUNGER_THROW);
            }
        });
        // Changelog R145: holding a bow drawn consumes hunger.
        bus.addListener((LivingEntityUseItemEvent.Tick e) -> {
            if (e.getEntity() instanceof Player p && e.getItem().getItem() instanceof BowItem) actionHunger(p, Formulas.HUNGER_BOW_TICK);
        });
    }
}
