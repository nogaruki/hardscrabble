package dev.mite.metabolism;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import dev.mite.core.Metabolism;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Registrations of the metabolism domain (docs/02 §6.2, §6.3). */
public final class MetabolismContent {
    private MetabolismContent() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MiteMod.MODID);

    private static final MapCodec<Metabolism> STATE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("satiety", 6).forGetter(m -> m.satiety),
            Codec.INT.optionalFieldOf("nutrition", 6).forGetter(m -> m.nutrition),
            Codec.FLOAT.optionalFieldOf("hunger", 0f).forGetter(m -> m.hunger),
            Codec.FLOAT.optionalFieldOf("drain", 0f).forGetter(m -> m.drain),
            Codec.INT.optionalFieldOf("protein", Formulas.NUTRIENT_MAX).forGetter(m -> m.protein),
            Codec.INT.optionalFieldOf("phyto", Formulas.NUTRIENT_MAX).forGetter(m -> m.phyto),
            Codec.INT.optionalFieldOf("starve_timer", 0).forGetter(m -> m.starveTimer),
            Codec.FLOAT.optionalFieldOf("regen", 0f).forGetter(m -> m.regen),
            Codec.INT.optionalFieldOf("insulin", 0).forGetter(m -> m.insulin),
            Codec.INT.optionalFieldOf("insulin_level", 0).forGetter(m -> m.insulinLevel)
    ).apply(i, (satiety, nutrition, hunger, drain, protein, phyto, starveTimer, regen, insulin, insulinLevel) -> {
        Metabolism m = new Metabolism();
        m.satiety = satiety;
        m.nutrition = nutrition;
        m.hunger = hunger;
        m.drain = drain;
        m.protein = protein;
        m.phyto = phyto;
        m.starveTimer = starveTimer;
        m.regen = regen;
        m.insulin = insulin;
        m.insulinLevel = insulinLevel;
        return m;
    }));

    /** The client only needs what the HUD and canEat read. */
    private static final StreamCodec<RegistryFriendlyByteBuf, Metabolism> SYNC_CODEC = StreamCodec.of((buf, m) -> {
        buf.writeVarInt(m.satiety);
        buf.writeVarInt(m.nutrition);
        buf.writeVarInt(m.protein);
        buf.writeVarInt(m.phyto);
        buf.writeVarInt(m.insulinLevel);
    }, buf -> {
        Metabolism m = new Metabolism();
        m.satiety = buf.readVarInt();
        m.nutrition = buf.readVarInt();
        m.protein = buf.readVarInt();
        m.phyto = buf.readVarInt();
        m.insulinLevel = buf.readVarInt();
        return m;
    });

    /** Not copied on death: respawn resets to defaults (docs/02 §6.3.1). Synced to the owner only. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Metabolism>> METABOLISM = ATTACHMENTS.register("metabolism",
            () -> AttachmentType.builder(Metabolism::new).serialize(dev.mite.attach.Migrations.versioned("metabolism", STATE_CODEC)).sync((holder, to) -> holder == to, SYNC_CODEC).build());

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    /** EF-NUT-12: filled on a water source or from a bucket (1 bucket = 4 bowls); an ingredient, not a food. */
    public static final DeferredItem<Item> WATER_BOWL = ITEMS.registerSimpleItem("water_bowl");
    /** EF-NUT-12 / EF-NUT-08: filled on a cow; 0 satiety, 1 nutrition, protein, always edible. */
    public static final DeferredItem<Item> MILK_BOWL = ITEMS.registerSimpleItem("milk_bowl", p -> p
            .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0).alwaysEdible().build(), Consumables.DEFAULT_DRINK).usingConvertsTo(Items.BOWL));

    public static final DataMapType<Item, Nutrition> NUTRITION = DataMapType.builder(MiteMod.id("nutrition"), Registries.ITEM, Nutrition.CODEC)
            .synced(Nutrition.CODEC, false).build();

    /** Raw food poison (EF-NUT-10): chance and Poison duration in ticks. Replaces the vanilla on-consume effects. */
    record Poison(float chance, int ticks) {}

    /** Durations missing from the spec (rotten flesh, poisonous potato) reuse the vanilla poisonous potato's 100 ticks. */
    static final Map<Item, Poison> RAW_POISON = Map.of(
            Items.CHICKEN, new Poison(0.3f, 20),
            Items.ROTTEN_FLESH, new Poison(0.8f, 100),
            Items.POISONOUS_POTATO, new Poison(0.6f, 100),
            Items.SPIDER_EYE, new Poison(1f, 5));

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((RegisterDataMapTypesEvent e) -> e.register(NUTRITION));
        modBus.addListener(MetabolismContent::foodComponents);
        MetabolismService.registerEvents(NeoForge.EVENT_BUS);
        MiteSleep.registerEvents(NeoForge.EVENT_BUS);
    }

    /** EF-NUT-08 / changelog R118: junk foods with a hardscrabble:nutrition entry that vanilla does not let you eat. */
    static final List<Item> MADE_EDIBLE = List.of(Items.SUGAR, Items.WHEAT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.NETHER_WART);

    private static void foodComponents(ModifyDefaultComponentsEvent event) {
        RAW_POISON.forEach((item, p) -> event.modify(item, (components, context, it) -> components.set(DataComponents.CONSUMABLE,
                Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.POISON, p.ticks(), 0), p.chance())).build())));
        // The vanilla food value is unused: MetabolismService.eat reads hardscrabble:nutrition.
        MADE_EDIBLE.forEach(item -> event.modify(item, (components, context, it) -> components
                .set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(1).saturationModifier(0).build())
                .set(DataComponents.CONSUMABLE, Consumables.defaultFood().build())));
        // EF-NUT-08: golden apple = Regeneration (I) 60 ticks, no Absorption; changelog l.593 (R127): the enchanted apple loses its Absorption.
        event.modify(Items.GOLDEN_APPLE, (components, context, it) -> components.set(DataComponents.CONSUMABLE, Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0))).build()));
        event.modify(Items.ENCHANTED_GOLDEN_APPLE, (components, context, it) -> components.set(DataComponents.CONSUMABLE, Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.REGENERATION, 400, 1),
                        new MobEffectInstance(MobEffects.RESISTANCE, 6000, 0), new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0)))).build()));
    }
}
