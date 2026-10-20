package com.tnc.tnc.boss.wind.client;

import com.tnc.tnc.boss.wind.*;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Optional real-render gallery; excluded from scoped installed releases. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WindGeneralClientAudit {
    private static final List<WindGeneralEntity> actors=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static int frames;
    private static boolean saving;
    private static volatile boolean saved;
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){
            if(Boolean.getBoolean("tnc.windGeneralAudit"))e.enqueueWork(()->{
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            });
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception {
        if(!Boolean.getBoolean("tnc.windGeneralAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(mc.getWindow().getWidth()<1600||mc.getWindow().getHeight()<900){mc.getWindow().setWindowed(1800,1000);mc.getWindow().setWidth(1800);mc.getWindow().setHeight(1000);mc.resizeDisplay();}
        if(actors.isEmpty()){
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            for(int i=0;i<3;i++){
                var actor=WindGeneralRegistration.TYPE.create(mc.level);actor.setNoAi(true);actor.setPos(mc.player.position());
                actor.yBodyRot=180;actor.yBodyRotO=180;actor.setYRot(180);actor.setYHeadRot(180);actors.add(actor);
            }
            if(WindGeneralRegistration.EGG==null)failures.add("Model preview spawn egg missing");
            mc.setScreen(new Gallery());
        }
        for(var actor:actors)actor.tickCount++;
        if(++frames<65)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"wind_general.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(!saved)return;
        var report=Map.of("failures",failures,"renderer",mc.getEntityRenderDispatcher().getRenderer(actors.get(0)).getClass().getName(),"framebuffer",List.of(mc.getMainRenderTarget().width,mc.getMainRenderTarget().height),"scope","Real isolated client model preview: front, three-quarter and side; model-first, no combat encounter");
        Files.writeString(Path.of(System.getProperty("tnc.windGeneralAuditReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Wind general model gallery"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF162129);g.drawString(font,"WIND GENERAL / SILVER ARMOR / WHITE SILK / JADE / LONG SPEAR",30,24,0xD7F0EE,false);
            for(int i=0;i<3;i++){
                int cell=width/3,x=i*cell;g.fill(x+12,65,x+cell-12,height-40,0xFF293941);
                g.drawString(font,new String[]{"FRONT / SILVER LAMELLAR","THREE QUARTER / WIND-BORNE SILK","PROFILE / SPEAR AND CAPE"}[i],x+24,84,0xC4DDE1,false);
                try {
                    g.flush();var rotation=Axis.ZP.rotationDegrees(180);rotation.mul(Axis.YP.rotationDegrees(i==0?0:i==1?38:90));
                    g.pose().pushPose();
                    try {g.pose().translate(0,0,450);InventoryScreen.renderEntityInInventory(g,x+cell/2+20,(int)(height*.86),Math.min((height-150)/5,(cell-35)/3),rotation,null,actors.get(i));}
                    finally {g.pose().popPose();}
                } catch(Throwable failure){var message=failure.toString();if(!failures.contains(message))failures.add(message);}
            }
            g.drawString(font,"Actual GeckoLib render | Original model | Animated cape, ribbons and plume",30,height-24,0xABC6CD,false);
        }
    }
}
