package com.tnc.tnc.client;

import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 物品栏（E）界面里那颗「魔法石」—— 点它打开魔法石界面。
 *
 * <p>设计文档第八节：魔法石不是背包物品，而是显示在界面上的一颗石头。
 * 实现方式：监听 {@code ScreenEvent.Init.Post}（见 {@link MagicStoneClientEvents}），
 * 如果是物品栏界面就塞一个控件进去 —— 用 Forge 的界面事件，<b>不需要 Mixin</b>。
 *
 * <p><b>这是两个入口之一</b>：HUD 上还有一个（{@link MagicStoneHud}，按 V 打开）。
 * 区别是<b>这个能用鼠标点</b>（界面里有光标），HUD 那个不行。
 *
 * <p>石头的画法在 {@link MagicStoneHud#drawStone} —— 两个入口共用一份，不重复写。
 */
public class MagicStoneButton extends AbstractWidget {

    public MagicStoneButton(int x, int y) {
        super(x, y, MagicStoneHud.ICON_SIZE, MagicStoneHud.ICON_SIZE, Component.literal("魔法石"));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MagicStoneHud.drawStone(graphics, getX(), getY(), isHoveredOrFocused());
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
}
