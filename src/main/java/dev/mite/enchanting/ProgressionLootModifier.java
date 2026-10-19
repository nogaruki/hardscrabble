package dev.mite.enchanting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * GLM {@code hardscrabble:remove_item} (docs/02 §6.5, ET-L-07, ET-H-05): drops every stack in {@code items} and strips the enchantments in
 * {@code enchantments}; an enchanted book left without enchantment is dropped. Applies to every loot table, other mods included.
 */
public class ProgressionLootModifier extends LootModifier {
    public static final MapCodec<ProgressionLootModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).and(i.group(
            TagKey.codec(Registries.ITEM).fieldOf("items").forGetter(m -> m.items),
            TagKey.codec(Registries.ENCHANTMENT).fieldOf("enchantments").forGetter(m -> m.enchantments))).apply(i, ProgressionLootModifier::new));

    private final TagKey<Item> items;
    private final TagKey<Enchantment> enchantments;

    public ProgressionLootModifier(Optional<Holder<LootItemCondition>> condition, int priority, TagKey<Item> items, TagKey<Enchantment> enchantments) {
        super(condition, priority);
        this.items = items;
        this.enchantments = enchantments;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        // L08 targets chest, mob and fishing loot; block drops never contain blacklisted items and must stay untouched.
        if (context.hasParameter(LootContextParams.BLOCK_STATE)) return loot;
        loot.removeIf(stack -> {
            if (stack.is(items)) return true;
            EnchantmentHelper.updateEnchantments(stack, e -> e.removeIf(h -> h.is(enchantments)));
            return stack.is(Items.ENCHANTED_BOOK) && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
        });
        return loot;
    }

    @Override
    public MapCodec<? extends LootModifier> codec() {
        return CODEC;
    }
}
