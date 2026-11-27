package com.tnc.tnc.sanctum;

import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** One of the two wardens can patrol and threaten its aisle while the other works the floor lock. */
final class SanctumGateSentinel {
    final int side;
    final Mob body;
    boolean unlocked;
    private final SanctumRoute route;
    private final Vec3 home;
    private String stance="";
    private int age,cooldown;
    private Vec3 facing;
    String hand;
    private Vec3 bindToContact;
    SanctumGateSentinel(SanctumRoute route,int side,double hp) {
        this.route=route;this.side=side;this.home=route.foot(side*14,108);
        body=route.spawn(EntityType.IRON_GOLEM,side<0?"gate_left":"gate_right",side<0?"左庭门神 · 执印":"右庭门神 · 持戒",home,hp);
        facing=route.foot(side*8,100).subtract(home);
        SanctumGalleryFoe.look(body,facing,180);
        cooldown=side<0?48:28;
    }
    boolean alive(){return body.isAlive();}
    void cue(String action,int ticks){
        stance=action;age=0;SanctumAppearance.act(body,action,ticks);
    }
    void lockSide(Vec3 anchor){
        // At yaw 180 local +X points west; the baked fist rests at (side*128,0,+160)/16.
        int localX=(anchor.x-body.getX())>0?-1:1;
        hand=localX<0?"_L":"_R";
        bindToContact=new Vec3(-localX*(128.0/16)*.42,0,-(160.0/16)*.42);
    }
    Vec3 trackPalm(Vec3 marked){
        // Feet can approach a moving mark during the lift. The actual slam stays at the authored reach.
        Vec3 wanted=marked.subtract(bindToContact);var o=route.at(0,0,0);
        walk(new Vec3(Math.max(o.getX()-25,Math.min(o.getX()+26,wanted.x)),home.y,
                Math.max(o.getZ()+81,Math.min(o.getZ()+120,wanted.z))),.27);
        SanctumGalleryFoe.look(body,new Vec3(0,0,-1),12);
        return body.position().add(bindToContact);
    }
    private void warning(Vec3 p){route.level().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,p.x,p.y+.2,p.z,1,0,0,0,0);}
    void walk(Vec3 target,double maxSpeed){
        Vec3 d=target.subtract(body.position()).multiply(1,0,1);double distance=d.length();
        if(distance<.04)return;
        body.move(MoverType.SELF,d.scale(Math.min(distance,maxSpeed)/distance));body.setDeltaMovement(Vec3.ZERO);
    }
    void face(Vec3 target){SanctumGalleryFoe.look(body,target.subtract(body.position()),12);}
    void defend(List<ServerPlayer> players,boolean protectOtherAnchor){
        if(!alive())return;
        age++;
        var enemy=players.stream().min(Comparator.comparingDouble(p->p.distanceToSqr(body))).orElseThrow();
        if(stance.equals("gate_sweep")||stance.equals("gate_lash")){
            boolean sweep=stance.equals("gate_sweep");
            int impact=sweep?28:26;
            if(age%5==0&&age<impact){
                Vec3 start=body.position(),sideway=new Vec3(-facing.z,0,facing.x);
                if(sweep){for(int i=-3;i<=3;i++)warning(start.add(facing.scale(4)).add(sideway.scale(i)));}
                else for(int i=1;i<13;i++)warning(start.add(facing.scale(i)));
            }
            if(age==impact){
                route.sound(body.blockPosition(),sweep?SoundEvents.IRON_GOLEM_ATTACK:SoundEvents.CHAIN_HIT,.65f);
                for(var p:players){
                    Vec3 delta=p.position().subtract(body.position());
                    double forward=delta.dot(facing), cross=Math.abs(delta.x*facing.z-delta.z*facing.x);
                    if(protectOtherAnchor && p.distanceToSqr(route.foot(-side*8,100))<6*6)continue;
                    if(forward<0 || Math.abs(delta.y)>2.6)continue;
                    if(sweep?forward<=6.8&&cross<=3.8:forward<=13&&cross<1.35){
                        p.hurt(route.level().damageSources().mobAttack(body),sweep?4:3);
                        Vec3 knock=facing.scale(sweep?.43:-.29);p.push(knock.x,.18,knock.z);p.hurtMarked=true;
                    }
                }
            }
            if(age>=impact+14){cue("gate_guard",55);cooldown=48;}
            return;
        }
        if(!stance.equals("gate_guard"))cue("gate_guard",2400);
        double dx=Math.max(-4,Math.min(4,(enemy.getX()-home.x)*.42));
        double dz=Math.max(-7,Math.min(3,(enemy.getZ()-home.z)*.43));
        walk(home.add(dx,0,dz),.105);face(enemy.position());
        if(cooldown>0){cooldown--;return;}
        double range=enemy.distanceToSqr(body);
        boolean threatened=(!protectOtherAnchor||enemy.distanceToSqr(route.foot(-side*8,100))>=36);
        if(range<15*15 && threatened){
            facing=SanctumGalleryFoe.horizontal(enemy.position().subtract(body.position()));
            cue(range<7*7?"gate_sweep":"gate_lash",range<7*7?42:40);
            route.sound(body.blockPosition(),SoundEvents.CHAIN_PLACE,.60f);
        }
    }
    boolean braced(){return stance.equals("gate_guard")||stance.equals("gate_lamp");}
}

