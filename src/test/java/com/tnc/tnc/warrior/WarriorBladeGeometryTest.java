package com.tnc.tnc.warrior;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 常驻剑身光效的几何：剑长、朝向、挥砍弧线。 */
class WarriorBladeGeometryTest {

    @Test
    void tipSitsOneBladeLengthFromTheGrip() {
        Vec3[] points = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), 0, 0, 0, 0.92);
        assertEquals(3, points.length);
        assertEquals(0.92, WarriorBladeGeometry.span(points), 1e-6, "剑尖到握把必须正好是一段剑身");
        assertTrue(points[2].y > points[1].y && points[1].y > points[0].y, "静止时剑尖应高于握把");
    }

    @Test
    void fullSwingDropsTheTipForwardAndDown() {
        Vec3 rest = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), 0, 0, 0, 1.0)[2];
        Vec3 swing = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), 0, 0, 1, 1.0)[2];
        assertTrue(swing.y < rest.y, "挥砍到底时剑尖应压低");
        assertTrue(swing.z > 0.2, "挥砍到底时剑尖仍在身前");
    }

    @Test
    void bladeTurnsWithThePlayer() {
        Vec3 north = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), 0, 0, 0, 1.0)[2];
        Vec3 east = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), -90, 0, 0, 1.0)[2];
        assertTrue(north.z > 0.4, "yaw=0（朝南）时剑尖朝 +Z");
        assertTrue(east.x > 0.4, "yaw=-90（朝东）时剑尖朝 +X");
    }

    @Test
    void bladeLengthsFollowTheFamily() {
        assertEquals(0.0, WarriorBladeGeometry.length(WarriorWeaponFamily.BOW), 1e-9, "弓没有剑身");
        assertTrue(WarriorBladeGeometry.length(WarriorWeaponFamily.GREATSWORD)
                > WarriorBladeGeometry.length(WarriorWeaponFamily.LONGSWORD), "重剑比长剑长");
        assertTrue(WarriorBladeGeometry.length(WarriorWeaponFamily.HAMMER)
                > WarriorBladeGeometry.length(null), "锤比斧长");
        assertEquals(1.05, WarriorBladeGeometry.length(null), 1e-9, "斧（没有连招家族）用短刃");
        // 2026-11-02 模型整体放大，刀光长度同步放大；这里钉住"不能比剑还短"
        assertTrue(WarriorBladeGeometry.length(WarriorWeaponFamily.LONGSWORD) >= 1.2,
                "长剑模型放大后剑身长度也要跟上");
    }

    @Test
    void swingProgressIsClamped() {
        Vec3 beyond = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), 0, 0, 4.0, 1.0)[2];
        Vec3 full = WarriorBladeGeometry.blade(new Vec3(0, 64, 0), 0, 0, 1.0, 1.0)[2];
        assertEquals(full.y, beyond.y, 1e-9, "超范围的挥砍进度要按 1 处理");
        assertEquals(0.0, WarriorBladeGeometry.clamp01(-3), 1e-9);
    }
}
