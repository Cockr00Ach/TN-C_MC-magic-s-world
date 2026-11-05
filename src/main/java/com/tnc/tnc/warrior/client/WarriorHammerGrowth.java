package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import com.tnc.tnc.warrior.WarriorWeaponFamily;

/** Render-only growth, sampled in pose time so peak size precedes the authored downswing. */
public final class WarriorHammerGrowth {
    public static final float MAX_SCALE=3;
    private WarriorHammerGrowth() {}
    public static boolean applies(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold){
        if(family!=WarriorWeaponFamily.HAMMER || hold || animation==null)return false;
        return switch(animation){
            case HM_CRUSH,HM_QUAKE,HM_RUIN,OVERHEAD_SMASH,GROUND_QUAKE -> true;
            default -> false;
        };
    }
    public static float liftEnd(WarriorAnimation animation){
        return switch(animation){
            case HM_CRUSH -> .18f/.95f;
            case HM_QUAKE -> .34f/1.15f;
            default -> .34f;
        };
    }
    public static float scale(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold,float poseProgress){
        if(!applies(animation,family,hold) || !Float.isFinite(poseProgress))return 1;
        float t=Math.max(0,Math.min(1,poseProgress));
        float growth=smooth(t/liftEnd(animation));
        float recovery=1-smooth((t-.70f)/.30f);
        return 1+(MAX_SCALE-1)*growth*recovery;
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}

    /** Generic charge-release smashes had no lift track: add a real lift before their downswing. */
    static WarriorPoseMath.Pose fallbackPose(WarriorAnimation animation,float t,boolean leftHanded){
        var p=WarriorPoseMath.of(animation,t,leftHanded);
        if(animation!=WarriorAnimation.OVERHEAD_SMASH && animation!=WarriorAnimation.GROUND_QUAKE)return p;
        float arm=t<.34f?-.55f-2.30f*smooth(t/.34f):
                t<.60f?-2.85f+2.65f*smooth((t-.34f)/.26f):-.20f-.35f*smooth((t-.60f)/.40f);
        return new WarriorPoseMath.Pose(arm,p.rightArmY(),p.rightArmZ(),arm,p.leftArmY(),p.leftArmZ(),
                p.bodyYaw(),p.bodyPitch(),p.headYaw(),p.headPitch(),p.rightLegX(),p.leftLegX());
    }
}
