package dev.mite.client;

import dev.mite.MiteMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry point: the Config button of Mods > Hardscrabble opens NeoForge's generic config editor (EF-CFG-02). */
@Mod(value = MiteMod.MODID, dist = Dist.CLIENT)
public final class MiteModClient {
    public MiteModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
