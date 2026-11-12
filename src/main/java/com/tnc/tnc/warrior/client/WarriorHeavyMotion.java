package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.WarriorAnimation;
import com.tnc.tnc.warrior.WarriorTrailAppearance;
import com.tnc.tnc.warrior.WarriorWeaponFamily;

/** Shared heavy-weapon timing and bracing, sampled by both camera views. */
public final class WarriorHeavyMotion {
    private static final float[] TIME={0,.34f,.58f,.78f,1},POSE={0,.22f,.58f,.65f,1};
    private WarriorHeavyMotion() {}
    public static boolean applies(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold){
        return animation!=WarriorAnimation.ULTIMATE && !hold && (family==WarriorWeaponFamily.GREATSWORD || family==WarriorWeaponFamily.HAMMER)
                && WarriorTrailAppearance.attacking(animation,false,false);
    }
    public static float progress(WarriorAnimation animation,WarriorWeaponFamily family,boolean hold,float t){
        t=Math.max(0,Math.min(1,t));
        if(!applies(animation,family,hold))return t;
        // Hold back the windup, accelerate into the stroke, linger before lifting the weapon back.
        // Overall duration and the end-of-stroke landmark remain unchanged.
        for(int i=1;i<TIME.length;i++)if(t<=TIME[i]){
            float u=(t-TIME[i-1])/(TIME[i]-TIME[i-1]);
            u=u*u*(3-2*u);
            return POSE[i-1]+(POSE[i]-POSE[i-1])*u;
        }
        return 1;
    }
    public static float brace(float t){
        // Lean back under the raised head, then let its momentum pull the torso down.
        return -.10f*(float)Math.sin(Math.PI*Math.min(1,t/.45f))
                +.20f*(float)Math.exp(-Math.pow((t-.63f)/.15f,2));
    }
}
