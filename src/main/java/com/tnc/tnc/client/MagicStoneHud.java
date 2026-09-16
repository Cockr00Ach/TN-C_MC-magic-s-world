package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD：<b>魔力条</b> + 「魔法石」入口图标。
 *
 * <h2>版面（按<b>这个整合包实际画出来的</b>位置摆，不是按原版）</h2>
 * <pre>
 *   高-64 :            ◆ 魔法石图标（居中，按 V 打开界面）
 *   高-61 : 护甲条(左) [████ 魔力 ████](右)   ← 魔力条就摆这一行
 *   高-50 : 血条(左)                饱食度(右)
 *   高-22 : [            快捷栏            ]
 * </pre>
 * 魔力条和<b>饱食度</b>同宽（80）同右边界（{@code 宽/2 + 91}），就贴在饱食度正上方一行。
 *
 * <h3>为什么不跟血条叠在一起（踩过的坑）</h3>
 * 这个包把血条/饱食度从原版的心/鸡腿换成了 11 像素高的贴图条
 * （{@code whisperingstatusbar}），于是它们比原版的"高-39"整整高了一行。
 * 我们一开始按原版算，条子画在"高-49"，结果<b>正好横在血条身上把它全盖住了</b>。
 * 现在是往上一行（"高-61"，也就是包内护甲条那一行的右半边，那里空着）。
 *
 * <h3>聊天框那块暗底：真正的坑不是"压暗"，是"整块被丢掉"</h3>
 * 原版聊天框每行消息都铺一条暗底，最新一条的底边在 GUI {@code 高-40}、
 * 一条 9 像素高（见 {@code ChatComponent.render}：{@code fill(-4, i1-9, 宽+8, i1)}，
 * 其中 {@code i1 = (高-40)/缩放}）。血条/饱食度正好长在这条带子里，所以一有聊天消息
 * 它们就会变暗。我们把条子挪到"高-61"（护甲条那一行的右半边）以后，条子整条都在带子
 * 外面（带宽最多到 {@code 宽/2+12} 左右），干干净净。
 *
 * <p>但<b>发光有 1 像素重叠也会出事</b>，而且出事的样子很吓人：
 * 落在带子里的像素不是变暗，是<b>整个消失</b>。原因是深度缓冲 ——
 * 聊天框暗底画在 {@code z=+50}、文字画在 {@code z=+100}，而 GUI 的渲染类型
 * 既做深度测试又写深度；我们挂在 {@code RenderGuiEvent.Post}（聊天框之后）用默认
 * {@code z=0} 画，深度测试直接失败、被丢掉。所以现在整体抬到 {@code z=+75}：
 * 压过暗底、仍低于文字。详见 {@link #HUD_Z}。
 *
 * <p>历史教训：之前用户报"魔法石被遮住了一半、就少了一半"，
 * 少掉的那半正好是落进暗底矩形的那半 —— 当时误判成"配色不够亮"，
 * 其实跟颜色毫无关系。
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

    /** 魔力条贴图尺寸 —— 必须和 tools\mana_bar_texture_gen.ps1 里的 $W / $H 一致。 */
    private static final int TEX_W = 80;
    private static final int TEX_H = 10;

    /** 原版血条/饱食度那一行的锚点（以屏幕底部为基准）。 */
    private static final int STATUS_ROW_MARGIN = 39;

    /**
     * 这个包的状态条一行有多高（实测 11 像素：条子本体 + 边框）。
     *
     * <p>包里的血条/饱食度被换成了贴图条，一行行往上码：护甲在 {@code 高-61}、
     * 血条和饱食度在 {@code 高-50}。这个 11 就是"往上挪一行"的步长。
     */
    private static final int PACK_BAR_ROW_HEIGHT = 11;

    /** 饱食度那一行的上沿（实测 高-50）。 */
    private static final int HUNGER_ROW_TOP_MARGIN = STATUS_ROW_MARGIN + PACK_BAR_ROW_HEIGHT;

    /** 魔力条：摆在饱食度正上方那一行（高-61），底边压不到饱食度的边框。 */
    private static final int MANA_BAR_TOP_MARGIN = HUNGER_ROW_TOP_MARGIN + PACK_BAR_ROW_HEIGHT;

    /** 魔力条左边界相对屏幕中心的偏移：右端和饱食度对齐（{@code 宽/2 + 91}）。 */
    private static final int MANA_BAR_X_OFFSET = 91 - TEX_W;

    /** 图标底边距：让菱形中心正好落在魔力条的中线上（高-56）。 */
    private static final int ICON_BOTTOM_MARGIN = (MANA_BAR_TOP_MARGIN - TEX_H / 2) + ICON_SIZE / 2;

    /**
     * 我们这一层画在 z = +75。
     *
     * <p>⚠️ 这是"魔法石被聊天框吃掉"的真正原因，非常反直觉，别再踩：
     * <ol>
     *   <li>原版聊天框的<b>暗底画在 z = +50</b>、<b>聊天文字画在 z = +100</b>
     *       （{@code ChatComponent.render} 里连着两次 {@code pose().translate(0, 0, 50)}）。</li>
     *   <li>GUI 用的渲染类型是 <b>带深度测试 + 还会写深度</b>的：
     *       {@code RenderType.GUI} 显式设了 {@code LEQUAL_DEPTH_TEST}，
     *       而 {@code CompositeState} 的默认写入掩码就是 {@code COLOR_DEPTH_WRITE}。</li>
     *   <li>我们挂在 {@code RenderGuiEvent.Post} 上，也就是聊天框<b>之后</b>才画，
     *       如果按默认的 z = 0 画，凡是落进聊天框暗底矩形里的像素都会
     *       <b>深度测试失败直接被丢掉</b> —— 不是"被压暗"，是<b>整块消失</b>。</li>
     * </ol>
     * 用户看到的就是"中心的魔法石被遮住了"，而且之前是"被遮到了一半、就少了一半"
     * —— 少的那一半正好是落在暗底矩形里的那一半。
     *
     * <p>所以抬到 75：<b>压过暗底（50），但仍低于聊天文字（100）</b>。
     * 结果就是条子和宝石永远看得见，而聊天文字也永远不会被我们挡住
     * （文字比我们更靠前，它要盖我们就盖）。位置一动都不用改。
     */
    private static final float HUD_Z = 75.0F;

    // ---------------- 贴图 ----------------
    //
    // 从"代码画色块"改成"贴图"了（作者那个血条 mod 也是这么做的）。
    // 贴图由 tools\mana_bar_texture_gen.ps1 生成，改配色重跑那个脚本即可。
    //
    // 两个关键点：
    //  1. 用**带贴图尺寸的 blit 重载**。不带尺寸的那个假设贴图是 256x256，会采样错位。
    //  2. 1 贴图像素 = 1 GUI 像素（和原版 GUI 美术一样，原版经验条贴图是 182x5），
    //     所以贴图尺寸必须和下面这两个常量一致，否则会被拉伸。
    private static final ResourceLocation MANA_BAR_EMPTY =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/gui/mana_bar/mana_empty.png");
    private static final ResourceLocation MANA_BAR_FILL =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/gui/mana_bar/mana_fill.png");

    // ---------------- 配色（只给代码画的部分用：魔法石图标、条上的数字） ----------------
    //
    // ⚠️ 条子本体已经改用贴图了，这几个颜色现在只管「魔法石菱形」和「条上的数字」。
    // 数字要白字带阴影：条子底色是紫色，白字在任何填充比例下都清楚。
    // 菱形取中高亮度，是因为它可能要压在聊天框暗底上，深紫蒙一层就糊成黑色了。

    /** 菱形的外描边。 */
    private static final int COLOR_BORDER = 0xFFB9A7FF;
    private static final int COLOR_FILL = 0xFF9C86F5;
    private static final int COLOR_FILL_TOP = 0xFFE4DCFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private MagicStoneHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // F1（hideGui）时整体隐藏，别的什么都不挡。
        //
        // ⚠️ 这里**故意不判 minecraft.screen** —— 这是之前一个 bug 的根：
        //    原版开着界面时照样画 HUD（GameRenderer：
        //    `if (!hideGui || screen != null) gui.render(...)`，界面在这之后才画上去），
        //    所以按 T 开聊天框时，血条、饱食度、快捷栏都还在、只是被压暗一点。
        //    我们原先写了 `screen != null` 就 return，结果全场只有魔力条整条消失，
        //    玩家一眼就看出是 bug（"血条饱食度半透明还在，魔力条没了"）。
        //    现在跟原版一致：照画，界面自己会盖在上面，该压暗就压暗。
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        // 抬到聊天框暗底之上（见 HUD_Z 的注释）。不抬的话，落在聊天框那块矩形里的
        // 像素会被深度测试直接丢掉 —— 表现就是魔法石"被遮住/少了一半"。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HUD_Z);

        // 右半边：和饱食度同宽、同右边界，摆在它正上方一行
        drawManaBar(minecraft, graphics, width / 2 + MANA_BAR_X_OFFSET, height);

        // 魔法石入口图标（画法复用物品栏那颗，避免两处各画一份）
        drawStone(graphics, width / 2 - ICON_SIZE / 2, height - ICON_BOTTOM_MARGIN, false);

        graphics.pose().popPose();
    }

    /** 画魔力条 + 条上的数字；拿不到数据（还没同步过来）就什么都不画。 */
    private static void drawManaBar(Minecraft minecraft, GuiGraphics graphics, int x, int height) {
        MagicStoneData data = MagicStone.getOrNull(minecraft.player);
        if (data == null || data.getMaxMana() <= 0) {
            return;     // 避免除以 0：数据还没同步到客户端时 maxMana 是 0
        }
        int mana = Math.max(0, Math.min(data.getMana(), data.getMaxMana()));
        int max = data.getMaxMana();

        int y = height - MANA_BAR_TOP_MARGIN;

        // 1) 空槽：整张铺上
        graphics.blit(MANA_BAR_EMPTY, x, y, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);

        // 2) 填充：把目标宽度按比例缩小 —— 这个重载里"目标宽"同时就是"取贴图的宽"，
        //    所以宽度小于整条时会自动只取贴图左边那一段，等于从右往左裁。
        //    （贴图因此必须左对齐：装饰画在左头，右段保持均匀。）
        int filled = (int) Math.round(TEX_W * (double) mana / max);
        if (filled > 0) {
            graphics.blit(MANA_BAR_FILL, x, y, 0.0F, 0.0F, filled, TEX_H, TEX_W, TEX_H);
        }

        // 3) 数字：画在条子正中间，白字带阴影 ——
        //    条子底色是紫色，白字在任何比例下都清晰。
        String text = mana + "/" + max;
        var font = minecraft.font;
        int textX = x + (TEX_W - font.width(text)) / 2;
        int textY = y + (TEX_H - font.lineHeight) / 2 + 1;
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
