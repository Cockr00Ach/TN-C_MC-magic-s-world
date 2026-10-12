package com.tnc.tnc.client;

import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNOrbEntities;
import com.tnc.tnc.magic.water.TNWaterFieldEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated real-client model gallery; never activates in normal play. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class WaterModelClientAudit {
    private static boolean started,saving,saved;
    private static int frames,page;
    private static final List<String> failures=new ArrayList<>();
    @Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e) {
            if(Boolean.getBoolean("tnc.waterModelAudit"))e.enqueueWork(()->{
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(TNSpellClientVisuals.class);
            });
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception {
        if(!Boolean.getBoolean("tnc.waterModelAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started) {
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            for(String name:WaterSolidModels.MODELS) {
                var model=mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","water/"+name));
                if(model==mc.getModelManager().getMissingModel()||model.getParticleIcon().contents().name().getPath().equals("missingno"))failures.add("Missing model "+name);
            }
            mc.setScreen(new Gallery());
        }
        if(++frames<35||saving)return;
        if(!saved) {saving=true;Screenshot.grab(mc.gameDirectory,"water-models-"+page+".png",mc.getMainRenderTarget(),m->{saved=true;saving=false;});return;}
        if(++page<2){frames=0;saved=false;return;}
        Files.writeString(Path.of(System.getProperty("tnc.waterModelAuditReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("rain_tiers",5,"models",3,"slash_angles",20,"screenshots",2,"failures",failures,"scope","Actual isolated dev-client rendering, not full-pack FPS")));
        mc.stop();
    }
    @SuppressWarnings("unchecked") private static void data(TNWaterFieldEntity e,String field,Object value)throws Exception {
        var f=TNWaterFieldEntity.class.getDeclaredField(field);f.setAccessible(true);
        e.getEntityData().set((EntityDataAccessor<Object>)f.get(null),value);
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Water model audit"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xFF101B27);
            g.drawString(font,page==0?"WATER / SOLID RAIN MODELS / TIERS 1-5":"WATER / FORWARD SLASH / 20 DISTINCT ROLLS",24,18,0xB4F3FF,false);
            int count=page==0?6:20;
            for(int i=0;i<count;i++) {
                int columns=page==0?3:5,cellW=width/columns,cellH=(height-45)/(page==0?2:4);
                int x=i%columns*cellW,y=45+i/columns*cellH;
                g.fill(x+8,y+8,x+cellW-8,y+cellH-8,0xFF203649);
                g.drawString(font,page==0?(i<5?"RAIN TIER "+(i+1):"SOLID WATER SEAL"):"ROLL "+i*18+" degrees",x+18,y+18,0xCBF6FF,false);
                try {
                    g.flush();var poses=g.pose();poses.pushPose();poses.translate(x+cellW*.5,y+cellH*(page==0?.78:.58),150);
                    float size=page==0?i<5?(float)(cellW*.42/com.tnc.tnc.magic.water.WaterSpellRules.fieldRadius(3,i+1)):170:14;
                    poses.scale(size,-size,size);poses.mulPose(Axis.XP.rotationDegrees(26));poses.mulPose(Axis.YP.rotationDegrees(25));
                    var buffers=minecraft.renderBuffers().bufferSource();
                    if(page==0&&i<5) {
                        var e=TNOrbEntities.WATER_FIELD.get().create(minecraft.level);
                        e.setPos(minecraft.player.position());data(e,"KIND",3);data(e,"TIER",i+1);data(e,"AGE",40);
                        WaterRainVisuals.render(e,40,1,poses,buffers);
                    } else if(page==0)WaterSolidModels.seal(poses,buffers,Vec3.ZERO,new Vec3(0,1,0),1,40,1);
                    else {
                        var body=buffers.getBuffer(WaterRenderTypes.geometry());
                        WaterWaveVisuals.crescent(body,poses.last().pose(),Vec3.ZERO,new Vec3(0,0,1),4,8,1,Math.toRadians(i*18));
                    }
                    buffers.endBatch();poses.popPose();
                }catch(Throwable ex){String message=ex.toString();if(!failures.contains(message))failures.add(message);}
            }
        }
    }
}
