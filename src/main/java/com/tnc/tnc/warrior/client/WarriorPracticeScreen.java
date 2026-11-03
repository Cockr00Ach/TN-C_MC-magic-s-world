package com.tnc.tnc.warrior.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.warrior.*;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「教官终端」面板：<b>只做三件事</b>——选教官、调难度、开始/结束练习。
 *
 * <p>成长、磨砺、招式条件这些「看」的东西全部搬到武器图鉴（{@link WarriorCodexScreen}，
 * 按 K 打开）。以前这里塞了磨砺/风刃/守则一大段文字，结果是"想点开始还得先在公告里找按钮"；
 * 现在终端只留能点的东西，玩家走到演武场中央右键，看到的就直接是四位教官。
 *
 * <p>面板本身不发奖励、不算进度，所有判定都在服务端。
 */
public final class WarriorPracticeScreen extends Screen {
    private static final int W = 360, H = 196, INK = 0xffe6dccb, MUTED = 0xffb9b1a3;
    private static final String[] ROLE_LABELS = {"陪练", "洛克", "莉娅", "岚"};
    /** 面板上给教官用的称呼：与图鉴里"洛克 · 盾卫"的说法保持一致。 */
    private static final String[] ROLE_TITLES = {"陪练", "盾卫", "剑术", "风刃"};
    private int left, top;
    private float fit = 1;
    private Button start, end, difficulty;
    private final Button[] roles = new Button[ROLE_LABELS.length];

    public WarriorPracticeScreen() {
        super(Component.literal("教官终端"));
    }

    @Override
    protected void init() {
        // 每次都从**窗口的真实尺寸**算，不能拿 this.width 再除一遍：
        // 服务端推新快照时会 rebuildWidgets() → init() 重进这里，用 this.width 会一次比一次缩。
        int actualW = minecraft.getWindow().getGuiScaledWidth();
        int actualH = minecraft.getWindow().getGuiScaledHeight();
        fit = Math.max(.25f, Math.min(1, Math.min((actualW - 16f) / (W + 4), (actualH - 16f) / (H + 4))));
        width = (int) Math.ceil(actualW / fit);
        height = (int) Math.ceil(actualH / fit);
        left = (width - W) / 2;
        top = (height - H) / 2;
        for (int i = 0; i < roles.length; i++) {
            final int index = i;
            roles[i] = addRenderableWidget(Button.builder(Component.literal(roleLabel(i)), b -> {
                TemperingClient.selectRole(InstructorEntity.VARIANTS[index]);
                refresh();
            }).bounds(left + 18 + i * 82, top + 52, 78, 20).build());
        }
        start = addRenderableWidget(Button.builder(Component.literal("开始练习"), b -> {
            TemperingActionPacket.send(TemperingActionPacket.Action.SUMMON, TemperingClient.selectedRole());
            minecraft.setScreen(null);
        }).bounds(left + 18, top + 152, 100, 20).build());
        end = addRenderableWidget(Button.builder(Component.literal("结束练习"), b ->
            TemperingActionPacket.send(TemperingActionPacket.Action.DISMISS))
            .bounds(left + 130, top + 152, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
            .bounds(left + 242, top + 152, 100, 20).build());
        difficulty = addRenderableWidget(Button.builder(Component.literal(difficultyLabel()), b ->
            TemperingActionPacket.send(TemperingActionPacket.Action.CYCLE_PRACTICE))
            .bounds(left + 18, top + 80, 202, 20).build());
        var dummy=addRenderableWidget(Button.builder(Component.literal("测试木桩"), b -> {
            TemperingActionPacket.send(TemperingActionPacket.Action.TEST_DUMMY);minecraft.setScreen(null);
        }).bounds(left+226,top+80,116,20).build());
        dummy.active=TemperingClient.ground()&&TemperingClient.enrolled();
        refresh();
    }

    /** 面板矩形，供渲染审计断言"没有控件被排到面板外"。 */
    int panelLeft() {
        return left;
    }

    int panelTop() {
        return top;
    }

    static int panelWidth() {
        return W;
    }

    static int panelHeight() {
        return H;
    }

    private static String roleLabel(int index) {
        String role = InstructorEntity.VARIANTS[index];
        String prefix = role.equals(TemperingClient.selectedRole()) ? "▶ " : "";
        String suffix = role.equals(TemperingClient.activeRole()) ? " · 在场" : "";
        return prefix + ROLE_LABELS[index] + suffix;
    }

    private static String difficultyLabel() {
        return "陪练难度 · " + PracticeDifficulty.name(TemperingClient.selectedMode())
            + " · " + (TemperingClient.ground() ? TemperingClient.training() ? "训练中锁定" : "点击切换" : "进场后可切换");
    }

    private void refresh() {
        boolean canStart = TemperingClient.enrolled() && TemperingClient.ground()
            && TemperingClient.activeRole().isEmpty();
        boolean canEnd = (TemperingClient.ground() && TemperingClient.enrolled()) || !TemperingClient.activeRole().isEmpty() || TemperingClient.training();
        if (start != null) start.active = canStart;
        if (end != null) end.active = canEnd;
        boolean canChange = TemperingClient.ground() && TemperingClient.enrolled() && !TemperingClient.training();
        if (difficulty != null) {
            difficulty.active = canChange;
            difficulty.setMessage(Component.literal(difficultyLabel()));
        }
        for (int i = 0; i < roles.length; i++) {
            if (roles[i] == null) continue;
            boolean picked = InstructorEntity.VARIANTS[i].equals(TemperingClient.selectedRole());
            roles[i].setMessage(Component.literal(roleLabel(i)));
            // 已选中的教官不用再点一次，但也不能"灰到看不出是选中"——用金色字标出来
            roles[i].active = !picked || !TemperingClient.activeRole().isEmpty();
        }
    }

    /** 服务端推来新快照时调用（{@link TemperingClient} 里 {@code open} 分支）。 */
    public void refreshFromServer() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        refresh();
    }

    private void text(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, left + x, top + y, color, false);
    }

    public static String trainingStatus() {
        if (!TemperingClient.training()) return "未在训练";
        if (TemperingClient.blocks() < 3) return "格挡 " + TemperingClient.blocks() + " / 3 · 持盾应对敌人";
        int ticks = TemperingClient.counterTicks();
        return ticks > 0
            ? "反击窗口 " + String.format(Locale.ROOT, "%.1f", ticks / 20.0) + " 秒 · 反击最后一个敌人"
            : "反击窗口已过 · 再次格挡刷新窗口";
    }

    /**
     * 选中教官的课程进度：{@code 莉娅 · 剑术 · 出师 1/4 · 正在上 第三课 · 旋转斩击 1/2}。
     *
     * <p>终端只做「选人 / 难度 / 开始」，但玩家点进来第一件想知道的事往往是"我跟他练到哪了"。
     * 进度由服务端随快照同步（{@link TemperingClient#courseBrief}），面板不自己算，
     * 免得面板显示 2/3、教官头顶显示 1/3。
     */
    static String courseLine() {
        int index = 1;
        for (int i = 0; i < ROLE_LABELS.length; i++) {
            if (InstructorEntity.VARIANTS[i].equals(TemperingClient.selectedRole())) {
                index = i;
                break;
            }
        }
        String role = InstructorEntity.VARIANTS[index];
        String brief = TemperingClient.courseBrief(role);
        if (brief.isEmpty()) return "";
        StringBuilder out = new StringBuilder(ROLE_LABELS[index])
            .append(" · ").append(ROLE_TITLES[index]).append(" · 出师 ").append(brief);
        if (TemperingClient.courseComplete(role)) out.append("（已出师）");
        String all = TemperingClient.courseTotals();
        if (!all.isEmpty()) out.append(" · 全部 ").append(all);
        if (role.equals(TemperingClient.activeRole())) {
            String now = TemperingClient.courseNow();
            if (!now.isEmpty()) out.append(" · 正在上 ").append(now);
        }
        return out.toString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        refresh();
        renderBackground(g);
        g.pose().pushPose();
        g.pose().scale(fit, fit, 1);
        ArcaneGui.frame(g, left, top, W, H);
        text(g, "教官终端", 18, 15, 0xffe8c68a);
        text(g, "武器图鉴 " + WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString(), 232, 15, MUTED);
        text(g, "选择教官", 18, 36, 0xffe8c68a);
        text(g, TemperingClient.enrolled()
            ? "磨砺 " + TemperingClient.points() + " / " + TemperingClient.cap()
                + " · " + TemperingRules.rankName(TemperingClient.points())
            : "此角色尚无战士天赋", 120, 36, MUTED);
        text(g, TemperingClient.ground()
            ? "场内保护生命，武器攻击仍会消耗耐久。"
            : "走到天空岛西侧的演武场，右键中央终端开始。", 18, 108, MUTED);
        String course = courseLine();
        if (!course.isEmpty()) text(g, course, 18, 120, INK);
        text(g, trainingStatus(), 18, 130, INK);
        text(g, TemperingClient.completions() == 0 ? "本档尚未完成"
            : "完成 " + TemperingClient.completions() + " 次 · 最佳 " + TemperingClient.bestScore() + " 分",
            18, 140, MUTED);
        super.render(g, (int) (mx / fit), (int) (my / fit), partial);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int b) {
        return super.mouseClicked(x / fit, y / fit, b);
    }

    @Override
    public boolean mouseReleased(double x, double y, int b) {
        return super.mouseReleased(x / fit, y / fit, b);
    }

    @Override
    public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
        return super.mouseDragged(x / fit, y / fit, b, dx / fit, dy / fit);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x / fit, y / fit);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (WarriorPracticeKeys.OPEN.matches(key, scan)) {
            // 终端里按 K 直接跳到图鉴，不用先关面板再按一次
            TemperingClient.hintCodex();
            TemperingActionPacket.send(TemperingActionPacket.Action.VIEW);
            minecraft.setScreen(new WarriorCodexScreen());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
