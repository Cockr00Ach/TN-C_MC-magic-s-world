package com.tnc.tnc.fish.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.fish.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class FishSchoolRenderer extends EntityRenderer<FishSchoolEntity> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("tnc","fish_school"),"main");
    private static final ResourceLocation LIVING=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/fish_school_living.png");
    private static final ResourceLocation METAL=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/fish_school_metal.png");
    private final FishSchoolModel model;
    public FishSchoolRenderer(EntityRendererProvider.Context context) { super(context);model=new FishSchoolModel(context.bakeLayer(LAYER));shadowRadius=0; }
    @Override public void render(FishSchoolEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double age=Math.min(FishSchoolGeometry.LIFETIME,entity.age()+partial);
        var vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        for (int lane=0;lane<FishSchoolGeometry.COUNT;lane++) {
            if (!entity.active(lane) || !entity.visuallyClear(lane,age)) continue;
            var offset=entity.fish(lane,age).subtract(entity.position());
            pose.pushPose();pose.translate(offset.x,offset.y,offset.z);
            pose.mulPose(Axis.YP.rotationDegrees(-entity.facingYaw()));
            pose.mulPose(Axis.XP.rotationDegrees(entity.facingPitch()));
            pose.scale(.7f,-.7f,.7f);
            model.render(pose,vertices,light,OverlayTexture.NO_OVERLAY,age,lane,entity.metal());
            pose.popPose();
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(FishSchoolEntity entity) { return entity.metal()?METAL:LIVING; }
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) { event.registerLayerDefinition(LAYER,FishSchoolModel::layer); }
        @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(FishSwords.school(),FishSchoolRenderer::new); }
    }
}
