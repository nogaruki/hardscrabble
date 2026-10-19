package dev.mite.lunar.client;

import dev.mite.MiteMod;
import dev.mite.core.MoonCalendar.Moon;
import dev.mite.lunar.Lunar;
import dev.mite.lunar.MiteSky;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Vector4f;

/**
 * EF-LUN-04, ET-N-04: moon colour per moon type and the moon dog halo, applied by SkyRendererMixin (MX-14): no environment
 * attribute tints the moon in 26.3. The moon comes from the synced clock and config, as on the server.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class LunarSky {
    private LunarSky() {}

    /** CL R132: the next world's sunrise must not show this one's forecast; the server sends the new one on arrival. */
    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MiteSky.forecast = 0;
    }

    /** Ring sprite of the celestials atlas (assets/hardscrabble/textures/environment/celestial/moon_halo.png). */
    public static final Identifier HALO = MiteMod.id("moon_halo");
    /** Halo quad half-size, the moon being 20 [INFÉRÉ]. */
    public static final float HALO_SIZE = 60.0F;

    /** Tints the white modulator in place (alpha = rain fade, kept). */
    public static Vector4f moonColor(Vector4f color) {
        var level = Minecraft.getInstance().level;
        var tint = level == null ? null : MiteSky.moonTint(Lunar.moon(level));
        return tint == null ? color : color.mul(tint.x(), tint.y(), tint.z(), 1);
    }

    /** EF-LUN-01: a halo surrounds the full moon that heralds a blue moon (changelog l.466). */
    public static boolean showHalo() {
        var level = Minecraft.getInstance().level;
        return level != null && Lunar.moon(level) == Moon.MOON_DOG;
    }
}
