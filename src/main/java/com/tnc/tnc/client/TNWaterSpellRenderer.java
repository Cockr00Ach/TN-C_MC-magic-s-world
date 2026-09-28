package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.tnc.tnc.magic.water.*;

public final class TNWaterSpellRenderer extends EntityRenderer<TNWaterSpellEntity> {
    public TNWaterSpellRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TNWaterSpellEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    @Override public boolean shouldRender(TNWaterSpellEntity e,Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<256*256;}
    @Override public void render(TNWaterSpellEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        double age=e.age()+partial,charge=WaterSpellRules.charge(e.tier());
        float fade=(float)Math.min(1,(charge+WaterSpellRules.duration(e.tier())-age)/10);
        if(fade<=0)return;
        Vec3 dir=e.direction();double circleRadius=WaterSpellRules.circleRadius(e.tier())*Math.min(1,.45+age/charge);
        WaterGeometry.richCircle(buffers,stack,Vec3.ZERO,dir,circleRadius,age,fade);
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();
        if(e.length()<=0)return;
        double length=e.length(),radius=WaterSpellRules.radius(e.tier());
        Vec3 end=dir.scale(length),right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        WaterGeometry.tube(out,pose,Vec3.ZERO,end,radius,.015F,.16F,.85F,fade*.38F);
        WaterGeometry.tube(out,pose,Vec3.ZERO,end,radius*(e.tier()==5?.94:.78),.025F,.5F,1,fade*.65F);
        WaterGeometry.tube(out,pose,Vec3.ZERO,end,radius*(e.tier()==5?.72:.35),.4F,.87F,1,fade*.9F);
        if(e.tier()==5) {
            // The whole main sigil emits, not only a narrow bright tube in its center.
            // Both caps stay inside the committed bore; no visual penetration of protected terrain.
            Vec3 muzzle=dir.scale(Math.min(.08,length*.5));
            WaterGeometry.disk(out,pose,muzzle,dir,radius,.015F,.36F,1,fade*.72F);
            WaterGeometry.disk(out,pose,end,dir,radius,.025F,.5F,1,fade*.65F);
        }
        // Two visible spiral ribbons convey the stream's direction and pressure.
        int strands=e.tier()==5?6:2;
        double step=e.tier()==5?1.6:.8;
        for(int strand=0;strand<strands;strand++)for(double z=0;z<length;z+=step) {
            double z2=Math.min(length,z+step),a=z*.75-age*.22+strand*Math.PI*2/strands,b=z2*.75-age*.22+strand*Math.PI*2/strands;
            Vec3 p=dir.scale(z).add(WaterGeometry.radial(right,up,a,radius*.9));
            Vec3 q=dir.scale(z2).add(WaterGeometry.radial(right,up,b,radius*.9));
            WaterGeometry.tube(out,pose,p,q,e.tier()==5?.12:.055,.5F,.94F,1,fade*.6F);
        }
        for(double z=(age*.65)%5;z<length;z+=5)
            WaterGeometry.ring(out,pose,dir.scale(z),dir,radius*1.04,e.tier()==5?.16:.05,0,fade*.65F);
        WaterGeometry.ring(out,pose,end,dir,radius,.12,0,fade);
        // Layered end flare, pressure crown and travelling white ribs, all clipped to actual drilled length.
        for(int i=0;i<3;i++)WaterGeometry.ringColor(out,pose,end.subtract(dir.scale(i*.12)),dir,
                radius*(1+i*.18),.08,.55F,.94F,1,fade*(.6F-i*.12F));
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6+age*.05;
            Vec3 p=end.add(WaterGeometry.radial(right,up,a,radius*.6));
            Vec3 q=end.add(WaterGeometry.radial(right,up,a+.08,radius*1.35));
            WaterGeometry.tube(out,pose,p,q,.035,.8F,.98F,1,fade*.6F);
        }
    }
}
