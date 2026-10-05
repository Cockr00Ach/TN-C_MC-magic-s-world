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
