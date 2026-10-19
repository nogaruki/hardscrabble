package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.mite.metabolism.MetabolismService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * MX-01 (docs/02 §5.2): vanilla hunger is replaced by {@link MetabolismService}. No NeoForge event covers the food tick,
 * so tick delegates to the service, which consumes the exhaustion vanilla accrued (no double counting, EF-NUT-02).
 * Replacing the tick also removes vanilla natural regeneration and starvation (EF-NUT-01 CA-2) without touching the gamerule.
 * Item eating goes through LivingEntityUseItemEvent.Finish (the data map needs the item); item-less calls (cake, Saturation)
 * are handed to the next tick (Saturation feeds nothing: SaturationMobEffectMixin).
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
    @Shadow private float exhaustionLevel;
    @Unique private int mite$pendingFood;

    @WrapMethod(method = "tick")
    private void mite$tick(ServerPlayer player, Operation<Void> original) {
        float exhaustion = this.exhaustionLevel;
        int food = this.mite$pendingFood;
        this.exhaustionLevel = 0;
        this.mite$pendingFood = 0;
        MetabolismService.tick(player, (FoodData) (Object) this, exhaustion, food);
    }

    @WrapMethod(method = "eat(IF)V")
    private void mite$eat(int food, float saturationModifier, Operation<Void> original) {
        this.mite$pendingFood += food;
    }

    @WrapMethod(method = "eat(Lnet/minecraft/world/food/FoodProperties;)V")
    private void mite$eat(FoodProperties food, Operation<Void> original) {}
}
