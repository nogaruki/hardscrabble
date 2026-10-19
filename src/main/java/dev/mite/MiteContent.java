package dev.mite;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shared registrations. Each domain owns its own DeferredRegisters for blocks and items. */
public final class MiteContent {
    private MiteContent() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MiteMod.MODID);

    /** Lists every item in the mite namespace, so domains never touch this file. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hardscrabble"))
            .icon(() -> Items.FLINT.getDefaultInstance())
            .displayItems((params, output) -> BuiltInRegistries.ITEM.entrySet().stream()
                    .filter(e -> e.getKey().identifier().getNamespace().equals(MiteMod.MODID))
                    .forEach(e -> output.accept(e.getValue())))
            .build());

    static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
