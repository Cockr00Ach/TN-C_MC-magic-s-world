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
 * 火山巨龟 Boss 血条：直接使用参考图美术（岩框 + 中央火山），内框熔岩由代码按血量填充。
 *
 * <p>art.png 由 tools/build_bossbar_art.py 从参考图生成：内框里的亮熔岩被抠成透明，
 * 保留岩框、火山体与深色岩块；代码先画冷却槽与熔岩，再把 art 叠在上面，
 * 于是熔岩只在内框里显示，血量比例完全来自服务端同步的 progress。</p>
 *
 * <p>布局（GUI 逻辑像素）：art 内框 182x14，整张 art 199x61，名字在 art 上方。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VolcanicBossBarOverlay {

    private static final ResourceLocation ART = id("textures/gui/bossbar/art.png");
    private static final ResourceLocation LAVA = id("textures/gui/bossbar/lava.png");
    private static final ResourceLocation COOLED = id("textures/gui/bossbar/cooled.png");

    /** art.png 的实际像素尺寸（2 倍超采样，绘制时除以 2）。 */
    private static final int ART_TEX_W = 398;
    private static final int ART_TEX_H = 121;
    /** 内框在 art.png 里的左上角与尺寸（像素）。 */
    private static final int INNER_LEFT = 11;
    private static final int INNER_TOP = 42;
    private static final int INNER_W = 364;
    private static final int INNER_H = 28;

    /** 内框换算到 GUI 像素后的尺寸。 */
    private static final int BAR_W = INNER_W / 2;   // 182
    private static final int BAR_H = INNER_H / 2;   // 14
    private static final int ART_W = ART_TEX_W / 2; // 199
    private static final int ART_H = ART_TEX_H / 2; // 60
    private static final int ART_OFF_X = INNER_LEFT / 2;      // 5
    private static final int ART_OFF_Y = INNER_TOP / 2;       // 21
    /** 非第一条时裁掉火山上半部分，避免压住上一条血条。 */
    private static final int CROP_TOP = INNER_TOP - 8;        // 34
    private static final int NAME_H = 9;
    private static final int NAME_GAP = 2;

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
        boolean full = index == 0;

        int shift = Math.max(0, (ART_OFF_Y + NAME_GAP + NAME_H + 4) - y);
        int barY = y + shift;
        int artX = x - ART_OFF_X;
        int artY = barY - ART_OFF_Y;

        // 冷却槽
        for (int dx = 0; dx < BAR_W; dx += 16) {
            int tw = Math.min(16, BAR_W - dx);
            graphics.blit(COOLED, x + dx, barY, dx, 0, tw, BAR_H, BAR_W, BAR_H);
        }

        // 熔岩：宽度严格来自 progress，按 16px 平铺，不横向拉伸
        float progress = Math.max(0.0F, Math.min(1.0F, event.getBossEvent().getProgress()));
        int fillW = Math.round(BAR_W * progress);
        if (fillW > 0) {
            graphics.enableScissor(x, barY, x + fillW, barY + BAR_H);
            for (int dx = 0; dx < fillW; dx += 16) {
                int tw = Math.min(16, fillW - dx);
                graphics.blit(LAVA, x + dx, barY, dx, 0, tw, BAR_H, BAR_W, BAR_H);
            }
            graphics.disableScissor();
        }

        // 岩框 + 火山叠在最上层；非第一条只画条子部分，避免盖住上一条
        if (full) {
            graphics.blit(ART, artX, artY, 0, 0, ART_W, ART_H, ART_TEX_W, ART_TEX_H);
        } else {
            int v = CROP_TOP;
            int h = ART_TEX_H - CROP_TOP;
            graphics.blit(ART, artX, barY - CROP_TOP / 2, 0, v, ART_W, h / 2, ART_TEX_W, ART_TEX_H);
        }

        int nameY = full ? artY - NAME_GAP - NAME_H : barY - NAME_GAP - NAME_H;
        graphics.drawString(font, name, x + BAR_W / 2 - font.width(name) / 2, nameY, 0xFFF3E2, true);
    }

    /** 只认默认译名，被命名牌改名的个体回退原版血条。 */
    private static boolean isVolcanicTortoise(Component name) {
        if (name.getContents() instanceof TranslatableContents translatable) {
            return "entity.tnc.volcanic_tortoise".equals(translatable.getKey());
        }
        return false;
    }
}
