package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 火山巨龟 Boss 血条的静态外观。
 *
 * <p>只替换“这一条”血条的绘制：其他 Boss 仍走原版。血量比例直接读
 * {@code BossEvent.getProgress()}（服务端同步值），客户端不参与任何血量计算。</p>
 *
 * <p>布局（GUI 逻辑像素，随 GUI 缩放）：</p>
 * <pre>
 *   小火山 34x26
 *   名字   8px
 *   血条   182x8（外框 186x12）
 * </pre>
 *
 * <p>当这条血条不是屏幕上第一条时跳过小火山，避免压住上一条 Boss 血条；
 * 名字与血条本身仍按原位置绘制。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VolcanicBossBarOverlay {

    private static final ResourceLocation FRAME = id("textures/gui/bossbar/frame.png");
    private static final ResourceLocation LAVA = id("textures/gui/bossbar/lava.png");
    private static final ResourceLocation COOLED = id("textures/gui/bossbar/cooled.png");
    private static final ResourceLocation VOLCANO = id("textures/gui/bossbar/volcano.png");

    /** 原版血条内容区尺寸。 */
    private static final int BAR_W = 182;
    private static final int BAR_H = 8;
    private static final int FRAME_W = 186;
    private static final int FRAME_H = 12;
    private static final int VOLCANO_W = 34;
    private static final int VOLCANO_H = 26;
    /** 名字行高与名字到血条的间距。 */
    private static final int NAME_GAP = 4;
    /** 小火山与名字之间的间距。 */
    private static final int VOLCANO_GAP = 2;

    private VolcanicBossBarOverlay() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Component name = event.getBossEvent().getName();
        if (!isVolcanicTortoise(name)) {
            return;
        }
        event.setCanceled(true);

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        int x = event.getX();
        int y = event.getY();
        int index = Math.max(0, (y - 12) / 19);

        int totalH = FRAME_H + NAME_GAP + 9 + VOLCANO_GAP + VOLCANO_H;
        // 顶部空间不够时整体下移，保证小火山不出屏
        int shift = Math.max(0, (totalH + 4) - y);
        int barY = y + shift;
        int nameY = barY - NAME_GAP - 9;

        // 空槽（先铺满，再用熔岩覆盖剩余部分）
        for (int dx = 0; dx < BAR_W; dx += 16) {
            int tw = Math.min(16, BAR_W - dx);
            graphics.blit(COOLED, x + dx, barY, 0, 0, tw, BAR_H, 16, 8);
        }

        // 熔岩填充：宽度严格取自同步的 progress，纹理按 16px 平铺、不横向拉伸
        float progress = Math.max(0.0F, Math.min(1.0F, event.getBossEvent().getProgress()));
        int fillW = Math.round(BAR_W * progress);
        if (fillW > 0) {
            graphics.enableScissor(x, barY, x + fillW, barY + BAR_H);
            for (int dx = 0; dx < fillW; dx += 16) {
                int tw = Math.min(16, fillW - dx);
                graphics.blit(LAVA, x + dx, barY, 0, 0, tw, BAR_H, 16, 8);
            }
            graphics.disableScissor();
        }

        // 岩框压在填充之上，保证边界清楚
        graphics.blit(FRAME, x - 2, barY - 2, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        // 名字：始终绘制，第一条时上方再叠小火山
        graphics.drawString(font, name, x + BAR_W / 2 - font.width(name) / 2, nameY, 0xFFF3E2, true);

        if (index == 0) {
            int vx = x + BAR_W / 2 - VOLCANO_W / 2;
            int vy = nameY - VOLCANO_GAP - VOLCANO_H;
            if (vy >= 2) {
                graphics.blit(VOLCANO, vx, vy, 0, 0, VOLCANO_W, VOLCANO_H, VOLCANO_W, VOLCANO_H);
            }
        }
    }

    /** 只认默认译名，被命名牌改名的个体回退原版血条。 */
    private static boolean isVolcanicTortoise(Component name) {
        if (name.getContents() instanceof TranslatableContents translatable) {
            return "entity.tnc.volcanic_tortoise".equals(translatable.getKey());
        }
        return false;
    }
}
