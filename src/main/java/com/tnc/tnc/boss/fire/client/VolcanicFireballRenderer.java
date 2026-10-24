package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicFireballEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Fireball projectile: the author-edited block model core, plus surface flame particles. */
public final class VolcanicFireballRenderer extends FireballModelRenderer<VolcanicFireballEntity> {

    public VolcanicFireballRenderer(EntityRendererProvider.Context context) {
        super(context, 2.0F);
    }
}
