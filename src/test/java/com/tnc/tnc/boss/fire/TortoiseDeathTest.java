package com.tnc.tnc.boss.fire;

import com.tnc.tnc.boss.fire.client.DeathMeshClipper;
import com.tnc.tnc.boss.fire.client.DeathMeshClipper.Vertex;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TortoiseDeathTest {
    private static List<Vertex> face() {
        return List.of(new Vertex(0, 100, 0, 0, 0, 0), new Vertex(1, 100, 0, 1, 0, 0),
                new Vertex(1, 101, 0, 1, 1, 1), new Vertex(0, 101, 0, 0, 1, 1));
    }

    @Test void corpseRemainsUntilTheThreeSecondDissolveFinishes() {
        assertEquals(188, TortoiseDeath.DURATION);
        assertEquals(60, TortoiseDeath.DURATION - TortoiseDeath.DISSOLVE_START);
        assertEquals(0, TortoiseDeath.dissolved(127));
        assertEquals(0.5F, TortoiseDeath.dissolved(158));
        assertEquals(1, TortoiseDeath.dissolved(188));
        assertEquals(-1, TortoiseDeath.age(-1, 6000, 0.5F));
        assertEquals(50.5F, TortoiseDeath.age(1000, 1050, 0.5F));
    }

    @Test void cutoffDescendsMonotonicallyAndEndsBelowEveryGroundedPart() {
        for (boolean phase : new boolean[]{false, true}) {
            float previous = TortoiseDeath.cutoff(128, phase);
            for (float age = 128; age <= 188; age += 0.125F) {
                float value = TortoiseDeath.cutoff(age, phase);
                assertTrue(value <= previous);
                previous = value;
            }
            assertTrue(previous < 0);
        }
    }

    @Test void deathFlashIsLocalAndCoolingEndsBeforeDissolution() {
        assertEquals(1.4F, TortoiseDeath.glow(3), 0.0001F);
        assertEquals(0, TortoiseDeath.glow(94));
        assertTrue(TortoiseDeath.glow(109) > 0);
        for (int age = 116; age <= 188; age++) assertEquals(0, TortoiseDeath.glow(age));
        assertEquals(1, TortoiseDeath.cooling(128));
    }

    @Test void silenceAndParticleCapsArePreserved() {
        for (int age = 0; age < 44; age++) assertEquals(0, TortoiseDeath.particleBudget(age));
        for (int age = 116; age < 128; age++) assertEquals(0, TortoiseDeath.particleBudget(age));
        assertEquals(40, TortoiseDeath.particleBudget(44));
        assertEquals(40, TortoiseDeath.particleBudget(140));
        assertEquals(0, TortoiseDeath.particleBudget(188));
        for (float age = -1; age <= 190; age += 0.25F) assertTrue(TortoiseDeath.particleBudget(age) <= 40);
        assertEquals(0, TortoiseDeath.shake(43));
        assertTrue(TortoiseDeath.shake(44) <= 0.14F);
        assertEquals(0, TortoiseDeath.shake(52));
    }

    @Test void meshAboveThePlaneIsActuallyRemoved() {
        assertTrue(DeathMeshClipper.below(face(), -0.01F).isEmpty());
        assertEquals(face(), DeathMeshClipper.below(face(), 1.01F));
    }

    @Test void crossingFaceKeepsOnlyTheLowerHalfAndInterpolatesUvs() {
        List<Vertex> result = DeathMeshClipper.below(face(), 0.5F);
        assertEquals(4, result.size());
        assertEquals(2, result.stream().filter(v -> v.height() == 0.5F).count());
        for (Vertex vertex : result) {
            assertTrue(vertex.height() <= 0.5F);
            assertTrue(vertex.v() <= 0.5F);
            // Height is entity-relative; emitted vertex coordinates may be camera-relative.
            assertEquals(vertex.height() + 100, vertex.y(), 0.0001F);
        }
    }

    @Test void coincidentPlaneNeverCreatesNanOrDuplicateIntersections() {
        List<Vertex> result = DeathMeshClipper.below(face(), 0);
        assertEquals(2, result.size());
        for (Vertex v : result) {
            assertTrue(Float.isFinite(v.x()) && Float.isFinite(v.u()) && Float.isFinite(v.height()));
            assertEquals(0, v.height());
        }
        assertTrue(DeathMeshClipper.below(List.of(), 0).isEmpty());
    }
}
