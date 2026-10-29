package com.tnc.tnc.boss.fire.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.fire.TortoiseRemainsEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
/** Low basalt/blackstone mound, with normal world lighting and no emissive layer. */
public final class TortoiseRemainsRenderer extends EntityRenderer<TortoiseRemainsEntity> {
    // x, z, width, height, depth, yaw, base height; bottoms stay above ground.
    private static final float[][] ROCKS = {
        {-1.35F,-.65F,1.2F,.55F,1.1F,18,0}, {-.15F,-.95F,1.5F,.75F,1.2F,-12,0},
        {1.25F,-.55F,1.2F,.5F,1.1F,32,0}, {-1.2F,.65F,1.3F,.6F,1.1F,-25,0},
        {.15F,.8F,1.5F,.7F,1.25F,11,0}, {1.3F,.65F,1.1F,.5F,1.1F,-14,0},
        {-.65F,-.2F,1.35F,.75F,1.3F,22,.4F}, {.65F,.1F,1.25F,.7F,1.25F,-18,.4F},
        {0,0,1,.5F,1,8,.95F}, {-2,.35F,.5F,.25F,.6F,35,0},
        {1.95F,-.2F,.6F,.3F,.5F,-20,0}, {.8F,1.65F,.6F,.25F,.5F,16,0}
    };
    public TortoiseRemainsRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 2.2F;
    }
    @Override public void render(TortoiseRemainsEntity entity, float yaw, float partial, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        for (int i = 0; i < ROCKS.length; i++) {
            float[] rock = ROCKS[i];
            poses.pushPose();
            poses.translate(rock[0], .015F + rock[6], rock[1]);
            poses.mulPose(Axis.YP.rotationDegrees(rock[5]));
            poses.scale(rock[2], rock[3], rock[4]);
            poses.translate(-.5F, 0, -.5F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    (i % 3 == 0 ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState(),
                    poses, buffers, light, OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        poses.popPose();
        super.render(entity, yaw, partial, poses, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(TortoiseRemainsEntity entity) {
        return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;
    }
}
