package com.tnc.tnc.arcana.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.LivingEntity;
import com.tnc.tnc.mana.sight.client.ManaSightClient;

/** Uses the creature's actual posed model quads, not a substitute bounding box. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ArcanaGhosts {
    public static long renderedQuads;
    private static boolean worldEntities;
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource OUTLINES=net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(262144));
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void flush(net.minecraftforge.client.event.RenderLevelStageEvent e){var s=e.getStage();if(s==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY||s==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)worldEntities=true;if(s==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES||s==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL){worldEntities=false;OUTLINES.endBatch();}}
    public static boolean active(LivingEntity e){return worldEntities&&net.minecraft.client.Minecraft.getInstance().screen==null&&ManaSightClient.enabled()&&ArcanaClient.sanity>80&&e instanceof net.minecraft.world.entity.Mob
            &&!(e instanceof com.tnc.tnc.arcana.DimensionalSpirit)&&!(e instanceof com.tnc.tnc.arcana.VoidDevourer)
            &&!(e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)&&!(e instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
            &&net.minecraft.client.Minecraft.getInstance().player!=null&&e.distanceToSqr(net.minecraft.client.Minecraft.getInstance().player)<1600;}
    public static float fade(){return (float)Math.max(0,Math.min(1,(ArcanaClient.sanity-80)/20));}
    public static VertexConsumer wrap(VertexConsumer solid){return new Wire(solid,OUTLINES.getBuffer(net.minecraft.client.renderer.RenderType.lines()),.2f+fade()*.6f);}
    private static final class Wire implements VertexConsumer {
        final VertexConsumer solid,lines;final float alpha;final double[][] quad=new double[4][3];int index;
        Wire(VertexConsumer solid,VertexConsumer lines,float alpha){this.solid=solid;this.lines=lines;this.alpha=alpha;}
        @Override public VertexConsumer vertex(double x,double y,double z){quad[index][0]=x;quad[index][1]=y;quad[index][2]=z;solid.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){solid.color(r,g,b,a);return this;}
        @Override public VertexConsumer uv(float u,float v){solid.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){solid.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){solid.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){solid.normal(x,y,z);return this;}
        @Override public void defaultColor(int r,int g,int b,int a){solid.defaultColor(r,g,b,a);}
        @Override public void unsetDefaultColor(){solid.unsetDefaultColor();}
        @Override public void endVertex(){solid.endVertex();if(++index<4)return;index=0;renderedQuads++;
            for(int i=0;i<4;i++){var a=quad[i];var b=quad[(i+1)%4];double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],n=Math.sqrt(dx*dx+dy*dy+dz*dz);if(n<1e-6)continue;
                for(var p:new double[][]{a,b})lines.vertex(p[0],p[1],p[2]).color(.4f,.92f,1,alpha).normal((float)(dx/n),(float)(dy/n),(float)(dz/n)).endVertex();}
        }
    }
    private ArcanaGhosts(){}
}
