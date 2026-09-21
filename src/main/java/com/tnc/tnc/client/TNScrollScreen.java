package com.tnc.tnc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

/**
 * 残卷的阅读界面 —— <b>直接复用原版书本界面</b> ✓（{@code BookViewScreen}）。
 *
 * <p>为什么复用而不是自己画 ✗：原版书界面自带翻页按钮、翻页音效、排版与 114px 正文宽度，
 * 而且不用新贴图 ✓。我们只负责把语言文件里的正文**切成一页一页**递进去 ✓。
 *
 * <p>两个硬约束（来自原版实现，别改 ✗）：
 * <ul>
 *   <li>正文宽度 {@code BookViewScreen.TEXT_WIDTH} = <b>114px</b>；</li>
 *   <li>一页最多画 {@code 128 / 9} = <b>14 行</b> ✗ 超出的行会被**直接裁掉**（不报错）；
 *       所以这里只放 13 行，留一行余量 ✓。</li>
 * </ul>
 *
 * <p>排版策略：先把整卷正文按 114px 宽自己折行（{@link Font#plainSubstrByWidth}），
 * 折好的行再切成每页 13 行、用 {@code \n} 连成一个组件交给原版。
 * 因为每行都已经 ≤114px，原版再折一次也还是这些行，<b>不会被裁</b> ✓。
 *
 * <p>「」里的关键字词按**编剧的要求**换成金色 ✓（"「」里的字可以用略不同的颜色"），
 * 不做解释、不加注释 ✓ —— 让玩家自己看。
 *
 * <p>本类只在客户端加载 ✓（由 {@code TNScrollItem} 经 {@code DistExecutor} 调用），
 * 专用服务端不会碰它 ✗。
 */
public final class TNScrollScreen {

    /** 原版书页正文宽度（BookViewScreen.TEXT_WIDTH） */
    private static final int TEXT_WIDTH = 114;
    /** 原版一页最多 14 行；留 1 行余量，避免被裁掉 ✓ */
    private static final int LINES_PER_PAGE = 13;
    /** 正文缺失时的兜底文案键（作者还没写/键写错时不至于显示成一串键名 ✗） */
    private static final String MISSING_KEY = "scroll.tnc.missing";

    /** 右键打开某一卷（titleKey = 卷名，textKey = 正文） */
    public static void open(String titleKey, String textKey) {
        Minecraft.getInstance().setScreen(new BookViewScreen(new ScrollAccess(titleKey, textKey)));
    }

    private TNScrollScreen() {
    }

    /** 把一卷正文变成原版书能读的"多页" */
    private static final class ScrollAccess implements BookViewScreen.BookAccess {

        private final List<FormattedText> pages = new ArrayList<>();

        ScrollAccess(String titleKey, String textKey) {
            String body = I18n.exists(textKey) ? I18n.get(textKey) : I18n.get(MISSING_KEY);

            List<String> lines = new ArrayList<>();
            lines.add(I18n.get(titleKey));   // 第一页第一行 = 卷名
            lines.add("");                   // 空一行再起正文
            lines.addAll(wrap(body));

            for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
                int end = Math.min(lines.size(), start + LINES_PER_PAGE);
                MutableComponent page = Component.empty();
                for (int i = start; i < end; i++) {
                    if (i > start) {
                        page.append(Component.literal("\n"));
                    }
                    page.append(i == 0
                            ? Component.literal(lines.get(i)).withStyle(ChatFormatting.GOLD)
                            : highlight(lines.get(i)));
                }
                this.pages.add(page);
            }
        }

        @Override
        public int getPageCount() {
            return this.pages.size();
        }

        @Override
        public FormattedText getPageRaw(int index) {
            return this.pages.get(index);
        }
    }

    /**
     * 按 114px 折行。{@code \n} 是作者分段用的，必须保留（空行 = 段落间隔）✓。
     *
     * <p>原版 {@code Font.split} 折的是 {@code FormattedCharSequence}（渲染层，拼不回组件 ✗），
     * 所以这里用 {@link Font#plainSubstrByWidth} 自己折纯文本行，再逐行上色 ✓。
     */
    private static List<String> wrap(String text) {
        Font font = Minecraft.getInstance().font;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String rest = paragraph;
            while (!rest.isEmpty()) {
                String fit = font.plainSubstrByWidth(rest, TEXT_WIDTH);
                if (fit.isEmpty()) {
                    fit = rest.substring(0, 1);   // 单个字比整页还宽（理论上不会有）时兜底 ✓
                }
                if (fit.length() < rest.length()) {
                    int space = fit.lastIndexOf(' ');
                    if (space > 0) {
                        // 英文单词尽量不从中间断开 ✓（中文没有空格，会走下面的整段折行）
                        lines.add(rest.substring(0, space));
                        rest = rest.substring(space + 1).trim();
                        continue;
                    }
                }
                lines.add(fit);
                rest = rest.substring(fit.length());
            }
        }
        return lines;
    }

    /** 「」里的字词上金色 —— 线索就藏在这儿，别的地方一律原色 ✓ */
    private static Component highlight(String line) {
        if (line.indexOf('\u300c') < 0) {
            return Component.literal(line);
        }
        MutableComponent out = Component.empty();
        int i = 0;
        while (i < line.length()) {
            int open = line.indexOf('\u300c', i);
            if (open < 0) {
                out.append(Component.literal(line.substring(i)));
                break;
            }
            int close = line.indexOf('\u300d', open + 1);
            if (close < 0) {
                out.append(Component.literal(line.substring(i)));
                break;
            }
            if (open > i) {
                out.append(Component.literal(line.substring(i, open)));
            }
            out.append(Component.literal(line.substring(open, close + 1)).withStyle(ChatFormatting.GOLD));
            i = close + 1;
        }
        return out;
    }
}
