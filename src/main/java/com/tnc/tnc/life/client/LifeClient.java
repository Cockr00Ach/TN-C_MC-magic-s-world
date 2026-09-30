package com.tnc.tnc.life.client;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.OpenTravelBagPacket;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ScreenEvent;

@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class LifeClient {
    private static final java.util.Map<InventoryScreen,Button> BAGS=new java.util.WeakHashMap<>();
    @SubscribeEvent public static void inventory(ScreenEvent.Init.Post e){if(e.getScreen() instanceof InventoryScreen s){
        var button=Button.builder(Component.literal("行囊"),b->MagicStoneNetwork.CHANNEL.sendToServer(new OpenTravelBagPacket())).bounds(s.getGuiLeft()+39,Math.max(2,s.getGuiTop()-22),42,20).build();
        BAGS.put(s,button);e.addListener(button);
    }}
    @SubscribeEvent public static void align(ScreenEvent.Render.Pre e){if(e.getScreen() instanceof InventoryScreen s){
        var b=BAGS.get(s);if(b!=null){b.setX(s.getGuiLeft()+39);b.setY(Math.max(2,s.getGuiTop()-22));}
    }}
    @Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Setup {@SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(TNMod.TRAVEL_BAG_MENU.get(),TravelBagScreen::new));}}
}
