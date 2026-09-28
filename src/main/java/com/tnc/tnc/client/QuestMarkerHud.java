package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * <b>「主线目标人物」屏幕标记</b> —— 追踪任务后，目标要么在视野里被框住、
 * 要么在屏幕边缘给一个指向箭头 + 距离（类似原神的寻人点）。
 *
 * <p>数据来源见 {@link QuestMarkerTarget}：位置是**每帧现查**的，不是写死的坐标。
 *
 * <h2>什么时候出现</h2>
 * 只有**玩家主动追踪了某条任务**（任务书里点了追踪）才画 ✓ ——
 * 没追踪就完全不画，不干扰正常游戏 ✓。
 *
 * <h2>画在哪一层</h2>
 * 与 {@code MagicStoneHud} 同一套：{@code RenderGuiEvent.Post}（聊天框之后）、
 * 自己的 {@code pose().translate(z)} 抬一层，避免和后画的原版元素打架 ✓。
 *
 * <h2>不依赖 Xaero</h2>
 * Xaero 小地图自带任务路点，但它**只吃 {@code location} 目标 + 固定坐标** ✗，
 * 而我们的主线故意不用固定坐标（每存档位置不同）。所以这里自己画，
 * Xaero 装不装都一样能用 ✓；小地图上的点等以后接它的 API 再说。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestMarkerHud {

    /** 抬到聊天框之上（与 MagicStoneHud 同一区间）。 */
    private static final float HUD_Z = 200.0F;

    /** 目标在屏幕外时，箭头离屏幕边留多少像素。 */
    private static final int EDGE_MARGIN = 28;

    /** 目标在视野内时，超过这个距离就不画屏幕内标记（免得挤成一团）。 */
    private static final double ONSCREEN_MAX_DISTANCE = 64.0D;

    /** 标记本身的颜色（金）。 */
    private static final int COLOR = 0xFFD8A657;
    private static final int COLOR_DIM = 0x80704A1E;

    private QuestMarkerHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }

        Optional<QuestMarkerTarget.Marker> found = QuestMarkerTarget.resolve(mc);
        if (found.isEmpty()) {
            return;
        }
        QuestMarkerTarget.Marker marker = found.get();

        GuiGraphics graphics = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, HUD_Z);

        int[] screen = projectToScreen(mc, marker.position(), width, height);
        if (screen != null && marker.distance() <= ONSCREEN_MAX_DISTANCE) {
            drawOnScreenMarker(graphics, mc, marker, screen[0], screen[1]);
        } else {
            drawEdgeArrow(graphics, mc, marker, width, height);
        }

        pose.popPose();
    }

    /**
     * 把世界坐标投到屏幕像素。
     *
     * @return {@code {x, y}}；目标在摄像机背后时返回 {@code null}
     */
    private static int[] projectToScreen(Minecraft mc, Vec3 world, int width, int height) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 delta = world.subtract(camera);
        float yaw = mc.gameRenderer.getMainCamera().getYRot();
        float pitch = mc.gameRenderer.getMainCamera().getXRot();

        // 世界 -> 摄像机局部坐标（先按 pitch 绕 X，再按 yaw 绕 Y）
        double cosPitch = Math.cos(-pitch * Mth.DEG_TO_RAD);
        double sinPitch = Math.sin(-pitch * Mth.DEG_TO_RAD);
        double y1 = delta.y * cosPitch - delta.z * sinPitch;
        double z1 = delta.y * sinPitch + delta.z * cosPitch;

        double cosYaw = Math.cos(-yaw * Mth.DEG_TO_RAD);
        double sinYaw = Math.sin(-yaw * Mth.DEG_TO_RAD);
        double x2 = delta.x * cosYaw - z1 * sinYaw;
        double z2 = delta.x * sinYaw + z1 * cosYaw;

        if (z2 <= 0.05D) {
            return null;                       // 在背后：交给边缘箭头
        }
        double fov = mc.options.fov().get();
        double scale = (height / 2.0D) / Math.tan(fov * Mth.DEG_TO_RAD / 2.0D);
        int x = (int) (width / 2.0D + x2 * scale / z2);
        int y = (int) (height / 2.0D - y1 * scale / z2);
        if (x < 0 || x > width || y < 0 || y > height) {
            return null;                       // 出了画面：交给边缘箭头
        }
        return new int[]{x, y};
    }

    /** 目标在视野里：画一个菱形 + 名字 + 距离。 */
    private static void drawOnScreenMarker(GuiGraphics graphics, Minecraft mc,
                                           QuestMarkerTarget.Marker marker, int x, int y) {
        graphics.fill(x - 1, y - 6, x + 2, y + 7, COLOR);
        graphics.fill(x - 6, y - 1, x + 7, y + 2, COLOR);
        graphics.fill(x - 1, y - 5, x + 2, y + 6, COLOR_DIM);

        String distance = ((int) marker.distance()) + "m";
        Component line = Component.literal(marker.label() + "  §7" + distance);
        int textWidth = mc.font.width(line);
        int textY = y + 10;
        graphics.drawString(mc.font, line, x - textWidth / 2, textY, COLOR, true);
    }

    /**
     * 目标在画面外/背后：贴屏幕边缘画一个指向箭头 + 距离。
     *
     * <p>方向取"目标相对玩家朝向的偏角"，不依赖投影结果 ——
     * 所以目标在正后方时也能给出正确的左右指向 ✓。
     */
    private static void drawEdgeArrow(GuiGraphics graphics, Minecraft mc,
                                      QuestMarkerTarget.Marker marker, int width, int height) {
        Vec3 player = mc.player.position();
        double dx = marker.position().x - player.x;
        double dz = marker.position().z - player.z;

        // 玩家朝向：把世界方向转到"以屏幕为参照"的角度
        float yaw = mc.player.getYRot();
        double rel = Math.atan2(-dx, dz) + Math.toRadians(yaw);
        double sin = Math.sin(rel);
        double cos = Math.cos(rel);

        int cx = width / 2;
        int cy = height / 2;
        int radiusX = cx - EDGE_MARGIN;
        int radiusY = cy - EDGE_MARGIN;

        // 把方向向量缩放到屏幕边缘（椭圆裁剪，角落不会跑出屏幕）
        double scale = 1.0D / Math.max(Math.abs(sin) / radiusX, Math.abs(cos) / radiusY);
        int ax = cx + (int) (sin * scale);
        int ay = cy - (int) (cos * scale);

        // 箭头：一个朝外的三角 + 一段"柄"
        int size = 7;
        graphics.fill(ax - size, ay - size, ax + size, ay + size, COLOR_DIM);
        graphics.fill(ax - size + 2, ay - size + 2, ax + size - 2, ay + size - 2, COLOR);
        graphics.fill(ax - 2, ay - 2, ax + 3, ay + 3, 0xFF101014);

        String distance = ((int) marker.distance()) + "m";
        Component line = Component.literal(marker.label());
        int textWidth = mc.font.width(line);
        int textX = Mth.clamp(ax - textWidth / 2, 2, width - textWidth - 2);
        int textY = Mth.clamp(ay + size + 3, 2, height - 12);
        graphics.drawString(mc.font, line, textX, textY, COLOR, true);
        graphics.drawString(mc.font, distance, textX, textY + 10, 0xFFB9B9B9, true);
    }
}
