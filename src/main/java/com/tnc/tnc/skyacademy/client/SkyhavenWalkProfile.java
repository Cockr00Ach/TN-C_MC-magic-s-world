package com.tnc.tnc.skyacademy.client;
import com.tnc.tnc.skyacademy.SkyAcademyWorld;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;
/** Explicit isolated walking/GC diagnostic. No production execution path. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class SkyhavenWalkProfile {
 private static final int[][] SITES={{260,117,265},{87,110,340},{420,115,369},{468,140,222},{258,157,61},{260,117,265}};
 private static final String[] NAMES={"town","farm","workshop","lake","ridge","town-repeat"};
 private static final List<Double> FRAMES=new ArrayList<>();private static final List<Map<String,Object>> RESULTS=new ArrayList<>();
 private static int stage;private static long start,last,gcAt,gcMsAt;private static volatile boolean placed;private static volatile float tickMs;
 private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x->Math.max(0,x.getCollectionCount())).sum();}
 private static long gcMs(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x->Math.max(0,x.getCollectionTime())).sum();}
 private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
 public static void tick()throws Exception{
  var mc=Minecraft.getInstance();if(stage>=SITES.length){mc.options.keyUp.setDown(false);Files.writeString(output().resolve("walk-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));mc.stop();return;}
  if(start==0){start=System.nanoTime();placed=false;FRAMES.clear();last=0;gcAt=gcCount();gcMsAt=gcMs();com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(false);
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);var o=SkyAcademyWorld.origin(s);var v=SITES[stage];p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.teleportTo(com.tnc.tnc.world.SkyIslandWorlds.island(s),o.getX()+v[0]+.5,o.getY()+v[1],o.getZ()+v[2]+.5,90,15);placed=true;});return;}
  if(!placed)return;mc.getSingleplayerServer().execute(()->tickMs=mc.getSingleplayerServer().getAverageTickTime());
  long elapsed=System.nanoTime()-start;mc.options.keyUp.setDown(elapsed>10_000_000_000L&&elapsed<48_000_000_000L);
  if(elapsed<50_000_000_000L)return;mc.options.keyUp.setDown(false);
  var sorted=FRAMES.stream().sorted().toList();var r=new LinkedHashMap<String,Object>();r.put("site",NAMES[stage]);r.put("frames",sorted.size());
  if(!sorted.isEmpty()){r.put("p50_ms",sorted.get(sorted.size()/2));r.put("p95_ms",sorted.get((int)(sorted.size()*.95)));r.put("p99_ms",sorted.get((int)(sorted.size()*.99)));r.put("over_50ms",sorted.stream().filter(x->x>50).count());r.put("over_200ms",sorted.stream().filter(x->x>200).count());}
  r.put("framebuffer_width",mc.getMainRenderTarget().viewWidth);r.put("framebuffer_height",mc.getMainRenderTarget().viewHeight);r.put("gc_count",gcCount()-gcAt);r.put("gc_ms",gcMs()-gcMsAt);r.put("heap_used_mb",(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);r.put("server_tick_ms",tickMs);RESULTS.add(r);
  String imageName=NAMES[stage]+"-walk.png";Screenshot.grab(mc.gameDirectory,imageName,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/"+imageName),output().resolve(imageName),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){throw new IllegalStateException(ex);}});
  Files.writeString(output().resolve("walk-progress.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));stage++;start=0;
 }
 @SubscribeEvent public static void frame(TickEvent.RenderTickEvent e){if(!Boolean.getBoolean("tnc.skyhavenWalkProfile")||e.phase!=TickEvent.Phase.END||start==0)return;long now=System.nanoTime();if(last!=0&&now-start>10_000_000_000L)FRAMES.add((now-last)/1e6);last=now;}
 private SkyhavenWalkProfile(){}
}
