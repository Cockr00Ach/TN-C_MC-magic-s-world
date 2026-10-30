package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Age-driven brush fragments. No particle entities, retained history, postprocess or world fog. */
final class InkWashVisuals {
    private static final ResourceLocation PAPER=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    private static final ResourceLocation BRUSH=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_brush_strip.png");
    private InkWashVisuals(){}
    private static int count(int full){var setting=Minecraft.getInstance().options.particles().get();return setting==ParticleStatus.MINIMAL?Math.max(4,full/3):setting==ParticleStatus.DECREASED?full*2/3:full;}
    static void gather(PoseStack pose,MultiBufferSource buffers,double age,Vec3 center,double radius,double reach){
        var out=buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
        for(int i=0;i<count(18);i++){
            double phase=(age*.018+i*.61803398875)%1;
            for(int j=0;j<9;j++){
                double t=Math.max(0,phase-j*.026),u=Math.max(0,t-.026);
                Vec3 a=inward(center,radius,reach,i,t),b=inward(center,radius,reach,i,u);
                float fade=(float)(Math.sin(phase*Math.PI)*(1-j/10.0));
                InkCraneRitualVisuals.stroke(out,pose.last(),a,b,(.07+i%3*.04)*(1-j/11.0),.008F,fade*.85F);
                if(j==3&&i%3==0)InkCraneRitualVisuals.stroke(out,pose.last(),a,b,.018,.82F,fade*.6F);
            }
            double t=phase,r=radius+(1-t)*(reach-radius);
            Vec3 p=inward(center,radius,reach,i,t);
            fleck(pose,buffers,p,Math.max(.07,r*.012),i,age,.012F,(float)Math.sin(t*Math.PI)*.65F);
        }
    }
    private static Vec3 inward(Vec3 center,double core,double reach,int seed,double t){
        double r=core+(1-t)*(Math.max(core,reach)-core),a=seed*2.399963+(1-t)*1.5;
        return center.add(Math.cos(a)*r,Math.sin(seed*1.7)*(r-core)*.32,Math.sin(a)*r);
    }
    static void burst(PoseStack pose,MultiBufferSource buffers,double age,double radius){
        if(age<0||age>=36)return;
        double t=age/36,spread=radius*(1-Math.pow(1-t,3));
        wash(pose,buffers,age,(float)((1-t)*.75),spread);
        for(int i=0;i<count(24);i++){
            double a=i*2.399963,r=spread*(.35+(i%7)*.105);
            double h=Math.sin(t*Math.PI)*(1+i%4)*radius*.08;
            fleck(pose,buffers,new Vec3(Math.cos(a)*r,.1+h,Math.sin(a)*r),radius*(.018+i%3*.01)*(1-t),i,age,i%6==0?.82F:.012F,(float)(1-t)*.85F);
        }
    }
    static void wash(PoseStack pose,MultiBufferSource buffers,double age,float alpha,double radius){
        if(alpha<=0||radius<=0)return;
        var out=buffers.getBuffer(RenderType.entityTranslucent(PAPER));
        // Dilute outer capillary edge, medium wash, then an offset concentrated heart. 144 quads maximum.
        for(int layer=0;layer<3;layer++){
            double r=radius*(1-layer*.19),cx=Math.sin(layer*3.1)*radius*.08,cz=Math.cos(layer*2.7)*radius*.07;
            Vec3 center=new Vec3(cx,.065+layer*.008,cz);
            for(int j=0;j<24;j++){
                double a=j*Math.PI/12,b=(j+1)*Math.PI/12;
                Vec3 p=edge(center,a,r),q=edge(center,b,r);
                InkCraneRitualVisuals.quad(out,pose.last(),center,p,q,center,.018F,alpha*(layer==0?.08F:layer==1?.13F:.24F));
            }
        }
        for(int i=0;i<count(12);i++){
            double a=i*2.399963,r=radius*(.8+i%4*.08),s=radius*(.014+i%3*.012);
            Vec3 c=new Vec3(Math.cos(a)*r,.095+i*.0003,Math.sin(a)*r);
            for(int j=0;j<6;j++)InkCraneRitualVisuals.quad(out,pose.last(),c,edge(c,j*Math.PI/3,s),edge(c,(j+1)*Math.PI/3,s),c,.012F,alpha*.4F);
        }
    }
    private static Vec3 edge(Vec3 center,double a,double r){r*=1+.14*Math.sin(a*5)+.08*Math.sin(a*11+1.3);return center.add(Math.cos(a)*r,0,Math.sin(a)*r);}
    static void fleck(PoseStack pose,MultiBufferSource buffers,Vec3 at,double size,int seed,double age,float gray,float alpha){
        if(size<=0||alpha<=0)return;
        pose.pushPose();try{
            pose.translate(at.x,at.y,at.z);pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            pose.mulPose(Axis.ZP.rotationDegrees((float)(seed*137.5+age*(seed%2==0?1:-1))));
            var out=buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
            InkCraneRitualVisuals.quad(out,pose.last(),new Vec3(-size,-size*.2,0),new Vec3(size*.7,-size*.38,0),new Vec3(size,size*.15,0),new Vec3(-size*.4,size*.55,0),gray,alpha);
        }finally{pose.popPose();}
    }
}
