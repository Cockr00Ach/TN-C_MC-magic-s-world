package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Near-camera representation for broad rigid appendages and their independent melee target. */
final class SanctumNearClaw {
    private SanctumNearClaw(){}
    static void wound(MultiBufferSource buffers,Matrix4f m){
        var out=buffers.getBuffer(SanctumProjectedBlade.EDGE);
        for(int i=-1;i<=1;i++){
            Vec3 heel=new Vec3(i*.23,.07,-.83),bend=new Vec3(i*.31+.14,.11,-.15),tip=new Vec3(i*.38+.2,.065,.78-.08*Math.abs(i));
            Vec3 lip=bend.add(.094,0,0),rim=bend.add(-.094,0,0);
            SanctumProjectedBlade.face(out,m,heel,rim,lip,0xFA8866,236);
            SanctumProjectedBlade.face(out,m,rim,tip,lip,0xA52B48,235);
        }
    }
    static boolean broad(String bone){return bone.startsWith("wing_")||bone.startsWith("forearm_")||bone.startsWith("hand_");}
    static boolean clipped(VertexConsumer out,SanctumMesh.Layer l,Matrix4f matrix,SanctumDemonReach.Near near,int light,boolean dark){
        if(near==null)return false;
        var eye=near.eye();var back=near.back();float stop=6.5F+2.1F*near.influence();
        Vector3f center=matrix.transformPosition(l.cx,l.cy,l.cz,new Vector3f());
        float size=matrix.transformDirection(new Vector3f(1,0,0)).length()*l.radius;
        if(center.distance(eye)>size+14 || back.dot(center.sub(eye))>stop+size)return false;
        var values=l.transformed;int n=values.length/6;
        if(l.clearance==null)l.clearance=new float[n];
        boolean outside=false,inside=false;
        for(int i=0;i<n;i++){
            int j=i*6;float clearance=(values[j]-eye.x)*back.x+(values[j+1]-eye.y)*back.y+(values[j+2]-eye.z)*back.z-stop;
            l.clearance[i]=clearance;outside|=clearance>=0;inside|=clearance<0;
        }
        if(!inside)return false;
        if(outside){
            // Preserve the membrane up to the sight-line boundary, including large triangles that straddle it.
            for(int j=0;j<l.corners.length;j+=4){
                int a=l.corners[j],b=l.corners[j+1],c=l.corners[j+2],d=l.corners[j+3];
                if(l.clearance[a]>=0&&l.clearance[b]>=0&&l.clearance[c]>=0&&l.clearance[d]>=0){
                    vertex(out,l,a,a,0,light,dark);vertex(out,l,b,b,0,light,dark);vertex(out,l,c,c,0,light,dark);vertex(out,l,d,d,0,light,dark);
                }else{
                    triangle(out,l,a,b,c,light,dark);
                    if(c!=d)triangle(out,l,a,c,d,light,dark);
                }
            }
        }
        return true;
    }
    private static void triangle(VertexConsumer out,SanctumMesh.Layer l,int a,int b,int c,int light,boolean dark){
        int count=0,previous=c,lastA=0,lastB=0;float lastT=0;
        for(int i=0;i<3;i++){
            int current=i==0?a:i==1?b:c;
            float p=l.clearance[previous],q=l.clearance[current];
            if((p>=0)!=(q>=0)){
                lastA=previous;lastB=current;lastT=p/(p-q);vertex(out,l,lastA,lastB,lastT,light,dark);count++;
            }
            if(q>=0){lastA=lastB=current;lastT=0;vertex(out,l,current,current,0,light,dark);count++;}
            previous=current;
        }
        if(count==3)vertex(out,l,lastA,lastB,lastT,light,dark);
    }
    private static void vertex(VertexConsumer out,SanctumMesh.Layer l,int first,int second,float t,int light,boolean dark){
        float[] v=l.transformed,s=l.source;int a=6*first,b=6*second,i=10*first,j=10*second;float k=1-t;
        float value=s[i+5]*k+s[j+5]*t;
        var packed=LightTexture.pack(Math.max((int)(s[i+9]*k+s[j+9]*t),LightTexture.block(light)),LightTexture.sky(light));
        out.vertex(v[a]*k+v[b]*t,v[a+1]*k+v[b+1]*t,v[a+2]*k+v[b+2]*t,dark?value*.69F:value,dark?value*.37F:value,dark?value*.56F:value,
                l.blend?.67F:1,s[i+3]*k+s[j+3]*t,s[i+4]*k+s[j+4]*t,OverlayTexture.NO_OVERLAY,packed,
                v[a+3]*k+v[b+3]*t,v[a+4]*k+v[b+4]*t,v[a+5]*k+v[b+5]*t);
    }
}
