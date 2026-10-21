package com.tnc.tnc.world.ink.client;

import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Native timeline study in a disposable dev world, opt-in only. */
final class InkDragonDiveAudit {
    private static InkCelestialDragonEntity dragon;
    private static int frames;private static boolean saving;private static volatile boolean saved;
    private static final List<String> failures=new ArrayList<>();
    static void tick(Minecraft mc)throws Exception{
        if(dragon==null){
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(mc.level);dragon.setYRot(90);dragon.yRotO=90;dragon.yHeadRot=90;dragon.yHeadRotO=90;dragon.yBodyRot=90;
            mc.setScreen(new Gallery());
        }
        dragon.tickCount++;if(++frames<70)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"ink_dragon_dive_native_v1.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){
            var report=new LinkedHashMap<String,Object>(InkCelestialDragonRenderer.diagnostics());report.put("failures",failures);
            report.put("phantomScale",InkDragonDiveRules.PHANTOM_SCALE);report.put("previewAges",List.of(76,98,123));
            report.put("orbDiameterBlocks",InkDragonDiveRules.ORB_RADIUS*2);
            report.put("scope","Actual shared dragon mesh with translucent phantom, converging orb and impact; staged preview, no combat AI or live installation");
            Files.writeString(Path.of("D:/ModTest/work/ink-dragon-dive-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
    private static final class Gallery extends Screen{
        Gallery(){super(Component.literal("Ink dragon phantom dive"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFFEAE8E2);
            g.drawString(font,"INK DRAGON / THREEFOLD PHANTOM DIVE / ACTUAL MINECRAFT RENDER",24,22,0xFF252525,false);
            g.drawString(font,"01 HIGH-ALTITUDE GATHERING                     02 PHANTOM + ORB DESCENT                     03 INK ERUPTION",24,45,0xFF555555,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int panel=0;panel<3;panel++){
                int left=16+panel*width/3,right=(panel+1)*width/3-12,top=80,bottom=height-45;
                g.fill(left,top,right,bottom,0xFFF3F1EC);
                g.drawString(font,new String[]{"3X PHANTOM / GIANT ORB BELOW FEET","SYNCHRONIZED DIVE / LONG INK WAKE","PHANTOM VANISHES / INK BURSTS OUTWARD"}[panel],left+12,top+14,0xFF333333,false);
                g.flush();g.enableScissor(left+2,top+36,right-2,bottom-2);g.pose().pushPose();
                try{
                    g.pose().translate((left+right)*.5,panel==2?top+520:bottom-100,450);float scale=panel==0?6.3F:panel==1?6.1F:18;
                    g.pose().scale(scale,-scale,scale);g.pose().mulPose(Axis.XP.rotationDegrees(15));g.pose().mulPose(Axis.YP.rotationDegrees(-12));
                    mc.getEntityRenderDispatcher().setRenderShadow(false);
                    if(panel==0){mc.getEntityRenderDispatcher().render(dragon,22,65,0,90,partial,g.pose(),buffers,LightTexture.FULL_BRIGHT);buffers.endBatch();}
                    InkDragonDiveVisuals.render(new double[]{76,98,123}[panel],new Vec3(0,28,0),Vec3.ZERO,dragon,g.pose(),buffers);buffers.endBatch();
                    int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0&&!failures.contains("GL error "+error))failures.add("GL error "+error);
                }catch(Throwable e){if(!failures.contains(e.toString()))failures.add(e.toString());}
                finally{g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
            g.drawString(font,"VISUAL TIMELINE STUDY / FIXED REVIEW FRAMES / FLIGHT AI, TARGET TRACKING AND DAMAGE NOT CONNECTED YET",24,height-24,0xFF333333,false);
        }
    }
}
