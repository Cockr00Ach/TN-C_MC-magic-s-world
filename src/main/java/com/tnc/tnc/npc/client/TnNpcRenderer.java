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

        // ★ 盔甲层（2026-09-22 加）：让 NPC 身上穿的装备**看得见**。
        //
        //   为什么需要它：HumanoidMobRenderer 不像 PlayerRenderer 那样自带盔甲渲染，
        //   不加这一层的话，即使给 NPC 穿上胸甲也完全看不出来。
        //
        //   模型层用 **ZOMBIE 那两套**（内层/外层盔甲）而不是 PLAYER 的：
        //   僵尸是"经典宽臂"人形，和我们用的骨架一致；PLAYER_INNER_ARMOR 是细臂版本，
        //   套上来会和我们的手臂对不齐。
        //
        //   有了这一层，"帅气"就多了一条低成本的路：给 NPC 穿装备（见 TnNpcEquipment），
        //   比重新建模便宜得多。
        this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(
                this,
                new net.minecraft.client.model.HumanoidModel<>(
                        context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(
                        context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ZOMBIE_OUTER_ARMOR)),
                // 第 4 个参数是 ModelManager（不是 EntityModelSet）—— 读 javap 签名确认的
                net.minecraft.client.Minecraft.getInstance().getModelManager()));
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
