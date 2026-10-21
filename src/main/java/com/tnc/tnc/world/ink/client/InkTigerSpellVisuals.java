package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Angular Chinese window tracery and bounded watercolor meshes, never circular spell seals. */
final class InkTigerSpellVisuals {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    private static final ResourceLocation BRUSH=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_brush_strip.png");
    private InkTigerSpellVisuals(){}
    static void window(PoseStack pose,MultiBufferSource buffers,double radius,double age,float alpha){
        var out=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        double[][] points={{-1,-.43},{-.43,-1},{.43,-1},{1,-.43},{1,.43},{.43,1},{-.43,1},{-1,.43}};
        for(int layer=0;layer<3;layer++){
            double r=radius*(1-layer*.065),w=radius*(layer==0?.035:.012);
            for(int i=0;i<8;i++){
                var a=points[i];var b=points[(i+1)%8];
                InkCraneRitualVisuals.stroke(out,pose.last(),new Vec3(a[0]*r,.06+layer*.006,a[1]*r),new Vec3(b[0]*r,.06+layer*.006,b[1]*r),w,layer==1?.75F:.015F,alpha);
            }
        }
        // Mirrored rectangular key-fret corners. The centre remains open, as in the reference.
        for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
            double[][] key={{.38,.86},{.38,.38},{.86,.38},{.86,.56},{.54,.56},{.54,.73},{.7,.73},{.7,.64}};
            for(int i=1;i<key.length;i++){
                var a=key[i-1];var b=key[i];
                InkCraneRitualVisuals.stroke(out,pose.last(),new Vec3(sx*a[0]*radius,.082,sz*a[1]*radius),new Vec3(sx*b[0]*radius,.082,sz*b[1]*radius),radius*.014,.02F,alpha);
            }
        }
        for(int side:new int[]{-1,1}){
            pose.pushPose();pose.translate(side*radius*.73,0,side*radius*.62);
            wash(pose,buffers,age,alpha*.6F,radius*.4);pose.popPose();
        }
    }
    static void caster(InkWingedTigerEntity tiger,float partial,PoseStack pose,MultiBufferSource buffers){
        if(tiger.inkMove()==0)return;
        double age=tiger.moveAge()+partial;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-tiger.getYRot()));pose.translate(0,4.8*InkTigerAttackRules.BODY_MULTIPLIER,-3*InkTigerAttackRules.BODY_MULTIPLIER);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        window(pose,buffers,5.5*InkTigerAttackRules.BODY_MULTIPLIER,age,(float)Math.min(1,age/15));pose.popPose();
        if(tiger.inkMove()==1&&age>=25&&age<65){
            Vec3 mouth=tiger.mouthPosition(false).subtract(tiger.position());
            gather(pose,buffers,age-25,mouth,Math.min(1,(age-25)/40.0)*1.4,9);
        }
        if(tiger.inkMove()==3&&age>=20&&age<90){
            Vec3 mouth=tiger.mouthPosition(true).subtract(tiger.position());
            gather(pose,buffers,age,mouth,InkTigerAttackRules.roarBallRadius(age),18);
        }
        if(tiger.inkMove()==3&&age>=InkTigerAttackRules.SPIN&&age<InkTigerAttackRules.SPIN_END){
            for(int side=0;side<2;side++){
                List<Vec3> arc=new ArrayList<>();double angle=Math.toRadians(tiger.getYRot())+side*Math.PI;
                for(int i=0;i<22;i++){double a=angle-i*.045;arc.add(tiger.position().add(Math.cos(a)*13,2.8*InkTigerAttackRules.BODY_MULTIPLIER+i*.025,Math.sin(a)*13));}
                InkCraneRitualVisuals.brushTrail(arc,tiger.position(),age,1.1,pose,buffers);
            }
        }
    }
    static void effect(InkTigerEffectEntity e,float partial,PoseStack pose,MultiBufferSource buffers){
        double age=e.age()+partial;
        if(e.mode()==InkTigerEffectEntity.STAIN){wash(pose,buffers,age,(float)Math.min(1,(60-age)/20),6);return;}
        if(e.mode()==InkTigerEffectEntity.BURST){burst(pose,buffers,age,18);return;}
        if(e.mode()==InkTigerEffectEntity.GHOST){
            InkCraneRitualVisuals.brushTrail(e.trail(),e.position(),age,4.5*InkTigerAttackRules.BODY_MULTIPLIER,pose,buffers);
            if(age<22)window(pose,buffers,8*InkTigerAttackRules.BODY_MULTIPLIER,age,(float)Math.min(1,age/8));return;
        }
        if(e.mode()==InkTigerEffectEntity.SHOT){
            gather(pose,buffers,age,Vec3.ZERO,1.4,3);
            InkCraneRitualVisuals.brushTrail(e.trail(),e.position(),age,1.6,pose,buffers);return;
        }
        if(age<100){
            pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees((float)age*.65F));
            window(pose,buffers,11,age,1);pose.popPose();
            double radius=InkCraneAttackRules.ballRadius(age);
            gather(pose,buffers,age,new Vec3(0,radius+.4,0),radius,22);
        }else{
            InkPaintingRenderer.aftermath(age,pose,buffers);
            if(age<118)burst(pose,buffers,(age-100)*2,24);
        }
    }
    static void gather(PoseStack pose,MultiBufferSource buffers,double age,Vec3 center,double radius,double reach){
        var ink=buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
        for(int i=0;i<28;i++){
            List<Vec3> path=new ArrayList<>();double phase=(age*.024+i*.173)%1;
            for(int j=0;j<13;j++){
                double t=Math.min(1,phase+j*.022),r=(1-t)*reach,a=i*2.39996+t*1.7;
                path.add(center.add(Math.cos(a)*r,Math.sin(i*1.4)*r*.4,Math.sin(a)*r));
            }
            for(int j=1;j<path.size();j++)InkCraneRitualVisuals.stroke(ink,pose.last(),path.get(j-1),path.get(j),.06+(i%4)*.045,.01F,.55F);
        }
        if(radius>.06){pose.pushPose();pose.translate(center.x,center.y,center.z);
            InkCraneRitualVisuals.sphere(buffers.getBuffer(RenderType.entityTranslucent(WHITE)),pose.last(),radius,0,age,1);pose.popPose();}
    }
    static void burst(PoseStack pose,MultiBufferSource buffers,double age,double radius){
        float fade=(float)Math.max(0,1-age/36);
        InkCraneRitualVisuals.bloom(buffers.getBuffer(RenderType.entityTranslucent(BRUSH)),pose.last(),age,radius,fade);
        wash(pose,buffers,age,fade,radius*Math.min(1,age/12));
    }
    static void wash(PoseStack pose,MultiBufferSource buffers,double age,float alpha,double radius){
        var out=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        double spread=Math.min(1,Math.max(.04,age/16));
        for(int layer=0;layer<12;layer++){
            Vec3 center=new Vec3(Math.sin(layer*.32)*radius*.16,.035+layer*.003,Math.cos(layer*.23)*radius*.12);
            for(int i=0;i<64;i++){
                double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
                double r=radius*spread*(1-layer*.052)*(1+.15*Math.sin(a*3+.5)+.08*Math.sin(a*7+1.7)+.025*Math.cos(a*13));
                double s=radius*spread*(1-layer*.052)*(1+.15*Math.sin(b*3+.5)+.08*Math.sin(b*7+1.7)+.025*Math.cos(b*13));
                InkCraneRitualVisuals.quad(out,pose.last(),center,center.add(Math.cos(a)*r,0,Math.sin(a)*r),
                        center.add(Math.cos(b)*s,0,Math.sin(b)*s),center,.015F,alpha*(.035F+layer*.008F));
            }
        }
        // Small rounded satellites and dilute halos make a bleeding wash rather than radial spokes.
        for(int drop=0;drop<38;drop++){
            double angle=drop*2.39996,d=radius*spread*(.5+(drop%7)*.105),r=radius*(.012+(drop%5)*.012);
            Vec3 center=new Vec3(Math.cos(angle)*d,.076+drop*.0001,Math.sin(angle)*d);
            for(int i=0;i<10;i++){
                double a=i*Math.PI/5,b=(i+1)*Math.PI/5;
                InkCraneRitualVisuals.quad(out,pose.last(),center,center.add(Math.cos(a)*r,0,Math.sin(a)*r),center.add(Math.cos(b)*r,0,Math.sin(b)*r),center,.012F,alpha*.25F);
            }
        }
    }
}
