package com.tnc.tnc.warrior;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
public final class WarriorSwordUltimate {
 private static final Map<UUID,Set<Integer>> HIT=new HashMap<>();
 public static void clear(ServerPlayer p){HIT.remove(p.getUUID());}
 public static void tick(ServerPlayer p,int age){
  if(age==26||age==40||age==54){
   var targets=WarriorComboService.targets(p,12).stream().filter(t->age!=26||WarriorComboService.inArc(p,t,110)).sorted(Comparator.comparingDouble(p::distanceToSqr)).toList();
   Vec3 from=p.position(),direction=targets.isEmpty()?p.getLookAngle().multiply(1,0,1).normalize():targets.get(0).position().subtract(from).multiply(1,0,1).normalize();
   double length=targets.isEmpty()?4:Math.min(10,p.distanceTo(targets.get(0))+2);Vec3 to=from;
   for(double d=.25;d<=length;d+=.25){Vec3 next=from.add(direction.scale(d));
    if(!p.level().noCollision(p,p.getBoundingBox().move(next.subtract(from)))||!p.level().getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(next)))break;
    var floor=p.level().clip(new net.minecraft.world.level.ClipContext(next.add(0,.1,0),next.add(0,-1.5,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
    if(floor.getType()==net.minecraft.world.phys.HitResult.Type.MISS)break;to=next;
   }
   Vec3 delta=to.subtract(from);double len=delta.length();
   for(var target:targets){Vec3 rel=target.position().subtract(from);double along=Math.max(0,Math.min(len,rel.dot(direction)));
    if(target.position().distanceTo(from.add(direction.scale(along)))>1.6||!p.hasLineOfSight(target))continue;
    if(target.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*1.15f))HIT.computeIfAbsent(p.getUUID(),x->new HashSet<>()).add(target.getId());
   }
   p.teleportTo(to.x,to.y,to.z);WarriorSwordFx.send(p,from,to,0);
  }
  if(age==84){
   for(int id:HIT.getOrDefault(p.getUUID(),Set.of()))if(p.level().getEntity(id) instanceof LivingEntity target&&WarriorComboService.validTarget(p,target)&&p.distanceTo(target)<24&&p.hasLineOfSight(target)){
    WarriorSwordFx.send(p,target.position(),target.position(),1);
    if(target.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*2.6f)){target.push(0,.65,0);target.hurtMarked=true;}
   }
   clear(p);
  }
 }
}
