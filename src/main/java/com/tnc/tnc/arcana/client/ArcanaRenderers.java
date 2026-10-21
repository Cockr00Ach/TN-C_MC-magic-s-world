package com.tnc.tnc.arcana.client;
import com.tnc.tnc.arcana.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class ArcanaRenderers {
    private static final ModelLayerLocation BEAST=new ModelLayerLocation(ResourceLocation.parse("tnc:void_devourer"),"main"),SPIRIT=new ModelLayerLocation(ResourceLocation.parse("tnc:dimensional_spirit"),"main");
    @SubscribeEvent public static void layers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(BEAST,()->ArcanaEntityModel.layer(false));e.registerLayerDefinition(SPIRIT,()->ArcanaEntityModel.layer(true));}
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ArcanaContent.DEVOURER,c->new Renderer<>(c,false));e.registerEntityRenderer(ArcanaContent.SPIRIT,c->new Renderer<>(c,true));e.registerEntityRenderer(ArcanaContent.WASTE_FLASK,ThrownItemRenderer::new);}
    private static final class Renderer<T extends Mob> extends MobRenderer<T,ArcanaEntityModel<T>> {
        private final boolean spirit;
        Renderer(EntityRendererProvider.Context c,boolean spirit){super(c,new ArcanaEntityModel<>(c.bakeLayer(spirit?SPIRIT:BEAST),spirit),spirit?0:1.4f);this.spirit=spirit;addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<T,ArcanaEntityModel<T>>(this){@Override public void render(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,T entity,float limb,float amount,float partial,float age,float yaw,float pitch){getParentModel().root.getChild("eye").render(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(ResourceLocation.parse("tnc:textures/block/alchemy_glow.png"))),0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,spirit?.65f:1,spirit?1:.2f,spirit?.9f:.45f,1);}});}
        @Override public ResourceLocation getTextureLocation(T e){return ResourceLocation.parse(spirit?"tnc:textures/block/alchemy_bronze.png":"tnc:textures/block/alchemy_ceramic.png");}
        @Override public boolean shouldRender(T e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return (!spirit||ArcanaClient.spiritVisible())&&super.shouldRender(e,f,x,y,z);}
        @Override protected void scale(T e,com.mojang.blaze3d.vertex.PoseStack pose,float partial){if(!spirit)pose.scale(1.9f,1.9f,1.9f);}
        @Override public void render(T e,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){if(spirit&&!ArcanaClient.spiritVisible())return;super.render(e,yaw,partial,pose,buffers,spirit?0xf000f0:light);if(spirit)DimensionalSpiritGeometry.render(pose,buffers,e.tickCount+partial);}
    }
}
