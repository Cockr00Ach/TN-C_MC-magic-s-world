package com.tnc.tnc.magic.free.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.tnc.tnc.magic.free.WaterCastGeometry;

/** World-space brush lettering with shader-compatible emissive strokes and no background panel. */
final class ChantTextVisuals {
    static void clear(){ChantGlyphAtlas.clear();}
    static void draw(PoseStack pose,MultiBufferSource.BufferSource buffers,Vec3 camera,Player player,
                     ChantCaption caption,double now,float partial){
        var mc=Minecraft.getInstance();boolean self=player==mc.player;
        Vec3 look=new Vec3(mc.gameRenderer.getMainCamera().getLookVector());
        Vec3 right=WaterCastGeometry.right(look),up=right.cross(look).normalize();
        Vec3 at=self?camera.add(look.scale(3.8)).add(right.scale(.65)).add(up.scale(.78)):
                player.getPosition(partial).add(0,player.getBbHeight()+.55,0);
        var saved=RenderSystem.getShaderColor().clone();RenderSystem.setShaderColor(1,1,1,1);
        ChantGlyphAtlas.prepare(caption.text());
        pose.pushPose();pose.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        float scale=self?.017f:.024f;pose.scale(-scale,-scale,scale);
        int[] chars=caption.text().codePoints().toArray();
        for(int row=0;row*20<chars.length;row++){
            int start=row*20,end=Math.min(chars.length,start+20);float width=0;
            for(int i=start;i<end;i++)width+=ChantGlyphAtlas.glyph(chars[i]).width()+1.4f;
            float x=-width/2;
            for(int i=start;i<end;i++){
                var glyph=ChantGlyphAtlas.glyph(chars[i]);float w=glyph.width();double alpha=caption.alpha(i,now);
                if(alpha>.025){
                    pose.pushPose();double wave=Math.sin(now*.055+i*.68);
                    pose.translate(x+w/2,row*20+wave*.9+Math.pow((i-start-(end-start-1)*.5)/10,2)*1.8,0);
                    pose.mulPose(Axis.ZP.rotationDegrees((float)(Math.sin(now*.035+i*1.7)*3)));
                    for(int k=0;k<8;k++)ink(glyph,-w/2+(float)Math.cos(k*Math.PI/4)*.65f,
                            (float)Math.sin(k*Math.PI/4)*.65f,-.02f,alpha*.10,0x89e9ff,pose,buffers);
                    ink(glyph,-w/2+.35f,.4f,0,alpha*.6,0x122b36,pose,buffers);
                    ink(glyph,-w/2,0,.03f,alpha,0xfff1dd,pose,buffers);pose.popPose();
                }
                x+=w+1.4f;
            }
        }
        pose.popPose();buffers.endBatch();RenderSystem.setShaderColor(saved[0],saved[1],saved[2],saved[3]);
    }
    private static void ink(ChantGlyphAtlas.Glyph glyph,float x,float y,float z,double alpha,int color,PoseStack pose,MultiBufferSource buffers){
        if(alpha<.02)return;
        var out=buffers.getBuffer(ChantGlyphAtlas.pass());float u=glyph.u(),v=glyph.v(),uv=ChantGlyphAtlas.cellUv();
        float[][] corners={{x-2.5f,y-2.5f,u,v},{x-2.5f,y+21.5f,u,v+uv},{x+21.5f,y+21.5f,u+uv,v+uv},{x+21.5f,y-2.5f,u+uv,v}};
        for(float[] p:corners)out.vertex(pose.last().pose(),p[0],p[1],z)
                .color((color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,(float)alpha).uv(p[2],p[3])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(),0,0,1).endVertex();
    }
}
