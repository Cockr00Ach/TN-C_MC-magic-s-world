package com.tnc.tnc.dungeon.leyline;
import com.tnc.tnc.dungeon.*;
import com.tnc.tnc.dungeon.ecology.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
/** The floor ledger owns all puzzles. Packets are read-only projections. */
public final class LeylineService {
 public static CompoundTag state(DungeonData.Run r,DungeonGraph.Room room){String key="Leyline"+room.id();var events=r.state().events;if(!events.contains(key,10))events.put(key,new CompoundTag());return events.getCompound(key);}
 public static List<CompoundTag> nodes(CompoundTag s,String role){var out=new ArrayList<CompoundTag>();for(Tag v:s.getList("Nodes",10)){var n=(CompoundTag)v;if(n.getString("Role").equals(role))out.add(n);}return out;}
 public static LeylineKind kind(DungeonData.Run r,DungeonGraph.Room room){
  var s=state(r,room);if(s.contains("Kind")){try{return LeylineKind.valueOf(s.getString("Kind"));}catch(IllegalArgumentException ignored){return LeylineKind.NONE;}}
  if(room.id()==0||room.kind()==DungeonGraph.Kind.EXIT||room.kind()==DungeonGraph.Kind.GUARDIAN||room.kind()==DungeonGraph.Kind.COMBAT&&room.id()%2==0)return LeylineKind.NONE;
  // A rotating seeded deck covers every mechanism; the first room teaches supply disruption.
  return LeylineDeck.choose(r.theme.id,r.seed,r.floor,room.id());
 }
 public static boolean puzzle(DungeonData.Run r,DungeonGraph.Room room){return kind(r,room)!=LeylineKind.NONE;}
 public static void markDirty(MinecraftServer s){DungeonData.get(s).setDirty();}
 public static void consumeHeld(ServerPlayer p,InteractionHand hand){if(!p.isCreative())p.getItemInHand(hand).shrink(1);}
 public static void build(List<DungeonRealm.Cell> cells,DungeonData.Run r,DungeonGraph.Room room){
  var s=state(r,room);var k=kind(r,room);s.putString("Kind",k.name());
  if(k!=LeylineKind.NONE){LeylineRooms.build(cells,r,room,k,s);return;}
  if(room.kind()!=DungeonGraph.Kind.COMBAT)return;
  var o=DungeonRealm.center(r,room);var ns=new ListTag();
  node(cells,ns,o.offset(8,1,7),"source",0,LeylineContent.SOURCE);
  node(cells,ns,o.offset(-8,1,6),"nest",0,LeylineContent.NEST);
  node(cells,ns,o.offset(7,1,-7),"nest",1,LeylineContent.NEST);
  s.put("Nodes",ns);s.putInt("Remaining",3);s.putLong("Origin",o.asLong());
 }
 private static void node(List<DungeonRealm.Cell> cells,ListTag ns,BlockPos at,String role,int index,net.minecraft.world.level.block.Block b){var n=new CompoundTag();n.putString("Role",role);n.putInt("Index",index);n.putLong("Pos",at.asLong());ns.add(n);cells.add(new DungeonRealm.Cell(at,b.defaultBlockState()));}
 public static EcologyMob spawnEcology(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,String kind,BlockPos at){var mob=EcologyContent.create(kind,l);if(mob==null)return null;mob.configure(r.floor,Math.max(1,r.members.size()),false);mob.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);if(l.addFreshEntity(mob))DungeonService.trackSpawn(l,r,room,mob);return mob;}
 public static void solve(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,ServerPlayer p){
  var s=state(r,room);if(s.getBoolean("Solved")||s.getBoolean("Failed"))return;s.putBoolean("Solved",true);DungeonService.clearRoom(l,r,room);
  for(var entry:r.state().participants.getList(Integer.toString(room.id()),8)) {
   try {LeylineRewards.award(l.getServer(),r,room,UUID.fromString(entry.getAsString()),!s.getBoolean("PartialTreasure"));}
   catch(IllegalArgumentException malformed){com.mojang.logging.LogUtils.getLogger().warn("Invalid dungeon participant {}",entry);}
  }markDirty(l.getServer());
 }
 /** Exploration rooms can be revisited; only combat seals the party inside. */
 public static boolean begin(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,List<ServerPlayer> party){
  if(!puzzle(r,room))return false;
  var eligible=party.stream().filter(p->!DungeonService.recovering(p)&&!com.tnc.tnc.combat.DownedCombat.isDowned(p)
      &&DungeonService.room(p)!=null&&DungeonService.room(p).id()==room.id()&&DungeonService.validateRoom(p,r,room)).toList();
  if(eligible.isEmpty())return true;
  var f=r.state();boolean first=(f.started&(1<<room.id()))==0;f.started|=1<<room.id();
  var list=f.participants.getList(Integer.toString(room.id()),8);
  boolean changed=first;
  for(var p:eligible)if(list.stream().noneMatch(t->t.getAsString().equals(p.getUUID().toString()))){list.add(StringTag.valueOf(p.getUUID().toString()));changed=true;}
  f.participants.put(Integer.toString(room.id()),list);
  if(first)DungeonRealm.doors(l,r,room,false);
  if(first)eligible.forEach(p->DungeonService.message(p,kind(r,room).title+" · "+kind(r,room).hint+" 可先离开，奖励需解开机关。"));
  if(changed)markDirty(l.getServer());return true;
 }
 public static int sealedMask(DungeonData.Run r){int mask=r.state().started&~r.state().cleared;for(var room:r.graph().rooms)if(puzzle(r,room))mask&=~(1<<room.id());return mask;}
 public static boolean traversable(DungeonData.Run r,DungeonGraph.Room room){return r.state().clear(room.id())||puzzle(r,room)&&(r.state().started&(1<<room.id()))!=0;}
 public static BlockPos landing(DungeonData.Run r,DungeonGraph.Room room){return DungeonRealm.center(r,room).offset(0,1,-9);}

 public static void tick(MinecraftServer server){
  var l=server.getLevel(DungeonRealm.KEY);if(l==null)return;
  for(var r:new ArrayList<>(DungeonData.get(server).runs.values())){if(r.closed||r.floor==0||r.building||!r.state().built)continue;var party=DungeonService.online(server,r);if(party.isEmpty())continue;
   for(var room:r.graph().rooms){var near=party.stream().filter(p->{var o=DungeonRealm.center(r,room);return Math.abs(p.getX()-o.getX())<11&&Math.abs(p.getZ()-o.getZ())<11&&!DungeonService.recovering(p);}).toList();if(near.isEmpty()){
     if(l.getGameTime()%10==0)for(var p:party)if(DungeonService.room(p)==null&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(DungeonRealm.center(r,room)))<23*23){
      var packet=new CompoundTag();packet.putString("Kind",kind(r,room).name());packet.putLong("Origin",DungeonRealm.center(r,room).asLong());packet.put("State",state(r,room).copy());LeylineNetwork.sync(p,packet);
     }continue;
    }
    var s=state(r,room);
    LeylineBroods.refresh(l,r,room);
    if(s.getInt("Poison")>0&&!s.getBoolean("SourceCut"))for(var mob:l.getEntitiesOfClass(EcologyMob.class,new net.minecraft.world.phys.AABB(DungeonRealm.center(r,room)).inflate(16)))
      if(mob.getPersistentData().hasUUID("DungeonRun")&&mob.getPersistentData().getUUID("DungeonRun").equals(r.id)&&mob.getPersistentData().getInt("DungeonFloor")==r.floor&&mob.getPersistentData().getInt("DungeonRoom")==room.id())mob.applySupply(s.getInt("Poison")==2?SupplyState.OVERLOAD:SupplyState.TAINTED);
    if(puzzle(r,room)){begin(l,r,room,near);if((r.state().started&(1<<room.id()))!=0)LeylinePuzzles.tick(l,r,room,near);
     if(s.getBoolean("Failed")&&!r.state().clear(room.id())){r.state().cleared|=1<<room.id();DungeonRealm.doors(l,r,room,false);markDirty(server);}
    }
    if(l.getGameTime()%10==0)for(var p:near){var packet=new CompoundTag();packet.putString("Kind",kind(r,room).name());packet.putLong("Origin",DungeonRealm.center(r,room).asLong());packet.put("State",s.copy());packet.putUUID("Run",r.id);packet.putInt("Floor",r.floor);packet.putInt("Room",room.id());LeylineNetwork.sync(p,packet);}
   }
  }
 }
 public static boolean cutSource(ServerPlayer p,DungeonData.Run r,DungeonGraph.Room room,BlockPos at){
  var s=state(r,room);if(nodes(s,"source").stream().noneMatch(n->n.getLong("Pos")==at.asLong()))return false;
  if(s.getBoolean("SourceCut"))return true;s.putBoolean("SourceCut",true);s.putInt("Remaining",0);
  DungeonRealm.write(()->p.serverLevel().setBlock(at,Blocks.CRYING_OBSIDIAN.defaultBlockState(),3));
  var o=DungeonRealm.center(r,room);for(var mob:p.serverLevel().getEntitiesOfClass(EcologyMob.class,new net.minecraft.world.phys.AABB(o).inflate(24)))if(mob.getPersistentData().hasUUID("DungeonRun")&&mob.getPersistentData().getUUID("DungeonRun").equals(r.id)&&mob.getPersistentData().getInt("DungeonRoom")==room.id()&&mob.getPersistentData().getInt("DungeonFloor")==r.floor)mob.applySupply(SupplyState.CUT);
  LeylineBroods.refresh(p.serverLevel(),r,room);
  DungeonService.message(p,"供魔灵石破碎 · 母巢断流，已生成魔物衰弱");markDirty(p.server);return true;
 }
}
