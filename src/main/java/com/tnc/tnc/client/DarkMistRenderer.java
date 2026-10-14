package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.DarkFogCloudEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Persistent soft vapor sheets, independent of particle settings and emission ticks. */
public final class DarkMistRenderer extends EntityRenderer<DarkFogCloudEntity> {
    private static ResourceLocation vapor;
    public DarkMistRenderer(EntityRendererProvider.Context context) {
        super(context);
        if (vapor == null) {
            // Smooth code-generated vapor avoids the coarse pixels in vanilla ink/smoke sprites.
            var image = new com.mojang.blaze3d.platform.NativeImage(128,128,false);
            for (int y=0;y<128;y++) for (int x=0;x<128;x++) {
                double u=(x-63.5)/63.5,v=(y-63.5)/63.5;
                double warp=.07*Math.sin(u*11+v*7)+.04*Math.cos(v*17-u*8);
                double edge=Math.max(0,1-u*u-v*v+warp*(1-u*u)*(1-v*v));
                double wisps=.75+.13*Math.sin(u*13+v*9)+.12*Math.cos(v*19-u*6);
                int alpha=(int)(255*Math.min(1,edge*edge*wisps));
                image.setPixelRGBA(x,y,(alpha<<24)|0xFFFFFF);
            }
            var texture=new net.minecraft.client.renderer.texture.DynamicTexture(image);
            texture.setFilter(true,false);
            vapor=net.minecraft.client.Minecraft.getInstance().getTextureManager().register("tnc_dark_mist",texture);
        }
    }
    @Override public ResourceLocation getTextureLocation(DarkFogCloudEntity entity) { return vapor; }
    @Override public boolean shouldRender(DarkFogCloudEntity entity, Frustum frustum, double x, double y, double z) {
        double r = entity.syncedRadius();
        return entity.distanceToSqr(x,y,z) < 128*128 && frustum.isVisible(
                new AABB(entity.getX()-r,entity.getY()-.5,entity.getZ()-r,
                        entity.getX()+r,entity.getY()+6,entity.getZ()+r));
    }
    @Override public void render(DarkFogCloudEntity entity, float yaw, float partial,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        double radius = entity.syncedRadius();
        if (radius <= 0) return;
        double age = entity.tickCount + partial;
        float appear = (float)Math.min(1,age/12);
        int tier = Math.max(1,entity.syncedTier());
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partial));
        // Fixed geometry budget: 57 broad overlapping sheets, no accumulating particles.
        var sheets = new java.util.ArrayList<Vec3>(57);
        for (int layer=0;layer<3;layer++) for (int i=0;i<19;i++) {
            double angle=i*2.399963+layer*1.7+age*.0015;
            double distance=radius*.69*Math.sqrt(i/18D);
            sheets.add(new Vec3(Math.cos(angle)*distance,
                    .65+layer*(.65+tier*.07)+Math.sin(age*.025+i*1.7+layer)*.16,
                    Math.sin(angle)*distance));
        }
        sheets.sort(java.util.Comparator.comparingDouble((Vec3 p)->p.distanceToSqr(camera)).reversed());
        VertexConsumer out=buffers.getBuffer(RenderType.entityTranslucent(vapor));
        float halfWidth=(float)Math.max(1.2,radius*.38);
        float halfHeight=1.1F+tier*.1F;
        for (Vec3 center:sheets) {
            // A camera passing through a sheet must not see a hard black screen.
            float near=(float)Math.min(1,center.distanceTo(camera)/1.4);
            float alpha=appear*(.43F+tier*.022F)*near;
            pose.pushPose();
            pose.translate(center.x,center.y,center.z);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            vertex(out,pose,-halfWidth,-halfHeight,0,1,alpha,light);
            vertex(out,pose,halfWidth,-halfHeight,1,1,alpha,light);
            vertex(out,pose,halfWidth,halfHeight,1,0,alpha,light);
            vertex(out,pose,-halfWidth,halfHeight,0,0,alpha,light);
            pose.popPose();
        }
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float u,float v,float alpha,int light) {
        out.vertex(pose.last().pose(),x,y,0).color(.001F,.001F,.002F,alpha).uv(u,v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.last().normal(),0,0,1).endVertex();
    }
}
