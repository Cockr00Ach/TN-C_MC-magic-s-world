package com.tnc.tnc.warrior.client;

/** Pure interpolation rules shared by body pose and whole-player turn. */
public final class WarriorMotionBlend {
    private WarriorMotionBlend(){}
    public static float weight(float age,int duration,boolean hold){
        float enter=smooth(age/2f);
        return enter*(hold?1:1-smooth((age-duration)/3f));
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float angle(float from,float to,float weight){
        float delta=(to-from)%(float)(Math.PI*2);
        if(delta>Math.PI)delta-=Math.PI*2;if(delta< -Math.PI)delta+=Math.PI*2;
        return from+delta*weight;
    }
    public static float root(float degrees,float weight){
        // Never fade an unwrapped 720-degree value: it would spin backwards.
        if(weight>=1)return degrees;
        float angle=degrees%360;if(angle>180)angle-=360;if(angle< -180)angle+=360;
        return angle*weight;
    }
}
