package dev.mite.mixin;

import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How long a target goal keeps an unseen target: read by the digger GameTests (AiContent.DIGGER_UNSEEN_MEMORY). */
@Mixin(TargetGoal.class)
public interface TargetGoalAccessor {
    @Accessor("unseenMemoryTicks")
    int mite$unseenMemoryTicks();
}
