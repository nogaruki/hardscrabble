package dev.mite.worldgen;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.mite.MiteMod;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Dirt gravity (EF-MON-04, ET-L-05, L07): a block of {@code hardscrabble:gravity_affected} falls when the block under it is removed
 * (neighbor notification) or when a living entity walks on it (MX-09, BlockMixin), if the block under it is free. Grass,
 * mud, podzol and mycelium stay put; dirt, coarse dirt and rooted dirt fall.
 */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class DirtGravity {
    private DirtGravity() {}

    public static final TagKey<Block> GRAVITY_AFFECTED = TagKey.create(Registries.BLOCK, MiteMod.id("gravity_affected"));

    /** Makes the block at {@code pos} fall if it is gravity-affected and unsupported. */
    public static void tryFall(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(level instanceof ServerLevel) || !state.is(GRAVITY_AFFECTED) || pos.getY() <= level.getMinY()
                || !FallingBlock.isFree(level.getBlockState(pos.below()))) return;
        FallingBlockEntity.fall(level, pos, state);
    }

    /** MX-09 entry point. */
    public static void onStep(Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity && !entity.isSpectator()) tryFall(level, pos);
    }

    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level) tryFall(level, event.getPos().above());
    }

    /** Called by MiteBlockTags. */
    public static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        var t = tag.apply(GRAVITY_AFFECTED);
        for (Block b : new Block[]{Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT}) t.add(BuiltInRegistries.BLOCK.getResourceKey(b).orElseThrow());
    }
}
