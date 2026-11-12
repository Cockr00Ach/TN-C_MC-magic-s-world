package com.tnc.tnc.mount.client;

import com.tnc.tnc.mount.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class LandMountRenderer extends GeoEntityRenderer<LandMount>{
    public LandMountRenderer(EntityRendererProvider.Context context){super(context,new Model());withScale(.65f);shadowRadius=.65f;}
    private static final class Model extends GeoModel<LandMount>{
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
