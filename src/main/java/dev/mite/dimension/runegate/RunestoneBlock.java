package dev.mite.dimension.runegate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Runestone (EF-DIM-06): one of 16 runes, cycled by right click. Mithril or adamantium. The rune's name shows above the hotbar
 * on each click (M34: the 16 runes share one texture); sneaking reads it without changing it.
 */
public final class RunestoneBlock extends Block {
    public static final IntegerProperty RUNE = IntegerProperty.create("rune", 0, 15);

    public final boolean adamantium;

    public RunestoneBlock(boolean adamantium, Properties properties) {
        super(properties);
        this.adamantium = adamantium;
        registerDefaultState(stateDefinition.any().setValue(RUNE, 0));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (!player.isSecondaryUseActive()) level.setBlockAndUpdate(pos, state = state.cycle(RUNE));
            player.sendOverlayMessage(runeName(state.getValue(RUNE)));
        }
        return InteractionResult.SUCCESS;
    }

    /** "Rune: <name>", names in the lang files ({@code hardscrabble.rune.0} to {@code hardscrabble.rune.15}). */
    public static Component runeName(int rune) {
        return Component.translatable("hardscrabble.runestone.rune", Component.translatable("hardscrabble.rune." + rune));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNE);
    }
}
