package com.tnc.tnc.sanctum;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.tnc.tnc.sanctum.SanctumTimings.*;

/** The flying body, marked ground lane and low wingtip share the same committed coordinates. */
final class FalseGodAerial {
    private final FalseGodFight f;
    private final Set<UUID> struck=new HashSet<>();
    private boolean left;
    private int passes,hooks;
    private Vec3 aim,source,entry,exit,landing,laneStart,laneEnd;
    FalseGodAerial(FalseGodFight fight){f=fight;}

    void begin(boolean wing) {
        left=((wing?passes++:hooks++)&1)==0;
        struck.clear();
        source=f.feet;
    }
    String side(){return left?"_l":"_r";}

    private static double ease(double n) {
        n=Math.min(1,Math.max(0,n));
        return n*n*(3-2*n);
    }
    private Vec3 tip(int t) {
        if(t<=WING_IMPACT)return laneStart.lerp(aim,Math.max(0,t-WING_IN)/(double)(WING_IMPACT-WING_IN));
        return aim.lerp(laneEnd,Math.min(t-WING_IMPACT,WING_OUT-WING_IMPACT)/(double)(WING_OUT-WING_IMPACT));
    }
    void wing(int t) {
        if(t<WING_LOCK){f.face(f.target.position(),6);return;}
        if(t==WING_LOCK) {
            // An opposite-corner runner can outrange one pass. Its warning never demands a cross-room blink.
            aim=f.clawReach(f.target.position(),48);
            var flank=f.stance(aim,source,12);
            var radial=flank.subtract(aim).normalize();
            var tangent=new Vec3(-radial.z,0,radial.x).scale(left?1:-1);
            entry=f.actorFloor(flank.subtract(tangent.scale(12)));
            exit=f.actorFloor(flank.add(tangent.scale(12)));
            laneStart=f.floor(aim.subtract(tangent.scale(9)));
            laneEnd=f.floor(aim.add(tangent.scale(9)));
            int duration=WING_OUT-WING_LOCK+1;
            f.mark("wing_from",laneStart,1.55,0,0xd73859,duration);
            f.mark("wing_center",aim,1.55,0,0xd73859,duration);
            f.mark("wing_to",laneEnd,1.55,0,0xd73859,duration);
            f.sound(SoundEvents.PHANTOM_FLAP,1,.64f);
        }
        if(t<=WING_IN) {
            double progress=(t-WING_LOCK)/(double)(WING_IN-WING_LOCK);
            // Curve around the viewer when a second player occupies the preferred flank.
            // A chord between those two sides would fly over the first player's head.
            double s=ease(progress);
            Vec3 radial=source.subtract(aim),destination=entry.subtract(aim);
            double angle=Math.atan2(radial.z,radial.x);
            double change=Math.atan2(destination.z,destination.x)-angle;
            angle+=Math.atan2(Math.sin(change),Math.cos(change))*s;
            double radius=radial.horizontalDistance()+(destination.horizontalDistance()-radial.horizontalDistance())*s;
            Vec3 point=aim.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
            f.place(f.actorFloor(point).add(0,9*ease(progress*1.65),0));
        } else if(t<=WING_OUT) {
            double progress=(t-WING_IN)/(double)(WING_OUT-WING_IN);
            f.place(entry.lerp(exit,progress).add(0,9-4.5*Math.sin(progress*Math.PI),0));
        } else if(t<=WING_LAND) {
            if(t==WING_OUT+1)landing=f.stance(aim,exit);
            double remain=WING_LAND-t+1;
            if(f.nearestDistance(landing)<14)landing=f.stance(aim,f.feet);
            Vec3 current=f.actorFloor(f.feet),delta=landing.subtract(current);
            Vec3 next=current.add(delta.scale(Math.min(1/remain,2/Math.max(.01,delta.length()))));
            double height=9*(1-ease((t-WING_OUT)/(double)(WING_LAND-WING_OUT)));
            if(f.nearestDistance(next)<9)height=Math.max(height,4.5);
            f.place(next.add(0,height,0));
        }
        f.face(aim,15);
        if(t>=WING_IN && t<=WING_OUT) {
            Vec3 from=tip(t-1),to=tip(t);
            f.expose(to,1);
            for(var p:f.players) {
                double distance=FalseGodRules.segment(p.getX(),p.getZ(),from.x,from.z,to.x,to.z);
                if(distance<=1.55 && Math.abs(p.getY()-to.y)<=2.5 && struck.add(p.getUUID())) {
                    f.hit(p,7,true);
                }
            }
            if(t==WING_IMPACT)f.sound(SoundEvents.PHANTOM_SWOOP,1,.68f);
        }
    }
    void hook(int t) {
        if(t<HOOK_LOCK) {
            f.advance(f.target,.50);
            return;
        }
        if(t==HOOK_LOCK) {
            aim=f.clawReach(f.target.position(), DEMON_REACH);
            f.mark("hook_tip",aim,PALM_RADIUS,0,0xcc2346,HOOK_IMPACT-HOOK_LOCK+1);
            f.sound(SoundEvents.IRON_GOLEM_REPAIR,.8f,.55f);
        }
        f.face(aim,9);
        if (t > HOOK_LOCK && t <= HOOK_IMPACT) {
            Vec3 delta = aim.subtract(f.feet);
            double step = Math.min(.72, delta.horizontalDistance() - PALM_REACH);
            if (step > 0) f.place(f.actorFloor(f.feet.add(delta.multiply(1, 0, 1).normalize().scale(step))));
        }
        if(t!=HOOK_IMPACT)return;
        f.expose(aim,HOOK_RELEASE-HOOK_IMPACT);
        f.mark("hook_open",aim,1.55,1.27,0xe4b58a,HOOK_RELEASE-HOOK_IMPACT);
        f.burst(aim,true);
        f.sound(SoundEvents.WARDEN_ATTACK_IMPACT,1,.75f);
        for(var p:f.players)if(FalseGodFight.flat(aim,p.position())<=PALM_RADIUS && Math.abs(p.getY()-aim.y)<3
                && f.hit(p,8,true)) {
            f.knock(p, f.feet, .22);
        }
    }
}
