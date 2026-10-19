package dev.mite.rules;

import dev.mite.MiteMod;
import dev.mite.config.MiteConfig;
import dev.mite.core.MoonCalendar;
import dev.mite.player.ProgressionService;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Gem sacrifice (EF-JOU-08, B09, daily emerald cap of audit 2 X1) and the held stick or bone (EF-OUT-10, B11). */
@EventBusSubscriber(modid = MiteMod.MODID)
public final class ItemRules {
    private ItemRules() {}

    /** EF-JOU-08: XP points given by one sacrificed gem. */
    public static final Map<Item, Integer> GEM_XP = Map.of(Items.DIAMOND, 500, Items.EMERALD, 250, Items.QUARTZ, 50, Items.LAPIS_LAZULI, 25);

    /** EF-OUT-10: a stick or bone in the main hand adds 0.5 to the attack reach. Block reach is computed per block by the reach rules (D08). */
    public static final double HELD_REACH = 0.5;

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        Integer xp = GEM_XP.get(stack.getItem());
        if (xp == null) return;
        event.setCanceled(true);
        // the server decides (daily emerald cap) and syncs the stack; the client only plays the swing
        if (event.getLevel().isClientSide()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        Player player = event.getEntity();
        if (stack.is(Items.EMERALD) && !takeEmeraldSacrifice(player)) {
            player.sendOverlayMessage(Component.translatable("message.hardscrabble.emerald_sacrifice_limit"));
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ProgressionService.addXp(player, xp);
        stack.consume(1, player);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Audit 2 X1 (RE-04 ≤ 750 XP a day for a riskless repeatable source): villagers buy crops and sticks for emeralds, so the
     * emerald sacrifice is capped per player and per world day ({@code trading.emerald_sacrifices_per_day}). Counts one if allowed.
     */
    static boolean takeEmeraldSacrifice(Player player) {
        long day = MoonCalendar.day(player.level().getServer().overworld().getDefaultClockTime());
        ProgressionService.EmeraldSacrifices done = player.getData(ProgressionService.EMERALD_SACRIFICES);
        int today = done.day() == day ? done.count() : 0;
        if (today >= MiteConfig.EMERALD_SACRIFICES_PER_DAY.getAsInt()) return false;
        player.setData(ProgressionService.EMERALD_SACRIFICES, new ProgressionService.EmeraldSacrifices(day, today + 1));
        return true;
    }

    @SubscribeEvent
    static void modifyComponents(ModifyDefaultComponentsEvent event) {
        ItemAttributeModifiers reach = ItemAttributeModifiers.builder().add(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(MiteMod.id("held_reach"), HELD_REACH, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
        for (Item item : new Item[] {Items.STICK, Items.BONE}) event.modify(item, (b, _, _) -> b.set(DataComponents.ATTRIBUTE_MODIFIERS, reach));
    }
}
