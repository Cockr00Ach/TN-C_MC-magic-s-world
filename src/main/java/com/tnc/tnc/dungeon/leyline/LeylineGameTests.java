package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.dungeon.*;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("tncdungeon") @PrefixGameTestTemplate(false)
public final class LeylineGameTests {
 private static DungeonData.Run run(){var r=new DungeonData.Run(UUID.randomUUID(),UUID.randomUUID(),4734,908,DungeonTheme.ASTRAL);r.floor=12;return r;}
 @GameTest(template="building_test_empty",timeoutTicks=60,batch="leyline")
 public static void bridgeCannotBeBypassedAlongEitherWallAndItsSpansRemainConnected(GameTestHelper h){
  var r=run();var room=r.graph().rooms.get(1);var origin=DungeonRealm.center(r,room);
  for(var kind:List.of(LeylineKind.BRIDGE,LeylineKind.DUAL_BRIDGE)){
   var state=new CompoundTag();var cells=new ArrayList<DungeonRealm.Cell>();
   // Include the inherited combat-room floor: the old regression hid in that perimeter.
   for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)cells.add(new DungeonRealm.Cell(origin.offset(x,0,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
   LeylineRooms.build(cells,r,room,kind,state);var map=new HashMap<BlockPos,BlockState>();cells.forEach(c->map.put(c.pos(),c.state()));
   h.assertTrue(!walksAcross(map,origin,false),kind+" has no ordinary floor bypass");
   h.assertTrue(walksAcross(map,origin,true),kind+" has a continuous intended span");
   h.assertTrue(LeylineService.nodes(state,"return").size()==2,"side entrances have recovery nodes");
  }h.succeed();
 }
 private static boolean walksAcross(Map<BlockPos,BlockState> map,BlockPos origin,boolean allowSpan){
  var visited=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(origin.offset(0,0,-9));
  while(!queue.isEmpty()){
   var p=queue.remove();if(!visited.add(p))continue;
   if(Math.abs(p.getX()-origin.getX())>10||Math.abs(p.getZ()-origin.getZ())>10)continue;
   var floor=map.get(p);if(floor==null||floor.isAir())continue;
   if(!allowSpan&&(floor.is(LeylineContent.VEIL)||floor.is(LeylineContent.PHASE_MATERIAL)||floor.is(LeylineContent.PHASE_AETHER)))continue;
   if(!map.getOrDefault(p.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()).isAir())continue;
   if(p.equals(origin.offset(0,0,8)))return true;
   for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL)queue.add(p.relative(direction));
  }return false;
 }
 @GameTest(template="building_test_empty",timeoutTicks=60,batch="leyline")
 public static void everyRoomBuildsWithNodesSafeLandingAndRealRewards(GameTestHelper h){
  var r=run();var room=r.graph().rooms.get(1);var origin=DungeonRealm.center(r,room);
  for(var kind:LeylineKind.values()){
   if(kind==LeylineKind.NONE)continue;var s=LeylineService.state(r,room);s.putString("Kind",kind.name());
   var cells=new ArrayList<DungeonRealm.Cell>();LeylineRooms.build(cells,r,room,kind,s);
   var map=new HashMap<BlockPos,BlockState>();cells.forEach(c->map.put(c.pos(),c.state()));
   h.assertTrue(!s.getList("Nodes",Tag.TAG_COMPOUND).isEmpty(),kind+" has real interactable positions");
   var safe=LeylineService.landing(r,room);
   h.assertTrue(map.get(safe).isAir()&&map.get(safe.above()).isAir()&&!map.get(safe.below()).isAir(),kind+" has a supported unobstructed recovery vestibule");
   for(var reward:LeylineRewards.loot(r,kind,true))h.assertTrue(!reward.isEmpty(),kind+" actual reward item is registered");
   h.assertTrue(cells.stream().allMatch(c->Math.abs(c.pos().getX()-origin.getX())<=11&&Math.abs(c.pos().getZ()-origin.getZ())<=11),kind+" stays inside authored footprint");
   for(var node:LeylineService.nodes(s,"dial_door"))h.assertTrue(!map.get(BlockPos.of(node.getLong("Pos"))).isAir(),"every dial door retains a reachable solid interaction target");
   for(var node:LeylineService.nodes(s,"barrier_core"))h.assertTrue(map.get(BlockPos.of(node.getLong("Pos"))).is(LeylineContent.NODE),"the arena power core is never erased by the entry vestibule");
  }h.succeed();
 }
 @GameTest(template="building_test_empty",timeoutTicks=40,batch="leyline")
 public static void unopenedPuzzlesCannotBeTraversedButVisitedPuzzlesDoNotSealTheTeam(GameTestHelper h){
  var r=run();var room=r.graph().rooms.get(1);LeylineService.state(r,room).putString("Kind","ECHO");
  h.assertTrue(!LeylineService.traversable(r,room),"unvisited room is not a teleport bypass");
  r.state().started=1<<room.id();h.assertTrue(LeylineService.traversable(r,room)&&LeylineService.sealedMask(r)==0,"missing echo potion never hardlocks party progression");
  h.assertTrue(!r.state().clear(room.id())&&!LeylineService.state(r,room).getBoolean("Solved"),"traversal does not grant puzzle completion or rewards");
  r.state().started|=1;h.assertTrue(LeylineService.sealedMask(r)==1,"ordinary combat remains sealed");h.succeed();
 }
 @GameTest(template="building_test_empty",timeoutTicks=40,batch="leyline")
 public static void puzzleMailSurvivesFullInventoryReloadAndDuplicateCompletion(GameTestHelper h){
  var p=AdventureGameTests.player(h);var r=run();var room=r.graph().rooms.get(1);LeylineService.state(r,room).putString("Kind","VINES");
  for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
  LeylineRewards.award(p.server,r,room,p.getUUID(),false);LeylineRewards.award(p.server,r,room,p.getUUID(),false);
  var data=DungeonData.get(p.server);var profile=data.profile(p.getUUID());int count=profile.getList("LeylineMail",10).size();
  h.assertTrue(count==2,"one material stack and one actual seed entitlement, never duplicated");
  LeylineRewards.deliver(p);h.assertTrue(profile.getList("LeylineMail",10).size()==count,"full bag preserves mail");
  h.assertTrue(DungeonData.load(data.save(new CompoundTag())).profile(p.getUUID()).getList("LeylineMail",10).size()==count,"mail survives server save/reload");
  p.getInventory().clearContent();LeylineRewards.deliver(p);LeylineRewards.deliver(p);
  h.assertTrue(profile.getList("LeylineMail",10).isEmpty()&&p.getInventory().countItem(com.tnc.tnc.arcana.ArcanaContent.item("shadow_hour_orchid_seed"))==1,"exactly one seed delivered");h.succeed();
 }
 @GameTest(template="building_test_empty",timeoutTicks=40,batch="leyline")
 public static void phaseBridgeCollisionDependsOnEachPlayersOwnSight(GameTestHelper h){
  var a=AdventureGameTests.player(h);var b=AdventureGameTests.player(h);var pos=h.absolutePos(new BlockPos(2,1,2));
  var stone=LeylineContent.PHASE_MATERIAL.defaultBlockState();var light=LeylineContent.PHASE_AETHER.defaultBlockState();
  com.tnc.tnc.mana.sight.ManaSightServer.subscribe(a,true);
  try{
   h.assertTrue(stone.getCollisionShape(h.getLevel(),pos,CollisionContext.of(a)).isEmpty()&&!light.getCollisionShape(h.getLevel(),pos,CollisionContext.of(a)).isEmpty(),"F8 player walks the aether span");
   h.assertTrue(!stone.getCollisionShape(h.getLevel(),pos,CollisionContext.of(b)).isEmpty()&&light.getCollisionShape(h.getLevel(),pos,CollisionContext.of(b)).isEmpty(),"ordinary teammate retains stone span");
  }finally{com.tnc.tnc.mana.sight.ManaSightServer.subscribe(a,false);}h.succeed();
 }
 @GameTest(template="building_test_empty",timeoutTicks=60,batch="leyline")
 public static void realCrystalHitsCompleteTheSeededSequenceAndPayOnlyOnce(GameTestHelper h){
  var p=AdventureGameTests.player(h);var r=run();r.slot=930;r.members.add(p.getUUID());
  var room=r.graph().rooms.get(1);var s=LeylineService.state(r,room);s.putString("Kind","MUSIC");
  LeylineRooms.build(new ArrayList<>(),r,room,LeylineKind.MUSIC,s);
  var l=p.server.getLevel(DungeonRealm.KEY);var o=DungeonRealm.center(r,room);l.getChunkAt(o);
  p.teleportTo(l,o.getX()+.5,o.getY()+1,o.getZ()+.5,0,0);
  var members=new ListTag();members.add(StringTag.valueOf(p.getUUID().toString()));r.state().participants.put(Integer.toString(room.id()),members);
  LeylinePuzzles.tick(l,r,room,List.of());
  for(int note:s.getIntArray("Notes")){
   var n=LeylineService.nodes(s,"crystal").stream().filter(t->t.getInt("Index")==note).findFirst().orElseThrow();
   var at=BlockPos.of(n.getLong("Pos"));p.setPos(at.getX()+.5,at.getY()+1,at.getZ()+.5);s.remove("NoteAt");
   h.assertTrue(LeylinePuzzles.breakBlock(p,r,room,at),"real hit is accepted");
  }
  h.assertTrue(s.getBoolean("Solved")&&r.state().clear(room.id()),"three server-authoritative notes open the room");
  var profile=DungeonData.get(p.server).profile(p.getUUID());int mailed=profile.getList("LeylineMail",10).size();
  LeylineService.solve(l,r,room,p);h.assertTrue(profile.getList("LeylineMail",10).size()==mailed,"repeat solve does not duplicate reward");r.closed=true;h.succeed();
 }
 @GameTest(template="building_test_empty",timeoutTicks=60,batch="leyline")
 public static void cuttingTheRealSourceWeakensOnlyItsOwnLivingBroodAndPreventsRespawn(GameTestHelper h){
  var p=AdventureGameTests.player(h);var r=run();r.slot=0;r.floor=1;r.members.add(p.getUUID());
  // Exercise the power network in the GameTest region, whose entity sections are already tracked.
  // The fake player is not a network-connected chunk loader in a second dimension.
  var anchor=h.absolutePos(new BlockPos(2,1,2));
  var room=new DungeonGraph.Room(0,new DungeonGraph.Cell(Math.round(anchor.getX()/40f),Math.round(anchor.getZ()/40f)),DungeonGraph.Kind.COMBAT,0,List.of());
  var state=LeylineService.state(r,room);state.putString("Kind","NONE");LeylineService.build(new ArrayList<>(),r,room);
  var l=h.getLevel();var o=DungeonRealm.center(r,room);l.getChunkAt(o);p.setPos(o.getX()+.5,o.getY()+1,o.getZ()+.5);
  LeylineBroods.spawn(l,r,room,1,false);
  var bounds=new net.minecraft.world.phys.AABB(o).inflate(18);
  java.util.function.Predicate<DungeonMob> own=m->m.getPersistentData().hasUUID("DungeonRun")&&m.getPersistentData().getUUID("DungeonRun").equals(r.id);
  var alive=l.getEntitiesOfClass(DungeonMob.class,bounds,own);
  h.assertTrue(alive.size()==3,"two fighters and an ecological creature are real entities; count="+alive.size());
  var speeds=new HashMap<UUID,Double>();alive.forEach(m->speeds.put(m.getUUID(),m.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
  var source=BlockPos.of(LeylineService.nodes(state,"source").get(0).getLong("Pos"));
  h.assertTrue(LeylineService.cutSource(p,r,room,source),"source can be severed");LeylineBroods.refresh(l,r,room);
  for(var mob:alive)h.assertTrue(Math.abs(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-speeds.get(mob.getUUID())*.5)<.001,"power cut really halves movement once");
  LeylineBroods.spawn(l,r,room,1,true);h.assertTrue(l.getEntitiesOfClass(DungeonMob.class,bounds,own).size()==3,"cut nest cannot spawn a second wave");alive.forEach(net.minecraft.world.entity.Entity::discard);r.closed=true;h.succeed();
 }

 private LeylineGameTests(){}
}
