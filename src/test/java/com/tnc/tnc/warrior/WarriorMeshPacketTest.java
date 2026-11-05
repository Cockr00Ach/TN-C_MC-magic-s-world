package com.tnc.tnc.warrior;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorMeshPacketTest {
    @Test void wavePreservesAuthoritativeTrajectoryAndAppearance() {
        for (boolean ground : new boolean[]{false, true}) {
            var packet = new WarriorWavePacket(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                    new Vec3(12, 64, -30), new Vec3(0, 0, 1), ground, .95, 9, 1.8, 0xE9FCFF, 0x2464C0, 500);
            var bytes = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.encode(bytes); assertEquals(packet, WarriorWavePacket.decode(bytes)); assertEquals(0, bytes.readableBytes()); }
            finally { bytes.release(); }
        }
    }
    @Test void malformedGeometryIsRejected() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new WarriorWavePacket(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), Vec3.ZERO,
                    new Vec3(0, 0, 1), false, Double.NaN, 9, 1, 0, 0, 0).encode(bytes);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> WarriorWavePacket.decode(bytes));
        } finally { bytes.release(); }
    }
    @Test void gripStaysFixedForBothHandsAndRotatedNonUniformModels() {
        for (float side : new float[]{1, -1}) {
            Matrix4f item = new Matrix4f().translation(side * -.4375f, .390625f, .078125f)
                    .rotateXYZ((float)Math.toRadians(-79), 0, (float)Math.toRadians(side * 89)).scale(1.45f, 1.5f, 1.5f);
            Vector3f anchor = WarriorGripGeometry.anchor(item, 7 / 16f, .5f / 16, 2.05f / 16);
            for (double scale : new double[]{1.02, 1.225, 1.45}) {
                Vector3f enlargedGrip = new Vector3f(anchor).mul((float)scale)
                        .add(WarriorGripGeometry.compensation(anchor, scale));
                assertEquals(0, anchor.distance(enlargedGrip), 1e-6);
            }
        }
    }
    @Test void remotePhantomScaleRoundTripsIndependentlyOfObserver() {
        var bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new WarriorPhantomPacket(java.util.UUID.randomUUID(), 1.45f);
            packet.encode(bytes); assertEquals(packet, WarriorPhantomPacket.decode(bytes));
        } finally { bytes.release(); }
    }
}
