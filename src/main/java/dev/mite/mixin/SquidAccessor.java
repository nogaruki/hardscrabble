package dev.mite.mixin;

import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The squid swims along its private movement vector: the MITE hunt steers it (SquidHuntGoal). */
@Mixin(Squid.class)
public interface SquidAccessor {
    @Accessor("movementVector")
    void mite$setMovementVector(Vec3 vector);
}
