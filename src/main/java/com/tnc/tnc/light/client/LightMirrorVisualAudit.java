package com.tnc.tnc.light.client;

import com.mojang.math.Axis;
import com.tnc.tnc.light.*;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in actual renderer gallery, disposable development save only. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class LightMirrorVisualAudit {
    private static final List<TNLightMirrorEntity> mirrors=new ArrayList<>();
    private static final List<TNLightBeamEntity> rays=new ArrayList<>();
    private static ApolloAcolyteEntity acolyte;
    private static int stage,ticks; private static volatile boolean saved; private static boolean saving;
    private static final List<String> failures=new ArrayList<>();
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception {
        if(!Boolean.getBoolean("tnc.lightMirrorAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(mirrors.isEmpty()) {
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            for(int t=1;t<=5;t++) { var m=LightMirrorRegistration.MIRROR.create(mc.level);m.configure(mc.player,t);mirrors.add(m); }
            acolyte=LightMirrorRegistration.ACOLYTE.create(mc.level);acolyte.setNoAi(true);
            addRay(mc,new Vec3(0,0,-6),new Vec3(0,0,1),6,0xffffff);
            var directions=LightMirrorRules.split(new Vec3(0,0,1),5);
            for(int i=0;i<directions.size();i++) addRay(mc,Vec3.ZERO,directions.get(i),14,LightMirrorRules.color(i));
            mc.setScreen(new Gallery());
        }
        if(++ticks<45||saving)return;
        if(!saved) { saving=true;Screenshot.grab(mc.gameDirectory,"light-mirror-"+stage+".png",mc.getMainRenderTarget(),m->{saved=true;saving=false;});return; }
        if(++stage<2){ticks=0;saved=false;mc.setScreen(new Gallery());return;}
        int error=org.lwjgl.opengl.GL11.glGetError();if(error!=0)failures.add("OpenGL: "+error);
        Files.writeString(Path.of("D:/ModTest/work/light-mirror-native-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"screenshots",2,"scope","Native entity rendering of all tiers, acolyte and white-to-spectrum beam geometry; combat verified separately by GameTests")));
        mc.stop();
    }
    private static void addRay(Minecraft mc,Vec3 at,Vec3 dir,double length,int color)throws Exception {
        var b=TNOrbEntities.LIGHT_BEAM.get().create(mc.level);b.configure(mc.player,.075,length,0,40,TNLightBeamEntity.STYLE_REFRACTED,null);
        b.moveTo(at.x,at.y,at.z,TNLightBeamMechanics.yawFor(dir),TNLightBeamMechanics.pitchFor(dir));b.yRotO=b.getYRot();b.xRotO=b.getXRot();b.xo=at.x;b.yo=at.y;b.zo=at.z;b.tickCount=3;
        var f=TNLightBeamEntity.class.getDeclaredField("DATA_COLOR");f.setAccessible(true);
        @SuppressWarnings("unchecked") var key=(net.minecraft.network.syncher.EntityDataAccessor<Integer>)f.get(null);
        b.getEntityData().set(key,color);rays.add(b);
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Prism gallery"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int x,int y,float partial) {
            g.fill(0,0,width,height,0xff121a28);g.drawString(font,"NATIVE MINECRAFT / REFRACTION PRISMS / "+(stage==0?"MODELS AND ACOLYTE":"WHITE INCIDENT / SEVEN COLOURED BRANCHES"),28,24,0xffedd0,false);
            if(stage==0) {
                for(int i=0;i<5;i++) {
                    int cx=width*(i+1)/6;
                    g.drawString(font,"T"+(i+1)+"  /  "+(i+3)+" COLOURS  /  "+(12+i*4)+"s",cx-75,75,0xd6bf8b,false);
                    g.flush();g.pose().pushPose();g.pose().translate(cx,height*.48,500);g.pose().scale(85,85,-85);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(180));g.pose().mulPose(Axis.YP.rotationDegrees(28));
                    draw(g,mirrors.get(i),0,-1.3,0,partial);g.pose().popPose();
                }
                for(int i=0;i<3;i++) {
                    g.flush();g.pose().pushPose();g.pose().translate(width*(i+1)/4,height-34,500);g.pose().scale(115,115,-115);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(180));g.pose().mulPose(Axis.YP.rotationDegrees(new int[]{180,220,270}[i]));
                    draw(g,acolyte,0,0,0,partial);g.pose().popPose();
                }
            } else {
                g.flush();g.pose().pushPose();g.pose().translate(width*.54,height*.42,500);g.pose().scale(32,32,-32);
                g.pose().mulPose(Axis.ZP.rotationDegrees(180));g.pose().mulPose(Axis.XP.rotationDegrees(55));g.pose().mulPose(Axis.YP.rotationDegrees(35));
                draw(g,mirrors.get(4),0,-1.3,0,partial);
                for(var b:rays) draw(g,b,b.getX(),b.getY(),b.getZ(),partial);
                g.pose().popPose();
            }
        }
        private void draw(GuiGraphics g,Entity e,double x,double y,double z,float partial) {
            var dispatcher=minecraft.getEntityRenderDispatcher();var buffer=minecraft.renderBuffers().bufferSource();
            dispatcher.setRenderShadow(false);
            try { dispatcher.render(e,x,y,z,0,partial,g.pose(),buffer,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);buffer.endBatch(); }
            catch(Throwable error) { if(!failures.contains(error.toString()))failures.add(error.toString()); }
            finally { dispatcher.setRenderShadow(true); }
        }
    }
}
