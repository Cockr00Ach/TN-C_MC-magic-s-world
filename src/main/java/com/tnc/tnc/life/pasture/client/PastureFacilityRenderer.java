package com.tnc.tnc.life.pasture.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.life.pasture.PastureFacilityBlock;
import com.tnc.tnc.life.pasture.PastureFacilityEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws saved inventory, never decorative fake eggs, bottles or piles of feed. */
public final class PastureFacilityRenderer implements BlockEntityRenderer<PastureFacilityEntity> {
    private final ItemRenderer items;
    public PastureFacilityRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(PastureFacilityEntity facility, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        if (facility.getLevel() == null || !(facility.getBlockState().getBlock() instanceof PastureFacilityBlock block)) return;
        PastureFacilityBlock.Kind kind = block.kind();
        if (kind == PastureFacilityBlock.Kind.LIGHT || kind == PastureFacilityBlock.Kind.MARKER || kind == PastureFacilityBlock.Kind.CHARGING) return;
        for (int slot = 0; slot < 4; slot++) {
            var stack = facility.getItem(slot);
            if (stack.isEmpty()) continue;
            if (kind == PastureFacilityBlock.Kind.BOTTLE && slot > 0) continue;
            pose.pushPose();
            boolean bottle = kind == PastureFacilityBlock.Kind.BOTTLE;
            pose.translate(bottle ? .5D : .31D + (slot % 2) * .38D,
                    bottle ? .25D : kind == PastureFacilityBlock.Kind.TROUGH ? .33D : .26D,
                    bottle ? .5D : .31D + (slot / 2) * .38D);
            if (!bottle) pose.mulPose(Axis.YP.rotationDegrees(slot * 37.0F));
            pose.scale(bottle ? .95F : .53F, bottle ? .95F : .53F, bottle ? .95F : .53F);
            items.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, pose, buffers,
                    facility.getLevel(), (int)facility.getBlockPos().asLong() + slot);
            pose.popPose();
        }
    }
}
