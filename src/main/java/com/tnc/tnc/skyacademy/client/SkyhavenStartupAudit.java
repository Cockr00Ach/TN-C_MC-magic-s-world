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
    private static int stage;
    private static boolean configured;
    private static final List<Double> frames=new ArrayList<>();
    private static final List<Map<String,Object>> measurements=new ArrayList<>();
    private static volatile boolean arrived,complete;
    private static volatile float tickMs;
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.skyhavenStartupAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(Path.of("D:/TNC-Audits/dungeons-20261019").toAbsolutePath().normalize()))throw new IllegalStateException("Isolated audit only");
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(joined==0)joined=System.nanoTime();
        if(!configured){configured=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(8);mc.options.simulationDistance().set(4);mc.options.framerateLimit().set(120);mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);mc.resizeDisplay();}
        mc.getSingleplayerServer().execute(()->{
            var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);
            complete=com.tnc.tnc.world.SkyIslandSavedData.get(s.overworld()).isSkyIslandComplete();
            arrived=p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean("SkyhavenArrived");
            tickMs=s.getAverageTickTime();
            // Dismiss only the naming UI in this isolated performance test.
            com.tnc.tnc.journey.JourneyData.get(s).player(p.getUUID()).putBoolean("Named",true);
        });
        mc.setScreen(null);
        if(!arrived||!complete){if(System.nanoTime()-joined>120_000_000_000L){Files.writeString(output().resolve("failed.txt"),"No normal arrival within 120s");mc.stop();}return;}
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
        int shot=stage;
        Screenshot.grab(mc.gameDirectory,"startup-"+shot+".png",mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/startup-"+shot+".png"),output().resolve("startup-"+shot+".png"),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){throw new RuntimeException(ex);}});
        if(stage++==0){com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(true);frames.clear();sampleStart=System.nanoTime();lastFrame=0;return;}
        Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("normal_arrival",arrived,"complete_on_entry",complete,"measurements",measurements)));
        mc.stop();
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(!Boolean.getBoolean("tnc.skyhavenStartupAudit")||event.phase!=TickEvent.Phase.END||sampleStart==0)return;
        long now=System.nanoTime();if(now-sampleStart>30_000_000_000L&&lastFrame!=0)frames.add((now-lastFrame)/1e6);lastFrame=now;
    }
}
