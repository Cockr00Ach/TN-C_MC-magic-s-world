package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 火山巨龟 Boss 血条：参考图美术（岩框 + 中央火山），内槽熔岩按血量裁切。
 *
 * <p>血条 ID 由实体同步数据带过来（{@code VolcanicTortoiseEntity#bossBarUuid()}），
 * 因此不受名字/语言影响，改名或同名实体都不会误判。</p>
 *
 * <p>绘制顺序：冷却槽 → 受击余量 → 流动熔岩 → 火星 → art 三段（左框 / 火山 / 右框）→ 名字。
 * art 分三段是为了“加长但不拉伸火山”：只有左右两段被横向拉伸，中央火山保持原始比例。</p>
 *
 * <p>血条矩形不是写死的常量，而是从 art 的<b>内槽矩形</b>按实际缩放反算再内缩 1–2px，
 * 这样无论 art 被拉多长，熔岩都不会越过岩框、也不会和内框底边留缝。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VolcanicBossBarOverlay {

    private static final ResourceLocation ART = id("textures/gui/bossbar/art.png");
    private static final ResourceLocation LAVA = id("textures/gui/bossbar/lava.png");
    private static final ResourceLocation COOLED = id("textures/gui/bossbar/cooled.png");

    // ---- art.png 原始像素（2 倍超采样） ----
    private static final int ART_TEX_W = 380;
    private static final int ART_TEX_H = 138;
    private static final int INNER_LEFT_TEX = 6;
    private static final int INNER_TOP_TEX = 73;
    private static final int INNER_W_TEX = 364;
    private static final int INNER_H_TEX = 51;
    /** art 三段切割：0..120 左框，120..260 火山，260..380 右框。 */
    private static final int ART_LEFT_TEX = 120;
    private static final int ART_CENTER_TEX = 140;
    private static final int ART_RIGHT_TEX = ART_TEX_W - ART_LEFT_TEX - ART_CENTER_TEX;

    private static final int LAVA_TEX_W = 182;
    private static final int LAVA_TEX_H = 25;

    // ---- GUI 尺寸 ----
    /** 中央火山在 GUI 里的宽度（140 tex × 0.25）。 */
    private static final int CENTER_W = Math.round(ART_CENTER_TEX * 0.25F);
    /** 左右框各占的宽度：两段平摊加长的部分。 */
    private static final int SIDE_W = 78;
    /** art 总宽（也是整个控件宽度，用于居中）。 */
    private static final int ART_W = SIDE_W * 2 + CENTER_W;
    /** art 显示高度（保持 0.5 缩放，火山不纵向压扁）。 */
    private static final int ART_H = 35;

    /** 熔岩相对内槽再内缩的像素：避开岩框描边与圆角。 */
    private static final int INSET_X = 2;
    private static final int INSET_Y = 1;

    /** 名字画在血条下方。 */
    private static final int NAME_COLOR = 0xFF5A24;
    private static final int NAME_GAP = 4;
    /** 相对原版 Boss 栏位置再上移。 */
    private static final int LIFT = 6;

    /** lava.png 的前 16 像素是完整平铺周期；不能用 182px 全图循环，会有接缝。 */
    private static final int FLOW_TILE_W = 16;
    /** 原图上方的静态烟被裁掉，单独取白烟区域绘制动态烟团。 */
    private static final int PLUME_CROP_TOP = 43;
    private static final int SMOKE_TEX_X = 181;
    private static final int SMOKE_TEX_Y = 2;
    private static final int SMOKE_TEX_W = 60;
    private static final int SMOKE_TEX_H = 32;
    private static final int SMOKE_PUFFS = 7;
    private static final double SMOKE_CYCLE_TICKS = 48.0;
    private static final int STATE_EXPIRY_TICKS = 40;
    private static final Map<UUID, TrackedBar> ANIMATIONS = new HashMap<>();
    private static ClientLevel animationLevel;

    private static final class TrackedBar {
        private final BossBarAnimation animation;
        private double lastSeen;

        private TrackedBar(BossBarAnimation animation) {
            this.animation = animation;
        }
    }

    private VolcanicBossBarOverlay() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        VolcanicTortoiseEntity tortoise = findTortoise(event.getBossEvent().getId());
        if (tortoise == null) {
            return;
        }
        event.setCanceled(true);

        Minecraft minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        double tick = minecraft.level.getGameTime() + event.getPartialTick();
        float progress = event.getBossEvent().getProgress();
        TrackedBar tracked = ANIMATIONS.computeIfAbsent(event.getBossEvent().getId(), ignored ->
                new TrackedBar(new BossBarAnimation(progress, tortoise.isPhaseTwo(),
                        tortoise.isEnraged(), tortoise.isWeakened(), tick)));
        tracked.lastSeen = tick;
        BossBarAnimation.Frame frame = tracked.animation.update(progress, tortoise.isPhaseTwo(),
                tortoise.isEnraged(), tortoise.isWeakened(), tick);

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        Component name = event.getBossEvent().getName();

        // 自己算居中：原版 x 是按 182 宽算的，控件宽度不同时不能直接用。
        int guiW = event.getWindow().getGuiScaledWidth();
        int y = event.getY();
        int index = Math.max(0, (y - 12) / 19);
        boolean full = index == 0;

        float sy = ART_H / (float) ART_TEX_H;
        int artOffY = Math.round(INNER_TOP_TEX * sy);           // 19
        int shift = Math.max(0, artOffY + 4 - y);
        int artX = (guiW - ART_W) / 2;
        int artY = Math.max(2, y + shift - LIFT) - artOffY;

        // ---- 由内槽矩形反算血条矩形（再内缩 INSET，避免压到框线/圆角）----
        float sx = ART_W / (float) ART_TEX_W;                   // 191/380
        int barX = artX + Math.round(INNER_LEFT_TEX * sx) + INSET_X;
        int barY = artY + Math.round(INNER_TOP_TEX * sy) + INSET_Y;
        int barW = Math.max(8, Math.round(INNER_W_TEX * sx) - INSET_X * 2);
        int barH = Math.max(4, Math.round(INNER_H_TEX * sy) - INSET_Y * 2);

        // 1. 空血底图始终静止，方便区分已经损失的血量。
        graphics.blit(COOLED, barX, barY, barW, barH, 0, 0, LAVA_TEX_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);

        // 2. 余量用暗橙色区别于真血量，短暂停留后冷却，回血时立即清除。
        int fillW = Math.round(barW * frame.progress());
        int trailW = Math.round(barW * frame.trail());
        if (trailW > fillW) {
            graphics.fill(barX + fillW, barY, barX + trailW, barY + barH, 0xB06E2913);
            graphics.fill(barX + fillW, barY + 1, barX + trailW, barY + 2, 0xD8DF8339);
        }
        drawLava(graphics, barX, barY, barW, barH, fillW, frame);

        if (full && fillW > 0) {
            drawEmbers(graphics, artX + SIDE_W + CENTER_W / 2, artY + 13, frame);
        }

        // 3. 前景三段：左右框横向拉伸，火山保持比例
        int v = full ? 0 : INNER_TOP_TEX - 10;
        int srcH = full ? ART_TEX_H : ART_TEX_H - v;
        int cropTop = Math.round(v * sy);
        int drawH = ART_H - cropTop;
        int drawY = artY + cropTop;

        graphics.blit(ART, artX, drawY, SIDE_W, drawH,
                0, v, ART_LEFT_TEX, srcH, ART_TEX_W, ART_TEX_H);
        int centerV = full ? PLUME_CROP_TOP : v;
        int centerTop = Math.round(centerV * sy);
        graphics.blit(ART, artX + SIDE_W, artY + centerTop, CENTER_W, ART_H - centerTop,
                ART_LEFT_TEX, centerV, ART_CENTER_TEX, ART_TEX_H - centerV, ART_TEX_W, ART_TEX_H);
        graphics.blit(ART, artX + SIDE_W + CENTER_W, drawY, SIDE_W, drawH,
                ART_LEFT_TEX + ART_CENTER_TEX, v, ART_RIGHT_TEX, srcH, ART_TEX_W, ART_TEX_H);

        if (full && fillW > 0) {
            drawSmoke(graphics, artX + SIDE_W + CENTER_W / 2, artY + 12, frame, tick);
        }

        // 4. 名字：血条正下方居中（以控件中心对齐）
        int nameY = barY + barH + NAME_GAP;
        graphics.drawString(font, name, artX + ART_W / 2 - font.width(name) / 2, nameY, NAME_COLOR, true);
        // 原版 19px 行距容不下岩框和下置名字；给后续 Boss 留足真实绘制高度。
        event.setIncrement(Math.max(event.getIncrement(), nameY + font.lineHeight + LIFT + 8 - y));
    }

    private static void drawLava(GuiGraphics graphics, int x, int y, int width, int height,
                                 int fill, BossBarAnimation.Frame frame) {
        if (fill <= 0) {
            return;
        }
        int tint = frame.lavaTint();
        graphics.enableScissor(x, y, x + fill, y + height);
        try {
            graphics.setColor((tint >> 16 & 255) / 255.0F, (tint >> 8 & 255) / 255.0F,
                    (tint & 255) / 255.0F, 1.0F);
            int offset = (int) (frame.flow() % FLOW_TILE_W);
            for (int tileX = x - offset; tileX < x + fill; tileX += FLOW_TILE_W) {
                graphics.blit(LAVA, tileX, y, FLOW_TILE_W, height,
                        0, 0, FLOW_TILE_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);
            }
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            // 虚弱时冷却岩覆盖上来，保留少量熔岩缓慢流动。
            if (frame.weak() > 0.0F) {
                graphics.setColor(1.0F, 1.0F, 1.0F, frame.weak() * 0.72F);
                graphics.blit(COOLED, x, y, width, height,
                        0, 0, LAVA_TEX_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            double pulse = 0.5 + Math.sin(frame.flow() * 0.42) * 0.5;
            graphics.fill(x, y, x + fill, y + height,
                    argb((int) (frame.rage() * (18 + pulse * 42)), 0xFF4A16));
            graphics.fill(x, y, x + fill, y + height,
                    argb(Math.round(frame.flash() * 70), 0xFFE7B0));

            // 稀疏亮点上浮，始终被裁在真实剩余血量内。
            for (int i = 0; i < 8; i++) {
                double age = (frame.flow() / 24.0 + i * 0.173) % 1.0;
                int bubbleX = x + Math.floorMod(i * 29 + 11, width);
                int bubbleY = y + height - 1 - (int) (age * (height - 1));
                int alpha = (int) (Math.sin(age * Math.PI) * 150 * (1 - frame.weak() * 0.9));
                graphics.fill(bubbleX, bubbleY, bubbleX + 2, bubbleY + 1, argb(alpha, 0xFFE6A0));
            }
            if (fill < width) {
                graphics.fill(x + fill - 1, y + 1, x + fill, y + height - 1,
                        argb((int) (170 * (1 - frame.weak() * 0.8)), 0xFFCE70));
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.disableScissor();
        }
    }

    /** 复用原画白烟，以交错生命周期连续发射；上浮、变大、侧摆、淡出均按游戏时间推进。 */
    private static void drawSmoke(GuiGraphics graphics, int x, int y,
                                  BossBarAnimation.Frame frame, double tick) {
        try {
            for (int i = 0; i < SMOKE_PUFFS; i++) {
                double age = (tick / SMOKE_CYCLE_TICKS + i / (double) SMOKE_PUFFS) % 1.0;
                int width = (int) Math.round(7.0 + age * 6.0);
                int height = (int) Math.round(4.0 + age * 4.0);
                double drift = age * 3.0 + Math.sin(tick * 0.035 + i * 1.7) * (0.6 + age);
                int drawX = (int) Math.round(x + drift - width * 0.5);
                int drawY = (int) Math.round(y - age * 5.0 - height * 0.5);
                float alpha = (float) (Math.sin(age * Math.PI)
                        * (0.6 + frame.rage() * 0.12) * (1.0 - frame.weak() * 0.25));
                float heat = frame.rage() * (float) (1.0 - age);
                graphics.setColor(0.96F + heat * 0.04F,
                        0.90F - heat * 0.10F + frame.weak() * 0.04F,
                        0.87F - heat * 0.20F + frame.weak() * 0.07F, alpha);
                graphics.blit(ART, drawX, drawY, width, height,
                        SMOKE_TEX_X, SMOKE_TEX_Y, SMOKE_TEX_W, SMOKE_TEX_H, ART_TEX_W, ART_TEX_H);
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawEmbers(GuiGraphics graphics, int x, int y, BossBarAnimation.Frame frame) {
        int count = 4 + Math.round(frame.rage() * 3);
        for (int i = 0; i < count; i++) {
            double age = (frame.flow() / 26.0 + i * 0.213) % 1.0;
            int emberX = x + (int) (Math.sin(i * 2.4 + age * 3.0) * (2 + age * 4));
            int emberY = Math.max(1, y - (int) (age * 12));
            int alpha = (int) (Math.sin(age * Math.PI) * 210 * (1 - frame.weak()));
            graphics.fill(emberX, emberY, emberX + 1, emberY + 1, argb(alpha, 0xFFD171));
        }
    }

    private static int argb(int alpha, int rgb) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        resetForLevel(level);
        if (level != null) {
            double tick = level.getGameTime();
            ANIMATIONS.values().removeIf(bar -> tick - bar.lastSeen > STATE_EXPIRY_TICKS);
        }
    }

    private static void resetForLevel(ClientLevel level) {
        if (animationLevel != level) {
            ANIMATIONS.clear();
            animationLevel = level;
        }
    }

    /** 血条 UUID 通过实体同步数据带过来：逐个比对，不靠名字匹配。 */
    private static VolcanicTortoiseEntity findTortoise(UUID barId) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity tortoise && barId.equals(tortoise.bossBarUuid())) {
                return tortoise;
            }
        }
        return null;
    }
}
