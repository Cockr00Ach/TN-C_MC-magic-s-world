package com.tnc.tnc.dungeon.mirror.client;

import com.mojang.blaze3d.vertex.*;
import com.tnc.tnc.dungeon.mirror.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MirrorHookRenderer extends EntityRenderer<MirrorHook>{
    public MirrorHookRenderer(EntityRendererProvider.Context context){super(context);}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(MirrorHook.type(),MirrorHookRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(MirrorHook entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(MirrorHook hook,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var m=hook.caster();if(m==null)return;
        Vec3 start=MirrorHook.tip(m,m.getPosition(partial),Mth.rotLerp(partial,m.yBodyRotO,m.yBodyRot)).subtract(hook.getPosition(partial));
        var vertices=buffers.getBuffer(RenderType.lines());var matrix=pose.last().pose();
        Vec3 prev=Vec3.ZERO;
        for(int i=1;i<=18;i++){
            double f=i/18.0;double sag=Math.min(.4,start.length()*.035)*4*f*(1-f);
            var next=new Vec3(f*start.x,f*start.y-sag,f*start.z);
            var n=next.subtract(prev).normalize();
            line(vertices,pose.last(),prev,n);line(vertices,pose.last(),next,n);prev=next;
        }
        // A short two-colour bobber silhouette remains legible against the black pool.
        pose.pushPose();pose.mulPose(entityRenderDispatcher.cameraOrientation());matrix=pose.last().pose();
        var bob=buffers.getBuffer(RenderType.lightning());
        for(int j=0;j<2;j++){
            float y=j*.065f-.06f;int red=j<1?90:213,green=j<1?180:111;
            bob.vertex(matrix,-.05f,y,0).color(red,green,112,255).endVertex();
            bob.vertex(matrix,.05f,y,0).color(red,green,112,255).endVertex();
            bob.vertex(matrix,.05f,y+.065f,0).color(red,green,112,255).endVertex();
            bob.vertex(matrix,-.05f,y+.065f,0).color(red,green,112,255).endVertex();
        }
        pose.popPose();
    }
    private static void line(VertexConsumer v,PoseStack.Pose pose,Vec3 pos,Vec3 n){
        v.vertex(pose.pose(),(float)pos.x,(float)pos.y,(float)pos.z).color(166,201,208,255)
                .normal(pose.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
