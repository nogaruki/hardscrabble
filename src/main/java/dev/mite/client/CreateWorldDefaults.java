package dev.mite.client;

import dev.mite.MiteMod;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * EF-REG-03 (A03, A04): the world creation screen starts on Large Biomes and Hard (EF-REG-01, dom-A-13) and the bonus
 * chest switch snaps back to off.
 * Events only (no CreateWorldScreen mixin, MX-13 is pure ergonomics): the switch stays visible but cannot be turned on.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class CreateWorldDefaults {
    /** Vanilla reopens the same screen after Game Rules, Data Packs and Experiments: apply the defaults once per screen. */
    private static final Set<CreateWorldScreen> HANDLED = Collections.newSetFromMap(new WeakHashMap<>());

    private CreateWorldDefaults() {}

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof CreateWorldScreen screen) || !HANDLED.add(screen)) return;
        WorldCreationUiState ui = screen.getUiState();
        if (ui.getWorldType().preset() != null && ui.getWorldType().preset().is(WorldPresets.NORMAL)) {
            Stream.concat(ui.getNormalPresetList().stream(), ui.getAltPresetList().stream())
                    .filter(t -> t.preset() != null && t.preset().is(WorldPresets.LARGE_BIOMES)).findFirst().ifPresent(ui::setWorldType);
        }
        ui.setDifficulty(Difficulty.HARD);
        ui.setBonusChest(false);
        ui.addListener(s -> {
            if (s.isBonusChest()) s.setBonusChest(false);
        });
    }
}
