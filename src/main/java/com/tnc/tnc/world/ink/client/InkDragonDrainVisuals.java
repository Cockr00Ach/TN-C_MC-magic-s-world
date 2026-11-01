package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.world.ink.InkCelestialDragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

/** Long inwards-moving ink filaments clearly flow from the victim into the open mouth. */
final class InkDragonDrainVisuals {
    static void render(InkCelestialDragonEntity e,float partial,PoseStack pose,MultiBufferSource buffers){
        double age=e.moveAge()+partial;if(age<36||age>136)return;
        Vec3 from=e.effectVector("point").subtract(e.position()),mouth=e.mouthPosition().subtract(e.position());
        double fade=Math.min(1,(age-36)/12)*Math.min(1,(136-age)/12);
        for(int strand=0;strand<14;strand++){
            var path=new ArrayList<Vec3>();double phase=strand*Math.PI*2/14;
            for(int i=0;i<=18;i++){double t=i/18.0,travel=(age*.035+strand*.071)%1;
                Vec3 p=from.lerp(mouth,t);double radius=(1-t)*(1.2+Math.sin(t*Math.PI)*1.8),angle=phase+t*8-age*.12;
                path.add(p.add(Math.cos(angle)*radius,Math.sin(angle)*radius,.2*Math.sin(angle*2)));
            }InkCraneRitualVisuals.brushTrail(path,Vec3.ZERO,age+strand*.5,.075*fade,pose,buffers);
            double t=(age*.045+strand/14.0)%1;Vec3 bead=from.lerp(mouth,t);
            var trail=new ArrayList<Vec3>();for(int i=0;i<8;i++)trail.add(from.lerp(mouth,Math.max(0,t-i*.018)).add(0,Math.sin(phase+t*7)*(1-t),0));
            InkCraneRitualVisuals.brushTrail(trail,Vec3.ZERO,age,.22*fade,pose,buffers);
        }
    }
}
