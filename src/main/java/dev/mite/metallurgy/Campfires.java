package dev.mite.metallurgy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Changelog l.764 (R119): "Putting more than one cookable item into an open fire gives it a chance of being extinguished (more
 * cookable items = higher chance fire will go out; this prevents player from quickly cooking large batches of foods in open fires)".
 * The campfire (arbitrage n°11) is MITE's open fire: each food added beside others may put it out (audit 2 m5).
 */
public final class Campfires {
    private Campfires() {}

    /** [INFÉRÉ] chance per food already cooking that adding one more puts the fire out: 25 %, 50 %, 75 % for the 2nd, 3rd, 4th. */
    public static final float EXTINGUISH_PER_ITEM = 0.25f;

    /** Called before a food is added to a lit campfire; true if the fire went out (the food is still placed, raw on a dead fire). */
    public static boolean crowd(ServerLevel level, BlockPos pos, @Nullable Entity cook, float roll) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.CAMPFIRES) || !state.getValue(CampfireBlock.LIT) || !(level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) return false;
        long cooking = campfire.getItems().stream().filter(s -> !s.isEmpty()).count();
        if (cooking == 0 || cooking >= campfire.getItems().size() || roll >= cooking * EXTINGUISH_PER_ITEM) return false;
        level.levelEvent(null, 1009, pos, 0);
        CampfireBlock.douse(cook, level, pos, state);
        level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
        return true;
    }

    /**
     * Whether the click reaches the campfire, as in {@code ServerPlayerGameMode.useItemOn}: a sneaking player holding an item
     * (the food) does not use the block, so no food is placed and the fire cannot be crowded.
     */
    public static boolean usesBlock(PlayerInteractEvent.RightClickBlock event) {
        return event.getUseBlock().isTrue() || event.getUseBlock().isDefault() && !event.getEntity().isSecondaryUseActive();
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack food = event.getItemStack();
        if (event.getLevel() instanceof ServerLevel level && !food.isEmpty() && usesBlock(event)
                && level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(food))
            crowd(level, event.getPos(), event.getEntity(), event.getEntity().getRandom().nextFloat());
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(Campfires::onUse);
    }
}
