package dev.mite.crafting;

import dev.mite.config.MiteConfig;
import dev.mite.core.Formulas;
import dev.mite.treasure.TreasureContent;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

/** Crafter restriction (EF-CRA-08, config {@code crafter_restriction}), called by MX-04. */
public final class CrafterRules {
    private CrafterRules() {}

    /** MITE duration at level 0 without bench bonus, instead of the vanilla 4-tick delay. */
    public static int delay(Level level, BlockPos pos, int vanilla) {
        if (!MiteConfig.CRAFTER_RESTRICTION.get() || !(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) return vanilla;
        return Formulas.craftTicks(TimedCrafting.difficulty(crafter.getItems()), 0, 0, false);
    }

    /**
     * A grid holding any metal ingredient, or an unlashed flint/obsidian tool (EF-OUT-12), is refused with the vanilla failure sound.
     * A coin is refused whatever the config (RE-04): minting costs the crafting player its XP value, and a crafter has no player.
     * While tool_lashing is on, the {@code hardscrabble:tool_lashing_disabled} condition already keeps the unlashed recipes out; the lashing
     * check only matters when the option is turned on after the datapacks loaded (until the next /reload).
     */
    public static boolean refuse(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter)) return false;
        CraftingInput input = crafter.asCraftInput();
        Optional<ItemStack> result = CrafterBlock.getPotentialResults(level, input).map(r -> r.value().assemble(input));
        boolean coin = result.filter(s -> s.getItem() instanceof TreasureContent.CoinItem).isPresent();
        if (!coin && (!MiteConfig.CRAFTER_RESTRICTION.get() || TimedCrafting.hardestMetal(crafter.getItems()) == null
                && result.map(s -> !TimedCrafting.missingLashing(s, crafter.getItems())).orElse(true))) return false;
        level.levelEvent(1050, pos, 0);
        return true;
    }
}
