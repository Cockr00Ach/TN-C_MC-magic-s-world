package com.tnc.tnc.mana.sight.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Soft textured grains go through Minecraft/Oculus' ordinary transparent particle pipeline. */
final class ManaSightParticle extends TextureSheetParticle {
    final long born;
    private final Vec3 start,target;
    private final double phase,radius,angle;
    private final int motion;
    private final boolean sightOnly;
    private final float opacity,size;
    float scenicRange;
    ManaSightParticle(ClientLevel level,Vec3 at,Vec3 target,int color,int texture,int motion,float size,float opacity,int life,double phase,SpriteSet sprites){
        this(level,at,target,color,texture,motion,size,opacity,life,phase,sprites,true);
    }
    ManaSightParticle(ClientLevel level,Vec3 at,Vec3 target,int color,int texture,int motion,float size,float opacity,int life,double phase,SpriteSet sprites,boolean sightOnly){
        super(level,at.x,at.y,at.z);this.sightOnly=sightOnly;
        this.start=at;this.target=target;this.motion=motion;this.phase=phase;this.opacity=opacity;this.size=size;this.lifetime=life;this.born=level.getGameTime();
        this.radius=Math.hypot(at.x-target.x,at.z-target.z);this.angle=Math.atan2(at.z-target.z,at.x-target.x);
        this.hasPhysics=false;this.alpha=0;this.quadSize=size;setSize(size*2,size*2);
        setColor((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f);setSprite(sprites.get(texture,2));
    }
    double distanceToSqr(Vec3 at){return at.distanceToSqr(x,y,z);}
    @Override public ParticleRenderType getRenderType(){return ManaRadiance.TYPES[Math.floorMod((int)phase,2)];}
    @Override protected int getLightColor(float partial){return LightTexture.FULL_BRIGHT;}
    @Override public void tick(){
        xo=x;yo=y;zo=z;oRoll=roll;
        if(sightOnly&&!ManaSightClient.enabled()||Minecraft.getInstance().level!=level||age++>=lifetime){remove();return;}
        double t=age/(double)lifetime;
        if(motion==2){
            Vec3 direction=target.subtract(start),side=direction.cross(new Vec3(0,1,0)).normalize();
            Vec3 p=start.lerp(target,t).add(side.scale(Math.sin(t*Math.PI)*Math.cos(phase+t*8)*.17)).add(0,Math.sin(t*Math.PI)*Math.sin(phase+t*6)*.16,0);setPos(p.x,p.y,p.z);
        }else if(motion==3){Vec3 p=start.lerp(target,t).add(Math.sin(phase+t*5)*t*.2,Math.sin(t*Math.PI)*.2,Math.cos(phase+t*5)*t*.2);setPos(p.x,p.y,p.z);}
        else if(motion==1){double a=angle+age*.012,r=radius*(1+.09*Math.sin(phase+age*.04));setPos(target.x+Math.cos(a)*r,start.y+age*.019+Math.sin(phase+age*.032)*.24,target.z+Math.sin(a)*r);}
        else setPos(start.x+age*.007+Math.sin(phase+age*.026)*.24,start.y+age*.011,start.z+Math.cos(phase+age*.021)*.32);
        float in=Math.min(1,age/Math.max(1,Math.min(6,lifetime/4f))),out=Math.min(1,(lifetime-age)/Math.max(1,Math.min(12,lifetime/3f)));
        alpha=opacity*in*out*(.82f+.18f*(float)Math.sin(phase+age*.12));
        quadSize=size*(motion<2?.85f+.2f*(float)Math.sin(phase+age*.055):1);
        roll=(float)(phase+age*.005);
    }
    @Override public void render(VertexConsumer out,Camera camera,float partial){
        if(ManaRadiance.skipOpaque||sightOnly&&!ManaSightClient.enabled()||Minecraft.getInstance().level!=level)return;
        float previous=alpha;double d=camera.getPosition().distanceTo(new Vec3(x,y,z)),range=scenicRange>0?scenicRange:sightOnly?ManaSightParticles.range():64;
        alpha*=(sightOnly?ManaSightParticles.fade():1)*Mth.clamp((float)((range-d)/18),0,1)*Mth.clamp((float)((d-.35)/1.1),0,1);
        if(alpha>.01f)renderRadiant(out,camera,partial,(float)d);alpha=previous;
    }
    private void renderRadiant(VertexConsumer out,Camera camera,float partial,float distance){
        var eye=camera.getPosition();float px=(float)(Mth.lerp(partial,xo,x)-eye.x),py=(float)(Mth.lerp(partial,yo,y)-eye.y),pz=(float)(Mth.lerp(partial,zo,z)-eye.z);
        var rotation=new org.joml.Quaternionf(camera.rotation()).rotateZ(Mth.lerp(partial,oRoll,roll));
        float scale=getQuadSize(partial)*(sightOnly?Mth.clamp(.85f+distance/60,1,2):1);
        org.joml.Vector3f[] corners={new org.joml.Vector3f(-1,-1,0),new org.joml.Vector3f(-1,1,0),new org.joml.Vector3f(1,1,0),new org.joml.Vector3f(1,-1,0)};
        float[] us={getU1(),getU1(),getU0(),getU0()},vs={getV1(),getV0(),getV0(),getV1()};
        for(int i=0;i<4;i++){var v=corners[i].rotate(rotation).mul(scale);out.vertex(px+v.x,py+v.y,pz+v.z).color(rCol,gCol,bCol,alpha).uv(us[i],vs[i]).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0,1,0).endVertex();}
    }
}
