package dev.mite.entity.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mite.MiteMod;
import java.util.Map;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.item.Items;

/**
 * Collar and wolf armour drawn on the dire wolf model (the vanilla layers only fit the vanilla wolf): the collar is the dyed
 * collar texture over the model itself, the armour an inflated copy of the model (MiteModelLayers.DIRE_WOLF_ARMOR) with
 * scute plates, a dyeable overlay and damage cracks. Both use DireWolfModel for the sitting pose.
 */
final class DireWolfLayers {
    private DireWolfLayers() {}

    private static final Identifier COLLAR = MiteMod.id("textures/entity/dire_wolf_collar.png");
    private static final Identifier ARMOR = MiteMod.id("textures/entity/dire_wolf_armor.png");
    private static final Identifier ARMOR_OVERLAY = MiteMod.id("textures/entity/dire_wolf_armor_overlay.png");
    /** Damage cracks like vanilla wolf armour, painted on the dire wolf layout. */
    private static final Map<Crackiness.Level, Identifier> CRACKS = Map.of(
            Crackiness.Level.LOW, MiteMod.id("textures/entity/dire_wolf_armor_crackiness_low.png"),
            Crackiness.Level.MEDIUM, MiteMod.id("textures/entity/dire_wolf_armor_crackiness_medium.png"),
            Crackiness.Level.HIGH, MiteMod.id("textures/entity/dire_wolf_armor_crackiness_high.png"));

    /**
     * The vanilla sitting pose sets absolute angles meant for the vanilla parts (body and mane boxes built vertical and turned
     * 90 degrees, shorter legs): on this model's level body it lifted the rump above the chest. Same pose for this geometry:
     * rump on the ground, hind legs folded forward under it, chest raised on straight front legs, tail along the ground.
     */
    static final class DireWolfModel extends AdultWolfModel {
        private final ModelPart upperBody;

        DireWolfModel(ModelPart root) {
            super(root);
            this.upperBody = root.getChild("upper_body");
        }

        @Override
        protected void setSittingPose(WolfRenderState state) {
            body.y += 5;
            body.xRot = (float) (-Math.PI / 4);
            upperBody.xRot = (float) (-Math.PI / 10);
            tail.y += 11;
            for (ModelPart leg : new ModelPart[] {rightHindLeg, leftHindLeg}) {
                leg.y += 8.5F;
                leg.xRot = (float) (-Math.PI / 2);
            }
        }
    }

    static final class Collar extends RenderLayer<WolfRenderState, WolfModel> {
        Collar(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WolfRenderState state, float yRot, float xRot) {
            if (state.collarColor == null || state.isInvisible) return;
            collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityCutout(COLLAR), light, OverlayTexture.NO_OVERLAY,
                    state.collarColor.getTextureDiffuseColor(), null, state.outlineColor);
        }
    }

    static final class Armor extends RenderLayer<WolfRenderState, WolfModel> {
        private final WolfModel model;

        Armor(RenderLayerParent<WolfRenderState, WolfModel> renderer, EntityRendererProvider.Context context) {
            super(renderer);
            this.model = new DireWolfModel(context.bakeLayer(MiteModelLayers.DIRE_WOLF_ARMOR));
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WolfRenderState state, float yRot, float xRot) {
            if (!state.bodyArmorItem.is(Items.WOLF_ARMOR) || state.isInvisible) return;
            model.setupAnim(state);
            collector.order(2).submitModel(model, state, poseStack, RenderTypes.armorCutoutNoCull(ARMOR), light, OverlayTexture.NO_OVERLAY,
                    -1, null, state.outlineColor);
            var dye = state.bodyArmorItem.get(DataComponents.DYED_COLOR);
            if (dye != null)
                collector.order(3).submitModel(model, state, poseStack, RenderTypes.armorCutoutNoCull(ARMOR_OVERLAY), light, OverlayTexture.NO_OVERLAY,
                        ARGB.opaque(dye.rgb()), null, state.outlineColor);
            Crackiness.Level crackiness = Crackiness.WOLF_ARMOR.byDamage(state.bodyArmorItem);
            if (crackiness != Crackiness.Level.NONE)
                collector.order(4).submitModel(model, state, poseStack, RenderTypes.wolfArmorCracks(CRACKS.get(crackiness)), light,
                        OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }
}
