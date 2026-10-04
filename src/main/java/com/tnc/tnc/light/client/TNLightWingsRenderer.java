package com.tnc.tnc.light.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 光翼的渲染 —— 挂在 Forge 的 {@code RenderPlayerEvent.Post} 上，在玩家背后画两片发光半透明翅膀 ✓。
 *
 * <p><b>飞的时候会动</b> ✓：扇动频率与幅度由"是否在飞"决定（飞 = 快而大 ✓，站着/走路 = 慢而小 ✓），
 * 再按竖直速度加一点俯仰 ✓ —— 纯代码算，不需要关键帧 ✓。
 *
 * <p>★ <b>位置/大小/朝向/透明度全部挪进了 {@link TNLightWingsPlacement}</b> ✓ ——
 * 也就是 {@code config/tnc/light_wings_placement.json}：改完存盘**1 秒内生效** ✓，
 * 不用重装、不用重启 ✗（作者 2026-10-01："出现了但是模型有点不对吧" —— 贴着看才知道对不对 ✓，
 * 所以这些数不该写死在代码里 ✗）。代码里剩下的只有"扇动"这条动画逻辑 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TNLightWingsRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/entity/light_wings.png");

    /** 作者 geo 模型的贴图（和 {@code light_wings.geo.json} 一套的 64×64 图 ✓）。 */
    private static final ResourceLocation GEO_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            TNMod.MODID, "textures/entity/light_wings_bedrock.png");

    /** 只打一次"我在这儿画了"的日志 ✓（下次"没翅膀"能一眼分清是渲染没跑还是模型没读进来 ✓）。 */
    private static boolean announced;

    /**
     * 每个玩家"这次起飞是从哪一 tick 开始的" ✓ —— 用来让 waving 动画**从起飞那一刻从头播** ✓
     * （不然会接着上次的相位继续 ✗，作者要的是"一起飞就播那个 waving"✓）。
     */
    private static final java.util.Map<java.util.UUID, Long> FLIGHT_START = new java.util.HashMap<>();

    /** 备用（程序化）翅膀的形状：半展宽 / 高 ✓（作者 geo 读不进来时才会用到 ✓）。 */
    private static final float SPAN = 0.95F;
    private static final float RISE = 1.15F;

    private TNLightWingsRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        // ★ 先让配置有机会重载（内部一秒只查一次文件 ✓）—— 放在最前面，
        //   这样"没有翅膀 buff"的时候也会把默认配置文件写出来 ✓
        TNLightWingsPlacement.tick();
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!TNEffects.LIGHT_WINGS.isPresent() || !player.hasEffect(TNEffects.LIGHT_WINGS.get())) {
            FLIGHT_START.remove(player.getUUID());
            return;
        }
        float partial = event.getPartialTick();
        boolean flying = player.getAbilities().flying;
        double vy = player.getDeltaMovement().y;

        // ★★ 2026-10-01 作者："我希望在站着的时候翅膀不动的，你只有起飞的时候就播放那个 waving" ✓
        //   ⇒ ① 站着（没飞）默认**完全静止**（备用扇动的幅度默认 0 ✓，可在 config 里调回来 ✓）；
        //     ② 飞起来就播作者的 waving **关键帧**动画 ✓（没有动画文件时退回程序化扇动 ✓）。
        TNLightWingsModel.ensureAnimationLoaded();
        long now = player.level().getGameTime();
        if (flying) {
            FLIGHT_START.putIfAbsent(player.getUUID(), now);
        } else {
            FLIGHT_START.remove(player.getUUID());
        }
        Long start = FLIGHT_START.get(player.getUUID());
        float flightSeconds = start == null ? 0.0F : (now - start + partial) / 20.0F;
        boolean animated = flying && TNLightWingsPlacement.wavingWhenFlying && TNLightWingsModel.hasAnimation();
        boolean idleWaving = !flying && TNLightWingsPlacement.wavingWhenIdle;
        float animTime = flightSeconds * TNLightWingsPlacement.animationSpeed;

        // 备用扇动：飞行时又快又大，平时慢慢摆 ✓（站着时幅度默认 0 ⇒ 静止 ✓）
        float speed = flying ? TNLightWingsPlacement.flapSpeedFlying : TNLightWingsPlacement.flapSpeedIdle;
        float amp = flying ? TNLightWingsPlacement.flapAmpFlying : TNLightWingsPlacement.flapAmpIdle;
        float flap = animated ? 0.0F : (float) Math.sin((player.tickCount + partial) * speed) * amp;
        if (!flying && !idleWaving) {
            flap = 0.0F;                     // ★ 站着不动：连备用扇动都不给 ✗
        }
        // 竖直速度带来的仰角：只在飞的时候用 ✓（站着时翅膀不该跟着掉落的 vy 动 ✗）
        float pitch = flying
                ? (float) Math.max(-18.0D, Math.min(18.0D, vy * 40.0D)) * TNLightWingsPlacement.pitchFactor
                : 0.0F;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = LightTexture.FULL_BRIGHT;
        // ★ 2026-10-01 换成作者的 geo 模型（半透明 + 自发光，见 TNLightWingsModel ✓）；
        //   geo 读不进来时**退回**程序化的两片翅膀 ✓ —— 作者要的是"飞的时候看得见翅膀"✗，
        //   宁可画得糙一点，也不能什么都没有 ✗（原来读失败是静默的 ⇒ 只有"没翅膀"这一个现象 ✗）
        boolean geo = TNLightWingsModel.isLoaded();
        if (!announced) {
            announced = true;
            org.apache.logging.log4j.LogManager.getLogger("TN-C/light").info(
                    "TN-C/light: 光翼渲染已启动（{}，{}，动画 {}，{}）",
                    geo ? "作者 geo 模型" : "备用程序化翅膀", TNLightWingsModel.describe(),
                    TNLightWingsModel.describeAnimation(), TNLightWingsPlacement.describe());
        }
        TNLightWingsModel.setAlpha(TNLightWingsPlacement.alpha);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                geo ? GEO_TEXTURE : TEXTURE));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-player.yBodyRot + TNLightWingsPlacement.yawOffset));
        if (TNLightWingsPlacement.mirrorX) {
            // ★ 左右镜像（作者 2026-10-01："翅膀的左右反了" ✗）—— 只换左右，不动前后 ✓。
            //   渲染是正反两面都提交的 ✓，所以负缩放不会因为背面剔除把翅膀弄没 ✓。
            pose.scale(-1.0F, 1.0F, 1.0F);
        }
        pose.translate(0.0F, TNLightWingsPlacement.backY, TNLightWingsPlacement.backZ);
        if (TNLightWingsPlacement.scale != 1.0F) {
            float s = TNLightWingsPlacement.scale;
            pose.scale(s, s, s);
        }
        if (geo) {
            TNLightWingsModel.render(pose, vc, flap, animTime, animated);
        } else {
            Matrix4f m = pose.last().pose();
            drawWing(vc, m, true, flap, pitch);
            drawWing(vc, m, false, flap, pitch);
        }
        pose.popPose();
    }

    /** 画一片翅膀（right = true 右翼 ✓ 左翼镜像 ✓）。 */
    private static void drawWing(VertexConsumer vc, Matrix4f m, boolean right, float flap, float pitch) {
        float s = right ? 1.0F : -1.0F;
        // 从肩点向外上展开；flap 让它上下扇，pitch 让它前后仰 ✓
        float ax = 0.0F, ay = 0.0F;
        float bx = s * SPAN, by = RISE * 0.72F;
        float cx = s * SPAN * 0.55F, cy = RISE * -0.28F;
        float dx = s * SPAN * 0.18F, dy = RISE * -0.42F;
        // ★ 正反各一份 ✓：geo 那条路踩过"绕序反了 ⇒ 被背面剔除吃光"✗，备用路径也照这个来 ✓
        wingVertex(vc, m, ax, ay, 0.92F, 0.0F, 1.0F);
        wingVertex(vc, m, bx, by, 0.70F, 1.0F, 0.06F);
        wingVertex(vc, m, cx, cy, 0.55F, 0.72F, 0.62F);
        wingVertex(vc, m, dx, dy, 0.35F, 0.32F, 0.9F);
        wingVertex(vc, m, dx, dy, 0.35F, 0.32F, 0.9F);
        wingVertex(vc, m, cx, cy, 0.55F, 0.72F, 0.62F);
        wingVertex(vc, m, bx, by, 0.70F, 1.0F, 0.06F);
        wingVertex(vc, m, ax, ay, 0.92F, 0.0F, 1.0F);
    }

    private static void wingVertex(VertexConsumer vc, Matrix4f m, float x, float y, float alpha, float u, float v) {
        vc.vertex(m, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F).endVertex();
    }
}