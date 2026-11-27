package com.tnc.tnc.sanctum;

import net.minecraft.core.particles.ParticleTypes;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.tnc.tnc.sanctum.FalseGodFight.*;
import static com.tnc.tnc.sanctum.FalseGodRules.Move;

/** Per-cast locks never depend on the player's position after a telegraph has been committed. */
final class FalseGodFightMoves {
    private final FalseGodFight f;
    private final FalseGodAerial aerial;
    private final List<Vec3> marks=new ArrayList<>();
    private final Map<Integer,Set<UUID>> contact=new HashMap<>();
    private final Set<UUID> fastMarked=new HashSet<>();
    private Vec3 origin, aim, direction, end, actorEnd, grip, windup, clawStart, meteorLanding;
    private UUID captive,grabTarget;
    boolean exhausted;
    FalseGodFightMoves(FalseGodFight fight) {f=fight;aerial=new FalseGodAerial(f);}
    String action(Move move) {
        return move==Move.WINGCUT || move==Move.HOOK?move.action+aerial.side():move.action;
    }
    String contactAction(Move move) {
        return move==Move.GRAB && captive!=null?"grab_hit":action(move);
    }
    void clear() {resetMove();exhausted=false;}
    void resetMove() {
        marks.clear();contact.clear();fastMarked.clear();captive=null;grabTarget=null;grip=null;meteorLanding=null;
    }
    void begin(Move move) {
        resetMove();
        origin=f.floor(f.feet); aim=f.floor(f.target.position());direction=toward(origin,aim);end=aim;
        if(f.phase==1 && move==Move.WINGS) {
            // The statue faces the nave throughout its first form, including its frontal sweep.
            double yaw=Math.toRadians(f.body.getYRot());
            direction=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        } else if(f.phase==3) f.face(aim);
        switch(move) {
            case PILLARS -> {
                int pattern=f.pattern();
                addMark(aim);
                if(pattern==0) {
                    for(int i=-3;i<=3;i++)if(i!=0) {addMark(aim.add(i*5.5,0,0));addMark(aim.add(0,0,i*5.5));}
                } else if(pattern==1) {
                    for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0)addMark(aim.add(x*5.8,0,z*5.8));
                } else {
                    for(int r=1;r<=2;r++)for(int i=0;i<r*6;i++)
                        addMark(aim.add(polar(i*2*Math.PI/(r*6),r*6.5)));
                }
            }
            case TEARS -> {
                for(var p:f.players)addMark(f.floor(p.position()));
                // Two additional drops remain walkable even when a solo player hugs the outer ring.
                addMark(f.floor(aim.add(6,0,-3)));addMark(f.floor(aim.add(-5,0,5)));
            }
            case GRAB -> {
                grabTarget=f.target.getUUID();
                aim=f.clawReach(aim,36);
                Vec3 flank=f.stance(aim,origin);
                direction=toward(flank,aim);
                windup=f.actorFloor(flank.subtract(direction.scale(3.5)));
                actorEnd=f.actorFloor(flank.add(direction.scale(2.5)));
                clawStart=f.floor(aim.subtract(direction.scale(8)));
                end=f.floor(aim.add(direction.scale(2)));
            }
            case FUNERAL -> {
                f.message("千翼葬礼 · 外圈铜环外可躲过陨星");
                origin=f.feet;
            }
            case WINGCUT,HOOK -> aerial.begin(move==Move.WINGCUT);
            default -> {}
        }
    }
    private void addMark(Vec3 p) {
        if(Math.abs(p.x-f.center.x)<=71.01 && Math.abs(p.z-f.center.z)<=71.01
                && marks.stream().noneMatch(v->flat(v,p)<1.5))marks.add(p);
    }
    void run(Move move,int t) {
        switch(move) {
            case PILLARS -> pillars(t);
            case PRAYER -> prayer(t);
            case WINGS -> wings(t);
            case GAZE -> gaze(t);
            case TEARS -> tears(t);
            case SCREAM -> scream(t);
            case COMBO -> combo(t);
            case FIRE -> fire(t);
            case GRAB -> grab(t);
            case FUNERAL -> funeral(t);
            case DANCE -> dance(t);
            case WINGCUT -> aerial.wing(t);
            case HOOK -> aerial.hook(t);
        }
    }
    private static Vec3 polar(double angle,double distance) {return new Vec3(Math.cos(angle)*distance,0,Math.sin(angle)*distance);}
    private static Vec3 toward(Vec3 a,Vec3 b) {
        Vec3 d=b.subtract(a).multiply(1,0,1);
        return d.lengthSqr()<.0001?new Vec3(0,0,-1):d.normalize();
    }
    private static Vec3 turn(Vec3 dir,double angle) {
        return new Vec3(dir.x*Math.cos(angle)-dir.z*Math.sin(angle),0,dir.x*Math.sin(angle)+dir.z*Math.cos(angle));
    }
    private boolean first(int wave,ServerPlayer p) {
        return contact.computeIfAbsent(wave,k->new HashSet<>()).add(p.getUUID());
    }
    private void pillars(int t) {
        if(t==1)for(int i=0;i<marks.size();i++)f.mark("gold_"+i,marks.get(i),2,0,0xdeaa39,30);
        if(t<FalseGodRules.WARNING && (t==1||t%7==0))for(Vec3 p:marks)f.ring(p,2,11,GOLD);
        if(t==FalseGodRules.WARNING) {
            for(Vec3 p:marks) {
                f.line(p,p.add(0,13,0),.9,GOLD);f.burst(p,false);
                for(var victim:f.players)if(flat(p,victim.position())<=2 && first(0,victim))f.hit(victim,8,false);
            }
            f.sound(SoundEvents.BEACON_ACTIVATE,.85f,.75f);
        }
    }
    private void prayer(int t) {
        for(int i=0;i<3;i++) {
            int since=t-14-i*21;
            if(since<0 || since>51)continue;
            if(since==0) {
                f.sound(SoundEvents.AMETHYST_BLOCK_RESONATE,.85f,.75f+i*.12f);
                f.waveMark("prayer_"+i,origin,.83,40,0xffdc69);
            }
            wave(i,origin,since,.83,40,5,GOLD);
        }
    }
    private void wave(int id,Vec3 at,int ticks,double speed,double reach,int damage,
                      net.minecraft.core.particles.DustParticleOptions color) {
        double radius=ticks*speed;
        if(radius>reach)return;
        // Render the nearby arc densely enough to read a jump even on a low particle setting.
        if(ticks%2==0)for(var p:f.players) {
            double distance=flat(at,p.position());
            if(Math.abs(distance-radius)>12)continue;
            double angle=Math.atan2(p.getZ()-at.z,p.getX()-at.x);
            double half=distance<=4?Math.PI:Math.min(Math.PI,8/Math.max(1,radius));
            int count=Math.max(6,(int)Math.ceil(half*2*radius/.8));
            for(int j=0;j<=count;j++)f.dot(at.add(polar(angle-half+2*half*j/count,radius)),color);
        }
        for(var p:f.players) {
            if(FalseGodRules.wave(flat(at,p.position()),p.getY()-at.y,radius-speed,radius) && first(id,p)
                    && !com.tnc.tnc.warrior.WarriorComboService.consumePrecisionCounter(p,false))f.hit(p,damage,false);
        }
    }
    private void wings(int t) {
        Vec3 left=turn(direction,-Math.PI/2),right=turn(direction,Math.PI/2);
        if(t<16 && t%4==1){f.line(origin,origin.add(left.scale(12)),1.1,GOLD);f.line(origin,origin.add(right.scale(12)),1.1,GOLD);}
        if(t>=16 && t<=43) {
            double angle=-Math.PI/2+Math.PI*(t-16)/27;
            Vec3 tip=origin.add(turn(direction,angle).scale(12));
            f.line(origin.add(0,.65,0),tip.add(0,.65,0),.85,PALE);
            for(var p:f.players)if(segment(p,origin,tip)<=1.4 && Math.abs(p.getY()-origin.y)<3 && first(0,p)){
                f.hit(p,5,true);f.knock(p,origin,.95);
            }
        }
        if(t>=47 && t<=80)for(int i=-3;i<=3;i++) {
            Vec3 ray=turn(direction,i*.245);
            if(t==47 || t==52)f.line(origin.add(ray.scale(3)),origin.add(ray.scale(21)),2.3,GOLD);
            int flying=t-55;
            if(flying<0)continue;
            Vec3 a=origin.add(ray.scale(flying*1.4)),b=a.add(ray.scale(1.4));
            f.line(a.add(0,.9,0),b.add(0,.9,0),.6,PALE);
            for(var p:f.players)if(segment(p,a,b)<=.85 && Math.abs(p.getY()-a.y)<2 && first(1,p))f.hit(p,5,true);
        }
    }
    private void gaze(int t) {
        if(t<FalseGodRules.WARNING && t%5==0) {
            for(var p:f.players)f.dot(p.getEyePosition().add(p.getLookAngle().scale(1.5)),BLOOD);
            if(t==20)f.sound(SoundEvents.BEACON_POWER_SELECT,.7f,1.8f);
        }
        if(t!=FalseGodRules.WARNING)return;
        for(var p:f.players) {
            // Minecraft yaw is authoritative for body-facing even when the camera is pitched straight up.
            double yaw=Math.toRadians(p.getYRot());
            if(FalseGodRules.backTurned(-Math.sin(yaw),Math.cos(yaw),f.feet.x-p.getX(),f.feet.z-p.getZ()))continue;
            f.failedGaze(p);
        }
    }
    private void tears(int t) {
        if(t==1)for(int i=0;i<marks.size();i++)f.mark("drop_"+i,marks.get(i),FalseGodRules.POOL_RADIUS,0,0xb8204b,27);
        if(t<28 && (t==1||t%5==0))for(var at:marks) {
            f.ring(at,FalseGodRules.POOL_RADIUS,14,BLOOD);
            f.level.sendParticles(net.minecraft.core.particles.ParticleTypes.DRIPPING_DRIPSTONE_LAVA,
                    at.x,at.y+5,at.z,6,.85,.8,.85,0);
        }
        if(t==28)for(var at:marks) {
            // Retain every stained area until the telegraphed scream detonates it.
            if(f.pools.stream().noneMatch(p->flat(p,at)<1.4))f.pools.add(at);
            f.burst(at,true);
            for(var p:f.players)if(flat(p.position(),at)<=2 && first(0,p))f.hit(p,3,false);
        }
    }
    private void scream(int t) {
        if(t==1)for(int i=0;i<f.pools.size();i++)f.mark("thorn_"+i,f.pools.get(i),3.3,0,0xfa3c4e,30);
        if(t<=30 && t%4==1)for(var p:f.pools) {
            f.ring(p,3.3,16,PALE);f.dot(p.add(0,.45,0),BLOOD);
        }
        if(t!=30)return;
        f.sound(SoundEvents.WARDEN_SONIC_BOOM,.75f,1.3f);
        for(var victim:f.players)if(flat(origin,victim.position())<6){f.hit(victim,3,false);f.knock(victim,origin,1.15);}
        for(var at:f.pools) {
            for(int k=0;k<7;k++) {
                Vec3 root=at.add(polar(k*2*Math.PI/7,2));
                f.line(root,root.add(toward(at,root).scale(.8)).add(0,3.5,0),.5,BLOOD);
            }
            for(var victim:f.players)if(flat(victim.position(),at)<=3.3 && first(0,victim))f.hit(victim,7,false);
        }
        f.pools.clear();
    }
    private double segment(ServerPlayer p,Vec3 a,Vec3 b) {
        return FalseGodRules.segment(p.getX(),p.getZ(),a.x,a.z,b.x,b.z);
    }
    private void lockSlash(int index) {
        aim=f.floor(f.target.position());
        direction=toward(f.feet,aim);
        if(f.phase==2) {
            var candidate=f.players.stream().filter(this::withinFastReach)
                    .min(java.util.Comparator.comparingDouble(p->p.distanceToSqr(f.target))).orElse(null);
            if(candidate!=null){aim=f.floor(candidate.position());direction=toward(f.feet,aim);}
            else aim=f.floor(f.feet.add(direction.scale(SanctumTimings.FAST_CUT_RANGE)));
        }
        Vec3 angle=turn(direction,(index%2==0?1:-1)*.78);
        origin=f.floor(aim.subtract(angle.scale(5.4)));
        end=f.floor(aim.add(angle.scale(5.4)));
        if(f.phase==2) {
            fastMarked.clear();
            for(var p:f.players)if(withinFastReach(p) && segment(p,origin,end)<=1.1) {
                fastMarked.add(p.getUUID());
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("腕上浮现血线　·　快刀已锁定，举盾！"),true);
            }
        }
        if(f.phase==3) {
            // Cross the marked ground with the claws while the demon steps laterally behind them.
            double side=(index%2==0?1:-1)*.35;
            f.place(f.stance(aim,aim.subtract(turn(direction,side).scale(SanctumTimings.DEMON_REACH))));
            actorEnd=f.stance(aim,aim.subtract(turn(direction,-side).scale(SanctumTimings.DEMON_REACH)));
        }
        if(f.phase==3)f.face(aim);
    }
    private boolean withinFastReach(ServerPlayer p) {
        return flat(f.feet,p.position())<=SanctumTimings.FAST_CUT_RANGE && Math.abs(p.getY()-f.center.y)<3;
    }
    private void dashSlash(int id,float dmg,double width) {
        f.line(origin.add(0,.85,0),end.add(0,.85,0),.48,EMBER);
        if(f.phase==2) {
            for(var p:f.players)if(fastMarked.contains(p.getUUID())) {
                f.line(aim.add(0,.9,0),p.position().add(0,1,0),.8,BLOOD);
                f.lockedCut(p);
            }
            fastMarked.clear();
        } else {
            for(var p:f.players)if(segment(p,origin,end)<=width && Math.abs(p.getY()-origin.y)<3 && first(id,p))f.hit(p,dmg,true);
            f.place(actorEnd);f.face(aim);
        }
    }
    private void combo(int t) {
        if(t==1||t==SanctumTimings.LOCK_TWO) {
            int side=t==1?0:1;
            lockSlash(side);
            for(int i=0;i<=8;i++)f.mark("wing_cut_"+side+"_"+i,origin.lerp(end,i/8.0),1.1,0,
                    0xa31637,side==0?SanctumTimings.CUT_ONE-1:SanctumTimings.CUT_TWO-t);
        }
        if(t<SanctumTimings.CUT_TWO && t%3==1)f.line(origin,end,1,BLOOD);
        if(t==SanctumTimings.CUT_ONE||t==SanctumTimings.CUT_TWO) {
            Vec3 palm=f.feet.add(direction.scale(8)).add(0,9,0);
            f.line(palm,aim.add(0,.5,0),.9,BLOOD);
            dashSlash(t,4,1.1);f.sound(SoundEvents.PLAYER_ATTACK_SWEEP,.9f,t==SanctumTimings.CUT_ONE?.85f:1.12f);
        }
        if(t==SanctumTimings.LOCK_HEAVY) {
            aim=f.floor(f.target.position());
            f.mark("heavy_hand",aim,3.7,0,0xb61739,SanctumTimings.HEAVY_IMPACT-t);
        }
        if(t>=SanctumTimings.LOCK_HEAVY && t<SanctumTimings.HEAVY_IMPACT && t%4==0)f.ring(aim,3.7,22,BLOOD);
        if(t==SanctumTimings.HEAVY_IMPACT) {
            f.frenzyImpact();
            f.expose(aim,65);f.burst(aim,true);f.sound(SoundEvents.IRON_GOLEM_DAMAGE,1,.6f);
            f.line(f.feet.add(toward(f.feet,aim).scale(7)).add(0,11,0),aim,.75,EMBER);
            for(var p:f.players)if(flat(aim,p.position())<=3.7 && Math.abs(p.getY()-aim.y)<=3.2)f.hit(p,8,true);
        }
        if(t<=SanctumTimings.HEAVY_IMPACT)f.face(aim,14);
    }
    private void fire(int t) {
        int index=(t-1)/20;
        if(index<5 && (t-1)%20==0) {
            ServerPlayer p=f.players.get((f.players.indexOf(f.target)+index)%f.players.size());
            marks.add(f.floor(p.position()));
            f.mark("fire_"+index,marks.get(index),2.35,0,0xff354b,22);
        }
        for(int i=0;i<marks.size();i++) {
            Vec3 at=marks.get(i);
            int since=t-(1+i*20);
            if(since<22 && since%4==0)f.ring(at,2.35,13,EMBER);
            if(since==22) {
                f.waveMark("firewave_"+i,at,.64,16,0xe83b41);
                f.line(at,at.add(0,14,0),.65,EMBER);f.burst(at,true);
                f.sound(SoundEvents.BLAZE_SHOOT,.7f,.7f);
                for(var p:f.players)if(flat(at,p.position())<=2.35)f.hit(p,7,false);
            }
            if(since>=22)wave(i,at,since-22,.64,16,4,EMBER);
        }
        if(t>3 && t%2==0)f.advance(f.target,.49);
    }
    private void grab(int t) {
        if(t<=22) {
            double u=t/22.0;f.place(origin.lerp(windup,u*u*(3-2*u)));f.face(aim,12);
            if(t==1) {
                f.mark("grab_tip",aim,1.35,0,0xd73141,29);
                for(int i=0;i<=8;i++)f.mark("grab_lane"+i,clawStart.lerp(end,i/8.0),1.35,0,0xb9243a,29);
            }
            if(t==1||t%5==0)f.line(clawStart,end,.8,BLOOD);
        }
        if(t>=23 && t<=29 && captive==null) {
            Vec3 previous=clawStart.lerp(end,(t-23)/7.0), next=clawStart.lerp(end,(t-22)/7.0);
            f.place(windup.lerp(actorEnd,(t-22)/7.0));f.face(aim);
            f.line(previous.add(0,1.3,0),next.add(0,1.3,0),.6,EMBER);
            for(var p:f.players)if(grabTarget.equals(p.getUUID()) && !p.isSpectator() && !p.isCreative()
                    && segment(p,previous,next)<1.35 && Math.abs(p.getY()-origin.y)<2.4) {
                if(f.hit(p,2,false)) {captive=p.getUUID();grip=f.floor(p.position());f.place(actorEnd);
                    f.mark("grab_grip",grip,1,.85,0xc43158,49-t);
                    f.expose(grip,50-t);f.sound(SoundEvents.WARDEN_HEARTBEAT,.9f,.75f);break;}
            }
        }
        if(captive==null)return;
        ServerPlayer held=f.players.stream().filter(p->p.getUUID().equals(captive)).findFirst().orElse(null);
        if(held==null || !held.isAlive() || held.isSpectator() || held.isCreative()
                || DownedCombat.isDowned(held)){captive=null;return;}
        Vec3 point=grip;
        if(t<49) {
            held.teleportTo(point.x,point.y+3.15,point.z);held.fallDistance=0;
            if(t==39) {
                float before=held.getHealth()+held.getAbsorptionAmount();
                if(f.hit(held,4,false))f.body.heal(Math.max(0,before-held.getHealth()-held.getAbsorptionAmount()));
            }
        } else {
            held.teleportTo(point.x,point.y,point.z);held.fallDistance=0;
            f.hit(held,6,true);f.burst(point,true);captive=null;
        }
    }
    private void funeral(int t) {
        if(t<=24)f.place(origin.lerp(f.center,(double)t/24).add(0,12*(double)t/24,0));
        if(t>=12 && t<=110) {
            int salvo=(t-12)/21, local=(t-12)%21;
            if(salvo<4)for(int i=0;i<9;i++) {
                double a=(i+(salvo%2)*.5)*Math.PI*2/9;
                Vec3 near=f.center.add(polar(a,4)),far=f.center.add(polar(a,32));
                if(local==0 || local==7)f.line(near,far,1.65,EMBER);
                if(local>=5 && local<14 && local%2==1) {
                    // Stagger different ranks in the same salvo while their ground lanes stay fixed.
                    double height=(14-local)*1.45;
                    for(int d=4+((local+i)%3)*2;d<=32;d+=6) {
                        Vec3 shard=f.center.add(polar(a,d));
                        f.level.sendParticles(EMBER,shard.x+.05*height,shard.y+height,shard.z-.08*height,
                                0,-.05,-1,.08,1.4);
                    }
                }
                if(local==14) {
                    for(int d=4;d<=32;d+=3) {
                        Vec3 base=f.center.add(polar(a,d));
                        f.line(base,base.add(-.75,3,.4),.85,PALE);
                    }
                    for(var p:f.players)if(segment(p,near,far)<=1.08 && first(salvo,p))f.hit(p,5,false);
                }
            }
        }
        if(t==65 || t==92)f.message("陨星将坠　·　离开中央二十四格红圈");
        if(t==45)f.mark("meteor",f.center,FalseGodRules.METEOR_RADIUS,0,0xab142d,85);
        if(t>=45 && t<=125 && t%5==0) f.ring(f.center,FalseGodRules.METEOR_RADIUS,80,EMBER);
        if(t>=112&&t<=130) {
            // The blast is still centred on the red disc; the demon banks past a party standing underneath it.
            if(meteorLanding==null || f.nearestDistance(meteorLanding)<12)
                meteorLanding=f.nearestDistance(f.center)>=12?f.center:f.stance(f.center,origin);
            Vec3 now=f.floor(f.feet),delta=meteorLanding.subtract(now);
            f.place(now.add(delta.scale(Math.min(1.0/(131-t),2.4/Math.max(.01,delta.length()))))
                    .add(0,(130-t)*12.0/18,0));
        }
        if(t==130) {
            f.sound(SoundEvents.GENERIC_EXPLODE,.9f,.6f);
            f.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,f.center.x,f.center.y+1,f.center.z,1,0,0,0,0);
            boolean countered=false;
            for(var p:f.players) {
                double distance=flat(p.position(),f.center);
                if(distance>=FalseGodRules.METEOR_RADIUS)continue;
                if(distance<=5.8 && f.counter(p,f.center))countered=true;
                else {f.hit(p,12,false);f.knock(p,f.center,1.1);}
            }
            if(countered)f.stagger(52);
        }
    }
    private void dance(int t) {
        if(t>=5 && t<=53 && (t-5)%12==0) {
            int index=(t-5)/12;
            lockSlash(index);
            f.mark("dance_cut_"+index,aim,1.15,0,0xb9243a,12);
        }
        if(t>=5 && t<=64 && (t-5)%4==0)f.line(origin,end,.8,EMBER);
        int slash=FalseGodRules.danceDash(t);
        if(slash>=0) dashSlash(slash,4,1.15);
        if(t==SanctumTimings.DANCE_LOCK) {
            aim=f.floor(f.target.position());origin=f.feet;
            actorEnd=f.stance(aim,origin);
            f.face(aim);f.message("末斩 · 红环凝实时，闪出或弹反");
            f.mark("final_cut",aim,4.2,0,0xff3046,42);
        }
        if(t>=SanctumTimings.DANCE_LOCK && t<SanctumTimings.DANCE_IMPACT) {
            double progress=Math.min(1,(t-70)/14.0);
            double height=t<99?5.5:5.5*(112-t)/13;
            f.place(origin.lerp(actorEnd,progress).add(0,height,0));
            if(t%4==0){f.ring(aim,4.2,22,EMBER);f.ring(aim,Math.max(.3,4.2*(112-t)/42),14,PALE);}
        }
        if(t==SanctumTimings.DANCE_IMPACT) {
            f.frenzyImpact();
            f.place(actorEnd);f.face(aim);f.expose(aim,FalseGodRules.EXHAUSTION);
            f.line(f.feet.add(0,6,0),aim,.7,EMBER);f.burst(aim,true);f.sound(SoundEvents.ANVIL_LAND,1,.65f);
            boolean success=false;
            for(var p:f.players) {
                if(flat(p.position(),aim)>4.2 || Math.abs(p.getY()-aim.y)>3.2) { if(p==f.target) success=true; continue; }
                if(f.counter(p))success=true;
                else f.hit(p,11,true);
            }
            if(success) {
                f.mark("final_open",aim,1.55,1.27,0xe4b58a,FalseGodRules.EXHAUSTION);
                f.stagger(FalseGodRules.EXHAUSTION);
            }
        }
    }
}
