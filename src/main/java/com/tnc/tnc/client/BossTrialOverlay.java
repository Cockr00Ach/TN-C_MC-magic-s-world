package com.tnc.tnc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.BossTrialSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Boss 交互的<b>屏幕特效</b>（2026-11-15 作者："让人感觉紧迫感"）。
 *
 * <p>四个层次，全部用 GUI 渐变画，不依赖光影包：
 * <ol>
 *   <li><b>元素色氛围</b>：屏幕左右上下四条渐变边，颜色 = 这个 Boss 的元素色</li>
 *   <li><b>收缩边框</b>：越接近结束，边框越往里收 —— 视觉上"空间在变小"</li>
 *   <li><b>倒计时</b>：屏幕正上方的大数字（秒），最后 3 秒变红并跳动</li>
 *   <li><b>心跳红闪</b>：最后 3 秒整块屏幕随心跳闪一下，越接近越亮</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class BossTrialOverlay {

    private static BossTrialSync.State state = new BossTrialSync.State(0, 1, 0, 0, 0);
    /** 收到包的时间，用来做淡出。 */
    private static long receivedAt;

    private BossTrialOverlay() {
    }

    /** 由 {@code BossTrialSync} 的主线程 consumer 调用。 */
    public static void accept(BossTrialSync.State next) {
        state = next;
        receivedAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (state.ticksLeft() <= 0) {
            return;
        }
        // 3 秒没更新就当结束了（防止 Boss 死了/玩家跑远了特效还挂着）
        if (System.currentTimeMillis() - receivedAt > 3200L) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();

        float ratio = Mth.clamp(state.ticksLeft() / (float) Math.max(1, state.total()), 0.0F, 1.0F);
        boolean urgent = state.ticksLeft() <= 60;
        float pulse = urgent
                ? 0.55F + 0.45F * (float) Math.sin(state.ticksLeft() * 0.9)
                : 1.0F;

        int r = (int) (state.r() * 255);
        int gr = (int) (state.g() * 255);
        int b = (int) (state.b() * 255);

        RenderSystem.enableBlend();
        // 1) 四条边：元素色氛围（内侧透明）
        int inset = (int) (10 + 18 * (1.0F - ratio));      // 越接近结束，边框越往里收
        g.fillGradient(0, 0, w, inset, argb(150, r, gr, b), argb(0, r, gr, b));
        g.fillGradient(0, h - inset, w, h, argb(0, r, gr, b), argb(150, r, gr, b));
        g.fillGradient(0, 0, inset, h, argb(120, r, gr, b), argb(0, r, gr, b));
        g.fillGradient(w - inset, 0, w, h, argb(0, r, gr, b), argb(120, r, gr, b));

        // 2) 最后 3 秒：整屏红闪（心跳）
        if (urgent) {
            int alpha = (int) (70 * pulse);
            g.fill(0, 0, w, h, argb(alpha, 200, 40, 40));
        }

        // 3) 倒计时：正上方大数字
        int seconds = Math.max(1, (state.ticksLeft() + 19) / 20);
        String text = String.valueOf(seconds);
        var font = mc.font;
        float scale = urgent ? 3.2F : 2.4F;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(w / 2.0F, h * 0.10F, 300.0F);
        pose.scale(scale, scale, 1.0F);
        int colour = urgent ? 0xFFFF5555 : 0xFFFFFFFF;
        g.drawString(font, text, -font.width(text) / 2, 0, colour, true);
        pose.popPose();

        // 4) 进度细条（在倒计时下面）
        int barW = (int) (w * 0.42F);
        int barX = (w - barW) / 2;
        int barY = (int) (h * 0.10F + 34 * scale * 0.5F + 6);
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, argb(120, 0, 0, 0));
        g.fill(barX, barY, barX + (int) (barW * ratio), barY + 4, argb(220, r, gr, b));
        RenderSystem.disableBlend();
    }

    private static int argb(int a, int r, int g, int b) {
        return (Mth.clamp(a, 0, 255) << 24) | (Mth.clamp(r, 0, 255) << 16)
                | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
    }
}
