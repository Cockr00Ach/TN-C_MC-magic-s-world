package com.tnc.tnc.warrior.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.warrior.InstructorCourses;
import com.tnc.tnc.warrior.WarriorCodex;
import com.tnc.tnc.warrior.WarriorCodex.Entry;
import com.tnc.tnc.warrior.WarriorCodex.Progress;
import com.tnc.tnc.warrior.WarriorCodex.State;
import com.tnc.tnc.warrior.WarriorCodex.Weapon;
import com.tnc.tnc.warrior.WarriorCombos;
import com.tnc.tnc.warrior.WarriorSkill;
import com.tnc.tnc.warrior.WarriorUnifiedSlots;
import com.tnc.tnc.warrior.WarriorTalentRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 战士 · 武器图鉴：和魔法石同一套界面语言（底部页签切换分类、左栏说明、右栏条目）。
 *
 * <p>差别只在"怎么开"：魔法石按元素分，这里按<b>武器</b>分（剑/盾/斧/长柄/弓/风刃）；
 * 魔法石花魔法点数解锁，这里<b>一个点数都不花</b>——每个条目写清"要做到什么"
 * （磨砺到线、训练完成几次、评分够高、盾牌试炼毕业），条件满足就是开放。
 * 判定全在 {@link WarriorCodex} 里，和服务端用同一段代码，所以界面上的"已开放"
 * 与真实能不能用不会打架。
 *
 * <p>数据不需要新协议：读的是 {@link TemperingClient} 早已同步的那份镜像。
 */
public final class WarriorCodexScreen extends Screen {

    private static final int PANEL_W = 600;
    private static final int PANEL_H = 288;
    private static final int ROW_H = 24;

    private static final int COLOR_TITLE = 0xFFF2DFC4;
    private static final int COLOR_TEXT = 0xFFDCC9B0;
    private static final int COLOR_DIM = 0xFF9A8570;
    private static final int COLOR_MET = 0xFF9FE0B8;
    private static final int COLOR_ONGOING = 0xFFE8C68A;
    private static final int COLOR_PLANNED = 0xFF8B8378;

    private static final int LIST_X = 186;
    private static final int LIST_Y = 108;

    private float uiScale = 1;
    /** 当前选中的武器线（底部页签）。 */
    private Weapon shown = Weapon.SWORD;
    /** 左栏正在详细展示的条目 id。 */
    private String selectedId;
    private boolean showGuide = true;
    /**
     * 每个条目按钮的屏幕矩形。
     *
     * <p>和魔法石一样：只在 {@link #init} 里排一次版并记下来，渲染时直接查表画悬停提示。
     * 重算就是第二份排版算法，迟早和按钮错位。
     */
    private final Map<String, int[]> rowRects = new HashMap<>();
    private List<Component> pendingTooltip;

    public WarriorCodexScreen() {
        super(Component.literal("战士系统"));
    }

    // ------------------------------------------------------------------
    //  布局
    // ------------------------------------------------------------------

    private int left() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelWidth() {
        return Math.min(PANEL_W, Math.max(260, this.width - 16));
    }

    private int top() {
        return (this.height - PANEL_H) / 2;
    }

    /** 面板矩形，供渲染审计断言"没有控件被排到面板外"。 */
    int panelLeft() {
        return left();
    }

    int panelTop() {
        return top();
    }

    int panelWidthNow() {
        return panelWidth();
    }

    static int panelHeight() {
        return PANEL_H;
    }

    @Override
    protected void init() {
        // 和魔法石同一套自适应：小窗口里整体缩放，而不是把右半边裁掉
        int actualW = minecraft.getWindow().getGuiScaledWidth();
        int actualH = minecraft.getWindow().getGuiScaledHeight();
        uiScale = Math.max(.2f, Math.min(1, Math.min((actualW - 16f) / 320, (actualH - 16f) / PANEL_H)));
        width = (int) Math.ceil(actualW / uiScale);
        height = (int) Math.ceil(actualH / uiScale);

        rowRects.clear();
        addRenderableWidget(Button.builder(Component.literal(showGuide?"查看招式":"操作说明"), b -> {
            showGuide=!showGuide;rebuildWidgets();
        }).bounds(left()+panelWidth()-88,top()+8,76,16).build());
        if(showGuide)return;

        int tabW = Math.max(28, Math.min(76, (panelWidth() - 24) / Weapon.values().length - 4));
        int x = left() + 12;
        for (Weapon weapon : Weapon.values()) {
            final Weapon target = weapon;
            Button tab = Button.builder(Component.literal(tabLabel(weapon)), b -> {
                shown = target;
                selectedId = null;
                rebuildWidgets();
            }).bounds(x, top() + PANEL_H - 24, tabW, 16).build();
            tab.active = weapon != shown;
            addRenderableWidget(tab);
            x += tabW + 4;
        }

        List<String> chains = WarriorCodex.chainsOf(shown);
        if (selectedId == null || !belongsToShown(selectedId)) {
            List<Entry> first = WarriorCodex.of(shown);
            selectedId = first.isEmpty() ? null : first.get(0).id();
        }
        if (chains.isEmpty()) {
            return;
        }
        int colW = Math.max(60, (panelWidth() - LIST_X - 12) / chains.size());
        int chainIndex = 0;
        for (String chain : chains) {
            int colX = left() + LIST_X + chainIndex * colW;
            int rowY = top() + LIST_Y + 16;
            for (Entry entry : WarriorCodex.of(shown, chain)) {
                final String id = entry.id();
                Button row = Button.builder(Component.literal(fitLabel(rowLabel(entry), Math.max(34,colW-34))), b -> {
                    selectedId = id;
                    rebuildWidgets();
                }).bounds(colX + 18, rowY, Math.max(40, colW - 26), 20).build();
                addRenderableWidget(row);
                rowRects.put(entry.id(), new int[]{colX, rowY, colW - 8});
                rowY += ROW_H;
            }
            chainIndex++;
        }
    }

    private boolean belongsToShown(String id) {
        Entry entry = WarriorCodex.byId(id);
        return entry != null && entry.weapon() == shown;
    }

    private String tabLabel(Weapon weapon) {
        int[] counts = WarriorCodex.metOfImplemented(weapon, progress());
        String base = (weapon == shown ? "§l" : "") + weapon.cn();
        return counts[1] == 0 ? base : base + " " + counts[0] + "/" + counts[1];
    }

    private String rowLabel(Entry entry) {
        String prefix = entry.id().equals(selectedId) ? "▶ " : "";
        // 连招条目把顺序直接摆在行上：玩家一眼就能看到"这招要按哪几下"
        WarriorSkill combo = WarriorCombos.byCodexId(entry.id());
        String label = combo == null ? entryName(entry) : WarriorUnifiedSlots.purpose(combo.slot())+" · "+combo.compactLabel();
        return prefix + label + (entry.implemented() ? "" : " · 规划");
    }

    private String entryName(Entry entry) {
        var base=WarriorCombos.byCodexId(entry.id());
        var move=WarriorComboClient.heldVariant(base);
        return move==null || move==base?entry.name():move.name();
    }

    private static Progress progress() {
        return TemperingClient.progress();
    }

    /** 服务端推来新快照时调用：重排控件但不换掉玩家正在看的页签。 */
    public void refreshFromServer() {
        rebuildWidgets();
    }

    // ------------------------------------------------------------------
    //  渲染
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        pendingTooltip = null;
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1);
        try {
            renderPanel(graphics, (int) (mouseX / uiScale), (int) (mouseY / uiScale), partialTick);
        } finally {
            graphics.pose().popPose();
        }
        // 提示画在缩放之外：跟随真实光标，不会被 uiScale 拉偏
        if (pendingTooltip != null) {
            graphics.renderComponentTooltip(this.font, pendingTooltip, mouseX, mouseY);
        }
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = left();
        int top = top();
        int panelW = panelWidth();
        Progress p = progress();

        ArcaneGui.proportionalFrame(g, left - 4, top - 4, panelW + 8, PANEL_H + 8);
        g.drawCenteredString(this.font, this.title, left + panelW / 2, top + 28, COLOR_TITLE);

        String head = p.enrolled()
            ? "天赋 " + p.talent() + " / 6 · 成长 " + WarriorTalentRules.multiplier(p.talent()) + " 倍"
              + " · " + p.rankName() + " · 磨砺 " + p.points() + " / " + p.cap()
            : "此角色尚无战士天赋：磨砺不会积累，图鉴里的条件无法推进";
        g.drawString(this.font, fitLabel(head, panelW - 24), left + 12, top + 46,
            p.enrolled() ? COLOR_TEXT : COLOR_ONGOING, false);
        String sub = "已点亮 " + WarriorCodex.metCount(p) + " / " + WarriorCodex.implementedCount()
            + " 项（不含规划）· 减伤 " + String.format(java.util.Locale.ROOT, "%.1f%%", p.reduction() * 100)
            + " · 按 " + WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString() + " 关闭";
        g.drawString(this.font, fitLabel(sub, panelW - 24), left + 12, top + 59, COLOR_DIM, false);
        g.fill(left + 8, top + 72, left + panelW - 8, top + 73, 0x50C9A063);
        if(!showGuide)g.fill(left + LIST_X - 8, top + 96, left + LIST_X - 7, top + PANEL_H - 8, 0x50C9A063);

        if(showGuide){
            drawGuide(g,left,top,panelW);
            super.render(g,mouseX,mouseY,partialTick);
            return;
        }
        drawDetail(g, p, left, top);

        List<String> chains = WarriorCodex.chainsOf(shown);
        int colW = Math.max(60, (panelW - LIST_X - 12) / Math.max(1, chains.size()));
        g.drawString(this.font, fitLabel(shown.cn() + "系招式 · 条件满足即开放，不消耗磨砺", panelW - LIST_X - 16),
            left + LIST_X, top + LIST_Y - 12, COLOR_TITLE, false);
        int chainIndex = 0;
        for (String chain : chains) {
            g.drawString(this.font, fitLabel(chain, colW - 8), left + LIST_X + chainIndex * colW, top + LIST_Y,
                COLOR_TITLE, false);
            chainIndex++;
        }

        super.render(g, mouseX, mouseY, partialTick);

        // 图标和悬停提示必须画在按钮之后，否则被 widget 盖住（魔法石学习页踩过这个坑）
        for (Map.Entry<String, int[]> row : rowRects.entrySet()) {
            Entry entry = WarriorCodex.byId(row.getKey());
            if (entry == null) continue;
            int[] rect = row.getValue();
            int iconX = rect[0] + 2;
            int iconY = rect[1] + 2;
            g.renderItem(icon(entry), iconX, iconY);
            State state = entry.state(p);
            if (state != State.MET) {
                g.fill(iconX, iconY, iconX + 16, iconY + 16, state == State.PLANNED ? 0xC0101518 : 0x70101518);
            }
            if (hovering(rect, mouseX, mouseY)) {
                pendingTooltip = tooltip(entry, p);
            }
        }
    }

    /** Short lines keep the introduction readable even at the minimum panel width. */
    private void drawGuide(GuiGraphics g,int left,int top,int panelW){
        String[] lines={
            "统一槽位：主攻 → 振刀 → 破振刀",
            "随后依次：连招1 → 连招2 → … → 连招n",
            "主攻：ZZZ，也可连续三次左键；QZE 仍可用",
            "振刀：E Q 后长按 E，架住攻击；松手收架",
            "破振刀：E Q Z，命中破开架势并打断",
            "连招1起：查看各武器条目中的输入与效果",
            "短按 Q 左斩、短按 E 右斩、Z／左键横斩",
            "长按 Q 半秒进入蓄力，松手攻击；受击中断",
            "近战蓄力逐渐放大，最大八倍，收招后缩回",
            "剑竖起重劈；刀横断；枪突刺；锤震地",
            "左 Ctrl：中断动作／战斗步法",
            "磨砺与训练满足条件后开放，不消耗点数",
            "演武场中央终端：选教官、练习、测试木桩",
            "点击右上角查看招式，切换武器页签看详情"
        };
        for(int i=0;i<lines.length;i++)
            g.drawString(font,fitLabel(lines[i],panelW-24),left+12,top+84+i*12,i<2?COLOR_TITLE:COLOR_TEXT,false);
    }

    private void drawDetail(GuiGraphics g, Progress p, int left, int top) {
        int x = left + 12;
        int width = LIST_X - 24;
        Entry entry = selectedId == null ? null : WarriorCodex.byId(selectedId);
        if (entry == null) {
            g.drawString(this.font, "选一个条目看条件", x, top + 90, COLOR_DIM, false);
            return;
        }
        State state = entry.state(p);
        g.drawString(this.font, fitLabel((WarriorCombos.byCodexId(entry.id())==null?"":WarriorUnifiedSlots.purpose(WarriorCombos.byCodexId(entry.id()).slot())+" · ")+entryName(entry), width), x, top + 84, stateColor(state), false);
        g.drawString(this.font, fitLabel(entry.title(), width), x, top + 96, COLOR_DIM, false);

        String stateText = switch (state) {
            case MET -> "已开放";
            case ONGOING -> "进行中 " + entry.percent(p) + "%";
            case PLANNED -> "规划中 · 尚未实装";
        };
        g.drawString(this.font, fitLabel(stateText, width), x, top + 110, stateColor(state), false);

        g.fill(x, top + 122, x + width, top + 127, 0xFF2B3033);
        int filled = (int) (width * (entry.percent(p) / 100.0));
        if (filled > 0) g.fill(x, top + 122, x + filled, top + 127, state == State.PLANNED ? 0xFF6B6459 : 0xFF87BFAC);

        int y = top + 136;
        // 左栏只能用到页签上方：页签是横跨整块面板底部的，详情再往下就会压在页签上。
        // 预算按 3 行效果 + 2 行来源 + 3 条条件留，超出就截断（宁可省略号，也不要盖住页签）。
        int bottom = top + PANEL_H - 32;
        g.drawString(this.font, "效果", x, y, 0xFFE8C68A, false);
        y += 11;
        for (String line : wrap(entry.effect(), width, 3)) {
            if (y > bottom) return;
            g.drawString(this.font, line, x, y, COLOR_TEXT, false);
            y += 10;
        }
        y += 2;
        if (y + 11 <= bottom) {
            g.drawString(this.font, "怎么练", x, y, 0xFFE8C68A, false);
            y += 11;
            for (String line : wrap(entry.source(), width, 2)) {
                if (y > bottom) return;
                g.drawString(this.font, line, x, y, COLOR_DIM, false);
                y += 10;
            }
            y += 2;
        }
        if (y + 11 > bottom) return;
        g.drawString(this.font, "条件", x, y, 0xFFE8C68A, false);
        y += 11;
        if (entry.gates().isEmpty()) {
            g.drawString(this.font, "无", x, y, COLOR_TEXT, false);
            return;
        }
        for (WarriorCodex.Clause clause : entry.gates()) {
            if (y > bottom) return;
            boolean ok = clause.met(p);
            String line = (ok ? "§a✓ " : "§c· ") + clause.text(p);
            g.drawString(this.font, fitLabel(line, width), x, y, ok ? COLOR_MET : COLOR_ONGOING, false);
            y += 10;
        }
    }

    private static int stateColor(State state) {
        return switch (state) {
            case MET -> COLOR_MET;
            case ONGOING -> COLOR_ONGOING;
            case PLANNED -> COLOR_PLANNED;
        };
    }

    private static ItemStack icon(Entry entry) {
        // 用战士自己的武器当页签/条目图标：原版铁剑配上一整套自研武器会显得"图鉴在讲别的游戏"。
        // 重武器那页用重剑代表（斧/重剑/战锤共用一条线），长柄用破天戟，弓用长弓。
        return switch (entry.weapon()) {
            case SWORD -> new ItemStack(com.tnc.tnc.TNMod.DRAGON_SWORD.get());
            case SHIELD -> new ItemStack(Items.SHIELD);
            case AXE -> new ItemStack(com.tnc.tnc.TNMod.GREATSWORD.get());
            case POLEARM -> new ItemStack(com.tnc.tnc.TNMod.SKY_HALBERD.get());
            case BOW -> new ItemStack(com.tnc.tnc.TNMod.BOW.get());
            case WIND -> new ItemStack(Items.FEATHER);
        };
    }

    private List<Component> tooltip(Entry entry, Progress p) {
        List<Component> lines = new ArrayList<>();
        State state = entry.state(p);
        lines.add(Component.literal(entryName(entry) + "（" + entry.title() + "）"));
        lines.add(Component.literal(switch (state) {
            case MET -> "§a已开放";
            case ONGOING -> "§e进行中 " + entry.percent(p) + "%";
            case PLANNED -> "§7规划中 · 尚未实装";
        }));
        lines.add(Component.literal("§7" + entry.effect()));
        WarriorSkill combo = WarriorCombos.byCodexId(entry.id());
        if (combo != null) {
            combo=WarriorComboClient.heldVariant(combo);
            lines.add(Component.literal("§7"+combo.finisherText()));
            lines.add(Component.literal("§b槽位 " + (combo.slot() + 1) + " / " + WarriorSkill.LOADOUT_SLOTS
                    + " · " + WarriorUnifiedSlots.purpose(combo.slot()) + " · 输入 " + combo.sequenceLabel()
                    + " · 冷却 " + combo.cooldownSeconds() + " 秒"));
            if (combo.displaces()) {
                lines.add(Component.literal("§7带位移 " + String.format(java.util.Locale.ROOT, "%.1f", combo.dash())
                        + " 格；动作：" + combo.animation().cn()));
            } else if (combo.backsteps()) {
                lines.add(Component.literal("§7后撤 " + String.format(java.util.Locale.ROOT, "%.1f", combo.backstep())
                        + " 格，无敌 " + combo.evadeTicks() / 20.0 + " 秒；动作：" + combo.animation().cn()));
            } else if (combo.interrupt() || combo.staggerTicks() > 0) {
                lines.add(Component.literal("§7命中打断施法/连招并僵直 " + combo.staggerTicks() / 20.0
                        + " 秒；动作：" + combo.animation().cn()));
            } else {
                lines.add(Component.literal("§7动作：" + combo.animation().cn()));
            }
            lines.add(Component.literal("§7持武器按顺序输入，连招成立后自动衔接；不消耗磨砺"));
        }
        if (!entry.source().isEmpty()) {
            lines.add(Component.literal("§7怎么练 " + entry.source()));
        }
        // "这条找谁练"是图鉴里最常被问的一句：写清楚是哪个教官、练到第几课。
        String instructor = instructor(entry.role());
        if (!instructor.isEmpty()) {
            String brief = TemperingClient.courseBrief(entry.role());
            String progress = brief.isEmpty() ? ""
                : " · " + (TemperingClient.courseComplete(entry.role()) ? "已出师 " : "出师 ") + brief;
            String now = entry.role().equals(TemperingClient.activeRole()) ? TemperingClient.courseNow() : "";
            // 一位教官手下好几节课，光写名字玩家还得回岛上猜：把"哪一课教这招"也写出来。
            var teaches = combo == null ? null : InstructorCourses.teaching(combo.id());
            lines.add(Component.literal("§b教官 " + instructor + progress
                + (teaches == null ? "" : " · 教的课 " + teaches.title())
                + (now.isEmpty() ? "" : " · 正在上 " + now)));
        }
        for (WarriorCodex.Clause clause : entry.gates()) {
            boolean ok = clause.met(p);
            lines.add(Component.literal((ok ? "§a✓ " : "§c· ") + clause.text(p)));
        }
        if (!entry.gates().isEmpty()) {
            lines.add(Component.literal("§8条件靠训练与历练推进，不消耗磨砺点数"));
        }
        return lines;
    }

    /**
     * 图鉴里的「这条找谁练」。只认四位教官；没挂教官的条目（纯武器类）返回空串。
     *
     * <p>写法与终端、教官头顶保持一致：{@code 莉娅 · 剑术}，玩家不会以为是两个人。
     */
    private static String instructor(String role) {
        return switch (role == null ? "" : role) {
            case "counter" -> "陪练";
            case "guard" -> "洛克 · 盾卫";
            case "blade" -> "莉娅 · 剑术";
            case "wind" -> "岚 · 风刃";
            default -> "";
        };
    }

    private boolean hovering(int[] rect, int mouseX, int mouseY) {
        return mouseX >= rect[0] && mouseX < rect[0] + rect[2] && mouseY >= rect[1] && mouseY < rect[1] + 20;
    }

    // ------------------------------------------------------------------
    //  小工具
    // ------------------------------------------------------------------

    /** 单行截断：超宽就用省略号，绝不画到面板外面。 */
    private String fitLabel(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && this.font.width(cut + "…") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    /**
     * 按像素宽度逐字换行（中文没有空格，靠 {@code font.split} 那套按空格切会整段溢出）。
     */
    private List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (line.length() > 0 && this.font.width(line.toString() + c) > maxWidth) {
                if (lines.size() + 1 >= maxLines) {
                    lines.add(fitLabel(line.toString() + text.substring(i), maxWidth));
                    return lines;
                }
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(c);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    // ------------------------------------------------------------------
    //  输入
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x / uiScale, y / uiScale, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / uiScale, y / uiScale, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / uiScale, y / uiScale, button, dx / uiScale, dy / uiScale);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x / uiScale, y / uiScale);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (WarriorPracticeKeys.OPEN.matches(key, scan) || key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
