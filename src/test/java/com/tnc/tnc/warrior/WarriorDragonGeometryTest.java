package com.tnc.tnc.warrior;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the production dragon mesh for finite, non-degenerate, bounded geometry.
 * Also exports the exact runtime triangles for the offline visual preview;
 * this export is not a substitute for a real-client rendering check.
 */
class WarriorDragonGeometryTest {

    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 DIR = new Vec3(0, 0, 1);
    private static final Vec3 SIDE = new Vec3(-1, 0, 0);

    @Test void headAdvancesOverTicksAndStops() {
        assertEquals(0.0, WarriorDragonGeometry.head(0), 1.0E-9);
        assertEquals(0.5, WarriorDragonGeometry.head(WarriorDragonGeometry.TRAVEL / 2.0), 1.0E-9);
        assertEquals(1.0, WarriorDragonGeometry.head(WarriorDragonGeometry.TRAVEL), 1.0E-9);
        assertEquals(1.0, WarriorDragonGeometry.head(WarriorDragonGeometry.LIFE + 5), 1.0E-9);
        assertEquals(1.0f, WarriorDragonGeometry.fade(0), 1.0E-6f);
        assertEquals(1.0f, WarriorDragonGeometry.fade(WarriorDragonGeometry.TRAVEL), 1.0E-6f);
        assertEquals(0.0f, WarriorDragonGeometry.fade(WarriorDragonGeometry.LIFE), 1.0E-6f);
        float previous = WarriorDragonGeometry.fade(WarriorDragonGeometry.TRAVEL);
        for (double age = WarriorDragonGeometry.TRAVEL; age <= WarriorDragonGeometry.LIFE; age += 0.5) {
            float current = WarriorDragonGeometry.fade(age);
            assertTrue(current <= previous + 1.0E-6f, "fade must not go back up at age " + age);
            previous = current;
        }
    }

    @Test void spineStaysFiniteAndTubular() {
        for (double s = 0; s <= 1.0001; s += 0.02) {
            Vec3 point = WarriorDragonGeometry.path(ORIGIN, DIR, SIDE, s);
            assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z),
                    "path must be finite at s=" + s);
            Vec3 tangent = WarriorDragonGeometry.tangent(ORIGIN, DIR, SIDE, s, 1.0);
            assertEquals(1.0, tangent.length(), 1.0E-6, "tangent must be unit length at s=" + s);
            double radius = WarriorDragonGeometry.radius(s);
            assertTrue(radius > 0.03 && radius <= 0.34, "radius out of range at s=" + s + ": " + radius);
        }
        assertEquals(0.0, ORIGIN.distanceTo(WarriorDragonGeometry.path(ORIGIN, DIR, SIDE, 0)), 1.0E-9);
        assertEquals(WarriorDragonGeometry.LENGTH, WarriorDragonGeometry.path(ORIGIN, DIR, SIDE, 1).z, 1.0E-6);
        assertTrue(WarriorDragonGeometry.radius(0.5) > WarriorDragonGeometry.radius(0.02));
        assertTrue(WarriorDragonGeometry.radius(0.5) > WarriorDragonGeometry.radius(0.98));
    }

    @Test void trianglesAreSolidAndBounded() throws IOException {
        List<WarriorDragonGeometry.Tri> early = WarriorDragonGeometry.build(ORIGIN, DIR, SIDE, 0.4, 1.0f);
        List<WarriorDragonGeometry.Tri> late = WarriorDragonGeometry.build(ORIGIN, DIR, SIDE, 1.0, 1.0f);
        assertTrue(early.size()>4000 && early.size()<10000, "detailed block dragon within its render budget");
        assertEquals(early.size(), late.size());
        for (var tri : late) {
            assertTrue(tri.alpha() >= 0 && tri.alpha() <= 1, "alpha out of range: " + tri.alpha());
            for (Vec3 point : List.of(tri.a(), tri.b(), tri.c())) {
                assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z));
            }
            // 截面塌掉/拧成蝴蝶结时三角面积会接近 0：这是"梳齿"那次事故的哨兵
            double area = tri.b().subtract(tri.a()).cross(tri.c().subtract(tri.a())).length() * 0.5;
            assertTrue(area > 1.0E-6, "degenerate triangle, area=" + area);
        }
        assertTrue(WarriorDragonGeometry.build(ORIGIN, DIR, SIDE, 0.0, 1.0f).isEmpty());
        assertTrue(WarriorDragonGeometry.build(ORIGIN, DIR, SIDE, 1.0, 0.0f).stream()
                .allMatch(tri -> tri.alpha() == 0.0f));
        dumpForPreview();
    }

    /** 给 `tools/dragon_preview.py` 的离线预览：写的是渲染器真会画的那批三角形。 */
    private static void dumpForPreview() throws IOException {
        Path out = Path.of("build", "tnc-dragon-quads.csv");
        Files.createDirectories(out.getParent());
        StringBuilder text = new StringBuilder();
        for (double head : new double[]{0.45, 1.0}) {
            text.append("FRAME,").append(head).append('\n');
            for (var tri : WarriorDragonGeometry.build(ORIGIN, DIR, SIDE, head, 1.0f)) {
                text.append(tri.color()).append(',').append(tri.alpha()).append(',')
                        .append(tri.a().x).append(',').append(tri.a().y).append(',').append(tri.a().z).append(',')
                        .append(tri.b().x).append(',').append(tri.b().y).append(',').append(tri.b().z).append(',')
                        .append(tri.c().x).append(',').append(tri.c().y).append(',').append(tri.c().z).append('\n');
            }
        }
        Files.writeString(out, text.toString());
    }
}
