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
        var out=buffers.getBuffer(RenderType.lightning());var pose=stack.last().pose();Vec3 dir=e.direction();
        WaterGeometry.circle(out,pose,dir,WaterSpellRules.circleRadius(e.tier())*Math.min(1,.2+age/charge),age,fade*.9F);
        if(e.length()<=0)return;
        double length=e.length(),radius=WaterSpellRules.radius(e.tier());
        Vec3 end=dir.scale(length),right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        WaterGeometry.tube(out,pose,Vec3.ZERO,end,radius,.05F,.4F,1,fade*.25F);
        WaterGeometry.tube(out,pose,Vec3.ZERO,end,radius*.78,.1F,.8F,1,fade*.45F);
        WaterGeometry.tube(out,pose,Vec3.ZERO,end,radius*.35,.8F,.97F,1,fade*.75F);
        // Two visible spiral ribbons convey the stream's direction and pressure.
        for(int strand=0;strand<2;strand++)for(double z=0;z<length;z+=.8) {
            double z2=Math.min(length,z+.8),a=z*.75-age*.22+strand*Math.PI,b=z2*.75-age*.22+strand*Math.PI;
            Vec3 p=dir.scale(z).add(WaterGeometry.radial(right,up,a,radius*.9));
            Vec3 q=dir.scale(z2).add(WaterGeometry.radial(right,up,b,radius*.9));
            WaterGeometry.tube(out,pose,p,q,.055,.5F,.94F,1,fade*.6F);
        }
        for(double z=(age*.65)%5;z<length;z+=5)
            WaterGeometry.ring(out,pose,dir.scale(z),dir,radius*1.04,.05,0,fade*.65F);
        WaterGeometry.ring(out,pose,end,dir,radius,.12,0,fade);
    }
}
