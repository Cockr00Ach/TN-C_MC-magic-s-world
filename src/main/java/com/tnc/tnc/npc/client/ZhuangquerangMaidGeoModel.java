package com.tnc.tnc.npc.client;

import com.tnc.tnc.npc.ZhuangquerangMaidNpcEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 庄鹊让（女仆模型版）的 GeckoLib 模型 —— 只回答"用哪个模型/贴图/动画"三个问题 ✓。
 *
 * <p>为什么不用 TLM 自己那套渲染：TLM 的 {@code BedrockModel.setupAnim} 走的是它**内置的 JS 动画引擎**
 * （{@code javax.script.Invocable}，见反编译签名），而且只对实现了 {@code IMaid} 的实体生效 ✗ ——
 * 我们的 NPC 不是女仆实体，套上去要伪造一整套女仆状态，不如直接用 GeckoLib 画同一个模型文件 ✓。
 *
 * <p><b>姿态说明</b>：咲夜的模型是"旧格式"（骨骼 {@code head/body/armRight…}），它的静止姿态就是
 * 站立姿态 ✓（TLM 的手办/雕像也是这么静态展示的），所以我们只叠一个很轻的 idle（只动头）✓。
 */
public class ZhuangquerangMaidGeoModel extends GeoModel<ZhuangquerangMaidNpcEntity> {

    @Override
    public ResourceLocation getModelResource(ZhuangquerangMaidNpcEntity animatable) {
        return ZhuangquerangMaidNpcEntity.modelResource();
    }

    @Override
    public ResourceLocation getTextureResource(ZhuangquerangMaidNpcEntity animatable) {
        return ZhuangquerangMaidNpcEntity.textureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(ZhuangquerangMaidNpcEntity animatable) {
        return ZhuangquerangMaidNpcEntity.animationResource();
    }

    /**
     * 骨骼名对不上时**不要崩** ✗ —— 我们自己写的 idle 只动 {@code head}，
     * 但万一以后换了模型（比如换成 GeckoLib 骨架的女仆模型），缺骨骼只该静默跳过 ✓。
     */
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
