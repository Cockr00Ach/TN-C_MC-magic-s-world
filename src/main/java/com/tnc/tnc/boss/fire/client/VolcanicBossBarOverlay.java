package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 火山巨龟 Boss 血条：参考图美术（岩框 + 中央火山），内槽熔岩按血量裁切。
 *
 * <p>血条 ID 就是实体 UUID（见 VolcanicTortoiseEntity 的 ServerBossEvent 构造），
 * 因此这里直接用 {@code BossEvent.getId()} 回查客户端实体，不依赖名字/语言，
 * 改名或同名实体都不会误判。</p>
 *
 * <p>分层：冷却槽（不裁切）→ 熔岩（按 progress 裁切，不缩放）→ art.png（岩框+火山，不裁切）→ 名字。</p>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VolcanicBossBarOverlay {

    private static final ResourceLocation ART = id("textures/gui/bossbar/art.png");
    private static final ResourceLocation LAVA = id("textures/gui/bossbar/lava.png");
    private static final ResourceLocation COOLED = id("textures/gui/bossbar/cooled.png");

    /** art.png 像素尺寸（2 倍超采样）。 */
    private static final int ART_TEX_W = 380;
    private static final int ART_TEX_H = 138;
    /** 内槽在 art.png 中的位置与尺寸（像素）。 */
    private static final int INNER_LEFT = 6;
    private static final int INNER_TOP = 73;
    private static final int INNER_W = 364;
    private static final int INNER_H = 51;
    /** lava.png / cooled.png 的原始像素尺寸。 */
    private static final int LAVA_TEX_W = 182;
    private static final int LAVA_TEX_H = 25;

    /** 整条等比缩到参考图的一半（宽度 182 -> 91）。 */
    private static final double UI_SCALE = 0.5;
    private static final int BAR_W = (int) Math.round(INNER_W / 2.0 * UI_SCALE);   // 91
    private static final int BAR_H = (int) Math.round(INNER_H / 2.0 * UI_SCALE);   // 13
    private static final int ART_W = (int) Math.round(ART_TEX_W / 2.0 * UI_SCALE); // 95
    private static final int ART_H = (int) Math.round(ART_TEX_H / 2.0 * UI_SCALE); // 35
    private static final int ART_OFF_X = (int) Math.round(INNER_LEFT / 2.0 * UI_SCALE); // 2
    private static final int ART_OFF_Y = (int) Math.round(INNER_TOP / 2.0 * UI_SCALE);  // 18

    /** 名字：橙红色、带阴影，画在血条下方。 */
    private static final int NAME_COLOR = 0xFF5A24;
    private static final int NAME_GAP = 4;
    private static final int NAME_H = 9;
    /** 整条相对原版 Boss 栏位置再上移的像素数。 */
    private static final int LIFT = 5;
    /** 非第一条时裁掉火山上半段，避免压住上一条血条。 */
    private static final int CROP_TOP = INNER_TOP - 10;

    private VolcanicBossBarOverlay() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!isVolcanicTortoise(event)) {
            return;
        }
        event.setCanceled(true);

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        Component name = event.getBossEvent().getName();

        int x = event.getX();
        int y = event.getY();
        int index = Math.max(0, (y - 12) / 19);
        boolean full = index == 0;

        int shift = Math.max(0, (ART_OFF_Y + 4) - y);
        // 名字挪到血条下方：整条上移，给下方名字腾出空间
        int barY = Math.max(2, y + shift - LIFT);
        int artX = x - ART_OFF_X;
        int artY = barY - ART_OFF_Y;

        // 1. 空血底图（整张缩放，不裁切）
        graphics.blit(COOLED, x, barY, BAR_W, BAR_H, 0, 0, LAVA_TEX_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);

        // 2. 熔岩填充：左侧为剩余血量，按 progress 裁切，不横向压缩
        float progress = Math.max(0.0F, Math.min(1.0F, event.getBossEvent().getProgress()));
        int fillW = Math.round(BAR_W * progress);
        if (fillW > 0) {
            graphics.enableScissor(x, barY, x + fillW, barY + BAR_H);
            graphics.blit(LAVA, x, barY, BAR_W, BAR_H, 0, 0, LAVA_TEX_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);
            graphics.disableScissor();
        }

        // 3. 前景：岩框 + 中央火山（不随血量变化）
        if (full) {
            graphics.blit(ART, artX, artY, ART_W, ART_H, 0, 0, ART_TEX_W, ART_TEX_H, ART_TEX_W, ART_TEX_H);
        } else {
            int v = CROP_TOP;
            int h = ART_TEX_H - CROP_TOP;
            graphics.blit(ART, artX, barY - CROP_TOP / 2, ART_W, (int) Math.round(h / 2.0),
                    0, v, ART_TEX_W, h, ART_TEX_W, ART_TEX_H);
        }

        // 4. 名字：血条正下方，橙红 + 阴影
        int nameY = barY + BAR_H + NAME_GAP;
        graphics.drawString(font, name, x + BAR_W / 2 - font.width(name) / 2, nameY, NAME_COLOR, true);
    }

    /** 血条 UUID 通过实体同步数据带过来：逐个比对，不靠名字匹配。 */
    private static boolean isVolcanicTortoise(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        UUID barId = event.getBossEvent().getId();
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity instanceof VolcanicTortoiseEntity tortoise && barId.equals(tortoise.bossBarUuid())) {
                return true;
            }
        }
        return false;
    }
}
