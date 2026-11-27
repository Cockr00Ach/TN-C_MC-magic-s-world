package com.tnc.tnc.sanctum.client;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static com.tnc.tnc.sanctum.SanctumTimings.*;
import static com.tnc.tnc.sanctum.client.SanctumPose.ease;

/** Steers a connected limb towards a server-locked point. */
record SanctumDemonReach(int side,boolean wing,boolean palm,float weight,Vector3f target,Vector3f worldUp,Near near) {
    private static final Matrix4f IDENTITY=new Matrix4f();
    private static float window(float f,float a,float b,float c,float d){return ease(f,a,b)*(1-ease(f,c,d));}
    record Near(Vector3f eye,Vector3f back,Vector3f flank,float influence){}
    private static Near near(Vec3 eye,Vec3 body,Vec3 aim,float yaw,Matrix4f frame){
        Vec3 back=body.subtract(eye).multiply(1,0,1).normalize();
        float proximity=1-ease((float)eye.subtract(aim).horizontalDistance(),4,14);
        if(back.lengthSqr()<.1)return null;
        var pos=frame.transformPosition(vector(eye.subtract(body).yRot((float)Math.toRadians(yaw))));
        var behind=frame.transformDirection(vector(back.yRot((float)Math.toRadians(yaw)))).normalize();
        var flank=frame.transformDirection(vector(new Vec3(back.z,0,-back.x).yRot((float)Math.toRadians(yaw)))).normalize();
        return new Near(pos,behind,flank,proximity);
    }
    private static Vector3f vector(Vec3 p){return new Vector3f((float)p.x,(float)p.y,(float)p.z);}
    static SanctumDemonReach lock(UUID owner,String action,float t,long started,Vec3 body,float yaw,Matrix4f frame){
        boolean wing=action.startsWith("wingcut"),hook=action.startsWith("hook");
        int side=action.endsWith("_r")?1:-1;
        Vec3 aim;float w;
        if(wing){
            float f=t*WINGCUT;
            w=window(f,WING_LOCK+1,WING_IN,WING_OUT,WING_OUT+13);
            if(w<=0)return null;
            var from=SanctumFloorRenderer.lockedPoint(owner,"wing_from",started);
            var center=SanctumFloorRenderer.lockedPoint(owner,"wing_center",started);
            var to=SanctumFloorRenderer.lockedPoint(owner,"wing_to",started);
            if(from==null||center==null||to==null)return null;
            aim=(f<=WING_IMPACT?from.lerp(center,clamp((f-WING_IN)/(WING_IMPACT-WING_IN))):
                    center.lerp(to,clamp((f-WING_IMPACT)/(WING_OUT-WING_IMPACT)))).add(0,.62,0);
        }else if(hook){
            float f=t*HOOK;w=window(f,HOOK_LOCK,HOOK_IMPACT,HOOK_RELEASE,HOOK-6);
            aim=SanctumFloorRenderer.lockedPoint(owner,"hook_tip",started);
            if (aim != null) {
                float drop = ease(f, HOOK_LOCK + 8, HOOK_IMPACT);
                float lift = ease(f, HOOK_RELEASE, HOOK_RELEASE + 6);
                aim = aim.add(0, 1.35 + 13 * (1 - drop) + 4 * lift, 0);
            }
        }else if(action.equals("grab")){
            float f=t*67;w=window(f,16,28,49,59);
            aim=SanctumFloorRenderer.lockedPoint(owner,"grab_tip",started);
            var grip=SanctumFloorRenderer.contact(owner,"grab_grip",started);
            if(aim!=null){
                aim=aim.add(0,1,0);
                if(grip!=null){float locked=grip.start()-started;
                    float lift=ease(f,locked,locked+2)*(1-ease(f,45,49));
                    aim=aim.lerp(grip.center().add(0,.70+3.45*lift,0),ease(f,locked-1,locked+1));}
            }
        }else if(action.equals("dance")){
            float f=t*142;
            if(f<DANCE_LOCK){
                int slash=Math.max(0,Math.min(4,(int)(f-7)/12));side=slash%2==0?-1:1;
                w=window(f-5-12*slash,2,11,11,14);
                aim=SanctumFloorRenderer.lockedPoint(owner,"dance_cut_"+slash,started);
                if(aim!=null)aim=aim.add(0,1.15,0);
            }else{
                w=window(f,DANCE_LOCK,DANCE_IMPACT,DANCE_IMPACT+6,136);
                aim=SanctumFloorRenderer.lockedPoint(owner,"final_cut",started);
                if(aim!=null)aim=aim.add(0,.70+7*(1-ease(f,100,DANCE_IMPACT)),0);
            }
        }else return null;
        if(w<=0||aim==null)return null;
        Vec3 at=aim.subtract(body).yRot((float)Math.toRadians(yaw));
        var view=frame.transformPosition(new Vector3f((float)at.x,(float)at.y,(float)at.z));
        var mc=net.minecraft.client.Minecraft.getInstance();
        var guard=mc.options.getCameraType().isFirstPerson()&&mc.getCameraEntity()==mc.player?
                near(mc.gameRenderer.getMainCamera().getPosition(),body,aim,yaw,frame):null;
        return new SanctumDemonReach(side,wing,hook,w,view,frame.transformDirection(new Vector3f(0,1,0)).normalize(),guard);
    }
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    String endBone(){return (wing?"wing_":"hand_")+side;}
    // Vertices measured on both the full sculpture and the overview meshes.
    Vector3f tip(){
        if (palm) return side < 0 ? new Vector3f(3.0F,-2.8F,4.8F) : new Vector3f(-2.0F,-4.6F,2.7F);
        if(wing)return side<0?new Vector3f(-50.04F,12.01F,-9.88F):new Vector3f(47.16F,1.81F,9.59F);
        return side<0?new Vector3f(8.17F,-5.65F,9.58F):new Vector3f(-1.94F,-10.66F,4.43F);
    }
    private void turn(Matrix4f joint,Matrix4f[] limb,int begin,Vector3f contact,Vector3f goal,float degrees){
        var inverse=joint.invert(new Matrix4f());
        var from=inverse.transformPosition(contact,new Vector3f());
        var to=inverse.transformPosition(goal,new Vector3f());
        if(from.lengthSquared()<.01F||to.lengthSquared()<.01F)return;
        var rot=new Quaternionf().rotationTo(from.normalize(),to.normalize());
        float angle=rot.angle();if(angle<.00001F)return;
        var weighted=new Quaternionf().slerp(rot,weight*Math.min(1,(float)Math.toRadians(degrees)/angle));
        var delta=new Matrix4f(joint).rotate(weighted).mul(inverse);
        for(int i=begin;i<limb.length;i++)delta.mul(limb[i],limb[i]);
    }
    /** No scale or bone translation is introduced by the reach solver. */
    Matrix4f solve(List<SanctumMesh.Bone> bones,Matrix4f[] transforms){
        int wrist=-1,elbow=-1,shoulder=-1;
        String distal=endBone(),upper="upper_arm_"+side,fore="forearm_"+side;
        for(int i=0;i<bones.size();i++){
            String b=bones.get(i).name();
            if(b.equals(distal))wrist=i;else if(b.equals(fore))elbow=i;else if(b.equals(upper))shoulder=i;
        }
        if(wrist<0||transforms[wrist]==null)return null;
        var hand=transforms[wrist];var nail=tip();
        Vector3f bodyTip=new Vector3f(target);
        float bank=wing&&near!=null?ease(near.influence,.02F,.48F):0;
        if(near!=null && !palm){
            bodyTip.fma((wing?12.8F:10.5F)*near.influence,near.back)
                    .fma(side*(wing?-28*bank:8.8F*near.influence),near.flank);
        }
        if(wing){
            // Present the leading edge on the wing's own side. The nail completes the sweep at the ground lock.
            bodyTip.fma(7*bank,worldUp);
            turn(hand,new Matrix4f[]{hand},0,hand.transformPosition(nail,new Vector3f()),bodyTip,130);
            if(bank>0)hand.rotate((float)Math.toRadians(-24*side*weight*bank),new Vector3f(nail).normalize());
        }else if(shoulder>=0&&elbow>=0){
            Matrix4f[] limb={transforms[shoulder],transforms[elbow],hand};
            Vector3f[] points={limb[0].getTranslation(new Vector3f()),limb[1].getTranslation(new Vector3f()),
                    limb[2].getTranslation(new Vector3f()),hand.transformPosition(nail,new Vector3f())};
            var base=new Vector3f(points[0]);float[] length={points[0].distance(points[1]),points[1].distance(points[2]),points[2].distance(points[3])};
            if((palm || near!=null&&near.influence>0)&&base.distance(bodyTip)<length[0]+length[1]+length[2]){
                // When the desired endpoint is inside the reach sphere, retain a bent elbow.
                for(int n=0;n<6;n++){
                    points[3].set(bodyTip);
                    for(int i=2;i>=0;i--)atLength(points[i],points[i+1],length[i]);
                    points[0].set(base);
                    for(int i=0;i<3;i++)atLength(points[i+1],points[i],length[i]);
                }
            }else for(int i=1;i<=3;i++)points[i].set(bodyTip);
            for(int i=0;i<3;i++){
                Vector3f end=i<2?limb[i+1].getTranslation(new Vector3f()):hand.transformPosition(nail,new Vector3f());
                turn(limb[i],limb,i,end,points[i+1],i==0?97:i==1?132:95);
            }
        }
        return hand;
    }
    private static void atLength(Vector3f p,Vector3f fixed,float r){
        p.sub(fixed);if(p.lengthSquared()<.00001F)p.set(0,-1,0);p.normalize(r).add(fixed);
    }
    Vec3[][][] talons(Matrix4f extremity){
        if(palm || extremity==null || weight<.03F)return null;
        var p=extremity.transformPosition(tip());Vec3 start=new Vec3(p.x,p.y,p.z);
        Vec3 delta=new Vec3(target.x,target.y,target.z).subtract(start).scale(weight);
        if(delta.lengthSqr()<.20)return null;
        Vec3 up=new Vec3(worldUp.x,worldUp.y,worldUp.z);
        Vec3 sideway=delta.normalize().cross(up).normalize();
        if(sideway.lengthSqr()<.1)sideway=delta.normalize().cross(new Vec3(1,0,0)).normalize();
        float length=(float)delta.length(),width=Math.min(wing?.69F:.41F,length*.14F);
        Vec3 point=start.add(delta);
        float narrow=near==null?0:ease(near.influence*weight,.12F,.48F);
        Vec3[][] low=null;
        if(narrow>0){
            Vec3 back=new Vec3(near.back.x,near.back.y,near.back.z),lateral=new Vec3(near.flank.x,near.flank.y,near.flank.z).scale(wing?-side:side);
            Vec3 knee=point.add(back.scale(4.9)).add(lateral.scale(2.0)).add(up.scale(.09));
            Vec3[] spine={start,start.lerp(knee,.58).add(lateral.scale(.72)),knee,
                    point.add(back.scale(1.9)).add(lateral.scale(.82)).subtract(up.scale(.12)),point};
            low=rings(spine,sideway,Math.min(.25F,width),new double[]{.65,1,.58,.30,0});
            if(narrow>=1)return new Vec3[][][]{low};
        }
        Vec3[][][] result=new Vec3[wing?2:3][][];
        if(wing){
            result[0]=talon(start,point,sideway,up,width,1.8*side);
            result[1]=talon(start.add(delta.scale(.26)),point.subtract(delta.scale(.26)).add(sideway.scale(1.05*side)),sideway,up,width*.60,-1.2*side);
        }else for(int i=-1;i<=1;i++){
            Vec3 knuckle=start.add(sideway.scale(i*width*.7));
            Vec3 tip=point.add(sideway.scale(i*.67)).subtract(delta.scale(Math.abs(i)*.07));
            result[i+1]=talon(knuckle,tip,sideway,up,width*(i==0?1:.74),i*.85);
        }
        if(low!=null){
            int lead=wing?0:1;
            for(int i=0;i<result.length;i++)for(int j=0;j<5;j++)for(int k=0;k<4;k++){
                Vec3 destination=i==lead?low[j][k]:start;
                result[i][j][k]=result[i][j][k].lerp(destination,narrow);
            }
        }
        return result;
    }
    private static final class Material {
        static final net.minecraft.client.renderer.RenderType ROCK=net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/polished_blackstone.png"));
    }
    private static Vec3[][] talon(Vec3 start,Vec3 tip,Vec3 side,Vec3 up,double width,double bend){
        Vec3 d=tip.subtract(start);double arch=Math.min(2.7,d.length()*.32);
        Vec3[] spine={start,start.add(d.scale(.34)).add(up.scale(arch*.84)).add(side.scale(bend*.60)),
            start.add(d.scale(.70)).add(up.scale(arch)).add(side.scale(bend)),start.add(d.scale(.89)).add(up.scale(arch*.56)),tip};
        return rings(spine,side,width,new double[]{.47,1,.75,.38,0});
    }
    private static Vec3[][] rings(Vec3[] spine,Vec3 side,double width,double[] radii){
        Vec3[][] rings=new Vec3[5][4];
        for(int j=0;j<5;j++){
            Vec3 tangent=spine[Math.min(4,j+1)].subtract(spine[Math.max(0,j-1)]).normalize();
            Vec3 normal=tangent.cross(side).normalize();double r=width*radii[j];
            rings[j]=new Vec3[]{spine[j].add(side.scale(-r)),spine[j].add(normal.scale(r*.76)),spine[j].add(side.scale(r)),spine[j].subtract(normal.scale(r*.76))};
        }
        return rings;
    }
    void edge(MultiBufferSource buffers,Matrix4f extremity){
        var blades=talons(extremity);if(blades==null)return;
        var rock=buffers.getBuffer(Material.ROCK);
        for(var rings:blades)for(int j=0;j<4;j++)for(int k=0;k<4;k++){
            int next=(k+1)%4;int shade=k<2?0xAE8E9A:0x65516C;
            SanctumProjectedBlade.face(rock,IDENTITY,rings[j][k],rings[j][next],rings[j+1][next],shade,255);
            SanctumProjectedBlade.face(rock,IDENTITY,rings[j][k],rings[j+1][next],rings[j+1][k],shade,255);
        }
        var glow=buffers.getBuffer(SanctumProjectedBlade.EDGE);
        for(var rings:blades)for(int j=1;j<4;j++){
            Vec3 ridge=rings[j][1];Vec3 crease=ridge.lerp(rings[j][0],.17);
            SanctumProjectedBlade.face(glow,IDENTITY,ridge,crease,rings[j+1][1],0xED6C72,224);
        }
    }
}
