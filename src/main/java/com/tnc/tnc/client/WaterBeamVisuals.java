package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** One water shell and narrow flowing ribbons keep the aperture legible without nested full beams. */
final class WaterBeamVisuals {
    static void render(VertexConsumer body,VertexConsumer glow,Matrix4f pose,int tier,Vec3 dir,
                       double length,double age,float fade,boolean detail) {
        if(length<=0)return;
        double r=WaterSpellRules.radius(tier);
        Vec3 end=dir.scale(length),right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        int segments=detail?32:16;
        WaterGeometry.tube(body,pose,Vec3.ZERO,end,r,.025F,.42F,.96F,fade*.52F,segments);
        WaterGeometry.tube(glow,pose,Vec3.ZERO,end,r*.10,.55F,.92F,1,fade*.32F,detail?12:8);
        int strands=detail?3:2;
        double step=tier==5?(detail?4:8):(detail?2:4);
        for(int strand=0;strand<strands;strand++)for(double z=0;z<length;z+=step) {
            double z2=Math.min(length,z+step),a=z*.32-age*.24+strand*Math.PI*2/strands;
            double b=z2*.32-age*.24+strand*Math.PI*2/strands;
            Vec3 p=dir.scale(z).add(WaterGeometry.radial(right,up,a,r*.96));
            Vec3 q=dir.scale(z2).add(WaterGeometry.radial(right,up,b,r*.96));
            WaterGeometry.stroke(body,pose,dir,p,q,tier==5?.18:.035,.48F,.9F,1,fade*.7F);
        }
        double spacing=tier==5?16:8;
        for(double z=(age*1.4)%spacing;z<length;z+=spacing)
            WaterGeometry.ringColor(body,pose,dir.scale(z),dir,r,.06+r*.005,.42F,.86F,1,fade*.5F,segments);
        // A single translucent end cap; emission stays on its perimeter.
        WaterGeometry.disk(body,pose,end,dir,r,.08F,.52F,1,fade*.35F,segments);
        WaterGeometry.ringColor(glow,pose,end,dir,r,.06+r*.01,.65F,.95F,1,fade*.68F,segments);
        WaterGeometry.ringColor(body,pose,end,dir,r*1.15,.06+r*.004,.30F,.75F,1,fade*.45F,segments);
    }
}
