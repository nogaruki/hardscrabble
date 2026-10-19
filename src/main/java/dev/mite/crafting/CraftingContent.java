package dev.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.mite.config.MiteConfig;
import dev.mite.MiteMod;
import dev.mite.core.Material;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.minecraft.server.level.ServerPlayer;

/** Crafting domain registrations: workbenches, data maps, inventory tag, progress and quality payloads (EF-CRA-01..05). */
public final class CraftingContent {
    private CraftingContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MiteMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiteMod.MODID);

    /** Every MITE workbench except flint, which is the vanilla crafting table. */
    public static final Map<Workbench, DeferredBlock<WorkbenchBlock>> WORKBENCHES = new EnumMap<>(Workbench.class);

    static {
        for (Workbench b : Workbench.values()) {
            if (b == Workbench.FLINT) continue;
            DeferredBlock<WorkbenchBlock> block = BLOCKS.registerBlock(b.blockName(), p -> new WorkbenchBlock(b, p),
                    p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
            ITEMS.registerSimpleBlockItem(block);
            WORKBENCHES.put(b, block);
        }
    }

    /** Codec of a core enum (pure Java, so not StringRepresentable) saved as its lowercase name. */
    static <E extends Enum<E>> Codec<E> lowercaseCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(s -> {
            try {
                return DataResult.success(Enum.valueOf(type, s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + " " + s);
            }
        }, e -> e.name().toLowerCase(Locale.ROOT));
    }

    private static final Codec<Material> MATERIAL_CODEC = lowercaseCodec(Material.class);

    /** Craft difficulty of one item used as an ingredient (EF-CRA-04). Unlisted items count {@link #DEFAULT_DIFFICULTY}. */
    public static final DataMapType<Item, Float> CRAFT_DIFFICULTY = DataMapType.builder(MiteMod.id("craft_difficulty"), Registries.ITEM, Codec.FLOAT)
            .synced(Codec.FLOAT, true).build();
    public static final float DEFAULT_DIFFICULTY = 25f;

    /** Metal an ingredient is made of; sets the minimum workbench tier (EF-CRA-02). */
    public static final DataMapType<Item, Material> MATERIAL = DataMapType.builder(MiteMod.id("material"), Registries.ITEM, MATERIAL_CODEC)
            .synced(MATERIAL_CODEC, true).build();

    /** Results craftable in the 2x2 inventory grid (EF-CRA-03). */
    public static final TagKey<Item> INVENTORY_CRAFTABLE = TagKey.create(Registries.ITEM, MiteMod.id("inventory_craftable"));

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MiteMod.MODID);

    /**
     * {@code hardscrabble:tool_lashing_disabled}: keeps the unlashed flint/obsidian tool recipes out of the recipe book while
     * {@code tool_lashing} is on (EF-OUT-12). ponytail: datapacks load before the per-world config, so the first load uses the
     * default (on); turning lashing off takes effect for these recipes after a /reload.
     */
    public record LashingDisabled() implements ICondition {
        public static final MapCodec<LashingDisabled> CODEC = MapCodec.unit(new LashingDisabled());

        @Override
        public boolean test(IContext context) {
            return MiteConfig.SPEC.isLoaded() ? !MiteConfig.TOOL_LASHING.get() : !MiteConfig.TOOL_LASHING.getDefault();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    static {
        CONDITIONS.register("tool_lashing_disabled", () -> LashingDisabled.CODEC);
    }

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MiteMod.MODID);

    static {
        SERIALIZERS.register("leather_repair", () -> LeatherRepairRecipe.SERIALIZER); // X11, changelog l.778 / l.331
    }

    /** Changelog l.774 / l.679 (R119, R123): a thrown brick. */
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MiteMod.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownBrick>> THROWN_BRICK = ENTITIES.registerEntityType("thrown_brick", ThrownBrick::new,
            MobCategory.MISC, b -> b.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));

    public static List<DeferredBlock<WorkbenchBlock>> workbenches() {
        return List.copyOf(WORKBENCHES.values());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        CraftQuality.COMPONENTS.register(modBus);
        CONDITIONS.register(modBus);
        SERIALIZERS.register(modBus);
        ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.addListener(ThrownBrick::onRightClickItem);
        modBus.addListener((RegisterDataMapTypesEvent e) -> {
            e.register(CRAFT_DIFFICULTY);
            e.register(MATERIAL);
        });
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1")
                .playToClient(TimedCrafting.Progress.TYPE, TimedCrafting.Progress.STREAM_CODEC)
                .playToServer(CraftQuality.Target.TYPE, CraftQuality.Target.STREAM_CODEC, CraftQuality.Target::handle)
                .playToServer(TimedCrafting.Hold.TYPE, TimedCrafting.Hold.STREAM_CODEC, TimedCrafting.Hold::handle));
        NeoForge.EVENT_BUS.addListener(CraftQuality::onCrafted);
        NeoForge.EVENT_BUS.addListener(CraftQuality::onTooltip);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) TimedCrafting.tick(player);
        });
        // A player who leaves, or the old instance after a death or an End return, must not stay referenced by the crafting maps.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) TimedCrafting.forget(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            if (!(e.getOriginal() instanceof ServerPlayer old) || !(e.getEntity() instanceof ServerPlayer now)) return;
            TimedCrafting.forget(old);
            CraftQuality.keepTarget(old, now); // the client sends its target only when it changes
        });
    }
}
