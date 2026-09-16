package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD 上的「魔法石」入口 —— 画在<b>血条和饱食度中间</b>（快捷栏上方）。
 *
 * <p>和 {@link MagicStoneClientEvents}（物品栏 E 界面里那颗）是<b>两个入口，都保留</b>：
 * 那边可以用鼠标点（界面里有光标），这边不行，所以这边配快捷键
 * {@link MagicStoneKeys}（默认 V，可在「选项 → 控制」里改）。
 *
 * <h2>为什么 HUD 上的图标"点不了"</h2>
 * 游戏里没开界面时，<b>鼠标是被锁定的</b>（光标固定在屏幕中心用来转视角），
 * 所以 HUD 图标收不到鼠标位置 —— 在那个位置点击会被当成攻击/挖方块。
 * 原版和其它 mod 的 HUD 按钮也都是"图标 + 快捷键"，就是这个原因。
 *
 * <h2>坐标是怎么来的（原版 HUD 布局）</h2>
 * <pre>
 *   快捷栏   : x = 宽/2 - 91,  y = 高 - 22
 *   血条     : 从 宽/2 - 91 起,  y = 高 - 39   （10 颗心到 宽/2 - 1 结束）
 *   饱食度   : 从 宽/2 + 1 起                   （所以两者正好在正中相接）
 *   经验等级 : 正中、约 y = 高 - 35
 * </pre>
 * 也就是说"血条和饱食度中间"那一格<b>本来就被占满了</b>（两者相接，经验数字也在那），
 * 所以图标放在它们<b>正上方</b>。想挪位置只改 {@link #BOTTOM_MARGIN}。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicStoneHud {

    /** 图标边长（和 {@link MagicStoneButton} 的石头一致）。 */
    static final int ICON_SIZE = 16;

    /**
     * 图标底边距屏幕底部多少像素。
     *
     * <p>58 = 血条/饱食度那一行（39）再往上让开 19 像素：
     * 图标自身高 16，占 高-58 .. 高-42；血条从 高-39 开始 —— 中间留 3 像素。
     * <b>（要调整位置就改这个数：越大越靠上。）</b>
     */
    private static final int BOTTOM_MARGIN = 58;

    private MagicStoneHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 开着界面时不画（原版这时本来也不画 HUD）；F1 隐藏 HUD 时也不画
        if (minecraft.screen != null || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        // 画法直接复用物品栏那颗石头，避免两处各画一份
        MagicStoneButton.drawStone(graphics, width / 2 - ICON_SIZE / 2, height - BOTTOM_MARGIN, false);
    }
}
