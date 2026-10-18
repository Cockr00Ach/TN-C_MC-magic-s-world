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
    // Native atlas and shader. Oculus 1.8 queues unknown types in BOTH phases; omit opaque geometry.
    // Additive blend gives luminous feathered edges instead of dark smoke halos in daylight.
    private static boolean skipOpaque;
    private static final ParticleRenderType GLOW=new ParticleRenderType(){
        public void begin(com.mojang.blaze3d.vertex.BufferBuilder b,net.minecraft.client.renderer.texture.TextureManager t){
            skipOpaque=ManaParticleShaderCompat.opaquePass();
            ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(b,t);
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        }
        public void end(com.mojang.blaze3d.vertex.Tesselator t){t.end();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();com.mojang.blaze3d.systems.RenderSystem.depthMask(true);}
        public String toString(){return "TN_C_MANA_SIGHT_PARTICLES";}
    };
    final long born;
    private final Vec3 start,target;
    private final double phase,radius,angle;
    private final int motion;
    private final boolean sightOnly;
    private final float opacity,size;
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
    @Override public ParticleRenderType getRenderType(){return GLOW;}
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
        if(skipOpaque||sightOnly&&!ManaSightClient.enabled()||Minecraft.getInstance().level!=level)return;
        float previous=alpha;double d=camera.getPosition().distanceTo(new Vec3(x,y,z));
        alpha*=(sightOnly?ManaSightParticles.fade():1)*Mth.clamp((float)((48-d)/10),0,1)*Mth.clamp((float)((d-.35)/1.1),0,1);
        if(alpha>.01f)super.render(out,camera,partial);alpha=previous;
    }
}
