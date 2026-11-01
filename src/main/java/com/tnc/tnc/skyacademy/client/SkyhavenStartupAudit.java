package com.tnc.tnc.skyacademy.client;

import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Explicit isolated audit: records real arrival and frame intervals, never installs or teleports. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class SkyhavenStartupAudit {
    private static long joined,lastFrame,sampleStart;
    private static int stage,loadingTicks,photoTicks,cinematicTicks,nameTicks;
    private static boolean configured,photoPrepared,saving,loadingCaptured;private static volatile boolean photoReady;
    private static final List<Double> frames=new ArrayList<>();
    private static final List<Map<String,Object>> measurements=new ArrayList<>();
    private static volatile boolean arrived,complete,named;
    private static boolean transientScreenProbed,namingSubmitted;private static long releasedAt;
    private static volatile float tickMs;
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.skyhavenStartupAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))throw new IllegalStateException("Isolated audit only");
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        // A hidden Windows GLFW surface can report a 1x1 framebuffer. Never
        // benchmark that tiny target as if it were the user's actual picture.
        if(mc.getWindow().getWidth()!=1600||mc.getWindow().getHeight()!=900){
            mc.getWindow().setWidth(1600);mc.getWindow().setHeight(900);mc.resizeDisplay();
        }
        if(mc.player!=null&&joined==0)joined=System.nanoTime();
        if(com.tnc.tnc.world.skyhaven.SkyhavenArrivalClient.active()){
            Files.createDirectories(output());
            if(!transientScreenProbed){
                transientScreenProbed=true;
                // Reproduce a vanilla joining screen arriving during our loading UI.
                // This must never be restored after the transition has completed.
                var joining=new net.minecraft.client.gui.screens.ProgressScreen(true);
                joining.progressStartNoAbort(net.minecraft.network.chat.Component.translatable("connect.joining"));mc.setScreen(joining);
            }
            if(++loadingTicks>=70&&mc.getOverlay()==null&&!loadingCaptured){loadingCaptured=true;capture(mc,"arrival-loading.png");}return;
        }
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(joined==0)joined=System.nanoTime();
        if(!configured){configured=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(8);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(120);mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.resizeDisplay();}
        mc.getSingleplayerServer().execute(()->{
            var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);
            complete=com.tnc.tnc.world.SkyIslandSavedData.get(s.overworld()).isSkyIslandComplete();
            arrived=p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean("SkyhavenArrived");
            tickMs=s.getAverageTickTime();
            named=com.tnc.tnc.journey.JourneyData.get(s).player(p.getUUID()).getBoolean("Named");
        });
        if(!arrived||!complete){if(System.nanoTime()-joined>120_000_000_000L){Files.writeString(output().resolve("failed.txt"),"No normal arrival within 120s");mc.stop();}return;}
        if(releasedAt==0)releasedAt=System.nanoTime();
        if(!named){
            if(mc.screen instanceof com.tnc.tnc.journey.client.JourneyScreen&&++nameTicks>=20&&!namingSubmitted){
                capture(mc,"arrival-naming.png");namingSubmitted=true;
                double scale=Math.min(1.3,Math.min((mc.screen.width-16)/600.,(mc.screen.height-16)/360.));
                double left=(mc.screen.width-600*scale)/2,top=(mc.screen.height-360*scale)/2;
                mc.screen.mouseClicked(left+300*scale,top+305*scale,0);mc.screen.keyPressed(65,0,2);
                for(char c:"云溪旅人".toCharArray())mc.screen.charTyped(c,0);
                mc.screen.mouseClicked(left+519*scale,top+304*scale,0);
            }
            if(System.nanoTime()-releasedAt>20_000_000_000L){Files.writeString(output().resolve("failed.txt"),"Arrival did not reach working naming UI; screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));mc.stop();}
            return;
        }
        if(mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen){Files.writeString(output().resolve("failed.txt"),"Transient joining screen restored");mc.stop();return;}
        if(AcademyClient.cinematic()&&++cinematicTicks==100)capture(mc,"arrival-cinematic.png");
        if(stage>=2){if(Boolean.getBoolean("tnc.skyhavenWalkProfile"))SkyhavenWalkProfile.tick();else photos(mc);return;}
        if(sampleStart==0){sampleStart=System.nanoTime();Files.writeString(output().resolve("arrival.txt"),"normal arrival seconds="+(sampleStart-joined)/1e9+", dimension="+mc.level.dimension().location()+", position="+mc.player.position());}
        long elapsed=System.nanoTime()-sampleStart;
        // Walk out of the arrival gate normally; standing in its trigger would
        // measure repeated dimension transfers instead of island gameplay.
        mc.options.keyUp.setDown(stage==0&&elapsed<3_000_000_000L);
        if(elapsed<30_000_000_000L)return;
        if(elapsed<60_000_000_000L)return;
        var sorted=frames.stream().sorted().toList();
        if(sorted.isEmpty())return;
        var result=new LinkedHashMap<String,Object>();result.put("f8",stage==1);result.put("frames",sorted.size());result.put("median_ms",sorted.get(sorted.size()/2));result.put("p95_ms",sorted.get((int)(sorted.size()*.95)));result.put("p99_ms",sorted.get((int)(sorted.size()*.99)));result.put("average_fps",1000/frames.stream().mapToDouble(x->x).average().orElse(1000));result.put("server_tick_ms",tickMs);result.put("heap_used_mb",(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);
        measurements.add(result);
        capture(mc,"startup-"+stage+".png");
        if(stage++==0){com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(true);frames.clear();sampleStart=System.nanoTime();lastFrame=0;return;}
    }
    private static void capture(Minecraft mc,String name){saving=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){throw new RuntimeException(ex);}finally{saving=false;}});}
    private static void photos(Minecraft mc)throws Exception {
        if(saving)return;
        if(stage>=4){Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("normal_arrival",arrived,"complete_on_entry",complete,"naming_confirmed",named,"transient_screen_probed",transientScreenProbed,"measurements",measurements,"screenshots",List.of("arrival-loading.png","startup-0.png","startup-1.png","gateway-foundation.png","sea-gyres.png"))));mc.stop();return;}
        if(!photoPrepared){photoPrepared=true;photoReady=false;photoTicks=0;com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(stage==3);
            mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);s.overworld().setWeatherParameters(12000,0,false,false);s.overworld().setDayTime(6000);
                if(stage==2)p.teleportTo(com.tnc.tnc.world.SkyIslandWorlds.island(s),-91,180,157,-80,4);
                else {var g=com.tnc.tnc.world.skyhaven.SeaGyreLayout.near(410,0).get(0);p.teleportTo(com.tnc.tnc.world.SkyIslandWorlds.island(s),g.x(),112,g.z()-20,0,74);}
                photoReady=true;
            });return;
        }
        if(photoReady&&++photoTicks>=220){capture(mc,stage==2?"gateway-foundation.png":"sea-gyres.png");stage++;photoPrepared=false;}
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("tnc.skyhavenStartupAudit")||event.phase!=TickEvent.Phase.END||sampleStart==0)return;
        long now=System.nanoTime();if(now-sampleStart>30_000_000_000L&&lastFrame!=0)frames.add((now-lastFrame)/1e6);lastFrame=now;
    }
}
