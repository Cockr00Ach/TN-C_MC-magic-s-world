package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.client.lightning.LightningRenderTypes;
import com.tnc.tnc.magic.TNTornadoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * ★ <b>龙卷风的渲染器</b>（风系范围风链 t3+）—— 2026-11-09 起是<b>线</b>，不是模型 ✓
 *
 * <p>作者原话：「你可以优化一下风法的龙卷风吗，我想做成就是，我们的闪电嘛，他不是一个一个线吗，
 * 我想这个线螺旋缠绕起来变成龙卷风，你看过鬼灭之刃吗，就是那种感觉，可以有明确的锐角，
 * 龙卷风极具那种线条特色」✓
 *
 * <h2>这一版怎么画</h2>
 * <ol>
 *   <li>{@link com.tnc.tnc.magic.TornadoLineShape} 算出 <b>7 根螺旋折线 + 6 道 V 形风切</b>
 *       （折点少、半径锯齿大 ⇒ 明确的锐角 ✓）</li>
 *   <li>{@link WindTornadoLines} 把每根折线画成三层加色光带（外辉光 / 青绿主色 / 白芯）✓
 *       —— 和闪电同一套 {@code LightningBoltGeometry} 读法，风与雷是一个美术体系 ✓</li>
 *   <li>自转角只读 {@link TNTornadoEntity#visualSpin(float)}（服务端同步 ＋ 帧间插值）✓
 *       —— 客户端不自己计时 ✗（否则两边不同步，/reload 之后会跳一下 ✗）</li>
 *   <li>出生 4 tick 淡入、消散 10 tick 淡出 ⇒ 不会"啪"一下出现/消失 ✓</li>
 * </ol>
 *
 * <h2>为什么不画模型了</h2>
 * 旧版画的是烘焙模型 {@code tnc:projectile/wind_tornado}（7 层 × 4 片薄板）——
 * 作者实机看到的是"几块板在转"，没有线条感 ✗。
 * 现在<b>一个模型都不画</b>，整团风全部由光带折线构成 ✓
 * （模型本身还在 {@code TNProjectileModels} 里，苍岚军神的 {@code CanglanTempestRenderer} 仍在使用 ✓）。
 *
 * <h2>坐标系</h2>
 * 光带是<b>朝向相机</b>的（侧向量 = 切线 × 视线），所以这里
 * <b>不能</b>再用 {@code poseStack.rotate} 做自转 ✗ —— 旋转之后"相机偏移"就不再是这个空间的向量，
 * 光带会侧翻。自转一律<b>烘进顶点</b>（{@code spinRad} 传进几何）✓，pose 只做平移（其实连平移都不用：
 * 顶点本来就是相对实体原点算的）✓。
 *
 * <p>之前那套 {@code CustomModels.render} 的 {@code −0.5} 对齐问题也就一起消失了 ✓
 * （那是引擎"方块模型空间"的规矩，跟我们自己算顶点无关 ✓）。
 */
public class TNTornadoRenderer extends EntityRenderer<TNTornadoEntity> {

    /** 加色光带要的贴图（白色 16×16；{@code LightningRenderTypes.BOLT} 就是拿它当遮罩 ✓）。 */
    private static final ResourceLocation TEXTURE = LightningRenderTypes.WHITE_TEXTURE;

    public TNTornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;       // 龙卷风不投影子 ✓（它是风，不是实体 ✗）
    }

    @Override
    public ResourceLocation getTextureLocation(TNTornadoEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNTornadoEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        int tier = entity.tier();
        int life = TNTornadoEntity.LIFETIME_SECONDS[Math.max(0, Math.min(2, tier - 3))] * 20;
        float age = entity.tickCount + partialTick;
        // 淡入 4 tick、淡出最后 10 tick ✓
        float fadeIn = Math.min(1.0F, age / 4.0F);
        float fadeOut = Math.min(1.0F, (life - age) / 10.0F);
        float alpha = Math.max(0.0F, Math.min(fadeIn, fadeOut));
        if (alpha <= 0.004F) {
            return;
        }

        double scale = entity.sizeScale();
        // 单位换算：MODEL_* 是 1/16 格（和粒子的算法一致 ✓）
        double bottom = TNTornadoEntity.MODEL_BASE_RADIUS / 16.0D;
        double top = TNTornadoEntity.MODEL_TOP_RADIUS / 16.0D;
        double height = TNTornadoEntity.MODEL_HEIGHT / 16.0D;

        double spinRad = Math.toRadians(entity.visualSpin(partialTick));
        // 相机相对实体原点（顶点就是相对原点算的 ⇒ 平移量用相机坐标）✓
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double camX = cam.x - entity.getX();
        double camY = cam.y - entity.getY();
        double camZ = cam.z - entity.getZ();

        poseStack.pushPose();
        // 只放大，不旋转（自转已烘进顶点，见类注释）✓
        poseStack.scale((float) scale, (float) scale, (float) scale);
        VertexConsumer vc = buffer.getBuffer(LightningRenderTypes.BOLT);
        WindTornadoLines.draw(vc, poseStack.last().pose(),
                spinRad, alpha, bottom, top, height,
                camX, camY, camZ, entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
