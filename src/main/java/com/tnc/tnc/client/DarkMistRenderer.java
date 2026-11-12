package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningBoltGeometry;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.magic.DarkFogCloudEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

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
        return entity.distanceToSqr(x,y,z) < (r+128)*(r+128) && frustum.isVisible(
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
        // Boss domains use a fixed-budget rim; the enlarged interior has no sheets or particles.
        boolean domain=radius>64;
        int layers=domain?2:3,segments=domain?32:19;
        // ★ 2026-11-11 作者：「黑雾的大小也要随着 t 级增长变大，你别只加宽度，高度也要加」
        //   以前半高写死 `1.1 + 档位×0.1`（t1 1.2 → t5 1.6，几乎不长 ✗）；
        //   现在半高由实体同步过来（= 半径 × 0.5：t1 2.25 → t5 4.5）✓，
        //   雾片也按半高散布，不再全挤在脚边 ✓
        float halfHeight = (float)Math.max(0.8D, entity.syncedHeight());
        double sheetBase = domain ? 0.65D : -halfHeight * 0.25D;
        double sheetStep = domain ? (0.65D + tier * 0.07D) : halfHeight * 0.60D;
        var sheets = new java.util.ArrayList<Vec3>(layers*segments);
        for (int layer=0;layer<layers;layer++) for (int i=0;i<segments;i++) {
            double angle=(domain?i*Math.PI*2/segments:i*2.399963)+layer*(domain?.08:1.7)+age*.0015;
            double distance=domain?radius*.96:radius*.69*Math.sqrt(i/18D);
            sheets.add(new Vec3(Math.cos(angle)*distance,
                    sheetBase+layer*sheetStep+Math.sin(age*.025+i*1.7+layer)*.16,
                    Math.sin(angle)*distance));
        }
        sheets.sort(java.util.Comparator.comparingDouble((Vec3 p)->p.distanceToSqr(camera)).reversed());

        // ★ 2026-11-06 作者：「雾加载在手之后，导致手一直看的见」
        //   ⇒ 顺序反过来：**先画手、再盖雾片**。雾片是半透明的（alpha≈.43），盖上去以后
        //   手只在雾里透出来一点 ⇒ 读作"雾里有手"，而不是"雾上面贴了只手" ✓。
        //   同时这里改用会测深度的 {@code DARK}（原来是 NO_DEPTH_TEST 的 DARK_OVERLAY，
        //   那层天生"永远压在最上面"，也是"手一直看得见"的另一半原因 ✗）。
        renderHands(entity, pose, buffers, age, radius, tier, appear);

        VertexConsumer out=buffers.getBuffer(RenderType.entityTranslucent(vapor));
        float halfWidth=(float)Math.max(1.2,radius*(domain?.08:.38));
        for (Vec3 center:sheets) {
            // A camera passing through a sheet must not see a hard black screen.
            float near=(float)Math.min(1,center.distanceTo(camera)/1.4);
            float alpha=appear*(domain?.42F:.43F+tier*.022F)*near;
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

    /** 雾心的"无主之手"数量（domain 雾很大，只留两只免得糊）。 */
    private static final int IDLE_HANDS_MAX = 3;

    /** 同时被"抓住"的敌人上限（再多就变成一片手，看不出形状 ✗）。 */
    private static final int GRAB_HANDS_MAX = 4;

    /**
     * ★ 2026-11-06 作者：「黑雾抓周围的敌人的距离要很远啊，起码比黑雾范围大」
     * ⇒ 手能够到 雾半径 × {@code GRAB_RANGE} 的地方去拉人（原来只到 0.85× 半径，
     * 在雾里靠外圈的人根本没人抓 ✗）。
     */
    private static final double GRAB_RANGE = 1.45D;

    /** Boss 领域雾（半径 > 64）里雾心的手数：领域很大，多给几只才像"里面全是手"。 */
    private static final int DOMAIN_HANDS = 6;

    private void renderHands(DarkFogCloudEntity entity, PoseStack pose, MultiBufferSource buffers,
                             double age, double radius, int tier, float appear) {
        boolean domain = radius > 64.0D;
        VertexConsumer vc = buffers.getBuffer(LightningRenderTypes.DARK);
        Matrix4f base = pose.last().pose();
        Vec3 camWorld = entityRenderDispatcher.camera.getPosition();
        Vec3 cam = camWorld.subtract(entity.position());
        Vec3 self = entity.position();

        // ① 雾心自己冒出来的手：错开时间、慢慢升起，然后张着（clench 轻微呼吸）
        int idle = domain ? DOMAIN_HANDS : IDLE_HANDS_MAX;
        // ★ Boss 领域雾半径 150 格，手的"半径比例"会把手甩到几十格外 ✗ ⇒ domain 用手数的**固定距离**，
        //   让手聚在中心附近（作者 2026-11-05："连着黑暗boss的也一起改了"）✓
        double inner = domain ? 2.6D : radius * 0.20D;
        double spread = domain ? 2.0D : radius * 0.06D;
        float handScale = domain ? 1.70F : 0.70F;
        for (int i = 0; i < idle; i++) {
            double ang = i * 2.399963D + age * 0.004D;
            double dist = inner + spread * i;
            float g = (float) Math.min(1.0D, Math.max(0.0D, (age - 4.0D - i * 9.0D) / 12.0D));
            if (g <= 0.01F) {
                continue;
            }
            float near = nearFade(camWorld, self.x + Math.cos(ang) * dist, self.y, self.z + Math.sin(ang) * dist);
            if (near <= 0.02F) {
                continue;
            }
            float clench = 0.18F + 0.12F * (float) Math.sin(age * 0.045D + i * 1.7D);
            pose.pushPose();
            pose.translate(Math.cos(ang) * dist, -0.55D, Math.sin(ang) * dist);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (ang * 57.2958D + 90.0D)));
            pose.scale(handScale, handScale, handScale);
            DarkHandLines.draw(pose, buffers, LightningRenderTypes.DARK,
                    g, 0.55F * appear * near, clench, entity.getId() * 31L + i * 7L);
            pose.popPose();
        }

        // ② 抓住雾里的敌人：手从目标脚下升起、五指收拢；手臂是一条从雾心拉过去的线 ✓
        if (!entity.level().isClientSide()) {
            return;                                  // 客户端才画（服务端渲染器不会被调用，保险起见）
        }
        double grab = radius * GRAB_RANGE;
        var box = new AABB(entity.getX() - grab, entity.getY() - 3.0D, entity.getZ() - grab,
                entity.getX() + grab, entity.getY() + 4.0D, entity.getZ() + grab);
        var found = new java.util.ArrayList<net.minecraft.world.entity.LivingEntity>();
        for (var candidate : entity.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (!candidate.isAlive() || candidate.isSpectator() || entity.isOwnedBy(candidate)) {
                continue;
            }
            // ★ Boss 领域雾的施法者就是巨人本人 ⇒ 别在自己身上抓一只（作者："连着黑暗boss的也一起改了"）
            if (candidate instanceof com.tnc.tnc.boss.TNDarkGiantEntity) {
                continue;
            }
            double dx = candidate.getX() - entity.getX();
            double dz = candidate.getZ() - entity.getZ();
            if (dx * dx + dz * dz > radius * radius * GRAB_RANGE * GRAB_RANGE) {
                continue;
            }
            found.add(candidate);
        }
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(entity)));
        int grabs = Math.min(GRAB_HANDS_MAX, found.size());
        for (int k = 0; k < grabs; k++) {
            var target = found.get(k);
            double dx = target.getX() - entity.getX();
            double dy = target.getY() - entity.getY();
            double dz = target.getZ() - entity.getZ();
            float g = (float) Math.min(1.0D, Math.max(0.0D, (age - 6.0D - k * 5.0D) / 10.0D));
            if (g <= 0.01F) {
                continue;
            }
            float clench = 0.82F + 0.14F * (float) Math.sin(age * 0.16D + k * 2.1D);
            float s = (domain ? 0.95F : 0.52F) + 0.07F * tier;
            float near = nearFade(camWorld, target.getX(), target.getY(), target.getZ());
            if (near <= 0.02F) {
                continue;
            }
            pose.pushPose();
            pose.translate(dx, dy - 0.72D, dz);
            // 手指朝上，略微朝雾心倾 ⇒ 像"从雾里伸手过来把他兜住" ✓
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (Math.atan2(dx, dz) * 57.2958D)));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-14.0F));
            pose.scale(s, s, s);
            DarkHandLines.draw(pose, buffers, LightningRenderTypes.DARK,
                    g, 0.88F * appear * near, clench, entity.getId() * 31L + 100L + k);
            pose.popPose();

            // 手臂（雾心 → 手）：只在手长出来之后拉过去，也是一节一节 ✓
            // 领域雾太大，几十格外的"手臂"会横穿半个屏幕 ⇒ 只给近处的人画 ✓
            double armReach = domain ? 20.0D : radius;
            if (g > 0.45F && dx * dx + dz * dz <= armReach * armReach) {
                float armGrow = (g - 0.45F) / 0.55F;
                LightningBoltGeometry.darkBolt(vc, base,
                        0.0D, 0.30D, 0.0D, dx, dy - 0.35D, dz,
                        entity.getId() * 131L + k * 977L, 0.055F, 0.70F * appear * near, armGrow,
                        cam.x, cam.y, cam.z);
            }
        }
    }
    /** 距离衰减：远处的雾中手淡出（不测深度 ⇒ 太远会像"穿墙"，用 alpha 压掉 ✓）。 */
    private static float nearFade(Vec3 cam, double x, double y, double z) {
        double d = cam.distanceTo(new Vec3(x, y, z));
        return (float) Math.max(0.0D, Math.min(1.0D, (48.0D - d) / 24.0D));
    }

    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float u,float v,float alpha,int light) {
        out.vertex(pose.last().pose(),x,y,0).color(.001F,.001F,.002F,alpha).uv(u,v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.last().normal(),0,0,1).endVertex();
    }
}
