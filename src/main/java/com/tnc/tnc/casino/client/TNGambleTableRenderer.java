package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.casino.machine.TNCasinoMachineBlock;
import com.tnc.tnc.casino.machine.TNGambleTableEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Green felt table with physical chips, three animated dice and printed result labels. */
public final class TNGambleTableRenderer implements BlockEntityRenderer<TNGambleTableEntity> {

    private static final List<Part> PARTS = buildParts();
    private final net.minecraft.client.gui.Font font;

    public TNGambleTableRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    private record Part(Vector3f pos, Vector3f size, int color) {
    }

    /**
     * 按"模型单位"给数（与 {@code tools/gen_gamble_table.py} 的 {@code PARTS} 表一致 ✓），
     * 内部除以 16 换成格 ✓（1 单位 = 1/16 格 ✓）。
     *
     * <p>⚠️ 六个坐标一律写成 <b>{@code 64.00F} 这样的字面量</b> ✗ ——
     * 自检脚本 {@code tools/casino-machines.mjs} 靠扫字面量把几何读出来 ✓，
     * 写成算式或裸整数会让那些盒子在报告里静默消失 ✓（它现在会打 WARNING ✓）。
     */
    private static void add(List<Part> out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color) {
        out.add(new Part(
                new Vector3f(x0 / 16.0F, y0 / 16.0F, z0 / 16.0F),
                new Vector3f((x1 - x0) / 16.0F, (y1 - y0) / 16.0F, (z1 - z0) / 16.0F),
                color));
    }

    private static List<Part> buildParts() {
        List<Part> out = new ArrayList<>();

        // ⚠️ 全部写成字面量（`0.00F` / `64.00F` ✓，不写 `0` / `64` ✗）——
        //    自检脚本 tools/casino-machines.mjs 只认 `数字F` 这种字面量 ✗，
        //    写裸整数的话这些盒子会在报告里**静默消失** ✓（踩过一次 ✓）。

        // ---- 台呢台面（4.0 × 2.0 格 ✓，就是原来那份 json 里唯一被许可尺寸的部件 ✓）----
        add(out, 0.00F, 12.00F, 0.00F, 64.00F, 13.00F, 32.00F, CasinoPalette.FELT);

        // ---- 四周围栏（木框，比台呢高 2 单位 ✓）----
        add(out, 0.00F, 13.00F, 0.00F, 64.00F, 15.00F, 2.00F, CasinoPalette.WOOD);      // 前
        add(out, 0.00F, 13.00F, 30.00F, 64.00F, 15.00F, 32.00F, CasinoPalette.WOOD);    // 后
        add(out, 0.00F, 13.00F, 2.00F, 2.00F, 15.00F, 30.00F, CasinoPalette.WOOD);      // 左
        add(out, 62.00F, 13.00F, 2.00F, 64.00F, 15.00F, 30.00F, CasinoPalette.WOOD);    // 右

        // ---- 台面下的裙板（apron ✓）----
        add(out, 3.00F, 10.00F, 3.00F, 61.00F, 12.00F, 29.00F, CasinoPalette.WOOD_DARK);

        // ---- 四条腿（避开裙板，落在 2..5 那一段 ✓）----
        add(out, 2.00F, 0.00F, 2.00F, 5.00F, 10.00F, 5.00F, CasinoPalette.WOOD_DARK);
        add(out, 2.00F, 0.00F, 27.00F, 5.00F, 10.00F, 30.00F, CasinoPalette.WOOD_DARK);
        add(out, 59.00F, 0.00F, 2.00F, 62.00F, 10.00F, 5.00F, CasinoPalette.WOOD_DARK);
        add(out, 59.00F, 0.00F, 27.00F, 62.00F, 10.00F, 30.00F, CasinoPalette.WOOD_DARK);

        // ---- 两张牌（斜着搭在一起 ✓—— 用不同高度做出"叠"的感觉 ✓）----
        add(out, 6.00F, 13.00F, 6.00F, 14.00F, 13.30F, 12.00F, CasinoPalette.CARD_FACE);
        add(out, 12.00F, 13.30F, 8.00F, 20.00F, 13.60F, 14.00F, CasinoPalette.CARD_BACK);

        // ---- 三摞筹码（红/蓝/白 ✓）----
        add(out, 34.00F, 13.00F, 6.00F, 38.00F, 14.00F, 10.00F, CasinoPalette.CHIP_RED);
        add(out, 39.00F, 13.00F, 7.00F, 43.00F, 14.00F, 11.00F, CasinoPalette.CHIP_BLUE);
        add(out, 44.00F, 13.00F, 6.00F, 48.00F, 14.00F, 10.00F, CasinoPalette.CHIP_WHITE);

        // ---- 牌靴（右侧的长条 ✓）----
        add(out, 54.00F, 13.00F, 6.00F, 62.00F, 14.00F, 26.00F, CasinoPalette.CARD_BACK);

        add(out, 0.00F, 14.00F, 0.00F, 64.00F, 15.20F, 1.40F, CasinoPalette.CARD_BACK);
        add(out, 0.00F, 14.00F, 30.60F, 64.00F, 15.20F, 32.00F, CasinoPalette.CARD_BACK);
        add(out, 0.00F, 14.00F, 1.40F, 1.40F, 15.20F, 30.60F, CasinoPalette.CARD_BACK);
        add(out, 62.60F, 14.00F, 1.40F, 64.00F, 15.20F, 30.60F, CasinoPalette.CARD_BACK);
        add(out, 1.40F, 13.02F, 1.40F, 62.60F, 13.12F, 1.65F, CasinoPalette.BRASS);
        add(out, 1.40F, 13.02F, 30.35F, 62.60F, 13.12F, 30.60F, CasinoPalette.BRASS);
        add(out, 1.40F, 13.02F, 1.65F, 1.65F, 13.12F, 30.35F, CasinoPalette.BRASS);
        add(out, 62.35F, 13.02F, 1.65F, 62.60F, 13.12F, 30.35F, CasinoPalette.BRASS);
        add(out, 3.00F, 10.50F, 2.80F, 61.00F, 10.80F, 3.00F, CasinoPalette.BRASS_DARK);
        return List.copyOf(out);
    }

    @Override
    public void render(TNGambleTableEntity table, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (table.getLevel() == null) return;

        // Table and dice use the room lighting.
        VertexConsumer buffer = buffers.getBuffer(
                RenderType.entityCutoutNoCull(TNCasinoGeometry.white()));

        Direction facing = table.getBlockState().hasProperty(TNCasinoMachineBlock.FACING)
                ? table.getBlockState().getValue(TNCasinoMachineBlock.FACING) : Direction.NORTH;

        pose.pushPose();
        // ⚠️ 这张桌子是 4×2 格 ✓，而父类那套"绕 2×2 的中心转"是按 1.0 格半宽写的 ✗
        //    —— 直接复用会把旋转中心放到桌子左半边 ✓。
        //    所以这里绕**真正的中心 (2.0, 0, 1.0)** 转 ✓。
        pose.translate(4.0D, 0.0D, 2.0D);
        pose.mulPose(Axis.YP.rotationDegrees(TNCasinoGeometry.yawFor(facing)));
        pose.translate(-4.0D, 0.0D, -2.0D);
        CasinoFurniture.table(pose,buffer,8,4,.81F,light);
        CasinoFurniture.chips(pose,buffer,5.6F,.815F,.8F,light);

        CasinoFurniture.bettingBox(pose,buffer,2.06F,0.816F,1.26F,3.4F,1.8F,light);
        for(int seat=0;seat<5;seat++) CasinoFurniture.bettingBox(pose,buffer,1F+seat*1.24F,
                0.816F,3.16F,.84F,.48F,light);
        float elapsed = (table.getLevel().getGameTime()-table.startedAt())+partialTick;
        for (int i=0;i<3;i++) CasinoTableDetails.die(pose, buffer, 2.9F+i*.8F,0.915F,2.4F,
                table.result(i)+1,elapsed,table.clientSpinTicks()-i*6,table.spinning(),light);
        CasinoTableDetails.label(font,pose,buffers,"三骰 · " + table.betName(),4,0.819F,0.8F,light);
        String outcome = table.spinning() ? "摇骰中…" : table.spins()==0 ? "潜行选注 · 铜币开局" :
                "点数 " + (table.result(0)+1) + "/" + (table.result(1)+1) + "/" + (table.result(2)+1)
                + " · 返回 " + table.lastPayout() + " 铜币";
        CasinoTableDetails.label(font,pose,buffers,outcome,4,0.819F,3.44F,light);
        pose.popPose();
    }

}
