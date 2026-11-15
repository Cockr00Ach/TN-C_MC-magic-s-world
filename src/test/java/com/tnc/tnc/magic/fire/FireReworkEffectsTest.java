package com.tnc.tnc.magic.fire;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FireReworkEffectsTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static FireReworkEffects.Cue cue(Vec3 position, Vec3 direction, float radius, float heat, int life) {
        return new FireReworkEffects.Cue(DIMENSION, FireReworkEffects.Kind.SEED, position, direction, radius, heat, life, null);
    }
    @Test void acceptsTheFullSeedDelayWithoutAnOwner() {
        var cue = cue(Vec3.ZERO, Vec3.ZERO, 3, 1, 60);
        assertEquals(60, cue.lifetime()); assertNull(cue.caster());
        assertEquals(FireReworkEffects.Kind.SEED, cue.kind());
    }
    @Test void rejectsNonFiniteGeometryBeforeItCanReachTheRenderer() {
        assertThrows(IllegalArgumentException.class, () -> cue(new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 1, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, new Vec3(0, Double.POSITIVE_INFINITY, 0), 1, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> cue(new Vec3(1e100, 0, 0), Vec3.ZERO, 1, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, Vec3.ZERO, Float.NaN, 1, 20));
    }
    @Test void boundsVisualAreaDurationAndHeatIndependentlyOfDamage() {
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, Vec3.ZERO, 33, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, Vec3.ZERO, 1, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, Vec3.ZERO, 1, 1, 101));
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, Vec3.ZERO, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> cue(Vec3.ZERO, new Vec3(257, 0, 0), 1, 1, 20));
        assertDoesNotThrow(() -> cue(Vec3.ZERO, new Vec3(256, 0, 0), 32, 1, 100));
    }
}
