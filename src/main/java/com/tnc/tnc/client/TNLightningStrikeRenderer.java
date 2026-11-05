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
        // ★★ 2026-11-05 作者："你现在的雷电就是只掉下来一个模型……我希望是从天空中一直落到地上，
        //    很震撼的从天劈到地的光效，而且可以多劈"
        //  ⇒ kind = 0（闪电）**不再画那个 flash 模型** ✗：整根闪电（含分叉/雷网/冲击闪）
        //    由 {@link #renderFullBolt} 现画，从云层一路画到脚底 ✓。
        //    神/球/跟随神仍然走原来的模型路径（它们本来就不是"劈下来的一道雷"✓）。
        if (!entity.usesGodModel() && !entity.isBall()) {
            renderFullBolt(entity, age, partialTick, poseStack, buffer);
            if (LOGGED.add(entity.isDark() ? "bolt-geometry-dark" : "bolt-geometry")) {
                LOGGER.info("TN-C: strike render kind=bolt-geometry scale={} theme={} life={} anchorY={}",
                        entity.scale(), entity.theme(), entity.life(), entity.anchorY());
            }
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

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

        // ★ 2026-11-05 作者："神在投篮的神还有球都变得不透明了" —— 根因在引擎那层：
        //   spell_engine 0.15.12 的 spellObject(LightEmission) 传的是 translucent=false
        //   ⇒ TransparencyState = NO_TRANSPARENCY ⇒ 贴图 alpha 被忽略、模型变实心 ✗。
        //   现在改走我们自己的 SPELL_MODEL（TRANSLUCENT ＋ 自发光 ＋ 不写深度）✓
        if (god || ball) {
            // 先垫一圈主题色的辉光（相机朝向的光斑），模型再盖上去 ⇒ 有"发光"的体感 ✓
            net.minecraft.world.phys.Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            net.minecraft.world.phys.Vec3 here = entity.getPosition(partialTick);
            double hx = camPos.x - here.x;
            double hy = camPos.y - here.y;
            double hz = camPos.z - here.z;
            double haloY = god ? 0.50D * s : 0.50D;
            float haloR = (float) (god ? 0.34D * s : 0.62D * s);
            com.tnc.tnc.client.lightning.LightningBoltGeometry.billboardFlash(
                    buffer.getBuffer(com.tnc.tnc.client.lightning.LightningRenderTypes.BOLT),
                    poseStack.last().pose(), 0.0D, haloY, 0.0D, haloR,
                    com.tnc.tnc.client.lightning.LightningTheme.GOLD, 0.14F, hx, hy, hz);
        }
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
            // ★ 2026-11-05 第二批（作者："闪电登神人物背后的雷神怎么也没有变透明"）：
            //   换半透明 RenderType 还不够 —— 引擎那条 baked-item 路径的**顶点 alpha 写死 1.0**，
            //   只有贴图镂空处透，实心部分（占 40% 像素）永远实心 ✗。
            //   所以再套一层 {@link com.tnc.tnc.client.lightning.AlphaFadeBuffer}，把整尊神的
            //   顶点 alpha 乘下去 ⇒ 真正半透明的雷神 ✓（神 0.60 / 跟随神 0.52 / 雷球 0.85）
            float modelAlpha = ball ? 0.85F : (t5god ? 0.60F : 0.52F);
            net.spell_engine.api.render.CustomModels.render(
                    // ★ 2026-11-05：不再用引擎那层（它是 NO_TRANSPARENCY ⇒ 模型不透明 ✗），
                    //   换成我们自己的半透明自发光层 ✓
                    com.tnc.tnc.client.lightning.LightningRenderTypes.SPELL_MODEL,
                    Minecraft.getInstance().getItemRenderer(), modelId,
                    poseStack,
                    com.tnc.tnc.client.lightning.AlphaFadeBuffer.of(buffer, modelAlpha),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
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

    // ------------------------------------------------------------------
    //  ★ 整根闪电（2026-11-05）
    // ------------------------------------------------------------------

    /** 云层在落点上方多少格（格）—— 40 格已经高过任何平视的视线 ✓。 */
    private static final double SKY_HEIGHT = 46.0D;

    /**
     * 画一根<b>从天到地</b>的闪电，以及落地后的雷网/冲击闪。
     *
     * <h2>为什么不用模型</h2>
     * {@code flash} 模型只有 2.25 格高（作者模型 y −5..31，单位 1/16），放大到 14 倍也才 31 格，
     * 而且是"一根直棍" ✗ —— 这才是"只掉下来一个模型"的根源。现画的版本：
     * <ul>
     *   <li>长度 = 落点上方 {@link #SKY_HEIGHT} 格（约 46）＋ 随体型再加 ✓；</li>
     *   <li>形状每根不同（种子同步 ✓），带 2~5 根分叉（大雷还有二级分叉）✓；</li>
     *   <li>落地后铺开<b>蛛网雷网</b> ＋ 贴地白光 ＋ 白闪 ＋ 扩散光圈 ✓；</li>
     *   <li>"多劈"：主雷之外还有 2~5 道错开 1~2 tick 的副雷（同一次施法里的连劈 ✓）。</li>
     * </ul>
     *
     * <p>坐标：实体原点就在落点（{@code tick()} 里第一 tick 就把自己钉在 {@code fallTo} ✓），
     * 所以几何直接从 {@code y=0} 画到 {@code y=SKY_HEIGHT}；相机位置换成"实体原点坐标系"再传进去 ✓。
     */
    private void renderFullBolt(TNLightningStrikeEntity entity, float age, float partialTick,
                                PoseStack poseStack, MultiBufferSource buffer) {
        net.minecraft.world.phys.Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        net.minecraft.world.phys.Vec3 at = entity.getPosition(partialTick);
        double camX = cam.x - at.x;
        double camY = cam.y - at.y;
        double camZ = cam.z - at.z;

        double scale = Math.max(1.0D, entity.scale());
        float width = (float) Math.min(1.25D, Math.max(0.16D, scale * 0.072D));
        int theme = entity.isDark() ? com.tnc.tnc.client.lightning.LightningTheme.DARK : entity.theme();
        int life = Math.max(1, entity.life());
        int impact = entity.impactAt();
        int sinceHit = impact < 0 ? -1 : (int) Math.floor(age) - impact;

        // 落地后的淡出（实体在 landedTick + life 时会被服务端 discard ✓）
        float fade = 1.0F;
        if (sinceHit >= 0 && sinceHit > life - 4) {
            fade = Math.max(0.0F, (life - sinceHit) / 4.0F);
        }
        // 出现的那一瞬：从 0.35 亮到 1（2 tick）—— 生硬的"啪"比渐变更像雷 ✓
        fade *= Math.min(1.0F, 0.35F + age * 0.5F);

        org.joml.Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(com.tnc.tnc.client.lightning.LightningRenderTypes.BOLT);

        double groundY = entity.anchorY() - at.y;                 // 落点在实体坐标系里的高度（≈0）
        double topY = groundY + SKY_HEIGHT + Math.min(26.0D, scale * 1.6D);
        int seed = entity.seed();
        float flicker = 0.88F + 0.12F * net.minecraft.util.Mth.sin(age * 2.3F + seed * 0.01F);

        // ---- 主雷 ----
        com.tnc.tnc.client.lightning.LightningBoltGeometry.bolt(vc, m,
                0.0D, topY, 0.0D, 0.0D, groundY, 0.0D,
                seed, width, 1.0F * fade * flicker, theme,
                (int) Math.min(6.0D, 2.0D + scale * 0.32D), camX, camY, camZ);

        // ---- 副雷（"可以多劈"）----
        //   出生时间错开 2 tick，位置在落点周围 1~2 格（不是同一个点 ✗，否则像重影）
        int extras = scale >= 10.0D ? 5 : (scale >= 6.0D ? 3 : 2);
        for (int i = 0; i < extras; i++) {
            int born = 2 + i * 2;
            if (sinceHit >= 0 && sinceHit < born) {
                continue;
            }
            double ang = seed * 0.0007D + i * 2.39996D;
            double off = 0.9D + scale * 0.10D + i * 0.35D;
            double ox = Math.cos(ang) * off;
            double oz = Math.sin(ang) * off;
            double sway = 0.8D + i * 0.45D;
            float a = fade * flicker * (0.78F - i * 0.07F);
            com.tnc.tnc.client.lightning.LightningBoltGeometry.bolt(vc, m,
                    ox, topY + i * 0.8D, oz,
                    ox * 0.25D + Math.cos(ang + 1.2D) * sway, groundY, oz * 0.25D + Math.sin(ang + 1.2D) * sway,
                    seed + 977L * (i + 1), width * (0.62F - i * 0.05F), a, theme,
                    i == 0 ? 3 : 1, camX, camY, camZ);
        }

        // ---- 大雷的"补刀"：第 6 tick 再劈一根粗的（只有大雷有 ✓）----
        if (scale >= 9.0D && sinceHit >= 6 && sinceHit <= 10) {
            float a = fade * 0.9F * Math.max(0.0F, 1.0F - (sinceHit - 6) / 5.0F);
            com.tnc.tnc.client.lightning.LightningBoltGeometry.bolt(vc, m,
                    0.7D, topY + 2.0D, -0.5D, -0.4D, groundY, 0.4D,
                    seed + 5501L, width * 0.9F, a, theme, 4, camX, camY, camZ);
        }

        // ---- 落地后的地网 / 闪光 ----
        if (sinceHit >= 0) {
            float t = sinceHit;
            // ① 蛛网：落地那一下最亮，之后留 8 tick 慢慢暗
            float webAlpha = fade * (t < 2.0F ? 0.95F : Math.max(0.0F, 0.95F - (t - 2.0F) * 0.10F));
            double maxRadius = 1.6D + scale * 0.42D;               // 4.5 倍 ⇒ 3.5；14 倍 ⇒ 7.5
            com.tnc.tnc.client.lightning.LightningBoltGeometry.groundWeb(vc, m,
                    0.0D, groundY, 0.0D, (float) maxRadius, seed, theme, webAlpha,
                    (float) (seed * 0.001D + t * 0.02D), camX, camY, camZ, true);
            // ② 贴地白光（中心白 → 主题色 → 透明）
            float flashA = fade * (t < 1.0F ? 0.95F : Math.max(0.0F, 0.95F - (t - 1.0F) * 0.12F));
            com.tnc.tnc.client.lightning.LightningBoltGeometry.groundFlash(vc, m,
                    0.0D, groundY, 0.0D, (float) (1.4D + scale * 0.30D), theme, flashA);
            // ③ 空中的白闪（那一下"啪"的来源）
            if (t < 3.0F) {
                com.tnc.tnc.client.lightning.LightningBoltGeometry.billboardFlash(vc, m,
                        0.0D, groundY + 0.9D + scale * 0.05D, 0.0D, (float) (1.0D + scale * 0.10D),
                        theme, fade * 0.6F * (1.0F - t / 3.0F), camX, camY, camZ);
            }
            // ④ 向外扩散的气浪光圈（第 3 tick 起，越大越淡）
            if (t >= 3.0F && t < 16.0F) {
                double grow = 1.0D + (t - 3.0D) * 0.55D;
                float a = fade * 0.35F * Math.max(0.0F, 1.0F - (t - 3.0F) / 13.0F);
                com.tnc.tnc.client.lightning.LightningBoltGeometry.groundFlash(vc, m,
                        0.0D, groundY, 0.0D, (float) (maxRadius * grow), theme, a);
            }
            // ⑤ 云底的亮斑：让"天被劈开了"这件事在头顶也看得见 ✓
            if (t < 8.0F) {
                com.tnc.tnc.client.lightning.LightningBoltGeometry.billboardFlash(vc, m,
                        0.0D, topY - 2.0D, 0.0D, (float) (3.0D + scale * 0.5D), theme,
                        fade * 0.18F * (1.0F - t / 8.0F), camX, camY, camZ);
            }
        } else {
            // 还没落地（正常情况下 kind=0 第一 tick 就落地 ✓，这里只兜底）：画一截从天上垂下来的前导
            double lead = Math.max(groundY, topY - age * 6.0D);
            com.tnc.tnc.client.lightning.LightningBoltGeometry.bolt(vc, m,
                    0.0D, topY, 0.0D, 0.0D, lead, 0.0D, seed + 31L, width, 0.9F * fade,
                    theme, 1, camX, camY, camZ);
        }
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
