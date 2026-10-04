package com.tnc.tnc.production.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.production.MagicForgeMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit development-only screenshot check in an isolated test world. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class ForgeGuiAudit {
    private static int ticks,stage;private static volatile boolean saving,saved;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!Boolean.getBoolean("tnc.forgeGuiAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(stage==0){
            mc.options.guiScale().set(2);mc.resizeDisplay();
            var menu=new MagicForgeMenu(0,mc.player.getInventory(),new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(mc.player.blockPosition()));
            menu.setData(0,125);menu.setData(1,45);menu.setData(2,80);menu.setData(4,1);
            mc.setScreen(new MagicForgeScreen(menu,mc.player.getInventory(),Component.literal("炼金炉")));stage=1;ticks=0;
        }
        if(++ticks<40||saving)return;
        if(!saved){
            saving=true;
            Screenshot.grab(mc.gameDirectory,stage==1?"forge-gui-feedback.png":"forge-guide-feedback.png",mc.getMainRenderTarget(),message->{System.out.println("TN-C FORGE GUI AUDIT: "+message.getString());saved=true;saving=false;});return;
        }
        if(stage==1){ForgeGuideScreen.open(mc.screen);stage=2;ticks=0;saved=false;}else mc.stop();
    }
}
