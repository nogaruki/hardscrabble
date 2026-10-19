package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mite.curse.Curses;
import dev.mite.metabolism.MetabolismService;
import dev.mite.core.Formulas;
import dev.mite.enchanting.EnchantmentEffects;
import dev.mite.player.PlayerLevel;
import dev.mite.player.XpCurve;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * MX-02 (docs/02 §5.2): vanilla allows eating only while foodLevel (the nutrition mirror) is below 20; MITE also allows it
 * while the hidden satiety is below the cap. The condition reads the attachment, which no event exposes. Also the MITE XP span (B02)
 * and the attack cost.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
    @ModifyReturnValue(method = "canEat", at = @At("RETURN"))
    private boolean mite$canEat(boolean original) {
        return original || MetabolismService.wantsFood((Player) (Object) this);
    }

    /** B02 (EF-JOU-02): points of the current level on the MITE curve, read by /xp points and the vanilla mirror import. */
    @ModifyReturnValue(method = "getXpNeededForNextLevel", at = @At("RETURN"))
    private int mite$xpNeededForNextLevel(int original) {
        return XpCurve.span(PlayerLevel.get((Player) (Object) this));
    }

    /**
     * EF-NUT-02 / C-2: a melee hit that deals damage costs the 1.6.4 price (0.3) instead of vanilla's 0.1, reduced by Endurance
     * (changelog R145). Vanilla charges it only inside {@code if (wasHurt)}, which no event exposes: a hit without damage costs nothing.
     */
    @ModifyArg(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private float mite$attackCost(float vanilla) {
        return Formulas.HUNGER_ATTACK * EnchantmentEffects.hungerMultiplier((Player) (Object) this);
    }

    /** EF-NUT-05: sprint needs food energy, not the vanilla foodLevel (nutrition mirror) above 6. EF-MAL-02 #3: LocalPlayer reads
     *  this before starting or keeping a sprint, so Can't Run must act here too (no event). */
    @ModifyReturnValue(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"))
    private boolean mite$canSprint(boolean original) {
        Player player = (Player) (Object) this;
        return MetabolismService.canSprint(player) && Curses.canSprint(player);
    }
}
