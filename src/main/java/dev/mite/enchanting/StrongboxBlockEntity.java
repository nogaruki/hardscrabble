package dev.mite.enchanting;

import dev.mite.config.MiteConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Strongbox storage and owner (EF-ENC-08, ET-H-10). The block entity is not a {@link Container} and exposes no item capability,
 * so hoppers cannot empty it (CA-1); the 27 slots open in the vanilla 9x3 chest menu.
 */
public class StrongboxBlockEntity extends BlockEntity implements MenuProvider {
    private @Nullable UUID owner;
    final SimpleContainer items = new SimpleContainer(27) {
        @Override
        public void setChanged() {
            super.setChanged();
            StrongboxBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(StrongboxBlockEntity.this, player) && canOpen(player);
        }
    };

    public StrongboxBlockEntity(BlockPos pos, BlockState state) {
        super(EnchantingContent.STRONGBOX_ENTITY.get(), pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** The placer, or an operator when {@code strongbox_op_access} is on (EF-MUL-06). A strongbox without owner is open to all. */
    public boolean canOpen(Player player) {
        return owner == null || owner.equals(player.getUUID())
                || MiteConfig.STRONGBOX_OP_ACCESS.get() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return ChestMenu.threeRows(id, inventory, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        items.clearContent();
        ContainerHelper.loadAllItems(input, items.getItems());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("owner", UUIDUtil.CODEC, owner);
        ContainerHelper.saveAllItems(output, items.getItems());
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, items);
    }
}
