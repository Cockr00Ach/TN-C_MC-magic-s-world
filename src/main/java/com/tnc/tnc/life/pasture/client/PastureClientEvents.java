package com.tnc.tnc.life.pasture.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.pasture.PastureRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PastureClientEvents {
    private PastureClientEvents() {}

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for (String material : new String[]{"hay", "grain", "roots", "fruit", "fish", "fungus", "pellets"})
            event.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "block/feed_" + material));
    }

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        PastureGeometry.LAYERS.forEach((species, layer) -> event.registerLayerDefinition(layer, () -> PastureGeometry.layer(species)));
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        PastureRegistry.TYPES.forEach((species, type) -> event.registerEntityRenderer(type, context -> new PastureAnimalRenderer(context, species)));
        event.registerBlockEntityRenderer(PastureRegistry.FACILITY_ENTITY, PastureFacilityRenderer::new);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PastureRegistry.MENU_TYPE, PastureFacilityScreen::new);
            PastureRegistry.BLOCKS.forEach((id, block) -> ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout()));
            for (String id : new String[]{"mana_bottle", "refined_mana_bottle"}) {
                int capacity = id.equals("mana_bottle") ? 24 : 72;
                ItemProperties.register(PastureRegistry.item(id), ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "mana_fill"),
                        (stack, level, entity, seed) -> {
                            int stored = stack.hasTag() ? stack.getTag().getInt("StoredMana") : 0;
                            return stored <= 0 ? 0.0F : Math.min(4, (stored * 4 + capacity - 1) / capacity) / 4.0F;
                        });
            }
        });
    }
}
