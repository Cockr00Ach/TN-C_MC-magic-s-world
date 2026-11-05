package com.tnc.tnc.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.bronze.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class BronzePuppetRenderer extends HumanoidMobRenderer<BronzePuppetEntity,BronzePuppetModel> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/bronze_puppet.png");
    private static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("tnc","bronze_puppet"),"main");
    public BronzePuppetRenderer(EntityRendererProvider.Context context){super(context,new BronzePuppetModel(context.bakeLayer(LAYER)),.55f);addLayer(new BronzeTwinLayer<>(this));}
    @Override public ResourceLocation getTextureLocation(BronzePuppetEntity entity){return TEXTURE;}
    @Override protected RenderType getRenderType(BronzePuppetEntity entity,boolean visible,boolean translucent,boolean glow){return RenderType.entityTranslucent(TEXTURE);}
    @Override protected float getFlipDegrees(BronzePuppetEntity entity){return 0;}
    @Override protected void scale(BronzePuppetEntity entity,PoseStack pose,float partial){pose.scale(1.04f,1.04f,1.04f);}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration{
        @SubscribeEvent public static void layer(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LAYER,BronzePuppetModel::layer);}
        @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(BronzeWeapons.PUPPET.get(),BronzePuppetRenderer::new);}
    }
}
