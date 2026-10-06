package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNNightGripEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <b>抓住不放的那只黑手</b>怎么画 —— 暗系第一条链「黑夜之手」重做的抓取端 ✓
 * （作者 2026-10-10：「暗魔法的黑夜之手可能要整个改一下」；并且特别强调
 * 「他名字虽然叫做不可视，但你别游戏里做的啥都看不见，我们还是要做出来要的特效的」✓
 * ⇒ 所以这只手**是画出来的**，不是隐形的 ✗）。
 *
 * <h2>画法与为什么这么画</h2>
 * 直接走<b>引擎画投射物的同一条路</b> ✓（反编译确认：
 * {@code SpellModelHelper.LAYERS.get(light_emission)} 取层 ＋
 * {@code CustomModels.render(layer, itemRenderer, modelId, ...)}）——
 * 和环绕雷球（{@code TNThunderOrbRenderer}）一模一样的做法，那条路已经验证能显示 ✓。
 * 模型就用现成的 {@code tnc:projectile/dark_hand}（已在
 * {@code TNProjectileModels.PROJECTILE_MODELS} 里烘焙 ✓，不需要新模型 ✗）。
 *
 * <p><b>为什么不用普通 Forge 渲染器自己读 JSON 模型</b> ✗：原版 {@code ModelManager} 的自定义
 * 模型要 {@code ModelEvent.RegisterAdditional} 注册再逐面搭，本项目为此专门立了
 * <b>六步配方</b>（{@code docs/spells/投射物模型_配方.md}）—— 走引擎这条路等于白拿 ✓。
 *
 * <h2>朝向：掌心朝着被抓的人 ✓</h2>
 * 模型是"手腕在 −Z、掌面朝 +Z"（见 {@code tools/gen_dark_hand_model.py} 的表头注释 ✓）。
 * 于是只要把模型的 <b>+Z 转到「施法者 → 手」这个方向</b>，读感就是
 * "从他背后伸出来的一只手，掌心按在目标身上" ✓ —— 正好是作者要的姿态。
 * 旋转的推导（把 +Z 转到方向 d）：
 * <pre>
 *   yaw   = atan2(d.x, d.z)      // 先绕 Y
 *   pitch = −asin(d.y)           // 再绕已经转过去的 X（局部轴）
 *   pose  = Ry(yaw) · Rx(pitch)
 * </pre>
 * 校验：d = (0,0,1) ⇒ yaw=0、pitch=0（正前方不动 ✓）；d = (0,0.707,0.707) ⇒ pitch=−45°
 * ⇒ +Z 变成 (0,+0.707,+0.707)（朝前上方 ✓）；d = (1,0,0) ⇒ yaw=90° ⇒ +Z 变成 (1,0,0) ✓。
 *
 * <h2>尺寸：与飞出去的那只手同一张表 ✓</h2>
 * {@link #SCALE} 就是法术 JSON 里那份 {@code scale}
 * （{@code 0.98/1.97/4.92/9.85/19.69} × 模型最大跨度 1.625 格 ⇒ **1.6/3.2/8/16/32 格** ✓）。
 * 两者必须一致 ✗：投射物在命中那一刻就被引擎回收了，接着本实体接手画同一只手 ——
 * 尺寸不一样就会"啪"地跳一下 ✓。
 *
 * <h2>★ 引擎在缩放后的坐标系里平移了 0.5 格（这个坑踩过）</h2>
 * {@code CustomModels.render} 内部先 {@code translate(−0.5,−0.5,−0.5)} ✗，而那是
 * <b>已经缩放过</b>的坐标系 ⇒ 实际偏移 = {@code scale × 0.5} 格。
 * 所以缩放之后要**自己补一个 +0.5** 抵消它 ✓（本模型包围盒本来就居中在原点，
 * 所以补 {@code (0.5,0.5,0.5)} 即回到实体原点 ✓）；
 * 环绕雷球当初就是没补这一下，整整歪了 2 格 ✓（推导见 {@code TNThunderOrbRenderer} 的同名注释）。
 */
public class TNNightGripRenderer extends EntityRenderer<TNNightGripEntity> {

    private static final Logger LOGGER = LogManager.getLogger("TN-C/nighthand");

    /** 给引擎的模型号 —— <b>普通 ResourceLocation</b>，不要加 {@code #standalone} 变体 ✗。 */
    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, TNProjectileModels.DARK_HAND);

    /** 贴图（模型自带的那张 ✓；引擎自己会去读，这里只是 {@code getTextureLocation} 的要求）。 */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/spell_projectile/dark_hand.png");

    /**
     * 每档的缩放 —— 与法术 JSON 里那份 {@code scale} **必须一致** ✓（见类注释）。
     * 索引 = tier − 1。
     */
    private static final float[] SCALE = {0.98F, 1.97F, 4.92F, 9.85F, 19.69F};

    /** 只在第一次打一行日志，免得每帧刷屏 ✓。 */
    private static boolean logged;

    public TNNightGripRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;      // 悬空的黑手不投影子 ✓
    }

    @Override
    public ResourceLocation getTextureLocation(TNNightGripEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TNNightGripEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float scale = SCALE[Math.max(0, Math.min(SCALE.length - 1, entity.tier() - 1))];
        Vec3 direction = directionOf(entity);

        poseStack.pushPose();
        // ① 把模型的 +Z（掌心）转到"施法者 → 手"的方向 ✓
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(direction.x, direction.z)));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotation(
                (float) -Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D))));
        // ② 按档位放大，再补回引擎内部那 −0.5 格 ✓
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.5F, 0.5F, 0.5F);

        if (!renderWithEngine(poseStack, buffer, entity)) {
            // 引擎不在/渲染失败：**什么都不画**，但绝不崩 ✓
            // （手的主体表现还有服务端撒的黑索与黑雾粒子 ⇒ 即使这条路径失败也不会"啥都看不见" ✓）
            if (!logged) {
                logged = true;
                LOGGER.warn("TN-C: night hand 引擎渲染路径不可用，本帧只靠粒子表现 ✓");
            }
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 从施法者指向这只手的方向 ✓ —— 也就是"掌心朝着目标"的那个方向。
     *
     * <p>施法者由同步字段带过来（{@code entity.ownerEntityId()} ✓）；拿不到
     * （隔着太远没被同步到、或施法者下线了）就退回"手自己的速度方向/正前方" ✓，
     * 绝不抛异常 ✗。
     */
    private static Vec3 directionOf(TNNightGripEntity entity) {
        int ownerId = entity.ownerEntityId();
        if (ownerId >= 0 && Minecraft.getInstance().level != null) {
            Entity owner = Minecraft.getInstance().level.getEntity(ownerId);
            if (owner != null) {
                Vec3 delta = entity.position().subtract(owner.position());
                if (delta.lengthSqr() > 1.0E-6D) {
                    return delta.normalize();
                }
            }
        }
        Vec3 motion = entity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-6D) {
            return motion.normalize();
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    /**
     * 走<b>引擎渲染投射物的同一条路</b> ✓（软依赖：引擎不在时抛 {@code NoClassDefFoundError}，
     * 那也是 {@code Throwable} ✓ ⇒ 外面接住即可，不影响别的渲染 ✓）。
     *
     * @return true ＝ 引擎画成功
     */
    private static boolean renderWithEngine(PoseStack poseStack, MultiBufferSource buffer,
                                            TNNightGripEntity entity) {
        try {
            net.minecraft.client.renderer.RenderType layer =
                    net.spell_engine.client.render.SpellModelHelper.LAYERS
                            .get(net.spell_engine.api.render.LightEmission.RADIATE);
            net.spell_engine.api.render.CustomModels.render(layer,
                    Minecraft.getInstance().getItemRenderer(), MODEL_ID,
                    poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            if (!logged) {
                logged = true;
                LOGGER.info("TN-C: night hand grip <- 引擎渲染路径 {} tier={} layer={} ✓",
                        MODEL_ID, entity.tier(), layer);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
