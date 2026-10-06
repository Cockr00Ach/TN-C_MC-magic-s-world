package com.tnc.tnc.client;

import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.DarkFogCloudEntity;
import com.tnc.tnc.magic.TNDarkDrainEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Loaded only when SpellEngine is present; keeps optional renderer types out of event discovery. */
final class TNFogClientRegistration {
    private TNFogClientRegistration() {}
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TNOrbEntities.FOG.get(),
                context -> new net.spell_engine.client.render.SpellCloudRenderer<DarkFogCloudEntity>(context));
        event.registerEntityRenderer(TNOrbEntities.DRAIN.get(), NoopRenderer::new);
        // ★ 黑夜之手的"抓住不放的那只手"（2026-10-10 重做）—— 它**要看得见** ✓
        //   （作者：「他名字虽然叫做不可视，但你别游戏里做的啥都看不见，我们还是要做出来要的特效的」）。
        //   ⚠️ 这一行**千万不能漏** ✗：实体类型没有渲染器 ⇒ 一生成就
        //   `entityrenderer is null` 卡退（OrbEntityRendererCoverageTest 会在构建期拦住 ✓）。
        //   放在这个"只在装了引擎时才加载"的类里，是因为它的渲染器要用引擎的模型通道
        //   （CustomModels / SpellModelHelper 都来自 SpellEngine ✓，见 TNNightGripRenderer）。
        event.registerEntityRenderer(TNOrbEntities.NIGHT_GRIP.get(),
                com.tnc.tnc.client.TNNightGripRenderer::new);
    }
    private static final class NoopRenderer extends net.minecraft.client.renderer.entity.EntityRenderer<TNDarkDrainEntity> {
        NoopRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) { super(context); }
        @Override public net.minecraft.resources.ResourceLocation getTextureLocation(TNDarkDrainEntity entity) {
            return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES;
        }
        @Override public void render(TNDarkDrainEntity entity, float yaw, float tick,
                com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffer, int light) {}
    }
}
