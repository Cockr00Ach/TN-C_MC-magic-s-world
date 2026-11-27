package com.tnc.tnc.sanctum.client;

import org.joml.Matrix4f;
import static com.tnc.tnc.sanctum.SanctumTimings.*;
import static com.tnc.tnc.sanctum.client.SanctumPose.ease;

/** Joint angles in the sculptor's local axes. The asymmetric arms have different lengths and binds. */
final class SanctumClawPose {
    private static final float RAD=(float)Math.PI/180;
    private record Joints(float sz,float sy,float sx,float ex,float ey,float ez,float wz,float wy,float wx){}
    private static final Joints REST=new Joints(0,0,-46, 0,0,0, 0,0,0);
    private static final Joints[] LEFT={REST,
            new Joints(-48,-16,-61, -29,8,22, -18,23,-15),
            new Joints(57,32,-85, -6,-36,-8, 33,15,12),
            new Joints(15,-41,12, -45,33,-2, -35,-38,20),
            new Joints(-17,-20,-76, -26,6,17, -25,0,-14),
            new Joints(29,71,-104, 27,-12,-27, -30,0,15)};
    private static final Joints[] RIGHT={REST,
            new Joints(37,26,-58, -67,26,-46, 18,-23,-15),
            new Joints(-63,-34,-105, -27,-16,47, -33,-15,12),
            new Joints(-26,62,25, -70,39,-84, 35,38,20),
            new Joints(15,24,-87, -65,12,-13, 25,0,-14),
            new Joints(-39,-71,-111, 14,6,23, 30,0,15)};
    private record Key(int before,int after,float amount,float attacker){}
    private static Key between(float t,float a,float b,int prev,int next,float hand){
        return new Key(prev,next,ease(t,a,b),hand);
    }
    private static Key sample(boolean dance,float t,float drag){
        // The shoulder leads. Wrist continues past the locked strike for a tick before recoiling.
        t-=drag;
        if(dance&&t>=DANCE_LOCK){
            if(t<85)return between(t,DANCE_LOCK,85,0,4,0);
            if(t<106)return new Key(4,4,0,0);
            if(t<DANCE_IMPACT)return between(t,106,DANCE_IMPACT,4,5,0);
            return between(t,DANCE_IMPACT+7,140,5,0,0);
        }
        if(!dance){
            if(t<23)return between(t,0,20,0,1,-1);
            if(t<29)return between(t,23,29,1,2,-1);
            if(t<45)return between(t,29,43,2,4,-1);
            if(t<49)return between(t,45,49,4,5,-1);
            if(t<57)return between(t,50,57,5,3,-1);
            return between(t,57,66,3,0,-1);
        }
        if(t<5||t>=68)return new Key(0,0,0,0);
        int index=Math.min(4,(int)(t-5)/12);
        float hand=index%2==0?-1:1;
        float tick=t-(5+12*index);
        if(tick<7)return between(tick,0,6,0,1,hand);
        if(tick<11)return between(tick,7,11,1,2,hand);
        if(index==4)return between(tick,11,15,2,0,hand);
        return between(tick,11,12,2,0,hand);
    }
    private static float mix(float a,float b,float u){return (a+(b-a)*u)*RAD;}
    private static float torso(int key,int axis,float hand){
        return switch(axis){
            case 0 -> switch(key){case 1->49;case 2->72;case 3->56;case 4->37;case 5->81;default->59;};
            case 1 -> switch(key){case 1->20*hand;case 2->-25*hand;case 3->-9*hand;default->0;};
            case 2 -> switch(key){case 1->-12*hand;case 2->17*hand;case 3->7*hand;default->0;};
            default -> switch(key){case 1->27*hand;case 2->-19*hand;case 3->-11*hand;default->0;};
        };
    }
    static boolean apply(String action,float time,String bone,Matrix4f m){
        boolean dance=action.equals("dance");
        if(!dance&&!action.equals("grab"))return false;
        boolean pelvis=bone.equals("pelvis"),chest=bone.equals("chest");
        boolean arm=bone.startsWith("upper_arm_"),fore=bone.startsWith("forearm_"),wrist=bone.startsWith("hand_");
        if(!(pelvis||chest||arm||fore||wrist))return false;
        float tick=time*(dance?142:67);
        Key k=sample(dance,tick,wrist?.85F:fore?.4F:0);
        int a=k.before,b=k.after;float u=k.amount,h=k.attacker;
        if(pelvis){
            m.translate(0,-7,0).rotateY(mix(torso(a,3,h),torso(b,3,h),u));
        }else if(chest){
            m.rotateXYZ(mix(torso(a,0,h),torso(b,0,h),u),mix(torso(a,1,h),torso(b,1,h),u),mix(torso(a,2,h),torso(b,2,h),u));
        }else{
            int side=bone.endsWith("_-1")?-1:1;
            if(h!=0&&h!=side)return false;
            Joints[] limb=side<0?LEFT:RIGHT;
            Joints p=limb[a],q=limb[b];
            if(arm)m.rotateZYX(mix(p.sz,q.sz,u),mix(p.sy,q.sy,u),mix(p.sx,q.sx,u));
            if(fore)m.rotateXYZ(mix(p.ex,q.ex,u),mix(p.ey,q.ey,u),mix(p.ez,q.ez,u));
            if(wrist)m.rotateZYX(mix(p.wz,q.wz,u),mix(p.wy,q.wy,u),mix(p.wx,q.wx,u));
        }
        return true;
    }
    private SanctumClawPose(){}
}
