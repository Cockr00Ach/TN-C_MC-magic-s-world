package com.tnc.tnc.npc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.npc.SelfNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Self 的渲染器：原版人形渲染 + 我们自己的皮肤贴图。
 *
 * <p>贴图路径遵循原版约定 —— {@code assets/<namespace>/textures/entity/<名字>.png}，
 * 所以这里写的 {@code tnc:textures/entity/self.png} 对应
 * {@code src/main/resources/assets/tnc/textures/entity/self.png}。
 */
public class SelfNpcRenderer extends HumanoidMobRenderer<SelfNpcEntity, SelfNpcModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "textures/entity/self.png");

    public SelfNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new SelfNpcModel(context.bakeLayer(SelfNpcModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SelfNpcEntity entity) {
        return TEXTURE;
    }
}
