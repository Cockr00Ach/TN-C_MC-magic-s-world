package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.fire.TNFlameHandEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Five-digit articulated plasma hand, on ordinary entity targets in vanilla and Oculus. */
public final class FireHandRenderer extends EntityRenderer<TNFlameHandEntity> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    public FireHandRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TNFlameHandEntity entity){return WHITE;}
    @Override public void render(TNFlameHandEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        double age=Mth.clamp(entity.phase()*TNFlameHandEntity.LIFE+partial,0,TNFlameHandEntity.LIFE);
        double scale=FireHandPose.scale(entity.tier()),grip=entity.gripHeight(),rise=FireHandPose.emergence(age);
        float viewOpacity=FirstPersonSpellView.opacity(entity.visualCaster(),
            entity.getPosition(partial).add(0,grip+rise*scale,0),Math.min(2,scale));
        if(viewOpacity<=.002F)return;
        var quads=FireHandMesh.build(age,entity.heat(),entity.aerial(),entity.tier());
        pose.pushPose();
        try{
            pose.translate(0,grip+rise*scale,0);
            pose.scale((float)scale,(float)scale,(float)scale);
            for(var layer:FireHandMesh.Layer.values()){
                VertexConsumer out=buffers.getBuffer(layer==FireHandMesh.Layer.STRUCTURE?
                    RenderType.entityTranslucent(WHITE):RenderType.entityTranslucentEmissive(WHITE));
                for(var quad:quads)if(quad.layer()==layer)draw(out,pose.last(),quad,scale,grip+rise*scale,viewOpacity);
            }
        }finally{pose.popPose();}
    }
    private static void draw(VertexConsumer out,PoseStack.Pose pose,FireHandMesh.Quad q,double scale,double offset,float viewOpacity){
        var normal=q.b().sub(q.a()).cross(q.c().sub(q.a())).unit();
        // This non-culling translucent type already draws both sides. Do not double-submit every surface.
        vert(out,pose,q.a(),q.color(),normal,scale,offset,viewOpacity,.25F,.25F);
        vert(out,pose,q.b(),q.color(),normal,scale,offset,viewOpacity,.75F,.25F);
        vert(out,pose,q.c(),q.color(),normal,scale,offset,viewOpacity,.75F,.75F);
        vert(out,pose,q.d(),q.color(),normal,scale,offset,viewOpacity,.25F,.75F);
    }
    private static void vert(VertexConsumer out,PoseStack.Pose pose,FireHandPose.V p,FireHandMesh.Color color,FireHandPose.V normal,double scale,double offset,float viewOpacity,float u,float v){
        float ground=(float)FireHandPose.smooth((p.y()*scale+offset+.16)/.65);
        out.vertex(pose.pose(),(float)p.x(),(float)p.y(),(float)p.z()).color(color.r(),color.g(),color.b(),color.a()*ground*viewOpacity)
            .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
            .normal(pose.normal(),(float)normal.x(),(float)normal.y(),(float)normal.z()).endVertex();
    }
}
