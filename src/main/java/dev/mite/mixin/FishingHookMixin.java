package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mite.equipment.Fishing;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Changelog l.829-830 (R117), l.643 (R129), l.212 (R166): the vanilla wait and bite window of FishingHook#catchingFish follow
 * the MITE rates ({@link Fishing#timing}). No event exposes the bite timing, hence the mixin.
 */
@Mixin(FishingHook.class)
abstract class FishingHookMixin {
    @Shadow @Final private int lureSpeed;

    @WrapOperation(method = "catchingFish", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;nextInt(Lnet/minecraft/util/RandomSource;II)I"))
    private int mite$timing(RandomSource random, int min, int max, Operation<Integer> original) {
        return Fishing.timing((FishingHook) (Object) this, random, min, max, original.call(random, min, max), lureSpeed);
    }
}
