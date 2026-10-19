package dev.mite.crafting;

import com.mojang.serialization.MapCodec;
import dev.mite.combat.CombatContent;
import dev.mite.core.Material;
import dev.mite.harvest.HarvestContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * X11 / changelog l.778 (R118) and l.331 (R152): a damaged leather armor piece combined with sinew or string (the lashings) in a
 * crafting grid. Each one restores 1/components of the durability: leather gives 4 sinews (l.779), so a full repair costs 1/4 of
 * the leather of the piece. An economy repair (l.412): extra lashings that would repair nothing are refused, not wasted.
 */
public final class LeatherRepairRecipe extends CustomRecipe {
    public static final LeatherRepairRecipe INSTANCE = new LeatherRepairRecipe();
    public static final RecipeSerializer<LeatherRepairRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    private LeatherRepairRecipe() {}

    private static boolean isLeatherArmor(ItemStack stack) {
        return CombatContent.armorEntry(stack).filter(a -> a.material() == Material.LEATHER).isPresent();
    }

    /** Durability one lashing restores: max damage / leathers in the recipe, at least 1. */
    public static int perLashing(ItemStack armor) {
        return Math.max(1, Math.round(armor.getMaxDamage() / CraftQuality.components(armor).orElse(1f)));
    }

    /** The single damaged leather armor piece of the grid, when every other ingredient is a lashing; null otherwise. */
    private static @Nullable ItemStack armor(CraftingInput input) {
        ItemStack armor = null;
        int lashings = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(HarvestContent.LASHINGS)) lashings++;
            else if (armor == null && isLeatherArmor(s) && s.getCount() == 1 && s.isDamaged()) armor = s;
            else return null;
        }
        if (armor == null || lashings == 0) return null;
        // Economy: the last lashing must still be needed.
        return (lashings - 1) * perLashing(armor) < armor.getDamageValue() ? armor : null;
    }

    private static int lashings(CraftingInput input) {
        int n = 0;
        for (int i = 0; i < input.size(); i++) if (input.getItem(i).is(HarvestContent.LASHINGS)) n++;
        return n;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return armor(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack armor = armor(input);
        if (armor == null) return ItemStack.EMPTY;
        ItemStack result = armor.copyWithCount(1);
        result.setDamageValue(Math.max(0, armor.getDamageValue() - lashings(input) * perLashing(armor)));
        return result;
    }

    @Override
    public RecipeSerializer<LeatherRepairRecipe> getSerializer() {
        return SERIALIZER;
    }
}
