package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.datafixers.util.Pair;
import dev.mite.environment.Environment;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EF-MON-08 (L03): no mushroom fields. The overworld multi-noise preset is built in code from this builder; a datapack could
 * only replace it with a full inline parameter list (thousands of entries per world preset), and no NeoForge hook exists.
 * Without the entry, its climate cell falls back to the nearest one (deep ocean). Rule in {@link Environment#keepsBiome}.
 */
@Mixin(OverworldBiomeBuilder.class)
abstract class OverworldBiomeBuilderMixin {
    @WrapWithCondition(method = "addOffCoastBiomes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addSurfaceBiome(Ljava/util/function/Consumer;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;FLnet/minecraft/resources/ResourceKey;)V"))
    private boolean mite$noMushroomFields(OverworldBiomeBuilder self, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes,
            Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion,
            Climate.Parameter weirdness, float offset, ResourceKey<Biome> biome) {
        return Environment.keepsBiome(biome);
    }
}
