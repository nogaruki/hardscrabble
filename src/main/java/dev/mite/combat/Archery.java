package dev.mite.combat;

import static dev.mite.core.Material.*;

import com.google.common.base.Suppliers;
import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.core.Quality;
import dev.mite.underworld.UnderworldContent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;

/**
 * Bows (EF-CBT-07), arrows with 10 tips (EF-CBT-08), sinew (EF-CBT-09), crossbow and trident restatted (M12).
 * Items go in {@link CombatContent#ITEMS}.
 */
public final class Archery {
    private Archery() {}

    /** A metal-reinforced bow: arrows leave faster (EF-CBT-07). */
    public static final class MiteBow extends BowItem {
        public final Material material;

        public MiteBow(Properties properties, Material material) {
            super(properties);
            this.material = material;
        }

        @Override
        protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle, @Nullable LivingEntity target) {
            super.shootProjectile(shooter, projectile, index, power * CombatFormulas.bowVelocity(material), uncertainty, angle, target);
        }
    }

    public static final DeferredItem<MiteBow> ANCIENT_METAL_BOW = bow(ANCIENT_METAL);
    public static final DeferredItem<MiteBow> MITHRIL_BOW = bow(MITHRIL);

    private static DeferredItem<MiteBow> bow(Material material) {
        return CombatContent.ITEMS.registerItem(material.id() + "_bow",
                p -> new MiteBow(p.durability(CombatFormulas.bowDurability(material)).enchantable(1), material));
    }

    /** EF-CBT-08: arrow of each tip. The vanilla arrow is the flint arrow; the ancient metal one belongs to the underworld domain. */
    public static final Map<Material, Supplier<Item>> ARROWS = new EnumMap<>(Material.class);
    /** The arrow items registered here. */
    public static final List<DeferredItem<ArrowItem>> NEW_ARROWS;

    static {
        ARROWS.put(FLINT, () -> Items.ARROW);
        ARROWS.put(ANCIENT_METAL, UnderworldContent.ANCIENT_METAL_ARROW::get);
        NEW_ARROWS = List.of(OBSIDIAN, COPPER, SILVER, GOLD, RUSTED_IRON, IRON, MITHRIL, ADAMANTIUM).stream().map(m -> {
            DeferredItem<ArrowItem> item = CombatContent.ITEMS.registerItem(CombatContent.prefix(m) + "_arrow", ArrowItem::new);
            ARROWS.put(m, item::get);
            return item;
        }).toList();
    }

    public static Item arrow(Material tip) {
        return ARROWS.get(tip).get();
    }

    private static final Supplier<Map<Item, Material>> TIPS = Suppliers.memoize(() -> {
        Map<Item, Material> map = new HashMap<>();
        ARROWS.forEach((m, item) -> map.put(item.get(), m));
        // Tipped and spectral arrows are crafted from flint arrows: flint damage and recovery [INFÉRÉ: MITE has neither].
        map.put(Items.TIPPED_ARROW, FLINT);
        map.put(Items.SPECTRAL_ARROW, FLINT);
        return map;
    });

    /** The tip of an arrow item (tipped and spectral arrows: flint), null for anything else (a trident). */
    public static @Nullable Material tip(ItemStack arrow) {
        return TIPS.get().get(arrow.getItem());
    }

    /** True if the damage comes straight from a silver-tipped arrow (EF-CBT-06). */
    public static boolean isSilverArrow(net.minecraft.world.entity.@Nullable Entity direct) {
        return direct instanceof AbstractArrow a && tip(a.getPickupItemStackOrigin()) == SILVER;
    }

    /**
     * EF-CBT-08: arrow base damage from its tip. Mob arrows keep the vanilla difficulty-based damage
     * ({@code setBaseDamageFromMob}), so only player, dispenser and ownerless arrows are set here.
     */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof AbstractArrow arrow) || arrow.getOwner() instanceof Mob) return;
        // Once per throw: a trident crossing a portal joins the other level again.
        if (arrow instanceof ThrownTrident && arrow.getOwner() instanceof Player && CombatContent.once(arrow)) wearThrown(arrow.getPickupItemStackOrigin());
        Material tip = tip(arrow.getPickupItemStackOrigin());
        if (tip != null) arrow.setBaseDamage(CombatFormulas.arrowDamage(tip));
    }

    /**
     * Audit 2 m1: a throw wears the trident like an iron spear hit (vanilla already took 1 point). It never breaks in flight: at
     * 1 durability vanilla refuses the next throw.
     */
    public static void wearThrown(ItemStack trident) {
        if (!trident.isDamageableItem()) return;
        int wear = Formulas.attackWear(dev.mite.core.ToolType.SPEAR) - 1;
        trident.setDamageValue(Math.min(trident.getMaxDamage() - 1, trident.getDamageValue() + wear));
    }

    /**
     * EF-CBT-08: true if an arrow of that tip survives its hit, the arrow recovery enchantment's {@code bonus} added to the chance
     * (H-4, EF-ENC-04). A projectile without a tip ({@link #tip}) never does.
     */
    public static boolean recovers(@Nullable Material tip, RandomSource random, float bonus) {
        return tip != null && random.nextFloat() < CombatFormulas.arrowRecovery(tip) + bonus;
    }

    /** Arrow recovery bonus of the bow (or crossbow) that shot {@code arrow}, 0 without one. */
    public static float recoveryBonus(AbstractArrow arrow) {
        ItemStack weapon = arrow.getWeaponItem();
        return weapon == null ? 0 : dev.mite.enchanting.EnchantmentEffects.value(weapon, dev.mite.enchanting.MiteEnchantments.ARROW_RECOVERY_EFFECT);
    }

    /**
     * EF-CBT-08: a pickable arrow hitting a block breaks unless it passes its tip's recovery chance, rolled once per shot: an arrow
     * that falls out of a broken or moved block lands again. Tridents (no tip) always stay.
     */
    private static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow.level().isClientSide() || arrow.pickup != AbstractArrow.Pickup.ALLOWED
                || event.getRayTraceResult().getType() != HitResult.Type.BLOCK) return;
        Material tip = tip(arrow.getPickupItemStackOrigin());
        if (tip != null && CombatContent.once(arrow) && !recovers(tip, arrow.getRandom(), recoveryBonus(arrow))) {
            arrow.discard();
            event.setCanceled(true);
        }
    }

    /**
     * EF-CBT-08 (dom-G-10): vanilla discards an arrow that wounds a mob; it drops at the victim with its tip's recovery chance.
     * Piercing arrows fly on and are judged where they land. Oozes and puddings take no arrow damage, so they keep none (R196).
     */
    private static void onArrowHit(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED && arrow.getPierceLevel() <= 0
                && event.getEntity().level() instanceof ServerLevel level && recovers(tip(arrow.getPickupItemStackOrigin()), arrow.getRandom(), recoveryBonus(arrow)))
            event.getEntity().spawnAtLocation(level, arrow.getPickupItemStackOrigin().copyWithCount(1));
    }

    /**
     * M12: a thrown trident (player or drowned) hits like an iron spear instead of vanilla's 8, enchantment bonus kept. A player
     * victim's damage is already scaled by difficulty (Player hurtServer), so the scaled 8 is swapped for the scaled spear value.
     */
    private static void onTridentHit(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof ThrownTrident) event.setAmount(tridentDamage(event.getEntity(), event.getSource(), event.getAmount()));
    }

    /** The {@link #onTridentHit} amount. Package-private for tests. */
    static float tridentDamage(LivingEntity victim, DamageSource source, float amount) {
        float spear = CombatFormulas.damage(dev.mite.core.ToolType.SPEAR, IRON);
        return Math.max(0, amount - difficulty(victim, source, CombatFormulas.VANILLA_THROWN_TRIDENT) + difficulty(victim, source, spear));
    }

    private static float difficulty(LivingEntity victim, DamageSource source, float damage) {
        return victim instanceof Player p ? source.type().scaling().getScalingFunction().scaleDamage(source, p, damage, p.level().getDifficulty()) : damage;
    }

    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.BOW, (b, _, _) -> b.set(DataComponents.MAX_DAMAGE, CombatFormulas.bowDurability(WOOD))); // EF-CBT-07: wooden bow 32
        event.modify(Items.CROSSBOW, (b, _, _) -> b.set(DataComponents.MAX_DAMAGE, CombatFormulas.CROSSBOW_DURABILITY)); // M12: vanilla 465
        event.modify(Items.TRIDENT, (b, _, _) -> {
            b.set(DataComponents.ATTRIBUTE_MODIFIERS, CombatContent.weaponAttributes(dev.mite.core.ToolType.SPEAR, IRON)); // M12: vanilla 9 damage
            // Audit 2 m1: durability and wear of an iron spear (vanilla 250 and 1 per hit, ~4 times the MITE endurance).
            b.set(DataComponents.MAX_DAMAGE, Formulas.toolDurability(dev.mite.core.ToolType.SPEAR, IRON, Quality.AVERAGE));
            b.set(DataComponents.WEAPON, new Weapon(Formulas.attackWear(dev.mite.core.ToolType.SPEAR), 0));
        });
    }

    static void register(IEventBus modBus) {
        modBus.addListener(Archery::modifyComponents);
        NeoForge.EVENT_BUS.addListener(Archery::onJoin);
        NeoForge.EVENT_BUS.addListener(Archery::onImpact);
        NeoForge.EVENT_BUS.addListener(Archery::onArrowHit);
        NeoForge.EVENT_BUS.addListener(Archery::onTridentHit);
    }
}
