package com.tnc.tnc.world.elementarena.client;
import com.tnc.tnc.world.elementarena.ElementArenas;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Opt-in native dimension preview; excluded from the installed JAR. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ElementArenaClientAudit {
 private static int stage,ticks;private static boolean pending;private static java.util.List<String> failures=new java.util.ArrayList<>();
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
  if(!Boolean.getBoolean("tnc.elementArenaAudit")||e.phase!=TickEvent.Phase.END||stage>=6)return;
  var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null||mc.level==null)return;
  if(stage==4){stage=5;ticks=0;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->ElementArenas.travel(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid),ElementArenas.WATER));return;}
  if(stage==5){if(++ticks<100)return;if(!mc.level.dimension().equals(ElementArenas.WIND))failures.add("Water arena return failed");Files.writeString(Path.of("D:/ModTest/work/element-arena-native-report-20261023.json"),new com.google.gson.Gson().toJson(java.util.Map.of("failures",failures,"screenshots",2,"returnChecked",true)));stage=6;mc.stop();return;}
  if(stage==0||stage==2){if(pending)return;pending=true;mc.setScreen(null);var uuid=mc.player.getUUID();var key=stage==0?ElementArenas.WIND:ElementArenas.WATER;mc.getSingleplayerServer().execute(()->{
   var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var level=p.server.getLevel(key);
   if(level==null){failures.add("Missing dimension: "+key);return;}p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);if(p.level().dimension().equals(key))p.teleportTo(p.server.overworld(),0,90,0,0,0);ElementArenas.travel(p,key);
  });stage++;ticks=0;return;}
  if(!mc.level.dimension().equals(stage==1?ElementArenas.WIND:ElementArenas.WATER))return;
  if(++ticks==20){var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);p.serverLevel().getChunk(4381,4381);p.teleportTo(p.serverLevel(),70100.5,58,70100.5,135,12);p.getAbilities().flying=true;p.onUpdateAbilities();if(p.level().dimension().equals(ElementArenas.WATER))p.setAirSupply(0);});}
  if(ticks<180)return;var name=stage==1?"wind-chessboard-arena.png":"water-undersea-arena.png";
  if(mc.getBlockRenderer().getBlockModel(ElementArenas.LIGHT.defaultBlockState())==mc.getModelManager().getMissingModel())failures.add("Wind block model missing");
  Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});stage++;pending=false;

 }
}
