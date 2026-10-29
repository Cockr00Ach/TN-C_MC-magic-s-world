package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.tnc.tnc.boss.fire.TortoiseQuake;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Optional crest accents, driven by the ground wave itself; no separate clock or terrain layer. */
public final class QuakeBandFx {
    private static final boolean ENABLED = Boolean.getBoolean("tnc.tortoise.quake.glow");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/quake_band.png"));
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(32768));

    private QuakeBandFx() {
    }

    static boolean enabled() {
        return ENABLED;
    }

    static VertexConsumer buffer() {
        return BUFFERS.getBuffer(TYPE);
    }

    static void endBatch() {
        BUFFERS.endBatch(TYPE);
    }

    static void draw(VertexConsumer buffer, Matrix4f matrix, Vec3 camera,
                     TortoiseQuake.Cell cell, double surfaceY, double age) {
        double delta = Math.abs(age - TortoiseQuake.arrival(cell.distance()));
        if (delta >= 0.8D) return;
        float x = (float) (cell.x() + 0.5D - camera.x);
        float z = (float) (cell.z() + 0.5D - camera.z);
        float y = (float) (surfaceY + 0.05D - camera.y);
        float alpha = (float) ((1.0D - delta / 0.8D) * 0.22D);
        vertex(buffer, matrix, x - 0.28F, y, z - 0.28F, 0, 0, alpha);
        vertex(buffer, matrix, x - 0.28F, y, z + 0.28F, 0, 1, alpha);
        vertex(buffer, matrix, x + 0.28F, y, z + 0.28F, 1, 1, alpha);
        vertex(buffer, matrix, x + 0.28F, y, z - 0.28F, 1, 0, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
                               float u, float v, float alpha) {
        buffer.vertex(matrix, x, y, z).color(1.0F, 0.83F, 0.48F, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0x00F000F0).normal(0, 1, 0).endVertex();
    }
}
