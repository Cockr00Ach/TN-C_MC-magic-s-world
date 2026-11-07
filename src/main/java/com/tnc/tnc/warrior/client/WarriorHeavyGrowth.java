package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import com.tnc.tnc.warrior.WarriorWeaponFamily;

/** All heavy melee weapons grow their solid model; they never submit a phantom overlay. */
public final class WarriorHeavyGrowth {
    public static final float MIN_PEAK=(float)com.tnc.tnc.warrior.WarriorReach.MIN_PEAK,
            MAX_PEAK=(float)com.tnc.tnc.warrior.WarriorReach.MAX_PEAK;
    private WarriorHeavyGrowth() {}
    public static float peak(int points){
        return (float)com.tnc.tnc.warrior.WarriorReach.peak(points);
    }
    public static boolean usesEntityGrowth(WarriorWeaponFamily family){
        return com.tnc.tnc.warrior.WarriorReach.heavy(family);
    }
    public static boolean applies(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold){
        if(!usesEntityGrowth(family) || hold || animation==null)return false;
        return switch(animation){
            case SLASH_LEFT,SLASH_RIGHT,SLASH_HORIZONTAL,OVERHEAD_SMASH,GROUND_QUAKE,SPIN_SLASH,GUARD_BASH,
                 GS_SWEEP,GS_SMASH,GS_WALL,GS_RIFT,GS_ADVANCE,HM_CRUSH,HM_QUAKE,HM_CUT,HM_RUIN,HM_DRIVE,
                 SCYTHE_POUNCE,SCYTHE_REAP,SCYTHE_HOOK,SCYTHE_EXECUTE,SCYTHE_WHIRL -> true;
            default -> false;
        };
    }
    public static float liftEnd(WarriorAnimation animation){
        return switch(animation){
            case SLASH_LEFT,SLASH_RIGHT,SLASH_HORIZONTAL,GUARD_BASH -> .22f;
            case GS_SWEEP -> .15f/.90f;
            case GS_SMASH -> .32f/1.10f;
            case HM_CUT -> .20f/.80f;
            default -> WarriorHammerGrowth.liftEnd(animation);
        };
    }
    public static float scale(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold,float progress){
        return scale(animation,family,hold,progress,MIN_PEAK);
    }
    public static float scale(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold,float progress,float peak){
        if(!applies(animation,family,hold) || !Float.isFinite(progress))return 1;
        return envelope(animation,progress,peak);
    }
    public static float phantomScale(WarriorAnimation animation,boolean hold,float progress,float peak){
        if(!com.tnc.tnc.warrior.WarriorTrailAppearance.attacking(animation,hold,false) || !Float.isFinite(progress))return 1;
        return envelope(animation,progress,peak);
    }
    private static float envelope(WarriorAnimation animation,float progress,float peak){
        peak=Float.isFinite(peak)?Math.max(MIN_PEAK,Math.min(MAX_PEAK,peak)):MIN_PEAK;
        float t=Math.max(0,Math.min(1,progress));
        float recovery=animation==WarriorAnimation.SPIN_SLASH || animation==WarriorAnimation.SCYTHE_WHIRL?.80f:.70f;
        return 1+(peak-1)*smooth(t/liftEnd(animation))*(1-smooth((t-recovery)/(1-recovery)));
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
