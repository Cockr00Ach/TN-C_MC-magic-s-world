package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.OceanWaveRules;
import com.tnc.tnc.magic.water.WaterSpellRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 水法表现的**顶点预算闸门**（2026-10-10 第十批）✓。
 *
 * <p><b>为什么要有这把尺子</b>：作者 2026-10-10："**水法全部都不流畅啊，t 级越高的法术越让我觉得卡**"✗。
 * 在那之前，四条链的"贵不贵"全靠人肉读代码估 ✗ —— 估错过好几次 ✗（雨链旧 t5 当时是十余万顶点 ✗）。
 * 这个测试用一个**假 {@link VertexConsumer}**（动态代理 ✗）把每个几何构造器的顶点数**真的数出来** ✗，
 * 于是"砍了多少"变成数字 ✓，而且以后再有人往渲染里塞细节，这里会先红 ✗。
 *
 * <p><b>只量走 {@code VertexConsumer} 的构造器</b> ✗：走 {@code MultiBufferSource} 的那些
 * （`richCircle` / `rainSeal` ✗）要先有 `RenderType` ✗，而 RenderType 的静态初始化需要游戏启动 ✗
 * ⇒ JUnit 里碰不得 ✗（那些按"逐件数出来"记在文档里 ✓）。
 */
class WaterVisualsCostTest {

    /** 假顶点消费者：只数 {@code vertex} 调用 ✗，其余链式方法原样返回自己 ✓。 */
    private static final class Counter implements InvocationHandler {
        int vertices;
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("vertex")) vertices++;
            return method.getReturnType() == void.class ? null : proxy;
        }
    }

    private static int cost(Consumer<VertexConsumer> draw) {
        Counter counter = new Counter();
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(
                WaterVisualsCostTest.class.getClassLoader(), new Class<?>[]{VertexConsumer.class}, counter);
        draw.accept(out);
        return counter.vertices;
    }

    private static final Matrix4f POSE = new Matrix4f();
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /* ==================== 水纹链（kind==1）：海啸 t4 / 灭世之海 t5 是最贵的两发 ✗ ==================== */

    @Test void tianhongWavesStayWithinTheOceanBudget() {
        int fronts = OceanWaveRules.MAX_FRONTS;
        int wave = cost(out -> {
            for (int i = 0; i < fronts; i++)
                WaterWaveVisuals.breakingWave(out, POSE, 40 + i * 8, OceanWaveRules.frontHeight(40 + i * 8), 64, 120 + i * 30, .9F, WaterWaveVisuals.frontTint(i));
        });
        int glow = cost(out -> {
            for (int i = 0; i < fronts; i++)
                WaterWaveVisuals.breakingGlow(out, POSE, 40 + i * 8, OceanWaveRules.frontHeight(40 + i * 8), 64, 120 + i * 30, .9F, WaterWaveVisuals.frontTint(i));
        });
        System.out.println("TN-C water cost: world_ending_sea bodies=" + wave + " glow=" + glow + " total=" + (wave + glow) + " verts over " + fronts + " fronts");
        assertTrue(wave + glow <= WORLD_ENDING_SEA_BUDGET,
                "World Ending Sea must stay inside " + WORLD_ENDING_SEA_BUDGET + " vertices for all " + fronts + " fronts, was " + (wave + glow));
    }

    @Test void rollingWaveAndCrescentStayWithinTheirBudgets() {
        int wave = cost(out -> WaterWaveVisuals.rollingWave(out, POSE, Vec3.ZERO, new Vec3(0, 0, 1), 8, 8, 40, .9F, 2.0));
        int crescent = cost(out -> WaterWaveVisuals.crescent(out, POSE, Vec3.ZERO, new Vec3(0, 0, 1), 8, 40, .9F, 0));
        int circular = cost(out -> WaterWaveVisuals.circularWave(out, POSE, 12, 3, 40, .9F));
        System.out.println("TN-C water cost: tsunami_wave=" + wave + " crescent=" + crescent + " circular=" + circular);
        assertTrue(wave <= TSUNAMI_BUDGET, "Tsunami wave body must stay inside " + TSUNAMI_BUDGET + ", was " + wave);
        assertTrue(crescent <= CRESCENT_BUDGET, "One crescent slash front must stay inside " + CRESCENT_BUDGET + ", was " + crescent);
        assertTrue(circular <= CIRCULAR_BUDGET, "Circular wave must stay inside " + CIRCULAR_BUDGET + ", was " + circular);
    }

    /* ==================== 水缚链（kind==2）：t4 深渊的水板圆筒最贵 ✗ ==================== */

    @Test void waterPanelCylinderStaysWithinBudget() {
        int abyss = cost(out -> WaterGeometry.panelWall(out, POSE, Vec3.ZERO, 18, 17, WaterGeometry.panelCount(18), 120, .95F));
        int prison = cost(out -> WaterGeometry.panelWall(out, POSE, Vec3.ZERO, 7, 9, WaterGeometry.panelCount(7), 120, .95F));
        System.out.println("TN-C water cost: abyss_panel_wall=" + abyss + " prison_panel_wall=" + prison);
        assertTrue(abyss <= PANEL_WALL_BUDGET, "Abyss water-panel cylinder must stay inside " + PANEL_WALL_BUDGET + ", was " + abyss);
    }

    /* ==================== 雨滴链（kind==3）：第十批新写的那套 ✗ ==================== */

    @Test void rainChainStaysWithinBudget() {
        for(int tier=1;tier<=5;tier++) {
            final int t=tier;
            double r=WaterSpellRules.fieldRadius(3,t), h=WaterSpellRules.fieldHeight(3,t);
            int near=cost(out -> WaterRainVisuals.renderGarden(out,out,POSE,t,r,h,120,.9F,true));
            int far=cost(out -> WaterRainVisuals.renderGarden(out,out,POSE,t,r,h,120,.9F,false));
            System.out.println("TN-C rain garden tier="+t+" near="+near+" far="+far);
            assertTrue(near<=RAIN_BUDGET[t-1],"Complete rain mesh including glow: "+near);
            assertTrue(far<near,"Distance LOD must reduce actual vertices");
            for(double age:new double[]{0,.5,24,699}) {
                int animated=cost(out -> WaterRainVisuals.renderGarden(out,out,POSE,t,r,h,age,.9F,true));
                assertTrue(animated<=RAIN_BUDGET[t-1],"Animation must retain a bounded mesh");
            }
        }
    }

    @Test void rainMeshHasFiniteCoordinatesAndExportsForReview() throws Exception {
        var meshes = new StringBuilder("[");
        for(int tier=1;tier<=5;tier++) {
            if(tier>1)meshes.append(',');
            meshes.append('[');
            var vertices = new java.util.ArrayList<double[]>();
            InvocationHandler recorder = (proxy,method,args) -> {
                if(method.getName().equals("vertex")) {
                    double[] v={((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue(),
                            ((Number)args[3]).doubleValue(),0,0,0,0};
                    for(int j=0;j<3;j++)assertTrue(Double.isFinite(v[j]),"Finite mesh coordinate");
                    vertices.add(v);
                }
                if(method.getName().equals("color")) {
                    var v=vertices.get(vertices.size()-1);
                    for(int j=0;j<4;j++) {
                        v[j+3]=((Number)args[j]).doubleValue();
                        assertTrue(v[j+3]>=0 && v[j+3]<=1,"Color must not wrap when packed into bytes");
                    }
                }
                return method.getReturnType()==void.class?null:proxy;
            };
            VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},recorder);
            WaterRainVisuals.renderGarden(out,out,POSE,tier,WaterSpellRules.fieldRadius(3,tier),
                    WaterSpellRules.fieldHeight(3,tier),120,.9F,true);
            assertEquals(0,vertices.size()%4,"Complete quads");
            for(int i=0;i<vertices.size();i++) {
                if(i>0)meshes.append(',');
                meshes.append(java.util.Arrays.toString(vertices.get(i)));
            }
            meshes.append(']');
        }
        meshes.append(']');
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/rain-preview-mesh.json"),meshes);
    }

    @Test void completeBeamBodyHasBoundedNearAndFarCost() {
        for(int tier:new int[]{4,5}) {
            int t=tier;
            int near=cost(out -> WaterBeamVisuals.render(out,out,POSE,t,new Vec3(0,0,1),WaterSpellRules.range(t),120,.9F,true));
            int far=cost(out -> WaterBeamVisuals.render(out,out,POSE,t,new Vec3(0,0,1),WaterSpellRules.range(t),120,.9F,false));
            System.out.println("TN-C complete beam tier="+t+" near="+near+" far="+far);
            assertTrue(near<=1800,"Beam shell, core, ribbons and caps share one budget");
            assertTrue(far<near,"Beam distance LOD reduces geometry");
            assertEquals(0,cost(out -> WaterBeamVisuals.render(out,out,POSE,t,UP,0,120,.9F,true)));
        }
    }

    @Test void fullSigilRotationRemainsContinuousAcrossServerAgeJumps() {
        var clock=new com.tnc.tnc.magic.water.WaterVisualClock();
        double before=clock.time(5,.99F,40),after=clock.time(6,0,48);
        for(double radius:new double[]{3.5,30,36}) {
            var a=positions(out -> WaterGeometry.drawCircle(out,out,POSE,Vec3.ZERO,UP,radius,before,.9F,3));
            var b=positions(out -> WaterGeometry.drawCircle(out,out,POSE,Vec3.ZERO,UP,radius,after,.9F,3));
            assertEquals(a.size(),b.size());
            assertTrue(a.size()<=3800,"Complete seal including satellites and glow is budgeted");
            double largest=0;
            for(int i=0;i<a.size();i++)largest=Math.max(largest,a.get(i).distanceTo(b.get(i)));
            assertTrue(largest<radius*.001,"A tick boundary must move the rotating glyphs by only a subframe step: "+largest);
            System.out.println("TN-C sigil radius="+radius+" fullVertices="+a.size()+" subframeMotion="+largest);
        }
    }
    private static java.util.List<Vec3> positions(Consumer<VertexConsumer> draw) {
        var result=new java.util.ArrayList<Vec3>();
        VertexConsumer out=(VertexConsumer)Proxy.newProxyInstance(WaterVisualsCostTest.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class},(proxy,method,args) -> {
                    if(method.getName().equals("vertex")) {
                        Vec3 p=new Vec3(((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue(),((Number)args[3]).doubleValue());
                        assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z));result.add(p);
                    }
                    return method.getReturnType()==void.class?null:proxy;
                });
        draw.accept(out);return result;
    }
    @Test void cachedRingsMatchThePreviousMeshForTiltedPlanes() {
        Vec3 center=new Vec3(2.5,-3,116);
        for(Vec3 dir:new Vec3[]{UP,new Vec3(0,0,1),new Vec3(.3,-.8,.5).normalize()})
            for(int segments:new int[]{16,32,48,64}) {
                double radius=36,width=.25;
                var cached=positions(out -> WaterGeometry.ringColor(out,POSE,center,dir,radius,width,.5F,.8F,1,.9F,segments));
                Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
                var previous=positions(out -> {
                    for(int i=0;i<segments;i++) {
                        double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
                        WaterGeometry.quad(out,POSE,center.add(WaterGeometry.radial(right,up,a,radius)),
                                center.add(WaterGeometry.radial(right,up,b,radius)),
                                center.add(WaterGeometry.radial(right,up,b,radius-width)),
                                center.add(WaterGeometry.radial(right,up,a,radius-width)),.5F,.8F,1,.9F);
                    }
                });
                assertEquals(previous.size(),cached.size());
                for(int i=0;i<cached.size();i++)assertTrue(previous.get(i).distanceTo(cached.get(i))<.00002,
                        "Cached rings preserve the geometry on arbitrary cast planes");
            }
    }

    @Test void glowIsSubmittedOnceAfterAllWaterVertices() {
        var order=new java.util.ArrayList<String>();
        java.util.function.Function<String,VertexConsumer> consumer=label -> (VertexConsumer)Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args) -> {
                    if(method.getName().equals("vertex"))order.add(label);
                    return method.getReturnType()==void.class?null:proxy;
                });
        int[] acquisitions={0};
        WaterVisualBuffers.draw(consumer.apply("body"),() -> {
            acquisitions[0]++;assertEquals(java.util.List.of("body","body"),order);
            return consumer.apply("glow");
        },(body,glow) -> {
            body.vertex(0,0,0).color(0,0,255,255).endVertex();
            glow.vertex(0,1,0).color(0,255,255,128).endVertex();
            body.vertex(1,0,0).color(0,0,255,255).endVertex();
            glow.vertex(1,1,0).color(0,255,255,128).endVertex();
        });
        assertEquals(1,acquisitions[0]);
        assertEquals(java.util.List.of("body","body","glow","glow"),order);
    }

    /* ==================== 共用零件：单件预算 ✗ ==================== */

    @Test void sharedPrimitivesStayCheap() {
        int ring = cost(out -> WaterGeometry.ringColor(out, POSE, Vec3.ZERO, UP, 16, .2, 1, 1, 1, .9F, 40));
        int disk = cost(out -> WaterGeometry.disk(out, POSE, Vec3.ZERO, UP, 16, 1, 1, 1, .5F, 40));
        int thick = cost(out -> WaterGeometry.tube(out, POSE, Vec3.ZERO, new Vec3(0, 10, 0), 1, 1, 1, 1, .8F, 24));
        int thin = cost(out -> WaterGeometry.tube(out, POSE, Vec3.ZERO, new Vec3(0, 10, 0), .05, 1, 1, 1, .8F));
        int stroke = cost(out -> WaterGeometry.stroke(out, POSE, UP, Vec3.ZERO, new Vec3(0, 0, 4), .1, 1, 1, 1, .8F));
        System.out.println("TN-C water cost: ring40=" + ring + " disk40=" + disk + " tube24=" + thick + " tubeThin=" + thin + " stroke=" + stroke);
        assertTrue(ring <= RING_BUDGET, "One bright ring must stay inside " + RING_BUDGET + ", was " + ring);
        assertTrue(stroke <= STROKE_BUDGET, "One bright stroke must stay inside " + STROKE_BUDGET + ", was " + stroke);
        // ★ 这一条是第十批最贵的那个坑 ✗：`tube` 的老判据是 radius<.1?4:24 ✗ ⇒
        //   半透明水体里那些"看着很细"的线（.10/.12/.13 ✗）其实全是 24 边＝96 顶点 ✗。
        //   细线要么把半径压到 .1 以下 ✓，要么显式给边数 ✓。
        assertTrue(thin <= THIN_TUBE_BUDGET, "A thin line drawn as a tube must stay inside " + THIN_TUBE_BUDGET + ", was " + thin);
    }

    /* ==================== 预算（第十批按实测钉住 ✗）==================== */

    /**
     * 灭世之海：六道浪的**水体＋亮线**合计 ✗（半径 64 格、持续 30 秒，是全水法最重的一发 ✗）。
     *
     * <p>实测轨迹：**81,408 → 11,712** ✓（2026-10-10 第十批 ✗）。
     * 现在的余量只够"再多一点点"✗ —— 谁要往这个文件里加浪，先看这个数 ✗。
     */
    static final int WORLD_ENDING_SEA_BUDGET = 13_000;
    static final int TSUNAMI_BUDGET = 2_400;
    static final int CRESCENT_BUDGET = 1_000;
    static final int CIRCULAR_BUDGET = 1_800;
    static final int PANEL_WALL_BUDGET = 3_000;
    /** 雨滴链各档（完整新版（含发光层）实测 1376 / 1432 / 2744 / 2272 / 2976）。 */
    static final int[] RAIN_BUDGET = {1_800, 2_600, 3_900, 4_200, 4_300};
    static final int RING_BUDGET = 200;
    static final int STROKE_BUDGET = 4;
    static final int THIN_TUBE_BUDGET = 32;
}
