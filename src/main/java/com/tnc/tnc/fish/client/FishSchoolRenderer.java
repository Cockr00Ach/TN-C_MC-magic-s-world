package com.tnc.tnc.fish.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.fish.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Depth-tested ink tubes and faint body planes. No solid fish model or model-layer baking. */
public final class FishSchoolRenderer extends EntityRenderer<FishSchoolEntity> {
    private static final ResourceLocation INK=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/fish_school_line.png");
    public FishSchoolRenderer(EntityRendererProvider.Context context) { super(context);shadowRadius=0; }
    @Override public void render(FishSchoolEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double age=Math.min(FishSchoolGeometry.LIFETIME,entity.age()+partial);
        var vertices=buffers.getBuffer(RenderType.entityTranslucentEmissive(INK));
        float alpha=(float)Math.min(1,Math.max(0,age/4));
        for (int lane=0;lane<FishSchoolGeometry.COUNT;lane++) {
            if (!entity.active(lane) || !entity.visuallyClear(lane,age)) continue;
            var offset=entity.fish(lane,age).subtract(entity.position());
            pose.pushPose();pose.translate(offset.x,offset.y,offset.z);
            pose.mulPose(Axis.YP.rotationDegrees(-entity.facingYaw()));pose.mulPose(Axis.XP.rotationDegrees(entity.facingPitch()));
            drawFish(pose.last(),vertices,age,lane,entity.metal(),alpha);pose.popPose();
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    private static Vec3 point(FishSchoolLineArt.Point p,double age,int lane,boolean metal) {
        var v=FishSchoolLineArt.animate(p,age,lane,metal);return new Vec3(v.x(),v.y(),v.z());
    }
    private static void drawFish(PoseStack.Pose pose,VertexConsumer v,double age,int lane,boolean metal,float alpha) {
        for (var t:FishSchoolLineArt.FILL) {
            Vec3 a=point(t.a(),age,lane,metal),b=point(t.b(),age,lane,metal),c=point(t.c(),age,lane,metal);
            quad(pose,v,a,b,c,c,metal?126:38,metal?179:166,metal?205:177,(int)(35*alpha),false);
            quad(pose,v,c,b,a,a,metal?126:38,metal?179:166,metal?205:177,(int)(35*alpha),false);
        }
        for (var s:FishSchoolLineArt.STROKES) for (int i=1;i<s.points().size();i++) {
            Vec3 a=point(s.points().get(i-1),age,lane,metal),b=point(s.points().get(i),age,lane,metal);
            boolean gold=metal&&s.accent();
            tube(pose,v,a,b,.0055,gold?246:metal?180:83,gold?202:metal?222:230,gold?112:metal?244:213,(int)(245*alpha));
        }
    }
    private static void tube(PoseStack.Pose pose,VertexConsumer v,Vec3 a,Vec3 b,double radius,int r,int g,int blue,int alpha) {
        Vec3 axis=b.subtract(a).normalize();
        Vec3 u=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(radius);
        Vec3 n=axis.cross(u).normalize().scale(radius);Vec3[] ring={u,n,u.scale(-1),n.scale(-1)};
        for (int i=0;i<4;i++) {
            Vec3 x=ring[i],y=ring[(i+1)%4];
            quad(pose,v,a.add(x),a.add(y),b.add(y),b.add(x),r,g,blue,alpha,true);
        }
    }
    private static void quad(PoseStack.Pose pose,VertexConsumer v,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int r,int g,int blue,int alpha,boolean ink) {
        vertex(pose,v,a,r,g,blue,alpha,ink?0:.5f);vertex(pose,v,b,r,g,blue,alpha,ink?1:.5f);
        vertex(pose,v,c,r,g,blue,alpha,ink?1:.5f);vertex(pose,v,d,r,g,blue,alpha,ink?0:.5f);
    }
    private static void vertex(PoseStack.Pose pose,VertexConsumer v,Vec3 p,int r,int g,int blue,int alpha,float u) {
        v.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,blue,alpha).uv(u,.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0,1,0).endVertex();
    }
    @Override public ResourceLocation getTextureLocation(FishSchoolEntity entity) { return INK; }
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(FishSwords.school(),FishSchoolRenderer::new); }
    }
}
