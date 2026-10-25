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

    private static final int BAR_W = INNER_W / 2;    // 182
    private static final int BAR_H = INNER_H / 2;    // 26
    private static final int ART_W = (int) Math.round(ART_TEX_W / 2.0); // 194
    private static final int ART_H = (int) Math.round(ART_TEX_H / 2.0); // 71
    private static final int ART_OFF_X = INNER_LEFT / 2;  // 5
    private static final int ART_OFF_Y = INNER_TOP / 2;   // 37

    /** 名字：橙红色、带阴影，与火山顶留 6px 间隔（避开火焰与烟）。 */
    private static final int NAME_COLOR = 0xFF5A24;
    private static final int NAME_GAP = 6;
    private static final int NAME_H = 9;
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

        int shift = Math.max(0, (ART_OFF_Y + NAME_GAP + NAME_H + 4) - y);
        int barY = y + shift;
        int artX = x - ART_OFF_X;
        int artY = barY - ART_OFF_Y;

        // 1. 空血底图（不裁切）
        for (int dx = 0; dx < BAR_W; dx += 16) {
            int tw = Math.min(16, BAR_W - dx);
            graphics.blit(COOLED, x + dx, barY, dx, 0, tw, BAR_H, BAR_W, BAR_H);
        }

        // 2. 熔岩填充：左侧为剩余血量，按 progress 裁切，不横向压缩
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

        // 3. 前景：岩框 + 中央火山（不随血量变化）
        if (full) {
            graphics.blit(ART, artX, artY, 0, 0, ART_W, ART_H, ART_TEX_W, ART_TEX_H);
        } else {
            int v = CROP_TOP;
            int h = ART_TEX_H - CROP_TOP;
            graphics.blit(ART, artX, barY - CROP_TOP / 2, 0, v, ART_W, (int) Math.round(h / 2.0), ART_TEX_W, ART_TEX_H);
        }

        // 4. 名字：橙红 + 阴影，跟着组件走
        int nameY = full ? artY - NAME_GAP - NAME_H : barY - NAME_GAP - NAME_H;
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
