package dev.mite.mixin;

import dev.mite.dimension.PortalRouting;
import dev.mite.dimension.runegate.Runegate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MX-06 (docs/02 §5.2): the portal destination depends on the block under the frame (EF-DIM-02). NeoForge has no portal
 * destination event. Spawn-bound portals return a plain teleport; otherwise only the vanilla target dimension is replaced,
 * so scaling, exit portal search and creation stay vanilla (MX-07 not needed: the Underworld range admits vanilla placement).
 */
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalBlockMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void mite$toSpawn(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
        if (Runegate.at(level, pos) instanceof Runegate.Gate gate) cir.setReturnValue(Runegate.teleport(level, entity, gate)); // EF-DIM-06
        else if (PortalRouting.toSpawn(level, pos)) cir.setReturnValue(PortalRouting.toOverworldSpawn(level, entity));
    }

    @ModifyVariable(method = "getPortalDestination", at = @At("STORE"), ordinal = 0)
    private ResourceKey<Level> mite$destination(ResourceKey<Level> vanilla, ServerLevel level, Entity entity, BlockPos pos) {
        return PortalRouting.destination(level, pos);
    }
}
