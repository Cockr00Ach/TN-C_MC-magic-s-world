package com.tnc.tnc.sanctum.client;

import org.joml.Matrix4f;
import static com.tnc.tnc.sanctum.SanctumTimings.*;

/** The model files store pivots in blocks. No camera-space imitation of a moving limb. */
record SanctumPose(String form,String action,float t,float age,float speed,boolean handOnly) {
    static float ease(float value,float lo,float hi){
        float v=Math.max(0,Math.min(1,(value-lo)/(hi-lo)));return v*v*(3-2*v);
    }
    private static float sin(double x){return (float)Math.sin(x);}
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    private boolean is(String s){return action.equals(s);}
    private float pulse(float tick,float prepare,float hit,float recover){
        return ease(tick,prepare,hit)*(1-ease(tick,hit+2,recover));
    }
    boolean visible(String bone){
        if(is("gaze_trunk") && bone.equals("head"))return false;
        if(is("awaken")&&form.equals("bleeding")&&bone.equals("head_tears")&&t*AWAKEN<TEARS_START)return false;
        return !bone.equals("relic_steps")||!form.equals("demon");
    }
    void animate(String b,Matrix4f m){
        if(is("gaze_lunge") && b.equals("head_jaw"))m.translate(0,-.37F*t,.22F*t).rotateX(radians(-14*t));
        if(SanctumSentinelPose.animate(this,b,m))return;
        float side=(b.endsWith("_L")||b.endsWith("_-1")||b.startsWith("左"))?-1:1;
        float slow=sin(age*.055),stride=sin(age*.43+side*1.57);
        float rise=ease(t,0,.32F)*(1-ease(t,.76F,1));
        switch(form){
            case "angel","bleeding","bleeding_feral" -> {
                if(b.equals("skirts"))m.translate(0,-.72F,0);
                float frame=t*COMBO;
                float preLeft=is("combo")?ease(frame,1,9)*(1-ease(frame,9,CUT_ONE)):0;
                float preRight=is("combo")?ease(frame,LOCK_TWO,26)*(1-ease(frame,26,CUT_TWO)):0;
                float sweepLeft=is("combo")?pulse(frame,9,CUT_ONE,25):0;
                float sweepRight=is("combo")?pulse(frame,26,CUT_TWO,43):0;
                float lift=is("combo")?ease(frame,LOCK_HEAVY,63)*(1-ease(frame,66,HEAVY_IMPACT)):0;
                float slam=is("combo")?pulse(frame,66,HEAVY_IMPACT,87):0;
                if(is("combo")&&b.equals("torso"))m.rotateY(radians(23*(preRight-preLeft)+29*(sweepLeft-sweepRight))).rotateX(radians(-16*lift+53*slam)).rotateZ(radians(8*(sweepRight-sweepLeft)));
                if(b.startsWith("arm_")){
                    float folded=(is("pillars")||is("prayer"))?rise:0;
                    m.translate(0,(side<0?-1.1F:2.3F)*folded,0)
                            .rotateZ(radians((side<0?57:-27)*folded+side*slow*1.4F)).rotateX(radians(-folded*12));
                    if(is("tears")||is("scream"))m.rotateZ(radians(-side*rise*24));
                    if(is("combo")){
                        float cut=side<0?sweepLeft:sweepRight,wind=side<0?preLeft:preRight;
                        m.rotateZ(radians(side*((side<0?-42:28)*cut-85*lift+21*slam-46*wind)))
                                .rotateY(radians(side*(58*wind-91*cut))).rotateX(radians((side<0?-24:39)*cut-51*lift+36*slam));
                    }
                }
                if(b.startsWith("hand_"))m.translate(0,.22F*slow,0)
                        .rotateX(radians(slow*4+(is("prayer")?rise*25:0)));
                if(is("combo")&&b.startsWith("hand_")){
                    float lag=side<0?pulse(frame,11,15,27):pulse(frame,28,32,46);
                    m.rotateZ(radians(side*48*lag)).rotateY(radians(-side*59*lag)).rotateX(radians(-22*lift+29*slam));
                }
                if(b.startsWith("angel_wing_")){
                    if(is("wings"))m.rotateY(radians(side*(ease(t,0,.28F)*-28+ease(t,.28F,.63F)*78-ease(t,.72F,1)*50)))
                            .rotateZ(radians(side*sin(t*47)*ease(t,.64F,.72F)*7*(1-ease(t,.90F,1))));
                    if(is("combo"))m.rotateY(radians(side*(17*(side<0?sweepLeft:sweepRight)-9*lift+8*slam)));
                }
                if(b.equals("head"))m.rotateX(radians(is("prayer")?10*rise:is("tears")?-16*rise:is("scream")?-9*rise:0));
                if(is("gaze")&&b.equals("head_oculus"))m.scale(1+rise*.10F);
                if(is("scream")&&b.equals("head_jaw"))m.rotateX(radians(-11*rise));
                if(is("awaken")&&form.equals("bleeding")){
                    if(b.equals("head_oculus"))m.scale(1,.025F+.975F*ease(t*AWAKEN,EYES_START,EYES_OPEN),1);
                    if(b.equals("head_jaw")) {
                        float split = ease(t * AWAKEN, MOUTH_START, SMILE_OPEN);
                        // Fold the lips, cavity and both rows of teeth into the same seam.
                        m.translate(0, 1.68F, .055F * (1 - split))
                                .scale(.66F + .34F * split, .028F + .972F * split, 1)
                                .translate(0, -1.68F, 0);
                    }
                    if(b.contains("plumage")&&b.endsWith("cheek"))m.rotateZ(radians(side*8*(1-ease(t,.35F,.71F))));
                }
                if(is("rupture")){
                    float split=ease(t*RUPTURE,ARM_OUT,SHELL_SPLIT);
                    float breath=ease(t*RUPTURE,0,FINGERS)*(1-ease(t*RUPTURE,ARM_OUT,SHELL_SPLIT));
                    if(b.equals("torso"))m.rotateZ(radians(sin(age*.9)*split*2));
                    if(b.equals("girdle"))m.scale(1+split*.65F,1,1+breath*.21F+split*.22F);
                    if(b.equals("pectorale"))m.translate(6*split,5*split,12*split).rotateZ(radians(-44*split));
                    if(b.startsWith("arm_")||b.startsWith("angel_wing_"))
                        m.translate(side*16*split,-7*split,0).rotateZ(radians(side*47*split));
                    if(b.startsWith("arm_")){
                        float recoil=ease(t*RUPTURE,10,48);
                        m.translate(side*6.5F*recoil,2.1F*recoil,-.5F*recoil)
                                .rotateY(radians(side*19*recoil)).rotateZ(radians(side*17*recoil));
                    }
                    if(b.equals("head"))m.translate(0,9*split,0).rotateZ(radians(-27*split));
                }
            }
            case "demon" -> {
                if(!handOnly)SanctumDemonMotion.pose(this,b,m);
            }
        }
    }
}
