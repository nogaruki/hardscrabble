package dev.mite.trading;

import dev.mite.config.MiteConfig;
import dev.mite.worldgen.village.VillageLock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Trading rules (PD-06, M2). Trade and barter data live in {@link TradingData}. */
public final class Trading {
    private Trading() {}

    /** M2: the wandering trader spawner only works once villages are unlocked (R165), and never when the option is off. */
    public static boolean wanderingTraderAllowed() {
        return (!MiteConfig.SPEC.isLoaded() || MiteConfig.WANDERING_TRADER.getAsBoolean()) && VillageLock.open();
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity() instanceof WanderingTrader && event.getSpawnType() == EntitySpawnReason.EVENT && !wanderingTraderAllowed()) {
            event.setSpawnCancelled(true); // its llamas are not spawned either
        }
    }

    /**
     * Audit 2 X5 / changelog l.190 (R170, villages generated without villagers) applied to the igloo: its basement template places
     * no villager nor zombie villager (added to the igloo pieces by {@code IglooPieceMixin}), so no villager can be had outside the
     * village lock (R165) before the Nether's weakness potion. Placement only: never serialized.
     */
    public static final StructureProcessor NO_VILLAGERS = new StructureProcessor() {
        @Override
        public StructureTemplate.@Nullable StructureEntityInfo processEntity(LevelReader level, BlockPos seedPos, StructureTemplate.StructureEntityInfo raw,
                StructureTemplate.StructureEntityInfo info, StructurePlaceSettings settings, StructureTemplate template) {
            String id = info.nbt.getStringOr("id", "");
            return id.equals(EntityType.getKey(EntityTypes.VILLAGER).toString()) || id.equals(EntityType.getKey(EntityTypes.ZOMBIE_VILLAGER).toString()) ? null : info;
        }

        @Override
        public MapCodec<? extends StructureProcessor> codec() {
            return MapCodec.unit(this);
        }
    };

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(Trading::onFinalizeSpawn);
    }
}
