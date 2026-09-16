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
 * HUD：<b>魔力条</b>（魔法石图标已经并进条子贴图里了，见下）。
 *
 * <h2>版面（按<b>这个整合包实际画出来的</b>位置摆，不是按原版）</h2>
 * <pre>
 *   高-63 : 护甲条(左) [▓◆████ 魔力 ████⚡▓](右)   ← 魔力条就摆这一行
 *   高-50 : 血条(左)                饱食度(右)
 *   高-22 : [            快捷栏            ]
 * </pre>
 * 魔力条和<b>饱食度</b>用同一套贴图规格（原始 168x26、按 0.5 倍画成 84x13），
 * 左边界也一样（{@code 宽/2 + 11}），就贴在饱食度的贴图框正上方 ——
 * 于是它正好落在包内<b>护甲条那一行的右半边</b>（那里本来就是空的）。
 *
 * <h3>条子长什么样</h3>
 * 整条都是贴图（{@code thundermagicbar_empty/fill.png}，用户自己画的）：
 * 左边一颗魔法石、中间一条轨道、右边一道闪电，外面套着和血条同款的金框。
 * 代码只干两件事：<b>按魔力比例把中间那段轨道填起来</b>、把数字压上去。
 *
 * <p>因为贴图自己就带着魔法石，所以原来那个<b>居中单独画的魔法石图标已经去掉了</b>
 * （重复）。{@link #drawStone} 还留着，给物品栏界面那个入口按钮用
 * （{@link MagicStoneButton}）；HUD 上打开界面靠快捷键 V（{@link MagicStoneKeys}）。
 *
 * <h3>为什么不跟血条叠在一起（踩过的坑）</h3>
 * 这个包把血条/饱食度从原版的心/鸡腿换成了 13 像素高的贴图条
 * （{@code whisperingstatusbar}），于是它们比原版的"高-39"整整高了一行。
 * 我们一开始按原版算，条子画在"高-49"，结果<b>正好横在血条身上把它全盖住了</b>。
 * 现在摆在"高-63"（= 护甲条那一行的右半边）。
 *
 * <h3>聊天框那块暗底：真正的坑不是"压暗"，是"整块被丢掉"</h3>
 * 原版聊天框每行消息都铺一条暗底，最新一条的底边在 GUI {@code 高-40}、
 * 一条 9 像素高（见 {@code ChatComponent.render}：{@code fill(-4, i1-9, 宽+8, i1)}，
 * 其中 {@code i1 = (高-40)/缩放}）。血条/饱食度正好长在这条带子里，所以一有聊天消息
 * 它们就会变暗。我们把条子挪到"高-63"（护甲条那一行的右半边）以后，条子基本都在带子
 * 外面（带宽最多到 {@code 宽/2+12} 左右，只有最左边一两个像素压线），干干净净。
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
 * <p>HUD 上的东西<b>点不了</b>（游戏里鼠标被锁定、没有光标），所以打开界面靠快捷键
 * {@link MagicStoneKeys}（默认 V）；物品栏界面里那个按钮是另一个入口（可以点）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicStoneHud {

    // ---------------- 版面常量（想挪位置改这里） ----------------

    /** 魔法石图标的边长（只给物品栏那个入口按钮用，见 {@link #drawStone}）。 */
    static final int ICON_SIZE = 16;

    /**
     * 魔力条贴图的原始尺寸 —— <b>和包内那套血条/饱食度贴图一模一样大</b>
     * （whisperingstatusbar 的 health_*.png / food_*.png 都是 168x26）。
     * 贴图里已经画好了左边的魔法石、右边的闪电和金框，所以我们什么都不用再画。
     */
    private static final int TEX_W = 168;
    private static final int TEX_H = 26;

    /**
     * 游戏里实际画多大 = 贴图的一半（84x13）。
     *
     * <p>这不是我们随便定的：**包里的血条/饱食度就是按 0.5 倍画的**
     * （量过：饱食度那条在屏幕上是 84 像素宽、13 像素高，贴图是 168x26）。
     * 照抄这个比例，我们的条子才和上面的护甲条/下面的饱食度严丝合缝。
     */
    private static final int BAR_W = TEX_W / 2;
    private static final int BAR_H = TEX_H / 2;

    /**
     * 贴图里"真正会涨的那一段轨道"的范围。
     *
     * <p>这张图里 {@code empty} 和 {@code fill} <b>只差轨道那一块</b>，
     * 所以最可靠的量法是把两张图逐像素相减、差异像素的包围盒就是轨道：
     * <b>u 46..164, v 13..18</b>（119 x 6 贴图像素）。
     *
     * <p>左边 0..26 是魔法石（27x26）、再往右到 45 是金框和翅膀，那两段永远不动；
     * 轨道最左端（u 46..48）只有 v=18 一行，是画成尖头的引导边 ——
     * 按比例裁剪时必须整段一起裁，否则会把尖头切掉。
     */
    private static final int TRACK_U = 46;
    private static final int TRACK_V = 13;
    private static final int TRACK_W = 119;
    private static final int TRACK_H = 6;

    /** 上面那段轨道换算到游戏坐标（0.5 倍）后在条子里的位置和大小。 */
    private static final int TRACK_DX = TRACK_U / 2;          // 23
    private static final int TRACK_DY = TRACK_V / 2;          // 6
    private static final int TRACK_DW = TRACK_W / 2;          // 59

    /** 原版血条/饱食度那一行的锚点（以屏幕底部为基准）。 */
    private static final int STATUS_ROW_MARGIN = 39;

    /**
     * 这个包的状态条一行有多高（实测 11 像素）—— 包内护甲条在 {@code 高-61}、
     * 血条/饱食度在 {@code 高-50}，差的这 11 就是"往上挪一行"的步长。
     */
    private static final int PACK_BAR_ROW_HEIGHT = 11;

    /** 饱食度那一行贴图框的上沿（实测 高-50）。 */
    private static final int HUNGER_ROW_TOP_MARGIN = STATUS_ROW_MARGIN + PACK_BAR_ROW_HEIGHT;

    /**
     * 魔力条贴图框：紧贴在饱食度贴图框正上方，一像素不差（{@code 高-63 .. 高-50}）。
     *
     * <p>步长用 {@link #BAR_H}（13）而不是那个 11：11 是"轨道"之间的间距，
     * 贴图框本身是 13 高，包里的框就是紧挨着码的（量过护甲框 297..310、
     * 饱食度框 310..323）。所以我们的框正好落在**护甲条那一行的右半边**。
     */
    private static final int MANA_BAR_TOP_MARGIN = HUNGER_ROW_TOP_MARGIN + BAR_H;

    /** 魔力条左边界相对屏幕中心的偏移：左端和饱食度的贴图框对齐（{@code 宽/2 + 11}）。 */
    private static final int MANA_BAR_X_OFFSET = 11;

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
    // 条子本体是贴图画好的（左：魔法石；中：轨道；右：闪电；外面套金框），
    // 我们只负责"按比例把中间那段轨道填起来" + 把数字压上去。
    //
    // 三个关键点：
    //  1. 用**带贴图尺寸的 blit 重载**。不带尺寸的那个假设贴图是 256x256，会采样错位。
    //  2. 贴图按 0.5 倍画（168x26 -> 84x13），和包内血条/饱食度一致，见 BAR_W / BAR_H。
    //     这一步要用带 uWidth/vHeight 的那个重载（目标尺寸和取样尺寸可以不同）。
    //  3. 填充只裁 TRACK_U..TRACK_U+TRACK_W 这一段，别把左边的宝石、右边的闪电切了。
    private static final ResourceLocation MANA_BAR_EMPTY =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/gui/mana_bar/thundermagicbar_empty.png");
    private static final ResourceLocation MANA_BAR_FILL =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/gui/mana_bar/thundermagicbar_fill.png");

    // ---------------- 配色（只给代码画的部分用：中间那颗魔法石图标、条上的数字） ----------------
    //
    // 条子本体已经全是贴图了，这几个颜色现在只管「中间那颗魔法石」和「条上的数字」。
    // 数字要白字带阴影：轨道底色是紫色，白字在任何填充比例下都清楚。
    // 菱形取中高亮度，是因为它可能要压在聊天框暗底上，深紫蒙一层就糊成黑色了。

    /** 中间那颗菱形的外描边。 */
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

        // 右半边：和饱食度同一套贴图规格，紧贴它正上方
        drawManaBar(minecraft, graphics, width / 2 + MANA_BAR_X_OFFSET, height);

        // 这里原来还画一颗居中的魔法石图标（HUD 入口的提示）。
        // 现在条子贴图自己左边就带着一颗魔法石，再画一颗是重复的，所以去掉了。
        // 石头本身的画法还留着给物品栏那个按钮用（MagicStoneButton），
        // HUD 打开界面仍然靠快捷键 V（见 MagicStoneKeys）。

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

        // 1) 整条：贴图 168x26 -> 84x13（0.5 倍），左边宝石、右边闪电、金框都在贴图里
        graphics.blit(MANA_BAR_EMPTY, x, y, BAR_W, BAR_H, 0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);

        // 2) 填充：只取"轨道"那一段（u 42..148 / v 14..19）按比例从左往右裁。
        //    用带 uWidth/vHeight 的重载 —— 它允许"目标宽"和"取样宽"不一样，
        //    所以既能裁剪、又能顺便维持 0.5 倍缩放。
        //    宽度按屏幕像素取整（= 半个贴图像素），所以一格一格地涨。
        int filled = (int) Math.round(TRACK_DW * (double) mana / max);
        if (filled > 0) {
            graphics.blit(MANA_BAR_FILL,
                    x + TRACK_DX, y + TRACK_DY,
                    filled, TRACK_H / 2,
                    TRACK_U, TRACK_V, filled * 2, TRACK_H,
                    TEX_W, TEX_H);
        }

        // 3) 数字：压在轨道正中间，白字带阴影 ——
        //    轨道底色是紫的，白字在任何填充比例下都清晰。
        //    中线按**贴图坐标**算再除以 2，比先用 0.5 倍取整更准。
        String text = mana + "/" + max;
        var font = minecraft.font;
        int trackCx = x + (TRACK_U + TRACK_W / 2) / 2;
        int trackCy = y + (TRACK_V + TRACK_H / 2) / 2;
        graphics.drawString(font, text, trackCx - font.width(text) / 2,
                trackCy - font.lineHeight / 2 + 1, COLOR_TEXT, true);
    }

    /**
     * 画一颗菱形宝石（由外到内三层，越里面越亮）。
     *
     * <p>现在只有<b>物品栏界面</b>那个入口按钮在用它（HUD 上那颗已经去掉了：
     * 条子贴图左边本来就画着一颗，再画一颗是重复的）。
     *
     * @param hovered 高亮（鼠标悬停时更亮，物品栏那个按钮用得上）
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
