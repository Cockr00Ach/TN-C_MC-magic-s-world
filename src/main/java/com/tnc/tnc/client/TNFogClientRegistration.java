package com.tnc.tnc.client;

import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.DarkFogCloudEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Loaded only when SpellEngine is present; keeps optional renderer types out of event discovery. */
final class TNFogClientRegistration {
    private TNFogClientRegistration() {}
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TNOrbEntities.FOG.get(),
                context -> new net.spell_engine.client.render.SpellCloudRenderer<DarkFogCloudEntity>(context));
    }
}
