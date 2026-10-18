package com.tnc.tnc.client;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.WindEarthVisualMechanics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WindEarthClientRegistration {
    private WindEarthClientRegistration(){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WindEarthVisualMechanics.VISUAL.get(),WindEarthVisualRenderer::new);
        event.registerEntityRenderer(WindEarthVisualMechanics.ATTACK.get(),EarthAttackRenderer::new);
    }
    @SubscribeEvent public static void alias(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) {
        int valid=0;
        for(String path:TNDirectSpellModels.PATHS) {
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",path);
            var baked=event.getModels().get(id);
            if(baked!=null&&!baked.getParticleIcon().contents().name().getPath().equals("missingno"))valid++;
            else org.slf4j.LoggerFactory.getLogger("TN-C/solid-models").error("Missing baked model or texture: {}",id);
        }
        org.slf4j.LoggerFactory.getLogger("TN-C/solid-models").info("Solid earth and original wind model bake validation: {}/{}",valid,TNDirectSpellModels.PATHS.size());
    }
}
