package dev.mite.entity.mob.client;

import dev.mite.MiteMod;
import dev.mite.entity.mob.DemonSpider;
import dev.mite.entity.mob.MobContent;
import dev.mite.entity.mob.WoodSpider;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractSkeletonRenderer;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.model.monster.silverfish.SilverfishModel;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.SilverfishRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderers of the MITE mobs: vanilla renderers (animations, layers) drawing the original MITE models of MiteModelLayers. */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class MobRenderers {
    private MobRenderers() {}

    private static Identifier texture(EntityType<?> type) {
        return MiteMod.id("textures/entity/" + EntityType.getKey(type).getPath() + ".png");
    }

    /** The original MITE models, generated into MiteModelLayers by tools/pixelart/models3d.py (sources: docs/art/models/*.bbmodel). */
    @SubscribeEvent
    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        MiteModelLayers.register(event);
    }

    /** A zombie-family mob drawn with its own humanoid model layer (MiteModelLayers), vanilla zombie armour. */
    private static EntityRendererProvider<Zombie> zombie(EntityType<?> type, ModelLayerLocation layer) {
        Identifier texture = texture(type);
        return context -> new ZombieRenderer(context, layer, layer, ModelLayers.ZOMBIE_ARMOR, ModelLayers.ZOMBIE_BABY_ARMOR) {
            @Override
            public Identifier getTextureLocation(ZombieRenderState state) {
                return texture;
            }
        };
    }

    private static <T extends AbstractSkeleton> EntityRendererProvider<T> skeleton(EntityType<T> type, ModelLayerLocation layer) {
        Identifier texture = texture(type);
        return context -> new AbstractSkeletonRenderer<T, SkeletonRenderState>(context, layer, ModelLayers.SKELETON_ARMOR) {
            @Override
            public Identifier getTextureLocation(SkeletonRenderState state) {
                return texture;
            }

            @Override
            public SkeletonRenderState createRenderState() {
                return new SkeletonRenderState();
            }
        };
    }

    /** Dire wolf and hellhound: the MITE wolf model (WolfModel parts, so the vanilla wolf animations apply), with its own collar and armour layers. */
    private static EntityRendererProvider<Wolf> wolf(EntityType<?> type, ModelLayerLocation layer) {
        Identifier texture = texture(type);
        return context -> new MobRenderer<Wolf, WolfRenderState, WolfModel>(context, new DireWolfLayers.DireWolfModel(context.bakeLayer(layer)), 0.7F) {
            {
                addLayer(new DireWolfLayers.Armor(this, context));
                addLayer(new DireWolfLayers.Collar(this));
            }

            @Override
            public WolfRenderState createRenderState() {
                return new WolfRenderState();
            }

            @Override
            public Identifier getTextureLocation(WolfRenderState state) {
                return texture;
            }

            @Override
            public void extractRenderState(Wolf entity, WolfRenderState state, float partialTicks) {
                super.extractRenderState(entity, state, partialTicks);
                state.isAngry = entity.isAngry();
                state.isSitting = entity.isInSittingPose();
                state.tailAngle = entity.getTailAngle();
                state.headRollAngle = entity.getHeadRollAngle(partialTicks);
                state.shakeAnim = entity.getShakeAnim(partialTicks);
                state.wetShade = entity.getWetShade(partialTicks);
                state.collarColor = entity.isTame() ? entity.getCollarColor() : null;
                state.bodyArmorItem = entity.getBodyArmorItem().copy();
            }
        };
    }

    /** MITE spider model; the vanilla eye glow is replaced by one painted on this model (textures/entity/<spider>_eyes.png). */
    private static <T extends Spider> EntityRendererProvider<T> spider(EntityType<?> type, float shadow) {
        Identifier texture = texture(type);
        RenderType eyes = RenderTypes.eyes(MiteMod.id("textures/entity/" + EntityType.getKey(type).getPath() + "_eyes.png"));
        return context -> new SpiderRenderer<T>(context, MiteModelLayers.SPIDER) {
            {
                layers.clear();
                addLayer(new EyesLayer<>(this) {
                    @Override
                    public RenderType renderType() {
                        return eyes;
                    }
                });
                shadowRadius = shadow;
            }

            @Override
            public Identifier getTextureLocation(LivingEntityRenderState state) {
                return texture;
            }
        };
    }

    private static EntityRendererProvider<Slime> slime(EntityType<?> type) {
        Identifier texture = texture(type);
        return context -> new SlimeRenderer(context) {
            {
                model = new SlimeModel(context.bakeLayer(MiteModelLayers.GELATINOUS_CUBE));
                // the vanilla shell layer, drawn with this cube's own texture (the vanilla layer hard-codes the slime's green)
                layers.removeIf(l -> l instanceof net.minecraft.client.renderer.entity.layers.SlimeOuterLayer);
                SlimeModel shell = new SlimeModel(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SLIME_OUTER));
                addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
                    @Override
                    public void submit(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
                                       SlimeRenderState state, float yRot, float xRot) {
                        boolean outlineOnly = state.appearsGlowing() && state.isInvisible;
                        if (state.isInvisible && !outlineOnly) return;
                        collector.order(1).submitModel(shell, state, pose, outlineOnly ? net.minecraft.client.renderer.rendertype.RenderTypes.outline(texture)
                                : net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture), light,
                                net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(state, 0f), state.outlineColor);
                    }
                });
            }

            @Override
            public Identifier getTextureLocation(SlimeRenderState state) {
                return texture;
            }
        };
    }

    private static EntityRendererProvider<Bat> bat(EntityType<?> type) {
        Identifier texture = texture(type);
        return context -> new BatRenderer(context) {
            {
                model = new BatModel(context.bakeLayer(MiteModelLayers.BAT));
            }

            @Override
            public Identifier getTextureLocation(BatRenderState state) {
                return texture;
            }
        };
    }

    private static EntityRendererProvider<Silverfish> silverfish(EntityType<?> type) {
        Identifier texture = texture(type);
        return context -> new SilverfishRenderer(context) {
            {
                model = new SilverfishModel(context.bakeLayer(MiteModelLayers.SILVERFISH));
            }

            @Override
            public Identifier getTextureLocation(LivingEntityRenderState state) {
                return texture;
            }
        };
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MobContent.GHOUL.get(), zombie(MobContent.GHOUL.get(), MiteModelLayers.GHOUL));
        event.registerEntityRenderer(MobContent.WIGHT.get(), zombie(MobContent.WIGHT.get(), MiteModelLayers.WRAITH));
        event.registerEntityRenderer(MobContent.REVENANT.get(), zombie(MobContent.REVENANT.get(), MiteModelLayers.REVENANT));
        event.registerEntityRenderer(MobContent.LONGDEAD.get(), skeleton(MobContent.LONGDEAD.get(), MiteModelLayers.LONGDEAD));
        event.registerEntityRenderer(MobContent.LONGDEAD_GUARDIAN.get(), skeleton(MobContent.LONGDEAD_GUARDIAN.get(), MiteModelLayers.LONGDEAD));
        event.registerEntityRenderer(MobContent.SHADOW.get(), zombie(MobContent.SHADOW.get(), MiteModelLayers.WRAITH));
        event.registerEntityRenderer(MobContent.INVISIBLE_STALKER.get(), zombie(MobContent.INVISIBLE_STALKER.get(), MiteModelLayers.WRAITH));
        event.registerEntityRenderer(MobContent.DIRE_WOLF.get(), wolf(MobContent.DIRE_WOLF.get(), MiteModelLayers.DIRE_WOLF));
        event.registerEntityRenderer(MobContent.HELLHOUND.get(), wolf(MobContent.HELLHOUND.get(), MiteModelLayers.DIRE_WOLF));
        event.registerEntityRenderer(MobContent.THROWN_WEB.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(MobContent.EARTH_ELEMENTAL.get(), zombie(MobContent.EARTH_ELEMENTAL.get(), MiteModelLayers.EARTH_ELEMENTAL));
        event.registerEntityRenderer(MobContent.BONE_LORD.get(), skeleton(MobContent.BONE_LORD.get(), MiteModelLayers.BONE_LORD));
        event.registerEntityRenderer(MobContent.ANCIENT_BONE_LORD.get(), skeleton(MobContent.ANCIENT_BONE_LORD.get(), MiteModelLayers.BONE_LORD));
        event.registerEntityRenderer(MobContent.PHASE_SPIDER.get(), spider(MobContent.PHASE_SPIDER.get(), 0.8F));
        event.registerEntityRenderer(MobContent.BLACK_WIDOW.get(), spider(MobContent.BLACK_WIDOW.get(), 0.8F));
        event.registerEntityRenderer(MobContent.CLAY_GOLEM.get(), zombie(MobContent.CLAY_GOLEM.get(), MiteModelLayers.EARTH_ELEMENTAL));
        for (var cube : java.util.List.of(MobContent.JELLY, MobContent.BLOB, MobContent.OOZE, MobContent.PUDDING)) event.registerEntityRenderer(cube.get(), slime(cube.get()));
        for (var bat : java.util.List.of(MobContent.VAMPIRE_BAT, MobContent.GIANT_VAMPIRE_BAT)) event.registerEntityRenderer(bat.get(), bat(bat.get()));
        event.registerEntityRenderer(MobContent.NIGHTWING.get(), bat(MobContent.NIGHTWING.get()));
        for (var fish : java.util.List.of(MobContent.COPPERSPINE, MobContent.NETHERSPAWN, MobContent.HOARY_SILVERFISH)) event.registerEntityRenderer(fish.get(), silverfish(fish.get()));
        event.registerEntityRenderer(MobContent.DEMON_SPIDER.get(), MobRenderers.<DemonSpider>spider(MobContent.DEMON_SPIDER.get(), 0.8F));
        Identifier creeper = texture(MobContent.INFERNAL_CREEPER.get());
        event.registerEntityRenderer(MobContent.INFERNAL_CREEPER.get(), context -> new CreeperRenderer(context) {
            {
                model = new CreeperModel(context.bakeLayer(MiteModelLayers.INFERNAL_CREEPER));
            }

            @Override
            public Identifier getTextureLocation(CreeperRenderState state) {
                return creeper;
            }
        });
        Identifier fire = texture(MobContent.FIRE_ELEMENTAL.get());
        event.registerEntityRenderer(MobContent.FIRE_ELEMENTAL.get(), context -> new BlazeRenderer(context) {
            {
                model = new BlazeModel(context.bakeLayer(MiteModelLayers.FIRE_ELEMENTAL));
            }

            @Override
            public Identifier getTextureLocation(LivingEntityRenderState state) {
                return fire;
            }
        });
        event.registerEntityRenderer(MobContent.WOOD_SPIDER.get(), MobRenderers.<WoodSpider>spider(MobContent.WOOD_SPIDER.get(), 0.56F));
    }
}
