package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 水系法阵/前戏的样式闸门（2026-11-06）。
 *
 * <p>作者原话：“你现在看看水法，优化优化水法的法阵，然后也给他加上前戏”。
 * 以前的 {@code richCircle} 只是把一张 120 个元素的方块模型贴到法阵上，俯视是一圈
 * 蓝色/金色方块；读条期间也没有任何前戏。这里用两条闸门把改动钉住：
 *
 * <ol>
 *   <li>水阵必须有自己的潮汐几何（中心漩涡、月牙、浪鳍、旋臂），不能再退回方块模型；</li>
 *   <li>四条链的读条前戏必须分家（水球/水波/水缚/雨），且低阶不画竖墙、不压屏；</li>
 *   <li>顶点预算：t1–t5 的单个水阵都要在 4k 顶点以内（含发光层）。</li>
 * </ol>
 */
class WaterSealStyleTest {

    private static final class Counter implements InvocationHandler {
        int vertices;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("vertex")) {
                vertices++;
            }
            return method.getReturnType() == void.class ? null : proxy;
        }
    }

    private static int cost(int tier) {
        Counter counter = new Counter();
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(
                WaterSealStyleTest.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, counter);
        Matrix4f pose = new Matrix4f();
        WaterGeometry.seal(out, out, pose, Vec3.ZERO, new Vec3(0, 1, 0), 4, 40, 1, tier);
        return counter.vertices;
    }

    @Test
    void tideSealUsesItsOwnMotifAndStaysInsideBudget() {
        int low = cost(1);
        int mid = cost(3);
        int high = cost(5);
        System.out.println("TN-C water seal cost: t1=" + low + " t3=" + mid + " t5=" + high);
        assertTrue(low > 600, "低阶水阵也不能是一张空环");
        assertTrue(mid > low, "t3 必须比 t1 多出月牙/旋臂，不能只换半径");
        assertTrue(high > mid, "t5 必须比 t3 更完整（更多浪鳍/旋臂）");
        assertTrue(high <= 4_200, "单个水阵含发光层必须 <= 4.2k 顶点，实际 " + high);
    }

    @Test
    void waterCirclesNoLongerUseTheBlockModel() throws Exception {
        String geometry = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/WaterGeometry.java"));
        assertTrue(geometry.contains("sealCrescent(") && geometry.contains("sealVortex(")
                        && geometry.contains("sealCrest(") && geometry.contains("sealArm("),
                "水阵必须有月牙、中心漩涡、浪鳍、旋臂四件潮汐母题");
        assertTrue(!geometry.contains("sealDrop("),
                "中心不能再画水滴+内环+白核，那从俯视看就是一只眼睛");
        String rich = geometry.substring(geometry.indexOf("static void richCircle("));
        assertTrue(!rich.contains("WaterSolidModels.seal("),
                "richCircle 不能再把方块 seal.json 贴到所有水系法阵上");
        String spell = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/TNWaterSpellRenderer.java"));
        assertTrue(spell.contains("e.tier()"), "实体水阵必须把档位传给几何");
    }

    @Test
    void castPreludeSeparatesFourWaterFamiliesAndTiers() throws Exception {
        String fx = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/WaterCastFx.java"));
        for (String group : new String[]{"water_ball", "water_wave", "water_bind", "water_rain"}) {
            assertTrue(fx.contains(group), "读条前戏必须覆盖 " + group);
        }
        assertTrue(fx.contains("case ORB") && fx.contains("case WAVE")
                        && fx.contains("case BIND") && fx.contains("case RAIN"),
                "四条链必须各有自己的前戏分支");
        assertTrue(fx.contains("tier < 3") && fx.contains("tier >= 5"),
                "低阶/高阶必须有独立结构，不能只是同一套几何缩放");
        assertTrue(fx.contains("deepVignette") || fx.contains("darkVignette"),
                "前戏需要蓝色暗角，但不能复用光法的暖白过曝");
    }

    @Test
    void playerCastGetsAFrontGateEchoAndTheRealBossGhost() throws Exception {
        String fx = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/WaterCastFx.java"));
        String ghost = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/LeviathanGhostVisual.java"));
        assertTrue(fx.contains("sideRitual(") && fx.contains("ghostFor(")
                        && fx.contains("LeviathanGhostVisual.render("),
                "玩家读条必须有一张前向主门，以及从门里飞出的真实水渊兽残影");
        assertTrue(fx.contains("gateFx(") && fx.contains("echoCenter("),
                "主门之外要有一张更小的回声门，读条空间不再只有单环");
        assertTrue(!fx.contains("miniBeast("),
                "不能再用手画的蛇形残影替代 Boss 模型");
        assertTrue(ghost.contains("renderRecursively") && ghost.contains("entityTranslucent")
                        && ghost.contains("TNWaterLeviathanEntity.modelResource()")
                        && ghost.contains("water_leviathan_bedrock.png"),
                "残影必须直接渲染 Boss 的 GeckoLib 模型，并使用半透明实体层");
        assertTrue(fx.contains("sideAngle") && fx.contains("sideDistance")
                        && fx.contains("sideStyle") && fx.contains("beastStart")
                        && fx.contains("beastEnd"),
                "侧门角度/距离/平立斜三种姿态和小兽出场时机必须按每个 CastState 固定");
        assertTrue(fx.contains("rippleGlow(out, pose, ripple"),
                "脚边要有一圈小水波，而不是只靠主阵");
        assertTrue(fx.contains("absorb(level, state, info)"),
                "小兽撞进胸口时要有一层吸收水花");
    }
}
