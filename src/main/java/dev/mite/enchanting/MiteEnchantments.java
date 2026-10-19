package dev.mite.enchanting;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.harvest.ToolStats.ToolInfo;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.advancements.predicates.DamageSourcePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.minecraft.world.item.enchantment.effects.ApplyMobEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The MITE enchantments (EF-ENC-04, ET-H-04) as data-driven registry entries. Effects vanilla components can express stay in JSON
 * (stunning, fishing fortune, speed, free action, poison, cleaving, arrow recovery); the others carry a {@code hardscrabble:*} effect component
 * holding a {@link LevelBasedValue} read by {@link EnchantmentEffects}. Levels and values are not documented: [HORS-MITE, provisional].
 */
public final class MiteEnchantments {
    private MiteEnchantments() {}

    static final DeferredRegister<DataComponentType<?>> EFFECTS = DeferredRegister.create(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, MiteMod.MODID);

    private static DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> effect(String name) {
        return EFFECTS.register(name, () -> DataComponentType.<LevelBasedValue>builder().persistent(LevelBasedValue.CODEC).build());
    }

    /** Max logs felled with the broken one. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> FELLING_EFFECT = effect("tree_felling");
    /** Fraction of the melee damage healed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> VAMPIRIC_EFFECT = effect("vampiric");
    /** Chance per melee hit that the victim drops its main hand item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> DISARMING_EFFECT = effect("disarming");
    /** Extra items per drop stack of a mature crop (integer part sure, fraction as a chance). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> HARVESTING_EFFECT = effect("harvesting");
    /** Chance a harvested mature crop replants itself. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> FERTILITY_EFFECT = effect("fertility");
    /** Extra items per meat drop stack of a killed mob. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> BUTCHERING_EFFECT = effect("butchering");
    /** Bonus fraction on every heal. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> REGENERATION_EFFECT = effect("regeneration");
    /** Fraction of action hunger removed (EF-NUT-02). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> ENDURANCE_EFFECT = effect("endurance");
    /** Ticks removed from the 20-tick full bow draw. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> QUICKNESS_EFFECT = effect("quickness");
    /** Bonus added to the recovery chance of the shot arrows (EF-ENC-04). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> ARROW_RECOVERY_EFFECT = effect("arrow_recovery");
    /** Bonus fraction on the arrow launch speed (flatter trajectory). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LevelBasedValue>> TRUE_FLIGHT_EFFECT = effect("true_flight");

    public static final ResourceKey<Enchantment> STUNNING = key("stunning"), FISHING_FORTUNE = key("fishing_fortune"), FERTILITY = key("fertility"),
            TREE_FELLING = key("tree_felling"), VAMPIRIC = key("vampiric"), SPEED = key("speed"), REGENERATION = key("regeneration"),
            FREE_ACTION = key("free_action"), QUICKNESS = key("quickness"), TRUE_FLIGHT = key("true_flight"), POISON = key("poison"),
            DISARMING = key("disarming"), CLEAVING = key("cleaving"), HARVESTING = key("harvesting"), BUTCHERING = key("butchering"),
            ENDURANCE = key("endurance"), ARROW_RECOVERY = key("arrow_recovery");
    /** The 16 MITE enchantments plus arrow recovery, which replaces Infinity. */
    public static final List<ResourceKey<Enchantment>> ALL = List.of(STUNNING, FISHING_FORTUNE, FERTILITY, TREE_FELLING, VAMPIRIC, SPEED,
            REGENERATION, FREE_ACTION, QUICKNESS, TRUE_FLIGHT, POISON, DISARMING, CLEAVING, HARVESTING, BUTCHERING, ENDURANCE, ARROW_RECOVERY);

    /** Target item tags (EF-ENC-04 "Cible"), filled from {@link dev.mite.harvest.ToolStats}. Scythes do not exist yet. */
    public static final TagKey<Item> BLUNT = tag("blunt"), FARMING = tag("farming"), FELLING = tag("felling"), VAMPIRIC_WEAPON = tag("vampiric"),
            SWORDS = tag("sword"), CLEAVING_TOOL = tag("cleaving"), BUTCHERING_TOOL = tag("butchering");
    static final Map<TagKey<Item>, Predicate<ToolInfo>> TARGETS = Map.of(
            BLUNT, t -> t.type() == ToolType.CLUB || t.type() == ToolType.CUDGEL || t.type() == ToolType.WAR_HAMMER,
            FARMING, t -> t.type() == ToolType.HOE || t.type() == ToolType.MATTOCK,
            FELLING, t -> t.type() == ToolType.AXE || t.type() == ToolType.BATTLE_AXE,
            VAMPIRIC_WEAPON, t -> t.type() == ToolType.SWORD && t.material() != Material.SILVER && t.material() != Material.MITHRIL,
            SWORDS, t -> t.type() == ToolType.SWORD,
            CLEAVING_TOOL, t -> t.type() == ToolType.PICKAXE || t.type() == ToolType.BATTLE_AXE,
            BUTCHERING_TOOL, t -> t.type() == ToolType.KNIFE || t.type() == ToolType.DAGGER);

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, MiteMod.id(name));
    }

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, MiteMod.id("enchantable/" + name));
    }

    /** Rarity weights (EF-ENC-04): common 10, uncommon 5, rare 2, very rare 1. Costs follow the vanilla shape. */
    private static Enchantment.Builder def(HolderSet<Item> items, int weight, int maxLevel, EquipmentSlotGroup slot) {
        int base = 21 - 2 * weight;
        return Enchantment.enchantment(Enchantment.definition(items, weight, maxLevel, Enchantment.dynamicCost(base, 10),
                Enchantment.dynamicCost(base + 50, 10), Math.max(1, 8 / weight), slot));
    }

    public static void bootstrap(BootstrapContext<Enchantment> context) {
        HolderGetter<Item> items = context.lookup(Registries.ITEM);
        var entityTypes = context.lookup(Registries.ENTITY_TYPE);
        EquipmentSlotGroup hand = EquipmentSlotGroup.MAINHAND;
        var arrowHit = LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.DIRECT_ATTACKER,
                EntityPredicate.Builder.entity().of(entityTypes, EntityTypeTags.ARROWS).build());
        register(context, STUNNING, def(items.getOrThrow(BLUNT), 5, 3, hand).withEffect(EnchantmentEffectComponents.POST_ATTACK,
                EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM, new ApplyMobEffect(HolderSet.direct(MobEffects.SLOWNESS),
                        LevelBasedValue.perLevel(1f), LevelBasedValue.perLevel(1f), LevelBasedValue.constant(3f), LevelBasedValue.constant(3f)),
                DamageSourceCondition.hasDamageSource(DamageSourcePredicate.Builder.damageType().isDirect(true))));
        register(context, FISHING_FORTUNE, def(items.getOrThrow(ItemTags.FISHING_ENCHANTABLE), 10, 3, hand)
                .withEffect(EnchantmentEffectComponents.FISHING_LUCK_BONUS, new AddValue(LevelBasedValue.perLevel(1f))));
        register(context, FERTILITY, def(items.getOrThrow(FARMING), 5, 3, hand).withSpecialEffect(FERTILITY_EFFECT.get(), LevelBasedValue.perLevel(1f / 3)));
        register(context, TREE_FELLING, def(items.getOrThrow(FELLING), 5, 1, hand).withSpecialEffect(FELLING_EFFECT.get(), LevelBasedValue.constant(64f)));
        register(context, VAMPIRIC, def(items.getOrThrow(VAMPIRIC_WEAPON), 1, 3, hand).withSpecialEffect(VAMPIRIC_EFFECT.get(), LevelBasedValue.perLevel(0.1f)));
        register(context, SPEED, def(items.getOrThrow(ItemTags.FOOT_ARMOR_ENCHANTABLE), 2, 3, EquipmentSlotGroup.FEET)
                .withEffect(EnchantmentEffectComponents.ATTRIBUTES, new EnchantmentAttributeEffect(MiteMod.id("enchantment.speed"),
                        Attributes.MOVEMENT_SPEED, LevelBasedValue.perLevel(0.1f), AttributeModifier.Operation.ADD_MULTIPLIED_BASE)));
        register(context, REGENERATION, def(items.getOrThrow(ItemTags.CHEST_ARMOR_ENCHANTABLE), 2, 3, EquipmentSlotGroup.CHEST)
                .withSpecialEffect(REGENERATION_EFFECT.get(), LevelBasedValue.perLevel(0.25f)));
        register(context, FREE_ACTION, def(items.getOrThrow(ItemTags.LEG_ARMOR_ENCHANTABLE), 5, 4, EquipmentSlotGroup.LEGS)
                .withEffect(EnchantmentEffectComponents.ATTRIBUTES, new EnchantmentAttributeEffect(MiteMod.id("enchantment.free_action"),
                        Attributes.MOVEMENT_EFFICIENCY, LevelBasedValue.perLevel(0.25f), AttributeModifier.Operation.ADD_VALUE)));
        // EF-ENC-04: draw time = 20 ticks - level.
        register(context, QUICKNESS, def(items.getOrThrow(ItemTags.BOW_ENCHANTABLE), 5, 5, hand).withSpecialEffect(QUICKNESS_EFFECT.get(), LevelBasedValue.perLevel(1f)));
        register(context, TRUE_FLIGHT, def(items.getOrThrow(ItemTags.BOW_ENCHANTABLE), 10, 3, hand).withSpecialEffect(TRUE_FLIGHT_EFFECT.get(), LevelBasedValue.perLevel(0.1f)));
        register(context, POISON, def(items.getOrThrow(ItemTags.BOW_ENCHANTABLE), 2, 2, hand).withEffect(EnchantmentEffectComponents.POST_ATTACK,
                EnchantmentTarget.ATTACKER, EnchantmentTarget.VICTIM, new ApplyMobEffect(HolderSet.direct(MobEffects.POISON),
                        LevelBasedValue.perLevel(3f), LevelBasedValue.perLevel(3f), LevelBasedValue.constant(0f), LevelBasedValue.constant(0f)), arrowHit));
        register(context, DISARMING, def(items.getOrThrow(SWORDS), 2, 3, hand).withSpecialEffect(DISARMING_EFFECT.get(), LevelBasedValue.perLevel(0.1f)));
        register(context, CLEAVING, def(items.getOrThrow(CLEAVING_TOOL), 2, 3, hand)
                .withEffect(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, new AddValue(LevelBasedValue.perLevel(-0.15f))));
        register(context, HARVESTING, def(items.getOrThrow(FARMING), 5, 3, hand).withSpecialEffect(HARVESTING_EFFECT.get(), LevelBasedValue.perLevel(0.5f)));
        register(context, BUTCHERING, def(items.getOrThrow(BUTCHERING_TOOL), 5, 3, hand).withSpecialEffect(BUTCHERING_EFFECT.get(), LevelBasedValue.perLevel(0.5f)));
        // EF-ENC-04: -20 % hunger cost per level.
        register(context, ENDURANCE, def(items.getOrThrow(ItemTags.CHEST_ARMOR_ENCHANTABLE), 5, 4, EquipmentSlotGroup.CHEST)
                .withSpecialEffect(ENDURANCE_EFFECT.get(), LevelBasedValue.perLevel(0.2f)));
        // Replaces Infinity (EF-ENC-05, §4.3 n°6): +10 % per level to the arrow's recovery chance (Archery.recovers) [INFÉRÉ];
        // the arrow is always used and stays a real, pickable arrow (H-4: no partial Infinity).
        register(context, ARROW_RECOVERY, def(items.getOrThrow(ItemTags.BOW_ENCHANTABLE), 5, 3, hand)
                .withSpecialEffect(ARROW_RECOVERY_EFFECT.get(), LevelBasedValue.perLevel(0.1f)));
    }

    private static void register(BootstrapContext<Enchantment> context, ResourceKey<Enchantment> key, Enchantment.Builder builder) {
        context.register(key, builder.build(key.identifier()));
    }

}
