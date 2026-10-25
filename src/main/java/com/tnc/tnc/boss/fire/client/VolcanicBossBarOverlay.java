package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 火山巨龟 Boss 血条：参考图美术（岩框 + 中央火山），内槽熔岩按血量裁切。
 *
 * <p>血条 ID 由实体同步数据带过来（{@code VolcanicTortoiseEntity#bossBarUuid()}），
 * 因此不受名字/语言影响，改名或同名实体都不会误判。</p>
 *
 * <p>绘制顺序：冷却槽 → 熔岩（按 progress 裁切）→ art 三段（左框 / 火山 / 右框）→ 名字。
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

        // 自己算居中：原版 x 是按 182 宽算的，控件宽度不同时不能直接用。
        int guiW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
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

        // 1. 空血底图
        graphics.blit(COOLED, barX, barY, barW, barH, 0, 0, LAVA_TEX_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);

        // 2. 熔岩：左侧是剩余血量，按 progress 裁切（纹理不横向压缩）
        float progress = Math.max(0.0F, Math.min(1.0F, event.getBossEvent().getProgress()));
        int fillW = Math.round(barW * progress);
        if (fillW > 0) {
            graphics.enableScissor(barX, barY, barX + fillW, barY + barH);
            graphics.blit(LAVA, barX, barY, barW, barH, 0, 0, LAVA_TEX_W, LAVA_TEX_H, LAVA_TEX_W, LAVA_TEX_H);
            graphics.disableScissor();
        }

        // 3. 前景三段：左右框横向拉伸，火山保持比例
        int v = full ? 0 : INNER_TOP_TEX - 10;
        int srcH = full ? ART_TEX_H : ART_TEX_H - v;
        int drawH = full ? ART_H : Math.max(12, ART_H - 4);
        int drawY = full ? artY : barY - 4;

        graphics.blit(ART, artX, drawY, SIDE_W, drawH,
                0, v, ART_LEFT_TEX, srcH, ART_TEX_W, ART_TEX_H);
        graphics.blit(ART, artX + SIDE_W, drawY, CENTER_W, drawH,
                ART_LEFT_TEX, v, ART_CENTER_TEX, srcH, ART_TEX_W, ART_TEX_H);
        graphics.blit(ART, artX + SIDE_W + CENTER_W, drawY, SIDE_W, drawH,
                ART_LEFT_TEX + ART_CENTER_TEX, v, ART_RIGHT_TEX, srcH, ART_TEX_W, ART_TEX_H);

        // 4. 名字：血条正下方居中（以控件中心对齐）
        int nameY = barY + barH + NAME_GAP;
        graphics.drawString(font, name, artX + ART_W / 2 - font.width(name) / 2, nameY, NAME_COLOR, true);
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
