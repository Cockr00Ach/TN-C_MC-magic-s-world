package com.tnc.tnc.dungeon;

import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Authoritative instance lifecycle. The client never supplies room destinations or rewards. */
public final class DungeonService {
    private static final Map<UUID,Long> TELEPORTS=new HashMap<>(),MISSING=new HashMap<>();
    private static final Map<UUID,net.minecraft.server.level.ServerBossEvent> BARS=new HashMap<>();
    private static final Map<UUID,UUID> RECOVERIES=new HashMap<>();
    public static DungeonData.Run run(ServerPlayer p){var d=DungeonData.get(p.server);var t=d.profile(p.getUUID());var r=t.hasUUID("Run")?d.runs.get(t.getUUID("Run")):null;return r!=null&&!r.closed&&r.members.contains(p.getUUID())?r:null;}
    public static boolean active(ServerPlayer p){var r=run(p);return DungeonRealm.inside(p.level())&&r!=null&&r.floor>0;}
    public static boolean recovering(ServerPlayer p){return active(p)&&DungeonData.get(p.server).profile(p.getUUID()).getBoolean("Recovering");}
    public static void resume(ServerPlayer p){if(!recovering(p)||DownedCombat.isDowned(p))return;DungeonData.get(p.server).profile(p.getUUID()).remove("Recovering");p.invulnerableTime=100;DungeonData.get(p.server).setDirty();message(p,"已继续挑战 · 入场保护5秒，倒地与自救次数保持原状");}
    public static BlockPos recoveryPoint(ServerPlayer p,DungeonData.Run r){
      if(r.floor==0)return DungeonRealm.lobby(r).above();
      int active=com.tnc.tnc.dungeon.leyline.LeylineService.sealedMask(r);if(active!=0)return com.tnc.tnc.dungeon.leyline.LeylineService.landing(r,r.graph().rooms.get(Integer.numberOfTrailingZeros(active)));
      for(var q:online(p.server,r))if(q!=p){var room=room(q);if(room!=null)return com.tnc.tnc.dungeon.leyline.LeylineService.landing(r,room);}
      var t=DungeonData.get(p.server).profile(p.getUUID());int last=t.getInt("LastRoom");if(t.getInt("LastFloor")==r.floor&&t.hasUUID("CursorRun")&&t.getUUID("CursorRun").equals(r.id)&&last>=0&&last<8)return com.tnc.tnc.dungeon.leyline.LeylineService.landing(r,r.graph().rooms.get(last));
      return DungeonRealm.center(r,r.graph().rooms.get(0)).above();
    }
    public static void beginRecovery(ServerPlayer p){var r=run(p);if(r==null)return;var d=DungeonData.get(p.server);var t=d.profile(p.getUUID());var target=recoveryPoint(p,r);t.putBoolean("Recovering",r.floor>0&&!DownedCombat.isDowned(p));t.putLong("RecoverPos",target.asLong());t.putUUID("CursorRun",r.id);t.putInt("LastFloor",r.floor);for(var room:r.graph().rooms)if(com.tnc.tnc.dungeon.leyline.LeylineService.landing(r,room).equals(target))t.putInt("LastRoom",room.id());com.tnc.tnc.magic.compat.CombatCasting.cancel(p);RECOVERIES.put(p.getUUID(),r.id);d.setDirty();if(DownedCombat.isDowned(p)){message(p,"已恢复到队伍当前楼层，仍然倒地，请等待队友救援");return;}p.sendSystemMessage(Component.literal("已安全恢复挑战。点击这里确认继续；进度、生命和倒地次数保持原状。").withStyle(s->s.withColor(net.minecraft.ChatFormatting.AQUA).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/dungeon resume"))));}
    public static String rescueGroup(ServerPlayer p){var r=run(p);return DungeonRealm.inside(p.level())&&r!=null?"dungeon:"+r.id:null;}
    public static boolean solo(ServerPlayer p){var r=run(p);return r!=null&&!r.multiplayer;}
    public static boolean takeSoloDown(ServerPlayer p){var r=run(p);if(r==null||r.multiplayer||!r.soloUsed.add(p.getUUID()))return false;DungeonData.get(p.server).setDirty();return true;}
    public static boolean mayTarget(DungeonMob mob,ServerPlayer p){if(p==null||p.isCreative()||p.isSpectator()||!p.isAlive()||DownedCombat.isDowned(p)||recovering(p))return false;if(!mob.getPersistentData().hasUUID("DungeonRun"))return true;var r=run(p);return r!=null&&r.id.equals(mob.getPersistentData().getUUID("DungeonRun"))&&DungeonRealm.inside(p.level());}
    public static List<ServerPlayer> online(MinecraftServer s,DungeonData.Run r){return s.getPlayerList().getPlayers().stream().filter(p->r.members.contains(p.getUUID())&&DungeonRealm.inside(p.level())).toList();}
    public static int unlocked(ServerPlayer p,DungeonTheme theme){return Math.max(1,Math.min(26,DungeonData.get(p.server).profile(p.getUUID()).getInt("Unlock_"+theme.id)));}
    public static void message(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),true);}
    public static void enter(ServerPlayer p,DungeonTheme theme){
      if(DownedCombat.isDowned(p)||!p.isAlive()||p.isSpectator()||DungeonRealm.inside(p.level()))return;
      var level=p.server.getLevel(DungeonRealm.KEY);if(level==null){message(p,"地下城维度尚未载入，请重新启动世界");return;}
      var d=DungeonData.get(p.server);var profile=d.profile(p.getUUID());var r=run(p);
      if(r!=null&&r.theme!=theme){message(p,"你还有另一主题的挑战，先用 /dungeon abandon 放弃后再换主题");return;}
      profile.putString("ReturnDimension",p.level().dimension().location().toString());profile.putDouble("ReturnX",p.getX());profile.putDouble("ReturnY",p.getY());profile.putDouble("ReturnZ",p.getZ());profile.putFloat("ReturnYaw",p.getYRot());
      if(r==null){r=new DungeonData.Run(UUID.randomUUID(),p.getUUID(),p.getRandom().nextLong(),d.nextSlot++,theme);d.runs.put(r.id,r);profile.putUUID("Run",r.id);profile.putLong("Pending",0);}
      d.setDirty();var ready=r;
      if(r.floor==0){DungeonRealm.build(level,r,DungeonRealm.lobbyCells(r),()->{teleport(p,ready,DungeonRealm.lobby(ready).offset(0,1,2));DungeonNetwork.open(p,"gate");});}
      else beginRecovery(p);
    }
    private static boolean validRoomPosition(DungeonData.Run r,BlockPos p){for(var room:r.graph().rooms)if(DungeonRealm.center(r,room).distSqr(p)<26*26)return true;return false;}
    public static void prepare(ServerLevel level,DungeonData.Run r,Runnable done){if(r.building)return;if(r.state().built){done.run();return;}DungeonRealm.build(level,r,DungeonRealm.floorCells(r),()->{r.state().built=true;DungeonData.get(level.getServer()).setDirty();done.run();});}
    public static void teleport(ServerPlayer p,DungeonData.Run r,BlockPos target){
      if(p.hasDisconnected()||!p.isAlive()||r.closed||!r.members.contains(p.getUUID()))return;var level=p.server.getLevel(DungeonRealm.KEY);if(level==null)return;
      long token=TELEPORTS.merge(p.getUUID(),1L,Long::sum);var cp=new net.minecraft.world.level.ChunkPos(target);level.getChunkSource().getChunkFuture(cp.x,cp.z,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((c,e)->{
        if(e!=null||r.closed||!Objects.equals(TELEPORTS.get(p.getUUID()),token)||p.hasDisconnected()||run(p)!=r)return;
        p.stopRiding();p.fallDistance=0;p.teleportTo(level,target.getX()+.5,target.getY(),target.getZ()+.5,180,0);p.invulnerableTime=80;DungeonTavern.award(p,"entered");DungeonData.get(p.server).profile(p.getUUID()).putBoolean("Entered",true);DungeonData.get(p.server).profile(p.getUUID()).putLong("ResumePos",target.asLong());
      },p.server);
    }
    public static void start(ServerPlayer p,int floor){var r=run(p);if(r==null||r.floor!=0||!r.leader.equals(p.getUUID())||floor<1||floor>26||(floor-1)%5!=0||floor>unlocked(p,r.theme)||r.building||DownedCombat.isDowned(p)||!near(p,DungeonRealm.lobby(r),12))return;
      var party=online(p.server,r);if(party.size()!=r.members.size()||party.stream().anyMatch(q->DownedCombat.isDowned(q)||recovering(q)||!near(q,DungeonRealm.lobby(r),12))){message(p,"请全队到入口集合，救起倒地队员后再开始");return;}for(var q:party)if(floor>unlocked(q,r.theme)){message(p,"队伍里有人还没有解锁这个五层段");return;}
      var consent=new HashMap<UUID,Long>();for(var q:party)consent.put(q.getUUID(),TELEPORTS.getOrDefault(q.getUUID(),0L));
      r.multiplayer=r.members.size()>1;r.segment=floor;r.floor=floor;r.soloUsed.clear();DungeonData.get(p.server).setDirty();prepare(p.serverLevel(),r,()->{
        if(!departureValid(r,floor,party,consent)){if(!r.closed&&r.floor==floor){r.floor=0;DungeonData.get(p.server).setDirty();party.stream().filter(q->!q.hasDisconnected()).forEach(q->message(q,"队伍状态已改变，本次启程取消。请回到大厅重新确认备战。"));}return;}
        party.forEach(q->teleport(q,r,DungeonRealm.center(r,r.graph().rooms.get(0)).above()));
      });
    }
    static boolean departureValid(DungeonData.Run r,int floor,List<ServerPlayer> party,Map<UUID,Long> consent){return !r.closed&&r.floor==floor&&r.members.equals(consent.keySet())&&party.size()==consent.size()&&party.stream().allMatch(q->!q.hasDisconnected()&&q.isAlive()&&!q.isSpectator()&&!DownedCombat.isDowned(q)&&run(q)==r&&DungeonRealm.inside(q.level())&&near(q,DungeonRealm.lobby(r),12)&&Objects.equals(TELEPORTS.getOrDefault(q.getUUID(),0L),consent.get(q.getUUID())));}
    public static boolean near(ServerPlayer p,BlockPos pos,int range){return p.blockPosition().distSqr(pos)<range*range;}
    public static DungeonGraph.Room room(ServerPlayer p){var r=run(p);if(r==null||r.floor==0||!DungeonRealm.inside(p.level()))return null;for(var room:r.graph().rooms){var o=DungeonRealm.center(r,room);int half=room.kind()==DungeonGraph.Kind.GUARDIAN?24:11;if(Math.abs(p.getX()-(o.getX()+.5))<half-.4&&Math.abs(p.getZ()-(o.getZ()+.5))<half-.4&&p.getY()>=o.getY()-10&&p.getY()<o.getY()+32)return room;}return null;}
    public static boolean validateRoom(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room){var t=DungeonData.get(p.server).profile(p.getUUID());if(!t.hasUUID("CursorRun")||!t.getUUID("CursorRun").equals(r.id)||t.getInt("LastFloor")!=r.floor){t.putUUID("CursorRun",r.id);t.putInt("LastFloor",r.floor);t.putInt("LastRoom",0);}int last=Math.max(0,Math.min(7,t.getInt("LastRoom")));if(last==room.id())return true;var from=r.graph().rooms.get(last);if(!from.links().contains(room.id())||!com.tnc.tnc.dungeon.leyline.LeylineService.traversable(r,from)){teleport(p,r,com.tnc.tnc.dungeon.leyline.LeylineService.landing(r,from));message(p,"先清理当前房间，再从相连的门前进");return false;}t.putInt("LastRoom",room.id());return true;}
    public static void next(ServerPlayer p){var r=run(p);var room=room(p);if(r==null||room==null||r.building||DownedCombat.isDowned(p))return;
      if(!validateRoom(p,r,room))return;if(room.kind()!=DungeonGraph.Kind.EXIT&&room.kind()!=DungeonGraph.Kind.GUARDIAN)return;if(!near(p,DungeonRealm.center(r,room).offset(0,1,3),5))return;
      if(room.kind()==DungeonGraph.Kind.GUARDIAN&&!r.state().clear(room.id())){message(p,"先完成守关试炼");return;}
      var party=online(p.server,r);if(party.stream().anyMatch(q->DownedCombat.isDowned(q)||recovering(q))){message(p,"先救起倒地队友，并等待重新上线的队员确认继续");return;}
      if(party.stream().anyMatch(q->!near(q,DungeonRealm.center(r,room),room.kind()==DungeonGraph.Kind.GUARDIAN?30:16))){message(p,"请队友到出口房集合");return;}
      if(!r.state().events.getBoolean("CommissionFloorPaid")){for(var q:party)com.tnc.tnc.journey.CommissionService.progress(q,"floor",r.theme.id);r.state().events.putBoolean("CommissionFloorPaid",true);}
      if(r.floor==30){r.floor=0;r.floors.clear();r.soloUsed.clear();DungeonRealm.build(p.serverLevel(),r,DungeonRealm.lobbyCells(r),()->party.forEach(q->{teleport(q,r,DungeonRealm.lobby(r).above());q.sendSystemMessage(Component.literal("已完成「"+r.theme.name+"」三十层基础试炼！专属首领机制后续补充。"));}));}
      else{r.floor++;if((r.floor-1)%5==0){r.segment=r.floor;r.soloUsed.clear();}prepare(p.serverLevel(),r,()->party.forEach(q->teleport(q,r,DungeonRealm.center(r,r.graph().rooms.get(0)).above())));}
      DungeonData.get(p.server).setDirty();
    }
    public static void tick(MinecraftServer server){var level=server.getLevel(DungeonRealm.KEY);if(level==null)return;DungeonRealm.tick(level);com.tnc.tnc.dungeon.leyline.LeylineService.tick(server);long now=server.overworld().getGameTime();if(now%10!=0)return;
      for(var entry:new ArrayList<>(RECOVERIES.entrySet())){var p=server.getPlayerList().getPlayer(entry.getKey());if(p==null){RECOVERIES.remove(entry.getKey());continue;}var r=run(p);if(r==null||!r.id.equals(entry.getValue())){RECOVERIES.remove(entry.getKey());continue;}if(r.building)continue;if(!r.state().built){if(r.floor==0)DungeonRealm.build(level,r,DungeonRealm.lobbyCells(r),()->r.state().built=true);else prepare(level,r,()->{});continue;}var t=DungeonData.get(server).profile(p.getUUID());var arrival=recoveryPoint(p,r);t.putLong("RecoverPos",arrival.asLong());t.putUUID("CursorRun",r.id);t.putInt("LastFloor",r.floor);if(r.floor>0)for(var room:r.graph().rooms)if(com.tnc.tnc.dungeon.leyline.LeylineService.landing(r,room).equals(arrival))t.putInt("LastRoom",room.id());DownedCombat.relocateAnchor(p,level,arrival);teleport(p,r,arrival);RECOVERIES.remove(entry.getKey());if(r.floor==0&&!DownedCombat.isDowned(p))DungeonNetwork.open(p,"gate");}

      for(var r:new ArrayList<>(DungeonData.get(server).runs.values())){var party=online(server,r);if(r.closed||r.floor==0||r.building||party.isEmpty())continue;var f=r.state();if(!f.built){prepare(level,r,()->party.forEach(p->teleport(p,r,DungeonRealm.center(r,r.graph().rooms.get(0)).above())));continue;}
        for(var p:party){if(recovering(p))continue;var room=room(p);if(room==null){if(p.getY()<70)teleport(p,r,DungeonRealm.center(r,r.graph().rooms.get(0)).above());continue;}if(!validateRoom(p,r,room))continue;f.visited|=1<<room.id();if(!com.tnc.tnc.dungeon.leyline.LeylineService.puzzle(r,room)&&room.kind()!=DungeonGraph.Kind.COMBAT&&room.kind()!=DungeonGraph.Kind.GUARDIAN&&(f.started&(1<<room.id()))==0)f.cleared|=1<<room.id();DungeonData.get(server).profile(p.getUUID()).putLong("ResumePos",p.blockPosition().asLong());if((room.kind()==DungeonGraph.Kind.COMBAT||room.kind()==DungeonGraph.Kind.GUARDIAN)&&!f.clear(room.id())&&(f.started&(1<<room.id()))==0){
          if(party.stream().anyMatch(q->!near(q,DungeonRealm.center(r,room),room.kind()==DungeonGraph.Kind.GUARDIAN?23:9))){message(p,"队员集合到房内后，试炼才会开始");continue;}
          if(party.stream().anyMatch(DownedCombat::isDowned))continue;startRoom(level,r,room,party);
        }if(now%40==0)DungeonNetwork.hud(p,r,room);}
        for(var room:r.graph().rooms)if((f.started&(1<<room.id()))!=0&&!f.clear(room.id()))checkRoom(level,r,room,party,now);
      }if(now%100==0)DungeonData.get(server).setDirty();
    }
    public static void trackSpawn(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,DungeonMob mob){
      var data=mob.getPersistentData();data.putUUID("DungeonRun",r.id);data.putInt("DungeonFloor",r.floor);data.putInt("DungeonRoom",room.id());data.putLong("DungeonRoomOrigin",DungeonRealm.center(r,room).asLong());
      var f=r.state();String k=key(room.id());if(!f.enemies.contains(k,10))f.enemies.put(k,new CompoundTag());var saved=new CompoundTag();mob.save(saved);f.enemies.getCompound(k).put(mob.getUUID().toString(),saved);DungeonData.get(l.getServer()).setDirty();
    }
    private static String key(int room){return Integer.toString(room);}
    static boolean startRoom(ServerLevel level,DungeonData.Run r,DungeonGraph.Room room,List<ServerPlayer> party){
      if(com.tnc.tnc.dungeon.leyline.LeylineService.begin(level,r,room,party))return true;
      // Only one encounter may be sealed at once: a split party cannot lock each other out.
      if((com.tnc.tnc.dungeon.leyline.LeylineService.sealedMask(r))!=0)return false;
      if(party.isEmpty()||party.stream().anyMatch(p->DownedCombat.isDowned(p)||recovering(p)||!near(p,DungeonRealm.center(r,room),room.kind()==DungeonGraph.Kind.GUARDIAN?22:9))){party.forEach(p->message(p,"请全队到房内安全区域集合，再开始试炼"));return false;}
      var f=r.state();f.started|=1<<room.id();var participants=new ListTag();party.forEach(p->participants.add(StringTag.valueOf(p.getUUID().toString())));f.participants.put(key(room.id()),participants);
      DungeonRealm.doors(level,r,room,true);f.events.putLong("SpawnAt"+room.id(),level.getServer().overworld().getGameTime()+20);DungeonData.get(level.getServer()).setDirty();party.forEach(p->message(p,"法阵正在亮起 · "+(room.kind()==DungeonGraph.Kind.GUARDIAN?"基础守关者（专属机制待补）":"一秒后迎战，清场后开门")));return true;
    }
    private static void spawnWave(ServerLevel level,DungeonData.Run r,DungeonGraph.Room room,int players,boolean second){
      if(room.kind()==DungeonGraph.Kind.COMBAT&&!com.tnc.tnc.dungeon.leyline.LeylineService.nodes(com.tnc.tnc.dungeon.leyline.LeylineService.state(r,room),"nest").isEmpty()){
        com.tnc.tnc.dungeon.leyline.LeylineBroods.spawn(level,r,room,players,second);return;
      }
      var f=r.state();var enemies=f.enemies.getCompound(key(room.id()));f.enemies.put(key(room.id()),enemies);var pool=MobCatalog.pool(r.theme);var rng=new Random(r.seed+r.floor*883L+room.id()*79L+(second?991:0));boolean guardian=room.kind()==DungeonGraph.Kind.GUARDIAN;int count=guardian?1:3+Math.min(3,players-1)+(room.layout()==1?0:1);
      if(room.kind()==DungeonGraph.Kind.TREASURE)count=1;
      for(int i=0;i<count;i++){var species=room.kind()==DungeonGraph.Kind.TREASURE?MobCatalog.find(r.floor>15?"mimic_iron":"mimic_wood"):guardian?MobCatalog.find(switch(r.theme){case DESERT->"goblin_bulwark";case CRYPT->"skeleton_warden";case TIDE->"giant_toad";case ASTRAL->"demon_brute";}):pool.get(rng.nextInt(pool.size()));var mob=DungeonContent.MOBS.get(species.id()).create(level);if(mob==null)continue;
        var o=DungeonRealm.center(r,room);double a=i*Math.PI*2/count;mob.setPos(o.getX()+.5+Math.cos(a)*5,o.getY()+1,o.getZ()+.5+Math.sin(a)*5);mob.configure(r.floor,players,guardian);mob.getPersistentData().putUUID("DungeonRun",r.id);mob.getPersistentData().putInt("DungeonFloor",r.floor);mob.getPersistentData().putInt("DungeonRoom",room.id());mob.getPersistentData().putLong("DungeonRoomOrigin",o.asLong());
        if(level.addFreshEntity(mob)){var t=new CompoundTag();mob.save(t);enemies.put(mob.getUUID().toString(),t);}
      }
    }
    static void checkRoom(ServerLevel level,DungeonData.Run r,DungeonGraph.Room room,List<ServerPlayer> party,long now){var f=r.state();String spawnKey="SpawnAt"+room.id();if(f.events.contains(spawnKey)){if(now<f.events.getLong(spawnKey)){var o=DungeonRealm.center(r,room);level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,o.getX()+.5,o.getY()+1,o.getZ()+.5,16,5,.1,5,.06);return;}f.events.remove(spawnKey);spawnWave(level,r,room,Math.max(1,r.state().participants.getList(key(room.id()),8).size()),false);}var enemies=f.enemies.getCompound(key(room.id()));boolean alive=false;
      for(String id:new HashSet<>(enemies.getAllKeys())){UUID uuid;try{uuid=UUID.fromString(id);}catch(IllegalArgumentException ex){continue;}var e=level.getEntity(uuid);
        if(e instanceof DungeonMob mob){if(!mob.isAlive()){enemies.remove(id);continue;}alive=true;MISSING.remove(uuid);if(now%40==0){var t=new CompoundTag();mob.save(t);enemies.put(id,t);}if(mob.guardian()){var bar=BARS.computeIfAbsent(uuid,u->new net.minecraft.server.level.ServerBossEvent(mob.getDisplayName(),net.minecraft.world.BossEvent.BossBarColor.BLUE,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS));bar.setProgress(mob.getHealth()/mob.getMaxHealth());party.forEach(bar::addPlayer);}}
        else{alive=true;var center=DungeonRealm.center(r,room);if(!level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(center).toLong()))continue;long since=MISSING.computeIfAbsent(uuid,u->now);if(now-since>=200){var loaded=EntityType.loadEntityRecursive(enemies.getCompound(id),level,m->m);if(loaded!=null&&level.getEntity(uuid)==null)level.addFreshEntity(loaded);MISSING.remove(uuid);}}
      }
      if(com.tnc.tnc.dungeon.leyline.LeylineService.puzzle(r,room))return;
      if(alive)return;
      if(room.kind()==DungeonGraph.Kind.COMBAT&&room.layout()==1&&(f.waves&(1<<room.id()))==0){f.waves|=1<<room.id();spawnWave(level,r,room,Math.max(1,r.state().participants.getList(key(room.id()),8).size()),true);return;}
      clearRoom(level,r,room);
    }
    public static void killed(DungeonMob mob){var t=mob.getPersistentData();if(!t.hasUUID("DungeonRun"))return;var d=DungeonData.get(mob.getServer());var r=d.runs.get(t.getUUID("DungeonRun"));if(r==null||r.closed)return;var f=r.floors.get(t.getInt("DungeonFloor"));if(f==null)return;f.enemies.getCompound(key(t.getInt("DungeonRoom"))).remove(mob.getUUID().toString());var bar=BARS.remove(mob.getUUID());if(bar!=null)bar.removeAllPlayers();d.setDirty();}
    public static void clearRoom(ServerLevel level,DungeonData.Run r,DungeonGraph.Room room){var f=r.state();if(f.clear(room.id()))return;f.cleared|=1<<room.id();DungeonRealm.doors(level,r,room,false);var d=DungeonData.get(level.getServer());
      for(Tag v:f.participants.getList(key(room.id()),8)){UUID id=UUID.fromString(v.getAsString());var profile=d.profile(id);profile.putLong("Pending",Math.min(1_000_000,profile.getLong("Pending")+(room.kind()==DungeonGraph.Kind.GUARDIAN?20:3)));if(room.kind()==DungeonGraph.Kind.GUARDIAN){int next=Math.min(26,r.floor+1);profile.putInt("Unlock_"+r.theme.id,Math.max(Math.max(1,profile.getInt("Unlock_"+r.theme.id)),next));profile.putLong("Saved",Math.min(10_000_000,profile.getLong("Saved")+profile.getLong("Pending")));profile.putLong("Pending",0);if(r.floor==30)profile.putBoolean("Complete_"+r.theme.id,true);var p=level.getServer().getPlayerList().getPlayer(id);DungeonRewards.checkpoint(level.getServer(),id,r);if(p!=null){message(p,"守关完成！进度已保存，遗币已结算");}}else{var p=level.getServer().getPlayerList().getPlayer(id);if(p!=null&&profile.getInt("Blessing")==1){var mana=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(mana!=null)mana.addMana(5);}}}
      d.setDirty();
    }
    public static void fail(ServerPlayer cause){var r=run(cause);if(r==null||r.closed)return;r.closed=true;var d=DungeonData.get(cause.server);for(UUID id:r.members){var profile=d.profile(id);long pending=profile.getLong("Pending"),loss=DungeonData.failureLoss(pending);profile.putLong("Saved",profile.getLong("Saved")+pending-loss);profile.putLong("Pending",0);profile.remove("Run");profile.remove("Recovering");profile.remove("Blessing");var p=cause.server.getPlayerList().getPlayer(id);if(p!=null){if(DownedCombat.isDowned(p))DownedCombat.revive(p);com.tnc.tnc.journey.FamilyLife.notice(p,"death");p.setHealth(p.getMaxHealth());p.clearFire();p.removeAllEffects();p.invulnerableTime=120;leave(p);p.sendSystemMessage(Component.literal("本段失败 · 装备背包经验保留，损失遗币 "+loss+"；可从第"+r.segment+"层重新挑战"));}}discardRunMobs(cause.server,r);d.setDirty();}
    public static void abandon(ServerPlayer p){var r=run(p);if(r==null)return;if(!r.leader.equals(p.getUUID())){message(p,"由队长在入口面板确认重新开段");return;}fail(p);}
    public static boolean cacheEncounter(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room){if(!validateRoom(p,r,room))return true;var f=r.state();String key="mimic:"+room.id();if(!f.events.getBoolean(key)){if(Math.floorMod(r.seed+r.floor*29L+room.id()*11L,7)==0){if(startRoom(p.serverLevel(),r,room,online(p.server,r))){f.cleared&=~(1<<room.id());f.events.putBoolean(key,true);message(p,"宝匣睁开了眼睛！清场后才能领取奖励");}return true;}f.events.putBoolean(key,true);}return (f.started&(1<<room.id()))!=0&&!f.clear(room.id());}
    public static void rest(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room){if(!validateRoom(p,r,room))return;var claims=DungeonRewards.claimRecord(r.state(),room.id());String key="rest:"+p.getUUID();if(claims.getBoolean(key)){message(p,"你已在此完成整备");return;}claims.putBoolean(key,true);p.setHealth(p.getMaxHealth());p.getFoodData().eat(6,1);var magic=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(magic!=null)magic.addMana(40);DungeonData.get(p.server).setDirty();message(p,"整备完成 · 生命恢复，补充40个人法力");}
    public static void event(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room,int choice){if(!validateRoom(p,r,room))return;int kind=DungeonNetwork.event(r,room);var f=r.state();var claims=DungeonRewards.claimRecord(f,room.id());String key="event:"+p.getUUID();if(kind!=2&&claims.getBoolean(key)){message(p,"这次奇遇已经完成");return;}
      if(kind==2){if(claims.getBoolean(p.getUUID().toString())){message(p,"你已选择这份补给");return;}if(choice==1&&!f.events.getBoolean("Challenge"+room.id())){if(startRoom(p.serverLevel(),r,room,online(p.server,r))){f.events.putBoolean("Challenge"+room.id(),true);f.cleared&=~(1<<room.id());}return;}if((f.started&(1<<room.id()))!=0&&!f.clear(room.id())){message(p,"先完成唤醒的守卫挑战");return;}DungeonRewards.claim(p,r,room);}
      else if(kind==3){for(var candidate:r.graph().rooms)if((f.visited&(1<<candidate.id()))==0){f.events.putInt("Reveal",f.events.getInt("Reveal")|(1<<candidate.id()));claims.putBoolean(key,true);message(p,"测绘员在地图上点亮了一间未知房");break;}}
      else if(kind==4){claims.putBoolean(key,true);var next=room.links().stream().filter(i->(f.visited&(1<<i))==0).findFirst();p.sendSystemMessage(Component.literal(next.isPresent()?"旧门环：还有未走过的门。别老盯着我，我只负责把门看住。":"旧门环：这里的路你都走过了，回上一个岔口看看。"));}
      DungeonData.get(p.server).setDirty();
    }
    public static void leave(ServerPlayer p){com.tnc.tnc.journey.Preparation.clearReady(p);var d=DungeonData.get(p.server);var profile=d.profile(p.getUUID());var r=run(p);if(r!=null){var room=room(p);if((com.tnc.tnc.dungeon.leyline.LeylineService.sealedMask(r))!=0){message(p,"战斗中不能撤离，先清场或救起队友");return;}profile.putLong("ResumePos",p.blockPosition().asLong());}
      if(DownedCombat.isDowned(p)){message(p,"倒地时请等待救援");return;}RECOVERIES.remove(p.getUUID());profile.remove("Recovering");long exitToken=TELEPORTS.merge(p.getUUID(),1L,Long::sum);var expectedRun=r;var id=ResourceLocation.tryParse(profile.getString("ReturnDimension"));var target=id==null?p.server.overworld():p.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,id));if(target==null||DungeonRealm.inside(target))target=p.server.overworld();var safe=profile.contains("ReturnX")?BlockPos.containing(profile.getDouble("ReturnX"),profile.getDouble("ReturnY"),profile.getDouble("ReturnZ")):target.getSharedSpawnPos();var dest=target;var cp=new net.minecraft.world.level.ChunkPos(safe);
      dest.getChunkSource().getChunkFuture(cp.x,cp.z,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((c,e)->{if(e!=null||p.hasDisconnected()||!p.isAlive()||!Objects.equals(TELEPORTS.get(p.getUUID()),exitToken)||run(p)!=expectedRun)return;BlockPos landing=null;for(int y=0;y<8&&landing==null;y++)for(int dx=-3;dx<=3&&landing==null;dx++)for(int dz=-3;dz<=3;dz++){var pos=safe.offset(dx,y,dz);if(dest.getBlockState(pos).getCollisionShape(dest,pos).isEmpty()&&dest.getBlockState(pos.above()).getCollisionShape(dest,pos.above()).isEmpty()&&dest.getBlockState(pos.below()).isFaceSturdy(dest,pos.below(),Direction.UP)){landing=pos;break;}}if(landing==null)landing=dest.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,safe);p.fallDistance=0;p.getPersistentData().putLong("DungeonGateCooldown",dest.getGameTime()+100);p.teleportTo(dest,landing.getX()+.5,landing.getY(),landing.getZ()+.5,profile.getFloat("ReturnYaw"),0);},p.server);d.setDirty();
    }
    public static void invite(ServerPlayer p,ServerPlayer target){var r=run(p);if(r==null||r.floor!=0||!r.leader.equals(p.getUUID())||r.members.size()>=4||target==p||DownedCombat.isDowned(target))return;var d=DungeonData.get(p.server);var mine=d.profile(p.getUUID());var theirs=d.profile(target.getUUID());boolean nearby=target.level()==p.level()&&p.distanceToSqr(target)<=16*16;var other=run(target);boolean sameGate=other!=null&&other.floor==0&&other.theme==r.theme&&mine.getString("ReturnDimension").equals(theirs.getString("ReturnDimension"))&&new Vec3(mine.getDouble("ReturnX"),mine.getDouble("ReturnY"),mine.getDouble("ReturnZ")).distanceToSqr(new Vec3(theirs.getDouble("ReturnX"),theirs.getDouble("ReturnY"),theirs.getDouble("ReturnZ")))<32*32;boolean atGate=!DungeonRealm.inside(target.level())&&target.level().dimension().location().toString().equals(mine.getString("ReturnDimension"))&&target.position().distanceToSqr(new Vec3(mine.getDouble("ReturnX"),mine.getDouble("ReturnY"),mine.getDouble("ReturnZ")))<32*32;if(!nearby&&!sameGate&&!atGate){message(p,"请让队友来到同一座野外大门或对应入口大厅");return;}theirs.putUUID("Invite",r.id);theirs.putLong("InviteUntil",p.server.overworld().getGameTime()+1200);d.setDirty();target.sendSystemMessage(Component.literal(p.getName().getString()+"邀请你加入「"+r.theme.name+"」，输入 /dungeon accept 接受（60秒）"));}
    public static void accept(ServerPlayer p){var d=DungeonData.get(p.server);var t=d.profile(p.getUUID());if(!t.hasUUID("Invite")||t.getLong("InviteUntil")<p.server.overworld().getGameTime())return;var r=d.runs.get(t.getUUID("Invite"));if(r==null||r.closed||r.floor!=0||r.members.size()>=4||DownedCombat.isDowned(p))return;if(r.building){message(p,"入口大厅正在准备，请稍后接受");return;}var old=run(p);if(old!=null&&(old.floor>0||old.members.size()>1)){message(p,"先结束自己的挑战或队伍再入队");return;}if(old!=null){old.members.remove(p.getUUID());if(old.members.isEmpty())old.closed=true;}r.members.add(p.getUUID());t.putUUID("Run",r.id);t.remove("Invite");if(!DungeonRealm.inside(p.level())){t.putString("ReturnDimension",p.level().dimension().location().toString());t.putDouble("ReturnX",p.getX());t.putDouble("ReturnY",p.getY());t.putDouble("ReturnZ",p.getZ());}teleport(p,r,DungeonRealm.lobby(r).above());d.setDirty();}
    private static void discardRunMobs(MinecraftServer s,DungeonData.Run r){var level=s.getLevel(DungeonRealm.KEY);if(level==null)return;for(var floor:r.floors.values())for(String k:floor.enemies.getAllKeys())for(String id:floor.enemies.getCompound(k).getAllKeys())try{var e=level.getEntity(UUID.fromString(id));if(e!=null)e.discard();var bar=BARS.remove(UUID.fromString(id));if(bar!=null)bar.removeAllPlayers();}catch(IllegalArgumentException ignored){}}
    public static void stop(){RECOVERIES.clear();TELEPORTS.clear();MISSING.clear();BARS.values().forEach(net.minecraft.server.level.ServerBossEvent::removeAllPlayers);BARS.clear();DungeonRealm.stop();}
}
