package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** T4 only: animated textured surf, a continuous barrel and torn whitewater. */
final class TsunamiVisuals {
    private static final Vec3 UP=new Vec3(0,1,0);
    record Uv(float u0,float u1,float v0,float v1) {
        static Uv of(TextureAtlasSprite sprite){return new Uv(sprite.getU0(),sprite.getU1(),sprite.getV0(),sprite.getV1());}
        float u(double t){return (float)(u0+(u1-u0)*t);}
        float v(double t){return (float)(v0+(v1-v0)*t);}
    }
    static void render(PoseStack pose,MultiBufferSource buffers,Vec3 center,Vec3 direction,double radius,
                       double height,double age,float alpha,boolean near) {
        var atlas=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        Uv water=Uv.of(atlas.apply(ResourceLocation.fromNamespaceAndPath("minecraft","block/water_flow")));
        Uv foam=Uv.of(atlas.apply(ResourceLocation.fromNamespaceAndPath("minecraft","block/white_wool")));
        mesh(buffers.getBuffer(WaterModelRenderTypes.alpha()),pose.last(),center,direction,radius,height,age,alpha,near,water,foam);
        // Acquire the second format only once all textured vertices have been submitted.
        glow(buffers.getBuffer(WaterRenderTypes.glow()),pose.last(),center,direction,radius,height,age,alpha,near);
    }
    static void mesh(VertexConsumer out,PoseStack.Pose pose,Vec3 center,Vec3 direction,double radius,
                     double height,double age,float alpha,boolean near,Uv water,Uv foam) {
        if(radius<=0||height<=0||alpha<=0)return;
        int segments=near?72:32,layers=near?10:7;
        Vec3 right=WaterSpellRules.right(direction);
        Vec3[][] columns=new Vec3[segments+1][layers+1];
        for(int i=0;i<=segments;i++)for(int j=0;j<=layers;j++)
            columns[i][j]=point(center,direction,right,i/(double)segments*2-1,j/(double)layers,radius,height,age);
        for(int i=0;i<segments;i++) {
            double a=i/(double)segments*2-1,b=(i+1)/(double)segments*2-1;
            for(int j=0;j<layers;j++) {
                float t=j/(float)layers;
                float light=.5F+.5F*t;
                quad(out,pose,columns[i][j],columns[i+1][j],columns[i+1][j+1],columns[i][j+1],
                        .12F+light*.08F,.38F+light*.26F,.94F,alpha*(.56F+t*.16F),water);
            }
            // Foam crosses the crest and follows the inward curl, with an irregular lower edge.
            if(i%5!=0) {
                double endA=.81+.035*Math.sin(i*1.9-age*.10),endB=.81+.035*Math.sin((i+1)*1.9-age*.10);
                quad(out,pose,point(center,direction,right,a,.62,radius,height,age).add(0,.025,0),
                        point(center,direction,right,b,.62,radius,height,age).add(0,.025,0),
                        point(center,direction,right,b,endB,radius,height,age).add(0,.025,0),
                        point(center,direction,right,a,endA,radius,height,age).add(0,.025,0),
                        .86F,.97F,1,alpha*.86F,foam);
            }
            if(i%4==1) {
                double u=(a+b)*.5,thin=(b-a)*.15;
                quad(out,pose,point(center,direction,right,u-thin,.36,radius,height,age),
                        point(center,direction,right,u+thin,.40,radius,height,age),
                        point(center,direction,right,u+thin,.57,radius,height,age),
                        point(center,direction,right,u-thin,.54,radius,height,age),
                        .68F,.91F,1,alpha*.52F,foam);
            }
            if(i%8==2) {
                double phase=(age*.033+i*.137)%1;
                Vec3 p=point(center,direction,right,(a+b)*.5,.67,radius,height,age)
                        .add(direction.scale(phase*1.8)).add(0,Math.sin(phase*Math.PI)*1.1,0);
                Vec3 across=right.scale(.065),up=UP.scale(.14*(1-phase)+.035);
                quad(out,pose,p.subtract(across),p.add(up),p.add(across),p.subtract(up),
                        .85F,.97F,1,alpha*(float)(1-phase)*.75F,foam);
            }
        }
    }
    private static Vec3 point(Vec3 center,Vec3 direction,Vec3 right,double across,double t,double radius,double height,double age) {
        double x,y;
        if(t<=.65) {
            double u=t/.65,v=1-u;
            x=-.95*v*v*v-1.65*v*v*u-.60*v*u*u+.55*u*u*u;
            y=.03*v*v*v+.42*v*v*u+3*v*u*u+u*u*u;
        } else {
            double angle=(t-.65)/.35*Math.PI*1.1;
            x=.55+.55*Math.sin(angle);y=.82+.18*Math.cos(angle);
        }
        double taper=Math.sqrt(Math.max(.001,1-across*across));
        double motion=1+.025*Math.sin(across*17-age*.13)+.012*Math.cos(across*31+age*.08);
        return center.add(right.scale(across*radius)).add(0,.06+y*height*taper*motion,0)
                .add(direction.scale((x+.25*taper)*3.2));
    }
    private static void quad(VertexConsumer out,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
                             float r,float g,float blue,float alpha,Uv uv) {
        Vec3 normal=b.subtract(a).cross(d.subtract(a)).normalize();
        vertex(out,pose,a,r,g,blue,alpha,uv.u(0),uv.v(1),normal);
        vertex(out,pose,b,r,g,blue,alpha,uv.u(1),uv.v(1),normal);
        vertex(out,pose,c,r,g,blue,alpha,uv.u(1),uv.v(0),normal);
        vertex(out,pose,d,r,g,blue,alpha,uv.u(0),uv.v(0),normal);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,float r,float g,float b,float alpha,float u,float v,Vec3 n) {
        out.vertex(pose.pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,b,alpha).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
    private static void glow(VertexConsumer out,PoseStack.Pose pose,Vec3 center,Vec3 direction,double radius,
                             double height,double age,float alpha,boolean near) {
        Vec3 right=WaterSpellRules.right(direction);int segments=near?72:32;
        for(int i=0;i<segments;i++)if(i%4!=0) {
            double a=i/(double)segments*2-1,b=(i+1)/(double)segments*2-1;
            WaterGeometry.stroke(out,pose.pose(),UP,point(center,direction,right,a,.65,radius,height,age),
                    point(center,direction,right,b,.65,radius,height,age),.035,.40F,.78F,1,alpha*.26F);
        }
    }
}
