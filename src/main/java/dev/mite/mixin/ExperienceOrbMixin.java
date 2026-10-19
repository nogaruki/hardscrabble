package dev.mite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mite.player.XpRules;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * B-2 (EF-JOU-05 WIKI_THIRD): the orbs of a dead player stay his. Vanilla merges nearby orbs of equal value, which would hand
 * his XP to everyone or lock ordinary XP to him; no event covers the merge.
 */
@Mixin(ExperienceOrb.class)
abstract class ExperienceOrbMixin {
    /** Orbs already in the world merge only with orbs of the same owner (or both without one). */
    @ModifyReturnValue(method = "canMerge(Lnet/minecraft/world/entity/ExperienceOrb;)Z", at = @At("RETURN"))
    private boolean mite$sameOwner(boolean original, ExperienceOrb other) {
        return original && Objects.equals(XpRules.owner((ExperienceOrb) (Object) this), XpRules.owner(other));
    }

    /**
     * A newly awarded (ownerless) orb never joins an owned one. Filtered in the award search only: the static canMerge is also
     * the core of the instance check above, where two orbs of the same owner must still merge.
     */
    @ModifyArg(method = "tryMergeToExisting", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntities"
            + "(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            index = 2)
    private static Predicate<ExperienceOrb> mite$ownerless(Predicate<ExperienceOrb> merge) {
        return merge.and(orb -> XpRules.owner(orb) == null);
    }
}
