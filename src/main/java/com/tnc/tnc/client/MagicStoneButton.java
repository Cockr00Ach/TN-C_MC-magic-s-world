package com.tnc.tnc.client;

import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 物品栏里那颗「魔法石」—— 点它打开魔法石界面。
 *
 * <p>设计文档第八节：魔法石不是背包物品，而是<b>显示在物品栏中间</b>的一个入口。
 * 实现方式：监听 {@code ScreenEvent.Init.Post}，如果是物品栏界面就塞一个控件进去
 * （用 Forge 的界面事件，<b>不需要 Mixin</b>）。
 *
 * <p>石头本身现在是用方块色块拼出来的菱形，以后再换成真正的贴图/模型。
 */
public class MagicStoneButton extends AbstractWidget {

    private static final int SIZE = 16;

    // 配色跟其它 TN-C 界面保持一致（奥术紫）
    private static final int COLOR_OUTLINE = 0xFF2E2A6B;
    private static final int COLOR_MID = 0xFF8E77E0;
    private static final int COLOR_MID_HOVER = 0xFFB9A7FF;
    private static final int COLOR_CORE = 0xFFF0E8FF;

    public MagicStoneButton(int x, int y) {
        super(x, y, SIZE, SIZE, Component.literal("魔法石"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawStone(graphics, getX(), getY(), isHoveredOrFocused());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // 打开界面时顺手拉一次最新数据
        MagicStoneNetwork.requestSync();
        Minecraft.getInstance().setScreen(new MagicStoneScreen());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** 画一颗菱形宝石（由外到内三层，越里面越亮）。 */
    static void drawStone(GuiGraphics graphics, int x, int y, boolean hovered) {
        int cx = x + SIZE / 2;
        int cy = y + SIZE / 2;

        for (int dy = -SIZE / 2 + 1; dy <= SIZE / 2 - 1; dy++) {
            int half = (SIZE / 2 - 1) - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, COLOR_OUTLINE);
        }
        for (int dy = -SIZE / 2 + 2; dy <= SIZE / 2 - 2; dy++) {
            int half = (SIZE / 2 - 2) - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, hovered ? COLOR_MID_HOVER : COLOR_MID);
        }
        // 中间的高光
        graphics.fill(cx - 2, cy - 2, cx + 1, cy, COLOR_CORE);
        graphics.fill(cx - 1, cy - 2, cx + 1, cy + 1, COLOR_CORE);
    }
}
