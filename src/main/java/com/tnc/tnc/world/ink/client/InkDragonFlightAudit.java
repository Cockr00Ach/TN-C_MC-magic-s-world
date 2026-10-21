package com.tnc.tnc.world.ink.client;
import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
/** Actual mesh + shader at distinct wave phases, never enabled in normal play. */
final class InkDragonFlightAudit {
    private static InkCelestialDragonEntity dragon;private static int frames;private static boolean saving,preloaded;private static volatile boolean saved;
    private static final List<String> failures=new ArrayList<>();
    static void tick(Minecraft mc)throws Exception{
        if(dragon==null){
            preloaded=((Number)InkCelestialDragonRenderer.diagnostics().get("loadedLods")).intValue()==3;
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(mc.level);dragon.setYRot(90);dragon.yRotO=90;dragon.yBodyRot=90;dragon.yHeadRot=90;dragon.yHeadRotO=90;
            mc.setScreen(new Gallery());
        }
        if(++frames<65)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,"ink_dragon_extended_flight_v3.png",mc.getMainRenderTarget(),m->saved=true);return;}
        if(saved){
            var report=new LinkedHashMap<String,Object>(InkCelestialDragonRenderer.diagnostics());report.put("failures",failures);report.put("preloadedBeforeFirstDragon",preloaded);
            report.put("scope","Actual runtime extended mesh and GPU travelling-wave shader, two animation phases, not concept art");
            if(!preloaded)failures.add("Mesh was not prepared during world login");
            var png=javax.imageio.ImageIO.read(Path.of("D:/ModTest/work/ink-crane-client-audit/screenshots/ink_dragon_extended_flight_v3.png").toFile());
            for(int row=0;row<2;row++){
                int pixels=0;for(int y=100+row*420;y<Math.min(png.getHeight()-30,480+row*420);y++)for(int x=30;x<png.getWidth()-30;x++){int p=png.getRGB(x,y);if((p&255)<100&&((p>>8)&255)<100&&((p>>16)&255)<100)pixels++;}
                report.put("visiblePixelsPhase"+row,pixels);if(pixels<5000)failures.add("Extended model was not visible in phase "+row);
            }
            Files.writeString(Path.of("D:/ModTest/work/ink-dragon-extended-flight-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
        }
    }
    private static final class Gallery extends Screen{
        Gallery(){super(Component.literal("Extended dragon flight"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFFEAE8E2);g.drawString(font,"INK DRAGON / EXTENDED FLIGHT RIG / ACTUAL MINECRAFT MESH + TRAVELLING WAVES",24,22,0xFF252525,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int row=0;row<2;row++){
                int top=75+row*(height-110)/2,bottom=top+(height-110)/2-12;
                g.fill(18,top,width-18,bottom,0xFFF3F1EC);g.drawString(font,row==0?"PHASE 0 / LONG HORIZONTAL BODY / ORIGINAL SCULPTED DETAILS":"PHASE 30 / WAVE PROPAGATES FROM NECK TO TAIL / NOT A RIGID COIL",32,top+16,0xFF333333,false);
                g.flush();g.enableScissor(22,top+36,width-22,bottom-2);g.pose().pushPose();
                try{
                    g.pose().translate(width*.5,(top+bottom)*.5+45,450);g.pose().scale(6.7F,-6.7F,6.7F);
                    g.pose().mulPose(Axis.XP.rotationDegrees(26));g.pose().mulPose(Axis.YP.rotationDegrees(-10));g.pose().translate(-26*3,-5*3,0);
                    dragon.tickCount=row*30;mc.getEntityRenderDispatcher().setRenderShadow(false);
                    mc.getEntityRenderDispatcher().render(dragon,0,0,0,90,0,g.pose(),buffers,LightTexture.FULL_BRIGHT);buffers.endBatch();
                    int e=org.lwjgl.opengl.GL11.glGetError();if(e!=0&&!failures.contains("GL error "+e))failures.add("GL error "+e);
                }catch(Throwable e){if(!failures.contains(e.toString()))failures.add(e.toString());}
                finally{g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
            g.drawString(font,"3X BODY SCALE / HEAD HELD FORWARD / LIMBS HANG NATURALLY / LIGHTER DISTANT LODS / PRELOADED ON WORLD ENTRY",24,height-22,0xFF333333,false);
        }
    }
}
