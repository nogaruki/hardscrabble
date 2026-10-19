package dev.mite.enchanting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * GLM {@code hardscrabble:replace_item} (docs/02 §6.5, ET-L-07, EF-MON-06): in the loot tables whose path starts with one of {@code tables}
 * (chests, vaults, archaeology, pots), every stack matching a rule becomes {@code min..max} of its replacement, whatever its count.
 */
public class ReplaceItemModifier extends LootModifier {
    public record Rule(List<Item> from, Item to, int min, int max) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("from").forGetter(Rule::from),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("to").forGetter(Rule::to),
                Codec.INT.fieldOf("min").forGetter(Rule::min),
                Codec.INT.fieldOf("max").forGetter(Rule::max)).apply(i, Rule::new));
    }

    public static final MapCodec<ReplaceItemModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).and(i.group(
            Codec.STRING.listOf().fieldOf("tables").forGetter(m -> m.tables),
            Rule.CODEC.listOf().fieldOf("rules").forGetter(m -> m.rules))).apply(i, ReplaceItemModifier::new));

    private final List<String> tables;
    private final List<Rule> rules;

    public ReplaceItemModifier(Optional<Holder<LootItemCondition>> condition, int priority, List<String> tables, List<Rule> rules) {
        super(condition, priority);
        this.tables = tables;
        this.rules = rules;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        String path = context.getQueriedLootTableId().getPath();
        if (tables.stream().noneMatch(path::startsWith)) return loot;
        loot.replaceAll(stack -> rules.stream().filter(r -> r.from.contains(stack.getItem())).findFirst()
                .map(r -> new ItemStack(r.to, r.min + context.getRandom().nextInt(r.max - r.min + 1))).orElse(stack));
        return loot;
    }

    @Override
    public MapCodec<? extends LootModifier> codec() {
        return CODEC;
    }
}
