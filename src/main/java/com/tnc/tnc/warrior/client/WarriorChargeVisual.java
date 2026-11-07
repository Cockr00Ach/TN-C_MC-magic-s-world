package com.tnc.tnc.warrior.client;

import com.tnc.tnc.warrior.*;
import java.util.HashMap;
import java.util.Map;

/** Charge size follows server animation lifetime, including remote players. */
public final class WarriorChargeVisual {
    private record Release(long start, float peak) {}
    private static final Map<Integer,Release> RELEASES=new HashMap<>();
    private WarriorChargeVisual(){}
    public static void accept(int id,WarriorAnimationClient.Active previous,WarriorAnimation next,boolean hold,long tick){
        RELEASES.remove(id);
        if(!hold && previous!=null && previous.hold() && previous.animation()!=WarriorAnimation.BOW_CHARGE && WeaponChargeRules.chargingAnimation(previous.animation()))
            RELEASES.put(id,new Release(tick,WeaponChargeRules.visualScale(tick-previous.startTick())));
    }
    /** Zero means the normal item renderer owns scale. */
    public static float scale(int id,float partial){
        var a=WarriorAnimationClient.active(id);if(a==null)return 0;
        if(a.hold() && WeaponChargeRules.chargingAnimation(a.animation()) && a.animation()!=WarriorAnimation.BOW_CHARGE)
            return WeaponChargeRules.visualScale(WarriorAnimationClient.clock()-a.startTick()+partial);
        var r=RELEASES.get(id);
        if(r==null || a.startTick()!=r.start())return 0;
        float t=a.progress(partial);return 1+(r.peak()-1)*(1-Math.max(0,(t-.45f)/.55f));
    }
    public static void clear(){RELEASES.clear();}
}
