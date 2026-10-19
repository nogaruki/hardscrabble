package dev.mite.entity.ai;

import dev.mite.worldgen.DirtGravity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Silverfish of changelog R147 (l.376-378): an infested block squeaks when one starts to mine it (warning before the silverfish
 * emerges), a silverfish bursting out of a block may start a cave-in, and silverfish ignore falling sand, dirt and gravel.
 * Their prey (l.302) is in {@link MobRules}, their natural spawns (l.301) in the mob datagen.
 */
public final class SilverfishRules {
    private SilverfishRules() {}

    /** l.377: radius and chance per loose block around the emergence point of a cave-in [INFÉRÉ]. */
    public static final int CAVE_IN_RADIUS = 2;
    public static final float LOOSE_FALL_CHANCE = 0.5f, ROOF_FALL_CHANCE = 0.25f;

    /** l.376: the infested block squeaks and crumbles a little as soon as a player starts mining it. */
    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && event.getLevel() instanceof ServerLevel level) warn(level, event.getPos());
    }

    public static boolean warn(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof InfestedBlock)) return false;
        level.playSound(null, pos, SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 1, 1);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.05);
        return true;
    }

    /**
     * l.377 (called by InfestedBlockMixin when a silverfish bursts out of {@code pos}): loose blocks around (sand, gravel, dirt)
     * fall with a chance of {@link #LOOSE_FALL_CHANCE}, and the stone roof right above caves in as cobblestone with a chance of
     * {@link #ROOF_FALL_CHANCE} [INFÉRÉ].
     */
    public static void caveIn(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos roof = pos.above();
        if (level.getBlockState(roof).is(BlockTags.BASE_STONE_OVERWORLD) && FallingBlock.isFree(level.getBlockState(pos)) && random.nextFloat() < ROOF_FALL_CHANCE)
            FallingBlockEntity.fall(level, roof, Blocks.COBBLESTONE.defaultBlockState()).disableDrop();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CAVE_IN_RADIUS, 0, -CAVE_IN_RADIUS), pos.offset(CAVE_IN_RADIUS, CAVE_IN_RADIUS, CAVE_IN_RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (loose(state) && FallingBlock.isFree(level.getBlockState(p.below())) && random.nextFloat() < LOOSE_FALL_CHANCE)
                FallingBlockEntity.fall(level, p.immutable(), state);
        }
    }

    /** Sand, gravel, concrete powder (falling blocks) and the dirt of DirtGravity. */
    static boolean loose(BlockState state) {
        return state.getBlock() instanceof FallingBlock || state.is(DirtGravity.GRAVITY_AFFECTED);
    }

    /** l.378: falling sand, dirt or gravel neither crushes nor smothers a silverfish. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Silverfish fish)) return;
        var source = event.getSource();
        if (source.is(DamageTypes.FALLING_BLOCK) && source.getDirectEntity() instanceof FallingBlockEntity block && harmless(block.getBlockState())
                || source.is(DamageTypes.IN_WALL) && harmless(fish.level().getBlockState(BlockPos.containing(fish.getEyePosition()))))
            event.setCanceled(true);
    }

    /** Sand, gravel or dirt: what neither crushes nor smothers a silverfish. */
    static boolean harmless(BlockState state) {
        return loose(state) || state.is(BlockTags.DIRT);
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(SilverfishRules::onLeftClick);
        NeoForge.EVENT_BUS.addListener(SilverfishRules::onIncomingDamage);
    }
}
