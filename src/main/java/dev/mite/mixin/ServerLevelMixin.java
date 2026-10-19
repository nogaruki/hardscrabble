package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mite.metabolism.MiteSleep;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * MX-08 (docs/02 §5.2): the "everyone sleeps" block of {@code tick} jumps the clock, wakes everyone and clears the rain.
 * {@code SleepFinishedTimeEvent} only moves the clock, so nothing in the API stops the wake-up and the weather reset;
 * MITE sleep (EF-NUT-11) simulates the night in {@link MiteSleep} instead.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/SleepStatus;areEnoughSleeping(I)Z"))
    private boolean mite$nightSkip(SleepStatus status, int percentage, Operation<Boolean> original) {
        return false; // MX-08: vanilla never skips the night, MiteSleep simulates it
    }
}
