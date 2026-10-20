package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.fire.TNMeteorSplashEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 陨星坠溅出来的岩浆：<b>什么都不画</b>。
 *
 * <p>这个实体本身没有任何几何体 —— 它的全部表现都是自己<b>真实摆出去又收回来</b>的岩浆方块
 * （见 {@code magic/fire/TNMeteorSplashEntity}）。所以这里只留一个空渲染器：
 * 一是占住"实体 → 渲染器"的注册（缺了会崩），二是把 {@code OrbEntityRendererCoverageTest}
 * 的覆盖率检查喂饱 ✓
 *
 * <p>{@code shadowRadius = 0}：它没有可见的形状，投影只会在地上凭空多一块阴影 ✗
 */
public final class TNMeteorSplashRenderer extends EntityRenderer<TNMeteorSplashEntity> {

    /** 只是占位（这里不画任何贴图），但 getTextureLocation 不能返回 null ✗ */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/burn_flame.png");

    public TNMeteorSplashRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(TNMeteorSplashEntity entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        // 故意留空：表现是真实的岩浆方块，不是模型
    }

    @Override
    public ResourceLocation getTextureLocation(TNMeteorSplashEntity entity) {
        return TEXTURE;
    }
}
