package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.dungeon.*;
import com.tnc.tnc.dungeon.ecology.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Nests combine realm-native fighters with the four F8 ecologies instead of replacing the bestiary. */
public final class LeylineBroods {
 private static final UUID SPEED=UUID.fromString("bac781e7-1b00-467c-b916-44d12cd06001"), DAMAGE=UUID.fromString("bac781e7-1b00-467c-b916-44d12cd06002");
 public static void spawn(ServerLevel level,DungeonData.Run run,DungeonGraph.Room room,int party,boolean second){
  var state=LeylineService.state(run,room);if(state.getBoolean("SourceCut"))return;
  var nests=LeylineService.nodes(state,"nest");var sources=LeylineService.nodes(state,"source");if(nests.isEmpty()||sources.isEmpty())return;
  var source=BlockPos.of(sources.get(0).getLong("Pos"));var random=new Random(run.seed+run.floor*787L+room.id()*43+(second?991:0));
  var pool=MobCatalog.pool(run.theme);String[] ecology=switch(run.theme){case DESERT->new String[]{"crab","flash","worm"};case CRYPT->new String[]{"flash","siphon","crab"};case TIDE->new String[]{"siphon","worm","crab"};case ASTRAL->new String[]{"flash","siphon","worm"};};
  int count=3+Math.min(2,Math.max(0,party-1));
  for(int i=0;i<count;i++){
   var at=BlockPos.of(nests.get(i%nests.size()).getLong("Pos")).offset(i/2,0,-1);
   DungeonMob mob;
   if(i==count-1){mob=EcologyContent.create(ecology[random.nextInt(ecology.length)],level);}
   else{var species=pool.get(random.nextInt(pool.size()));mob=DungeonContent.MOBS.get(species.id()).create(level);}
   if(mob==null)continue;mob.configure(run.floor,party,false);mob.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
   mob.getPersistentData().putLong("LeylineSupply",source.asLong());if(mob instanceof EcologyMob e)e.bindSupply(source);
   if(level.addFreshEntity(mob)){DungeonService.trackSpawn(level,run,room,mob);apply(level,mob,state.getInt("Poison"),false);}
  }
 }
 public static void refresh(ServerLevel level,DungeonData.Run run,DungeonGraph.Room room){
  var state=LeylineService.state(run,room);
  for(var mob:level.getEntitiesOfClass(DungeonMob.class,new AABB(DungeonRealm.center(run,room)).inflate(18))){
   var t=mob.getPersistentData();if(!t.contains("LeylineSupply")||!t.hasUUID("DungeonRun")||!t.getUUID("DungeonRun").equals(run.id)||t.getInt("DungeonFloor")!=run.floor||t.getInt("DungeonRoom")!=room.id())continue;
   apply(level,mob,state.getInt("Poison"),state.getBoolean("SourceCut"));
  }
 }
 private static void apply(ServerLevel level,DungeonMob mob,int poison,boolean cut){
  if(mob instanceof EcologyMob e){e.applySupply(cut?SupplyState.CUT:poison==2?SupplyState.OVERLOAD:poison==1?SupplyState.TAINTED:SupplyState.NORMAL);return;}
  var t=mob.getPersistentData();
  if(cut){modifier(mob,Attributes.MOVEMENT_SPEED,SPEED);modifier(mob,Attributes.ATTACK_DAMAGE,DAMAGE);t.remove("BroodBurstAt");}
  else if(poison==1&&!t.getBoolean("BroodTainted")){t.putBoolean("BroodTainted",true);mob.setHealth(Math.min(mob.getHealth(),mob.getMaxHealth()*.1f));mob.getAttribute(Attributes.ARMOR).setBaseValue(0);}
  else if(poison==2){if(!t.contains("BroodBurstAt"))t.putLong("BroodBurstAt",level.getGameTime()+30);if(level.getGameTime()>=t.getLong("BroodBurstAt")){DungeonService.killed(mob);level.explode(mob,mob.getX(),mob.getY()+.3,mob.getZ(),2,net.minecraft.world.level.Level.ExplosionInteraction.NONE);mob.discard();}}
 }
 private static void modifier(DungeonMob mob,Attribute attribute,UUID id){var a=mob.getAttribute(attribute);if(a!=null&&a.getModifier(id)==null)a.addTransientModifier(new AttributeModifier(id,"Severed brood supply",-.5,AttributeModifier.Operation.MULTIPLY_TOTAL));}
 private LeylineBroods(){}
}
