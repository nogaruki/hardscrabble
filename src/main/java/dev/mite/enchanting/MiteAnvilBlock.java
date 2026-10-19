package dev.mite.enchanting;

import dev.mite.core.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A metal anvil (EF-ENC-01). {@link #DAMAGE} plays the role of the vanilla intact/chipped/damaged blocks. */
public class MiteAnvilBlock extends AnvilBlock {
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 2);
    public final Material material;

    public MiteAnvilBlock(Material material, Properties properties) {
        super(properties);
        this.material = material;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return MiteAnvilMenu.provider(material, level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DAMAGE);
    }
}
