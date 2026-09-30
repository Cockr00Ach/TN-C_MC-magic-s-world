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

    private static final int PANEL_W = 600;   // Four water routes when the window is wide enough.
    private static final int PANEL_H = 224;
    private static final int ROW_H = 22;

    private static final int COLOR_PANEL = 0xE62A1F16;   // dark brown
    private static final int COLOR_BORDER = 0xFF8A6A42;  // brown-gold border
    private static final int COLOR_TITLE = 0xFFF2DFC4;    // warm cream
    private static final int COLOR_TEXT = 0xFFDCC9B0;      // light tan
    private static final int COLOR_DIM = 0xFF9A8570;        // muted brown
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
    private int chainPage;

    // ------------------------------------------------------------------
    //  页签（学习 / 配键）
    // ------------------------------------------------------------------

    /** 当前页签。 */
    private boolean bindTab;

    /** 配键页：当前选中的槽（全局下标 0..17；-1 = 还没选）。 */
    private int bindSlot = -1;

    /**
     * 配键页里每个法术按钮的屏幕矩形 —— <b>只为画悬停提示</b>。
     *
     * <p>为什么记下来而不是在渲染时重算一遍排版：重算就是第二份排版算法，
     * 迟早和 {@link #initBindTab} 里那份跑偏（按钮和提示错位）✓。
     */
    private final java.util.Map<SpellCatalog.Entry, int[]> bindRects = new java.util.HashMap<>();

    /**
     * 配键列表滚到第几屏（0 基）。
     *
     * <p>为什么用"屏"而不是"行"：列表按"每屏 N 行 × 2 列"翻，一次滚一屏
     * 才不会出现"滚了半行、两列错位"的观感 ✗。
     */
    private int bindScroll;

    private int listX() { return Math.min(LIST_X, (int)(panelWidth()*.35)); }
    private int chainsPerPage() { return Math.max(1,(panelWidth()-listX()-12)/80); }
    private List<SpellCatalog.Chain> visibleChains() {
        var all=SpellCatalog.chainsOf(shownElement);
        int pages=Math.max(1,(all.size()+chainsPerPage()-1)/chainsPerPage());
        chainPage=Math.max(0,Math.min(pages-1,chainPage));
        int from=chainPage*chainsPerPage();
        return all.subList(from,Math.min(all.size(),from+chainsPerPage()));
    }

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
        initTabs();
        if (bindTab) {
            initBindTab(data);
        } else {
            initLearnTab(data);
        }
    }

    /** 顶部两个页签：学习（花点数解锁） / 配键（哪个键放哪个法术）。 */
    private void initTabs() {
        // 放在"魔法点数"那一行下面（top+40 起），避开左上角两行文字和中间的分隔线
        int y = top() + 42;
        addRenderableWidget(Button.builder(Component.literal(bindTab ? "学习" : "§l学习"),
                        b -> { bindTab = false; rebuildWidgets(); })
                .bounds(left() + 12, y, 50, 15).build());
        addRenderableWidget(Button.builder(Component.literal(bindTab ? "§l配键" : "配键"),
                        b -> { bindTab = true; bindSlot = -1; rebuildWidgets(); })
                .bounds(left() + 66, y, 50, 15).build());
    }

    /**
     * 学习页：每个法术一行按钮，<b>按链分列</b>排。
     *
     * <p>以前是"名字文本 + 右侧单独一个「解锁 N点」按钮"，所有按钮排在同一列往下叠：
     * 5 个法术时没问题，15 个法术（三条链）就会排到面板外面、跑到屏幕外点不到，
     * 而且那一列还压住了第三条链的名字。现在按钮就是整行（列宽），三个链并排。
     */
    private void initLearnTab(MagicStoneData data) {
        // 元素切换：每个"有法术的元素"一个按钮。
        // 加新元素时这里不用动 —— 遍历 Element.values() 自动多一个。
        int tabX = left() + listX();
        int tabWidth=Math.min(40,(panelWidth()-listX()-12)/Element.values().length-4);
        for (Element element : Element.values()) {
            if (SpellCatalog.chainsOf(element).isEmpty()) {
                continue;
            }
            final Element target = element;
            Button tab = Button.builder(Component.literal(element==Element.DARK?"暗":element.cn()), clicked -> {
                        shownElement = target;
                        chainPage=0;
                        rebuildWidgets();
                    })
                    .bounds(tabX, top() + PANEL_H - 24, tabWidth, 16)
                    .build();
            tab.active = element != shownElement;   // 当前元素灰掉
            addRenderableWidget(tab);
            tabX += tabWidth+4;
        }
        Button independent=Button.builder(Component.literal("独立魔法"),clicked->{shownElement=null;chainPage=0;rebuildWidgets();})
                .bounds(left()+12,top()+PANEL_H-24,listX()-24,16).build();
        independent.active=shownElement!=null;addRenderableWidget(independent);
        int pages=Math.max(1,(SpellCatalog.chainsOf(shownElement).size()+chainsPerPage()-1)/chainsPerPage());
        if(pages>1) {
            Button previous=Button.builder(Component.literal("◀"),b->{chainPage--;rebuildWidgets();}).bounds(left()+panelWidth()-48,top()+LIST_Y-16,18,12).build();
            Button next=Button.builder(Component.literal("▶"),b->{chainPage++;rebuildWidgets();}).bounds(left()+panelWidth()-28,top()+LIST_Y-16,18,12).build();
            previous.active=chainPage>0;next.active=chainPage<pages-1;addRenderableWidget(previous);addRenderableWidget(next);
        }
        List<SpellCatalog.Chain> chains = visibleChains();
        int colW = columnWidth(chains.size());
        int chainIndex = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left() + listX() + chainIndex * colW;
            int rowY = top() + LIST_Y + 16;
            for (SpellCatalog.Entry entry : SpellCatalog.of(shownElement, chain)) {
                final SpellCatalog.Entry target = entry;
                MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
                // 未学会的名字要不要遮：只有**王级(3)及以上**才乱码 ✗
                // —— 冒险者(1)、勇者(2) 是基础，未学也照样显示真名 ✓（用户要求）
                boolean hideName = !data.hasLearned(entry.id()) && entry.tier() >= 3;
                String label = hideName ? garble(entry.displayName()) : entry.displayName();
                Button button = Button.builder(Component.literal("    "+fitLabel(label, colW - 34)),
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

    /**
     * 配键页：左列 = 当前这一页的 9 个键（点一下选中），右列 = 已学法术（点一下放进选中的键）。
     *
     * <p>为什么要有这个页：引擎只给 9 个施法热键（右键 + 2~9），改不动 ✗ ——
     * 所以内容按页切（2 页 × 9），而"哪个键放哪个法术"由玩家自己排 ✓。
     * 一页装不下 18 个，装得下也要允许玩家为了手感重排。
     */
    private void initBindTab(MagicStoneData data) {
        int page = data.getLoadoutPage();
        int panelH = PANEL_H;

        // 左列：9 个槽（第一页第 1 个是右键位）
        for (int i = 0; i < MagicStoneData.SLOTS_PER_PAGE; i++) {
            final int global = page * MagicStoneData.SLOTS_PER_PAGE + i;
            boolean selected = global == bindSlot;
            String key = i == 0 ? "右键" : String.valueOf(i + 1);
            var bound = data.getSlot(global);
            String label = (selected ? "§e▶ " : "§7") + key + " §r" + boundName(bound, data);
            Button slot = Button.builder(Component.literal(label), b -> {
                        bindSlot = (bindSlot == global) ? -1 : global;
                        rebuildWidgets();
                    })
                    .bounds(left() + 12, top() + LIST_Y + i * BIND_SLOT_ROW_H, listX() - 24, 14)
                    .build();
            addRenderableWidget(slot);
        }

        // 切页（只改服务端的"当前页"，它会把新一页写进法杖）
        addRenderableWidget(Button.builder(Component.literal("切页 §7(默认数字键 1)"), b -> {
                    MagicStoneNetwork.requestNextPage();
                    bindSlot = -1;
                })
                .bounds(left() + 12, top() + PANEL_H - 24, listX() - 24, 16).build());

        // 右列：元素筛选（只列有链的元素）—— 最后补一个「独立」，否则乱魔没地方配 ✓
        int tabX = left() + listX();
        int tabWidth = Math.min(40, (panelWidth() - listX() - 12) / (Element.values().length + 1) - 4);
        for (Element element : Element.values()) {
            if (SpellCatalog.chainsOf(element).isEmpty()) {
                continue;
            }
            final Element target = element;
            Button tab = Button.builder(Component.literal(element == Element.DARK ? "暗" : element.cn()),
                            b -> { shownElement = target; bindScroll = 0; rebuildWidgets(); })
                    .bounds(tabX, top() + PANEL_H - 24, tabWidth, 16).build();
            tab.active = element != shownElement;
            addRenderableWidget(tab);
            tabX += tabWidth + 4;
        }
        Button independentTab = Button.builder(Component.literal("独立"),
                        b -> { shownElement = null; bindScroll = 0; rebuildWidgets(); })
                .bounds(tabX, top() + PANEL_H - 24, tabWidth, 16).build();
        independentTab.active = shownElement != null;
        addRenderableWidget(independentTab);

        // 右列：这个元素下**每条链当前拥有的最高档**（作者 2026-09-29 规则）。
        // 没学过的链不出现；链里学了更高档，列表里换的就是那一档 ✓。
        List<SpellCatalog.Entry> options = SpellCatalog.chainTopAssignable(data, shownElement);
        int colW = Math.max(80, (panelWidth() - listX() - 12) / bindColsPerView());
        int maxRows = bindVisibleRows();
        int perView = maxRows * bindColsPerView();
        bindRects.clear();
        for (int i = 0; i < options.size(); i++) {
            int visual = i - bindScroll * perView;
            if (visual < 0) {
                continue;                                    // 滚出上边
            }
            int col = visual / maxRows;
            int row = visual % maxRows;
            if (col >= bindColsPerView()) {
                break;                                       // 滚出下边（后面的等滚动再看）
            }
            SpellCatalog.Entry entry = options.get(i);
            final SpellCatalog.Entry target = entry;
            int colX = left() + listX() + col * colW;
            int rowY = top() + LIST_Y + 16 + row * BIND_ROW_H;
            boolean bound = data.isBound(entry.id());
            Button button = Button.builder(
                            Component.literal("    " + (bound ? "§7" : "§f") + fitLabel(entry.displayName(), colW - 34)),
                            b -> {
                                if (bindSlot < 0) {
                                    return;                   // 没选槽就什么都不做（提示画在表头）
                                }
                                MagicStoneNetwork.requestSetSlot(bindSlot, target.id());
                            })
                    .bounds(colX, rowY, colW - 8, 15)
                    .build();
            button.active = bindSlot >= 0 && !bound;
            addRenderableWidget(button);
            bindRects.put(entry, new int[]{colX, rowY, colW - 8, 15});
        }

        // 滚动提示（只在真的放不下时出现）
        if (bindScrollRows() > 1) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
                        bindScroll = Math.max(0, bindScroll - 1);
                        rebuildWidgets();
                    })
                    .bounds(left() + panelWidth() - 40, top() + LIST_Y - 16, 16, 12).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
                        bindScroll = Math.min(bindScrollRows() - 1, bindScroll + 1);
                        rebuildWidgets();
                    })
                    .bounds(left() + panelWidth() - 20, top() + LIST_Y - 16, 16, 12).build());
        }

        // 清空选中槽
        Button clear = Button.builder(Component.literal("清空选中的键"), b -> {
                    if (bindSlot >= 0) {
                        MagicStoneNetwork.requestClearSlot(bindSlot);
                    }
                })
                .bounds(left() + panelWidth() - 110, top() + PANEL_H - 24, 100, 16).build();
        clear.active = bindSlot >= 0;
        addRenderableWidget(clear);
    }

    /** 配键列表一行多高（比学习页的 ROW_H 矮一点，为了多塞几行）。 */
    private static final int BIND_ROW_H = 16;

    /** 配键页左列"一个键位"那一行多高（按钮 14 高 + 2 间隙）。 */
    private static final int BIND_SLOT_ROW_H = 16;

    /** 配键列表一屏能显示几行。 */
    private int bindVisibleRows() {
        return Math.max(1, (PANEL_H - 28 - LIST_Y - 16) / BIND_ROW_H);
    }

    /** 配键列表一屏能显示几列。 */
    private int bindColsPerView() {
        return 2;
    }

    /** 配键列表一共要滚几屏。 */
    private int bindScrollRows() {
        MagicStoneData data = clientData();
        if (data == null) {
            return 1;
        }
        int count = SpellCatalog.chainTopAssignable(data, shownElement).size();
        int perView = bindVisibleRows() * bindColsPerView();
        return Math.max(1, (count + perView - 1) / perView);
    }

    /** 槽里那个法术的短名（悬停有全名）。 */
    private static String boundName(net.minecraft.resources.ResourceLocation id, MagicStoneData data) {
        if (id == null) {
            return "§8（空）";
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(id);
        String name = entry != null ? entry.displayName() : id.getPath();
        // 忘了/引擎没实装的 → 明确标出来，别让玩家以为这个键坏了
        return data.hasLearned(id) ? name : "§c" + name + "（已遗忘）";
    }

    /**
     * 未学会的法术名字显示成乱码。
     *
     * <p>用<b>名字本身</b>推出乱码，所以同一个法术每次打开界面乱码都一样 ✓（不会闪 ✗）；
     * 学完之后立刻变回真名 ✓。
     */
    private static String garble(String name) {
        final String glyphs = "\u2593\u2592\u2591\u2588\u259a\u259e\u2573\u203B\u2261\u25A0";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            sb.append(glyphs.charAt(Math.floorMod(name.charAt(i) * 31 + i * 7, glyphs.length())));
        }
        return sb.toString();
    }

    /** 法术目录每列多宽（渲染和按钮布局必须用同一个算法，否则又会对不上）。 */
    private int columnWidth(int chainCount) {
        return (panelWidth() - listX() - 12) / Math.max(1, chainCount);
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

        // 面板外框：模仿法术按钮那种"多层金框"（按钮是整合包资源包给的斜面风格）
        // 由外到内四层：深棕 -> 亮金 -> 中金 -> 面板底色，看起来和按钮是一套的 ✓
        int bx0 = left - 4, by0 = top - 4, bx1 = left + panelW + 4, by1 = top + PANEL_H + 4;
        graphics.fill(bx0, by0, bx1, by1, 0xFF2A1B0E);                 // 最外层深棕描边
        graphics.fill(bx0 + 1, by0 + 1, bx1 - 1, by1 - 1, 0xFFF2D48A); // 亮金高光
        graphics.fill(bx0 + 2, by0 + 2, bx1 - 2, by1 - 2, 0xFFB98A46); // 中金
        graphics.fill(bx0 + 3, by0 + 3, bx1 - 3, by1 - 3, 0xFF6E4A22); // 内层暗金
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
        graphics.fill(left + 8, top + 54, left + panelWidth() - 8, top + 55, 0x50C9A063);
        graphics.fill(left + listX() - 8, top + 58, left + listX() - 7, top + PANEL_H - 8, 0x50C9A063);

        if (bindTab) {
            drawBindTab(graphics, data, mouseX, mouseY, left, top, panelW);
            return;
        }

        // 左列：元素亲和度 —— 用户画的水晶贴图 + 42 个亲和力点（点位由用户标注，见 AffinityWidget）
        // 用户要求：等比例放大到占满左下框约 80%，且**不要任何文字** ✗
        int affinityScale=Math.max(1,Math.min(3,(listX()-20)/33));
        AffinityWidget.render(graphics,left+(listX()-33*affinityScale)/2,top()+71,affinityScale,data::getAffinity);

        // 法术目录：**每条链一列**（雷系现在有主链/雷球/雷速三条，15 个法术挤一列会跑出面板）
        // 每行的按钮在 init() 里创建（名字就是按钮的标签），这里只画表头和悬停提示。
        List<SpellCatalog.Chain> chains = visibleChains();
        int colW = columnWidth(chains.size());
        String heading=shownElement==null?"独立魔法 · 无元素亲和限制":shownElement.cn()+"系法术 · 每链最高档";
        graphics.drawString(this.font,fitLabel(heading,panelW-listX()-64),left+listX(),top+LIST_Y-14,COLOR_TITLE,false);
        int chainIndex = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left + listX() + chainIndex * colW;
            graphics.drawString(this.font, chain.cn(), colX, top + LIST_Y, COLOR_TITLE, false);
            chainIndex++;
        }

        // 先画面板/文字/表头，再让 super.render 画那一排按钮
        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停提示必须放在**按钮之后**画：按钮是 widget，super.render 会盖住先画的东西
        int chainIndex2 = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left + listX() + chainIndex2 * colW;
            int spellY = top + LIST_Y + 16;
            for (SpellCatalog.Entry entry : SpellCatalog.of(shownElement, chain)) {
                // 法术图标画在按钮左边（super.render 之后画，否则被按钮盖住）
                if (data.hasLearned(entry.id()) || entry.independent() || entry.tier()<3) {
                    drawSpellIcon(graphics, entry, colX + 2, spellY + 1);
                }
                if (isHovering(colX, spellY, colW - 8, 16, mouseX, mouseY)) {
                    MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
                    // 消耗要按玩家自己的上限算（随上限等比放大），不然提示和实际扣费对不上
                    String tooltip=entry.fullName()
                                    + "\n§b效果 §r" + effectSummary(entry)
                                    + "\n§7解锁消耗 " + MagicStoneLearning.learningCost(data,entry) + " 点"
                                    + "\n§7施放消耗 " + entry.manaCostFor(data.getMaxMana()) + " 魔力"
                                    + (entry.independent()?"\n§d独立魔法 · 无亲和力要求": "\n§7亲和力要求 " + Element.tierName(entry.tier())
                                    + "（当前上限 " + Element.tierName(Math.max(1, data.maxTierFor(entry.element()))) + "）")
                                    + "\n§7前置 " + (entry.tier() <= 1 ? "无（链的第 1 级）"
                                            : "先学本链第 " + (entry.tier() - 1) + " 级")
                                    + "\n§7状态 " + stateText(state, entry);
                    var lines=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
                    for(String line:tooltip.split("\n"))
                        lines.addAll(this.font.split(Component.literal(line),Math.max(100,Math.min(300,this.width-24))));
                    graphics.renderTooltip(this.font,lines,mouseX,mouseY);
                }
                spellY += ROW_H;
            }
            chainIndex2++;
        }
    }

    /**
     * 配键页的绘制。
     *
     * <p>按钮（9 个槽 + 切页 + 元素筛选 + 法术列表）都在 {@link #initBindTab} 里建好，
     * 这里只画表头、页号说明和悬停提示 —— 提示必须画在 {@code super.render} <b>之后</b>，
     * 否则会被按钮盖住（学习页踩过这个坑）。
     */
    private void drawBindTab(GuiGraphics graphics, MagicStoneData data,
                             int mouseX, int mouseY, int left, int top, int panelW) {
        int page = data.getLoadoutPage();
        int bound = (int) data.pageSpellIds(page).stream().filter(java.util.Objects::nonNull).count();

        graphics.drawString(this.font, fitLabel("配键 · 第 " + (page + 1) + "/"
                        + MagicStoneData.PAGE_COUNT + " 页 · 本页 " + bound + "/"
                        + MagicStoneData.SLOTS_PER_PAGE + " 个", listX() - 26),
                left + 12, top + LIST_Y - 14, COLOR_TITLE, false);
        graphics.drawString(this.font,
                bindSlot < 0 ? "§7先点左边一个键" : "§e已选中，点右边的法术放上去",
                left + 12, top + PANEL_H - 38, bindSlot < 0 ? COLOR_DIM : 0xFFFFD479, false);

        String heading = shownElement == null ? "独立魔法"
                : "每条链的最高档 · " + shownElement.cn() + "系";
        if (bindScrollRows() > 1) {
            heading = heading + " §7(" + (bindScroll + 1) + "/" + bindScrollRows() + " 滚轮/▲▼)";
        }
        graphics.drawString(this.font, fitLabel(heading, panelW - listX() - 20),
                left + listX(), top + LIST_Y - 14, COLOR_TITLE, false);

        super.render(graphics, mouseX, mouseY, 0.0F);

        // 槽位行高必须和 drawBindTab 里的悬停判定用同一个常量（对不上的话提示会串行）
        for (int i = 0; i < MagicStoneData.SLOTS_PER_PAGE; i++) {
            int global = page * MagicStoneData.SLOTS_PER_PAGE + i;
            int slotY = top + LIST_Y + i * BIND_SLOT_ROW_H;
            if (!isHovering(left + 12, slotY, listX() - 24, 14, mouseX, mouseY)) {
                continue;
            }
            var boundSpell = data.getSlot(global);
            SpellCatalog.Entry entry = boundSpell == null ? null : SpellCatalog.byId(boundSpell);
            String tip = (i == 0 ? "右键位" : "数字键 " + (i + 1))
                    + "\n§7全局第 " + (global + 1) + " 个槽"
                    + (entry == null ? "\n§7当前：空" : "\n§r" + entry.fullName()
                    + "\n§7施放消耗 " + entry.manaCostFor(data.getMaxMana()) + " 魔力");
            renderLines(graphics, tip, mouseX, mouseY);
            return;
        }
        for (var hovered : bindRects.entrySet()) {
            int[] r = hovered.getValue();
            if (!isHovering(r[0], r[1], r[2], r[3], mouseX, mouseY)) {
                continue;
            }
            SpellCatalog.Entry entry = hovered.getKey();
            renderLines(graphics, entry.fullName()
                    + "\n§7施放消耗 " + entry.manaCostFor(data.getMaxMana()) + " 魔力"
                    + "\n§7" + (data.isBound(entry.id()) ? "已经配在某个键上（换个键要重新点）" : "点一下放进选中的键"),
                    mouseX, mouseY);
            return;
        }
    }

    /** 折行画一段带 § 颜色的多行提示。 */
    private void renderLines(GuiGraphics graphics, String text, int mouseX, int mouseY) {
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line : text.split("\n")) {
            lines.addAll(this.font.split(Component.literal(line), Math.max(100, Math.min(300, this.width - 24))));
        }
        graphics.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    /** 把法术图标画在按钮左边（16x16，贴图路径 = assets/tnc/textures/spell/<id>.png）。 */
    private static void drawSpellIcon(net.minecraft.client.gui.GuiGraphics graphics,
                                      SpellCatalog.Entry entry, int x, int y) {
        net.minecraft.resources.ResourceLocation icon = net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("tnc", "textures/spell/" + entry.id().getPath() + ".png");
        graphics.blit(icon, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /**
     * 效果摘要：由目录数据自动生成（不手写 105 条描述 ✗）。
     * 想要更文学化的描述，就得在目录里给每条法术加一个 desc 字段 —— 那是后话 ✓
     */
    private static String effectSummary(SpellCatalog.Entry entry) {
        if(entry.independent() || entry.element()==Element.WATER)
            return Component.translatable("spell.tnc."+entry.id().getPath()+".description").getString();
        return entry.element().cn() + "系 · " + entry.chain().cn() + "链 · 第 " + entry.tier() + " 级 · "
                + entry.chain().desc();
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

    /**
     * 鼠标滚轮滚配键列表。
     *
     * <p>只在"配键页 + 真的有好几屏"时才吃这个事件 —— 否则会把滚轮吞掉，
     * 而原版/别的界面可能还指望它（返回 true = 事件被消费）✗。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (bindTab && bindScrollRows() > 1 && delta != 0.0) {
            int next = Math.max(0, Math.min(bindScrollRows() - 1, bindScroll + (delta < 0 ? 1 : -1)));
            if (next != bindScroll) {
                bindScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
