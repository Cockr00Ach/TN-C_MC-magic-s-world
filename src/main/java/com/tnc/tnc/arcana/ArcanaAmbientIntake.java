package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.life.pasture.PastureAnimal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
/** High-tier storage samples the same finite plant/animal ledgers as the small collectors. */
public final class ArcanaAmbientIntake {
 public static int collect(ServerLevel l,RouteNodeEntity n){
  if(n.owner==null||!RouteAccess.allowed(l,n.getBlockPos(),n.owner)||n.kind()!=RouteKind.ABYSS_TOWER&&n.kind()!=RouteKind.SINGULARITY||n.kind()==RouteKind.ABYSS_TOWER&&!ArcanaDevices.formed(l,n.getBlockPos()))return 0;
  var t=n.getPersistentData();long stamp=l.getGameTime()/20;if(t.contains("AmbientSecond")&&t.getLong("AmbientSecond")==stamp)return t.getInt("AmbientRate");
  int remaining=Math.min(1024,n.kind().capacity-n.mana),total=0;t.putLong("AmbientSecond",stamp);
  for(var at:BlockPos.betweenClosed(n.getBlockPos().offset(-4,-1,-4),n.getBlockPos().offset(4,4,4))){if(remaining<=0)break;if(!l.hasChunkAt(at)||!RouteAccess.allowed(l,at,n.owner))continue;int got=RouteSources.draw(l,at,n.owner,remaining);if(got<=0)continue;int accepted=n.receive(got);ArcanaEnergy.credit(n,ArcanaEnergy.source(l.getBlockEntity(at)),accepted);total+=accepted;remaining-=accepted;RouteNetwork.trace(l,at,n.getBlockPos(),accepted);}
  if(remaining>0)for(var a:l.getEntitiesOfClass(PastureAnimal.class,new AABB(n.getBlockPos()).inflate(4),a->n.owner.equals(a.owner()))){if(remaining<=0)break;if(!RouteAccess.allowed(l,a.blockPosition(),n.owner))continue;int got=a.drawNetworkMana(n.owner,remaining,false);int accepted=n.receive(got);if(accepted>0){ArcanaEnergy.credit(n,ArcanaEnergy.animal(a),accepted);total+=accepted;remaining-=accepted;RouteNetwork.trace(l,a.blockPosition(),n.getBlockPos(),accepted);}}
  t.putInt("AmbientRate",total);n.setChanged();return total;
 }
 private ArcanaAmbientIntake(){}
}
