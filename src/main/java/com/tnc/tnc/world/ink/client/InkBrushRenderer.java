package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.io.*;
import java.util.*;

public final class InkBrushRenderer extends EntityRenderer<InkBrushBossEntity> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    private static final ResourceLocation MODEL=ResourceLocation.fromNamespaceAndPath("tnc","meshes/ink_brush.mesh");
    private final float[] mesh;
    public InkBrushRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.7F;
        try(var in=new DataInputStream(Minecraft.getInstance().getResourceManager().open(MODEL))){
            if(in.readInt()!=0x49425231)throw new IOException("Invalid brush mesh");int n=in.readInt();if(n<3||n>90000||n%3!=0)throw new IOException("Brush exceeds vertex budget");mesh=new float[n*6];
            for(int i=0;i<n;i++){for(int k=0;k<3;k++){float v=in.readFloat();if(!Float.isFinite(v)||Math.abs(v)>16)throw new IOException("Invalid brush vertex");mesh[i*6+k]=v;}for(int k=3;k<6;k++)mesh[i*6+k]=in.readUnsignedByte()/255F;}
            if(in.read()!=-1)throw new IOException("Unexpected mesh data");
        }catch(IOException e){throw new IllegalStateException("Cannot load ink brush",e);}
    }
    @Override public ResourceLocation getTextureLocation(InkBrushBossEntity entity){return WHITE;}
    @Override public void render(InkBrushBossEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var path=new ArrayList<Vec3>();entity.inkTrail().forEach(path::add);
        if(path.size()>1){
            var ink=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
            for(int i=1;i<path.size();i++){
                Vec3 a=path.get(i-1).subtract(entity.position()),b=path.get(i).subtract(entity.position());
                if(a.distanceToSqr(b)>9)continue;
                float fade=1-i/(float)path.size();double pressure=1.1+.35*Math.sin((entity.tickCount-i*2)*.12);
                InkCraneRitualVisuals.stroke(ink,pose.last(),a,b,pressure*1.35,.06F,fade*.14F);
                InkCraneRitualVisuals.stroke(ink,pose.last(),a.add(0,.008,0),b.add(0,.008,0),pressure,.008F,fade*.88F);
                if(i%3!=0)InkCraneRitualVisuals.stroke(ink,pose.last(),a.add(.18,.014,.12),b.add(.16,.014,.12),pressure*.025,.75F,fade*.5F);
            }
        }
        pose.pushPose();try{
            pose.mulPose(Axis.YP.rotationDegrees(180-yaw));
            double moving=entity.getDeltaMovement().horizontalDistance();
            pose.mulPose(Axis.ZP.rotationDegrees((float)(Math.min(14,moving*60)+Math.sin((entity.tickCount+partial)*.045)*2)));
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));var p=pose.last();
            for(int i=0;i<mesh.length;i+=18){
                Vec3 a=point(i),b=point(i+6),c=point(i+12),normal=b.subtract(a).cross(c.subtract(a)).normalize();
                for(int corner=0;corner<4;corner++){
                    int j=Math.min(2,corner)*6;
                    int k=i+j;out.vertex(p.pose(),mesh[k],mesh[k+1],mesh[k+2]).color(mesh[k+3],mesh[k+4],mesh[k+5],1F).uv(j==0?0:1,j>=12?1:0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
                }
            }
        }finally{pose.popPose();}
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    private Vec3 point(int i){return new Vec3(mesh[i],mesh[i+1],mesh[i+2]);}
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(InkBrushRegistration.BRUSH,InkBrushRenderer::new);}
    }
}
