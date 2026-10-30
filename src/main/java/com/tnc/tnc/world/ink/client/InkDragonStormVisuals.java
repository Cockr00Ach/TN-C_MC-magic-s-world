package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

final class InkDragonStormVisuals {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    static void render(InkCelestialDragonEntity dragon,float partial,PoseStack pose,MultiBufferSource buffers){
        double age=dragon.moveAge()+partial;
        if(dragon.inkMove()==4)return;
        if(dragon.inkMove()==3){
            for(int i=0;i<InkDragonStormRules.PILLARS;i++){
                double elapsed=age-InkDragonStormRules.hitTick(i);if(elapsed>25)continue;
                Vec3 p=dragon.effectVector("pillar"+i).subtract(dragon.position());pose.pushPose();pose.translate(p.x,p.y+.08,p.z);
                var out=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
                InkCraneRitualVisuals.ring(out,pose.last(),InkDragonStormRules.PILLAR_RADIUS,.16,.03,age*.03,.04F,.9F,false);
                InkCraneRitualVisuals.ring(out,pose.last(),InkDragonStormRules.PILLAR_RADIUS+.2,.05,.04,0,.9F,.8F,false);
                if(elapsed>=0){
                    float fade=(float)(1-elapsed/25);
                    for(int row=0;row<24;row++)for(int side=0;side<32;side++){
                        double y=row*3.2,next=(row+1)*3.2,a=side*Math.PI/16,b=(side+1)*Math.PI/16;
                        double r=5.7+Math.sin(y*.18+age*.2)*.7,s=5.7+Math.sin(next*.18+age*.2)*.7;
                        InkCraneRitualVisuals.quad(out,pose.last(),new Vec3(Math.cos(a)*r,y,Math.sin(a)*r),new Vec3(Math.cos(b)*r,y,Math.sin(b)*r),
                                new Vec3(Math.cos(b)*s,next,Math.sin(b)*s),new Vec3(Math.cos(a)*s,next,Math.sin(a)*s),.018F,.88F*fade);
                    }
                    for(int j=0;j<6;j++){
                        var ribbon=new ArrayList<Vec3>();
                        for(int k=0;k<=24;k++){double y=k*3.2,a=j*Math.PI/3+y*.06+age*.03;ribbon.add(new Vec3(Math.cos(a)*(6+y*.008),y,Math.sin(a)*(6+y*.008)));}
                        InkCraneRitualVisuals.brushTrail(ribbon,Vec3.ZERO,age,.9*fade,pose,buffers);
                    }
                    InkDragonBreathVisuals.impact(elapsed,pose,buffers);
                }
                pose.popPose();
            }
        }else{
            Vec3 center=dragon.effectVector("point").subtract(dragon.position());pose.pushPose();pose.translate(center.x,center.y+.12,center.z);
            double r=age<45?6:InkDragonStormRules.ringRadius(age);
            // The same angular gap used by server damage is left visibly open in every ribbon.
            for(int layer=0;layer<4;layer++){
                var path=new ArrayList<Vec3>();
                for(int j=0;j<=100;j++){
                    double a=InkDragonStormRules.GAP+InkDragonStormRules.GAP_HALF_WIDTH+j/100.0*(Math.PI*2-2*InkDragonStormRules.GAP_HALF_WIDTH);
                    path.add(new Vec3(Math.cos(a)*r,layer*.55+Math.sin(a*9+age*.16)*.13,Math.sin(a)*r));
                }
                InkCraneRitualVisuals.brushTrail(path,Vec3.ZERO,age,age<45?.08:.35,pose,buffers);
            }
            // Pale escape corridor telegraphs which way the ring can be crossed safely.
            if(age<60){var out=buffers.getBuffer(RenderType.entityTranslucent(WHITE));for(double side:new double[]{-1,1}){
                double a=InkDragonStormRules.GAP+side*InkDragonStormRules.GAP_HALF_WIDTH;
                InkCraneRitualVisuals.stroke(out,pose.last(),new Vec3(Math.cos(a)*4,.1,Math.sin(a)*4),new Vec3(Math.cos(a)*30,.1,Math.sin(a)*30),.10,.95F,.8F);
            }}
            pose.popPose();
        }
    }
}
