package com.tnc.tnc.world.ink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.world.ink.InkPaintingPattern;
import com.tnc.tnc.world.ink.InkCraneAttackRules;
import com.tnc.tnc.world.ink.InkRealmTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** High-detail, transparent painting decals resting on the flat paper, not billboards/blocks. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkPaintingRenderer {
    private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("tnc",InkPaintingPattern.TEXTURE);
    private static final ResourceLocation NEUTRAL=ResourceLocation.fromNamespaceAndPath("tnc","dynamic/ink_shanshui_neutral");
    private static boolean textureReady;
    private static final MultiBufferSource.BufferSource FLOOR_BUFFERS=MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(262144));
    private InkPaintingRenderer() {}
    private static ResourceLocation texture() {
        if(!textureReady) {
            var mc=Minecraft.getInstance();
            try(var input=mc.getResourceManager().open(ATLAS)) {
                var image=com.mojang.blaze3d.platform.NativeImage.read(input);
                for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++)
                    image.setPixelRGBA(x,y,InkPaintingPattern.neutralColor(image.getPixelRGBA(x,y)));
                mc.getTextureManager().register(NEUTRAL,new net.minecraft.client.renderer.texture.DynamicTexture(image));
                textureReady=true;
            }catch(java.io.IOException e){throw new IllegalStateException("Cannot load ink painting material",e);}
        }
        return NEUTRAL;
    }
    private static RenderType material(){texture();return InkPaintingRenderTypes.paint();}
    @SubscribeEvent public static void renderFloor(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || mc.level==null
                || !mc.level.dimension().equals(InkRealmTravel.INK_REALM))return;
        var camera=event.getCamera().getPosition();
        var pose=event.getPoseStack();pose.pushPose();
        try {
            pose.translate(-camera.x,-camera.y,-camera.z);
            var buffers=FLOOR_BUFFERS;
            double range=Math.min(256,mc.options.getEffectiveRenderDistance()*16.0);
            int minX=(int)Math.floor((camera.x-range)/InkPaintingPattern.CELL);
            int maxX=(int)Math.floor((camera.x+range)/InkPaintingPattern.CELL);
            int minZ=(int)Math.floor((camera.z-range)/InkPaintingPattern.CELL);
            int maxZ=(int)Math.floor((camera.z+range)/InkPaintingPattern.CELL);
            for(int cx=minX;cx<=maxX;cx++)for(int cz=minZ;cz<=maxZ;cz++) {
                int layer=0;
                for(var stamp:InkPaintingPattern.stamps(cx,cz)) {
                    // Split along chunk edges: never show an unloaded patch or paint over a player's construction.
                    double left=stamp.x()-stamp.width()/2,top=stamp.z()-stamp.height()/2;
                    for(int x=(int)Math.floor(left/16)*16;x<left+stamp.width();x+=16)
                        for(int z=(int)Math.floor(top/16)*16;z<top+stamp.height();z+=16) {
                            if(!mc.level.hasChunk(x>>4,z>>4))continue;
                            double x0=Math.max(x,left),x1=Math.min(x+16,left+stamp.width());
                            double z0=Math.max(z,top),z1=Math.min(z+16,top+stamp.height());
                            if(Math.abs((x0+x1)/2-camera.x)>range || Math.abs((z0+z1)/2-camera.z)>range)continue;
                            patch(buffers.getBuffer(material()),pose.last(),stamp,
                                    x0,x1,z0,z1,mc.level.getMinBuildHeight()+5.015+layer*.003);
                        }
                    layer++;
                }
            }
            buffers.endBatch(material());
        }finally {pose.popPose();}
    }
    private static void patch(VertexConsumer out,PoseStack.Pose pose,InkPaintingPattern.Stamp s,
                              double x0,double x1,double z0,double z1,double y) {
        float u0=(float)((x0-s.x())/s.width()+.5),u1=(float)((x1-s.x())/s.width()+.5);
        if(s.mirror()){u0=1-u0;u1=1-u1;}
        float v0=(float)((z0-s.z())/s.height()+.5),v1=(float)((z1-s.z())/s.height()+.5);
        float a00=s.opacity()*InkPaintingPattern.edgeOpacity(u0,v0),a01=s.opacity()*InkPaintingPattern.edgeOpacity(u0,v1);
        float a10=s.opacity()*InkPaintingPattern.edgeOpacity(u1,v0),a11=s.opacity()*InkPaintingPattern.edgeOpacity(u1,v1);
        u0=(s.region()%2+u0)/2;u1=(s.region()%2+u1)/2;
        v0=(s.region()/2+v0)/2;v1=(s.region()/2+v1)/2;
        vertex(out,pose,x0,y,z0,u0,v0,a00);vertex(out,pose,x0,y,z1,u0,v1,a01);
        vertex(out,pose,x1,y,z1,u1,v1,a11);vertex(out,pose,x1,y,z0,u1,v0,a10);
    }
    static void preview(PoseStack pose,MultiBufferSource buffers) {
        var out=buffers.getBuffer(material());
        for(var s:InkPaintingPattern.stamps(0,0))patch(out,pose.last(),s,s.x()-s.width()/2,s.x()+s.width()/2,
                s.z()-s.height()/2,s.z()+s.height()/2,.02+s.region()*.01);
    }
    /** Mountain/cloud illustrations suspended after an explosion: flat ethereal paintings, no solid terrain. */
    static void aftermath(double age,PoseStack pose,MultiBufferSource buffers) {
        double t=age-100;
        if(t<0 || t>=120)return;
        float alpha=(float)(Math.min(1,t/12)*Math.min(1,(120-t)/35));
        var out=buffers.getBuffer(material());
        for(int i=0;i<InkCraneAttackRules.PAINTING_FACES;i++) {
            double angle=i*Math.PI*2/InkCraneAttackRules.PAINTING_FACES+.25;
            pose.pushPose();
            pose.translate(Math.cos(angle)*InkCraneAttackRules.PAINTING_RADIUS,1.5+Math.sin(age*.02+i)*.45,
                    Math.sin(angle)*InkCraneAttackRules.PAINTING_RADIUS);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float)(-angle+Math.PI/2)));
            upright(out,pose.last(),i%2,InkCraneAttackRules.PAINTING_WIDTH,InkCraneAttackRules.PAINTING_HEIGHT,alpha*.52F);
            pose.translate(Math.sin(age*.008+i)*2,-.5,1.2);
            upright(out,pose.last(),2+i%2,42,12,alpha*.66F);
            pose.popPose();
        }
        // A final painting imprinted on the ground under the fading cloud layers.
        var stamp=new InkPaintingPattern.Stamp(0,0,72,57,1,alpha*.65F,false);
        patch(out,pose.last(),stamp,-36,36,-28.5,28.5,.035);
    }
    private static void upright(VertexConsumer out,PoseStack.Pose pose,int region,double width,double height,float alpha) {
        float u=region%2*.5F,v=region/2*.5F;
        for(int x=0;x<8;x++)for(int y=0;y<8;y++) {
            float a=x/8F,b=(x+1)/8F,c=y/8F,d=(y+1)/8F;
            vertex(out,pose,(a-.5)*width,(1-c)*height,0,u+a*.5F,v+c*.5F,alpha*InkPaintingPattern.edgeOpacity(a,c));
            vertex(out,pose,(a-.5)*width,(1-d)*height,0,u+a*.5F,v+d*.5F,alpha*InkPaintingPattern.edgeOpacity(a,d));
            vertex(out,pose,(b-.5)*width,(1-d)*height,0,u+b*.5F,v+d*.5F,alpha*InkPaintingPattern.edgeOpacity(b,d));
            vertex(out,pose,(b-.5)*width,(1-c)*height,0,u+b*.5F,v+c*.5F,alpha*InkPaintingPattern.edgeOpacity(b,c));
        }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,double x,double y,double z,float u,float v,float alpha) {
        out.vertex(pose.pose(),(float)x,(float)y,(float)z).color(1F,1F,1F,alpha).uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0,1,0).endVertex();
    }
}
