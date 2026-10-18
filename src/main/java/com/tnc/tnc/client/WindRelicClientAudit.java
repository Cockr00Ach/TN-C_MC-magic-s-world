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

/** Standalone baked-asset gallery: no saved-world migration dialog can block this opt-in audit. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WindRelicClientAudit {
    private static boolean shown,saving;private static volatile boolean saved;private static int frames;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!(Boolean.getBoolean("tnc.windTwoChainsAudit")||Boolean.getBoolean("tnc.windPhantomAudit"))||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(!shown){
            if(mc.getModelManager().getModel(WindConstructModels.id("sword"))==mc.getModelManager().getMissingModel())return;
            shown=true;mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new Gallery());
        }
        if(++frames<35)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,Boolean.getBoolean("tnc.windPhantomAudit")?"wind_phantoms.png":"wind_relics.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){Files.writeString(Path.of(Boolean.getBoolean("tnc.windPhantomAudit")?"D:/ModTest/work/wind-phantom-native-preview.json":"D:/ModTest/work/wind-relic-native-preview.json"),"{\"bakedModelsRendered\":true,\"worldRequired\":false}");mc.stop();}
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Wind relics"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF0D1D28);
            g.drawString(font,"WIND RELICS / NATIVE BAKED MODELS / SPELLS + HELD EQUIPMENT",40,30,0xD8F6E8,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int i=0;i<3;i++){
                int x=i*width/3,y=90;g.fill(x+20,y,x+width/3-20,height-65,0xFF1A3540);
                g.drawString(font,new String[]{"GALE SOVEREIGN / WINGBLADE","SKYBREAKER / WINDLANCE","COMPRESSED WIND / SOFT MIST CORE"}[i],x+40,y+25,0xD8F6E8,false);
                g.flush();g.pose().pushPose();g.pose().translate(x+width/6,height*.53,400);
                float scale=i==2?180:210;g.pose().scale(scale,-scale,scale);
                g.pose().mulPose(Axis.XP.rotationDegrees(i==2?15:-75));g.pose().mulPose(Axis.YP.rotationDegrees(-20));
                if(i<2&&Boolean.getBoolean("tnc.windPhantomAudit"))WindConstructModels.phantomWeapon(i+1,40,g.pose(),buffers,1,false);
                else if(i<2)WindConstructModels.weapon(i+1,g.pose(),buffers,.99F,false);
                else WindConstructModels.orb(2,40,g.pose(),buffers);
                buffers.endBatch();g.pose().popPose();
            }
            g.drawString(font,"Layered cutting edges / engraved metal / wing guards / wrapped grips / shared spell and item geometry",40,height-35,0xA3CCBE,false);
        }
    }
}
