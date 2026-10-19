package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * X10 / EF-NUT-09: the Saturation effect feeds nothing. Each tick it calls {@code FoodData.eat(int, float)}, which MITE turns
 * into satiety and nutrition, so a suspicious stew made with a dandelion (7 ticks of Saturation) gave about +7 / +7 on top of
 * its 2 / 4 table value. The stew keeps its table value; no other effect has a hidden food value in MITE.
 */
@Mixin(targets = "net.minecraft.world.effect.SaturationMobEffect")
abstract class SaturationMobEffectMixin {
    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"))
    private void mite$noFood(FoodData food, int nutrition, float saturation, Operation<Void> original) {}
}
