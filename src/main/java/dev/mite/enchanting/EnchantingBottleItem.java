package dev.mite.enchanting;

import dev.mite.player.ProgressionService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Enchanting bottle (EF-ENC-06, ET-H-06): use stores 200 MITE XP points in {@code hardscrabble:stored_xp}; using a full bottle gives them back. */
public class EnchantingBottleItem extends Item {
    public static final int CAPACITY = 200;

    public EnchantingBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int stored = stack.getOrDefault(EnchantingContent.STORED_XP.get(), 0);
        if (stored <= 0 && ProgressionService.xp(player) < CAPACITY) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            ProgressionService.setXp(player, ProgressionService.xp(player) + (stored > 0 ? stored : -CAPACITY));
            stack.set(EnchantingContent.STORED_XP.get(), stored > 0 ? 0 : CAPACITY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(EnchantingContent.STORED_XP.get(), 0) > 0;
    }
}
