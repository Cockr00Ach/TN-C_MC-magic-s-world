package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.casino.machine.TNCasinoMachineBlock;
import com.tnc.tnc.casino.machine.TNSlotMachineEntity;
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
 * 老虎机的自绘渲染器 ✓ —— <b>1 格宽 × 2 格高 × 1 格深</b>的经典立式老虎机 ✓。
 *
 * <h2>★ 2026-10-08 重做（作者三条反馈，逐条对应 ✗）</h2>
 * <table>
 *   <tr><th>作者原话</th><th>改了什么</th></tr>
 *   <tr><td>「我要<b>高 2 宽 1</b> 的那种」</td>
 *       <td>占地从 2×2 改成 <b>1×1、高 2 格</b> ✓（碰撞箱也跟着改 ✓，见 {@code TNSlotMachineBlock} 的宽度参数 ✓）</td></tr>
 *   <tr><td>「右边有一个<b>红红的拉杆</b>的经典老虎机」</td>
 *       <td>右侧一根<b>黄铜杆 ＋ 红球头</b>的拉杆 ✓，转起来会往下扳 ✓（{@link #drawLever}）</td></tr>
 *   <tr><td>「中间旋转的三个你也应该转的是一个<b>圆环</b>啊，怎么在那里转一个<b>片</b>」</td>
 *       <td>★ 这是真 bug ✗：原来滚轮是<b>一块平板</b>在上下平移 ✓ —— 看着当然像"片" ✗。
 *           现在换成<b>真正的圆柱滚筒</b> ✓：12 块平板围成一圈（{@code TNCasinoGeometry.cylinder} ✓），
 *           每个符号是筒面上的一格 ✓，转动 = 绕轴转整圈 ✓。</td></tr>
 * </table>
 *
 * <h2>动画怎么算（和以前一样：客户端插值 ✓，每 tick 零网络包 ✓）</h2>
 * <pre>
 *   elapsed = gameTime + partialTick − startedAt
 *   转动中：角度从 0 匀加速推到 SPIN_CYCLES 圈 ＋ 目标符号
 *   停轮：  每个滚筒有自己的停轮时刻，用 easeOutCubic 收尾
 * </pre>
 */
public final class TNSlotMachineRenderer implements BlockEntityRenderer<TNSlotMachineEntity> {

    // ------------------------------------------------------------------
    //  机器尺寸（格；原点 = 方块最小角；正面朝 −Z ✓）
    //  整机 1.0 宽 × 2.0 高 × 1.0 深 ✓
    // ------------------------------------------------------------------

    /**
     * 整机高 2 格 ✓（占地 1×1，见 {@code TNSlotMachineBlock} 传来的尺寸 ✓）。
     * ⚠️ 这里不复述宽度/深度 ✗ —— 真正生效的是方块那边的碰撞箱 ✓，
     * 渲染只要跟它对齐就行 ✓（写成常量只是给下面那些坐标当参照 ✓）。
     */
    private static final float H = 2.00F;

    /** 柜体前后面（正面在 z = FRONT ✓，背面在 BACK ✓）。 */
    private static final float FRONT = 0.06F;
    private static final float BACK = 0.94F;

    /** 三只滚筒的轴心高度与 X ✓（横轴滚动的圆筒）。 */
    private static final float REEL_Y = 1.30F;
    private static final float[] REEL_X = {0.28F, 0.50F, 0.72F};

    /** 滚筒半径 / 高 / 段数 ✓。 */
    private static final float REEL_R = 0.16F;
    private static final float REEL_H = 0.18F;
    private static final int REEL_SEGMENTS = 12;

    /** 观察窗（正面挖开的那一块 ✓）。 */
    private static final float WIN_LEFT = 0.15F;
    private static final float WIN_RIGHT = 0.85F;
    private static final float WIN_BOTTOM = 1.12F;
    private static final float WIN_TOP = 1.48F;

    /** 转动圈数 ✓。 */
    private static final int SPIN_CYCLES = 7;

    /** 每只滚筒错开停的 tick ✓（第三只最晚 ✓，"滴、滴、滴" ✓）。 */
    private static final int REEL_TAIL = 6;

    private static final List<Part> PARTS = buildParts();

    public TNSlotMachineRenderer(BlockEntityRendererProvider.Context context) {
        // 纯自绘 ⇒ 不需要 ItemRenderer ✓
    }

    // ------------------------------------------------------------------
    //  静态柜体
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

        // ---- 底座（略微外扩，让机器站得住 ✓）----
        add(out, 0.02F, 0.00F, 0.02F, 0.98F, 0.07F, 0.98F, CasinoPalette.CABINET_DARK);

        // ---- 柜体四壁（中间留空给滚筒 ✓）----
        //
        // ⚠️ 这一段**六个坐标全部写字面量** ✅，不写 FRONT/BACK/WIN_* 这些常量 ✗ ——
        //    自检脚本 tools/casino-machines.mjs 是**扫字面量**把几何读出来的 ✅，
        //    写成常量/算式那些盒子会在报告里静默消失 ✅（它现在会打 WARNING 提醒 ✅）。
        //    当前值：FRONT=0.06 BACK=0.94 窗口=0.15..0.85 / 1.12..1.48 ✅
        add(out, 0.06F, 0.07F, 0.06F, 0.15F, 1.82F, 0.94F, CasinoPalette.CABINET);      // 左壁
        add(out, 0.85F, 0.07F, 0.06F, 0.94F, 1.82F, 0.94F, CasinoPalette.CABINET);      // 右壁
        add(out, 0.15F, 0.07F, 0.86F, 0.85F, 1.82F, 0.94F, CasinoPalette.CABINET);      // 背板
        add(out, 0.15F, 0.07F, 0.06F, 0.85F, 1.12F, 0.86F, CasinoPalette.CABINET);      // 窗口以下
        add(out, 0.15F, 1.48F, 0.06F, 0.85F, 1.82F, 0.86F, CasinoPalette.CABINET);      // 窗口以上
        add(out, 0.15F, 1.12F, 0.10F, 0.15F, 1.48F, 0.86F, CasinoPalette.CABINET);      // 窗口左墙
        add(out, 0.85F, 1.12F, 0.10F, 0.85F, 1.48F, 0.86F, CasinoPalette.CABINET);      // 窗口右墙

        // ---- 顶盖 ＋ 招牌（发光板 ✓）----
        add(out, 0.03F, 1.82F, 0.03F, 0.97F, 1.90F, 0.97F, CasinoPalette.CABINET_TOP);
        add(out, 0.02F, 1.90F, 0.02F, 0.98F, 1.96F, 0.98F, CasinoPalette.BRASS);

        // ---- 正面腰线 / 台面 ----
        add(out, 0.06F, 1.12F, 0.055F, 0.94F, 1.15F, 0.07F, CasinoPalette.BRASS);
        add(out, 0.06F, 1.06F, 0.055F, 0.94F, 1.09F, 0.07F, CasinoPalette.BRASS_DARK);

        // ---- 顶部三盏结果灯（凹槽 ＋ 灯面 ✓）----
        add(out, 0.16F, 1.62F, 0.050F, 0.32F, 1.70F, 0.075F, CasinoPalette.WELL);
        add(out, 0.42F, 1.62F, 0.050F, 0.58F, 1.70F, 0.075F, CasinoPalette.WELL);
        add(out, 0.68F, 1.62F, 0.050F, 0.84F, 1.70F, 0.075F, CasinoPalette.WELL);

        // ---- 投币口（正面中段 ✓）----
        add(out, 0.40F, 1.00F, 0.050F, 0.60F, 1.03F, 0.075F, CasinoPalette.BRASS);
        add(out, 0.43F, 1.005F, 0.052F, 0.57F, 1.025F, 0.078F, CasinoPalette.WELL);

        // ---- 出币槽（正面下段 ✓）----
        add(out, 0.28F, 0.16F, 0.050F, 0.72F, 0.34F, 0.075F, CasinoPalette.WELL);
        add(out, 0.26F, 0.34F, 0.045F, 0.74F, 0.38F, 0.080F, CasinoPalette.BRASS_DARK);

        // ---- 拉杆底座（右侧外壁 ✓）----
        add(out, 0.94F, 1.30F, 0.30F, 0.99F, 1.44F, 0.44F, CasinoPalette.IRON);

        // ---- 四角黄铜护条（让 1 格宽也不显单薄 ✓）----
        add(out, 0.06F, 0.07F, 0.06F, 0.09F, 1.82F, 0.08F, CasinoPalette.BRASS_DARK);
        add(out, 0.91F, 0.07F, 0.06F, 0.94F, 1.82F, 0.08F, CasinoPalette.BRASS_DARK);
        add(out, 0.06F, 0.07F, 0.86F, 0.09F, 1.82F, 0.88F, CasinoPalette.BRASS_DARK);
        add(out, 0.91F, 0.07F, 0.86F, 0.94F, 1.82F, 0.88F, CasinoPalette.BRASS_DARK);

        // Walnut panel with burgundy inset, fine gold lines and an ivory marquee.
        add(out, 0.18F, 0.44F, 0.047F, 0.82F, 0.94F, 0.058F, CasinoPalette.CARD_BACK);
        add(out, 0.21F, 0.47F, 0.036F, 0.79F, 0.485F, 0.049F, CasinoPalette.BRASS);
        add(out, 0.21F, 0.89F, 0.036F, 0.79F, 0.905F, 0.049F, CasinoPalette.BRASS);
        add(out, 0.21F, 0.47F, 0.036F, 0.225F, 0.905F, 0.049F, CasinoPalette.BRASS);
        add(out, 0.775F, 0.47F, 0.036F, 0.79F, 0.905F, 0.049F, CasinoPalette.BRASS);
        add(out, 0.35F, 0.66F, 0.032F, 0.65F, 0.73F, 0.044F, CasinoPalette.BRASS_DARK);
        add(out, 0.18F, 1.75F, 0.045F, 0.82F, 1.79F, 0.058F, CasinoPalette.CARD_FACE);
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------
    //  渲染
    // ------------------------------------------------------------------

    @Override
    public void render(TNSlotMachineEntity machine, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (machine.getLevel() == null) return;

        final int packed = light;
        VertexConsumer buffer = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TNCasinoGeometry.white()));

        Direction facing = machine.getBlockState().hasProperty(TNCasinoMachineBlock.FACING)
                ? machine.getBlockState().getValue(TNCasinoMachineBlock.FACING) : Direction.NORTH;

        long gameTime = machine.getLevel().getGameTime();
        float elapsed = (gameTime - machine.startedAt()) + partialTick;

        pose.pushPose();
        // ★ 1×1 占地 ⇒ 绕自己的中心 (0.5, 0.5) 转就够 ✓（不用像 2×2 那样绕 1.0 ✓）
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(TNCasinoGeometry.yawFor(facing)));
        pose.translate(-0.5D, 0.0D, -0.5D);

        for (Part part : PARTS) {
            TNCasinoGeometry.box(pose, buffer, part.pos(), part.size(), part.color(), packed);
        }

        // 滚筒（真正的圆筒 ✓）
        for (int reel = 0; reel < TNSlotMachineEntity.REELS; reel++) {
            drawReel(pose, buffer, machine, reel, elapsed, packed);
        }

        drawLamps(pose, buffer, machine, elapsed, packed);
        drawLever(pose, buffer, machine, elapsed, packed);

        pose.popPose();
    }

    /**
     * ★ 一只滚轮 —— <b>真正的圆柱滚筒</b> ✓（作者：「怎么在那里转一个片啊」✗）。
     *
     * <p>做法：把 {@link #REEL_SEGMENTS} 块平板围着轴心摆一圈 ✓（{@code cylinder} ✓），
     * 每块板的正面涂成它那一格的符号色 ✓；<b>转动 = 绕 X 轴转整圈</b> ✓。
     * 于是"停下时正面那一格"就是结果 ✓ —— 角度与符号的换算写在下面 ✓。
     */
    private void drawReel(PoseStack pose, VertexConsumer buffer, TNSlotMachineEntity machine,
                          int reel, float elapsed, int light) {
        int symbols = TNSlotMachineEntity.SYMBOLS;
        Vector3f axis = new Vector3f(REEL_X[reel], REEL_Y, FRONT + REEL_R + 0.02F);

        // 这只滚筒什么时候停、停在哪个符号 ✓
        float stopAt = machine.clientSpinTicks() - (TNSlotMachineEntity.REELS - 1 - reel) * REEL_TAIL;
        int target = machine.result(reel);
        // 每格 = 360/segments 度；要让"能看见的那一面"正好是 target ✓
        float perSymbol = 360.0F / symbols;
        float targetDeg = SPIN_CYCLES * 360.0F + perSymbol * target + 15.0F;

        float degrees;
        if (elapsed >= stopAt) {
            degrees = targetDeg;
        } else {
            float t = Mth.clamp(elapsed / stopAt, 0.0F, 1.0F);
            float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            degrees = targetDeg * eased;
        }

        // Rear liner must be behind the reels, never in front of their visible faces.
        TNCasinoGeometry.box(pose, buffer,
                new Vector3f(REEL_X[reel]-0.105F, WIN_BOTTOM, axis.z+REEL_R+0.03F),
                new Vector3f(0.21F, WIN_TOP-WIN_BOTTOM, 0.02F), CasinoPalette.WELL, light);
        pose.pushPose();
        pose.translate(axis.x,axis.y,axis.z);
        // Cylinder's local Y axle becomes the classic reel's horizontal X axle.
        pose.mulPose(Axis.ZP.rotationDegrees(90));
        Vector3f local=new Vector3f(0,0,0);
        TNCasinoGeometry.cylinder(pose, buffer, local, REEL_R, REEL_H, 0.02F,
                REEL_SEGMENTS, -degrees, CasinoPalette.DRUM, light,
                segment -> CasinoPalette.symbol(Math.floorMod(segment/(REEL_SEGMENTS/symbols),symbols)));
        for (float edge : new float[]{-REEL_H*0.5F,REEL_H*0.5F}) {
            TNCasinoGeometry.studs(pose,buffer,new Vector3f(0,edge,0),REEL_R,
                    0.015F,0.018F,REEL_SEGMENTS,-degrees,CasinoPalette.BRASS_DARK,light);
        }
        pose.popPose();
    }

    /** 结果灯：三只滚筒各一盏 ✓ —— 转动时流灯、出结果后按赢没赢亮 ✓。 */
    private void drawLamps(PoseStack pose, VertexConsumer buffer, TNSlotMachineEntity machine,
                           float elapsed, int light) {
        boolean spinning = machine.phase() == TNMachineEntity.Phase.SPINNING;
        boolean won = isWin(machine);
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
            float cx = 0.24F + i * 0.26F;
            TNCasinoGeometry.box(pose, buffer,
                    new Vector3f(cx - 0.07F, 1.63F, 0.053F),
                    new Vector3f(0.14F, 0.06F, 0.02F), color, light);
        }
    }

    /** 中奖判定（读的是服务端同步下来的结果下标 ✓）。 */
    private static boolean isWin(TNSlotMachineEntity machine) {
        int a = machine.result(0), b = machine.result(1), c = machine.result(2);
        return a == b || b == c || a == c;
    }

    /**
     * ★ 拉杆（作者：「右边有一个红红的拉杆」✓）。
     *
     * <p>黄铜杆 ＋ 红球头 ✓，挂在右侧外壁的底座上 ✓；
     * 转动时先<b>往下扳</b>再弹回 ✓（纯表现 ✓，与结算无关 ✓）。
     */
    private void drawLever(PoseStack pose, VertexConsumer buffer, TNSlotMachineEntity machine,
                           float elapsed, int light) {
        float angle;
        if (machine.phase() == TNMachineEntity.Phase.SPINNING) {
            // 0 → 58° → 0 ✓（前 20 tick 扳下去再回来 ✓）
            angle = Mth.sin(Mth.clamp(elapsed / 20.0F, 0.0F, 1.0F) * Mth.PI) * 58.0F;
        } else {
            angle = 0.0F;
        }

        // 底座在右侧外壁 (0.965, 1.37, 0.37) ✓；杆从这里往上支出去 ✓
        pose.pushPose();
        pose.translate(0.965F, 1.37F, 0.37F);
        // 绕 Z 转 ⇒ 在 XY 平面里往外侧扳 ✓
        pose.mulPose(Axis.ZP.rotationDegrees(angle));

        // 杆身（往 +Y 长 ✓）
        TNCasinoGeometry.box(pose, buffer,
                new Vector3f(-0.018F, 0.0F, -0.018F), new Vector3f(0.036F, 0.34F, 0.036F),
                CasinoPalette.BRASS, light);
        // 红球头 ✓
        TNCasinoGeometry.box(pose, buffer,
                new Vector3f(-0.042F, 0.34F, -0.042F), new Vector3f(0.084F, 0.08F, 0.084F),
                CasinoPalette.CHIP_RED, light);
        pose.popPose();
    }
}
