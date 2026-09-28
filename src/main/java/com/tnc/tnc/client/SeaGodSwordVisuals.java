package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.SeaGodSwordRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Finite, procedural 3D sword rather than a reskinned laser or a particle cloud. */
final class SeaGodSwordVisuals {
    private static final Vec3 UP=new Vec3(0,1,0);
    private SeaGodSwordVisuals() {}
    static void render(double age,double radius,float fieldFade,PoseStack stack,MultiBufferSource buffers) {
        float fade=fieldFade*SeaGodSwordRules.opacity(age);
        if(fade<=0)return;
        double tip=SeaGodSwordRules.tipHeight(age);
        // Draw all textured seals first, then acquire the geometry buffer once.
        WaterGeometry.richCircle(buffers,stack,new Vec3(0,116,0),UP,18,age,fade);
        WaterGeometry.richCircle(buffers,stack,new Vec3(0,112,0),UP,12,-age,fade*.85F);
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();
        double spin=age*.006;
        double[] heights={0,8,44,52},widths={0,1.8,6,4.6},depths={0,.45,1.6,1.2};
        for(int layer=0;layer<3;layer++) {
            Vec3[] low=section(tip+heights[layer],widths[layer],depths[layer],spin);
            Vec3[] high=section(tip+heights[layer+1],widths[layer+1],depths[layer+1],spin);
            for(int side=0;side<4;side++) {
                int next=(side+1)%4;
                WaterGeometry.quad(out,pose,low[side],high[side],high[next],low[next],
                        .015F,side%2==0?.32F:.58F,1,fade*.82F);
                WaterGeometry.tube(out,pose,low[side],high[side],.13,.3F,.85F,1,fade*.95F);
            }
        }
        // Raised central ridge, swept crossguard, banded grip and luminous pommel.
        WaterGeometry.tube(out,pose,new Vec3(0,tip+2,0),new Vec3(0,tip+52,0),.24,.45F,.92F,1,fade);
        Vec3 guardCenter=new Vec3(0,tip+53,0);
        for(int side:new int[]{-1,1}) {
            Vec3 inner=rotate(new Vec3(side*7,tip+54,0),spin),edge=rotate(new Vec3(side*13,tip+51,0),spin);
            WaterGeometry.tube(out,pose,guardCenter,inner,1.05,.015F,.25F,.85F,fade*.88F);
            WaterGeometry.tube(out,pose,inner,edge,.7,.02F,.5F,1,fade*.9F);
            WaterGeometry.tube(out,pose,inner.add(0,.8,0),edge.add(0,.5,0),.14,.5F,.92F,1,fade);
        }
        WaterGeometry.tube(out,pose,new Vec3(0,tip+53,0),new Vec3(0,tip+62,0),.65,.015F,.18F,.65F,fade*.9F);
        for(int i=0;i<7;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,tip+54+i,0),UP,.85,.15,1,.67F,.08F,fade*.9F);
        WaterGeometry.tube(out,pose,new Vec3(0,tip+62,0),new Vec3(0,tip+SeaGodSwordRules.LENGTH,0),1.25,.08F,.6F,1,fade);
        WaterGeometry.ringColor(out,pose,new Vec3(0,tip+63,0),UP,3,.25,1,.65F,.08F,fade);
        // Six open spiral streams flow along the blade, leaving its silhouette readable.
        for(int i=0;i<6;i++)WaterGeometry.spiral(out,pose,new Vec3(0,tip,0),UP,7,52,-age*.12+i*Math.PI/3,.015F,.45F,1,fade*.65F);
        WaterGeometry.ringColor(out,pose,new Vec3(0,.16,0),UP,radius,.4,.02F,.5F,1,fade);
        double impactAge=age-SeaGodSwordRules.IMPACT_TICK;
        if(impactAge>=0)impact(out,pose,impactAge,radius,fade);
    }
    private static Vec3[] section(double y,double width,double depth,double spin) {
        return new Vec3[]{rotate(new Vec3(-width,y,0),spin),rotate(new Vec3(0,y,depth),spin),
                rotate(new Vec3(width,y,0),spin),rotate(new Vec3(0,y,-depth),spin)};
    }
    private static Vec3 rotate(Vec3 p,double spin) {
        return new Vec3(p.x*Math.cos(spin)-p.z*Math.sin(spin),p.y,p.x*Math.sin(spin)+p.z*Math.cos(spin));
    }
    private static void impact(VertexConsumer out,Matrix4f pose,double age,double radius,float fade) {
        for(int i=0;i<4;i++) {
            double r=Math.min(radius,Math.max(0,(age-i*3)*1.3));
            if(r<=0)continue;
            WaterGeometry.ringColor(out,pose,new Vec3(0,.2+i*.25,0),UP,r,.65,.015F,.55F,1,fade*(1-i*.12F));
        }
        // Splash crown is visual only; gameplay still uses the existing upright field area.
        double burst=Math.sin(Math.min(1,age/24)*Math.PI);
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8;
            Vec3 foot=new Vec3(Math.cos(a)*radius*.45,.25,Math.sin(a)*radius*.45);
            Vec3 crest=new Vec3(Math.cos(a)*radius*.8,2+burst*(12+i%3*3),Math.sin(a)*radius*.8);
            WaterGeometry.tube(out,pose,foot,crest,.22,.04F,.5F,1,fade*.75F);
        }
    }
}
