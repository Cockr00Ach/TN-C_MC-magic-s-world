package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public final class WindChainRenderers {
    public static final class Ball extends EntityRenderer<WindBallEntity>{
        public Ball(EntityRendererProvider.Context c){super(c);}
        @Override public ResourceLocation getTextureLocation(WindBallEntity e){return TextureAtlas.LOCATION_BLOCKS;}
        @Override public void render(WindBallEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
            double age=e.level().getGameTime()+partial;float r=(float)e.radius(partial);
            p.pushPose();p.scale(r,r,r);
            if(e.phase()==2){p.mulPose(Axis.XP.rotationDegrees(80));p.mulPose(Axis.ZP.rotationDegrees((float)(age*8%360)));WindConstructModels.draw("cage",p,b,(float)Math.max(0,1-e.phaseAge(partial)/12));}
            else {p.translate(0,-.2,0);WindConstructModels.orb(2,age,p,b);}
            p.popPose();
        }
    }
    public static final class Volley extends EntityRenderer<WindVolleyEntity>{
        public Volley(EntityRendererProvider.Context c){super(c);}
        @Override public ResourceLocation getTextureLocation(WindVolleyEntity e){return TextureAtlas.LOCATION_BLOCKS;}
        @Override public void render(WindVolleyEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
            double age=e.visualAge(partial);float alpha=(float)Math.min(1,age/8);
            if(e.tier()>=1){p.pushPose();var marker=e.aimPoint().subtract(e.position());p.translate(marker.x,marker.y+.05,marker.z);WindSolidModels.seal(e.tier()==5?34:5+e.tier(),age,alpha,p,b);p.popPose();}
            for(int slot=0;slot<e.count();slot++){
                double end=e.endTime(slot);
                if(!e.visible(slot,age)||e.done(slot)&&age<end)continue;
                WindWeaponTrails.volley(e,slot,age,p,b,alpha);
                var at=e.weaponAt(slot,age).subtract(e.position());
                var direction=e.direction(slot,age);
                if(direction.lengthSqr()<.001)direction=new net.minecraft.world.phys.Vec3(0,-1,0);
                p.pushPose();p.translate(at.x,at.y,at.z);
                p.mulPose(new Quaternionf().rotationTo(0,0,1,(float)direction.x,(float)direction.y,(float)direction.z));
                float scale=WindChainRules.modelScale(e.tier());p.scale(scale,scale,scale);
                float fade=(float)Math.max(0,Math.min(1,(end+8-age)/8));
                WindConstructModels.phantomWeapon(WindChainRules.form(e.tier(),slot),age,p,b,alpha*fade,true);p.popPose();
            }
            // A bounded set of inward cutting currents, rather than making the weapons lunge.
            if(e.tier()<5&&age>=38&&age<100){
                int cuts=Math.min(6,e.count());
                for(int i=0;i<cuts;i++){
                    int slot=i*e.count()/cuts;double t=((age-38+i*2)%12)/12;
                    var start=e.orbitPoint(slot,age);var end=e.aimPoint().add(0,1.3,0);
                    var at=start.lerp(end,t).subtract(e.position());var direction=end.subtract(start).normalize();
                    p.pushPose();p.translate(at.x,at.y,at.z);
                    p.mulPose(new Quaternionf().rotationTo(0,0,1,(float)direction.x,(float)direction.y,(float)direction.z));
                    float s=.65F+e.tier()*.16F;p.scale(s,s,s);
                    WindConstructModels.draw("crescent",p,b,(float)(Math.sin(t*Math.PI)*.55));p.popPose();
                }
            }
        }
    }
}
