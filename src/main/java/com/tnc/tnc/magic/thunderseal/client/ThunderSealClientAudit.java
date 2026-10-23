package com.tnc.tnc.magic.thunderseal.client;
import com.tnc.tnc.magic.thunderseal.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ThunderSealClientAudit {
 private static int tier=1,entityId=-1,ticks;private static boolean waiting,pulse,impact;private static java.util.List<String> failures=new java.util.ArrayList<>();
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
  if(!Boolean.getBoolean("tnc.thunderSealAudit")||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
  mc.options.hideGui=true;mc.getToasts().clear();
  if(tier>5){Files.writeString(Path.of("D:/ModTest/work/thunder-seal-native-report-20261023.json"),new com.google.gson.Gson().toJson(java.util.Map.of("failures",failures,"screenshots",10)));mc.options.hideGui=false;mc.stop();return;}
  if(!waiting){waiting=true;pulse=false;impact=false;ticks=0;var uuid=mc.player.getUUID();int t=tier;
   mc.setScreen(null);mc.getSingleplayerServer().execute(()->{
    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var l=p.server.overworld();l.setDayTime(13500);
    for(int x=570;x<=630;x++)for(int z=570;z<=630;z++)l.setBlock(new net.minecraft.core.BlockPos(x,80,z),net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES.defaultBlockState(),2);
    p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);double distance=12+t*5;p.teleportTo(l,600,84+t*3,600+distance,180,16);
    var seal=ThunderSealContent.SEAL.create(l);seal.configure(p,t,new net.minecraft.world.phys.Vec3(600,81,600),null);l.addFreshEntity(seal);entityId=seal.getId();
   });return;
  }
  if(++ticks>240){failures.add("Tier "+tier+" did not track");waiting=false;tier++;return;}
  if(!(mc.level.getEntity(entityId) instanceof ThunderSealEntity e)){if(impact){waiting=false;tier++;}return;}
  double age=e.age(0);
  if(age>=ThunderSealRules.charge(tier)+1&&!pulse){
   pulse=true;var model=mc.getModelManager().getModel(new net.minecraft.client.resources.model.ModelResourceLocation(ThunderSealContent.id("thunder_seal_pylon"),"inventory"));if(model==mc.getModelManager().getMissingModel()||model.getQuads(null,null,net.minecraft.util.RandomSource.create()).size()<30)failures.add("Pylon model missing");
   if(model.getQuads(null,null,net.minecraft.util.RandomSource.create()).stream().anyMatch(q->q.getSprite().contents().name().getPath().contains("missing")))failures.add("Pylon texture missing");
   Screenshot.grab(mc.gameDirectory,"thunder-seal-t"+tier+"-pulse.png",mc.getMainRenderTarget(),m->{});
  }
  if(age>=ThunderSealRules.finish(tier)+2&&!impact){impact=true;Screenshot.grab(mc.gameDirectory,"thunder-seal-t"+tier+"-impact.png",mc.getMainRenderTarget(),m->{});if(org.lwjgl.opengl.GL11.glGetError()!=0)failures.add("Render GL error at tier "+tier);}
 }
}
