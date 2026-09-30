package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNLightningStrikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the author's {@code tnc:projectile/flash} model as a standing lightning bolt.
 *
 * <p>Same trick as TNThunderOrbRenderer (that one is verified in game): never look the
 * model up yourself - go through the engine's own projectile render path
 * ({@code CustomModels.render} + {@code SpellModelHelper.LAYERS}). A plain
 * {@code ResourceLocation} is required; the {@code #standalone} variant misses and Forge
 * then hands back a missing model that is NOT {@code getMissingModel()}, which silently
 * rendered a purple-black cube once already.
 *
 * <p>Author's flash model bbox (units, 1 = 1/16 block): x 8..13, y -5..31, z 8..10.
 * So to stand the bolt ON the entity position: scale, then translate
 * (-10.5/16, +5/16, -9/16) - centre it in x/z and lift its bottom up to y = 0.
 *
 * <h2>三种形态（{@code DATA_KIND}）</h2>
 * <ul>
 *   <li>{@code 0} 闪电（主链雷场/雷暴/雷击）→ {@code flash} ✓</li>
 *   <li>{@code 1} 大雷球（神在投篮 t5 那颗）→ {@code lightingball_2} ✓</li>
 *   <li>{@code 2} 神（神在投篮 t5 天上那三尊）→ {@code lightning_god} ＋ 脚底一圈
 *       {@code god_ring.png} ✓（2026-09-29 补；之前 kind=2 没有分支，被当闪电画 ✗）</li>
 *   <li>{@code 3} <b>跟随神</b>（2026-09-29 作者："我已登神，我希望玩家背后会出现 god 的模型跟随"）
 *       → 同一个 {@code lightning_god} 模型 ✓，但<b>不画脚底环</b>（它悬在玩家背后 ✗）</li>
 * </ul>
 */
public class TNLightningStrikeRenderer extends EntityRenderer<TNLightningStrikeEntity> {

    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.FLASH);

    /** 球形态（神在投篮 t5 那颗大雷球）用的模型 ✓ */
    private static final ResourceLocation BALL_MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.LIGHTNINGBALL_2);

    /**
     * 神形态（神在投篮 t5 天上那三尊）用的模型 ✓
     *
     * <p>2026-09-29 修：kind=2 **根本没有分支** —— 三尊神掉进了 {@code else}，被当成闪电画
     * （作者："t5 怎么光爆炸，模型没看见"✗）。现在三态各走各的模型 ✓。
     */
    private static final ResourceLocation GOD_MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "projectile/lightning_god");

    /**
     * ★ 黑暗衍（公孙衍·迷失，{@code tnc:yan_dark}）专用的<b>暗色三件套</b>
     * （作者 2026-09-30 自制 ✓："我制作了 god_dark 和 flash_dark 和 lightingball_dark，
     * 你拿去替换他的法术"）。
     *
     * <p>几何与上面三个亮色模型**完全一致**，只有 UV 与贴图不同 —— 由
     * {@code tools/gen_dark_projectile_models.ps1} 从**已发货的亮色模型**生成，
     * 脚本里逐元素校验过几何（`-SelfTest` 还能把亮色模型重新生成一遍跟发货文件比对 ✓）。
     * 实体带 {@link TNLightningStrikeEntity#isDark()} 时按形态各取一个 ✓。
     */
    private static final ResourceLocation FLASH_DARK_MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.FLASH_DARK);
    private static final ResourceLocation BALL_DARK_MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.LIGHTNINGBALL_DARK);
    private static final ResourceLocation GOD_DARK_MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.GOD_DARK);

    private static final float CENTER_X = 10.5F / 16.0F;
    private static final float CENTER_Z = 9.0F / 16.0F;
    private static final float BOTTOM_Y = 5.0F / 16.0F;

    /**
     * 雷霆之神模型的原点（单位 → 格，量自 {@code models/projectile/lightning_god.json}）：
     * 身体 x 中心 8.5、z 中心 8、<b>最低点 y = -1</b> ✓。
     */
    private static final float GOD_CENTER_X = 8.5F / 16.0F;
    private static final float GOD_CENTER_Z = 8.0F / 16.0F;
    private static final float GOD_BOTTOM_Y = -1.0F / 16.0F;

    /**
     * ★★ <b>引擎会替我们平移 (−0.5, −0.5, −0.5)</b> —— 而在**缩放过的坐标系里**，所以必须补回来 ✓
     *
     * <h2>2026-09-29 的大坑（作者："god 的黑环位置不对，怎么环位置在手持的雷电上呢"
     * ＋ "球呢，我要的球缓缓下落怎么消失了"）</h2>
     * 反编译 {@code net.spell_engine.api.render.CustomModels#render} 看到它在画模型前有一句
     * {@code poseStack.translate(-0.5, -0.5, -0.5)}（方块模型"原点在方块角"要挪到中心 ✓）。
     * 但它是作用在<b>当前姿势</b>上的，而我们的姿势是 {@code scale(s)} 过的 ✗ ⇒
     * <b>实际位移 = s × 0.5 格</b> ✗✗：
     * <ul>
     *   <li>神 {@code s=26} ⇒ 模型被画偏 <b>13 格</b>（x/y/z 各 13）；而脚底那圈环是用
     *       {@code RenderType} 自己画的（不走引擎 ✗）⇒ 环留在实体原点 ⇒
     *       <b>看上去"环跑到手持雷电那边去了"</b> ✗</li>
     *   <li>大雷球 {@code s=29} ⇒ 偏 <b>14.5 格</b> ⇒ 球其实在正常下落，但被画在 14.5 格外
     *       ⇒ 玩家眼里"球不见了" ✗</li>
     *   <li>主链闪电 {@code s=4~9.6} ⇒ 偏 2~5 格（从天上劈下来时看不出来，所以一直没被发现 ✗）</li>
     * </ul>
     * 修法：把我们自己的 {@code translate} 每个分量都 <b>+0.5</b> 预补偿 ✓
     * （推导：最终世界位移 = s·(t_ours − 0.5)；想让模型上的点 c 落到实体原点 ⇒ t_ours = 0.5 − c ✓）。
     */
    private static final float ENGINE_HALF_BLOCK = 0.5F;

    /**
     * 神模型的**正脸朝向**（渲染时的 yaw 偏移）✓
     *
     * <p>2026-09-29 量的（{@code models/projectile/lightning_god.json}）：
     * <b>脸在 −Z 一侧</b> —— 护面/颈甲三块（z 0~5）都在头和躯干的**前面**，
     * 头发在 z 10~10.5（后脑），"背后那道闪电"那组在 z 13~15 ✓。
     *
     * <p>所以要走原版活体那套：{@code mulPose(180° − yaw)} ✓
     * （原版约定 yaw=0 面向 +Z；模型的脸在 −Z，转 180° 刚好把脸转到 +Z ✓）。
     * <b>万一哪天换了模型、或者游戏里看到神是背对着的</b>，只改这一个数即可：
     * 180 ⇒ 0（脸朝反方向），或者 ±90 微调 ✓。
     *
     * <p>★★ 2026-09-30 实机反馈（作者："第一个登神的朝向反了，应该面部跟 boss 的面部朝向一致，
     * 我发现玩家的也反了"）⇒ **180 → 0** ✓。
     * 也就是说这份模型实际的脸在 <b>+Z</b>（不是上面记的 −Z ✗）：跟随神按 {@code owner.getYRot()}
     * 走，180 偏移时正好背对主人 ✗；改成 0 之后 {@code face = (−sin yaw, cos yaw)} ＝ 主人的朝向 ✓。
     * ⚠️ 连带：三尊神"朝向圆心"的 yaw 公式必须跟着写成 {@code atan2(−dx, dz)} ✓
     * （同一套约定：{@code look = (−sin yaw, +cos yaw)} ✓），否则它们会背对圆心 ✗。
     */
    private static final float GOD_MODEL_YAW_OFFSET = 0.0F;

    /** 神脚底那圈环的贴图 ✓（128×128 的黑紫圆环，半透明自发光画成贴地面片） */
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("TN-C/strike");
    private static final ResourceLocation GOD_RING = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/god_ring.png");
    /** 三种形态各记一行日志（作者要的"有伤害没模型时，日志直接告诉我们在哪个分支"✓）。 */
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public TNLightningStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TNLightningStrikeEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/spell_projectile/flash.png");
    }

    /**
     * <b>绕开视锥剔除</b> ✓ —— 只按距离画。
     *
     * <h2>为什么必须这样（2026-09-29 作者："我的模型呢，球的模型跟 god 的模型嘞，没看见"）</h2>
     * 实体类型的碰撞箱只有 {@code 0.1×0.1×0.1}（见 {@code TNOrbEntities.LIGHTNING_STRIKE}），
     * 而我们的模型比它大得多：闪电 9 格高、大雷球 16 格、**三尊神悬停在锚点上方 30 格**。
     * 原版 {@code EntityRenderer.shouldRender(...)} 是拿<b>那个 0.1 的小盒子</b>去和视锥求交 ✗——
     * 玩家平视锚点（十几格外的地面）时，头顶 30 格处的盒子**根本不在视锥里** ⇒ 飞过 100 tick
     * 的三尊神一次都不画、渲染器里那一行日志也不会打 ✗（这也是"有伤害没模型"的最后一环）。
     *
     * <p>水法的那三个渲染器（{@code TNWaterSpellRenderer} 等）早就是这么干的 ✓ —— 同一条既成做法，
     * 不是新发明。
     */
    @Override
    public boolean shouldRender(TNLightningStrikeEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                               double camX, double camY, double camZ) {
        return entity.distanceToSqr(camX, camY, camZ) < RENDER_DISTANCE_SQR;
    }

    /** 多远之内无条件画（格²）——181²＝原版 {@code shouldRenderAtSqrDistance} 的上限 ✓ */
    private static final double RENDER_DISTANCE_SQR = 32768.0D;

    @Override
    public void render(TNLightningStrikeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        // pop in over 2 ticks, shrink away over the last 2 - a strike has no time to fade
        // 2026-09-27: 原来这里是 grow * min(1, (life-age)/2) ✗ —— life 只有 4 tick 而其中
        // 大部分被"下落"吃掉，于是雷还在天上时 k 已经归零、整道雷不画 ✗（作者："有的时候
        // 模型会缺失"）。雷劈本来就是瞬时的，直接满尺寸出现即可 ✓ 消失交给服务端 discard。
        float k = Math.min(1.0F, age / 2.0F);
        float s = (float) entity.scale() * k;

        boolean god = entity.usesGodModel();
        boolean t5god = entity.isGod();
        boolean ball = entity.isBall();
        // ★ 暗色版（黑暗衍专用）：形态不变，只把模型换成 *_dark ✓
        boolean dark = entity.isDark();
        ResourceLocation modelId = god ? (dark ? GOD_DARK_MODEL_ID : GOD_MODEL_ID)
                : ball ? (dark ? BALL_DARK_MODEL_ID : BALL_MODEL_ID)
                : (dark ? FLASH_DARK_MODEL_ID : MODEL_ID);

        poseStack.pushPose();
        poseStack.scale(s, s, s);
        if (god) {
            // 神：**朝向由实体的 yaw 决定** ✓（作者 2026-09-29："三个 god 应该环绕成一个圆，朝向向中心看"）
            //   顺序很重要：**先转、再平移** ⇒ 顶点映射成 s · R · (v − 模型中心) ✓
            //   （反过来先平移再转，模型会绕着实体原点偏心 ≈ 13 格 ✗ —— 因为缩放 26 倍会把这个偏移也放大 ✗）
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(GOD_MODEL_YAW_OFFSET - entityYaw));
            poseStack.translate(ENGINE_HALF_BLOCK - GOD_CENTER_X,
                    ENGINE_HALF_BLOCK - GOD_BOTTOM_Y,
                    ENGINE_HALF_BLOCK - GOD_CENTER_Z);
        } else if (ball) {
            // 球心落在实体原点 ✓（同样要补引擎那 0.5 格）
            poseStack.translate(ENGINE_HALF_BLOCK - 0.59375F,
                    ENGINE_HALF_BLOCK - 0.71875F,
                    ENGINE_HALF_BLOCK - 0.53125F);
        } else {
            // 闪电底端立在地面 ✓（同样要补引擎那 0.5 格）
            poseStack.translate(ENGINE_HALF_BLOCK - CENTER_X,
                    ENGINE_HALF_BLOCK + BOTTOM_Y,
                    ENGINE_HALF_BLOCK - CENTER_Z);
        }
        try {
            net.spell_engine.api.render.CustomModels.render(
                    net.spell_engine.client.render.SpellModelHelper.LAYERS
                            .get(net.spell_engine.api.render.LightEmission.RADIATE),
                    Minecraft.getInstance().getItemRenderer(), modelId,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable ignored) {
            // engine missing / API changed: the hit still has its particles, just no model
        }
        poseStack.popPose();

        if (t5god) {
            // 脚底那圈黑紫环（作者 2026-09-29 指定）✓
            //  ★ 跟随神（登神时背后那尊）**不画环** ✗：它是悬空的（贴着玩家背后浮动），
            //    在它脚下画一圈"贴地"的环会看着像浮在半空的一张光盘 ✗。
            drawGodRing(age, poseStack, buffer, 0.45F * (float) entity.scale());
        }

        String kind = t5god ? "god" : (entity.isFollowerGod() ? "follower-god" : (ball ? "ball" : "bolt"));
        if (dark) {
            kind = kind + "-dark";           // 日志里一眼看出用的是暗色模型 ✓
        }
        if (LOGGED.add(kind)) {
            LOGGER.info("TN-C: strike render kind={} model={} scale={} life={}",
                    kind, modelId, entity.scale(), entity.life());
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 在神脚下贴地画一圈 {@link #GOD_RING} ✓。
     *
     * <p>做法照抄已经验证过的魔法阵渲染器（{@code TNMagicCircleRenderer}）：
     * {@code entityTranslucentEmissive} ＋ <b>两个绕序各画一遍</b> —— 半透明层是剔背面的 ✗，
     * 只画一遍时"哪一面朝上"搞反就整个看不见，而这一点在开发环境里没法用眼睛验收 ✓。
     */
    private static void drawGodRing(float age, PoseStack poseStack,
                                    MultiBufferSource buffer, float radius) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.02D, 0.0D);                 // 稍微离脚底，免得和"脚"的面打架
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * 1.5F));   // 慢慢转 ✓
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));        // 立着 -> 摊到地面 ✓
        org.joml.Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(GOD_RING));
        drawQuad(consumer, matrix, radius, false);
        drawQuad(consumer, matrix, radius, true);
        poseStack.popPose();
    }

    /** 在 XY 平面画一个 -r..r 的方片（贴图铺满），flip = 反向绕序 ✓。 */
    private static void drawQuad(VertexConsumer consumer, org.joml.Matrix4f matrix, float r, boolean flip) {
        float[][] corners = flip
                ? new float[][]{{-r, -r}, {-r, r}, {r, r}, {r, -r}}
                : new float[][]{{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        float[][] uvs = {{0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}};
        for (int i = 0; i < 4; i++) {
            consumer.vertex(matrix, corners[i][0], corners[i][1], 0.0F)
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(uvs[i][0], uvs[i][1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(0.0F, 0.0F, 1.0F)
                    .endVertex();
        }
    }
}
