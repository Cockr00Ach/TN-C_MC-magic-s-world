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
        if(++page<5){frames=0;saved=false;return;}
        Files.writeString(Path.of(System.getProperty("tnc.waterModelAuditReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("rain_tiers",5,"models",5,"water_transparency_cases",6,"seal_depth_cases",4,"sword_path_frames",4,"slash_angles",20,"screenshots",5,"failures",failures,"scope","Actual isolated dev-client rendering, not full-pack FPS")));
        mc.stop();
    }
    @SuppressWarnings("unchecked") private static void data(TNWaterFieldEntity e,String field,Object value)throws Exception {
        var f=TNWaterFieldEntity.class.getDeclaredField(field);f.setAccessible(true);
        e.getEntityData().set((EntityDataAccessor<Object>)f.get(null),value);
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Water model audit"));}
        @Override public boolean isPauseScreen(){return false;}
        private void sealDepth(GuiGraphics g) {
            g.fill(0,0,width,height,0xFF101B27);
            g.drawString(font,"SEAL DEPTH / opaque boss behind / opaque wall in front",24,18,0xB4F3FF,false);
            for(int i=0;i<4;i++) {
                int cellW=width/4,x=i*cellW;
                g.drawString(font,new String[]{"BEHIND: drawn first","BEHIND: drawn last","WALL: drawn first","WALL: drawn last"}[i],x+12,60,0xCBF6FF,false);
                g.flush();var poses=g.pose();poses.pushPose();poses.translate(x+cellW*.5,height*.55,150);
                poses.scale(cellW*.36F,-cellW*.36F,cellW*.36F);
                var buffers=minecraft.renderBuffers().bufferSource();
                boolean wall=i>=2,first=i%2==0;
                if(first)depthBlock(poses,buffers,wall);
                WaterSolidModels.seal(poses,buffers,Vec3.ZERO,new Vec3(0,0,1),1,40,1);
                buffers.endBatch();
                if(!first)depthBlock(poses,buffers,wall);
                poses.popPose();
            }
        }
        private void depthBlock(com.mojang.blaze3d.vertex.PoseStack poses,
                                net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,boolean wall) {
            poses.pushPose();poses.translate(-1.1,-1.1,wall?.4:-.8);poses.scale(2.2F,2.2F,.2F);
            minecraft.getBlockRenderer().renderSingleBlock(net.minecraft.world.level.block.Blocks.MAGENTA_CONCRETE.defaultBlockState(),
                    poses,buffers,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            buffers.endBatch();poses.popPose();
        }
        private void swordPath(GuiGraphics g) {
            g.fill(0,0,width,height,0xFF101B27);
            g.drawString(font,"SWORD GATEWAYS / fixed 12-block spacing / pre-drop and descent",24,18,0xB4F3FF,false);
            double[] ages={122,130,137,144};
            for(int i=0;i<ages.length;i++) {
                int cellW=width/4,x=i*cellW;
                g.drawString(font,"TICK "+(int)ages[i],x+12,55,0xCBF6FF,false);
                g.flush();var poses=g.pose();poses.pushPose();poses.translate(x+cellW*.5,height*.88,150);
                float scale=Math.min(cellW/82F,(height-100)/145F);
                poses.scale(scale,-scale,scale);poses.mulPose(Axis.XP.rotationDegrees(12));
                poses.mulPose(Axis.YP.rotationDegrees(20));
                var buffers=minecraft.renderBuffers().bufferSource();
                SeaGodSwordVisuals.render(ages[i],ages[i],26,1,poses,buffers,1,116);
                buffers.endBatch();poses.popPose();
            }
        }
        private void waterTransparency(GuiGraphics g) {
            g.fill(0,0,width,height,0xFF101B27);
            g.drawString(font,"WATER + SEAL / late transparency / background boss and foreground wall",24,18,0xB4F3FF,false);
            for(int i=0;i<6;i++) {
                int col=i%3,row=i/3,cellW=width/3,cellH=(height-65)/2;
                g.drawString(font,(row==0?"BALL + SEAL / ":"T5 BEAM / ")+new String[]{"BOSS FIRST","BOSS LAST","WALL LAST"}[col],col*cellW+12,55+row*cellH,0xCBF6FF,false);
                g.flush();var poses=g.pose();poses.pushPose();poses.translate((col+.5)*cellW,75+(row+.5)*cellH,150);
                poses.scale(cellW*.36F,-cellW*.36F,cellW*.36F);
                var buffers=minecraft.renderBuffers().bufferSource();
                WaterTransparencyPass.begin();
                if(col==0)depthBlock(poses,buffers,false);
                if(row==0) {
                    WaterSolidModels.seal(poses,buffers,Vec3.ZERO,new Vec3(0,0,1),.8,40,1);
                    poses.pushPose();poses.translate(0,0,.5);
                    var bolt=TNOrbEntities.WATER_BOLT.get().create(minecraft.level);bolt.setDeltaMovement(0,0,1);
                    minecraft.getEntityRenderDispatcher().getRenderer(bolt).render(bolt,0,0,poses,buffers,15728880);
                    poses.popPose();
                } else {
                    poses.pushPose();poses.translate(-.72,0,.4);poses.scale(.015F,.015F,.015F);
                    WaterVisualBuffers.draw(WaterTransparencyPass.route(buffers),(body,glow)->WaterBeamVisuals.render(
                            body,glow,poses.last().pose(),5,new Vec3(1,0,0),96,40,1,true));
                    poses.popPose();
                }
                buffers.endBatch();
                if(col==1)depthBlock(poses,buffers,false);
                if(col==2){poses.pushPose();poses.translate(0,0,1);depthBlock(poses,buffers,true);poses.popPose();}
                WaterTransparencyPass.finish();poses.popPose();
            }
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xFF101B27);
            if(page==2){sealDepth(g);return;}
            if(page==3){swordPath(g);return;}
            if(page==4){waterTransparency(g);return;}
            g.drawString(font,page==0?"WATER / SOLID RAIN MODELS / TIERS 1-5":"WATER / FORWARD SLASH / 20 DISTINCT ROLLS",24,18,0xB4F3FF,false);
            int count=page==0?8:20;
            for(int i=0;i<count;i++) {
                int columns=page==0?4:5,cellW=width/columns,cellH=(height-45)/(page==0?2:4);
                int x=i%columns*cellW,y=45+i/columns*cellH;
                g.fill(x+8,y+8,x+cellW-8,y+cellH-8,0xFF203649);
                g.drawString(font,page==0?(i<5?"RAIN TIER "+(i+1):i==5?"SOLID WATER SEAL":i==6?"BLUE-GOLD GOD SWORD":"REFINED PRISON PANELS"):"ROLL "+i*18+" degrees",x+18,y+18,0xCBF6FF,false);
                try {
                    g.flush();var poses=g.pose();poses.pushPose();poses.translate(x+cellW*.5,y+cellH*(page==0?.78:.58),150);
                    float size=page==0?i<5?(float)(cellW*.42/com.tnc.tnc.magic.water.WaterSpellRules.fieldRadius(3,i+1)):170:14;
                    poses.scale(size,-size,size);poses.mulPose(Axis.XP.rotationDegrees(26));poses.mulPose(Axis.YP.rotationDegrees(25));
                    var buffers=minecraft.renderBuffers().bufferSource();
                    if(page==0&&i<5) {
                        var e=TNOrbEntities.WATER_FIELD.get().create(minecraft.level);
                        e.setPos(minecraft.player.position());data(e,"KIND",3);data(e,"TIER",i+1);data(e,"AGE",40);
                        WaterRainVisuals.render(e,40,1,poses,buffers);
                    } else if(page==0&&i==5)WaterSolidModels.seal(poses,buffers,Vec3.ZERO,new Vec3(0,1,0),1,40,1);
                    else if(page==0&&i==6){poses.scale(.66F,.66F,.66F);WaterSolidModels.draw("sea_god_sword",poses,buffers,1);}
                    else if(page==0)WaterSolidModels.prisonPanels(poses,buffers,1.3,2.3,12,40,.9F);
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
