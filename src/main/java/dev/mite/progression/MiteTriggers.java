package dev.mite.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import java.util.Optional;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom advancement triggers (ET-P-01): {@code hardscrabble:crafted_item} (an item taken out of a crafting grid) and {@code hardscrabble:event} (named code events). */
public final class MiteTriggers {
    private MiteTriggers() {}

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, MiteMod.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, Crafted> CRAFTED = TRIGGERS.register("crafted_item", Crafted::new);
    public static final DeferredHolder<CriterionTrigger<?>, Event> EVENT = TRIGGERS.register("event", Event::new);

    static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    public static final class Crafted extends SimpleCriterionTrigger<Crafted.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, ItemStack crafted) {
            trigger(player, i -> i.item().test(crafted));
        }

        public record Instance(Optional<Holder<LootItemCondition>> player, ItemPredicate item) implements SimpleInstance {
            static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ItemPredicate.CODEC.fieldOf("item").forGetter(Instance::item)).apply(i, Instance::new));
        }

        public static Criterion<Instance> of(ItemPredicate item) {
            return CRAFTED.get().createCriterion(new Instance(Optional.empty(), item));
        }
    }

    public static final class Event extends SimpleCriterionTrigger<Event.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String event) {
            trigger(player, i -> i.event().equals(event));
        }

        public record Instance(Optional<Holder<LootItemCondition>> player, String event) implements SimpleInstance {
            static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.fieldOf("event").forGetter(Instance::event)).apply(i, Instance::new));
        }

        public static Criterion<Instance> of(String event) {
            return EVENT.get().createCriterion(new Instance(Optional.empty(), event));
        }
    }
}
