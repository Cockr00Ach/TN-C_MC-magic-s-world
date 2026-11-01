package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.InkPortalEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

/** Openwork octagonal ink window with layered mountain silhouettes and suspended brush ribbons. */
public final class InkPortalRenderer extends EntityRenderer<InkPortalEntity> {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    public InkPortalRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public ResourceLocation getTextureLocation(InkPortalEntity e){return WHITE;}
    @Override public void render(InkPortalEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-yaw));pose.translate(0,2.6,0);
        double age=e.tickCount+partial;
        var out=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for(int layer=0;layer<3;layer++)for(int i=0;i<8;i++){
            double a=(i+.5)*Math.PI/4,b=(i+1.5)*Math.PI/4,r=2.5-layer*.12;
            InkCraneRitualVisuals.stroke(out,pose.last(),new Vec3(Math.cos(a)*r,Math.sin(a)*r,0),new Vec3(Math.cos(b)*r,Math.sin(b)*r,0),layer==0?.13:.025,layer==1?.85F:.02F,1);
        }
        for(int side:new int[]{-1,1})for(int i=0;i<3;i++){
            double y=-1.4+i*1.25;
            InkCraneRitualVisuals.stroke(out,pose.last(),new Vec3(side*2.05,y,0),new Vec3(side*1.55,y,0),.035,.05F,1);
            InkCraneRitualVisuals.stroke(out,pose.last(),new Vec3(side*1.55,y,0),new Vec3(side*1.55,y+.6,0),.035,.05F,1);
        }
        // Wash mountains drift within the window; no solid walls or generated structures.
        for(int layer=0;layer<3;layer++){
            var ridge=new ArrayList<Vec3>();
            for(int i=0;i<=40;i++){
                double x=-1.7+i*.085,y=-1.3+layer*.45+Math.pow(Math.sin(x*3+layer*1.7),2)*(.6+layer*.16);
                ridge.add(new Vec3(x,y,.03+layer*.02));
            }
            InkCraneRitualVisuals.brushTrail(ridge,Vec3.ZERO,age*.3,.16+layer*.025,pose,buffers);
        }
        for(int j=0;j<4;j++){
            var ribbon=new ArrayList<Vec3>();for(int i=0;i<28;i++){double a=j*Math.PI/2+i*.065+age*.008;ribbon.add(new Vec3(Math.cos(a)*2.4,Math.sin(a)*2.4,.12+Math.sin(a*3+age*.02)*.08));}
            InkCraneRitualVisuals.brushTrail(ribbon,Vec3.ZERO,age,.06,pose,buffers);
        }
        pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
    }
}
