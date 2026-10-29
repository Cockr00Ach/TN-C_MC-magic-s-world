package com.tnc.tnc.world;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
/** World boundary orchestration; shared island generation remains checkpointed by its manager. */
@Mod.EventBusSubscriber(modid="tnc")
public final class SkyhavenTravel {
 private static final BlockPos WAIT=new BlockPos(640,180,640);
 private static net.minecraft.nbt.CompoundTag flags(ServerPlayer p){var root=p.getPersistentData();if(!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,10))root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,new net.minecraft.nbt.CompoundTag());return root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);}
 private static final Set<UUID> PENDING=new HashSet<>();
 private static final Map<MinecraftServer,Set<ChunkPos>> GATES=new WeakHashMap<>();
 private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_skyhaven_gate",Comparator.comparingLong(ChunkPos::toLong),40);
 public static void login(ServerPlayer p){
  if(!SkyIslandWorlds.separate(p.server))return;
  var t=flags(p);if(t.getBoolean("SkyhavenArrived"))return;
  if(!PENDING.add(p.getUUID()))return;
  var target=SkyIslandWorlds.island(p.server);
  target.getChunkSource().getChunkFuture(WAIT.getX()>>4,WAIT.getZ()>>4,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((c,e)->{
   try{if(e!=null||c==null||c.left().isEmpty()||p.hasDisconnected())return;
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)target.setBlock(WAIT.offset(x,-1,z),Blocks.SMOOTH_QUARTZ.defaultBlockState(),18);
    p.teleportTo(target,WAIT.getX()+.5,WAIT.getY(),WAIT.getZ()+.5,180,0);
    t.putBoolean("SkyhavenWaiting",true);
    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(Math.abs(x)==3||Math.abs(z)==3)for(int y=0;y<2;y++)target.setBlock(WAIT.offset(x,y,z),Blocks.GLASS.defaultBlockState(),18);
   }finally{PENDING.remove(p.getUUID());}
  },p.server);
 }
 static boolean groundGate(ServerLevel main,SkyIslandSavedData data,SkyIslandManifest manifest)throws java.io.IOException{
  var s=main.getServer();if(SkyIslandWorlds.Choice.get(s).groundBuilt)return true;var origin=new BlockPos(data.groundPortalX,data.groundPortalY,data.groundPortalZ);var min=PortalArchitecture.footprintOrigin(origin);var wanted=new HashSet<ChunkPos>();
  for(int x=min.getX()>>4;x<=(min.getX()+PortalArchitecture.WIDTH-1)>>4;x++)for(int z=min.getZ()>>4;z<=(min.getZ()+PortalArchitecture.WIDTH-1)>>4;z++)wanted.add(new ChunkPos(x,z));
  GATES.put(s,wanted);for(var c:wanted)main.getChunkSource().addRegionTicket(TICKET,c,2,c);
  if(wanted.stream().anyMatch(c->main.getChunkSource().getChunkNow(c.x,c.z)==null||!main.areEntitiesLoaded(c.toLong())))return false;
  try {var gate=PortalArchitecture.prepare(main,manifest.groundPortal(),origin,false);var supports=PortalArchitecture.prepareSupports(main,origin);gate.apply(main);supports.apply(main);main.getChunkSource().save(true);var choice=SkyIslandWorlds.Choice.get(s);choice.groundBuilt=true;choice.setDirty();return true;}
  catch(PortalArchitecture.SiteOccupied occupied){return false;}
  finally{for(var c:wanted)main.getChunkSource().removeRegionTicket(TICKET,c,2,c);GATES.remove(s);}
 }
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
  if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!SkyIslandWorlds.separate(e.getServer()))return;
  var s=e.getServer();var world=SkyIslandWorlds.island(s);var data=SkyIslandSavedData.get(world);
  if(!data.isSkyIslandComplete()||!com.tnc.tnc.skyacademy.SkyAcademyWorld.ready(s))return;
  var at=data.anchorPos("ARRIVAL");
  for(var p:s.getPlayerList().getPlayers())if(flags(p).getBoolean("SkyhavenWaiting")&&p.isAlive()&&PENDING.add(p.getUUID())){
   world.getChunkSource().getChunkFuture(at.getX()>>4,at.getZ()>>4,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((c,error)->{
    try{if(error!=null||p.hasDisconnected()||c==null||c.left().isEmpty())return;
     p.teleportTo(world,at.getX()+.5,at.getY(),at.getZ()+.5,180,0);p.setRespawnPosition(world.dimension(),at,180,true,false);
     flags(p).putBoolean("SkyhavenWaiting",false);flags(p).putBoolean("SkyhavenArrived",true);
    }finally{PENDING.remove(p.getUUID());}
   },s);
  }
 }
 @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){var pending=GATES.remove(e.getServer());if(pending!=null)for(var c:pending)e.getServer().overworld().getChunkSource().removeRegionTicket(TICKET,c,2,c);PENDING.clear();}
 @SubscribeEvent public static void respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p&&!flags(p).getBoolean("SkyhavenArrived"))login(p);}
 @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent e){SkyIslandWorlds.releaseUnusedSpawn(e.getServer());}
 private SkyhavenTravel(){}
}
