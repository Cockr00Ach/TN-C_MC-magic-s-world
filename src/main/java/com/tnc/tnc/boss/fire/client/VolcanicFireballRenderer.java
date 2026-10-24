package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicFireballEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The fireball itself is drawn by particles (FireBlobFx); this renderer draws nothing.
 *
 * <p>It exists only because Forge's EntityRenderDispatcher returns null for an entity type
 * without a registered renderer (vanilla would fall back to the default renderer), which
 * then NPEs in LevelRenderer. So tnc:volcanic_fireball needs a no-op registration.</p>
 */
public class VolcanicFireballRenderer extends EntityRenderer<VolcanicFireballEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tnc", "textures/particle/fire_blob_0.png");

    public VolcanicFireballRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(VolcanicFireballEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(VolcanicFireballEntity entity) {
        return TEXTURE;
    }
}