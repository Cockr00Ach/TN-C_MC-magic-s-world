package com.tnc.tnc.dialogue.client;

import com.tnc.tnc.dialogue.DialogueNetwork;
import com.tnc.tnc.dialogue.DialogueScript;
import com.tnc.tnc.dialogue.DialogueTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话界面 —— 视觉小说式的下方对话框（用户 2026-09-21 选定的方案 A）。
 *
 * <h2>手感</h2>
 * <ul>
 *   <li>文字<b>逐字显示</b>；没显示完时点击/空格 = <b>立刻显示整句</b>；</li>
 *   <li>显示完了再点击/空格 = <b>下一句</b>；</li>
 *   <li>最后一句之后关闭界面，并回报服务端"看完了"。</li>
 * </ul>
 *
 * <h2>为什么继承 Screen 而不是画 HUD</h2>
 * 继承 {@link Screen} 会<b>自动吃掉所有输入</b>（玩家不会一边对话一边走动/挖方块），
 * 而且原版会正确处理"界面打开时暂停""鼠标释放"。画在 HUD 上这些都得自己写一遍。
 *
 * <p>旁白（说话人为空的行）用<b>灰色斜体、居中</b>显示，和角色台词区分开
 * —— 剧本里那七处动作提示就是旁白。
 *
 * <p><b>配色</b>：每个 NPC 可以不一样（用户 2026-09-22），由剧本自带的主题决定，
 * 见 {@link DialogueTheme}。旁白灰是唯一不随主题变的部分。
 */
public class DialogueScreen extends Screen {

    private static final int MARGIN = 12;
    private static final int PANEL_HEIGHT = 84;
    private static final int PAD = 8;
    private static final int LINE_HEIGHT = 11;

    /** 逐字速度：每 N tick 出一个字（1 = 最快，20 tick = 1 秒）。 */
    private static final int CHARS_PER_TICK_INTERVAL = 1;

    /** 旁白灰 —— 刻意**不随主题变**：动作提示要一直在"画面之外"，不能跟角色抢眼。 */
    private static final int NARRATION_COLOR = 0xFFB9B9B9;

    private final DialogueScript script;

    /**
     * 对话框配色 —— 每个 NPC 可以不一样（用户 2026-09-22 的要求）。
     * 主题随剧本下发（见 {@code DialogueTheme}），这里只管取用；
     * {@code null} 时回落默认金色，也就是以前的样子。
     */
    private final DialogueTheme.Palette theme;

    private final List<FormattedCharSequence> wrapped = new ArrayList<>();

    private int lineIndex;
    private int revealChars;
    private int revealTimer;
    private int panelTop;
    private int panelLeft;
    private int panelWidth;
    private boolean finished;

    public DialogueScreen(DialogueScript script) {
        super(Component.literal("TN-C Dialogue"));
        this.script = script;
        this.theme = DialogueTheme.byName(script.theme());
    }

    // ------------------------------------------------------------------ 布局

    @Override
    protected void init() {
        this.panelWidth = Math.max(220, this.width - MARGIN * 2);
        this.panelLeft = (this.width - panelWidth) / 2;
        this.panelTop = this.height - PANEL_HEIGHT - MARGIN;
        rewrap();
    }

    private DialogueScript.Line currentLine() {
        return script.lines().get(lineIndex);
    }

    private boolean isNarration() {
        return currentLine().speaker().isEmpty();
    }

    /** 按面板宽度把当前这句折行（中文要按字符宽度算，不能按空格断词）。 */
    private void rewrap() {
        wrapped.clear();
        var font = Minecraft.getInstance().font;
        String text = currentLine().text();
        int maxWidth = panelWidth - PAD * 2;

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                wrapped.addAll(font.split(Component.literal(line.toString()), maxWidth));
                line.setLength(0);
                continue;
            }
            line.append(c);
            if (font.width(line.toString()) > maxWidth) {
                // 回退最后一个字符，换行
                line.setLength(line.length() - 1);
                wrapped.addAll(font.split(Component.literal(line.toString()), maxWidth));
                line.setLength(0);
                line.append(c);
            }
        }
        if (line.length() > 0) {
            wrapped.addAll(font.split(Component.literal(line.toString()), maxWidth));
        }
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public void tick() {
        if (revealChars < currentLine().text().length()) {
            revealTimer++;
            if (revealTimer >= CHARS_PER_TICK_INTERVAL) {
                revealTimer = 0;
                revealChars++;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClick();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 空格 / 回车 / ESC 都算"继续"；ESC 也走正常流程，避免半路关掉不留记录
        if (keyCode == 32 || keyCode == 257 || keyCode == 256) {
            onClick();
            return true;
        }
        return true; // 吃掉其它按键：对话时不移动、不开背包
    }

    private void onClick() {
        if (revealChars < currentLine().text().length()) {
            revealChars = currentLine().text().length(); // 先补全整句
            return;
        }
        if (lineIndex + 1 < script.lines().size()) {
            lineIndex++;
            revealChars = 0;
            revealTimer = 0;
            rewrap();
            return;
        }
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        DialogueNetwork.notifyDone(script.id());
        this.onClose();
    }

    @Override
    public void onClose() {
        // 无论是点到最后一句、还是按 ESC 半途离开，都算"看过了"（避免卡住无法重看）
        if (!finished) {
            finished = true;
            DialogueNetwork.notifyDone(script.id());
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 单人游戏里不暂停世界，和原版对话框一致
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 底部半透明面板 + 主题色描边（颜色随 NPC 变，见 DialogueTheme）
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + PANEL_HEIGHT, theme.panel());
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, theme.border());
        graphics.fill(panelLeft, panelTop + PANEL_HEIGHT - 1, panelLeft + panelWidth,
                panelTop + PANEL_HEIGHT, theme.border());
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + PANEL_HEIGHT, theme.border());
        graphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth,
                panelTop + PANEL_HEIGHT, theme.border());

        var font = Minecraft.getInstance().font;
        int x = panelLeft + PAD;
        int y = panelTop + PAD;

        // 名字栏（旁白不显示名字）
        if (!isNarration()) {
            graphics.drawString(font, currentLine().speaker(), x, y, theme.name(), true);
            y += LINE_HEIGHT + 2;
        }

        // 正文（逐字）
        String full = currentLine().text();
        int shown = Math.min(revealChars, full.length());
        int color = isNarration() ? NARRATION_COLOR : theme.text();
        int maxRows = (PANEL_HEIGHT - PAD * 2 - (isNarration() ? 0 : LINE_HEIGHT + 2)) / LINE_HEIGHT;
        int drawn = 0;
        for (FormattedCharSequence row : wrapped) {
            if (drawn >= maxRows || shown <= 0) {
                break;
            }
            // 这一行要显示几个字符：够就直接画整行，不够就逐字符画到 shown 用完。
            // （不切片 FormattedCharSequence —— 那样要手写 FormattedCharSink，
            //   中文场景逐字符画就够了，代码也短得多。）
            String rowText = plainText(row);
            if (shown >= rowText.length()) {
                graphics.drawString(font, row, x, y, color, true);
                shown -= rowText.length();
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < shown && i < rowText.length(); i++) {
                    sb.append(rowText.charAt(i));
                }
                graphics.drawString(font, sb.toString(), x, y, color, true);
                shown = 0;
            }
            y += LINE_HEIGHT;
            drawn++;
        }

        // 右下角的"继续"提示（整句显示完之后才出现）
        if (revealChars >= full.length()) {
            String hint = "\u25bc";
            graphics.drawString(font, hint,
                    panelLeft + panelWidth - PAD - font.width(hint),
                    panelTop + PANEL_HEIGHT - PAD - 8, theme.hint(), true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 把一行 FormattedCharSequence 还原成纯文本（只用于算"这一行有几个字"）。 */
    private static String plainText(FormattedCharSequence row) {
        StringBuilder sb = new StringBuilder();
        row.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
