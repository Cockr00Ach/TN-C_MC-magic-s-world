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
        var out=buffers.getBuffer(RenderType.lightning());var pose=stack.last().pose();double radius=e.radius();Vec3 up=new Vec3(0,1,0);
        if(e.kind()==1) {
            Vec3 center=e.waveCenter(age).subtract(e.position());
            if(e.tier()==1) {
                for(int i=0;i<3;i++)WaterGeometry.ring(out,pose,new Vec3(0,.1+i*.12,0),up,Math.min(radius,age*.2+i*.3),.09,0,fade);
            } else if(e.tier()==5) {
                // Four full circular walls: the interior is filled by successive moving wave fronts.
                for(int i=0;i<4;i++) {
                    double r=(age*.55+i*5)%20;
                    double height=3+12*Math.sin(Math.PI*r/20);
                    WaterWaveVisuals.circularWave(out,pose,r,height,age+i*10,fade*.65F);
                }
                WaterGeometry.ring(out,pose,new Vec3(0,.1,0),up,20,.3,0,fade);
            } else {
                if(e.tier()==3)WaterWaveVisuals.crescent(out,pose,center,e.direction(),radius,age,fade);
                else WaterWaveVisuals.rollingWave(out,pose,center,e.direction(),radius,e.tier()==4?8:2,age,fade);
            }
        } else if(e.kind()==2) {
            if(e.tier()<3) {
                for(int i=0;i<(e.tier()==1?2:6);i++) {
                    double y=e.tier()==1?.2+i*.25:.15+i*.38;
                    WaterGeometry.ring(out,pose,new Vec3(0,y,0),up,e.tier()==1?.65:Math.sqrt(Math.max(.1,1.5-Math.pow(y-1.1,2))),.07,0,fade);
                }
                if(e.tier()==2)for(int i=0;i<8;i++) {
                    double a=i*Math.PI/4+age*.02;
                    WaterGeometry.tube(out,pose,new Vec3(Math.cos(a),.3,Math.sin(a)),new Vec3(Math.cos(a),2,Math.sin(a)),.045,.25F,.85F,1,fade*.5F);
                }
            } else {
                for(int i=0;i<8;i++)WaterGeometry.ring(out,pose,new Vec3(0,.08+i*.2,0),up,radius*(1-i*.07),.05,0,fade*.8F);
                if(e.tier()>=4)WaterWaveVisuals.circularWave(out,pose,radius, e.tier()==5?8:4,age,fade*.45F);
                if(e.tier()==5)for(int i=0;i<10;i++) {
                    double y=i*.8;
                    WaterGeometry.ring(out,pose,new Vec3(0,y,0),up,radius*Math.sqrt(Math.max(.02,1-y*y/64)),.06,0,fade*.65F);
                }
            }
        }
        if(e.kind()==2 && e.tier()>=3)WaterGeometry.richCircle(buffers,stack,new Vec3(0,.06,0),up,radius,age,fade*.65F);
    }
}
