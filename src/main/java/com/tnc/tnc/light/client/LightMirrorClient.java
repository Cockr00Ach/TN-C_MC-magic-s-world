package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.light.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class LightMirrorClient {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/beam_white.png");
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LightMirrorRegistration.MIRROR, MirrorRenderer::new);
        event.registerEntityRenderer(LightMirrorRegistration.ACOLYTE, AcolyteRenderer::new);
    }
    public static final class MirrorRenderer extends EntityRenderer<TNLightMirrorEntity> {
        private final List<List<LightPrismModels.Face>> models=java.util.stream.IntStream.rangeClosed(1,5).mapToObj(LightPrismModels::mirror).toList();
        public MirrorRenderer(EntityRendererProvider.Context c) { super(c); }
        @Override public ResourceLocation getTextureLocation(TNLightMirrorEntity e) { return WHITE; }
        @Override public void render(TNLightMirrorEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            pose.pushPose();pose.translate(0,1.3,0);pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            LightPrismModels.render(models.get(e.tier()-1),pose,buffers.getBuffer(RenderType.entitySolid(WHITE)),
                    e.flashing()?LightTexture.FULL_BRIGHT:light);
            pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
        }
    }
    public static final class AcolyteRenderer extends EntityRenderer<ApolloAcolyteEntity> {
        private final List<LightPrismModels.Face> body=LightPrismModels.acolyteBody(),head=LightPrismModels.acolyteHead(),
                arm=LightPrismModels.limb(true),leg=LightPrismModels.limb(false);
        public AcolyteRenderer(EntityRendererProvider.Context c) { super(c);shadowRadius=.45F; }
        @Override public ResourceLocation getTextureLocation(ApolloAcolyteEntity e) { return WHITE; }
        @Override public void render(ApolloAcolyteEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
            var out=buffers.getBuffer(RenderType.entitySolid(WHITE));
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot)));
            LightPrismModels.render(body,pose,out,light);
            pose.pushPose();pose.translate(0,1.62,0);
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(Mth.rotLerp(partial,e.yHeadRotO,e.yHeadRot)-Mth.rotLerp(partial,e.yBodyRotO,e.yBodyRot))));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial,e.xRotO,e.getXRot())));
            LightPrismModels.render(head,pose,out,light);pose.popPose();
            float walk=e.walkAnimation.position(partial),amount=e.walkAnimation.speed(partial);
            for(int side:new int[]{-1,1}) {
                pose.pushPose();pose.translate(side*.145,.38,0);pose.mulPose(Axis.XP.rotationDegrees((float)Math.cos(walk*.666)*amount*28*side));
                LightPrismModels.render(leg,pose,out,light);pose.popPose();
                pose.pushPose();pose.translate(side*.36,1.48,0);
                pose.mulPose(Axis.XP.rotationDegrees(-20+(float)Math.sin(e.getAttackAnim(partial)*Math.PI)*-65));
                LightPrismModels.render(arm,pose,out,light);pose.popPose();
            }
            pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
        }
    }
}
