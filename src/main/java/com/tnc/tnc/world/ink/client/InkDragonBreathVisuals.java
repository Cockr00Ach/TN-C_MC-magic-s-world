package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Reusable dragon-local breath mesh. Preview only until the visual is approved.
 * Bounded geometry, no persistent particles or world mutation. Axis is local -X. */
final class InkDragonBreathVisuals {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    private static final ResourceLocation BRUSH=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_brush_strip.png");
    static final Vec3 MOUTH=com.tnc.tnc.world.ink.InkDragonFlightRules.MOUTH;
    private InkDragonBreathVisuals(){}

    /** Mouth and collision point are in the caller's coordinates; impact normal comes from raycast. */
    static void aimed(double age,Vec3 mouth,Vec3 hit,Vec3 normal,PoseStack pose,MultiBufferSource buffers){
        aimed(age,mouth,hit,normal,true,pose,buffers);
    }
    static void aimed(double age,Vec3 mouth,Vec3 hit,Vec3 normal,boolean collision,PoseStack pose,MultiBufferSource buffers){
        Vec3 delta=hit.subtract(mouth);double distance=delta.length();
        if(distance<.01||distance>49)return;
        pose.pushPose();
        try{
            pose.translate(mouth.x,mouth.y,mouth.z);
            pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(-1,0,0),delta.normalize().toVector3f()));
            pose.translate(-MOUTH.x,-MOUTH.y,-MOUTH.z);
            jet(age,distance,collision,pose,buffers);
        }finally{pose.popPose();}
        if(!collision)return;
        pose.pushPose();
        try{
            pose.translate(hit.x,hit.y,hit.z);
            pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,1,0),normal.normalize().toVector3f()));
            impact(age,pose,buffers);
        }finally{pose.popPose();}
    }

    static void render(double age,double length,PoseStack pose,MultiBufferSource buffers){
        jet(age,length,false,pose,buffers);
    }
    private static void jet(double age,double length,boolean collision,PoseStack pose,MultiBufferSource buffers){
        length=Math.max(0,Math.min(48,length));if(length<.01)return;
        var solid=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        // Continuous turbulent core; the nozzle remains narrow at the lips.
        for(int i=0;i<80;i++)for(int j=0;j<20;j++){
            double u=i/80.0,v=(i+1)/80.0,a=j*Math.PI/10,b=(j+1)*Math.PI/10;
            float gray=(float)(.009+.032*(.5+.5*Math.sin(i*.41+j*.67-age*.19)));
            InkCraneRitualVisuals.quad(solid,pose.last(),surface(u,a,age,length,1),surface(v,a,age,length,1),
                    surface(v,b,age,length,1),surface(u,b,age,length,1),gray,.98F);
        }
        var brush=buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
        // Ragged overlapping sheets and broken fly-white veins, advected outward.
        for(int strand=0;strand<32;strand++)for(int i=0;i<64;i++){
            double u=i/64.0,v=(i+1)/64.0;
            double a=strand*2.399963+u*9-age*.075;
            double spread=strand<12?1.02:1.15+.22*Math.sin(strand*3.7+u*11-age*.14);
            double w=strand<12?.018:.10+.04*Math.sin(i*.3+strand);
            float gray=strand<8?.64F:strand<12?.15F:.012F;
            if(strand<12&&(i+strand*3)%17>11)continue;
            InkCraneRitualVisuals.quad(brush,pose.last(),surface(u,a-w,age,length,spread),surface(v,a-w,age,length,spread),
                    surface(v,a+w,age,length,spread),surface(u,a+w,age,length,spread),gray,strand<12?.48F:.6F);
        }
        // Fast thin splinters, rather than a smoke cloud masking the dragon.
        for(int i=0;i<100;i++){
            double u=(i*.61803398875+age*.019)%1;
            double a=i*2.399963+age*.035;
            Vec3 p=surface(u,a,age,length,1.4+.65*Math.sin(i*7.1));
            Vec3 q=p.add(-.25-(i%7)*.14,Math.sin(a)*.13,Math.cos(a)*.13);
            InkCraneRitualVisuals.stroke(brush,pose.last(),p,q,.025+(i%4)*.015,.015F,(float)(.7*Math.sin(u*Math.PI)));
        }
        // End fan: streaks carry the jet forward, no sphere-shaped muzzle cap.
        for(int i=0;i<(collision?0:36);i++){
            double a=i*2.399963,r=.4+(i%6)*.38;
            Vec3 p=surface(.96,a,age,length,1);
            Vec3 q=MOUTH.add(-length-1.1-(i%5)*.37,Math.sin(a)*r,Math.cos(a)*r);
            InkCraneRitualVisuals.stroke(brush,pose.last(),p,q,.08+(i%4)*.035,.008F,.7F);
        }
    }
    /** Radial watercolor wash + rising, overturning ink sheets; all motion is age driven. */
    static void impact(double age,PoseStack pose,MultiBufferSource buffers){
        var wash=buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        // Uneven translucent wash spreads across the hit surface, not a geometric magic circle.
        for(int ring=0;ring<12;ring++)for(int j=0;j<96;j++){
            double a=j*Math.PI/48,b=(j+1)*Math.PI/48;
            double r=ring*.72,s=(ring+1)*.72;
            float gray=ring<4?.008F:.06F;
            float alpha=(float)(.64*Math.pow(1-ring/12.0,1.6));
            InkCraneRitualVisuals.quad(wash,pose.last(),wash(a,r,age),wash(b,r,age),wash(b,s,age),wash(a,s,age),gray,alpha);
        }
        var brush=buffers.getBuffer(RenderType.entityTranslucent(BRUSH));
        // Pressure-driven fans rise at the impact point, curl, then flatten outward.
        for(int plume=0;plume<52;plume++)for(int i=0;i<26;i++){
            double t=i/26.0,u=(i+1)/26.0,a=plume*2.399963;
            double w=(.10+.32*Math.sin(t*Math.PI))*(1+(plume%4)*.23);
            Vec3 p=plume(a,t,age,plume),q=plume(a,u,age,plume);
            Vec3 side=new Vec3(-Math.sin(a),0,Math.cos(a)).scale(w);
            float gray=plume%7==0?.30F:plume%3==0?.065F:.009F;
            float alpha=(float)(.85*Math.pow(1-t,.55));
            InkCraneRitualVisuals.quad(brush,pose.last(),p.add(side),q.add(side),q.subtract(side),p.subtract(side),gray,alpha);
        }
        // Rolling low ink mist separates into long ragged tongues instead of a solid black disc.
        for(int k=0;k<30;k++)for(int i=0;i<20;i++){
            double t=i/20.0,u=(i+1)/20.0,a=k*2.399963;
            double wave=(age*.035+k*.173)%1;
            double r=1.2+t*(6+wave*3),s=1.2+u*(6+wave*3);
            double h=.22+Math.sin(t*Math.PI)*(.5+(k%4)*.22);
            double w=.10+.065*Math.sin(t*Math.PI);
            InkCraneRitualVisuals.quad(brush,pose.last(),radial(a-w,r,h),radial(a-w,s,h),radial(a+w,s,h+.12),radial(a+w,r,h+.12),
                    k%4==0?.22F:.018F,(float)(.55*(1-t)));
        }
        for(int i=0;i<110;i++){
            double t=(age*.023+i*.61803398875)%1,a=i*2.399963,r=1+t*(5+i%6);
            Vec3 p=radial(a,r,.18+Math.sin(t*Math.PI)*(1+i%4));
            InkCraneRitualVisuals.stroke(brush,pose.last(),p,p.add(Math.cos(a)*.55,-.15,Math.sin(a)*.55),.025+(i%3)*.025,.012F,(float)(.8*(1-t)));
        }
    }
    private static Vec3 radial(double a,double r,double y){return new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);}
    private static Vec3 wash(double a,double r,double age){
        r*=1+.12*Math.sin(a*7)+.08*Math.sin(a*13+age*.025);
        return radial(a,r,.045);
    }
    private static Vec3 plume(double a,double t,double age,int index){
        double travel=(age*.025+index*.137)%1;
        double r=.25+t*(4.5+(index%7)*.45+travel);
        double h=.15+Math.sin(Math.pow(t,.72)*Math.PI)*(2.7+(index%5)*.48);
        a+=Math.sin(t*5+age*.065+index)*.16;
        return radial(a,r,h);
    }
    private static Vec3 surface(double u,double a,double age,double length,double spread){
        double r=(.13+1.10*(1-Math.exp(-u*8)))*spread;
        r*=1+.13*Math.sin(u*39-age*.32+a*3)+.07*Math.sin(u*83-age*.51-a*5);
        double tip=Math.max(0,(u-.90)/.10);
        r*=1-tip*(.16+.14*Math.sin(a*7+age*.1));
        double pulse=Math.sin(u*18-age*.15)*.16*u;
        return MOUTH.add(-u*length-tip*(.8+.7*Math.sin(a*9+age*.11)),Math.sin(a)*r+pulse,Math.cos(a)*r+Math.sin(u*12-age*.09)*.18*u);
    }
}
