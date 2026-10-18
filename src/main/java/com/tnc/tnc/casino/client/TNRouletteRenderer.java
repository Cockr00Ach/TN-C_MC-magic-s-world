package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.casino.machine.TNCasinoMachineBlock;
import com.tnc.tnc.casino.machine.TNRouletteTableEntity;
import com.tnc.tnc.casino.machine.TNMachineEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 转盘赌桌的自绘渲染器 ✓ —— <b>转盘真的在转、球真的在跑、骰子真的在滚</b> ✓。
 *
 * <h2>★ 2026-10-08 重做（作者：「转盘赌，怎么没有<b>数字转盘</b>啊」✗）</h2>
 * 第一版只有"24 片扇形拼一个圆 ＋ 红黑交替" ✓ ——
 * 远看就是一块**彩色圆饼** ✗，完全读不出"这是个轮盘" ✗。这一版照真实轮盘的层次重做 ✓：
 *
 * <pre>
 *   从外到内（俯视）：
 *   ① 木框包边        —— 桌子外圈 ✓
 *   ② 球道 BALL_TRACK —— 小球先在外圈跑的那条环 ✓（直径最大 ✓）
 *   ③ 挡圈 STUD_RING  —— 一圈黄铜小柱 ＝ 真实轮盘的挡珠棱 ✓
 *   ④ 号码环 POCKET_R —— ★ 36 格红黑交替 ＋ 1 格绿 0 ✓（**"数字转盘"就在这一层** ✓）
 *   ⑤ 分格棱          —— 每格之间插一根黄铜细柱 ✓（数字之间的隔断 ✓）
 *   ⑥ 内碗/锥心       —— 木色 ＋ 黄铜轴心 ✓
 * </pre>
 *
 * <p>★ <b>"号码"用什么表示</b> ✗：自绘**画不出字形** ✓（没有字体、也不该为此做贴图 ✗）。
 * 所以用**真实轮盘上本来就有的视觉特征**来表达"这里有号码" ✓：
 * <b>一格一色 ＋ 格间有棱 ＋ 绿色单 0 ＋ 一圈小刻度柱</b> ✓ ——
 * 这是轮盘最容易被认出来的样子 ✓。真要印上数字，得等作者给贴图 ✓（见交接文档 ✓）。
 *
 * <h2>动画（客户端插值 ✓，每 tick 零网络包 ✓）</h2>
 * <pre>
 *   转盘：恒速转，末段 easeOutCubic 收尾到落袋角
 *   小球：先沿球道快跑，后 45% 半径收到袋圈、并随盘一起转
 *   骰子：绕 X 与 Z 一起滚，两颗错开停
 * </pre>
 */
public final class TNRouletteRenderer implements BlockEntityRenderer<TNRouletteTableEntity> {

    // ------------------------------------------------------------------
    //  桌子（格；原点 = 方块最小角；正面朝 −Z ✓）  整机 2 × 1 × 2 ✓
    // ------------------------------------------------------------------

    private static final float TOP = 0.90F;

    /** 转盘轴心（桌面左半边 ✓）。 */
    private static final float WHEEL_X = 0.86F;
    private static final float WHEEL_Z = 1.00F;

    /** ★ 四层半径（从外到内 ✓）—— 改这几个数就能整体放大/缩小轮盘 ✓。 */
    private static final float BOWL_R = 0.66F;        // 凹槽外沿
    private static final float BALL_TRACK_R = 0.58F;  // 小球跑的环
    private static final float STUD_RING_R = 0.48F;   // 挡珠棱
    private final net.minecraft.client.gui.Font font;
    private static final float POCKET_R = 0.42F;      // ★ 号码环（36+1 格）

    /** 号码格数：真实轮盘是 36 格 ＋ 1 个绿 0 ✓。 */
    private static final int NUMBER_POCKETS = 36;
    /** 加上 0 ⇒ 一共 37 格 ✓（渲染时 0 占一格 ✓）。 */
    private static final int TOTAL_POCKETS = NUMBER_POCKETS + 1;
    /** 圆环段数：格数 × 这个倍数 ⇒ 每格由整数段拼成 ✓（取 2 ✓）。 */
    private static final int SEGMENTS_PER_POCKET = 2;

    /** 骰子桌位（桌面右半边 ✓）。 */
    private static final float[] DIE_X = {1.50F, 1.74F};
    private static final float DIE_Z = 1.00F;

    private static final List<Part> PARTS = buildParts();

    public TNRouletteRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
        // 纯自绘 ⇒ 不需要 ItemRenderer ✓
    }

    // ------------------------------------------------------------------
    //  静态桌体
    // ------------------------------------------------------------------

    private record Part(Vector3f pos, Vector3f size, int color) {
    }

    private static void add(List<Part> out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color) {
        out.add(new Part(new Vector3f(x0, y0, z0),
                new Vector3f(x1 - x0, y1 - y0, z1 - z0), color));
    }

    private static List<Part> buildParts() {
        List<Part> out = new ArrayList<>();

        // ---- 台呢桌面 ----
        add(out, 0.10F, 0.86F, 0.10F, 1.90F, 0.90F, 1.90F, CasinoPalette.FELT);
        // ---- 黄铜包边（四条 ✓）----
        add(out, 0.06F, 0.85F, 0.06F, 1.94F, 0.93F, 0.14F, CasinoPalette.BRASS);
        add(out, 0.06F, 0.85F, 1.86F, 1.94F, 0.93F, 1.94F, CasinoPalette.BRASS);
        add(out, 0.06F, 0.85F, 0.14F, 0.14F, 0.93F, 1.86F, CasinoPalette.BRASS);
        add(out, 1.86F, 0.85F, 0.14F, 1.94F, 0.93F, 1.86F, CasinoPalette.BRASS);

        // ---- 四条腿 ----
        add(out, 0.16F, 0.00F, 0.16F, 0.30F, 0.84F, 0.30F, CasinoPalette.WOOD_DARK);
        add(out, 1.70F, 0.00F, 0.16F, 1.84F, 0.84F, 0.30F, CasinoPalette.WOOD_DARK);
        add(out, 0.16F, 0.00F, 1.70F, 0.30F, 0.84F, 1.84F, CasinoPalette.WOOD_DARK);
        add(out, 1.70F, 0.00F, 1.70F, 1.84F, 0.84F, 1.84F, CasinoPalette.WOOD_DARK);
        // ---- 踢脚横档 ----
        add(out, 0.22F, 0.10F, 0.22F, 1.78F, 0.20F, 1.78F, CasinoPalette.WOOD);

        // ---- 转盘凹槽（深色碗底 ＋ 木色碗壁 ✓）----
        // ⚠️ 写字面量（0.20 = WHEEL_X−BOWL_R = 0.86−0.66 ✓，1.52 = +BOWL_R ✓）——
        //    自检脚本扫字面量 ✓，写算式那些盒子会在报告里消失 ✓。
        add(out, 0.20F, 0.84F, 0.34F, 1.52F, 0.902F, 1.66F, CasinoPalette.WOOD_DARK);

        // ---- 投币口（正面靠右 ✓，因为左边被轮盘占了 ✓）----
        add(out, 1.52F, 0.91F, 0.14F, 1.72F, 0.93F, 0.24F, CasinoPalette.BRASS);
        add(out, 1.54F, 0.91F, 0.16F, 1.70F, 0.925F, 0.22F, CasinoPalette.WELL);

        // ---- 显示板（骰子结果下面那块黄铜底 ✓）----
        add(out, 1.34F, 0.905F, 0.62F, 1.90F, 0.915F, 1.38F, CasinoPalette.WOOD_DARK);

        // Match the table series: padded burgundy rail, slim gold piping and walnut apron.
        add(out, 0.02F, 0.86F, 0.02F, 1.98F, 0.95F, 0.08F, CasinoPalette.CARD_BACK);
        add(out, 0.02F, 0.86F, 1.92F, 1.98F, 0.95F, 1.98F, CasinoPalette.CARD_BACK);
        add(out, 0.02F, 0.86F, 0.08F, 0.08F, 0.95F, 1.92F, CasinoPalette.CARD_BACK);
        add(out, 1.92F, 0.86F, 0.08F, 1.98F, 0.95F, 1.92F, CasinoPalette.CARD_BACK);
        add(out, 0.12F, 0.66F, 0.12F, 1.88F, 0.84F, 1.88F, CasinoPalette.WOOD);
        add(out, 0.12F, 0.69F, 0.10F, 1.88F, 0.705F, 0.12F, CasinoPalette.BRASS_DARK);
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------
    //  渲染
    // ------------------------------------------------------------------

    @Override
    public void render(TNRouletteTableEntity table, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (table.getLevel() == null) return;

        final int packed = light;
        VertexConsumer buffer = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TNCasinoGeometry.white()));

        Direction facing = table.getBlockState().hasProperty(TNCasinoMachineBlock.FACING)
                ? table.getBlockState().getValue(TNCasinoMachineBlock.FACING) : Direction.NORTH;

        long gameTime = table.getLevel().getGameTime();
        float elapsed = (gameTime - table.startedAt()) + partialTick;

        pose.pushPose();
        // 2×2 占地 ⇒ 绕 (1,0,1) 转 ✓
        pose.translate(4.0D, 0.0D, 2.0D);
        pose.mulPose(Axis.YP.rotationDegrees(TNCasinoGeometry.yawFor(facing)));
        pose.translate(-4.0D, 0.0D, -2.0D);
        CasinoFurniture.table(pose,buffer,8,4,TOP,packed);

        float wheelSpin = wheelAngle(table, elapsed);
        // Enlarge the circular assembly equally in X/Z, independently of the 8x4 frame.
        pose.pushPose();pose.translate(.28F,0,0);pose.scale(2,1,2);
        drawWheel(pose, buffer, wheelSpin, packed);
        drawBall(pose, buffer, table, elapsed, wheelSpin, packed);
        pose.popPose();

        for(int button=0;button<3;button++) {
            float z=0.9F+button*0.85F;
            int color=button==0?CasinoPalette.CHIP_RED:button==1?CasinoPalette.FELT:CasinoPalette.PIP;
            TNCasinoGeometry.box(pose,buffer,5.6F,0.96F,z,7.4F,1.0F,z+0.7F,CasinoPalette.BRASS_DARK,packed);
            TNCasinoGeometry.box(pose,buffer,5.64F,1.0F,z+0.04F,7.36F,1.025F,z+0.66F,color,packed);
            if(table.selectedBet()==com.tnc.tnc.casino.machine.RouletteControls.BETS[button])
                CasinoFurniture.bettingBox(pose,buffer,5.599F,1.027F,z-0.001F,1.802F,0.702F,packed);
        }

        pose.pushPose();pose.translate(4,0,0);drawLamps(pose, buffer, table, elapsed, packed);pose.popPose();
        pose.pushPose();
        pose.translate(.28F,0,0);pose.scale(2,1,2);
        pose.translate(WHEEL_X,0.946F,WHEEL_Z);
        pose.mulPose(Axis.YP.rotationDegrees(wheelSpin));
        for (int pocket=0;pocket<TOTAL_POCKETS;pocket++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees((pocket+0.25F)*360.0F/TOTAL_POCKETS));
            pose.translate(0,0,-POCKET_R*0.98F);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(0.006F,-0.006F,0.006F);
            String number=Integer.toString(pocket);
            font.drawInBatch(number,-font.width(number)/2.0F,0,0xFFF5DF,false,pose.last().pose(),
                    buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,packed);
            pose.popPose();
        }
        pose.popPose();
        for(int button=0;button<3;button++) CasinoTableDetails.label(font,pose,buffers,
                com.tnc.tnc.casino.machine.RouletteControls.LABELS[button],6.5F,1.03F,1.14F+button*0.85F,packed,
                0xFFF1D3,0.012F);
        CasinoTableDetails.label(font,pose,buffers,"轮盘 · " + table.betName(),4,0.98F,0.45F,packed);
        if (!table.spinning() && table.spins()>0) CasinoTableDetails.label(font,pose,buffers,
                "落号 " + table.result(0) + " · 返回 " + table.lastPayout(),4,0.98F,3.56F,packed);

        pose.popPose();
    }

    /** 转盘角度 ✓ —— 转动时恒速，末段 easeOut 收尾到目标袋 ✓。 */
    private static float wheelAngle(TNRouletteTableEntity table, float elapsed) {
        float perPocket = 360.0F / TOTAL_POCKETS;
        float target = 360.0F * 4 + perPocket * (table.result(0)+0.25F);
        if (table.phase() != TNMachineEntity.Phase.SPINNING) return target;
        float t = Mth.clamp(elapsed / table.clientSpinTicks(), 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        return target * eased;
    }

    /**
     * ★ 轮盘本体 —— <b>四层圆环</b> ✓（球道 / 挡珠棱 / 号码环 / 内碗 ✓），整体一起转 ✓。
     */
    private static void drawWheel(PoseStack pose, VertexConsumer buffer, float spin, int light) {
        Vector3f axis = new Vector3f(WHEEL_X, 0.0F, WHEEL_Z);
        final int ringSegments = TOTAL_POCKETS * SEGMENTS_PER_POCKET;

        pose.pushPose();
        pose.translate(WHEEL_X, 0.93F, WHEEL_Z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        Vector3f local = new Vector3f(0.0F, 0.0F, 0.0F);

        // ① 球道（最外圈，凹陷的木色环 ✓）
        TNCasinoGeometry.cylinder(pose, buffer, local, BALL_TRACK_R, 0.012F, 0.115F,
                ringSegments, 0.0F, CasinoPalette.WOOD_DARK, light, null);

        // ② 挡珠棱（球道与号码环之间那一圈黄铜小柱 ✓）
        TNCasinoGeometry.studs(pose, buffer, local, STUD_RING_R, 0.028F, 0.055F,
                TOTAL_POCKETS, 0.0F, CasinoPalette.BRASS, light);

        // ③ ★ 号码环：37 格，0 号绿、其余红黑交替 ✓
        TNCasinoGeometry.cylinder(pose, buffer, local, POCKET_R, 0.022F, 0.10F,
                ringSegments, 0.0F, CasinoPalette.CARD_FACE, light,
                segment -> {
                    int pocket = (segment / SEGMENTS_PER_POCKET) % TOTAL_POCKETS;
                    return pocketColor(pocket);
                });

        // ④ 格间分格棱（每格一根黄铜细柱 ⇒ "一格一号码"的视觉 ✓）
        TNCasinoGeometry.studs(pose, buffer, local, POCKET_R * 0.985F, 0.016F, 0.048F,
                TOTAL_POCKETS, 0.0F, CasinoPalette.BRASS_DARK, light);

        // ⑤ 内碗（木色锥心 ✓）
        TNCasinoGeometry.cylinder(pose, buffer, local, POCKET_R * 0.72F, 0.020F, 0.10F,
                ringSegments, 0.0F, CasinoPalette.WOOD_TOP, light, null);

        // ⑥ 黄铜轴心 ＋ 一圈铆钉（让盘心不空 ✓）
        TNCasinoGeometry.cylinder(pose, buffer, local, 0.15F, 0.055F, 0.16F,
                12, 0.0F, CasinoPalette.BRASS_DARK, light, null);
        TNCasinoGeometry.studs(pose, buffer, local, 0.11F, 0.03F, 0.06F, 8, 22.5F,
                CasinoPalette.BRASS, light);

        pose.popPose();
    }

    /**
     * 号码格的颜色 ✓ —— <b>0 号绿、其余红黑交替</b> ✓
     * （真实轮盘的红黑排布是固定的 ✓；这里只要"红黑相间 ＋ 一个绿 0"就足够被认出来 ✓）。
     */
    private static int pocketColor(int pocket) {
        if (pocket == 0) return CasinoPalette.FELT;          // 绿 0（庄家位 ✓）
        return com.tnc.tnc.casino.machine.CasinoRules.red(pocket) ? CasinoPalette.CHIP_RED : CasinoPalette.PIP;
    }

    /**
     * 小球：转动时沿<b>球道</b>快跑 ✓，末段半径收到<b>号码环</b>里、并随盘一起转 ✓
     * —— 看起来就是"球掉进那个格、随盘一起转" ✓。
     */
    private static void drawBall(PoseStack pose, VertexConsumer buffer, TNRouletteTableEntity table,
                                 float elapsed, float wheelSpin, int light) {
        boolean spinning = table.phase() == TNMachineEntity.Phase.SPINNING;
        float radius;
        float angle;
        float perPocket = 360.0F / TOTAL_POCKETS;
        if (spinning) {
            float t = Mth.clamp(elapsed / table.clientSpinTicks(), 0.0F, 1.0F);
            float fall = Mth.clamp((t - 0.55F) / 0.45F, 0.0F, 1.0F);
            radius = Mth.lerp(fall, BALL_TRACK_R - 0.04F, POCKET_R * 0.94F);
            angle = 360.0F * 9 * t + wheelSpin + perPocket * (table.result(0)+0.25F);
        } else {
            radius = POCKET_R * 0.94F;
            angle = wheelSpin + perPocket * (table.result(0)+0.25F);
        }
        double rad = Math.toRadians(angle);
        float x = WHEEL_X + -(float) Math.sin(rad) * radius;
        float z = WHEEL_Z + -(float) Math.cos(rad) * radius;
        TNCasinoGeometry.box(pose, buffer,
                new Vector3f(x - 0.028F, 0.944F, z - 0.028F),
                new Vector3f(0.056F, 0.056F, 0.056F), 0xF7F3E8, light);
    }

    /**
     * 骰子：绕 X 与 Z 一起滚 ✓，末段收尾停住 ✓。
     * 点数用"顶面画 N 个深色点块"表达 ✓（自绘没有字形 ✗，点块是最省事又看得懂的做法 ✓）。
     */
    private static void drawDie(PoseStack pose, VertexConsumer buffer, TNRouletteTableEntity table,
                                int die, float elapsed, int light) {
        float stopAt = table.clientSpinTicks() - 10.0F * (1 - die);
        boolean spinning = table.phase() == TNMachineEntity.Phase.SPINNING;

        float tumble = 0.0F;
        if (spinning && elapsed < stopAt) {
            float t = Mth.clamp(elapsed / stopAt, 0.0F, 1.0F);
            tumble = 360.0F * 3 * (1.0F - (1.0F - t) * (1.0F - t));
        }

        int face = table.result(1 + die) + 1;
        float x = DIE_X[die];
        pose.pushPose();
        pose.translate(x, 0.985F, DIE_Z);
        pose.mulPose(Axis.XP.rotationDegrees(tumble * 0.7F));
        pose.mulPose(Axis.ZP.rotationDegrees(tumble));
        TNCasinoGeometry.box(pose, buffer,
                new Vector3f(-0.075F, -0.075F, -0.075F), new Vector3f(0.15F, 0.15F, 0.15F),
                CasinoPalette.DICE, light);
        drawPips(pose, buffer, face, light);
        pose.popPose();
    }

    /** 把一个面的点数画出来 ✓（1..6 的常见九宫格排布 ✓）。 */
    private static void drawPips(PoseStack pose, VertexConsumer buffer, int face, int light) {
        float s = 0.042F;
        float[][] layout = switch (face) {
            case 1 -> new float[][]{{0, 0}};
            case 2 -> new float[][]{{-1, -1}, {1, 1}};
            case 3 -> new float[][]{{-1, -1}, {0, 0}, {1, 1}};
            case 4 -> new float[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            case 5 -> new float[][]{{-1, -1}, {-1, 1}, {0, 0}, {1, -1}, {1, 1}};
            default -> new float[][]{{-1, -1}, {-1, 0}, {-1, 1}, {1, -1}, {1, 0}, {1, 1}};
        };
        for (float[] pip : layout) {
            TNCasinoGeometry.box(pose, buffer,
                    new Vector3f(pip[0] * s - 0.014F, 0.0755F, pip[1] * s - 0.014F),
                    new Vector3f(0.028F, 0.006F, 0.028F), CasinoPalette.PIP, light);
        }
    }

    /** 结果灯：沿正面桌沿排三盏 ✓。 */
    private static void drawLamps(PoseStack pose, VertexConsumer buffer, TNRouletteTableEntity table,
                                  float elapsed, int light) {
        boolean spinning = table.phase() == TNMachineEntity.Phase.SPINNING;
        boolean won = table.phase() == TNMachineEntity.Phase.RESULT && table.lastPayout() > 0;
        for (int i = 0; i < 3; i++) {
            int color;
            if (spinning) {
                int phase = (int) (elapsed / 4.0F) % 3;
                color = i == phase ? CasinoPalette.LAMP_ON : CasinoPalette.LAMP_OFF;
            } else if (won) {
                color = ((int) (elapsed / 3.0F)) % 2 == 0 ? CasinoPalette.LAMP_WIN : CasinoPalette.LAMP_ON;
            } else {
                color = CasinoPalette.LAMP_OFF;
            }
            TNCasinoGeometry.box(pose, buffer,
                    new Vector3f(1.42F + i * 0.20F, 0.905F, 0.16F),
                    new Vector3f(0.14F, 0.018F, 0.06F), color, light);
        }
    }
}
