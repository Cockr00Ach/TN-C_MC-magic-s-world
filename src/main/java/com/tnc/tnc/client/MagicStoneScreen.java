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

import java.util.List;

/**
 * 魔法石界面（设计文档第九节：这是 TN-C <b>唯一</b>的功能性 GUI）。
 *
 * <p>显示四样东西：亲和力 / 魔法点数 / 已学法术 / 可解锁法术，并在这里花点数解锁。
 * 数据来自客户端那份镜像（服务端同步过来的），解锁动作发网络包给服务端判定。
 */
public class MagicStoneScreen extends Screen {

    private static final int PANEL_W = 520;   // 变宽了：法术目录要按链分三列
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

    /** 界面当前显示哪个元素的法术（底部的雷/火切换按钮改它）。 */
    private Element shownElement = Element.LIGHTNING;

    public MagicStoneScreen() {
        super(Component.literal("魔法石"));
    }

    // ------------------------------------------------------------------
    //  布局
    // ------------------------------------------------------------------

    private int left() {
        return (this.width - panelWidth()) / 2;
    }

    /**
     * 面板实际宽度：屏幕比面板窄时收缩，别把内容顶到屏幕外。
     *
     * <p>用户会用较小的窗口玩（截图见过 GUI 只有 428 宽的情况），
     * 固定 520 的面板在小窗口里会左右被裁掉 —— 而法术按钮在面板右半边，
     * 一裁就又变成"点不到"。所以宽度取 min(设计宽度, 屏幕宽 - 16)。
     */
    private int panelWidth() {
        return Math.min(PANEL_W, Math.max(260, this.width - 16));
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
        // 元素切换：每个"有法术的元素"一个按钮（目前是雷 / 火）。
        // 加新元素时这里不用动 —— 遍历 Element.values() 自动多一个。
        int tabX = left() + 12;
        for (Element element : Element.values()) {
            if (SpellCatalog.chainsOf(element).isEmpty()) {
                continue;
            }
            final Element target = element;
            Button tab = Button.builder(Component.literal(element.cn()), clicked -> {
                        shownElement = target;
                        rebuildWidgets();
                    })
                    .bounds(tabX, top() + PANEL_H - 24, 40, 16)
                    .build();
            tab.active = element != shownElement;   // 当前元素灰掉
            addRenderableWidget(tab);
            tabX += 44;
        }
        // 每个法术一行按钮，**按链分列**排。
        //
        // 以前是"名字文本 + 右侧单独一个「解锁 N点」按钮"，所有按钮排在同一列往下叠：
        // 5 个法术时没问题，15 个法术（三条链）就会排到面板外面、跑到屏幕外点不到，
        // 而且那一列还压住了第三条链的名字。现在按钮就是整行（列宽），三个链并排。
        List<SpellCatalog.Chain> chains = SpellCatalog.chainsOf(shownElement);
        int colW = columnWidth(chains.size());
        int chainIndex = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left() + LIST_X + chainIndex * colW;
            int rowY = top() + LIST_Y + 16;
            for (SpellCatalog.Entry entry : SpellCatalog.of(shownElement, chain)) {
                final SpellCatalog.Entry target = entry;
                MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
                // 点不了的按钮全都灰着，光看按钮分不清"已学"还是"点数不足" ——
                // 所以把短状态直接写进标签，完整原因还是在悬停提示里
                String short_ = shortState(state);
                String label = short_.isEmpty()
                        ? entry.displayName()
                        : entry.displayName() + " · " + short_;
                Button button = Button.builder(Component.literal(fitLabel(label, colW - 16)),
                                clicked -> MagicStoneNetwork.requestUnlock(target.id()))
                        .bounds(colX, rowY, colW - 8, 16)
                        .build();
                button.active = state == MagicStoneLearning.Result.OK;
                addRenderableWidget(button);
                rowY += ROW_H;
            }
            chainIndex++;
        }
    }

    /** 法术目录每列多宽（渲染和按钮布局必须用同一个算法，否则又会对不上）。 */
    private int columnWidth(int chainCount) {
        return (panelWidth() - LIST_X - 12) / Math.max(1, chainCount);
    }

    /** 列太窄时把法术名截断（完整名字在悬停提示里），免得按钮文字溢出去。 */
    private String fitLabel(String name, int maxWidth) {
        if (this.font == null || this.font.width(name) <= maxWidth) {
            return name;
        }
        String cut = name;
        while (cut.length() > 2 && this.font.width(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
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
        int panelW = panelWidth();

        graphics.fill(left - 2, top - 2, left + panelW + 2, top + PANEL_H + 2, COLOR_BORDER);
        graphics.fill(left, top, left + panelW, top + PANEL_H, COLOR_PANEL);
        graphics.drawCenteredString(this.font, this.title, left + panelW / 2, top + 8, COLOR_TITLE);

        MagicStoneData data = clientData();
        if (data == null) {
            graphics.drawCenteredString(this.font, "等待服务端数据…", left + panelW / 2, top + PANEL_H / 2, COLOR_BAD);
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
        graphics.fill(left + 8, top + 54, left + panelWidth() - 8, top + 55, 0x40FFFFFF);
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

        // 法术目录：**每条链一列**（雷系现在有主链/雷球/雷速三条，15 个法术挤一列会跑出面板）
        // 每行的按钮在 init() 里创建（名字就是按钮的标签），这里只画表头和悬停提示。
        List<SpellCatalog.Chain> chains = SpellCatalog.chainsOf(shownElement);
        int colW = columnWidth(chains.size());
        graphics.drawString(this.font, shownElement.cn() + "系法术（每条链独立进度，高级自动替换低级）",
                left + LIST_X, top + LIST_Y - 14, COLOR_TITLE, false);
        int chainIndex = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left + LIST_X + chainIndex * colW;
            graphics.drawString(this.font, chain.cn(), colX, top + LIST_Y, COLOR_TITLE, false);
            chainIndex++;
        }

        // 先画面板/文字/表头，再让 super.render 画那一排按钮
        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停提示必须放在**按钮之后**画：按钮是 widget，super.render 会盖住先画的东西
        int chainIndex2 = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left + LIST_X + chainIndex2 * colW;
            int spellY = top + LIST_Y + 16;
            for (SpellCatalog.Entry entry : SpellCatalog.of(shownElement, chain)) {
                if (isHovering(colX, spellY, colW - 8, 16, mouseX, mouseY)) {
                    MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
                    // 消耗要按玩家自己的上限算（随上限等比放大），不然提示和实际扣费对不上
                    graphics.renderTooltip(this.font,
                            Component.literal(entry.fullName()
                                    + "\n§7解锁消耗 " + entry.learnCost() + " 点"
                                    + "\n§7施放消耗 " + entry.manaCostFor(data.getMaxMana()) + " 魔力"
                                    + "\n§7状态 " + stateText(state, entry)),
                            mouseX, mouseY);
                }
                spellY += ROW_H;
            }
            chainIndex2++;
        }
    }

    private static boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 灰按钮上的短状态（OK 返回空串）。 */
    private static String shortState(MagicStoneLearning.Result state) {
        return switch (state) {
            case OK -> "";
            case ALREADY_LEARNED -> "已学";
            case AFFINITY_TOO_LOW -> "亲和力不够";
            case OUT_OF_ORDER -> "需前置";
            case NOT_ENOUGH_POINTS -> "点数不足";
            // default 是故意留的：以后给 Result 加新值（例如"尚未实装"）时，
            // 这里不会因为枚举不穷尽而编译不过 —— 分两步改就不用一次动三个文件。
            default -> "";
        };
    }

    private static String stateText(MagicStoneLearning.Result state, SpellCatalog.Entry entry) {
        return switch (state) {
            case OK -> "可解锁";
            case ALREADY_LEARNED -> "已学";
            case AFFINITY_TOO_LOW -> "亲和力不足";
            case OUT_OF_ORDER -> "需先学上一级";
            case NOT_ENOUGH_POINTS -> "点数不足";
            default -> "尚未实装";
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
