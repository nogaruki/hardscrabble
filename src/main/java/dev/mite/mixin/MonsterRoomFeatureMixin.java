package dev.mite.mixin;

import dev.mite.underworld.UnderworldDungeonFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No monster room where its spawner cannot go ({@link UnderworldDungeonFeature#spawnerFits}): vanilla built the room around
 * the block, then logged "Failed to fetch mob spawner entity". No event covers feature placement. The test runs once both
 * room radii are drawn, as a vanilla refusal does: refusing at the head shifted the random draws of every later room of the
 * chunk (the e2e Underworld area lost two of its six dungeons).
 */
@Mixin(MonsterRoomFeature.class)
abstract class MonsterRoomFeatureMixin {
    @Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 1, shift = At.Shift.AFTER),
            cancellable = true)
    private void mite$spawnerFits(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
        if (!UnderworldDungeonFeature.spawnerFits(level, origin)) cir.setReturnValue(false);
    }
}
