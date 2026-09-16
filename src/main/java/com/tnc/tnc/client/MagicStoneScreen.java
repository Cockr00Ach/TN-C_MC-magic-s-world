package com.tnc.tnc.client;

import com.tnc.tnc.Config;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.MagicStoneLearning;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 魔法石界面（设计文档第九节：这是 TN-C <b>唯一</b>的功能性 GUI）。
 *
 * <p>显示四样东西：亲和力 / 魔法点数 / 已学法术 / 可解锁法术，并在这里花点数解锁。
 * 数据来自客户端那份镜像（服务端同步过来的），解锁动作发网络包给服务端判定。
 */
public class MagicStoneScreen extends Screen {

    private static final int PANEL_W = 336;
    private static final int PANEL_H = 224;
    private static final int ROW_H = 22;

    private static final int COLOR_PANEL = 0xE6101018;
    private static final int COLOR_BORDER = 0xFF8E77E0;
    private static final int COLOR_TITLE = 0xFFE8DEFF;
    private static final int COLOR_TEXT = 0xFFCFCFD6;
    private static final int COLOR_DIM = 0xFF8A8A93;
    private static final int COLOR_OK = 0xFF6BE06B;
    private static final int COLOR_BAD = 0xFFE06B6B;

    private static final int LIST_X = 186;
    private static final int LIST_Y = 62;

    /**
     * 只在第一次 init 时拉数据。
     * 否则会死循环：init → 请求同步 → 服务端回包 → 刷新界面（rebuildWidgets → init）→ 又请求同步 …
     */
    private boolean syncRequested;

    public MagicStoneScreen() {
        super(Component.literal("魔法石"));
    }

    // ------------------------------------------------------------------
    //  布局
    // ------------------------------------------------------------------

    private int left() {
        return (this.width - PANEL_W) / 2;
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    protected void init() {
        if (!syncRequested) {
            syncRequested = true;
            MagicStoneNetwork.requestSync();
        }
        MagicStoneData data = clientData();
        if (data == null) {
            return;
        }
        int buttonX = left() + PANEL_W - 78;
        int y = top() + LIST_Y + 16;

        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            if (MagicStoneLearning.check(data, entry) == MagicStoneLearning.Result.OK) {
                final SpellCatalog.Entry target = entry;
                addRenderableWidget(Button.builder(Component.literal("解锁 " + Config.learnCostForTier(entry.tier()) + "点"),
                                button -> MagicStoneNetwork.requestUnlock(target.id()))
                        .bounds(buttonX, y, 68, 16)
                        .build());
            }
            y += ROW_H;
        }
    }

    /** 数据同步回来之后，让界面重新按最新数据摆按钮。 */
    public void refreshFromServer() {
        if (this.minecraft != null) {
            this.rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------
    //  绘制
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int left = left();
        int top = top();

        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, COLOR_BORDER);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, COLOR_PANEL);
        graphics.drawCenteredString(this.font, this.title, left + PANEL_W / 2, top + 8, COLOR_TITLE);

        MagicStoneData data = clientData();
        if (data == null) {
            graphics.drawCenteredString(this.font, "等待服务端数据…", left + PANEL_W / 2, top + PANEL_H / 2, COLOR_BAD);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // 顶部：魔力 + 魔法点数
        graphics.drawString(this.font, "魔力  " + data.getMana() + " / " + data.getMaxMana(),
                left + 12, top + 28, COLOR_TEXT, false);
        graphics.drawString(this.font, "魔法点数  " + data.getPointsAvailable(Config.pointThresholds) + " 可用"
                        + "（总 " + data.getPointsTotal(Config.pointThresholds)
                        + " · 已投 " + data.getPointsSpent() + "）",
                left + 12, top + 41, COLOR_TEXT, false);
        graphics.fill(left + 8, top + 54, left + PANEL_W - 8, top + 55, 0x40FFFFFF);
        graphics.fill(left + LIST_X - 8, top + 58, left + LIST_X - 7, top + PANEL_H - 8, 0x40FFFFFF);

        // 左列：七元素亲和力与进度
        graphics.drawString(this.font, "元素亲和 / 进度", left + 12, top + 62, COLOR_TITLE, false);
        int y = top + 78;
        for (Element element : Element.values()) {
            String line = element.cn() + "  亲和 " + data.getAffinity(element)
                    + "  进度 " + tierShort(data.getProgress(element))
                    + "  上限 " + Element.tierName(Math.max(1, data.maxTierFor(element)));
            graphics.drawString(this.font, line, left + 12, y, COLOR_TEXT, false);
            y += 12;
        }
        graphics.drawString(this.font, "已学 " + data.getLearned().size() + " 个法术",
                left + 12, y + 6, COLOR_DIM, false);

        // 右列：法术目录 + 状态
        graphics.drawString(this.font, "雷系法术", left + LIST_X, top + LIST_Y, COLOR_TITLE, false);
        int spellY = top + LIST_Y + 16;
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
            graphics.drawString(this.font, entry.displayName(), left + LIST_X, spellY + 4, stateColor(state), false);
            graphics.drawString(this.font, stateText(state, entry), left + LIST_X + 66, spellY + 4, COLOR_DIM, false);
            if (isHovering(left + LIST_X, spellY, 176, 16, mouseX, mouseY)) {
                // 消耗要按玩家自己的上限算（随上限等比放大），不然提示和实际扣费对不上
                graphics.renderTooltip(this.font,
                        Component.literal(entry.fullName()
                                + "\n§7解锁消耗 " + entry.learnCost() + " 点"
                                + "\n§7施放消耗 " + entry.manaCostFor(data.getMaxMana()) + " 魔力"),
                        mouseX, mouseY);
            }
            spellY += ROW_H;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int stateColor(MagicStoneLearning.Result state) {
        return switch (state) {
            case OK -> COLOR_OK;
            case ALREADY_LEARNED -> COLOR_DIM;
            default -> COLOR_BAD;
        };
    }

    private static String stateText(MagicStoneLearning.Result state, SpellCatalog.Entry entry) {
        return switch (state) {
            case OK -> "可解锁";
            case ALREADY_LEARNED -> "已学";
            case AFFINITY_TOO_LOW -> "亲和力不足";
            case OUT_OF_ORDER -> "需先学上一级";
            case NOT_ENOUGH_POINTS -> "点数不足";
        };
    }

    private static String tierShort(int tier) {
        return tier <= 0 ? "—" : Element.tierName(tier);
    }

    private static MagicStoneData clientData() {
        if (Minecraft.getInstance().player == null) {
            return null;
        }
        return MagicStone.getOrNull(Minecraft.getInstance().player);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
