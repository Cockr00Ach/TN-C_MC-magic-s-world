package com.tnc.tnc.sanctum;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Positions and pose phase start together. There is no trip back to a spawn coordinate after impact. */
final class SanctumGalleryFoe {
    final Mob body;
    final boolean stone;
    private final SanctumRoute route;
    private enum Motion { FLIGHT, TAKEOFF, DIVE, LANDED, CLIMB, STALK, LUNGE, REGAIN, VEIL, FLINCH }
    private Motion motion;
    private int age, duration, nextVeil, tick, turn, recoilTicks;
    private Vec3 start, end, control, prey, away=Vec3.ZERO;
    private final Set<UUID> struck=new HashSet<>();

    SanctumGalleryFoe(SanctumRoute route,Mob body,boolean stone,int delay) {
        this.route=route;this.body=body;this.stone=stone;
        nextVeil=90+delay; turn=delay==0?1:-1;
        if(!stone)begin(Motion.STALK,"stalk",2400);
        else if(delay>0)patrol(body.position().add(0,0,5),delay);
        else takeoff();
    }
    private void begin(Motion next,String cue,int ticks) {
        motion=next;age=0;duration=ticks;start=body.position();struck.clear();
        SanctumAppearance.act(body,cue,ticks);
    }
    private Vec3 floor(Vec3 p) {
        var o=route.at(0,0,0);
        return new Vec3(Mth.clamp(p.x,o.getX()-5.8,o.getX()+6.8),o.getY(),
                Mth.clamp(p.z,o.getZ()+20.2,o.getZ()+51.8));
    }
    static void look(Mob mob,Vec3 direction,float degreesPerTick) {
        if(direction.horizontalDistanceSqr()<.0001)return;
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        yaw=mob.getYRot()+Mth.clamp(Mth.wrapDegrees(yaw-mob.getYRot()),-degreesPerTick,degreesPerTick);
        mob.setYRot(yaw);mob.setYBodyRot(yaw);mob.setYHeadRot(yaw);
    }
    static Vec3 horizontal(Vec3 d) {return new Vec3(d.x,0,d.z).normalize();}
    static float smooth(double t){return (float)(t*t*(3-2*t));}
    private void moveTo(Vec3 goal,double speed,boolean face) {
        Vec3 d=goal.subtract(body.position());double l=d.length();
        if(face)look(body,d,18);
        if(l>.0001)body.move(MoverType.SELF,l>speed?d.scale(speed/l):d);
        body.setDeltaMovement(Vec3.ZERO);
    }
    private Vec3 arc(double t) {return start.lerp(control,t).lerp(control.lerp(end,t),t);}
    private void takeoff() {
        begin(Motion.TAKEOFF,"stone_windup",40);prey=null;
    }
    private void patrol(Vec3 target,int ticks){
        begin(Motion.FLIGHT,"stone_flight",ticks);
        Vec3 toward=horizontal(target.subtract(start));
        if(toward.lengthSqr()<.01)toward=horizontal(body.getLookAngle());
        Vec3 bank=new Vec3(-toward.z,0,toward.x).scale(3*turn);
        control=floor(start.add(bank)).add(0,5.4,0);
        end=floor(start.add(bank).add(toward.scale(3.2))).add(0,3.8,0);
    }
    void tick(List<ServerPlayer> players,ServerPlayer target) {
        tick++;age++;
        if(stone){
            stone(players,target);
            if(recoilTicks>0){
                Vec3 before=body.position();body.move(MoverType.SELF,away.scale(.11*recoilTicks/7.0));
                Vec3 shift=body.position().subtract(before);start=start.add(shift);
                if(motion!=Motion.DIVE&&end!=null)end=end.add(shift);
                if(control!=null)control=control.add(shift);
                recoilTicks--;
            }
        }else shade(players,target);
    }
    private void stone(List<ServerPlayer> players,ServerPlayer target) {
        switch(motion){
            case FLIGHT -> {
                moveTo(arc((double)age/duration),.38,true);
                if(age>=duration)takeoff();
            }
            case TAKEOFF -> {
                if(age==1){
                    Vec3 heading=horizontal(target.position().subtract(start));
                    Vec3 side=new Vec3(-heading.z,0,heading.x).scale(turn*1.8);
                    end=floor(start.add(heading.scale(2.5)).add(side)).add(0,4.5,0);
                    control=start.add(side.scale(1.6)).add(0,1.8,0);
                }
                if(age>=6)moveTo(arc(smooth((age-6)/34.0)),.48,false);
                if(age>=20&&age<=34){
                    Vec3 dest=floor(target.position().add(target.getDeltaMovement().scale(4)));
                    Vec3 delta=dest.subtract(body.position()).multiply(1,0,1);
                    prey=floor(body.position().add(delta.length()>10?delta.normalize().scale(10):delta));
                }
                look(body,(age>=34?prey:target.position()).subtract(body.position()),12);
                if(prey!=null && age%5==0)route.ring(prey,1.9,false);
                if(age>=duration){
                    begin(Motion.DIVE,"stone_dive",18);end=prey;control=start.lerp(end,.67).add(0,2,0);
                    route.sound(body.blockPosition(),SoundEvents.PHANTOM_SWOOP,.72f);
                }
            }
            case DIVE -> {
                double t=(double)age/duration;moveTo(arc(t*t),1.45,true);
                if(age>=duration){
                    begin(Motion.LANDED,"stone_land",50);
                    Vec3 contact=body.position();
                    route.sound(body.blockPosition(),SoundEvents.DEEPSLATE_BREAK,.72f);route.ring(contact,2,false);
                    for(var p:players)if(p.position().distanceToSqr(contact)<=6.25)
                        p.hurt(route.level().damageSources().mobAttack(body),5);
                    body.setGlowingTag(true);
                }
            }
            case LANDED -> {
                moveTo(floor(body.position()),.30,false);
                if(age>=duration)climb(target.position());
            }
            case CLIMB -> {
                double t=smooth((double)age/duration);moveTo(arc(t),.51,true);
                if(age>=duration)patrol(target.position(),48);
            }
            default -> {}
        }
    }
    private void climb(Vec3 threat) {
        body.setGlowingTag(false);begin(Motion.CLIMB,"stone_climb",32);
        Vec3 retreat=horizontal(body.position().subtract(threat));
        if(retreat.lengthSqr()<.01)retreat=horizontal(body.getLookAngle()).scale(-1);
        turn=-turn;
        Vec3 side=new Vec3(-retreat.z,0,retreat.x).scale(3*turn);
        end=floor(start.add(retreat.scale(3.4)).add(side)).add(0,3.7,0);
        control=floor(start.add(retreat.scale(2.0))).add(0,1.45,0);
    }
    private void shade(List<ServerPlayer> players,ServerPlayer target) {
        switch(motion){
            case FLINCH -> recoiling();
            case STALK -> {
                boolean seekLamp=tick>=nextVeil;
                Vec3 goal=seekLamp?route.foot(4,48):floor(target.position());
                Vec3 d=goal.subtract(body.position());
                if(seekLamp && d.horizontalDistanceSqr()<1.8){
                    begin(Motion.VEIL,"shade_veil",40);route.hint("锁链绷紧了 · 击中捂灯的影鬼可打断它");
                    route.sound(body.blockPosition(),SoundEvents.CHAIN_HIT,.6f);
                }else if(!seekLamp && age>=18 && d.horizontalDistanceSqr()<5.2*5.2){
                    begin(Motion.LUNGE,"shade_lunge",24);
                    Vec3 heading=horizontal(d);if(heading.lengthSqr()<.01)heading=horizontal(body.getLookAngle());
                    end=floor(start.add(heading.scale(Math.min(4.2,d.horizontalDistance()+.6))));
                    control=start.lerp(end,.55).add(0,1.28,0);look(body,d,60);
                    route.sound(body.blockPosition(),SoundEvents.CHAIN_STEP,.57f);
                }else moveTo(goal,.17,true);
            }
            case VEIL -> {
                if(age>=duration){route.coverLamp();nextVeil=tick+145;begin(Motion.REGAIN,"shade_recover",14);}
            }
            case LUNGE -> {
                if(age>=11){
                    double t=Math.min(1,(age-10)/11.0);Vec3 was=body.position();moveTo(arc(t),.62,false);
                    if(age>=14&&age<=16){
                        Vec3 reach=body.position().add(horizontal(end.subtract(start)).scale(1.93));
                        for(var p:players){
                            if(p.position().distanceToSqr(reach)<3.0 && Math.abs(p.getY()-was.y)<1.8 && struck.add(p.getUUID()))
                                p.hurt(route.level().damageSources().mobAttack(body),4);
                        }
                    }
                }
                if(age>=duration)begin(Motion.REGAIN,"shade_recover",14);
            }
            case REGAIN -> {moveTo(floor(body.position()),.28,false);if(age>=duration)begin(Motion.STALK,"stalk",2400);}
            default -> {}
        }
    }
    void hurt(LivingHurtEvent event) {
        if(stone&&motion==Motion.LANDED)event.setAmount(event.getAmount()*1.5f);
        if(motion==Motion.FLINCH || event.getAmount()<=0)return;
        Vec3 source=event.getSource().getSourcePosition();
        away=source==null?horizontal(body.getLookAngle()).scale(-1):horizontal(body.position().subtract(source));
        if(stone){recoilTicks=7;return;}
        begin(Motion.FLINCH,"shade_hit",10);
        nextVeil=Math.max(nextVeil,tick+48);
    }
    private void recoiling() {
        double t=(double)age/duration;
        double y=Math.max(route.at(0,0,0).getY(),start.y+Math.sin(Math.PI*t)*.20-Math.min(.50,start.y-route.at(0,0,0).getY())*t);
        Vec3 landing=floor(start.add(away.scale(.6*smooth(t))));
        moveTo(new Vec3(landing.x,y,landing.z),.30,false);
        if(age>=duration)begin(Motion.REGAIN,"shade_recover",14);
    }
}

