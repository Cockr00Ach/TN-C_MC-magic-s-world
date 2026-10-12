package com.tnc.tnc.magic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_power.api.*;
public final class EarthAttackMechanics {
    private EarthAttackMechanics(){}
    public static void cast(ServerPlayer player,SpellInfo info,WindEarthVisualRules.Spec spec) {
        int tier=spec.tier(),kind=spec.kind();
        if(!((kind==3&&tier>=3)||(kind==4&&tier>=2)||(kind==5&&tier==5)))return;
        var aimed=TNAimRay.aimedEnemy(player,40);
        Vec3 target=aimed==null?player.pick(36,0,false).getLocation():aimed.position();
        Vec3 forward=new Vec3(player.getLookAngle().x,0,player.getLookAngle().z).normalize();
        if(forward.lengthSqr()<.001)forward=new Vec3(0,0,1);
        Vec3 side=new Vec3(-forward.z,0,forward.x);
        var power=SpellPower.getSpellPower(info.spell().school,player);
        DamageSource source=SpellDamageSource.create(info.spell().school,player);
        float damage=(float)(power.randomValue()*EarthActionRules.coefficient(kind,tier));
        if(kind==3&&tier>=3) {
            for(int i=0;i<EarthActionRules.shots(tier);i++) {
                if(tier==5)spawn(player,3,"earth_spear_sharp",target.add(0,125,0),target,25,70,8,35,damage,6,source);
                else {
                    Vec3 start=tier==4?player.position().add(0,3.5+(i/5)*1.8,0).add(side.scale((i%5-2)*1.8))
                            :player.getEyePosition().add(player.getLookAngle().scale(1.2));
                    spawn(player,tier==4?0:5,tier==4?"earth_spear_sharp":"earth_shell",start,target.add(0,.8,0),tier==4?1.4F:2F,tier==4?3F:2F,tier==4?20:i*6,100,damage,tier==4?2.5:3.5,source);
                }
            }
        } else if(kind==4&&tier>=2) {
            if(tier==5) {
                for(int i=0;i<5;i++) {
                    double a=i*Math.PI*2/5;Vec3 point=target.add(Math.cos(a)*2,0,Math.sin(a)*2);
                    spawn(player,6,"earth_spike_sharp",point.add(0,-6,0),point,2,6,0,130,damage,4,source);
                }
                spawn(player,2,"earth_orb",target.add(0,14,0),target.add(0,18,0),20,20,40,135,damage,9,source);
            } else for(int i=0;i<EarthActionRules.spikes(tier);i++) {
                int step=tier==4?i%6:i;double angle=tier==4?(i/6)*Math.PI/3:Math.atan2(forward.z,forward.x);
                Vec3 point=player.position().add(Math.cos(angle)*(tier==4?4+step*3.6:2+step*1.7),0,Math.sin(angle)*(tier==4?4+step*3.6:2+step*1.7));
                float width=tier==2?.8F+step*.25F:tier==3?1.6F:3.2F;
                float height=(tier==2?1.8F+step*.8F:3.6F+step*.6F)*(tier==4?2:1);
                spawn(player,1,"earth_spike_sharp",point,point,width,height,step*3,120,damage,width+1,source);
            }
        } else if(kind==5&&tier==5) {
            Vec3 centre=player.position();
            spawn(player,2,"earth_orb",centre.add(0,-6,0),centre.add(0,6,0),12,12,0,95,(float)(power.randomValue()*2),spec.radius(),source);
        }
    }
    private static void spawn(ServerPlayer caster,int mode,String model,Vec3 start,Vec3 end,float scale,float height,int wait,int ttl,float damage,double radius,DamageSource source) {
        var entity=WindEarthVisualMechanics.ATTACK.get().create(caster.serverLevel());if(entity==null)return;
        entity.configure(caster,mode,model,start,end,scale,height,wait,ttl,damage,radius,source);caster.serverLevel().addFreshEntity(entity);
        if(mode==2)entity.damageCentre=new Vec3(end.x,end.y-(wait>0?18:6),end.z);
    }
}
