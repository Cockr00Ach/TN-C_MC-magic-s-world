package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 让 Minecraft <b>真正烘焙</b>我们的投射物模型 —— 这是"紫黑方块"的最后一环。
 *
 * <h2>为什么需要这个类（从别的模组反编译出来的标准答案）</h2>
 * 能正常显示自制投射物模型的模组（如 {@code ysjxspells}）都做了三件事：
 * <ol>
 *   <li>{@code CustomModels.registerModelIds(list)} —— 告诉 SpellEngine 这些号要用
 *       （我们已做 ✓）</li>
 *   <li><b>{@code ModelEvent.RegisterAdditional} 事件 —— 让 Minecraft 去烘焙这些模型文件</b>
 *       ← <b>我们之前漏掉了这一步 ✗</b>，所以文件在 jar 里、号登记了，但<b>从未被烘焙</b>，
 *       引擎 {@code getModelManager().getModel(id)} 永远返回 null → 渲染紫黑占位方块。</li>
 *   <li>模型号用自己的命名空间（{@code tnc:projectile/xxx}）—— 只要前两步都做对就行 ✓</li>
 * </ol>
 *
 * <p>引擎侧（Fabric 构建）对应做法是 {@code ModelLoadingPlugin} →
 * {@code Context.addModels(CustomModelRegistry.modelIds)}；Forge 侧的等价物就是本事件 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TNModelBaking {

    /** 需要被烘焙的投射物模型（与 TNProjectileModels 登记给 SpellEngine 的号一致）。 */
    private static final String[] MODELS = {
            "projectile/lightingball",
            "projectile/waterball",
            "projectile/fireball",
            "projectile/fire_ball",
            "projectile/fire_ray",
            "projectile/thunder_ball",
            "projectile/thunder_orb",
            "projectile/tnc_ball_v2",
    };

    private TNModelBaking() {
    }

    /**
     * ★ 关键：把模型注册成"额外模型"，Minecraft 才会把它们加入烘焙队列。
     *
     * <p>1.20.1 的约定：额外模型用 {@link ModelResourceLocation} + 变体 {@code "standalone"} 表示，
     * 与 {@code models/**} 路径对应。
     */
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        for (String path : MODELS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
            event.register(new ModelResourceLocation(id, "standalone"));
        }
    }
}
