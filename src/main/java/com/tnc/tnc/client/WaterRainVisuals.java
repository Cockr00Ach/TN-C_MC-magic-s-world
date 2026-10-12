package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.TNWaterFieldEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Open rain gardens: clear sight lines, small water accents and sparse luminous borders. */
final class WaterRainVisuals {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[][] TINT = {
            {.12F,.42F,.86F}, {.10F,.70F,.62F}, {.20F,.56F,.96F},
            {.22F,.52F,.84F}, {.38F,.72F,1F}};

    static void render(TNWaterFieldEntity e, double age, float fade, PoseStack stack, MultiBufferSource buffers) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(e.position());
        WaterSolidModels.seal(stack,buffers,new Vec3(0,.07,0),UP,e.radius(),age,fade,com.tnc.tnc.magic.water.WaterSpellRules.RAIN_SEAL_OPACITY);
        stack.pushPose();stack.translate(0,e.height(),0);
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)(-age*.3)));
        stack.scale((float)(e.radius()*1.5),2,(float)(e.radius()*1.5));
        WaterSolidModels.draw("rain_crown",stack,buffers);stack.popPose();
        if(e.tier()==5)WaterSolidModels.seal(stack,buffers,new Vec3(0,e.height()+.15,0),UP,e.radius()*.78,-age,fade,com.tnc.tnc.magic.water.WaterSpellRules.RAIN_SEAL_OPACITY);
        int count=com.tnc.tnc.magic.water.WaterRainModelLayout.count(e.tier(),distance<64);
        for(int i=0;i<count;i++) {
            Vec3 p=com.tnc.tnc.magic.water.WaterRainModelLayout.drop(i,e.radius(),e.height(),age);
            stack.pushPose();stack.translate(p.x,p.y,p.z);
            float size=.85F+e.tier()*.07F;
            stack.scale(size,size,size);WaterSolidModels.draw("raindrop",stack,buffers);stack.popPose();
        }
    }

    // Kept separate from RenderType initialization so the complete mesh can be measured in tests.
    static void renderGarden(VertexConsumer body, VertexConsumer glow, Matrix4f pose, int tier,
                             double radius, double height, double age, float fade, boolean detail) {
        float[] c = TINT[tier - 1];
        int segments = detail ? 64 : 32;
        // No full disk or enclosing dome: terrain and the battle remain visible through the rain.
        WaterGeometry.ringColor(body, pose, new Vec3(0,.07,0), UP, radius, .16 + tier*.035,
                c[0], c[1], c[2], fade*.24F, segments);
        WaterGeometry.ringColor(glow, pose, new Vec3(0,.09,0), UP, radius*.985, .07 + tier*.012,
                c[0], c[1], c[2], fade*.30F, segments);
        if (detail) {
            int pools = tier==5?10:4+tier*2;
            for (int i=0; i<pools; i++) {
                double a=i*2.399963, rr=radius*Math.sqrt((i+.5)/pools)*.88;
                double phase=(age*.025+i*.618033)%1;
                WaterGeometry.ringColor(body,pose,new Vec3(Math.cos(a)*rr,.10,Math.sin(a)*rr),UP,
                        .2+phase*(.65+tier*.12),.045,c[0],c[1],c[2],fade*(float)(1-phase)*.42F,20);
            }
        }
        if (tier==1) {
            for (int i=0;i<3;i++) {
                double a=i*Math.PI*2/3+age*.004;
                WaterGeometry.droplet(body,pose,new Vec3(Math.cos(a)*radius*.28,1.1,Math.sin(a)*radius*.28),
                        .18,age,c[0],c[1],c[2],fade*.55F);
            }
            rain(body,pose,tier,radius,height,age,fade,detail);
            return;
        }
        double sky = height*(tier==5?1:.80);
        double canopy = radius*(tier==5?.80:.65);
        WaterGeometry.ringColor(glow,pose,new Vec3(0,sky,0),UP,canopy,.13,c[0],c[1],c[2],fade*.40F,segments);
        int petals=tier==5?12:tier==4?8:6;
        for(int i=0;i<petals;i++) {
            double a=i*Math.PI*2/petals+age*.002;
            petal(body,glow,pose,a,canopy*.60,canopy*.51,canopy*.075,sky,
                    1.2+Math.sin(age*.035+i)*.22,detail?(tier==5?8:10):5,c,fade*.40F,detail);
        }
        if(tier==3) WaterGeometry.waterLotus(body,pose,radius*.17,age,
                Math.min(1,age/24),c[0],c[1],c[2],fade*.48F);
        if(tier==4) for(int i=0;i<4;i++) {
            double a=i*Math.PI/2;
            Vec3 base=new Vec3(Math.cos(a)*radius*.80,.12,Math.sin(a)*radius*.80);
            Vec3 top=base.add(0,height*.28,0);
            WaterGeometry.tube(body,pose,base,top,.30,c[0],c[1],c[2],fade*.32F,12);
            WaterGeometry.ringColor(glow,pose,top,UP,.38,.07,c[0],c[1],c[2],fade*.42F,20);
        }
        if(tier==5) {
            WaterGeometry.ringColor(glow,pose,new Vec3(0,sky-3,0),UP,canopy*.66,.10,
                    .55F,.70F,1,fade*.32F,segments);
            // Water falls in curved, tapered streams rather than straight curtain panels.
            for(int i=0;i<12;i++) {
                double a=i*Math.PI/6+age*.002;
                for(int j=0;j<(detail?8:4);j++) {
                    int n=detail?8:4;
                    double t=j/(double)n,u=(j+1)/(double)n;
                    Vec3 p=stream(a,t,canopy,sky,age,i),q=stream(a,u,canopy,sky,age,i);
                    Vec3 side=new Vec3(-Math.sin(a),0,Math.cos(a));
                    double w=.10+.16*(1-t),w2=.10+.16*(1-u);
                    WaterGeometry.quad(body,pose,p.subtract(side.scale(w)),p.add(side.scale(w)),
                            q.add(side.scale(w2)),q.subtract(side.scale(w2)),
                            c[0]+(float)t*.15F,c[1],c[2],fade*.27F);
                }
            }
            for(int i=0;i<8;i++)petal(body,glow,pose,i*Math.PI/4+age*.003,
                    radius*.025,radius*.09,radius*.012,.15,height*.085,detail?6:4,c,fade*.45F,detail);
        }
        rain(body,pose,tier,radius,height,age,fade,detail);
    }

    private static Vec3 stream(double a,double t,double r,double h,double age,int slot) {
        double rr=r*(1-.08*Math.sin(t*Math.PI))+.12*Math.sin(age*.08+t*8+slot);
        return new Vec3(Math.cos(a)*rr,.12+h*(1-t),Math.sin(a)*rr);
    }

    /** Rounded petal: a tapered strip with smooth curvature and a narrow reflective edge. */
    private static void petal(VertexConsumer body,VertexConsumer glow,Matrix4f pose,double angle,
                              double root,double reach,double width,double y,double lift,int steps,
                              float[] c,float alpha,boolean edge) {
        Vec3 side=new Vec3(-Math.sin(angle),0,Math.cos(angle));
        for(int j=0;j<steps;j++) {
            double t=j/(double)steps,u=(j+1)/(double)steps;
            Vec3 p=petalPoint(angle,root,reach,y,lift,t),q=petalPoint(angle,root,reach,y,lift,u);
            double w=width*Math.sin(Math.PI*t),w2=width*Math.sin(Math.PI*u);
            WaterGeometry.quad(body,pose,p.subtract(side.scale(w)),p.add(side.scale(w)),
                    q.add(side.scale(w2)),q.subtract(side.scale(w2)),
                    c[0]+(float)t*.18F,c[1]+(float)t*.14F,c[2],alpha*(.65F+(float)t*.35F));
            if(edge)WaterGeometry.stroke(glow,pose,UP,p.add(side.scale(w)),q.add(side.scale(w2)),
                    .025,c[0]+.25F,c[1]+.20F,c[2],alpha*.55F);
        }
    }
    private static Vec3 petalPoint(double a,double root,double reach,double y,double lift,double t) {
        double r=root+reach*t;
        return new Vec3(Math.cos(a)*r,y+lift*Math.sin(t*Math.PI*.85),Math.sin(a)*r);
    }
    /** Bounded mesh rain has continuous motion even when native particle settings are reduced. */
    private static void rain(VertexConsumer out,Matrix4f pose,int tier,double r,double h,double age,float fade,boolean detail) {
        int count=detail?20+tier*16:10+tier*10;
        for(int i=0;i<count;i++) {
            double a=i*2.399963,rr=r*.94*Math.sqrt((i+.5)/count);
            double speed=.55+(i%7)*.065;
            double y=.25+(h-((age*speed+i*.618033*h)%h));
            double len=Math.min(y-.12,.55+tier*.12);
            if(len<=0)continue;
            Vec3 p=new Vec3(Math.cos(a)*rr,y,Math.sin(a)*rr),q=p.add(-.04,-len,.015);
            WaterGeometry.stroke(out,pose,new Vec3(0,0,1),p,q,.012+tier*.002,.48F,.76F,1,fade*.32F);
            if(detail)WaterGeometry.stroke(out,pose,new Vec3(1,0,0),p,q,.012+tier*.002,.48F,.76F,1,fade*.24F);
        }
    }
}
