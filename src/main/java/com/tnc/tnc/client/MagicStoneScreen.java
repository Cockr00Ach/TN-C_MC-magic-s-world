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

    private static final int PANEL_W = 680;   // Four water routes when the window is wide enough.
    private static final int PANEL_H = 360;
    private static final int ROW_H = 30;

    private static final int COLOR_PANEL = 0xE62A1F16;   // dark brown
    private static final int COLOR_BORDER = 0xFF8A6A42;  // brown-gold border
    private static final int COLOR_TITLE = 0xFFF2DFC4;    // warm cream
    private static final int COLOR_TEXT = 0xFFDCC9B0;      // light tan
    private static final int COLOR_DIM = 0xFF9A8570;        // muted brown
    private static final int COLOR_OK = 0xFF96CDBA;
    private static final int COLOR_BAD = 0xFFD79282;

    private static final int LIST_X = 200;
    private static final int LIST_Y = 112;

    /**
     * 只在第一次 init 时拉数据。
     * 否则会死循环：init → 请求同步 → 服务端回包 → 刷新界面（rebuildWidgets → init）→ 又请求同步 …
     */
    private boolean syncRequested;
    private float uiScale = 1;
    private List<net.minecraft.util.FormattedCharSequence> pendingTooltip;

    float uiScale() { return uiScale; }

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
     * <p>为什么用"屏"而不是"行"：列表按"每屏 N 行 × 3 列"翻，一次滚一屏
     * 才不会出现"滚了半行、两列错位"的观感 ✗。
     */
    private int bindScroll;
    private final SpellDragGesture<net.minecraft.resources.ResourceLocation> drag=new SpellDragGesture<>();
    private final java.util.Map<Integer,MagicStoneLoadoutLayout.Rect> slotRects=new java.util.LinkedHashMap<>();
    private String bindFeedback="按住法术拖入下方槽位 · 右键槽位清空";


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
        drag.cancel();bindRects.clear();slotRects.clear();
        // Always use the physical GUI viewport: server refreshes also call init().
        int actualW = minecraft.getWindow().getGuiScaledWidth();
        int actualH = minecraft.getWindow().getGuiScaledHeight();
        uiScale = Math.max(.2f, Math.min(1, Math.min((actualW - 16f) / 580, (actualH - 16f) / PANEL_H)));
        width = (int) Math.ceil(actualW / uiScale);
        height = (int) Math.ceil(actualH / uiScale);
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
        // Information, navigation and spell lists have separate vertical bands.
        int y = top() + 76;
        int tabW = Math.min(72, (listX() - 28) / 2);
        addRenderableWidget(Button.builder(Component.literal(bindTab ? "学习法术" : "§l学习法术"),
                        b -> { bindTab = false; rebuildWidgets(); })
                .bounds(left() + 12, y, tabW, 20).build());
        addRenderableWidget(Button.builder(Component.literal(bindTab ? "§l拖动配键" : "拖动配键"),
                        b -> { bindTab = true; bindSlot = -1; rebuildWidgets(); })
                .bounds(left() + 16 + tabW, y, tabW, 20).build());
    }

    /**
     * 学习页：每个法术一行按钮，<b>按链分列</b>排。
     *
     * <p>以前是"名字文本 + 右侧单独一个「解锁 N点」按钮"，所有按钮排在同一列往下叠：
     * 5 个法术时没问题，15 个法术（三条链）就会排到面板外面、跑到屏幕外点不到，
     * 而且那一列还压住了第三条链的名字。现在按钮就是整行（列宽），三个链并排。
     */
    private void initLearnTab(MagicStoneData data) {
        initElementTabs();
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
                // 名字一律显示真名 —— 早期"未学会的 t3+ 显示乱码方块"已按作者要求去掉 ✓
                String label = entry.displayName();
                Button button = Button.builder(Component.literal("    "+fitLabel(label, colW - 34)),
                                clicked -> MagicStoneNetwork.requestUnlock(target.id()))
                        .bounds(colX, rowY, colW - 8, 24)
                        .build();
                button.active = state == MagicStoneLearning.Result.OK;
                addRenderableWidget(button);
                rowY += ROW_H;
            }
            chainIndex++;
        }
    }

    /**
     * 配键页：上方法术库，下方九个配键槽；拖动松手后才提交服务端。
     *
     * <p>为什么要有这个页：引擎只给 9 个施法热键（右键 + 2~9），改不动 ✗ ——
     * 所以内容按页切（2 页 × 9），而"哪个键放哪个法术"由玩家自己排 ✓。
     * 一页装不下 18 个，装得下也要允许玩家为了手感重排。
     */
    private void initElementTabs(){
        int x=left()+listX();
        int w=(panelWidth()-listX()-12)/(Element.values().length+1)-4;
        for(var e:Element.values()){
            if(SpellCatalog.chainsOf(e).isEmpty())continue;
            var button=Button.builder(Component.literal(e==Element.DARK?"暗":e.cn()),b->{
                shownElement=e;chainPage=0;bindScroll=0;rebuildWidgets();
            }).bounds(x,top()+76,w,20).build();
            button.active=e!=shownElement;addRenderableWidget(button);x+=w+4;
        }
        var independent=Button.builder(Component.literal("独立"),b->{shownElement=null;chainPage=0;bindScroll=0;rebuildWidgets();})
                .bounds(x,top()+76,w,20).build();
        independent.active=shownElement!=null;addRenderableWidget(independent);
    }

    private void initBindTab(MagicStoneData data) {
        initElementTabs();
        int page=data.getLoadoutPage();
        for(int i=0;i<MagicStoneData.SLOTS_PER_PAGE;i++)
            slotRects.put(page*MagicStoneData.SLOTS_PER_PAGE+i,MagicStoneLoadoutLayout.slot(left(),top(),panelWidth(),PANEL_H,i));
        addRenderableWidget(Button.builder(Component.literal("切换配键页"),b->{
            drag.cancel();bindSlot=-1;MagicStoneNetwork.requestNextPage();
        }).bounds(left()+panelWidth()-94,top()+PANEL_H-102,82,18).build());
        var options=SpellCatalog.learnedAssignable(data,shownElement);
        bindScroll=Math.max(0,Math.min(bindScrollRows()-1,bindScroll));
        int rows=bindVisibleRows(),cols=bindColsPerView(),colW=(panelWidth()-24)/cols;
        for(int i=0;i<options.size();i++){
            int visual=i-bindScroll*rows*cols;
            if(visual<0)continue;
            if(visual>=rows*cols)break;
            var entry=options.get(i);int x=left()+12+(visual%cols)*colW,y=top()+LIST_Y+16+(visual/cols)*BIND_ROW_H;
            addRenderableWidget(Button.builder(Component.literal("    "+fitLabel(entry.displayName(),colW-40)),b->{})
                    .bounds(x,y,colW-8,24).build());
            bindRects.put(entry,new int[]{x,y,colW-8,24});
        }
        if(bindScrollRows()>1){
            addRenderableWidget(Button.builder(Component.literal("◀"),b->{bindScroll=Math.max(0,bindScroll-1);rebuildWidgets();})
                    .bounds(left()+panelWidth()-52,top()+LIST_Y-14,18,14).build());
            addRenderableWidget(Button.builder(Component.literal("▶"),b->{bindScroll=Math.min(bindScrollRows()-1,bindScroll+1);rebuildWidgets();})
                    .bounds(left()+panelWidth()-30,top()+LIST_Y-14,18,14).build());
        }
    }

    /** 配键列表一行多高（比学习页的 ROW_H 矮一点，为了多塞几行）。 */
    private static final int BIND_ROW_H = 28;

    /** 配键列表一屏能显示几行。 */
    private int bindVisibleRows() {
        return Math.max(1, (PANEL_H - 106 - LIST_Y - 16) / BIND_ROW_H);
    }

    /** 配键列表一屏能显示几列。 */
    private int bindColsPerView() {
        return 3;
    }

    /** 配键列表一共要滚几屏。 */
    private int bindScrollRows() {
        MagicStoneData data = clientData();
        if (data == null) {
            return 1;
        }
        int count = SpellCatalog.learnedAssignable(data, shownElement).size();
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
        pendingTooltip = null;
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        try {
            renderPanel(graphics, (int) (mouseX / uiScale), (int) (mouseY / uiScale), partialTick);
            if(drag.active()){
                pendingTooltip=null;
                if(drag.dragging()){
                    int x=(int)(mouseX/uiScale)+10,y=(int)(mouseY/uiScale)-8;
                    var entry=SpellCatalog.byId(drag.payload());
                    graphics.pose().pushPose();graphics.pose().translate(0,0,400);
                    graphics.fill(x-3,y-3,x+140,y+21,0xF0302920);
                    if(entry!=null){drawSpellIcon(graphics,entry,x,y);graphics.drawString(font,fitLabel(entry.displayName(),116),x+20,y+4,COLOR_TITLE,false);}
                    graphics.pose().popPose();
                }
            }
        } finally {
            graphics.pose().popPose();
        }
        // Tooltip placement and text stay in the actual viewport, outside the fitted panel pose.
        if (pendingTooltip != null) graphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = left();
        int top = top();
        int panelW = panelWidth();

        // 面板外框：模仿法术按钮那种"多层金框"（按钮是整合包资源包给的斜面风格）
        // 由外到内四层：深棕 -> 亮金 -> 中金 -> 面板底色，看起来和按钮是一套的 ✓
        com.tnc.tnc.client.ui.ArcaneGui.proportionalFrame(graphics,left-4,top-4,panelW+8,PANEL_H+8);
        graphics.drawCenteredString(this.font, this.title, left + panelW / 2, top + 28, COLOR_TITLE);

        MagicStoneData data = clientData();
        if (data == null) {
            graphics.drawCenteredString(this.font, "等待服务端数据…", left + panelW / 2, top + PANEL_H / 2, COLOR_BAD);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // 顶部：魔力 + 魔法点数
        graphics.drawString(this.font, "魔力  " + data.getMana() + " / " + data.getMaxMana(),
                left + 12, top + 46, COLOR_TEXT, false);
        graphics.drawString(this.font, fitLabel("魔法点数  " + data.getPointsAvailable(Config.pointThresholds) + " 可用"
                        + "（总 " + data.getPointsTotal(Config.pointThresholds)
                        + " · 已投 " + data.getPointsSpent() + "）", panelW - 24),
                left + 12, top + 59, COLOR_TEXT, false);
        graphics.fill(left + 8, top + 72, left + panelWidth() - 8, top + 73, 0x50C9A063);
        if(!bindTab)graphics.fill(left + listX() - 8, top + 102, left + listX() - 7, top + PANEL_H - 8, 0x50C9A063);

        if (bindTab) {
            drawBindTab(graphics, data, mouseX, mouseY, left, top, panelW);
            return;
        }

        // 左列：元素亲和度 —— 用户画的水晶贴图 + 42 个亲和力点（点位由用户标注，见 AffinityWidget）
        // 用户要求：等比例放大到占满左下框约 80%，且**不要任何文字** ✗
        int affinityScale=Math.max(1,Math.min(3,(listX()-20)/33));
        AffinityWidget.render(graphics,left+(listX()-33*affinityScale)/2,top()+128,affinityScale,data::getAffinity);

        // 法术目录：**每条链一列**（雷系现在有主链/雷球/雷速三条，15 个法术挤一列会跑出面板）
        // 每行的按钮在 init() 里创建（名字就是按钮的标签），这里只画表头和悬停提示。
        List<SpellCatalog.Chain> chains = visibleChains();
        int colW = columnWidth(chains.size());
        String heading=shownElement==null?"独立魔法 · 无元素亲和限制":shownElement.cn()+"系法术 · 按链进阶学习";
        graphics.drawString(this.font,fitLabel(heading,panelW-listX()-64),left+listX(),top+LIST_Y-10,COLOR_TITLE,false);
        int chainIndex = 0;
        for (SpellCatalog.Chain chain : chains) {
            int colX = left + listX() + chainIndex * colW;
            graphics.drawString(this.font, fitLabel(chain.cn(), colW - 8), colX, top + LIST_Y, COLOR_TITLE, false);
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
                // 一律画 —— 未学会的 t3+ 以前不画，看上去像"图标坏了"，作者要求统一显示 ✓
                drawSpellIcon(graphics, entry, colX + 4, spellY + 4);
                if (isHovering(colX, spellY, colW - 8, 24, mouseX, mouseY)) {
                    MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
                    // 消耗要按玩家自己的上限算（随上限等比放大），不然提示和实际扣费对不上
                    String tooltip=entry.fullName()
                                    + "\n§b效果 §r" + effectSummary(entry)
                                    + "\n§7解锁消耗 " + MagicStoneLearning.learningCost(data,entry) + " 点"
                                    + "\n§7施放消耗 " + entry.manaCostFor(data.getMaxMana()) + " 魔力"
                                    + (com.tnc.tnc.magic.domain.DomainSpells.isDomain(entry.id())
                                            // ★ 2026-11-08 领域法术：作者要求"告知玩家只能选择一个"
                                            ? "\n§d领域魔法 · 全部元素只能选一个"
                                              + "\n§7要求 " + com.tnc.tnc.magic.domain.DomainSpells
                                                    .nameOf(com.tnc.tnc.magic.domain.DomainSpells.elementOf(entry.id()))
                                                    + "亲和力 ≥ 1（不看等级）"
                                              + "\n§7当前 " + (data.specialElements().isEmpty() ? "还没有领域"
                                                    : "已选「" + com.tnc.tnc.magic.domain.DomainSpells
                                                      .nameOf(data.specialElements().get(0)) + "」")
                                            : entry.independent()?"\n§d独立魔法 · 无亲和力要求": "\n§7亲和力要求 " + Element.tierName(entry.tier())
                                    + "（当前上限 " + Element.tierName(Math.max(1, data.maxTierFor(entry.element()))) + "）")
                                    + "\n§7前置 " + (entry.tier() <= 1 ? "无（链的第 1 级）"
                                            : "先学本链第 " + (entry.tier() - 1) + " 级")
                                    + "\n§7状态 " + stateText(state, entry);
                    renderLines(graphics, tooltip, mouseX, mouseY);
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
        int page=data.getLoadoutPage();
        String element=shownElement==null?"独立魔法":(shownElement==Element.DARK?"暗":shownElement.cn())+"系法术";
        graphics.drawString(font,"已学法术 · "+element+" · 全部已学 · 各等级均可配键",left+12,top+LIST_Y-12,COLOR_TITLE,false);
        super.render(graphics,mouseX,mouseY,0);
        if(bindRects.isEmpty())graphics.drawCenteredString(font,"暂无可配置法术，请先到学习页解锁",left+panelW/2,top+LIST_Y+58,COLOR_DIM);
        for(var e:bindRects.entrySet()){
            int[] r=e.getValue();drawSpellIcon(graphics,e.getKey(),r[0]+4,r[1]+4);
            if(data.isBound(e.getKey().id()))graphics.fill(r[0]+r[2]-7,r[1]+3,r[0]+r[2]-3,r[1]+7,COLOR_OK);
            if(!drag.active()&&isHovering(r[0],r[1],r[2],r[3],mouseX,mouseY))
                renderLines(graphics,e.getKey().fullName()+"\n§7"+effectSummary(e.getKey())+"\n§7按住拖入下方槽位；已配置法术可拖动改位置",mouseX,mouseY);
        }
        graphics.drawString(font,"配键槽 · 第 "+(page+1)+" / "+MagicStoneData.PAGE_COUNT+" 页",left+12,top+PANEL_H-98,COLOR_TITLE,false);
        for(var cell:slotRects.entrySet()){
            int global=cell.getKey();var r=cell.getValue();var id=data.getSlot(global);var entry=id==null?null:SpellCatalog.byId(id);
            boolean over=r.contains(mouseX,mouseY);
            graphics.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),over&&drag.dragging()?0xFF385548:COLOR_PANEL);
            int border=over&&drag.dragging()?COLOR_OK:global==bindSlot?COLOR_TITLE:COLOR_BORDER;
            graphics.renderOutline(r.x(),r.y(),r.width(),r.height(),border);
            String key=global%MagicStoneData.SLOTS_PER_PAGE==0?"右键":String.valueOf(global%MagicStoneData.SLOTS_PER_PAGE+1);
            graphics.drawString(font,key,r.x()+4,r.y()+3,COLOR_DIM,false);
            if(entry!=null)drawSpellIcon(graphics,entry,r.x()+(r.width()-16)/2,r.y()+12);
            String name=entry==null?"拖入法术":entry.displayName();
            graphics.drawCenteredString(font,fitLabel(name,r.width()-6),r.x()+r.width()/2,r.y()+30,entry==null?COLOR_DIM:COLOR_TEXT);
            if(over&&!drag.active())renderLines(graphics,(entry==null?"空槽":entry.fullName())+"\n§7拖入法术配置或替换；右键清空\n§7已配置的法术也可直接拖动",mouseX,mouseY);
        }
        graphics.drawString(font,fitLabel(drag.dragging()?"松手放入高亮槽位 · 放在槽外取消":bindFeedback,panelW-24),left+12,top+PANEL_H-24,COLOR_DIM,false);
    }

    /** 折行画一段带 § 颜色的多行提示。 */
    private void renderLines(GuiGraphics graphics, String text, int mouseX, int mouseY) {
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (String line : text.split("\n")) {
            lines.addAll(this.font.split(Component.literal(line), Math.max(100, Math.min(300, minecraft.getWindow().getGuiScaledWidth() - 24))));
        }
        pendingTooltip = lines;
    }

    /** 把法术图标画在按钮左边（16x16，贴图路径 = assets/tnc/textures/spell/<id>.png）。 */
    private static void drawSpellIcon(net.minecraft.client.gui.GuiGraphics graphics,
                                      SpellCatalog.Entry entry, int x, int y) {
        net.minecraft.resources.ResourceLocation icon = net.minecraft.resources.ResourceLocation
                .fromNamespaceAndPath("tnc", "textures/spell/" + entry.id().getPath() + ".png");
        // Full normalized UVs support both existing 16px and 32px artwork.
        graphics.blit(icon, x, y, 16, 16, 0.0F, 0.0F, 1, 1, 1, 1);
    }

    /**
     * 效果摘要：优先用手写介绍（lang 里的 {@code spell.tnc.<id>.description}），
     * 没有手写介绍的档才退回目录数据自动生成（不手写 105 条描述 ✗）。
     *
     * <p>⚠️ 判定"有没有手写介绍"靠<b>缺键回显</b>：{@code I18n} 查不到键时返回键名本身，
     * 所以 `译文 == 键` 就说明这条没写。以前只给 {@code independent()}/水链查译文 ⇒
     * 火系（射线链、火球链）永远走不到手写介绍，是这次补介绍时一并修的 ✓
     */
    private static String effectSummary(SpellCatalog.Entry entry) {
        String key = "spell.tnc." + entry.id().getPath() + ".description";
        String written = Component.translatable(key).getString();
        if (!written.equals(key)) return written;
        // ★ 2026-11-08 作者报「学领域就报错了」：独立/领域法术的 element 是 null
        //   （它们只出现在「独立」页、不进七元素 NBT 数组），以前这里直接 entry.element().cn()
        //   ⇒ 鼠标一划过领域法术就 NPE 崩客户端 ✗。现在按"有没有元素"分三支 ✓
        String own;
        if (entry.element() != null) {
            own = entry.element().cn() + "系";
        } else if (com.tnc.tnc.magic.domain.DomainSpells.isDomain(entry.id())) {
            own = com.tnc.tnc.magic.domain.DomainSpells.nameOf(
                    com.tnc.tnc.magic.domain.DomainSpells.elementOf(entry.id()));
        } else {
            own = "独立魔法";
        }
        return own + " · " + entry.chain().cn() + "链 · 第 " + entry.tier() + " 级 · "
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

    private int dropSlot(double x,double y){
        for(var r:slotRects.entrySet())if(r.getValue().contains(x,y))return r.getKey();
        return -1;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        double px=x/uiScale,py=y/uiScale;
        if(bindTab){
            var data=clientData();int slot=dropSlot(px,py);
            if(data!=null&&slot>=0){
                bindSlot=slot;
                if(button==1){drag.cancel();MagicStoneNetwork.requestClearSlot(slot);bindFeedback="已请求清空槽位";return true;}
                if(button==0){var id=data.getSlot(slot);if(id!=null)drag.begin(id,px,py);return true;}
            }
            if(button==0)for(var card:bindRects.entrySet()){
                int[] r=card.getValue();if(isHovering(r[0],r[1],r[2],r[3],(int)px,(int)py)){
                    drag.begin(card.getKey().id(),px,py);return true;
                }
            }
        }
        return super.mouseClicked(px,py,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==0&&drag.active()){
            double px=x/uiScale,py=y/uiScale;drag.move(px,py);int slot=dropSlot(px,py);
            var spell=drag.finish(slot>=0);var data=clientData();
            if(spell!=null&&data!=null&&data.hasLearned(spell)){
                boolean occupied=data.getSlot(slot)!=null;bindSlot=slot;
                MagicStoneNetwork.requestSetSlot(slot,spell);
                bindFeedback=occupied?"已请求替换槽位法术":"已请求配置法术";
            }else bindFeedback="已取消拖动，原配键保留";
            return true;
        }
        return super.mouseReleased(x/uiScale,y/uiScale,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&drag.active()){drag.move(x/uiScale,y/uiScale);return true;}
        return super.mouseDragged(x/uiScale,y/uiScale,button,dx/uiScale,dy/uiScale);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256&&drag.active()){drag.cancel();bindFeedback="已取消拖动，原配键保留";return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void tick(){super.tick();if(!minecraft.isWindowActive())drag.cancel();}
    @Override public void removed(){drag.cancel();super.removed();}
    @Override public void mouseMoved(double x, double y) {
        super.mouseMoved(x / uiScale, y / uiScale);
    }

    /**
     * 鼠标滚轮滚配键列表。
     *
     * <p>只在"配键页 + 真的有好几屏"时才吃这个事件 —— 否则会把滚轮吞掉，
     * 而原版/别的界面可能还指望它（返回 true = 事件被消费）✗。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (bindTab && !drag.active() && bindScrollRows() > 1 && delta != 0.0) {
            int next = Math.max(0, Math.min(bindScrollRows() - 1, bindScroll + (delta < 0 ? 1 : -1)));
            if (next != bindScroll) {
                bindScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX / uiScale, mouseY / uiScale, delta);
    }
}
