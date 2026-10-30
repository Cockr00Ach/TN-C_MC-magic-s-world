package com.tnc.tnc.warrior.client;

import com.tnc.tnc.client.ui.ArcaneGui;
import com.tnc.tnc.warrior.*;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 「战士修习」面板。
 *
 * <p>下半张就是演武终端的界面：先点一位教官，再点「开始练习」，服务器会把教官召到你的演武位上。
 * 面板本身不发奖励、不算进度，所有判定都在服务端。
 */
public final class WarriorPracticeScreen extends Screen {
    private static final int W = 360, H = 340, INK = 0xffe6dccb, MUTED = 0xffb9b1a3;
    private static final String[] ROLE_LABELS = {"陪练", "洛克", "莉娅", "岚"};
    private int left, top;
    private float fit = 1;
    private Button start, end, difficulty;
    private final Button[] roles = new Button[ROLE_LABELS.length];

    public WarriorPracticeScreen() {
        super(Component.literal("战士修习"));
    }

    @Override
    protected void init() {
        fit = Math.min(1, Math.min((width - 16f) / (W + 4), (height - 16f) / (H + 4)));
        fit = Math.max(.25f, fit);
        width = (int) Math.ceil(width / fit);
        height = (int) Math.ceil(height / fit);
        left = (width - W) / 2;
        top = (height - H) / 2;
        for (int i = 0; i < roles.length; i++) {
            final int index = i;
            roles[i] = addRenderableWidget(Button.builder(Component.literal(roleLabel(i)), b -> {
                TemperingClient.selectRole(InstructorEntity.VARIANTS[index]);
                refresh();
            }).bounds(left + 18 + i * 82, top + 217, 78, 20).build());
        }
        start = addRenderableWidget(Button.builder(Component.literal("开始练习"), b -> {
            TemperingActionPacket.send(TemperingActionPacket.Action.SUMMON, TemperingClient.selectedRole());
            minecraft.setScreen(null);
        }).bounds(left + 18, top + 265, 100, 20).build());
        end = addRenderableWidget(Button.builder(Component.literal("结束练习"), b ->
            TemperingActionPacket.send(TemperingActionPacket.Action.DISMISS))
            .bounds(left + 130, top + 265, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
            .bounds(left + 242, top + 265, 100, 20).build());
        difficulty = addRenderableWidget(Button.builder(Component.literal(difficultyLabel()), b ->
            TemperingActionPacket.send(TemperingActionPacket.Action.CYCLE_PRACTICE))
            .bounds(left + 18, top + 241, 324, 20).build());
        refresh();
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
        boolean canEnd = !TemperingClient.activeRole().isEmpty() || TemperingClient.training();
        if (start != null) start.active = canStart;
        if (end != null) end.active = canEnd;
        boolean canChange = TemperingClient.ground() && TemperingClient.enrolled() && !TemperingClient.training();
        if (difficulty != null) {
            difficulty.active = canChange;
            difficulty.setMessage(Component.literal(difficultyLabel()));
        }
        for (int i = 0; i < roles.length; i++) {
            if (roles[i] != null) roles[i].setMessage(Component.literal(roleLabel(i)));
        }
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        refresh();
        renderBackground(g);
        g.pose().pushPose();
        g.pose().scale(fit, fit, 1);
        ArcaneGui.frame(g, left, top, W, H);
        int talent = TemperingClient.talent(), points = TemperingClient.points(), cap = TemperingClient.cap();
        text(g, "战士修习", 18, 15, 0xffe8c68a);
        text(g, font.plainSubstrByWidth("快捷键 " + WarriorPracticeKeys.OPEN.getTranslatedKeyMessage().getString(), 122), 220, 15, MUTED);
        text(g, "天赋 " + talent + " / 6", 18, 39, INK);
        text(g, "成长 " + WarriorTalentRules.multiplier(talent) + " 倍", 220, 39, INK);
        text(g, (talent == 0 ? "未修习" : TemperingRules.rankName(points)) + " · 磨砺 " + points + " / " + cap, 18, 60, INK);
        text(g, "额外减伤 " + String.format(Locale.ROOT, "%.1f%%", TemperingRules.reduction(points) * 100), 220, 60, INK);
        g.fill(left + 18, top + 79, left + 342, top + 85, 0xff2b3033);
        if (cap > 0) g.fill(left + 18, top + 79, left + 18 + (int) (324L * points / cap), top + 85, 0xff87bfac);
        String goal = talent == 0 ? "此角色尚无战士天赋，无法积累磨砺。"
            : points >= cap ? "已达到当前天赋上限，修习成果永久保留。"
            : "下一目标 " + TemperingRules.nextMilestone(points, cap) + " 磨砺 · 天赋不会随训练提升";
        text(g, goal, 18, 96, MUTED);
        text(g, "守势反击", 18, 120, 0xffe8c68a);
        int reward = Math.min(Math.max(0, cap - points), WarriorTalentRules.gain(GuardDrill.REWARD, talent));
        text(g, TemperingClient.graduate() ? "首次训练已完成" : "首次完成预计 +" + reward + " 磨砺", 170, 120, MUTED);
        text(g, "三次盾挡后，在" + (PracticeDifficulty.window(TemperingClient.mode()) / 20) + "秒内持剑或斧反击最后一个敌人。", 18, 139, MUTED);
        text(g, trainingStatus(), 18, 158, INK);
        String wind = points < 200 ? "风刃 · 需要 200 磨砺及风灌注武器"
            : TemperingClient.windTicks() > 0
            ? "风刃 · 冷却 " + String.format(Locale.ROOT, "%.1f", TemperingClient.windTicks() / 20.0) + " 秒"
            : "风刃 · 已就绪，持风灌注武器潜行对空右键";
        text(g, wind, 18, 180, INK);
        text(g, TemperingClient.ground()
            ? "场内保护生命，武器攻击仍会消耗耐久。"
            : "走到天空岛西侧的演武场，右键中央终端选择教官。", 18, 195, MUTED);
        text(g, "教官终端 · 四个演武位，选一位召出", 18, 205, 0xffe8c68a);
        String record = TemperingClient.completions() == 0 ? "本档尚未完成 · 漏挡越少、反击越快，评价越高"
            : "完成 " + TemperingClient.completions() + " 次 · 最佳 " + TemperingClient.bestScore() + "分 · 最快反击 "
            + String.format(Locale.ROOT, "%.2f", TemperingClient.bestReaction() / 20.0) + "秒";
        text(g, record, 18, 292, MUTED);
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
