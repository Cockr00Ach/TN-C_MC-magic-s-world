package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.SeaGodSwordRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Finite, procedural 3D sword rather than a reskinned laser or a particle cloud. */
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

        var out=buffers.getBuffer(WaterRenderTypes.geometry());var pose=stack.last().pose();
        double spin=age*.006;
        double[] heights={0,8,44,52},widths={0,1.8,6,4.6},depths={0,.45,1.6,1.2};
        for(int layer=0;layer<3;layer++) {
            Vec3[] low=section(tip+heights[layer],widths[layer],depths[layer],spin);
            Vec3[] high=section(tip+heights[layer+1],widths[layer+1],depths[layer+1],spin);
            for(int side=0;side<4;side++) {
                int next=(side+1)%4;
                WaterGeometry.quad(out,pose,low[side],high[side],high[next],low[next],
                        .015F,side%2==0?.32F:.58F,1,fade*.82F);
                // 第十批：显式 **8 边** ✗（.13 半径走老判据是 24 边＝96 顶点 ✗，剑身 12 根就 1.2k ✗）
                WaterGeometry.tube(out,pose,low[side],high[side],.13,.3F,.85F,1,fade*.95F,8);
            }
        }
        // Raised central ridge, swept crossguard, banded grip and luminous pommel.
        WaterGeometry.tube(out,pose,new Vec3(0,tip+2,0),new Vec3(0,tip+52,0),.24,.45F,.92F,1,fade,10);
        Vec3 guardCenter=new Vec3(0,tip+53,0);
        for(int side:new int[]{-1,1}) {
            Vec3 inner=rotate(new Vec3(side*7,tip+54,0),spin),edge=rotate(new Vec3(side*13,tip+51,0),spin);
            WaterGeometry.tube(out,pose,guardCenter,inner,1.05,.015F,.25F,.85F,fade*.88F,10);
            WaterGeometry.tube(out,pose,inner,edge,.7,.02F,.5F,1,fade*.9F,10);
            WaterGeometry.tube(out,pose,inner.add(0,.8,0),edge.add(0,.5,0),.14,.5F,.92F,1,fade,8);
        }
        WaterGeometry.tube(out,pose,new Vec3(0,tip+53,0),new Vec3(0,tip+62,0),.65,.015F,.18F,.65F,fade*.9F,10);
        // 握柄束环：7 圈 64 段（1.8k ✗）⇒ **4 圈 32 段** ✓
        for(int i=0;i<4;i++)WaterGeometry.ringColor(out,pose,new Vec3(0,tip+54+i*1.7,0),UP,.85,.15,1,.67F,.08F,fade*.9F,32);
        WaterGeometry.tube(out,pose,new Vec3(0,tip+62,0),new Vec3(0,tip+SeaGodSwordRules.LENGTH,0),1.25,.08F,.6F,1,fade,12);
        WaterGeometry.ringColor(out,pose,new Vec3(0,tip+63,0),UP,3,.25,1,.65F,.08F,fade,40);
        // Three open streams preserve the silhouette even when consecutive swords overlap.
        for(int i=0;i<3;i++)WaterGeometry.spiral(out,pose,new Vec3(0,tip,0),UP,7,52,-age*.12+i*Math.PI*2/3,.015F,.45F,1,fade*.65F);
        WaterGeometry.ringColor(out,pose,new Vec3(0,.16,0),UP,radius,.4,.02F,.5F,1,fade,48);
        double impactAge=age-SeaGodSwordRules.IMPACT_TICK;
        if(impactAge>=0)impact(out,pose,impactAge,radius,fade);
    }
    private static Vec3[] section(double y,double width,double depth,double spin) {
        return new Vec3[]{rotate(new Vec3(-width,y,0),spin),rotate(new Vec3(0,y,depth),spin),
                rotate(new Vec3(width,y,0),spin),rotate(new Vec3(0,y,-depth),spin)};
    }
    private static Vec3 rotate(Vec3 p,double spin) {
        return new Vec3(p.x*Math.cos(spin)-p.z*Math.sin(spin),p.y,p.x*Math.sin(spin)+p.z*Math.cos(spin));
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
