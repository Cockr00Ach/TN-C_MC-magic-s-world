package com.tnc.tnc.mount.client;

import com.tnc.tnc.mount.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class LandMountRenderer extends GeoEntityRenderer<LandMount>{
    public LandMountRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        float scale = 0.65f * LandMounts.SIZE_MULTIPLIER;
        withScale(scale);
        shadowRadius = scale;
    }
    private static final class Model extends GeoModel<LandMount>{
        @Override
        public void setCustomAnimations(LandMount mount, long instanceId, AnimationState<LandMount> state) {
            super.setCustomAnimations(mount, instanceId, state);
            if (mount.species().equals("rainbow_unicorn")) {
                float lean = mount.visualRunLean(state.getPartialTick());
                setRunHairPitch("run_mane_lean", lean, 5);
                setRunHairPitch("run_tail_lean", lean, 9);
            }
        }

        private void setRunHairPitch(String name, float lean, float degrees) {
            var bone = getAnimationProcessor().getBone(name);
            if (bone != null) {
                bone.setRotX((float) Math.toRadians(lean * degrees));
            }
        }

        private ResourceLocation resource(LandMount mount,String folder,String suffix){return ResourceLocation.fromNamespaceAndPath("tnc",folder+mount.species()+suffix);}
        @Override public ResourceLocation getModelResource(LandMount m){return resource(m,"geo/mount/",".geo.json");}
        @Override public ResourceLocation getTextureResource(LandMount m){return resource(m,"textures/entity/mount/",".png");}
        @Override public ResourceLocation getAnimationResource(LandMount m){return resource(m,"animations/mount/",".animation.json");}
    }
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Registration{
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){for(var t:java.util.List.of(LandMounts.UNICORN,LandMounts.GOAT,LandMounts.WOLF))e.registerEntityRenderer(t.get(),LandMountRenderer::new);}
    }
}
