package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mite.trading.Trading;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Audit 2 X5: the igloo basement places no villager nor zombie villager ({@link Trading#NO_VILLAGERS}). The igloo hard-codes its
 * placement settings (no processor list in data) and NeoForge has no structure entity event.
 */
@Mixin(IglooPieces.IglooPiece.class)
abstract class IglooPieceMixin {
    @ModifyReturnValue(method = "makeSettings", at = @At("RETURN"))
    private static StructurePlaceSettings mite$noVillagers(StructurePlaceSettings settings) {
        return settings.addProcessor(Trading.NO_VILLAGERS);
    }
}
