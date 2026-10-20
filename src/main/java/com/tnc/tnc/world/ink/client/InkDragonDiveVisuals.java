package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Reusable age-driven visual sequence; no world entities or damage spawned by this renderer. */
final class InkDragonDiveVisuals {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png");
    private InkDragonDiveVisuals(){}
    static void render(double age,Vec3 highOrb,Vec3 target,InkCelestialDragonEntity dragon,PoseStack pose,MultiBufferSource buffers){
        var f=InkDragonDiveRules.sample(age,highOrb,target);
        if(age>=InkDragonDiveRules.HIT){
            if(age>=InkDragonDiveRules.END)return;
            pose.pushPose();try{
                pose.translate(target.x,target.y,target.z);
                double spread=(.5+f.burst()*3.8)*InkDragonCombatRules.BODY_SCALE;pose.scale((float)spread,(float)spread,(float)spread);
                InkDragonBreathVisuals.impact(age*1.7,pose,buffers);
                var ink=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
                InkCraneRitualVisuals.bloom(ink,pose.last(),f.burst()*18,10,(float)(1-f.burst()));
            }finally{pose.popPose();}return;
        }
        if(f.radius()<=0)return;
        pose.pushPose();try{
            pose.translate(f.orb().x,f.orb().y+12*InkDragonCombatRules.BODY_SCALE,f.orb().z);
            Vec3 heading=target.subtract(highOrb);float yaw=f.diving()&&heading.horizontalDistanceSqr()>.01?
                    (float)Math.toDegrees(Math.atan2(-heading.x,heading.z)):dragon.getYRot();
            pose.mulPose(Axis.YP.rotationDegrees(90-yaw));
            pose.translate(0,15*InkDragonCombatRules.BODY_SCALE,0);pose.mulPose(Axis.ZP.rotationDegrees(f.tilt()));pose.translate(0,-15*InkDragonCombatRules.BODY_SCALE,0);
            pose.scale(InkDragonDiveRules.PHANTOM_SCALE,InkDragonDiveRules.PHANTOM_SCALE,InkDragonDiveRules.PHANTOM_SCALE);
            var renderer=(InkCelestialDragonRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dragon);
            renderer.phantom(dragon,pose,buffers,f.alpha());
        }finally{pose.popPose();}
        pose.pushPose();try{
            pose.translate(f.orb().x,f.orb().y,f.orb().z);
            var ink=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
            InkCraneRitualVisuals.sphere(ink,pose.last(),f.radius(),0,age,1);
            // External ink flows converge visibly before launch; during dive they stream behind.
            for(int k=0;k<22;k++){
                List<Vec3> path=new ArrayList<>();double a=k*2.399963+age*.012;
                for(int i=0;i<=22;i++){
                    double t=i/22.0,r=f.radius()+Math.pow(1-t,1.5)*14;
                    double h=f.diving()?Math.pow(1-t,1.3)*20:Math.sin(t*Math.PI)*3+(1-t)*(k%5-2);
                    double turn=a+(1-t)*1.2;
                    path.add(new Vec3(Math.cos(turn)*r,h,Math.sin(turn)*r));
                }
                InkCraneRitualVisuals.brushTrail(path,Vec3.ZERO,age,.12+(k%3)*.045,pose,buffers);
            }
        }finally{pose.popPose();}
    }
}
