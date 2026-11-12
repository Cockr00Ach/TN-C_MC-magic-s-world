package com.tnc.tnc.boss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.boss.TNWaterLeviathanEntity;
import com.tnc.tnc.client.WaterRitualSeal;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Huge silhouette, broad shadow, and a separate full-bright blue-gold rune layer.
 *
 * <p>2026-11-07 作者：「他要飞到很高的天空再消失……这个 boss 的虚影也可以发着光」：
 * 升空段 {@code castPose()==2} 的俯仰改由飞行曲线切线决定（爬升即抬头），
 * 到站 {@code castPose()==3} 后本体不是突然不见了，而是<b>整具模型转成自发光半透明虚影</b>
 * 再淡出——用的还是同一副 GeckoLib 模型和同一套骨骼，只换渲染类型/亮度/透明度。
 */
public final class TNWaterLeviathanRenderer extends GeoEntityRenderer<TNWaterLeviathanEntity> {
    /** 起飞阵：半径、从亮到暗的总时长（tick）。 */
    private static final double LAUNCH_SEAL_RADIUS = 11.0D;
    private static final int LAUNCH_SEAL_TICKS = 70;

    /** 起飞点（世界坐标），第一次进入升空姿态时锁定；离开仪式就清空。 */
    private Vec3 launchAnchor;
    private long launchStart;

    public TNWaterLeviathanRenderer(EntityRendererProvider.Context context) {
        super(context, new TNWaterLeviathanGeoModel());
        this.withScale(TNWaterLeviathanEntity.SIZE_SCALE);
        this.shadowRadius = 3.2F*TNWaterLeviathanEntity.SIZE_SCALE;
        this.addRenderLayer(new TNWaterLeviathanEmissiveLayer(this));
    }

    @Override
    public void render(TNWaterLeviathanEntity entity, float yaw, float partial,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 虚影淡完之后整具模型不再提交，省掉一次 76 格巨兽的骨骼遍历。
        if (entity.ghostAlpha()<=.01F) return;
        super.render(entity,yaw,partial,poseStack,bufferSource,packedLight);
        drawLaunchSeal(entity,poseStack,bufferSource,partial);
    }

    @Override
    protected void applyRotations(TNWaterLeviathanEntity entity, com.mojang.blaze3d.vertex.PoseStack stack,
                                  float age, float yaw, float partial) {
        super.applyRotations(entity,stack,age,yaw,partial);
        boolean traveling=!entity.mouthOpen() && entity.castPose()!=2 && entity.castPose()!=3;
        float pitch=entity.castingPitch(partial)+(traveling?entity.flightPitch(partial):0F);
        stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
        if(traveling) stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.flightBank(partial)));
    }

    /** 虚影期间不用环境光：整具模型走自发光贴图，天再黑也一直在发亮。 */
    @Override
    public void actuallyRender(PoseStack poseStack, TNWaterLeviathanEntity entity, BakedGeoModel model,
                               RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        int light=entity.ghosting() ? LightTexture.FULL_BRIGHT : packedLight;
        if (entity.ghosting()) {
            // 虚影偏冷蓝：蓝金鳞甲压成“水做的灵体”，不是一团糊掉的原贴图。
            red*=.72F; green*=.94F; blue=Math.min(1F,blue*1.12F);
        }
        super.actuallyRender(poseStack,entity,model,renderType,bufferSource,buffer,isReRender,
                partialTick,light,packedOverlay,red,green,blue,alpha);
    }

    @Override
    public RenderType getRenderType(TNWaterLeviathanEntity entity, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return entity.ghosting() ? RenderType.entityTranslucentEmissive(texture)
                : super.getRenderType(entity,texture,bufferSource,partialTick);
    }

    /** 逐帧透明度：0 = 实体，1 = 完全消失。 */
    @Override
    public Color getRenderColor(TNWaterLeviathanEntity entity, float partialTick, int packedLight) {
        float alpha=entity.ghostAlpha();
        return alpha>=.999F ? super.getRenderColor(entity,partialTick,packedLight)
                : Color.ofRGBA(1F,1F,1F,alpha);
    }

    /** 起飞点脚下先亮一张水法阵——“先出现法阵，再升空，最后才是巨剑”。 */
    private void drawLaunchSeal(TNWaterLeviathanEntity entity, PoseStack poseStack,
                                MultiBufferSource buffers, float partial) {
        boolean ritual=entity.castPose()==2 || entity.castPose()==3;
        if (!ritual) { launchAnchor=null; return; }
        if (launchAnchor==null) {
            launchAnchor=entity.position();
            launchStart=entity.level().getGameTime();
            return;
        }
        double age=entity.level().getGameTime()+partial-launchStart;
        if (age>=LAUNCH_SEAL_TICKS) return;
        float alpha=(float)Math.min(1D,Math.min(age/8D,(LAUNCH_SEAL_TICKS-age)/18D));
        if (alpha<=.01F) return;
        double x=Mth.lerp(partial,entity.xo,entity.getX());
        double y=Mth.lerp(partial,entity.yo,entity.getY());
        double z=Mth.lerp(partial,entity.zo,entity.getZ());
        poseStack.pushPose();
        poseStack.translate(launchAnchor.x-x,launchAnchor.y-y+.15D,launchAnchor.z-z);
        WaterRitualSeal.launch(buffers,poseStack,Vec3.ZERO,LAUNCH_SEAL_RADIUS,age*1.4D,alpha*.92F);
        poseStack.popPose();
    }
}
