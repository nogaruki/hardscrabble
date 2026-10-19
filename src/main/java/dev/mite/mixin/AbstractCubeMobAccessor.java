package dev.mite.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Cubes only hurt players and iron golems they bump: the MITE prey rules (CubeRules) reuse their touch attack on animals and villagers. */
@Mixin(AbstractCubeMob.class)
public interface AbstractCubeMobAccessor {
    @Invoker("dealDamage")
    void mite$dealDamage(LivingEntity target);

    @Invoker("canDealDamage")
    boolean mite$canDealDamage();
}
