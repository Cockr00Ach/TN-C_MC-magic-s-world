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

/** Opt-in native visual review, never enabled in ordinary play. */
final class InkDragonBreathAudit {
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
            dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(mc.level);
            dragon.setYRot(90);dragon.yRotO=90;dragon.yHeadRot=90;dragon.yHeadRotO=90;dragon.yBodyRot=90;
            mc.setScreen(new Gallery());
        }
        dragon.tickCount++;if(++frames<70)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"ink_dragon_breath_ground_v2.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){
            var report=new LinkedHashMap<String,Object>(InkCelestialDragonRenderer.diagnostics());
            report.put("failures",failures);report.put("scope","Actual Minecraft breath geometry preview; no combat AI, damage or live installation yet");
            report.put("mouth",List.of(-4.65,23.14,0));report.put("target",List.of(-26,0,0));report.put("surfaceNormal",List.of(0,1,0));
            report.put("impactSpreadRadiusBlocks",10);report.put("persistentParticles",0);
            Files.writeString(Path.of("D:/ModTest/work/ink-dragon-breath-ground-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
    private static final class Gallery extends Screen{
        Gallery(){super(Component.literal("Ink dragon breath preview"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFFEAE8E2);
            g.drawString(font,"INK DRAGON / AERIAL AIM + GROUND ERUPTION / NATIVE RENDER STUDY 02",28,22,0xFF252525,false);
            g.drawString(font,"DIAGONAL TARGETED BREATH / RISING INK FANS / OUTWARD WATERCOLOR WASH",28,43,0xFF555555,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int panel=0;panel<2;panel++){
                int top=78,bottom=height-45,left=panel==0?20:width*2/3+10,right=panel==0?width*2/3-10:width-20;
                g.fill(left,top,right,bottom,0xFFF3F1EC);
                g.drawString(font,panel==0?"AIRBORNE DRAGON -> TARGET ON GROUND":"IMPACT DETAIL / INK BURST + SPREAD",left+14,top+14,0xFF333333,false);
                g.flush();g.enableScissor(left+2,top+36,right-2,bottom-2);g.pose().pushPose();
                try{
                    g.pose().translate(panel==0?left+(right-left)*.75:(left+right)*.5,panel==0?bottom-130:top+550,450);
                    float scale=panel==0?24:25;g.pose().scale(scale,-scale,scale);
                    g.pose().mulPose(Axis.XP.rotationDegrees(24));g.pose().mulPose(Axis.YP.rotationDegrees(-12));
                    if(panel==1)g.pose().translate(26,0,0);
                    var ink=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","textures/entity/ink_crane_primitive.png")));
                    InkCraneRitualVisuals.quad(ink,g.pose().last(),new Vec3(-41,-.02,-13),new Vec3(15,-.02,-13),new Vec3(15,-.02,13),new Vec3(-41,-.02,13),.86F,1);
                    for(int grid=-40;grid<=16;grid+=4)InkCraneRitualVisuals.stroke(ink,g.pose().last(),new Vec3(grid,0,-13),new Vec3(grid,0,13),.012,.68F,.65F);
                    for(int grid=-12;grid<=12;grid+=4)InkCraneRitualVisuals.stroke(ink,g.pose().last(),new Vec3(-41,0,grid),new Vec3(15,0,grid),.012,.68F,.65F);
                    buffers.endBatch();
                    mc.getEntityRenderDispatcher().setRenderShadow(false);
                    if(panel==0){mc.getEntityRenderDispatcher().render(dragon,3,8,0,90,partial,g.pose(),buffers,LightTexture.FULL_BRIGHT);buffers.endBatch();}
                    InkDragonBreathVisuals.aimed(70,new Vec3(-4.65,23.14,0),new Vec3(-26,0,0),new Vec3(0,1,0),g.pose(),buffers);buffers.endBatch();
                    int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0&&!failures.contains("GL error "+error))failures.add("GL error "+error);
                }catch(Throwable e){if(!failures.contains(e.toString()))failures.add(e.toString());}
                finally{g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
            g.drawString(font,"VISUAL REVIEW ONLY / TARGET AND HIT POINT ARE STAGED / AI + DAMAGE NOT CONNECTED YET",28,height-24,0xFF333333,false);
        }
    }
}
