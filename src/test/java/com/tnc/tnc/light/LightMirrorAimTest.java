package com.tnc.tnc.light;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「光镜能被瞄准」的**纯几何**自检 ✓（{@link LightMirrorAim#nearest} 只碰 {@link AABB}/{@link Vec3} ✓）。
 *
 * <p>背景（作者 2026-11-07）：<i>"镜子要能瞄准，不然光线 t4t5 砸不到它身上"</i> ✗ ——
 * t4/t5 是从天上竖直落的光柱 ✓，落点原来取"准星打到的方块面" ⇒ 浮在空中的镜子被略过 ✗。
 * 现在施法先把落点换成**准星瞄着的那面镜子** ✓；这一条测试钉住"瞄得上/挡得住/跳得开"三件事 ✓。
 */
class LightMirrorAimTest {

    /** 一面光镜的光路盒子（{@link TNLightMirrorEntity#opticalBounds()} 的形状 ✓，中心在 (0,0,0)）。 */
    private static AABB mirrorAt(double x, double y, double z) {
        return new AABB(x - 0.9D, y - 1.2D, z - 0.9D, x + 0.9D, y + 1.2D, z + 0.9D);
    }

    @Test
    void picksThePrismInFront() {
        List<AABB> boxes = List.of(mirrorAt(0, 0, 10));
        assertEquals(0, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), boxes,
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "正前方的镜子没瞄上 ✗");
    }

    @Test
    void ignoresPrismsBehindOrToTheSide() {
        assertEquals(-1, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), List.of(mirrorAt(0, 0, -10)),
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "背后的镜子也瞄上了 ✗");
        assertEquals(-1, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), List.of(mirrorAt(14, 0, 6)),
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "侧面 60° 的镜子也瞄上了 ✗");
        assertEquals(-1, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), List.of(mirrorAt(0, 0, 80)),
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "超出距离的镜子也瞄上了 ✗");
    }

    @Test
    void picksTheNearestOfSeveral() {
        List<AABB> boxes = List.of(mirrorAt(0, 0, 20), mirrorAt(0, 0, 6), mirrorAt(0, 0, 12));
        assertEquals(1, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), boxes,
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING),
                "一排镜子应该选**最近**那面 ✗（挡在前面的要先分光 ✓）");
    }

    @Test
    void offlinePrismDoesNotBlockTheAimedOne() {
        // 近处那面偏 40°（不在锥里 ✓），远处那面正在正前方 ⇒ 应该选远处那面 ✓
        List<AABB> boxes = List.of(mirrorAt(6, 0, 5), mirrorAt(0, 0, 18));
        assertEquals(1, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), boxes,
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING),
                "锥外的近镜子把锥内的远镜子挡住了 ✗");
    }

    @Test
    void paddingMakesNearMissesCount() {
        // 擦着边缘：盒子上沿在 y = +1.2，视线从 (0, 1.35, 0) 平着走 ⇒ 差 0.15 格
        AABB edge = mirrorAt(0, 0, 10);
        Vec3 eye = new Vec3(0, 1.32D, 0);
        Vec3 look = new Vec3(0, 0, 1);
        assertEquals(0, LightMirrorAim.nearest(eye, look, List.of(edge), 48.0D,
                LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "擦边（0.12 格）应该算命中 ✗");
        assertEquals(-1, LightMirrorAim.nearest(new Vec3(0, 3.0D, 0), look, List.of(edge), 48.0D,
                LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "差 1.8 格还算命中 ✗（cone 也不该放宽）");
    }

    @Test
    void emptyInputIsSafe() {
        assertEquals(-1, LightMirrorAim.nearest(Vec3.ZERO, new Vec3(0, 0, 1), List.of(),
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "没有镜子应该返回 -1 ✗");
        assertEquals(-1, LightMirrorAim.nearest(Vec3.ZERO, Vec3.ZERO, List.of(mirrorAt(0, 0, 10)),
                48.0D, LightMirrorAim.COS_LIMIT, LightMirrorAim.PADDING), "零视线应该返回 -1 而不是炸 ✗");
    }
}
