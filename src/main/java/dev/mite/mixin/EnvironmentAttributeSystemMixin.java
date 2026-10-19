package dev.mite.mixin;

import dev.mite.lunar.MiteSky;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MX-14b (docs/02 §5.2, EF-LUN-04, EF-DIM-01): no API adds environment attribute layers to a level, and the client level's
 * system is final. Appends {@link MiteSky}'s time-based layers (moon glow, sunrise colours, fogs) after the vanilla dynamic ones.
 */
@Mixin(EnvironmentAttributeSystem.class)
abstract class EnvironmentAttributeSystemMixin {
    @Inject(method = "addDynamicLayers", at = @At("TAIL"))
    private static void mite$layers(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        MiteSky.addLayers(builder, level);
    }
}
