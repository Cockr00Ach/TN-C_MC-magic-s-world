package com.tnc.tnc.boss.earth.client;

import com.tnc.tnc.boss.earth.*;
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

/** Optional isolated GPU gallery, disabled and omitted from scoped game releases. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class StoneLancerClientAudit {
    private static boolean started,saving;
    private static volatile boolean saved;
    private static int frames;
    private static final List<String> failures=new ArrayList<>();
    private static final List<StoneLancerEntity> bosses=new ArrayList<>();
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){
            if(Boolean.getBoolean("tnc.stoneLancerAudit"))e.enqueueWork(()->{
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            });
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception {
        if(!Boolean.getBoolean("tnc.stoneLancerAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            for(int i=0;i<3;i++){var boss=StoneLancerRegistration.TYPE.create(mc.level);boss.setNoAi(true);boss.setPos(mc.player.position());boss.yBodyRot=180;boss.yBodyRotO=180;boss.setYRot(180);boss.setYHeadRot(180);bosses.add(boss);}
            mc.setScreen(new Gallery());
        }
        for(var boss:bosses)boss.tickCount++;
        if(++frames<40)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"stone_lancer_full.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(!saved)return;
        var report=Map.of("failures",failures,"renderer",mc.getEntityRenderDispatcher().getRenderer(bosses.get(0)).getClass().getName(),"scope","Real isolated dev-client model rendering; no full-pack FPS claim");
        Files.writeString(Path.of(System.getProperty("tnc.stoneLancerAuditReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Stone Lancer model gallery"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF151A20);g.drawString(font,"PANYUE / STONE LANCER / ACTUAL GECKOLIB MODEL",30,22,0xFFE2AB,false);
            for(int i=0;i<3;i++){
                int cell=width/3,x=i*cell;
                g.fill(x+12,65,x+cell-12,height-40,0xFF263039);
                g.drawString(font,new String[]{"FRONT / SPEAR ARMS","THREE QUARTER / EARTH CORE","PROFILE / CRYSTAL RIDGE"}[i],x+24,82,0xE8C68D,false);
                try{
                    g.flush();
                    var rotation=Axis.ZP.rotationDegrees(180);rotation.mul(Axis.YP.rotationDegrees(i==0?0:i==1?42:90));
                    g.pose().pushPose();
                    try {
                        // Inventory rendering starts only 50 GUI units from the clipping plane;
                        // a large rock body needs more depth than a player model.
                        g.pose().translate(0,0,450);
                        InventoryScreen.renderEntityInInventory(g,x+cell/2,(int)(height*.84),Math.max(20,Math.min((height-150)/11,(width/3-36)/9)),rotation,null,bosses.get(i));
                    } finally {g.pose().popPose();}
                }catch(Throwable failure){var message=failure.toString();if(!failures.contains(message))failures.add(message);}
            }
            g.drawString(font,"Real client render | 32 bones, beveled rock and true lance tips",30,height-24,0xAFBAC6,false);
        }
    }
}
