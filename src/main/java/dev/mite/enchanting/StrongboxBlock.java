package dev.mite.enchanting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** A metal strongbox (EF-ENC-08): only its placer opens it. Breaking it needs a tool above its metal (hardscrabble:block_hardness, harvest). */
public class StrongboxBlock extends BaseEntityBlock {
    public StrongboxBlock(Properties properties) {
        super(properties);
    }

    /** No menu from the block state: vanilla opens it for spectators without {@link #useWithoutItem}'s owner check. */
    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StrongboxBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (by instanceof Player player && level.getBlockEntity(pos) instanceof StrongboxBlockEntity box) box.setOwner(player.getUUID());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StrongboxBlockEntity box) {
            if (box.canOpen(player)) player.openMenu(box);
            else player.sendOverlayMessage(Component.translatable("container.hardscrabble.strongbox.locked"));
        }
        return InteractionResult.SUCCESS;
    }
}
