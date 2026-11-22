package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import com.tnc.tnc.magic.free.*;

/** Shared cast positions make the visible formation the actual source of each attack. */
final class WaterCastVisuals {
    private static final RenderType LINES=WaterEffectPass.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","textures/effects/free_water/core.png"));
    static void formation(PoseStack pose,MultiBufferSource buffers,Vec3 camera,Vec3 eye,Vec3 look,
                          FreeMagicIntent.Intent intent,int count,double progress,boolean confirmed,double now,java.util.List<Vec3> targets){
        var spell=intent==null?new FreeMagicIntent.Intent(FreeMagicIntent.Shape.BALL,FreeMagicIntent.Mode.SINGLE):intent;
        var effective=spell.shape();
        for(int i=0;i<count;i++){
            Vec3 at=confirmed&&i<targets.size()?targets.get(i):WaterCastGeometry.origin(spell,eye,look,i,count);
            Vec3 direction=WaterCastGeometry.direction(eye,look,at);
            local(pose,at,camera,effective.stationary()?new Vec3(0,1,0):direction);double size=(effective.large()?1.5:1)*(spell.has(FreeMagicIntent.GIANT)?3.0:1);
            double open=confirmed?Math.min(1,progress*5):.32;
            circle(pose,buffers,size*open,now*.018,.55*open,-.45);
            if(spell.chantPower()>0)for(int j=0;j<spell.chantPower()*3;j++){
                double a=j*Math.PI*2/(spell.chantPower()*3)+now*.025;
                Vec3 p=new Vec3(Math.cos(a)*size*1.12,Math.sin(a)*size*1.12,-.43);
                line(pose,buffers,p.add(0,-.035,0),p.add(0,.035,0),.013,.8*open,1f,.8f,.35f);
            }
            for(int j=0;j<12;j++){
                double a=j*Math.PI/6+now*.055,r=size*(1.15+.2*Math.sin(j+now*.1));
                double q=(now*.025+j/12.0)%1,tail=Math.max(0,q-.18);
                Vec3 from=thread(a,r,tail),to=thread(a,r,q);
                line(pose,buffers,from,to,.012,confirmed?.45:.18);
            }
            if(spell.has(FreeMagicIntent.EMPOWER)){
                for(int seal=0;seal<3;seal++)coloredRing(pose,buffers,size*(.88+seal*.18),now*(.035+seal*.012)*(seal%2==0?1:-1),open*(.45+.45*progress),-.6-seal*.28,1f,.7f,.18f,.025);
                for(int j=0;j<8;j++){
                    double a=j*Math.PI/4+now*.08;
                    line(pose,buffers,new Vec3(Math.cos(a)*size,Math.sin(a)*size,-1.6),new Vec3(Math.cos(a+.65)*size*.4,Math.sin(a+.65)*size*.4,0),.035,open*.8,1f,.78f,.25f);
                }
            }
            modifierAura(pose,buffers,spell,size,now,open*.65);
            pose.popPose();
            if(confirmed&&intent!=null&&progress>.17){
                Vec3 flourish=direction.yRot((float)(.38*(1-progress)));
                model(pose,buffers,camera,spell,at,flourish,Math.min(1,(progress-.17)/.68),now);
            }
        }
    }
    static void gather(PoseStack pose,MultiBufferSource buffers,Vec3 camera,Vec3 eye,Vec3 look,double now){
        Vec3 at=eye.add(look.scale(1.3)).add(WaterCastGeometry.right(look).scale(.55)).add(0,-.45,0);
        local(pose,at,camera,look);
        for(int i=0;i<8;i++){
            double a=now*.06+i*Math.PI/4,r=.17+.08*Math.sin(now*.1+i);
            line(pose,buffers,new Vec3(Math.cos(a)*r,Math.sin(a)*r,0),new Vec3(Math.cos(a+.6)*r,Math.sin(a+.6)*r,.15),.015,.5);
        }
        pose.popPose();
    }
    static void residue(PoseStack pose,MultiBufferSource buffers,Vec3 camera,FreeMagicIntent.Intent intent,Vec3 at,double age,int duration,double now){
        double fade=Math.min(1,Math.max(0,(duration-age)/15.0)),r=Math.max(1.6,intent.radius()*1.8);
        local(pose,at,camera,new Vec3(0,1,0));
        for(int i=0;i<4;i++){
            double q=(age*.025+i*.25)%1;
            coloredRing(pose,buffers,r*(.25+.75*q),now*.01*(i%2==0?1:-1),fade*(1-q)*.8,.05+i*.025,.3f,.9f,1f,.035);
        }
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6+now*.025;
            Vec3 foot=new Vec3(Math.cos(a)*r*.8,Math.sin(a)*r*.8,0);
            line(pose,buffers,foot,foot.add(0,0,.4+.35*Math.sin(now*.12+i)),.025,fade*.55,.45f,1f,.85f);
        }
        pose.popPose();
    }
    private static void modifierAura(PoseStack pose,MultiBufferSource buffers,FreeMagicIntent.Intent intent,double radius,double now,double alpha){
        if(intent.has(FreeMagicIntent.SHOCK))for(int i=0;i<3;i++){
            double q=(now*.12+i/3.0)%1;
            coloredRing(pose,buffers,radius*(.7+q),now*.08,alpha*(1-q),-.2-q*1.6,.75f,.96f,1f,.045);
        }
        if(intent.has(FreeMagicIntent.DURABLE)){
            coloredRing(pose,buffers,radius*1.22,now*.018,alpha,-.2,.3f,1f,.75f,.035);
            coloredRing(pose,buffers,radius*1.37,-now*.022,alpha*.65,-.4,.3f,.85f,1f,.025);
        }
    }
    private static void coloredRing(PoseStack pose,MultiBufferSource buffers,double radius,double turn,double alpha,double z,float r,float g,float b,double width){
        for(int i=0;i<48;i++){
            double a=i*Math.PI/24+turn,c=a+Math.PI/24;
            line(pose,buffers,new Vec3(Math.cos(a)*radius,Math.sin(a)*radius,z),new Vec3(Math.cos(c)*radius,Math.sin(c)*radius,z),width,alpha,r,g,b);
        }
    }
    private static Vec3 thread(double a,double r,double q){double turn=a+q*2.4;return new Vec3(Math.cos(turn)*r*(1-q),Math.sin(turn)*r*(1-q),-.45+q*.45);}
    static void attack(PoseStack pose,MultiBufferSource buffers,Vec3 camera,FreeMagicIntent.Intent intent,
                       Vec3 at,Vec3 velocity,double age,int duration,double now){
        var shape=intent.shape();
        Vec3 dir=velocity.lengthSqr()<.00001?new Vec3(0,0,1):velocity.normalize();
        model(pose,buffers,camera,intent,at,dir,Math.min(1,(duration-age)/6),now);
        if(shape.stationary()){
            local(pose,at,camera,new Vec3(0,1,0));circle(pose,buffers,intent.radius(),now*.02,.6,0);
            if(shape.behavior==FreeMagicIntent.Behavior.VORTEX)circle(pose,buffers,intent.radius()*2.4,-now*.012,.16,0);
            modifierAura(pose,buffers,intent,intent.radius(),now,.8);
            pose.popPose();
        }else{
            local(pose,at,camera,dir);
            double width=intent.has(FreeMagicIntent.GIANT)?1.1:.38;
            double tail=intent.has(FreeMagicIntent.FAST)?7:2;
            for(int j=0;j<(intent.has(FreeMagicIntent.FAST)?10:5);j++){double a=j*Math.PI*2/10+now*.09;
                line(pose,buffers,new Vec3(Math.cos(a)*width,Math.sin(a)*width,-.3),
                        new Vec3(Math.cos(a+.25)*width*1.4,Math.sin(a+.25)*width*1.4,-tail),intent.has(FreeMagicIntent.FAST)?.035:.018,.7);}
            if(intent.has(FreeMagicIntent.EMPOWER))for(int j=0;j<4;j++)for(int k=0;k<10;k++){
                double a=j*Math.PI/2+now*.12-k*.24,r=width*(1+k*.06);
                line(pose,buffers,new Vec3(Math.cos(a)*r,Math.sin(a)*r,-k*.45),new Vec3(Math.cos(a-.24)*r,Math.sin(a-.24)*r,-(k+1)*.45),.045,.85*(1-k/12.0),1f,.72f,.18f);
            }
            modifierAura(pose,buffers,intent,Math.max(.6,intent.radius()),now,.8);
            pose.popPose();
        }
    }
    static void impact(PoseStack pose,MultiBufferSource buffers,Vec3 camera,FreeMagicIntent.Intent intent,
                       Vec3 at,double age,int duration,double now){
        var shape=intent.shape();
        double fade=Math.max(0,Math.min(1,(duration-age)/12));if(fade<=0)return;
        if(shape.behavior==FreeMagicIntent.Behavior.PRISON||shape.behavior==FreeMagicIntent.Behavior.NET)
            model(pose,buffers,camera,intent,at,new Vec3(0,0,1),Math.min(fade,age/4),now);
        local(pose,at,camera,new Vec3(0,1,0));
        double radius=shape.behavior==FreeMagicIntent.Behavior.EXPLODE?intent.radius()*3.6:1.74;
        circle(pose,buffers,.3+Math.min(age/16,1)*(radius-.3),now*.025,fade*.45,0);
        if(intent.has(FreeMagicIntent.SHOCK))for(int ring=0;ring<3;ring++){
            double r=.4+age*.32-ring*.6;
            if(r>0)coloredRing(pose,buffers,r,-now*.02,fade*.9,ring*.35,.7f,.95f,1f,.05);
        }
        if(intent.has(FreeMagicIntent.EMPOWER))coloredRing(pose,buffers,.5+age*.22,now*.04,fade,.18,1f,.72f,.2f,.045);
        pose.popPose();
    }
    private static void model(PoseStack pose,MultiBufferSource buffers,Vec3 camera,FreeMagicIntent.Intent intent,Vec3 at,Vec3 dir,double growth,double now){
        if(growth<=0)return;var shape=intent.shape();
        Vec3 facing=shape.stationary()?new Vec3(0,0,1):dir;
        if(shape.behavior==FreeMagicIntent.Behavior.WAVE){
            facing=new Vec3(dir.x,0,dir.z);facing=facing.lengthSqr()<.001?new Vec3(0,0,1):facing.normalize();
        }
        local(pose,at,camera,facing);
        pose.scale((float)intent.scale(),(float)intent.scale(),(float)intent.scale());
        WaterShapeVisuals.draw(pose,buffers,shape.ordinal(),growth,now*(intent.has(FreeMagicIntent.FAST)?1.25:1));pose.popPose();
    }
    private static void local(PoseStack pose,Vec3 at,Vec3 camera,Vec3 dir){
        pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
        pose.mulPose(new Quaternionf().rotationTo(0,0,1,(float)dir.x,(float)dir.y,(float)dir.z));
    }
    private static void circle(PoseStack pose,MultiBufferSource buffers,double radius,double turn,double alpha,double z){
        for(int ring=0;ring<2;ring++)for(int j=0;j<64;j++){
            double a=j*Math.PI/32+turn*(ring==0?1:-1),b=a+Math.PI/32,r=radius*(ring==0?1:.83);
            line(pose,buffers,new Vec3(Math.cos(a)*r,Math.sin(a)*r,z),new Vec3(Math.cos(b)*r,Math.sin(b)*r,z),ring==0?.013:.008,alpha);
        }
        for(int j=0;j<6;j++){
            double a=j*Math.PI/3-turn;Vec3 p=new Vec3(Math.cos(a)*radius,Math.sin(a)*radius,z);
            Vec3 tip=new Vec3(Math.cos(a)*radius*1.17,Math.sin(a)*radius*1.17,z);
            line(pose,buffers,p,tip,.022,alpha);
            double b=a+Math.PI*2/3;
            line(pose,buffers,new Vec3(Math.cos(a)*radius*.72,Math.sin(a)*radius*.72,z),new Vec3(Math.cos(b)*radius*.72,Math.sin(b)*radius*.72,z),.007,alpha*.5);
        }
    }
    private static void line(PoseStack pose,MultiBufferSource buffers,Vec3 a,Vec3 b,double width,double alpha){
        line(pose,buffers,a,b,width,alpha,.7f,.95f,1f);
    }
    private static void line(PoseStack pose,MultiBufferSource buffers,Vec3 a,Vec3 b,double width,double alpha,float red,float green,float blue){
        if(alpha<=.01)return;Vec3 delta=b.subtract(a),side=delta.cross(new Vec3(0,0,1)).normalize().scale(width);
        if(side.lengthSqr()<1e-8)side=new Vec3(width,0,0);
        var v=buffers.getBuffer(LINES);var matrix=pose.last().pose();
        for(Vec3 p:new Vec3[]{a.add(side),b.add(side),b.subtract(side),a.subtract(side)})
            v.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color(red,green,blue,(float)alpha).uv(.01f,.01f)
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.last().normal(),0,0,1).endVertex();
    }
    private WaterCastVisuals(){}
}
