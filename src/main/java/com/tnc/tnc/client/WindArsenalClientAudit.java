package com.tnc.tnc.client;

import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated GPU gallery; excluded from release overlays. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WindArsenalClientAudit {
    private static boolean started,saving;private static volatile boolean saved;private static int frames;
    private static final List<String> failures=new ArrayList<>();
    private static boolean enabled(){return Boolean.getBoolean("tnc.windArsenalAudit")||Path.of("").toAbsolutePath().getFileName().toString().equals("wind-arsenal-client-audit");}
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){
            if(enabled())e.enqueueWork(()->{
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(TNSpellClientVisuals.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(DarkGiantSiphonInput.class);
                System.out.println("WIND ARSENAL AUDIT: isolated gallery listeners prepared");
            });
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(!enabled()||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.getOverlay()!=null||mc.screen==null)return;
        if(!started){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            for(String name:WindConstructModels.NAMES){
                var model=mc.getModelManager().getModel(WindConstructModels.id(name));
                if(model==mc.getModelManager().getMissingModel()||model.getParticleIcon().contents().name().getPath().equals("missingno"))failures.add("Missing model "+name);
            }
            mc.setScreen(new Gallery());
        }
        if(++frames<40)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"wind_arsenal_full.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(!saved)return;
        Files.writeString(Path.of(System.getProperty("tnc.windArsenalAuditReport","../wind-arsenal-client-audit.json")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"models",6,"scope","Real baked-model GPU render; not a full-pack FPS benchmark")));mc.stop();
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Wind arsenal model gallery"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF101D26);g.drawString(font,"WIND / BAKED ELEMENTAL CONSTRUCTS",28,22,0xB8F6DD,false);
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            for(int i=0;i<6;i++){
                int cell=width/3,row=i/3,col=i%3,cx=col*cell,top=60+row*(height-90)/2;
                g.fill(cx+12,top,cx+cell-12,top+(height-90)/2-12,0xFF233A46);
                g.drawString(font,new String[]{"T1 / INWARD WIND CORE","T2 / SIX ORBITING BLADES","WIND CRESCENT","ELEMENTAL SWORD","ELEMENTAL SPEAR","PIERCED CHAKRAM"}[i],cx+25,top+15,0xC9F8E6,false);
                try{
                    g.flush();g.pose().pushPose();g.pose().translate(cx+cell/2,top+(height-90)/4+20,450);
                    float s=Math.min(cell/3.7F,(height-90)/4.5F);g.pose().scale(s,-s,s);
                    g.pose().mulPose(Axis.XP.rotationDegrees(15));g.pose().mulPose(Axis.YP.rotationDegrees(-25));
                    if(i<2)WindConstructModels.orb(i+1,45+partial,g.pose(),buffers);
                    else{
                        if(i==3||i==4){g.pose().mulPose(Axis.XP.rotationDegrees(-70));g.pose().scale(.75F,.75F,.75F);}
                        WindConstructModels.weapon(i-2,g.pose(),buffers,.9F,false);
                    }
                    buffers.endBatch();g.pose().popPose();
                }catch(Throwable t){if(!failures.contains(t.toString()))failures.add(t.toString());}
            }
            g.drawString(font,"Native model atlas | Mint-white wind crystal | Fixed orbit instance budget",28,height-20,0x8ABBAF,false);
        }
    }
}
