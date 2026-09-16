package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD：<b>魔力条</b> + 「魔法石」入口图标。
 *
 * <h2>版面（全部依据原版 HUD 的实际布局）</h2>
 * <pre>
 *   高-58 :              ◆ 魔法石图标（居中，按 V 打开界面）
 *   高-49 : [████░░░░]   魔力条（和血条同宽、同左边界）
 *   高-39 : ♥♥♥♥♥♥♥♥♥♥   血条（左）        🍗🍗🍗🍗🍗🍗🍗🍗🍗🍗 饱食度（右）
 *   高-22 : [  快捷栏  ]
 * </pre>
 * 魔力条和血条是<b>同一个左边界、同一个宽度</b>（{@code 宽/2 - 91}，80 像素），
 * 所以看上去就是"血条上面又长了一条"。
 *
 * <h2>为什么是"条"而不是"一排宝石"</h2>
 * 血条是 10 颗心，因为它固定是 20 点。魔力上限会从 210 一路涨到上千，
 * 按心那样画会变成几十上百个图标 —— 所以用按比例填充的条，任何上限都放得下。
 *
 * <h2>数值从哪来</h2>
 * 客户端的魔力值是由服务端同步过来的（{@code MagicStoneNetwork}）。
 * 所以服务端在<b>任何改变魔力</b>的地方都必须发包，否则条子会停在旧值上 ——
 * 施法、命令、以及每秒回魔都发了（见 {@code MagicStone.onPlayerTick}）。
 *
 * <p>HUD 图标本身<b>点不了</b>（游戏里鼠标被锁定、没有光标），所以打开界面靠快捷键
 * {@link MagicStoneKeys}（默认 V）。详见 {@link MagicStoneHud} 类注释里的说明。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicStoneHud {

    // ---------------- 版面常量（想挪位置改这里） ----------------

    /** 图标边长。 */
    static final int ICON_SIZE = 16;

    /** 原版 HUD 里各种东西的位置（以屏幕底部为基准）。 */
    private static final int HOTBAR_TOP_MARGIN = 22;
    private static final int STATUS_ROW_MARGIN = 39;   // 血条/饱食度那一行
    private static final int STATUS_ROW_WIDTH = 80;    // 10 颗心 / 10 个鸡腿，各 8 像素

    /** 图标底边距屏幕底部：58 = 魔力条（49）再往上让开，压不到任何东西。 */
    private static final int ICON_BOTTOM_MARGIN = 58;

    /** 魔力条：紧贴在血条正上方。高度 10 是为了把数字塞进条子里。 */
    private static final int MANA_BAR_HEIGHT = 10;
    private static final int MANA_BAR_TOP_MARGIN = STATUS_ROW_MARGIN + 10;

    // ---------------- 配色（奥术紫，和魔法石界面一致） ----------------
    //
    // ⚠️ 这些颜色是**按"要能扛住一层半透明暗色"来选的**，不是随便挑的好看色。
    //
    // 背景：铁匠聊天框（按 T 打开）的暗底是逐行画的，最下面那一行的底边正好在
    // 「高-39」—— 也就是血条的上沿。而魔力条在 高-49..高-39、图标在 高-58..高-42，
    // **正好长在那条暗底带子里**（血条在带子下沿之外，所以它一直看得见）。
    // 于是：血条鲜红 → 蒙上去只是"暗一点"；我们原来的深紫近黑 → 蒙上去直接融成黑色，看着像消失了。
    //
    // 所以这里全部取**中高亮度**：被一层 50% 黑盖住之后仍然认得出。

    /** 描边要够亮 —— 它是被暗色盖住时最后还能辨认出"这里有个东西"的依据。 */
    private static final int COLOR_BORDER = 0xFFB9A7FF;
    /** 空槽。比原来亮很多：原来的 #171232 在暗色下和黑色没区别。 */
    private static final int COLOR_EMPTY = 0xFF3A2F6E;
    private static final int COLOR_FILL = 0xFF9C86F5;
    private static final int COLOR_FILL_TOP = 0xFFE4DCFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

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
        int left = width / 2 - 91;      // 和血条同一个左边界

        drawManaBar(minecraft, graphics, left, height);

        // 魔法石入口图标（画法复用物品栏那颗，避免两处各画一份）
        drawStone(graphics, width / 2 - ICON_SIZE / 2, height - ICON_BOTTOM_MARGIN, false);
    }

    /** 画魔力条 + 条上的数字；拿不到数据（还没同步过来）就什么都不画。 */
    private static void drawManaBar(Minecraft minecraft, GuiGraphics graphics, int left, int height) {
        MagicStoneData data = MagicStone.getOrNull(minecraft.player);
        if (data == null || data.getMaxMana() <= 0) {
            return;     // 避免除以 0：数据还没同步到客户端时 maxMana 是 0
        }
        int mana = Math.max(0, Math.min(data.getMana(), data.getMaxMana()));
        int max = data.getMaxMana();

        int x = left;
        int y = height - MANA_BAR_TOP_MARGIN;
        int w = STATUS_ROW_WIDTH;
        int h = MANA_BAR_HEIGHT;

        // 底 + 边框
        graphics.fill(x, y, x + w, y + h, COLOR_EMPTY);
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);              // 上
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);      // 下
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);              // 左
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);      // 右

        // 按比例填充（内区宽 w-2）
        int inner = w - 2;
        int filled = (int) Math.round(inner * (double) mana / max);
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, COLOR_FILL);
            // 顶部一条亮线，让条子看起来有厚度（血条里的心也有明暗）
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, COLOR_FILL_TOP);
        }

        // 数字：画在条子正中间，白字带阴影 ——
        // 阴影很关键：填充区是紫色、空区是深紫，白字在两种底上都清晰。
        String text = mana + "/" + max;
        var font = minecraft.font;
        int textX = x + (w - font.width(text)) / 2;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        graphics.drawString(font, text, textX, textY, COLOR_TEXT, true);
    }

    /**
     * 画一颗菱形宝石（由外到内三层，越里面越亮）。
     *
     * <p>和物品栏界面里那颗是同一个画法 —— 物品栏的入口保留了，两处共用这一份。
     *
     * @param hovered 高亮（HUD 上用不到；物品栏那个按钮用得上）
     */
    static void drawStone(GuiGraphics graphics, int x, int y, boolean hovered) {
        int size = ICON_SIZE;
        int cx = x + size / 2;
        int cy = y + size / 2;
        int half = size / 2;

        for (int dy = -half + 1; dy <= half - 1; dy++) {
            int w = (half - 1) - Math.abs(dy);
            graphics.fill(cx - w, cy + dy, cx + w, cy + dy + 1, COLOR_BORDER);
        }
        for (int dy = -half + 2; dy <= half - 2; dy++) {
            int w = (half - 2) - Math.abs(dy);
            graphics.fill(cx - w, cy + dy, cx + w, cy + dy + 1,
                    hovered ? COLOR_FILL_TOP : COLOR_FILL);
        }
        // 中间的高光
        graphics.fill(cx - 2, cy - 2, cx + 1, cy, 0xFFF0E8FF);
        graphics.fill(cx - 1, cy - 2, cx + 1, cy + 1, 0xFFF0E8FF);
    }
}
