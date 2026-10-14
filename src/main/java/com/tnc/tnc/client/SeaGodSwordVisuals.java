package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.SeaGodSwordRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Finite blue-gold baked sword, sharing the server's impact schedule. */
final class SeaGodSwordVisuals {
    private static final Vec3 UP=new Vec3(0,1,0);
    private SeaGodSwordVisuals() {}
    static void render(double age,double animation,double radius,float fieldFade,PoseStack stack,MultiBufferSource buffers) {
        float sealFade=0;
        for(int strike=0;strike<SeaGodSwordRules.COUNT;strike++) {
            double phase=age-strike*SeaGodSwordRules.INTERVAL;
            if(SeaGodSwordRules.visible(phase))sealFade=Math.max(sealFade,fieldFade*SeaGodSwordRules.opacity(phase));
        }
        // Consecutive swords share the same two stationary sky seals.
        if(sealFade>0) {
            WaterGeometry.richCircle(buffers,stack,new Vec3(0,116,0),UP,36,animation,sealFade,3);
            WaterGeometry.richCircle(buffers,stack,new Vec3(0,112,0),UP,24,-animation,sealFade*.85F,3);
        }
        // Finite barrage: at most two visible swords, sharing the same server schedule.
        for(int strike=0;strike<SeaGodSwordRules.COUNT;strike++) {
            double phase=age-strike*SeaGodSwordRules.INTERVAL;
            if(SeaGodSwordRules.visible(phase))renderSword(phase,radius,fieldFade,stack,buffers);
        }
    }
    private static void renderSword(double age,double radius,float fieldFade,PoseStack stack,MultiBufferSource buffers) {
        float fade=fieldFade*SeaGodSwordRules.opacity(age);
        if(fade<=0)return;
        double tip=SeaGodSwordRules.tipHeight(age);
        // Draw all textured seals first, then acquire the geometry buffer once.
        // 2026-10-10 作者："t5 的魔法阵加大一倍" ⇒ 半径 18/12 → **36/24** ✓（纯视觉：不改领域半径、
        // 不改落剑伤害、不改 64 格巨剑 ✗ —— 那些都由 SeaGodSwordRules / WaterSpellRules 决定 ✓）
        // 第十批：**子阵只要 3 个** ✗ —— 半径 ≥6 时默认给 6 个 ✗，而这两座阵本身就已经 36/24 格 ✗，
        // 每个子阵原来又是一座完整 sigil（≈9k 顶点 ✗）⇒ 冥穴光法阵就上十万顶点 ✗（全水法最贵的一处 ✗）。
        // 现在 `richCircle` 内部也瘦身了 ✓（符文改成 4 顶点亮笔划 ✗、子阵只画环 ✗）。

        // One registered baked model: real blue crystal planes, gold guard/edge and pearl core.
        stack.pushPose();
        stack.translate(0,tip,0);
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)(age*.35)));
        float modelScale=48F*64F/66F;
        stack.scale(modelScale,modelScale,modelScale);
        WaterSolidModels.draw("sea_god_sword",stack,buffers,1);
        stack.popPose();
        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();
        double spin=age*.006;
        // Three open streams preserve the silhouette even when consecutive swords overlap.
        for(int i=0;i<3;i++)WaterGeometry.spiral(out,pose,new Vec3(0,tip,0),UP,7,52,-age*.12+i*Math.PI*2/3,.015F,.45F,1,fade*.65F);
        WaterGeometry.ringColor(out,pose,new Vec3(0,.16,0),UP,radius,.4,.02F,.5F,1,fade,48);
        double impactAge=age-SeaGodSwordRules.IMPACT_TICK;
        if(impactAge>=0)impact(out,pose,impactAge,radius,fade);
    }
    /** 冥穴落剑冲击浪的色相：偏深的青蓝 ✓（水纹链那条 6 道浪的渐变不适用于这里 ✗）。 */
    private static final float[] IMPACT_TINT={.06F,.34F,.86F};
    private static void impact(VertexConsumer out,Matrix4f pose,double age,double radius,float fade) {
        for(int i=0;i<2;i++) {
            double r=(age-i*6)*1.2;if(r<=0 || r>=radius)continue;
            double height=12*Math.min(1,r/5);
            float edge=(float)Math.min(1,(radius-r)/4);
            WaterWaveVisuals.breakingWave(out,pose,r,height,radius,age+i*7,fade*edge,IMPACT_TINT);
        }
    }
}
