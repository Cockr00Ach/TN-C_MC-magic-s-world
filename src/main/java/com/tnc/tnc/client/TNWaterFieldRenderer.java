package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class TNWaterFieldRenderer extends EntityRenderer<TNWaterFieldEntity> {
    public TNWaterFieldRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TNWaterFieldEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    @Override public boolean shouldRender(TNWaterFieldEntity e,Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<256*256;}
    @Override public void render(TNWaterFieldEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        double age=e.age()+partial;float fade=(float)Math.min(1,Math.min(age/5,(e.life()-age)/8));if(fade<=0)return;
        if(e.kind()==3){WaterRainVisuals.render(e,age,fade,stack,buffers);return;}
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();double radius=e.radius();Vec3 up=new Vec3(0,1,0);
        if(e.kind()==1) {
            Vec3 center=e.waveCenter(age).subtract(e.position());
            if(e.tier()==1) {
                for(int i=0;i<3;i++)WaterGeometry.ring(out,pose,new Vec3(0,.1+i*.12,0),up,Math.min(radius,age*.2+i*.3),.09,0,fade);
            } else if(e.tier()==5) {
                // Four full circular walls: the interior is filled by successive moving wave fronts.
                for(int i=0;i<4;i++) {
                    double r=(age*.85+i*radius/4)%radius;
                    double height=8+20*Math.sin(Math.PI*r/radius);
                    WaterWaveVisuals.circularWave(out,pose,r,height,age+i*10,fade*.72F);
                }
                WaterGeometry.ring(out,pose,new Vec3(0,.1,0),up,radius,.45,0,fade);
                for(int i=0;i<16;i++) {
                    double a=i*Math.PI/8+age*.012;Vec3 p=new Vec3(Math.cos(a)*radius*.8,0,Math.sin(a)*radius*.8);
                    WaterGeometry.spiral(out,pose,p,up,1.2,e.height(),age*.08+i,.02F,.3F,1,fade*.75F);
                }
            } else {
                if(e.tier()==3)WaterWaveVisuals.crescent(out,pose,center,e.direction(),radius,age,fade);
                else WaterWaveVisuals.rollingWave(out,pose,center,e.direction(),radius,e.tier()==4?8:2,age,fade);
            }
        } else if(e.kind()==2) {
            // Open cage: no continuous opaque wall, even at the highest tier.
            double h=e.height();
            for(int i=0;i<3;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,.12+h*i/2,0),up,radius,.12,.01F,.28F,1,fade*.85F);
            for(int i=0;i<8+e.tier()*2;i++) {
                double a=i*Math.PI*2/(8+e.tier()*2)+age*.003;
                Vec3 p=new Vec3(Math.cos(a)*radius,.12,Math.sin(a)*radius);
                WaterGeometry.tube(out,pose,p,p.add(0,h,0),.055,.025F,.42F,1,fade*.62F);
            }
            for(int i=0;i<2;i++)WaterGeometry.spiral(out,pose,Vec3.ZERO,up,radius*.97,h,-age*.022+i*Math.PI,.01F,.2F,.82F,fade*.65F);
            WaterGeometry.ringColor(out,pose,new Vec3(0,.08,0),up,radius,.22,1,.65F,.08F,fade*.9F);
        }
        if(e.kind()==2 && e.tier()>=3)WaterGeometry.richCircle(buffers,stack,new Vec3(0,.06,0),up,radius,age,fade*.65F);
    }
}
