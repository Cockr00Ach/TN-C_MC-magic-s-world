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
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Draws saved inventory, never decorative fake eggs, bottles or piles of feed. */
public final class PastureFacilityRenderer implements BlockEntityRenderer<PastureFacilityEntity> {
    private final ItemRenderer items;
    public PastureFacilityRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(PastureFacilityEntity facility, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        if (facility.getLevel() == null || !(facility.getBlockState().getBlock() instanceof PastureFacilityBlock block)) return;
        PastureFacilityBlock.Kind kind = block.kind();
        if (kind == PastureFacilityBlock.Kind.LIGHT || kind == PastureFacilityBlock.Kind.MARKER || kind == PastureFacilityBlock.Kind.CHARGING) return;
        if (kind == PastureFacilityBlock.Kind.TROUGH) {
            var feeds = new java.util.ArrayList<ItemStack>(4);
            for (int slot = 0; slot < 4; slot++) {
                var saved = facility.getItem(slot);
                if (saved.isEmpty()) continue;
                ItemStack existing = feeds.stream().filter(feed -> ItemStack.isSameItemSameTags(feed, saved)).findFirst().orElse(null);
                if (existing == null) feeds.add(saved.copy());
                else existing.grow(saved.getCount());
            }
            for (int pile = 0; pile < feeds.size(); pile++)
                renderFeed(feeds.get(pile), pile, feeds.size(), pose, buffers, light, overlay);
            return;
        }
        for (int slot = 0; slot < 4; slot++) {
            var stack = facility.getItem(slot);
            if (stack.isEmpty()) continue;
            if (kind == PastureFacilityBlock.Kind.BOTTLE && slot > 0) continue;
            pose.pushPose();
            boolean bottle = kind == PastureFacilityBlock.Kind.BOTTLE;
            pose.translate(bottle ? .5D : .31D + (slot % 2) * .38D,
                    bottle ? .25D : .26D,
                    bottle ? .5D : .31D + (slot / 2) * .38D);
            if (!bottle) pose.mulPose(Axis.YP.rotationDegrees(slot * 37.0F));
            pose.scale(bottle ? .95F : .53F, bottle ? .95F : .53F, bottle ? .95F : .53F);
            items.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, pose, buffers,
                    facility.getLevel(), (int)facility.getBlockPos().asLong() + slot);
            pose.popPose();
        }
    }

    /** The saved item decides the pile material; its real count decides fullness. */
    private static void renderFeed(ItemStack stack, int slot, int occupied, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;
        String material = switch (id.getPath()) {
            case "bellwool_fodder", "warm_feed", "sugar_cane" -> "hay";
            case "wheat", "wheat_seeds" -> "grain";
            case "potato", "carrot", "beetroot" -> "roots";
            case "apple", "sweet_berries", "melon_slice" -> "fruit";
            case "cod" -> "fish";
            case "brown_mushroom" -> "fungus";
            default -> "pellets";
        };
        var mc = Minecraft.getInstance();
        var model = mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc", "block/feed_" + material));
        pose.pushPose();
        // Merge identical inventory stacks before drawing. Adding a seventeenth
        // portion keeps the central pile and makes it grow instead of splitting.
        float firstStack = Math.min(16, stack.getCount()) / 16F;
        float extraStacks = Math.max(0, Math.min(64, stack.getCount()) - 16) / 48F;
        float horizontal = occupied == 1 ? .42F + .28F * firstStack + .10F * extraStacks
                                        : .24F + .14F * firstStack;
        double x = occupied == 1 ? (1D - horizontal) / 2 : .14D + slot % 2 * .315D + (.38D - horizontal) / 2;
        double z = occupied == 1 ? (1D - horizontal) / 2 : .14D + slot / 2 * .315D + (.38D - horizontal) / 2;
        pose.translate(x, 3D / 16D, z);
        float fullness = .50F + firstStack * .50F + extraStacks * .20F;
        pose.scale(horizontal, .85F * fullness, horizontal);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                null, model, 1, 1, 1, light, overlay);
        pose.popPose();
    }
}
