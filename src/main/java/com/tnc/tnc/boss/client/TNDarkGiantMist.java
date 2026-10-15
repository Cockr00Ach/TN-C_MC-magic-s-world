package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNDarkGiantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A fixed budget of broad, near-black vapor sheets follows the living boss. */
final class TNDarkGiantMist {
    private static ResourceLocation texture;
    private static ResourceLocation texture(){
        if(texture!=null)return texture;
        var image=new com.mojang.blaze3d.platform.NativeImage(128,128,false);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++){
            double u=(x-63.5)/63.5,v=(y-63.5)/63.5;
            double edge=Math.max(0,1-u*u-v*v);
            double wisps=.72+.16*Math.sin(u*12+v*8)+.12*Math.cos(v*18-u*5);
            int a=(int)(255*edge*edge*wisps);
            image.setPixelRGBA(x,y,(a<<24)|0xFFFFFF);
        }
        var dynamic=new net.minecraft.client.renderer.texture.DynamicTexture(image);
        dynamic.setFilter(true,false);
        texture=Minecraft.getInstance().getTextureManager().register("tnc_giant_black_vapor",dynamic);
        return texture;
    }
    static void render(TNDarkGiantEntity boss,float partial,PoseStack pose,MultiBufferSource buffers,
                       int light,EntityRenderDispatcher dispatcher){
        if(boss.isInvisible())return;
        double age=boss.tickCount+partial,height=boss.getBbHeight();
        Vec3 camera=dispatcher.camera.getPosition().subtract(boss.getPosition(partial));
        var sheets=new java.util.ArrayList<Vec3>(48);
        for(int layer=0;layer<6;layer++)for(int i=0;i<8;i++){
            double angle=i*Math.PI/4+layer*.7+age*(layer%2==0?.006:-.005);
            double radius=boss.getBbWidth()*(.48+.09*Math.sin(age*.018+i*1.9+layer));
            sheets.add(new Vec3(Math.cos(angle)*radius,
                    height*(.08+layer*.15)+Math.sin(age*.025+i+layer)*1.4,Math.sin(angle)*radius));
        }
        sheets.sort(java.util.Comparator.comparingDouble((Vec3 p)->p.distanceToSqr(camera)).reversed());
        VertexConsumer out=buffers.getBuffer(RenderType.entityTranslucent(texture()));
        float fade=(float)Math.min(1,age/20)*Math.max(0,1-boss.deathTime/20F);
        for(Vec3 center:sheets){
            float alpha=fade*(boss.isPhaseTwo()?.68F:.56F)*(float)Math.min(1,center.distanceTo(camera)/5);
            float w=boss.getBbWidth()*.46F,h=(float)height*.15F;
            pose.pushPose();pose.translate(center.x,center.y,center.z);pose.mulPose(dispatcher.cameraOrientation());
            vertex(out,pose,-w,-h,0,1,alpha,light);vertex(out,pose,w,-h,1,1,alpha,light);
            vertex(out,pose,w,h,1,0,alpha,light);vertex(out,pose,-w,h,0,0,alpha,light);pose.popPose();
        }
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float u,float v,float a,int light){
        out.vertex(pose.last().pose(),x,y,0).color(.002F,.001F,.003F,a).uv(u,v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.last().normal(),0,0,1).endVertex();
    }
}
