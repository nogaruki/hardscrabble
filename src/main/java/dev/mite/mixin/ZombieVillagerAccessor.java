package dev.mite.mixin;

import java.util.UUID;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Changelog l.241 (R163): the enchanted golden apple starts the vanilla cure with no delay (MobRules). */
@Mixin(ZombieVillager.class)
public interface ZombieVillagerAccessor {
    @Invoker("startConverting")
    void mite$startConverting(@Nullable UUID player, int time);
}
