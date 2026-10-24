package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.VolcanicMortarEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Barrage mortar: smaller core model (1.35x) so a 21-ball volley stays readable. */
public final class VolcanicMortarRenderer extends FireballModelRenderer<VolcanicMortarEntity> {

    public VolcanicMortarRenderer(EntityRendererProvider.Context context) {
        super(context, 1.35F);
    }
}
