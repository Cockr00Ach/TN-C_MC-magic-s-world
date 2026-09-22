package com.tnc.tnc.npc.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.npc.TnDialogueNpc;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * TN-C 所有"皮肤 NPC"共用的渲染器：原版人形渲染 + 各自的皮肤贴图。
 *
 * <p>贴图路径遵循原版约定 —— {@code assets/<namespace>/textures/entity/<名字>.png}，
 * 名字由实体自己给出（{@link TnDialogueNpc#skinName()}），所以
 * <b>加一个 NPC 不用再写渲染器</b>，只要放一张同名 PNG。
 */
public class TnNpcRenderer extends HumanoidMobRenderer<TnDialogueNpc, TnHumanoidNpcModel> {

    public TnNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new TnHumanoidNpcModel(context.bakeLayer(TnHumanoidNpcModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TnDialogueNpc entity) {
        return skinOf(entity);
    }

    /** 皮肤贴图路径：{@code tnc:textures/entity/<skinName>.png}。 */
    public static ResourceLocation skinOf(TnDialogueNpc entity) {
        return ResourceLocation.fromNamespaceAndPath(TNMod.MODID,
                "textures/entity/" + entity.skinName() + ".png");
    }
}
