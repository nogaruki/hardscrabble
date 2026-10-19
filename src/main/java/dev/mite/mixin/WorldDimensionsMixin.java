package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Lifecycle;
import dev.mite.dimension.UnderworldContent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla rates every non-vanilla dimension "experimental", so every MITE world (it always has the Underworld) asks
 * "Worlds using Experimental Settings are not supported" at creation and when a server world is opened in solo.
 * NeoForge has no hook for it. The Underworld is part of the mod, not an experiment: rated stable.
 */
@Mixin(WorldDimensions.class)
abstract class WorldDimensionsMixin {
    @ModifyReturnValue(method = "checkStability", at = @At("RETURN"))
    private static Lifecycle mite$stableUnderworld(Lifecycle original, ResourceKey<LevelStem> key, LevelStem dimension) {
        return UnderworldContent.STEM.equals(key) ? Lifecycle.stable() : original;
    }
}
