package com.tnc.tnc.journey.client;
import net.minecraft.client.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class JourneyClient {
    private static Object welcomeConnection;
    private static boolean welcomeRequested;
    private static CompoundTag pendingWelcome;
    private static boolean nameConfirmed;
    public static void confirmedName(){nameConfirmed=true;pendingWelcome=null;welcomeRequested=true;}
    public static void queueWelcome(CompoundTag t){if(!nameConfirmed)pendingWelcome=t.copy();}
    private static void requestWelcome(Minecraft mc){
        if(mc.getConnection()!=welcomeConnection){welcomeConnection=mc.getConnection();welcomeRequested=false;pendingWelcome=null;nameConfirmed=false;}
        if(nameConfirmed||welcomeRequested||pendingWelcome!=null||mc.getConnection()==null)return;
        welcomeRequested=true;com.tnc.tnc.journey.JourneyNetwork.send(0,"welcome","");
    }
    private static void welcomeTick(Minecraft mc){
        if(mc.getConnection()!=welcomeConnection){welcomeConnection=mc.getConnection();welcomeRequested=false;pendingWelcome=null;nameConfirmed=false;}
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)return;
        if(mc.screen!=null&&!(mc.screen instanceof JourneyScreen))return;
        requestWelcome(mc);
        if(pendingWelcome!=null&&mc.screen==null){var t=pendingWelcome;pendingWelcome=null;mc.setScreen(new JourneyScreen(t));com.tnc.tnc.journey.JourneyNetwork.send(t.getLong("Token"),"welcome_seen","");}
    }
    private static String greeting="";private static long until;private static int entity;
    public static final KeyMapping SIP=new KeyMapping("key.tnc.elixir_sip",net.minecraftforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_H,"key.categories.tnc");
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup{@SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(SIP);}}
    public static void greeting(CompoundTag t){greeting=t.getString("Line");entity=t.getInt("Entity");until=System.currentTimeMillis()+9000;}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();welcomeTick(mc);while(SIP.consumeClick())if(mc.player!=null&&mc.screen==null)com.tnc.tnc.journey.JourneyNetwork.send(0,"sip","");}
    @SubscribeEvent public static void destiny(ScreenEvent.Opening e){if(e.getNewScreen()==null||!e.getNewScreen().getClass().getName().equals("forge.net.mca.client.gui.DestinyScreen"))return;try{var d=Class.forName("forge.net.mca.MCAClient").getMethod("getDestinyManager").invoke(null);d.getClass().getMethod("allowClosing").invoke(d);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("MCA destiny close bridge",ex);}e.setCanceled(true);requestWelcome(Minecraft.getInstance());}
    @SubscribeEvent public static void hiddenOutline(RenderHighlightEvent.Block e){var mc=Minecraft.getInstance();if(mc.level!=null&&!com.tnc.tnc.mana.sight.client.ManaSightClient.enabled()&&mc.level.getBlockState(e.getTarget().getBlockPos()).is(com.tnc.tnc.alchemy.AlchemyContent.HIDDEN_CACHE))e.setCanceled(true);}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||System.currentTimeMillis()>until)return;var npc=mc.level.getEntity(entity);if(npc==null||npc.distanceToSqr(mc.player)>256)return;var g=e.getGuiGraphics();var lines=mc.font.split(net.minecraft.network.chat.Component.literal(npc.getName().getString()+"："+greeting),Math.min(330,g.guiWidth()-40));int x=(g.guiWidth()-Math.min(350,g.guiWidth()-20))/2,w=Math.min(350,g.guiWidth()-20),y=g.guiHeight()-88-lines.size()*11;g.fill(x,y,x+w,y+16+lines.size()*11,0xe30b1119);g.fill(x,y,x+2,y+16+lines.size()*11,0xffb5a175);for(int i=0;i<lines.size();i++)g.drawString(mc.font,lines.get(i),x+10,y+8+i*11,0xfff3eee2,false);}
}
