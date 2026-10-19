package dev.mite.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A MITE workbench (EF-CRA-02): a crafting table whose menu knows its tier. */
public class WorkbenchBlock extends CraftingTableBlock {
    public final Workbench bench;

    public WorkbenchBlock(Workbench bench, Properties properties) {
        super(properties);
        this.bench = bench;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new Menu(id, inventory, ContainerLevelAccess.create(level, pos), this), getName());
    }

    /** Server-side menu; the client uses a plain {@link CraftingMenu} (same menu type). */
    public static class Menu extends CraftingMenu {
        public final WorkbenchBlock block;
        private final ContainerLevelAccess access;

        public Menu(int id, Inventory inventory, ContainerLevelAccess access, WorkbenchBlock block) {
            super(id, inventory, access);
            this.access = access;
            this.block = block;
        }

        @Override
        public boolean stillValid(Player player) {
            return stillValid(access, player, block);
        }
    }
}
