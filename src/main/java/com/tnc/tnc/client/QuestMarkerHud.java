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
        if (screen != null) {
            drawOnScreenMarker(graphics, mc, marker, screen[0], screen[1]);
        } else {
            drawEdgeArrow(graphics, mc, marker, width, height);
        }

        pose.popPose();
    }

    /**
     * 把世界坐标投到屏幕像素。
     *
     * <p>旋转交给 {@link QuestMarkerGeometry}（它按原版摄像机基向量点乘，写错了会被
     * {@code QuestMarkerGeometryTest} 当场抓住）；这里只管"在不在画面里"。
     *
     * @return {@code {x, y}}；目标在摄像机背后、或投影落在画面外时返回 {@code null}
     */
    private static int[] projectToScreen(Minecraft mc, Vec3 world, int width, int height) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 delta = world.subtract(camera);
        QuestMarkerGeometry.CameraSpace space = QuestMarkerGeometry.cameraSpace(
                delta.x, delta.y, delta.z,
                mc.gameRenderer.getMainCamera().getYRot(),
                mc.gameRenderer.getMainCamera().getXRot());
        if (space.depth() <= 0.05D) {
            return null;                       // 在背后：交给边缘箭头
        }
        QuestMarkerGeometry.ScreenPoint point =
                QuestMarkerGeometry.project(space, mc.options.fov().get(), width, height);
        if (point.x() < 0 || point.x() > width || point.y() < 0 || point.y() > height) {
            return null;                       // 出了画面：交给边缘箭头
        }
        return new int[]{(int) Math.round(point.x()), (int) Math.round(point.y())};
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
     * <p>方向同样走 {@link QuestMarkerGeometry#edgeDirection} —— 用摄像机基向量算方位角，
     * 所以朝西、朝北、低头抬头时都指向它真正所在的那一侧（旧写法把 yaw 加反了，
     * 只有朝南时碰巧正确）。
     */
    private static void drawEdgeArrow(GuiGraphics graphics, Minecraft mc,
                                      QuestMarkerTarget.Marker marker, int width, int height) {
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 delta = marker.position().subtract(camera);
        double[] dir = QuestMarkerGeometry.edgeDirection(QuestMarkerGeometry.cameraSpace(
                delta.x, delta.y, delta.z,
                mc.gameRenderer.getMainCamera().getYRot(),
                mc.gameRenderer.getMainCamera().getXRot()));

        int cx = width / 2;
        int cy = height / 2;
        int radiusX = Math.max(1, cx - EDGE_MARGIN);
        int radiusY = Math.max(1, cy - EDGE_MARGIN);

        int ax = cx + (int) Math.round(dir[0] * radiusX);
        int ay = cy + (int) Math.round(dir[1] * radiusY);

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
