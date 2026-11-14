package com.tnc.tnc.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 水法“唤兽门 + 真实 Boss 残影”的回归闸门（2026-11-06 修订）。
 *
 * <p>第一版残影是 {@code WaterCastFx.miniBeast} 手工画的七段蛇形几何；作者指出
 * “小兽就是水系 Boss 模型缩小 + 透明度”。现在改为 {@link LeviathanGhostVisual}
 * 直接渲染 {@code TNWaterLeviathanEntity} 的 GeckoLib 模型，本测试守住：
 * <ul>
 *   <li>残影缩放后仍是“小型”（约 1.5–1.7 格长），不会把 Boss 原尺寸搬进读条；</li>
 *   <li>侧门随机姿态仍不切地、不穿玩家；</li>
 *   <li>残影走 Boss 原模型/原贴图/半透明层，而不是另一套手画几何。</li>
 * </ul>
 */
class WaterBeastGeometryTest {

    @Test
    void ghostKeepsBossModelMiniatureAndUsesBossForwardAxis() {
        float scale = LeviathanGhostVisual.scale();
        assertTrue(scale > .02F && scale <= .13F,
                "残影应缩到 Boss 原模型约 1/8，实际 " + scale);
        assertEquals(0F, LeviathanGhostVisual.yawFor(new Vec3(0, 0, 1)), 1e-4F);
        assertEquals(90F, LeviathanGhostVisual.yawFor(new Vec3(-1, 0, 0)), 1e-4F);
        assertEquals(-90F, LeviathanGhostVisual.yawFor(new Vec3(1, 0, 0)), 1e-4F);
    }

    @Test
    void sideGatewayStaysAboveGroundAndOutsideThePlayer() throws Exception {
        Class<?> stateClass = Class.forName("com.tnc.tnc.client.WaterCastFx$CastState");
        var ctor = stateClass.getDeclaredConstructor(int.class, boolean.class);
        ctor.setAccessible(true);
        Method centerMethod = WaterCastFx.class.getDeclaredMethod("sideCenter", stateClass);
        Method normalMethod = WaterCastFx.class.getDeclaredMethod("sideNormal", stateClass);
        centerMethod.setAccessible(true);
        normalMethod.setAccessible(true);

        for (int tier = 1; tier <= 5; tier++) {
            double radius = .74D + tier * .05D;
            for (int i = 0; i < 250; i++) {
                Object state = ctor.newInstance(tier, true);
                Vec3 center = (Vec3) centerMethod.invoke(null, state);
                Vec3 normal = (Vec3) normalMethod.invoke(null, state);
                double verticalExtent = Math.sqrt(Math.max(0, 1 - normal.y * normal.y)) * radius;
                assertTrue(center.y - verticalExtent > .01D,
                        "侧门不能切进地面: center=" + center + " normal=" + normal);
                double distance = Math.hypot(center.x, center.z);
                assertTrue(distance - radius > .35D,
                        "侧门不能穿进玩家身体: center=" + center + " radius=" + radius);
            }
        }
    }

    @Test
    void primaryGatewayIsBiasedIntoTheFrontHemisphere() throws Exception {
        Class<?> stateClass = Class.forName("com.tnc.tnc.client.WaterCastFx$CastState");
        var ctor = stateClass.getDeclaredConstructor(int.class, boolean.class);
        ctor.setAccessible(true);
        Method centerMethod = WaterCastFx.class.getDeclaredMethod("sideCenter", stateClass);
        centerMethod.setAccessible(true);

        // 玩家 yaw=0 时看向 +Z；主门必须落在前半圆，第一人称才看得见。
        int front = 0;
        for (int i = 0; i < 250; i++) {
            Object state = ctor.newInstance(1, true);
            Vec3 center = (Vec3) centerMethod.invoke(null, state);
            if (center.z > 0) {
                front++;
            }
        }
        assertTrue(front > 235, "主门必须几乎总在正前方扇区里，实际 front=" + front + "/250");
    }

    @Test
    void ghostIsSubmittedAfterTheAdditiveRitualPass() throws Exception {
        String fx = Files.readString(Path.of(
                "src/main/java/com/tnc/tnc/client/WaterCastFx.java"));
        assertTrue(fx.contains("BUFFERS.endBatch(RenderType.lightning())")
                        && fx.contains("BUFFERS.endBatch(LeviathanGhostVisual.renderType())"),
                "半透明实体层必须在闪电/加色层之后单独 flush，否则残影会被当成加色几何");
        int lightningFlush = fx.indexOf("BUFFERS.endBatch(RenderType.lightning())");
        int ghostFlush = fx.indexOf("BUFFERS.endBatch(LeviathanGhostVisual.renderType())");
        assertTrue(lightningFlush >= 0 && ghostFlush > lightningFlush,
                "先结束加色层，再提交半透明残影；顺序不能反");
    }
}
