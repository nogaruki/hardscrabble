package dev.mite.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.mite.lunar.client.LunarSky;
import java.util.function.Supplier;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MX-14 (docs/02 §5.2, conditional): the moon's colour modulator is hardcoded white; {@link LunarSky} tints it per moon type
 * (EF-LUN-04). The moon dog's halo (EF-LUN-01) is a second celestial quad drawn around the moon with the vanilla pipeline.
 */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Shadow @Final private TextureAtlas celestialsAtlas;
    @Unique private GpuBuffer mite$halo;

    @Shadow
    private static GpuBuffer buildCelestialQuad(String name, TextureAtlasSprite sprite) {
        throw new AssertionError();
    }

    @Shadow
    private Matrix4f applyCelestialBodyTransform(PoseStack poseStack, float height, float scale) {
        throw new AssertionError();
    }

    @Shadow
    private void drawCelestialBody(Supplier<String> label, RenderPass renderPass, GpuBufferSlice dynamicTransforms, GpuBuffer indexBuffer,
            GpuBuffer vertexBuffer, int baseVertex) {
        throw new AssertionError();
    }

    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 1)
    private Vector4f mite$moonColor(Vector4f color) {
        return LunarSky.moonColor(color);
    }

    @Inject(method = "renderMoon", at = @At("TAIL"))
    private void mite$halo(RenderPass pass, MoonPhase phase, float rainBrightness, PoseStack poseStack, CallbackInfo ci) {
        if (!LunarSky.showHalo()) return;
        if (mite$halo == null) mite$halo = buildCelestialQuad("MITE moon halo", celestialsAtlas.getSprite(LunarSky.HALO));
        Matrix4f transform = applyCelestialBodyTransform(poseStack, 100.0F, LunarSky.HALO_SIZE);
        GpuBufferSlice uniforms = RenderSystem.getDynamicUniforms().writeTransform(transform, new Vector4f(1, 1, 1, rainBrightness));
        drawCelestialBody(() -> "Moon halo", pass, uniforms, RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6), mite$halo, 0);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void mite$close(CallbackInfo ci) {
        if (mite$halo != null) mite$halo.close();
    }
}
