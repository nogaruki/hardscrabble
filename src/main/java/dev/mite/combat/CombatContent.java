package dev.mite.combat;

import static dev.mite.core.Material.*;

import dev.mite.MiteMod;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.harvest.ToolStats;
import com.google.common.base.Suppliers;
import dev.mite.core.Formulas;
import dev.mite.core.Quality;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Combat domain: weapon damage and attack speed (G01, G02), armor per material (G03, leather, horse armor, turtle shell),
 * worn armor (G04), chainmail (G05),
 * rusted iron (F12), vanilla mob stats (K04). Bows and arrows live in {@link Archery}, mob equipment and drops in {@link MobGear}.
 */
public final class CombatContent {
    private CombatContent() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    /**
     * True the first time it is called on {@code entity}, false afterwards: one-shot rules such as the spawn gear of a mob
     * ({@link MobGear}), the wear of a thrown trident or the recovery roll of an arrow that hit a block ({@link Archery}).
     * The mark is an entity tag: saved, so a portal trip (the copy is rebuilt from the save) does not run the rule again, and
     * copied by a conversion before the new mob joins the level.
     */
    static boolean once(Entity entity) {
        return entity.addTag("hardscrabble.rule_applied");
    }

    /** M12 / arbitrage n°18: loot reserved to players who killed the Dragon (the heavy core, so the mace stays end-game). */
    public static final net.minecraft.tags.TagKey<Item> POST_DRAGON = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, MiteMod.id("post_dragon"));

    /** One armor piece: item, material, plate or chainmail (EF-CBT-03). */
    public record ArmorEntry(Supplier<Item> item, Material material, ArmorType type, boolean chain) {
        /** Units of material in the recipe (plate ingots, chains or leather): 5/8/7/4. */
        public int components() {
            return switch (type) {
                case HELMET -> CombatFormulas.HELMET;
                case CHESTPLATE -> CombatFormulas.CHESTPLATE;
                case LEGGINGS -> CombatFormulas.LEGGINGS;
                default -> CombatFormulas.BOOTS;
            };
        }

        public double protection() {
            return CombatFormulas.pieceProtection(components(), material, chain);
        }

        public int durability() {
            return CombatFormulas.armorDurability(components(), material, chain);
        }

        ItemAttributeModifiers attributes() {
            return armorAttributes(type, protection());
        }
    }

    /** Armor points only (no toughness, no knockback resistance), under the vanilla id so they replace the vanilla bonus. */
    static ItemAttributeModifiers armorAttributes(ArmorType type, double armor) {
        Identifier id = Identifier.withDefaultNamespace("armor." + type.getName());
        return ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.bySlot(type.getSlot()))
                .build();
    }

    static final List<ArmorType> PIECES = List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);

    /** Every MITE armor piece: re-statted vanilla copper/iron/gold/chainmail (arbitrage n°1, CA-2) and the new metals. */
    public static final List<ArmorEntry> ARMORS = new ArrayList<>();
    /** dom-G-13 [INFÉRÉ]: the turtle shell (HORS-MITE) sits at the copper helmet level. Not a MITE material, so not in {@link #ARMORS}. */
    static final ArmorEntry TURTLE = new ArmorEntry(() -> Items.TURTLE_HELMET, COPPER, ArmorType.HELMET, false);
    /** dom-G-13: horse armor per material, see {@link CombatFormulas#horseArmor}. Diamond and netherite are not obtainable. */
    static final Map<Supplier<Item>, Material> HORSE_ARMORS = Map.of(() -> Items.LEATHER_HORSE_ARMOR, LEATHER, () -> Items.COPPER_HORSE_ARMOR, COPPER,
            () -> Items.IRON_HORSE_ARMOR, IRON, () -> Items.GOLDEN_HORSE_ARMOR, GOLD, dev.mite.treasure.TreasureContent.SILVER_HORSE_ARMOR, SILVER);
    /** The new armor items, registered here. */
    public static final List<DeferredItem<Item>> NEW_ARMOR = new ArrayList<>();

    static {
        vanilla(LEATHER, false, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS); // M9: 2 points
        vanilla(COPPER, false, Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS);
        vanilla(IRON, false, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        vanilla(GOLD, false, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        vanilla(IRON, true, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        for (Material m : List.of(SILVER, ANCIENT_METAL, MITHRIL, ADAMANTIUM, RUSTED_IRON)) for (ArmorType t : PIECES) armor(m, t, false);
        // EF-CBT-05: chainmail in every metal; vanilla chainmail is the iron one.
        for (Material m : List.of(COPPER, SILVER, GOLD, ANCIENT_METAL, MITHRIL, ADAMANTIUM, RUSTED_IRON)) for (ArmorType t : PIECES) armor(m, t, true);
    }

    /** EF-CBT-05 / EF-CRA-06: the chain (4 nuggets in a diamond) of each craftable metal. Iron and copper reuse the vanilla chains. */
    public static final Map<Material, Supplier<Item>> CHAINS = new EnumMap<>(Material.class);
    /** The chain items registered here. */
    public static final List<DeferredItem<Item>> NEW_CHAINS = new ArrayList<>();

    static {
        CHAINS.put(COPPER, () -> Items.COPPER_CHAIN.weathering().unaffected());
        CHAINS.put(IRON, () -> Items.IRON_CHAIN);
        for (Material m : List.of(SILVER, GOLD, ANCIENT_METAL, MITHRIL, ADAMANTIUM)) {
            DeferredItem<Item> chain = ITEMS.registerSimpleItem(prefix(m) + "_chain");
            CHAINS.put(m, chain);
            NEW_CHAINS.add(chain);
        }
    }

    // ---- Rusted iron (EF-MET-09): mob-only gear, no recipe, max quality poor ----

    public static final DeferredItem<Item> RUSTED_IRON_SWORD = rustedWeapon(ToolType.SWORD);
    public static final DeferredItem<Item> RUSTED_IRON_DAGGER = rustedWeapon(ToolType.DAGGER);

    private static DeferredItem<Item> rustedWeapon(ToolType type) {
        return ITEMS.registerItem("rusted_iron_" + type.name().toLowerCase(Locale.ROOT), props -> new Item(props
                .durability(Formulas.toolDurability(type, RUSTED_IRON, Quality.POOR)).attributes(weaponAttributes(type, RUSTED_IRON))
                .component(DataComponents.WEAPON, new Weapon(Formulas.attackWear(type), 0))));
    }

    /** Every rusted iron item: no recipe may produce one (EF-MET-09 CA-1). */
    public static List<Item> rustedItems() {
        List<Item> items = new ArrayList<>(List.of(RUSTED_IRON_SWORD.get(), RUSTED_IRON_DAGGER.get()));
        ARMORS.stream().filter(a -> a.material() == RUSTED_IRON).forEach(a -> items.add(a.item().get()));
        return items;
    }

    public static Item armorItem(Material material, ArmorType type, boolean chain) {
        return ARMORS.stream().filter(a -> a.material() == material && a.type() == type && a.chain() == chain).findFirst().orElseThrow().item().get();
    }

    private static void vanilla(Material material, boolean chain, Item... items) {
        for (int i = 0; i < 4; i++) {
            Item item = items[i];
            ARMORS.add(new ArmorEntry(() -> item, material, PIECES.get(i), chain));
        }
    }

    /** Item id prefix: "golden" like vanilla, else the material name. */
    static String prefix(Material m) {
        return m == GOLD ? "golden" : m.id();
    }

    private static void armor(Material material, ArmorType type, boolean chain) {
        String name = prefix(material) + (chain ? "_chainmail" : "");
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, MiteMod.id(name));
        Holder<SoundEvent> sound = chain ? SoundEvents.ARMOR_EQUIP_CHAIN
                : material == MITHRIL || material == ADAMANTIUM ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_IRON;
        ArmorEntry stats = new ArmorEntry(null, material, type, chain);
        DeferredItem<Item> item = ITEMS.registerItem(name + "_" + type.getName(), props -> new Item(props
                .durability(stats.durability()).attributes(stats.attributes())
                .component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot()).setEquipSound(sound).setAsset(asset).build())));
        ARMORS.add(new ArmorEntry(item, material, type, chain));
        NEW_ARMOR.add(item);
    }

    /** Weapon attributes: damage (EF-CBT-01) and attack speed (EF-CBT-02), as modifiers over the player bases. */
    public static ItemAttributeModifiers weaponAttributes(ToolType type, Material material) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                        CombatFormulas.damage(type, material) - CombatFormulas.PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                        CombatFormulas.attacksPerSecond(type) - CombatFormulas.PLAYER_BASE_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** Arbitrage n°22 (audit 2 m2/G-1): the spear's reach and charge follow the MITE table instead of vanilla's material-blind values. */
    private static void spear(DataComponentMap.Builder b, Material material) {
        AttackRange range = b.get(DataComponents.ATTACK_RANGE);
        if (range != null) b.set(DataComponents.ATTACK_RANGE, new AttackRange(0, CombatFormulas.SPEAR_REACH, 0, CombatFormulas.SPEAR_CREATIVE_REACH,
                range.hitboxMargin(), range.mobFactor()));
        KineticWeapon k = b.get(DataComponents.KINETIC_WEAPON);
        if (k != null) b.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(k.contactCooldownTicks(), k.delayTicks(), k.dismountConditions(),
                k.knockbackConditions(), k.damageConditions(), k.forwardMovement(), CombatFormulas.chargeMultiplier(material), k.sound(), k.hitSound()));
    }

    private static void modifyComponents(ModifyDefaultComponentsEvent event) {
        ToolStats.all().forEach((item, info) -> event.modify(item, (b, _, _) -> {
            b.set(DataComponents.ATTRIBUTE_MODIFIERS, weaponAttributes(info.type(), info.material()));
            if (info.type() == ToolType.SPEAR) spear(b, info.material());
        }));
        for (ArmorEntry a : ARMORS) {
            event.modify(a.item().get(), (b, _, _) -> {
                b.set(DataComponents.MAX_DAMAGE, a.durability());
                b.set(DataComponents.ATTRIBUTE_MODIFIERS, a.attributes());
                if (a.material().enchantability > 0) b.set(DataComponents.ENCHANTABLE, new Enchantable(a.material().enchantability)); // rusted iron: none
            });
        }
        event.modify(Items.TURTLE_HELMET, (b, _, _) -> {
            b.set(DataComponents.MAX_DAMAGE, TURTLE.durability());
            b.set(DataComponents.ATTRIBUTE_MODIFIERS, TURTLE.attributes());
        });
        HORSE_ARMORS.forEach((item, m) -> event.modify(item.get(), (b, _, _) -> b.set(DataComponents.ATTRIBUTE_MODIFIERS, armorAttributes(ArmorType.BODY, CombatFormulas.horseArmor(m)))));
    }

    /**
     * Armor wear. A player's wear goes to one random location, an empty one sparing the armor (changelog R118). MITE pieces wear
     * on the x100 scale (EF-OUT-05) with the hazard factors of {@link ArmorHazards#wearFactor}, and stop at 1 durability instead
     * of breaking (EF-CBT-04, R118) unless {@link ArmorHazards#canDestroy}. ponytail: falling blocks hit the helmet through the
     * same event, so they also pick a random location.
     */
    private static void onArmorHurt(ArmorHurtEvent event) {
        EquipmentSlot hit = event.getEntity() instanceof Player p ? PIECES.get(p.getRandom().nextInt(PIECES.size())).getSlot() : null;
        event.getArmorMap().forEach((slot, entry) -> {
            ItemStack stack = entry.armorItemStack;
            if (hit != null && slot != hit) entry.newDamage = 0;
            else if (isArmor(stack)) {
                float wear = entry.newDamage * CombatFormulas.ARMOR_WEAR_SCALE * ArmorHazards.wearFactor(event.getDamageSource(), stack);
                entry.newDamage = ArmorHazards.canDestroy(event.getDamageSource(), stack) ? wear : Math.min(wear, Math.max(0, stack.getMaxDamage() - 1 - stack.getDamageValue()));
            }
        });
    }

    private static final Supplier<Set<Item>> ARMOR_ITEMS = Suppliers.memoize(() -> java.util.stream.Stream.concat(ARMORS.stream(), java.util.stream.Stream.of(TURTLE))
            .map(a -> a.item().get()).collect(Collectors.toSet()));

    public static boolean isArmor(ItemStack stack) {
        return ARMOR_ITEMS.get().contains(stack.getItem());
    }

    private static final Supplier<Map<Item, ArmorEntry>> ARMOR_ENTRIES = Suppliers.memoize(() -> ARMORS.stream()
            .collect(Collectors.toMap(a -> a.item().get(), a -> a, (first, other) -> first)));

    /** The MITE armor entry (material, type, chain) of a stack, empty for anything else (turtle shell included). */
    public static java.util.Optional<ArmorEntry> armorEntry(ItemStack stack) {
        return java.util.Optional.ofNullable(ARMOR_ENTRIES.get().get(stack.getItem()));
    }

    /** EF-CBT-04: a worn piece's armor modifier is scaled by {@link CombatFormulas#wearFactor}, per stack. */
    private static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.isDamaged() || !isArmor(stack)) return;
        double factor = CombatFormulas.wearFactor(stack.getDamageValue(), stack.getMaxDamage());
        for (ItemAttributeModifiers.Entry e : List.copyOf(event.getModifiers())) {
            if (e.attribute().equals(Attributes.ARMOR)) event.replaceModifier(e.attribute(),
                    new AttributeModifier(e.modifier().id(), e.modifier().amount() * factor, e.modifier().operation()), e.slot());
        }
    }

    static final Identifier MOB_ARMOR_ID = MiteMod.id("mob_armor_factor");

    /**
     * EF-CBT-04: non-player wearers get a fixed 0.5 factor. A transient modifier on the mob takes away what the worn pieces
     * give above half of their full value. Armor stands are not mobs and keep the full value.
     */
    static void updateMobArmor(Mob mob) {
        double correction = 0;
        for (ArmorType t : PIECES) {
            EquipmentSlot slot = t.getSlot();
            ItemStack stack = mob.getItemBySlot(slot);
            if (!isArmor(stack)) continue;
            double full = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0, slot);
            correction += full * (CombatFormulas.MOB_ARMOR_FACTOR - CombatFormulas.wearFactor(stack.getDamageValue(), stack.getMaxDamage()));
        }
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        armor.removeModifier(MOB_ARMOR_ID);
        if (correction != 0) armor.addTransientModifier(new AttributeModifier(MOB_ARMOR_ID, correction, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) updateMobArmor(mob);
    }

    // ---- Vanilla mob stats (EF-IA-04, K04) ----

    public record MobStat(EntityType<? extends LivingEntity> type, Holder<Attribute> attribute, double value) {}

    /**
     * EF-IA-04. Spider "cannot be poisoned" is already vanilla. Behaviours (cave spider hostile by day) and XP multipliers
     * (witch, pigman, ghast, blaze) are not stats and live elsewhere. Unchanged vanilla values (enderman 40 HP, ghast 10 HP,
     * blaze 6, silverfish 8 HP, wolf 8 HP) are listed anyway so the table matches the spec.
     */
    public static final List<MobStat> MOB_STATS = List.of(
            new MobStat(EntityTypes.ZOMBIE, Attributes.ATTACK_DAMAGE, 5),
            new MobStat(EntityTypes.SKELETON, Attributes.MAX_HEALTH, 6),
            new MobStat(EntityTypes.SKELETON, Attributes.ATTACK_DAMAGE, 4),
            new MobStat(EntityTypes.SKELETON, Attributes.MOVEMENT_SPEED, 0.3),
            new MobStat(EntityTypes.SPIDER, Attributes.MAX_HEALTH, 12),
            new MobStat(EntityTypes.SPIDER, Attributes.ATTACK_DAMAGE, 4),
            new MobStat(EntityTypes.CAVE_SPIDER, Attributes.MAX_HEALTH, 16),
            new MobStat(EntityTypes.WITCH, Attributes.MAX_HEALTH, 26),
            new MobStat(EntityTypes.ENDERMAN, Attributes.MAX_HEALTH, 40),
            new MobStat(EntityTypes.ENDERMAN, Attributes.ATTACK_DAMAGE, 10),
            new MobStat(EntityTypes.ZOMBIFIED_PIGLIN, Attributes.ATTACK_DAMAGE, 8),
            new MobStat(EntityTypes.GHAST, Attributes.MAX_HEALTH, 10),
            new MobStat(EntityTypes.BLAZE, Attributes.ATTACK_DAMAGE, 6),
            new MobStat(EntityTypes.SILVERFISH, Attributes.MAX_HEALTH, 8),
            new MobStat(EntityTypes.SILVERFISH, Attributes.ATTACK_DAMAGE, 3),
            new MobStat(EntityTypes.WOLF, Attributes.MAX_HEALTH, 8),
            new MobStat(EntityTypes.WOLF, Attributes.ATTACK_DAMAGE, 3));

    /** Max health of a tamed wolf (EF-IA-04). */
    public static final double TAMED_WOLF_HEALTH = 12;
    /** Vanilla resets a tamed wolf's base max health to this value (Wolf#applyTamingSideEffects). */
    private static final double VANILLA_TAMED_WOLF_HEALTH = 40;
    static final Identifier TAMED_WOLF_ID = MiteMod.id("tamed_wolf_health");

    private static void modifyAttributes(EntityAttributeModificationEvent event) {
        MOB_STATS.forEach(s -> event.add(s.type(), s.attribute(), s.value()));
    }

    /**
     * A permanent modifier brings the vanilla tamed base (40) down to 12. It is added before vanilla applies the taming,
     * so the health set at taming is clamped to 12. Vanilla wolves only: the Dire Wolf sets its own 24 (EF-MOB-06). ponytail: wolves tamed by commands keep 40, hook taming if that matters.
     */
    private static void onTame(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Wolf wolf && wolf.getType() == EntityTypes.WOLF && !wolf.getAttribute(Attributes.MAX_HEALTH).hasModifier(TAMED_WOLF_ID)) {
            wolf.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(TAMED_WOLF_ID,
                    TAMED_WOLF_HEALTH - VANILLA_TAMED_WOLF_HEALTH, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(CombatContent::modifyComponents);
        modBus.addListener(CombatContent::modifyAttributes);
        NeoForge.EVENT_BUS.addListener(CombatContent::onArmorHurt);
        NeoForge.EVENT_BUS.addListener(CombatContent::onItemAttributes);
        NeoForge.EVENT_BUS.addListener(CombatContent::onEquipmentChange);
        Archery.register(modBus);
        MobGear.register();
        PostDragon.register();
        ArmorHazards.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatContent::onTame);
    }
}
