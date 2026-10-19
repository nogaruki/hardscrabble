package dev.mite.combat;

import dev.mite.core.Material;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.Nullable;

/**
 * EF-CBT-10 (G07, G08) and the armor reduction (B2). Silver armor wards off poison, one chance in four per silver piece (the
 * Wight drain and the Shadow effects are handled by those mobs, see {@code Wight#wearsSilver}). A player's armor is a flat
 * reduction ({@link CombatFormulas#damageAfterArmor}); other wearers keep the vanilla formula [INFÉRÉ: keeps the mob tables
 * calibrated]. Fire and lava ignore metal armor (leather still protects). Wear (changelog R178): lava wears metal x10 and can
 * destroy it, adamantium does not wear in lava, fire does not wear metal, fire and lava can destroy leather.
 */
public final class ArmorHazards {
    private ArmorHazards() {}

    public static final int LAVA_WEAR_FACTOR = 10;

    private static @Nullable Material material(ItemStack stack) {
        return CombatContent.ARMORS.stream().filter(a -> a.item().get() == stack.getItem()).map(CombatContent.ArmorEntry::material).findFirst().orElse(null);
    }

    /** Worn silver pieces, plate or chainmail. */
    public static int silverPieces(LivingEntity entity) {
        int n = 0;
        for (ArmorType t : CombatContent.PIECES) if (material(entity.getItemBySlot(t.getSlot())) == Material.SILVER) n++;
        return n;
    }

    private static boolean metal(@Nullable Material m) {
        return m != null && m.isMetal();
    }

    /**
     * Armor points the worn metal pieces add to the wearer's armor: wear included, or for a mob the fixed
     * {@link CombatFormulas#MOB_ARMOR_FACTOR} of their full value ({@link CombatContent#updateMobArmor}).
     */
    public static double metalArmor(LivingEntity entity) {
        double sum = 0;
        for (ArmorType t : CombatContent.PIECES) {
            EquipmentSlot slot = t.getSlot();
            ItemStack stack = entity.getItemBySlot(slot);
            if (!metal(material(stack))) continue;
            sum += entity instanceof Mob
                    ? CombatFormulas.MOB_ARMOR_FACTOR * stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0, slot)
                    : stack.getAttributeModifiers().compute(Attributes.ARMOR, 0, slot);
        }
        return sum;
    }

    /** Wear multiplier of a MITE piece for that source (R178): lava x10 on metal, 0 on adamantium; fire 0 on metal. */
    static float wearFactor(DamageSource source, ItemStack stack) {
        Material m = material(stack);
        if (!metal(m) || !source.is(DamageTypeTags.IS_FIRE)) return 1;
        return source.is(DamageTypes.LAVA) && m != Material.ADAMANTIUM ? LAVA_WEAR_FACTOR : 0;
    }

    /** Whether that source may break the piece (R178): lava on metal, fire or lava on leather. Anything else stops at 1 durability (R118). */
    static boolean canDestroy(DamageSource source, ItemStack stack) {
        Material m = material(stack);
        return m == Material.LEATHER ? source.is(DamageTypeTags.IS_FIRE) : metal(m) && source.is(DamageTypes.LAVA);
    }

    static void onEffect(MobEffectEvent.Applicable event) {
        LivingEntity e = event.getEntity();
        if (event.getEffectInstance().is(MobEffects.POISON) && e.getRandom().nextInt(4) < silverPieces(e))
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** The armor reduction: flat for players, vanilla for the others; fire and lava without the metal pieces' points. */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        boolean fire = event.getSource().is(DamageTypeTags.IS_FIRE);
        if (!fire && !(e instanceof Player)) return;
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (c, reduction) -> {
            if (c.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return reduction;
            double armor = Math.max(0, e.getAttributeValue(Attributes.ARMOR) - (fire ? metalArmor(e) : 0));
            float damage = c.getNewDamage();
            return damage - (e instanceof Player ? CombatFormulas.damageAfterArmor(damage, armor, e.getRandom().nextFloat())
                    : CombatRules.getDamageAfterAbsorb(e, damage, c.getSource(), (float) Math.floor(armor), (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
        });
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ArmorHazards::onEffect);
        NeoForge.EVENT_BUS.addListener(ArmorHazards::onIncomingDamage);
    }
}
