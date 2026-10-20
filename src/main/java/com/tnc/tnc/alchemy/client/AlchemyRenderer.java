package com.tnc.tnc.alchemy.client;
import com.tnc.tnc.alchemy.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
public final class AlchemyRenderer implements BlockEntityRenderer<AlchemyEntity>{
    private static final ResourceLocation WHITE=ResourceLocation.parse("tnc:textures/misc/alchemy_white.png");
    private static final int EMBER=0xffb961;
    private final java.util.Map<net.minecraft.core.BlockPos,Long> particles=new java.util.HashMap<>();
    public AlchemyRenderer(BlockEntityRendererProvider.Context c){}
    public static void vertex(VertexConsumer v,PoseStack pose,float x,float y,float z,float u,float w,int color){v.vertex(pose.last().pose(),x,y,z).color(color>>16&255,color>>8&255,color&255,color>>>24).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xf000f0).normal(pose.last().normal(),0,1,0).endVertex();}
    public static void ring(PoseStack p,MultiBufferSource b,double time,int color,float y,float radius){var v=b.getBuffer(RenderType.entityTranslucent(WHITE));for(int i=0;i<48;i++){double a=i*Math.PI/24+time*.02,c=(i+1)*Math.PI/24+time*.02;float r=radius,s=radius-.012f;vertex(v,p,.5f+(float)Math.cos(a)*r,y,.5f+(float)Math.sin(a)*r,0,0,color);vertex(v,p,.5f+(float)Math.cos(a)*s,y,.5f+(float)Math.sin(a)*s,0,1,color);vertex(v,p,.5f+(float)Math.cos(c)*s,y,.5f+(float)Math.sin(c)*s,1,1,color);vertex(v,p,.5f+(float)Math.cos(c)*r,y,.5f+(float)Math.sin(c)*r,1,0,color);}}
    @Override public void render(AlchemyEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){if(be.getLevel()==null)return;double time=be.getLevel().getGameTime()+partial;int color=0xff000000|be.color;var v=buffers.getBuffer(RenderType.entityTranslucent(WHITE));float y=.63f+(float)Math.sin(time*.05)*.008f;
        for(int i=0;i<32;i++){double a=i*Math.PI/16,c=(i+1)*Math.PI/16;vertex(v,pose,.5f,y,.5f,.5f,.5f,0xd0000000|be.color);vertex(v,pose,.5f+(float)Math.cos(c)*.31f,y,.5f+(float)Math.sin(c)*.31f,1,0,0xd0000000|be.color);vertex(v,pose,.5f+(float)Math.cos(a)*.31f,y,.5f+(float)Math.sin(a)*.31f,0,1,0xd0000000|be.color);vertex(v,pose,.5f,y,.5f,.5f,.5f,0xd0000000|be.color);}
        if(!be.working)return;ring(pose,buffers,time,color,1.06f,.53f);ring(pose,buffers,-time,0xaa9ce6da,.84f,.43f);
        for(int i=0;i<4;i++){var item=be.inventory.getItem(i);if(item.isEmpty())continue;double a=time*.025+i*Math.PI/2;pose.pushPose();pose.translate(.5+Math.cos(a)*.4,1.13+Math.sin(time*.06+i)*.09,.5+Math.sin(a)*.4);pose.mulPose(com.mojang.math.Axis.YP.rotation((float)-a));pose.scale(.36f,.36f,.36f);Minecraft.getInstance().getItemRenderer().renderStatic(item,ItemDisplayContext.GROUND,0xf000f0,OverlayTexture.NO_OVERLAY,pose,buffers,be.getLevel(),i);pose.popPose();}
        long tick=be.getLevel().getGameTime();if(tick%4==0&&particles.getOrDefault(be.getBlockPos(),-1L)!=tick){particles.put(be.getBlockPos(),tick);if(particles.size()>128)particles.clear();var p=be.getBlockPos();var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((be.color>>16&255)/255f,(be.color>>8&255)/255f,(be.color&255)/255f),1.1f);for(int i=0;i<7;i++){double a=time*.035+i*Math.PI/3.5;be.getLevel().addParticle(dust,p.getX()+.5+Math.cos(a)*.25,p.getY()+.75,p.getZ()+.5+Math.sin(a)*.25,Math.cos(a)*.012,.025,Math.sin(a)*.012);}var heart=net.minecraft.world.phys.Vec3.atCenterOf(p).add(0,.38,0);for(int i=0;i<5;i++){double a=time*.045+i*Math.PI*.4;var at=heart.add(Math.cos(a)*.32,-.1+Math.sin(a)*.1,Math.sin(a)*.32);com.tnc.tnc.mana.sight.client.ManaSightParticles.effect(at,heart,be.color,i%4==0?1:0,1,.035f+i*.005f,28);}
            for(int i=0;i<2;i++){double a=time*.03+i*Math.PI;var at=heart.add(Math.cos(a)*.4,.35,Math.sin(a)*.4);com.tnc.tnc.mana.sight.client.ManaSightParticles.effect(at,heart,0xc5f2df,0,2,.045f,22);}
            com.tnc.tnc.mana.sight.client.ManaSightParticles.effect(heart.add(.1,-.68,0),heart.add(0,-.3,0),EMBER,1,3,.055f,20);
            be.getLevel().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,p.getX()+.5,p.getY()+.18,p.getZ()+.5,0,.01,0);}
    }
    @Override public int getViewDistance(){return 40;}
}
