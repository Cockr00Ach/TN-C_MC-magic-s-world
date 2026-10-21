package com.tnc.tnc.world.ink;

import net.minecraft.world.phys.Vec3;

/** Shared deterministic timeline. Visual study only, not yet wired to server combat. */
public final class InkDragonDiveRules {
    public static final float PHANTOM_SCALE=3;
    public static final double ORB_RADIUS=9.5*InkDragonCombatRules.BODY_SCALE;
    public static final int FORM=40,DIVE=80,HIT=110,END=150;
    public record Frame(Vec3 orb,double radius,float alpha,float tilt,double burst,boolean diving){}
    private InkDragonDiveRules(){}
    public static Frame sample(double age,Vec3 highOrb,Vec3 target){
        double charge=clamp((age-FORM)/(DIVE-FORM));
        double dive=clamp((age-DIVE)/(HIT-DIVE));
        double ease=dive*dive;
        Vec3 position=highOrb.lerp(target,ease);
        float alpha=age<FORM?0:age<HIT?(float)(.16+.15*charge):0;
        double radius=age<FORM?0:age<HIT?ORB_RADIUS*Math.sqrt(charge):0;
        return new Frame(position,radius,alpha,(float)(65*Math.min(1,dive*3)),clamp((age-HIT)/(END-HIT)),age>=DIVE&&age<HIT);
    }
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
}
