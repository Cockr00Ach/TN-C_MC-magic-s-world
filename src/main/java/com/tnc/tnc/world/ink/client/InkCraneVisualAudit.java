package com.tnc.tnc.world.ink.client;

import com.mojang.math.Axis;
import com.tnc.tnc.world.ink.*;
import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Explicit opt-in, hidden-window native renderer gallery in a disposable dev world. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkCraneVisualAudit {
    private static boolean started,saving;
    private static volatile boolean saved;
    private static int frames;
    private static int waiting;
    private static InkCraneBossEntity boss;
    private static InkCranePhantomEntity avatar,gather,ball,drake;
    private static WindVolleyEntity wind;
    private static InkWingedTigerEntity[] tigers;
    private static InkTigerEffectEntity[] tigerEffects;
    private static final List<String> failures=new ArrayList<>();

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.inkCraneVisualAudit")||event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null&&mc.screen!=null&&mc.getOverlay()==null) {
            if(++waiting%100==1)System.out.println("INK AUDIT SCREEN: "+mc.screen.getClass().getName()+" / "+mc.screen.getTitle().getString());
            String name=mc.screen.getClass().getSimpleName();
            if(name.contains("Confirm")||name.contains("Safety")) {
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button) {
                    String label=button.getMessage().getString();
                    if(waiting%100==1)System.out.println("INK AUDIT CONFIRM BUTTON: "+label);
                    if(label.equals("Proceed")||label.equals("Continue")||label.equals("I know what I'm doing!")||label.equals("继续")) {
                        button.onPress();break;
                    }
                }
            }
        }
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(Boolean.getBoolean("tnc.inkEncounterAudit")){InkEncounterAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkDragonVisibilityAudit")){InkDragonVisibilityAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkDragonFlightAudit")){InkDragonFlightAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkDragonCombatAudit")){InkDragonCombatAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkDragonDiveAudit")){InkDragonDiveAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkDragonBreathAudit")){InkDragonBreathAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkDragonAudit")){InkDragonMeshAudit.tick(mc);return;}
        if(Boolean.getBoolean("tnc.inkPaintingWorldAudit")){worldAudit(mc);return;}
        if(!started) {
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            boss=InkBossRegistration.INK_CRANE_BOSS.create(mc.level);
            boss.setPos(0,0,0);boss.startInkAttack(3,mc.player);
            avatar=phantom(1);gather=phantom(3);ball=phantom(3);drake=phantom(4);
            drake.setYRot(90);drake.yRotO=90;
            wind=WindChainRegistration.VOLLEY.create(mc.level);wind.configure(mc.player,4,null);
            for(int i=0;i<40;i++)drake.trail().add(new net.minecraft.world.phys.Vec3(i*.7,Math.sin(i*.1),0));
            if(Boolean.getBoolean("tnc.inkTigerAudit")) {
                boolean poseAudit=Boolean.getBoolean("tnc.inkTigerPoseAudit");
                tigers=new InkWingedTigerEntity[poseAudit?4:3];
                for(int i=0;i<tigers.length;i++) {
                    tigers[i]=InkBossRegistration.INK_WINGED_TIGER.create(mc.level);tigers[i].setPos(0,0,0);
                    if(!poseAudit||i==3){data(tigers[i],"MOVE",poseAudit?1:i+1);data(tigers[i],"START",mc.level.getGameTime()-35);
                        tigers[i].triggerAnim("action",poseAudit?"sky_spit":new String[]{"sky_spit","phantom_cast","ink_roar"}[i]);}
                }
                tigerEffects=new InkTigerEffectEntity[4];
                for(int i=0;i<4;i++){
                    tigerEffects[i]=InkBossRegistration.INK_TIGER_EFFECT.create(mc.level);
                    data(tigerEffects[i],"MODE",new int[]{2,1,1,3}[i]);
                    data(tigerEffects[i],"START",mc.level.getGameTime()-new int[]{35,70,110,20}[i]);
                }
            }
            mc.setScreen(new Gallery());
        }
        boss.tickCount++;avatar.tickCount++;drake.tickCount++;
        if(tigers!=null)for(var tiger:tigers)tiger.tickCount++;
        if(++frames<38)return;
        if(!saving){saving=true;Screenshot.grab(mc.gameDirectory,tigers==null?"ink_crane_v8.png":Boolean.getBoolean("tnc.inkTigerPoseAudit")?"ink_winged_tiger_v5_pose.png":"ink_winged_tiger_v3_spells.png",mc.getMainRenderTarget(),message->saved=true);return;}
        if(saved){Files.writeString(Path.of(System.getProperty("tnc.inkCraneVisualAuditReport")),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,
                "nativeRegisteredRenderers",true,"scope","Isolated renderer gallery; not full-pack FPS or combat-camera verification")));mc.stop();}
    }

    private static void worldAudit(Minecraft mc)throws Exception {
        if(!started) {
            started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
            mc.options.renderDistance().set(12);
            var server=mc.getSingleplayerServer();
            if(server==null)throw new IllegalStateException("World audit requires the isolated integrated server");
            UUID playerId=mc.player.getUUID();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(playerId);
                var realm=server.getLevel(InkRealmTravel.INK_REALM);
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(realm,192,110,192,180,90);
            });
        }
        if(!mc.level.dimension().equals(InkRealmTravel.INK_REALM))return;
        mc.setScreen(null);
        if(++frames<200)return;
        if(!saving) {
            saving=true;Screenshot.grab(mc.gameDirectory,"ink_crane_v7_floor_world.png",mc.getMainRenderTarget(),message->saved=true);return;
        }
        if(saved) {
            Files.writeString(Path.of("D:/ModTest/work/ink-painting-world-audit-report.json"),
                    "{\"dimension\":\"tnc:ink_realm\",\"nativeWorldFloor\":true,\"framesWaited\":"+frames+"}");
            mc.stop();
        }
    }

    private static InkCranePhantomEntity phantom(int mode)throws Exception {
        var entity=InkBossRegistration.INK_CRANE_PHANTOM.create(Minecraft.getInstance().level);
        data(entity,"MODE",mode);entity.setPos(0,0,0);return entity;
    }
    @SuppressWarnings("unchecked") private static <T> void data(Entity entity,String name,T value)throws Exception {
        var field=entity.getClass().getDeclaredField(name);field.setAccessible(true);
        entity.getEntityData().set((EntityDataAccessor<T>)field.get(null),value);
    }
    private static void age(Entity entity,int age)throws Exception {
        data(entity,"START",entity.level().getGameTime()-age);
        if(entity instanceof InkCranePhantomEntity)data(entity,"CHARGE_START",entity.level().getGameTime()-age);
    }

    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Ink and wind renderer audit"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            if(tigers!=null){renderTigers(g,partial);return;}
            g.fill(0,0,width,height,0xFFBEBBB1);
            g.drawString(font,"INK CRANE V8 / ATTACHED 2X SKY SHROUD / NATIVE RENDERERS",25,20,0xFF181818,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int i=0;i<6;i++) {
                int cw=width/3,ch=(height-85)/2,x=i%3*cw,y=55+i/3*ch;
                g.fill(x+12,y,x+cw-12,y+ch-12,i==5?0xFF17313A:0xFFE5E2D8);
                g.drawString(font,new String[]{"SKY ASCENT / ATTACHED 2X SHROUD","FULL 10X AVATAR / AUTHOR SWEEP","INK STREAMS GATHER BEFORE FORMING",
                        "3X CORE / FLOWING INK SKIN","AFTER EXPLOSION / MOUNTAINS + CLOUDS","FLAT FLOOR PAINTING / NO TERRAIN HEIGHT"}[i],x+24,y+14,i==5?0xFFC9F9EB:0xFF222222,false);
                g.flush();g.enableScissor(x+14,y+32,x+cw-14,y+ch-14);g.pose().pushPose();
                try {
                    Entity entity=new Entity[]{boss,avatar,gather,ball,drake,wind}[i];
                    if(i==1)age(entity,44);if(i==2)age(entity,20);if(i==3)age(entity,80);
                    g.pose().translate(x+cw/2.0,y+ch*(i==0?.9:.62),450);
                    float scale=new float[]{14,4.7F,5,5.33F,2.25F,18}[i];g.pose().scale(scale,-scale,scale);
                    g.pose().mulPose(Axis.XP.rotationDegrees(i==2||i==3?55:i==5?45:12));
                    g.pose().mulPose(Axis.YP.rotationDegrees(i==4?35:145));
                    if(i==4) {
                        g.pose().scale(.55F,.55F,.55F);
                        InkPaintingRenderer.aftermath(155,g.pose(),buffers);buffers.endBatch();continue;
                    }
                    if(i==5) {
                        g.pose().scale(.065F,.065F,.065F);g.pose().translate(-192,0,-192);
                        InkPaintingRenderer.preview(g.pose(),buffers);buffers.endBatch();continue;
                    }
                    if(i==5){var p=wind.aimPoint().subtract(wind.position());g.pose().translate(-p.x,-8,-p.z);}
                    mc.getEntityRenderDispatcher().setRenderShadow(false);
                    mc.getEntityRenderDispatcher().render(entity,0,0,0,entity.getYRot(),partial,g.pose(),buffers,LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                } catch(Throwable failure) {String message=failure.toString();if(!failures.contains(message))failures.add(message);}
                finally {g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
            g.drawString(font,"Actual Minecraft render paths / paper background for checking black ink and pale wash",25,height-21,0xFF333333,false);
        }
        private void renderTigers(GuiGraphics g,float partial) {
            if(Boolean.getBoolean("tnc.inkTigerPoseAudit")){renderTigerPoses(g,partial);return;}
            g.fill(0,0,width,height,0xFFE4E4E4);
            g.drawString(font,"INK WINGED TIGER V3 / OCTAGON WINDOW + THREE PHASED SPELLS + WATERCOLOR BLINK",25,20,0xFF222222,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int i=0;i<6;i++) {
                int cw=width/3,ch=(height-85)/2,x=i%3*cw,y=55+i/3*ch;g.fill(x+10,y,x+cw-10,y+ch-12,0xFFF3F3F3);
                g.drawString(font,new String[]{"SKY CHARGE / ANGULAR WINDOW","FIVEFOLD TIGER APPARITION","ROAR / INK GATHERS ABOVE MOUTH","IMPACT / GROWING INK CORE","AFTERMATH / 12 MOUNTAIN CLOUD FACES","BLINK / WATERCOLOR INK WASH"}[i],x+25,y+15,0xFF202020,false);
                g.flush();g.enableScissor(x+12,y+35,x+cw-12,y+ch-14);g.pose().pushPose();
                try {
                    g.pose().translate(x+cw/2.0,y+ch*.78,450);float scale=new float[]{19,4.2F,19,7,2,27}[i];g.pose().scale(scale,-scale,scale);
                    g.pose().mulPose(Axis.XP.rotationDegrees(i==5?65:17));g.pose().mulPose(Axis.YP.rotationDegrees(i==2?25:135));
                    mc.getEntityRenderDispatcher().setRenderShadow(false);
                    Entity entity=i==0?tigers[0]:i==1?tigerEffects[0]:i==2?tigers[2]:tigerEffects[i-2];
                    int age=new int[]{45,35,60,70,110,20}[i];
                    data(entity,"START",mc.level.getGameTime()-age);
                    mc.getEntityRenderDispatcher().render(entity,0,0,0,0,partial,g.pose(),buffers,LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                }catch(Throwable failure){if(!failures.contains(failure.toString()))failures.add(failure.toString());}
                finally{g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
        }
        private void renderTigerPoses(GuiGraphics g,float partial){
            g.fill(0,0,width,height,0xFFE4E4E4);g.drawString(font,"INK TIGER V5 / DOUBLED BODY + SCALED FIVEFOLD GHOST / FLANK-FOLDED WINGS",25,20,0xFF222222,false);
            var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
            for(int i=0;i<6;i++){
                int cw=width/3,ch=(height-85)/2,x=i%3*cw,y=55+i/3*ch;
                g.fill(x+10,y,x+cw-10,y+ch-12,0xFFF3F3F3);
                g.drawString(font,new String[]{"IDLE / FOLDED ALONG FLANKS","FRONT / NO DROOPING WINGS","FACE / LOWER INNER EYE CORNERS","SIDE / OVERLAPPING FOLDED FEATHERS","SPELL / FULL DOUBLED WING SPAN","GHOST / OPEN WINGS, NO FOLDED DUPLICATE"}[i],x+24,y+15,0xFF222222,false);
                g.flush();g.enableScissor(x+12,y+35,x+cw-12,y+ch-14);g.pose().pushPose();
                try{
                    g.pose().translate(x+cw/2.0,y+ch*.77,450);float scale=new float[]{48,58,120,56,19,4.2F}[i]/InkTigerAttackRules.BODY_MULTIPLIER;g.pose().scale(scale,-scale,scale);
                    g.pose().mulPose(Axis.XP.rotationDegrees(i==2?4:12));g.pose().mulPose(Axis.YP.rotationDegrees(new float[]{135,15,0,95,135,135}[i]));
                    if(i==2)g.pose().translate(0,-2.0*InkTigerAttackRules.BODY_MULTIPLIER,-3.8*InkTigerAttackRules.BODY_MULTIPLIER);
                    mc.getEntityRenderDispatcher().setRenderShadow(false);
                    Entity entity=i<3?tigers[i]:i==3?tigers[0]:i==4?tigers[3]:tigerEffects[0];
                    if(i>=4)data(entity,"START",mc.level.getGameTime()-35);
                    mc.getEntityRenderDispatcher().render(entity,0,0,0,0,partial,g.pose(),buffers,LightTexture.FULL_BRIGHT);buffers.endBatch();
                }catch(Throwable failure){if(!failures.contains(failure.toString()))failures.add(failure.toString());}
                finally{g.pose().popPose();g.disableScissor();mc.getEntityRenderDispatcher().setRenderShadow(true);}
            }
        }
    }
}
