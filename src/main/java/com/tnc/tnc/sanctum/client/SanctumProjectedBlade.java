package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.tnc.tnc.sanctum.SanctumTimings.*;
import static com.tnc.tnc.sanctum.client.SanctumPose.ease;

/** An extension of the sculpted hilt, driven by the same three ground locks that resolve damage. */
final class SanctumProjectedBlade {
    private record Lock(long start,Vec3 point){}
    record Strike(Vec3 first,Vec3 second,Vec3 heavy){}
    private static final Map<UUID,Lock[]> locks=new HashMap<>();
    private static final ResourceLocation IVORY=ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/white_concrete.png");
    private static final RenderType STEEL=RenderType.entityCutoutNoCull(IVORY);
    static final RenderType EDGE=RenderType.entityTranslucentEmissive(IVORY);
    static void clear(){locks.clear();}
    static void lock(UUID owner,String key,Vec3 point,long start){
        int slot=switch(key){case "wing_cut_0_4"->0;case "wing_cut_1_4"->1;case "heavy_hand"->2;default->-1;};
        if(slot<0)return;
        locks.entrySet().removeIf(e->{for(Lock old:e.getValue())if(old!=null&&start-old.start<140)return false;return true;});
        locks.computeIfAbsent(owner,id->new Lock[3])[slot]=new Lock(start,point);
    }
    static Strike target(UUID owner,Vec3 feet,float yaw,long started){
        Lock[] saved=locks.get(owner);
        Vec3[] result=new Vec3[3];
        for(int i=0;i<3;i++){
            Lock value=saved==null?null:saved[i];
            result[i]=value==null||value.start<started||value.start>started+COMBO?null
                    :value.point.subtract(feet).yRot((float)Math.toRadians(yaw));
        }
        return new Strike(result[0],result[1],result[2]);
    }
    private static Vec3 from(Matrix4f m,float x,float y,float z){
        var v=m.transformPosition(new Vector3f(x,y,z));return new Vec3(v.x,v.y,v.z);
    }
    static Vec3 tip(float f,int side,Strike strike,Vec3 rest){
        boolean heavy=f>=LOCK_HEAVY;float hit=heavy?HEAVY_IMPACT:side<0?CUT_ONE:CUT_TWO;
        float motion=heavy?ease(f,LOCK_HEAVY,54)*(1-ease(f,75,88)):
                ease(f,hit-11,hit-4)*(1-ease(f,hit+4,hit+11));
        if(motion==0)return rest;
        Vec3 aim=heavy?strike.heavy:side<0?strike.first:strike.second;
        if(aim==null)return rest;
        Vec3 forward=new Vec3(aim.x,0,aim.z).normalize();
        if(forward.lengthSqr()<.1)forward=new Vec3(0,0,1);
        Vec3 across=new Vec3(forward.z,0,-forward.x);
        if(heavy){
            float impact=ease(f,65,HEAVY_IMPACT);
            // Both blades hang above the called location. Their edges actually meet the floor on the hit tick.
            return rest.lerp(aim.add(across.scale(side*(.5+6*(1-impact)))).subtract(forward.scale(8*(1-impact)))
                    .add(0,.12+24*(1-impact),0),motion);
        }
        float offset=f<=hit?-7.4F*(1-ease(f,hit-4,hit)):7.4F*ease(f,hit,hit+3);
        var line=forward.scale(.72).add(across.scale(side*.69));
        return rest.lerp(aim.add(line.scale(offset)).add(0,.8+Math.abs(offset)*.2,0),motion);
    }
    static void draw(MultiBufferSource buffers,Matrix4f base,Matrix4f hand,int side,float f,Strike target){
        Matrix4f local=new Matrix4f(base).invert().mul(hand);
        Vec3 hilt=from(local,0,-.45F,0), resting=from(local,side*4,-19,5), point=tip(f,side,target,resting);
        Vec3 delta=point.subtract(hilt);
        if(delta.lengthSqr()<2)return;
        Vec3 flat=new Vec3(delta.z,0,-delta.x).normalize();
        if(flat.lengthSqr()<.2)flat=new Vec3(1,0,0);
        Vec3 spine=delta.normalize().cross(flat).normalize();
        Vec3 a=hilt.add(flat.scale(-.8)), b=hilt.add(flat.scale(.8));
        Vec3 left=hilt.lerp(point,.50).add(flat.scale(-1.08)),right=hilt.lerp(point,.50).add(flat.scale(1.78));
        Vec3 ridge=hilt.lerp(point,.46).add(spine.scale(.76));
        var steel=buffers.getBuffer(STEEL);
        face(steel,base,a,b,ridge,0xAF8259);face(steel,base,b,right,ridge,0xA65250);face(steel,base,a,ridge,left,0x471724);
        face(steel,base,left,ridge,point,0x812940);face(steel,base,ridge,right,point,0xCF9A85);
        var light=buffers.getBuffer(EDGE);
        face(light,base,right,ridge.lerp(right,.75),point,0xDA8787);
        float hit=f>=LOCK_HEAVY?HEAVY_IMPACT:side<0?CUT_ONE:CUT_TWO;
        if(f>hit-4&&f<hit+4){
            Vec3 previous=tip(f-1.7F,side,target,resting);
            var sweep=previous.lerp(point,.52);
            face(light,base,ridge,previous,sweep,0x9C3845,34);face(light,base,ridge,sweep,point,0xD46568,51);
        }
    }
    private static void face(VertexConsumer out,Matrix4f view,Vec3 a,Vec3 b,Vec3 c,int rgb){
        face(out,view,a,b,c,rgb,255);
    }
    static void face(VertexConsumer out,Matrix4f view,Vec3 a,Vec3 b,Vec3 c,int rgb,int alpha){
        Vec3 n=b.subtract(a).cross(c.subtract(a));
        if(n.lengthSqr()<1e-9)return;
        n=n.normalize();
        Vector3f normal=view.transformDirection(new Vector3f((float)n.x,(float)n.y,(float)n.z)).normalize();
        vertex(out,view,a,0,0,rgb,alpha,normal);vertex(out,view,b,1,0,rgb,alpha,normal);
        vertex(out,view,c,1,1,rgb,alpha,normal);vertex(out,view,c,1,1,rgb,alpha,normal);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Vec3 p,float u,float v,int c,int alpha,Vector3f n){
        out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(c>>16&255,c>>8&255,c&255,alpha).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n.x,n.y,n.z).endVertex();
    }
    private SanctumProjectedBlade(){}
}
