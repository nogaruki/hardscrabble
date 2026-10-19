package dev.mite.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Saved flag set on a projectile's first tick: a copy crossing a portal is not re-aimed (VanillaMobRules). */
@Mixin(Projectile.class)
public interface ProjectileAccessor {
    @Accessor("hasBeenShot")
    boolean mite$hasBeenShot();
}
