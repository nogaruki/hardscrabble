package dev.mite.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Where monsters may spawn beyond vanilla: on bottom slabs and stairs (changelog l.696, R123: the old "slab floor" trick no
 * longer keeps a base free of spawns), on lily pads and thin snow, and in shallow water (l.703, R122). Tripwire already lets
 * mobs spawn in vanilla. Read by BlockStateBaseMixin and NaturalSpawnerMixin.
 */
public final class SpawnSurfaces {
    private SpawnSurfaces() {}

    /** Extra floors a monster may spawn on (the block under its feet). */
    public static boolean extraFloor(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
        if (type.getCategory() != MobCategory.MONSTER) return false;
        if (state.getBlock() instanceof SlabBlock) return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        if (state.getBlock() instanceof StairBlock) return state.getValue(StairBlock.HALF) == Half.BOTTOM;
        if (state.is(Blocks.LILY_PAD)) return true;
        return state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) < SnowLayerBlock.MAX_HEIGHT
                && level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), type);
    }

    /** l.703: a single block of still water over the ground is room enough for a land monster. */
    public static boolean shallowWater(BlockGetter level, BlockPos pos, BlockState state, FluidState fluid, EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER && state.is(Blocks.WATER) && fluid.isSource()
                && !level.getFluidState(pos.below()).is(FluidTags.WATER) && !level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    /** Mob#checkSpawnObstruction refuses any liquid: in shallow water only the spawn rules and solid obstruction count. Runs last. */
    private static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        Mob mob = event.getEntity();
        BlockPos pos = mob.blockPosition();
        var level = event.getLevel();
        if (event.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT || event.getSpawnType() != EntitySpawnReason.NATURAL
                || !shallowWater(level, pos, level.getBlockState(pos), level.getFluidState(pos), mob.getType())) return;
        event.setResult(mob.checkSpawnRules(level, event.getSpawnType()) && level.isUnobstructed(mob)
                ? MobSpawnEvent.PositionCheck.Result.SUCCEED : MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpawnSurfaces::onPositionCheck);
    }
}
