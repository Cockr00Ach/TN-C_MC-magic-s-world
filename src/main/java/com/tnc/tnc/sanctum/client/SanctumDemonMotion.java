package com.tnc.tnc.sanctum.client;

import org.joml.Matrix4f;
import static com.tnc.tnc.sanctum.client.SanctumPose.ease;
import static com.tnc.tnc.sanctum.SanctumTimings.*;

/** Grounded weight shifts, airborne banking and footwork use the same rigid joints as the talons. */
final class SanctumDemonMotion {
    private static final float RAD=(float)Math.PI/180;
    private static float pulse(float t,float a,float b,float c,float d){return ease(t,a,b)*(1-ease(t,c,d));}
    private static float sin(float t){return (float)Math.sin(t);}
    static float fullSpan(SanctumPose pose){
        return pose.action().equals("funeral")?pulse(pose.t()*147,3,31,104,132):0;
    }
    static void frame(SanctumPose pose,Matrix4f root){
        float crown=fullSpan(pose),t=pose.t(),spring=0,glide=0;
        String action=pose.action();
        if(action.startsWith("wingcut")){glide=pulse(t*WINGCUT,16,38,64,86);spring=-2.7F*glide;}
        if(action.startsWith("hook"))spring=5*pulse(t*HOOK,18,38,66,80);
        if(action.equals("grab"))spring=7*pulse(t*67,19,29,49,66);
        if(action.equals("dance")){
            float f=t*142;spring=4.8F*pulse(f,72,106,117,140);
            if(f>=5&&f<=68) {int slash=Math.max(0,Math.min(4,(int)(f-7)/12));spring+=3.3F*pulse(f-5-12*slash,1,10,11,14);}
        }
        float scale=.50F+.50F*crown+.07F*glide;
        // Keep aspect ratio intact. At full span the guardian is again its sculpted 112 blocks wide.
        root.translate(0,0,-2.6F*(1-crown)+spring).scale(scale);
    }
    static void pose(SanctumPose p,String bone,Matrix4f m){
        String a=p.action();float t=p.t();
        boolean cut=a.startsWith("wingcut"),hook=a.startsWith("hook"),grab=a.equals("grab"),dance=a.equals("dance");
        boolean funeral=a.equals("funeral"),fire=a.equals("fire"),stagger=a.equals("stagger");
        float f=t*(cut?WINGCUT:hook?HOOK:grab?67:dance?142:funeral?147:100);
        int active=a.endsWith("_r")?1:-1,side=bone.endsWith("_-1")?-1:1;
        float air=cut?pulse(f,15,31,72,86):funeral?pulse(f,0,25,110,130):dance?pulse(f,70,82,101,112):0;
        float load=cut?pulse(f,0,14,18,31):hook?pulse(f,0,19,23,35):funeral?pulse(f,0,4,6,17):0;
        float push=cut?pulse(f,27,43,64,80):hook?pulse(f,21,38,58,74):0;
        float brake=cut?pulse(f,61,76,82,92):funeral?pulse(f,93,106,111,126):0;
        float land=cut?pulse(f,80,86,88,92):funeral?pulse(f,123,130,135,147):dance?pulse(f,107,112,118,135):0;
        float dip=cut?pulse(f,34,44,64,77):0, twist=active*(26*load-32*push+14*brake);
        float gait=sin(p.age()*.38F+side*1.57F)*p.speed()*(1-air);
        float beat=0;
        if(cut)beat=42*pulse(f,0,14,15,23)-34*pulse(f,15,24,25,32)+24*pulse(f,24,34,35,43);
        if(funeral)beat=37*sin(f*.29F)*pulse(f,0,10,97,110)-32*pulse(f,105,115,126,139);
        if(dance)beat=31*pulse(f,69,79,79,88)-27*pulse(f,79,90,96,106);
        if(!bone.equals("pelvis") && (grab||dance) && SanctumClawPose.apply(a,t,bone,m))return;
        if(bone.equals("pelvis")){
            m.translate(0,-2.9F-.6F*load+1.5F*air,0)
                .rotateY(RAD*twist*.48F).rotateZ(-RAD*active*(cut?26*dip:4*push));
        }else if(bone.equals("chest")){
            float pitch=48+13*load+(cut?18:-20)*air+19*push+12*land+(stagger?12:0);
            m.rotateYXZ(RAD*twist*.52F,RAD*pitch,-RAD*active*(cut?15*dip:8*push));
        }else if(bone.equals("head")){
            m.rotateYXZ(-RAD*twist*.54F,RAD*(-40+(cut?-17:14)*air-8*load-11*push),RAD*active*16*dip);
        }else if(bone.equals("demon_mandible")){
            m.rotateX(-RAD*(3+turnOfBite(a,t)+9*push+2*sin(p.age()*.09F)));
        }else if(bone.startsWith("wing_")){
            float raked=34-28*air+17*load-23*brake;
            float flap=8+beat+22*brake+side*active*(cut?-28*dip:6*push);
            m.rotateZ(RAD*side*flap).rotateY(RAD*side*raked).rotateX(RAD*(-8*air+10*dip));
        }else if(bone.startsWith("thigh_")){
            m.rotateX(RAD*(-8+13*gait+(cut?-62:27)*air+12*load-12*land+side*active*8*push));
        }else if(bone.startsWith("shin_")){
            m.rotateX(RAD*(6-10*gait+(cut?101:-54)*air-22*load+25*land));
        }else if(bone.startsWith("foot_")){
            m.rotateX(RAD*(4+20*air+14*load-22*land-6*gait));
        }else if(bone.startsWith("vestment_")||bone.equals("burial_mantle")){
            m.rotateX(RAD*(-13*air-7*brake+4*land)); 
        }else if(bone.startsWith("upper_arm_")){
            float leading=side==active?1:0;
            m.rotateZYX(RAD*side*(7+15*air+leading*(32*load-29*push)),
                    RAD*side*(12*air+leading*(17*load-28*push)),
                    RAD*(-39+17*air-9*gait+leading*(17*load-48*push)-(fire?13:0)));
        }else if(bone.startsWith("forearm_")){
            m.rotateX(RAD*(-11-19*air+(side==active?27*load-23*push:0)));
        }else if(bone.startsWith("hand_")){
            m.rotateZYX(RAD*side*(7+14*push),0,RAD*(6*brake-11*push));
        }
    }
    private static float turnOfBite(String action,float t){
        return action.equals("grab")||action.equals("dance")?14*pulse(t,.20F,.38F,.70F,.88F):0;
    }
    private static final float[] LEFT_SOLE={-4.98f,-1.48f,-.91f,-4.89f,-.37f,-2.2f,-4.59f,-.65f,-2.14f,-3.79f,-.45f,-2.43f,
        -3.77f,-1.4f,4.78f,-1.39f,-1.27f,6.7f,1.63f,-1.34f,6.38f,1.67f,-.11f,-1.12f,2.96f,-.78f,3.81f,3.6f,-.46f,2.73f,3.76f,-.36f,4.14f};
    private static final float[] RIGHT_SOLE={-4,-1.11f,4.45f,-3.93f,-1.4f,4.87f,-3.89f,-1.25f,4.99f,-1.97f,.58f,-1.14f,
        -1.6f,-1.27f,6.41f,-.97f,-.4f,-.95f,1.14f,-1.46f,5.41f,1.19f,-1.34f,6.7f,1.42f,-1.34f,6.67f,1.48f,-.16f,-2.1f,
        3.62f,-.68f,3.92f,3.79f,-.45f,-2.43f,5,-1.45f,-.88f,5.06f,-.04f,-1.99f};
    /** Project the authored support vertices onto the carrier's floor; airborne carriers retain their world altitude. */
    static void feet(Matrix4f frame,java.util.List<SanctumMesh.Bone> bones,Matrix4f[] transforms){
        var inverse=frame.invert(new Matrix4f());float low=Float.POSITIVE_INFINITY;
        for(int i=0;i<bones.size();i++){
            String name=bones.get(i).name();
            if(!name.equals("foot_-1")&&!name.equals("foot_1")||transforms[i]==null)continue;
            var m=inverse.mul(transforms[i],new Matrix4f());var sole=name.endsWith("_-1")?LEFT_SOLE:RIGHT_SOLE;
            for(int j=0;j<sole.length;j+=3)low=Math.min(low,m.m01()*sole[j]+m.m11()*sole[j+1]+m.m21()*sole[j+2]+m.m31());
        }
        if(!Float.isFinite(low))return;
        var adjustment=new org.joml.Vector3f(0,.08F-low,0);frame.transformDirection(adjustment);
        for(var m:transforms)if(m!=null)m.translateLocal(adjustment.x,adjustment.y,adjustment.z);
    }
    static void guardClaws(Matrix4f frame,java.util.List<SanctumMesh.Bone> bones,Matrix4f[] transforms){
        var inverse=frame.invert(new Matrix4f());
        for(int i=0;i<bones.size();i++){
            var wrist=bones.get(i);
            if(!wrist.name().startsWith("hand_")||transforms[i]==null)continue;
            var local=inverse.mul(transforms[i],new Matrix4f());
            if(local.m31()>16)continue; // Both sculpted hands fit in a 14-block radius.
            float lowest=.12F;var low=new org.joml.Vector3f();
            for(var layer:wrist.layers())for(int j=0;j<layer.source.length;j+=10){
                var points=layer.source;float x=points[j],y=points[j+1],z=points[j+2];
                float h=local.m01()*x+local.m11()*y+local.m21()*z+local.m31();
                if(h<lowest){lowest=h;low.set(x,y,z);}
            }
            if(lowest>=.12F)continue;
            var elbow=transforms[wrist.parent()];
            var hinge=inverse.mul(elbow,new Matrix4f()).getTranslation(new org.joml.Vector3f());
            var d=local.transformPosition(low).sub(hinge);float horizontal=(float)Math.hypot(d.x,d.z);
            if(horizontal<.001F||d.lengthSquared()<.001F)continue;
            float angle=(float)(Math.asin(Math.max(-1,Math.min(1,(.72F-hinge.y)/d.length())))-Math.atan2(d.y,horizontal));
            if(angle<=0)continue;
            var turn=new Matrix4f(frame).translate(hinge).rotate(angle,-d.z/horizontal,0,d.x/horizontal)
                .translate(-hinge.x,-hinge.y,-hinge.z).mul(inverse);
            turn.mul(elbow,elbow);turn.mul(transforms[i],transforms[i]);
        }
    }
    private SanctumDemonMotion(){}
}
