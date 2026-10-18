package com.tnc.tnc.life.routes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class RouteClient {
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{RouteContent.PLANTS.values().forEach(b->net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(b.get(),net.minecraft.client.renderer.RenderType.cutout()));net.minecraft.client.gui.screens.MenuScreens.register(RouteContent.MENU.get(),Screen::new);net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(RouteContent.MANA.get(),net.minecraft.client.renderer.RenderType.translucent());net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(RouteContent.MANA_FLOW.get(),net.minecraft.client.renderer.RenderType.translucent());});}
    @SubscribeEvent public static void models(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e){for(String id:java.util.List.of("mana_mirror","star_mirror"))for(String part:java.util.List.of("base","head"))e.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","block/"+id+"_"+part));}
    @SubscribeEvent public static void layers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(RouteMechModel.LAYER,RouteMechModel::layer);}
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(RouteContent.NODE_ENTITY.get(),RouteNodeRenderer::new);e.registerBlockEntityRenderer(RouteContent.PLANT_ENTITY.get(),RoutePlantRenderer::new);}
    public static final class Screen extends com.tnc.tnc.client.ui.ArcaneContainerScreen<RouteMenu> {
        public Screen(RouteMenu menu,net.minecraft.world.entity.player.Inventory inv,net.minecraft.network.chat.Component title){super(menu,inv,title);imageWidth=196;imageHeight=207;inventoryLabelY=110;titleLabelX=12;}
        @Override protected void init(){super.init();if(menu.kind()==RouteKind.INFUSER)addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("注入自身魔力 · 5/秒"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+18,topPos+88,160,16).build());}
        @Override protected void renderBg(net.minecraft.client.gui.GuiGraphics g,float pt,int mx,int my){com.tnc.tnc.client.ui.ArcaneGui.frame(g,leftPos-3,topPos-3,202,213);
            g.fill(leftPos+18,topPos+29,leftPos+178,topPos+34,0xff202a32);g.fill(leftPos+18,topPos+29,leftPos+18+160*menu.value(0)/Math.max(1,menu.value(1)),topPos+34,0xff62cebb);
            for(var s:menu.slots){com.tnc.tnc.client.ui.ArcaneGui.slot(g,leftPos+s.x,topPos+s.y);}}
        @Override protected void renderLabels(net.minecraft.client.gui.GuiGraphics g,int mx,int my){g.drawString(font,title,12,8,0xfff0f4df,false);g.drawString(font,menu.value(0)+" / "+menu.value(1)+" 魔力",18,37,0xffa4ceca,false);if(minecraft.level!=null&&minecraft.level.getBlockEntity(menu.pos) instanceof RouteNodeEntity be)g.drawString(font,font.plainSubstrByWidth(be.status,164),18,48,0xffa4ceca,false);g.drawString(font,"输入",20,79,0xffa4ceca,false);g.drawString(font,"成品",108,78,0xffa4ceca,false);g.drawString(font,"辅助",150,78,0xffa4ceca,false);
            if(menu.kind()!=RouteKind.INFUSER){g.drawString(font,"流入 "+menu.value(2)+" · 流出 "+menu.value(3)+" /秒",18,93,0xffa4ceca,false);}g.drawString(font,"背包",17,110,0xffa4ceca,false);}

    }
}
