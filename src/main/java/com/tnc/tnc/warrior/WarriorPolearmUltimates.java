package com.tnc.tnc.warrior;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorPolearmUltimates {
 private static final Map<UUID,Sequence> CASTS=new HashMap<>();
 private static final class Sequence {final List<UUID> targets=new ArrayList<>();int index;UUID victim;Vec3 origin;float yaw;boolean landed;}
 public static void clear(ServerPlayer p){CASTS.remove(p.getUUID());}
 public static String kind(net.minecraft.world.item.ItemStack stack){
  var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());if(id==null)return "";String key=id.getPath();
  if(key.contains("scythe"))return "scythe";
  if(key.contains("halberd")||key.equals("fangtian_ji"))return "halberd";
  return key.contains("spear")?"spear":"";
 }
 public static boolean tick(ServerPlayer p,int age){
  String kind=kind(p.getMainHandItem());if(kind.isEmpty())return false;
  var c=CASTS.computeIfAbsent(p.getUUID(),id->{var s=new Sequence();s.origin=p.position();s.yaw=p.getYRot();return s;});
  p.setDeltaMovement(0,0,0);p.fallDistance=0;p.setYRot(c.yaw);p.yBodyRot=c.yaw;
  switch(kind){case "scythe"->scythe(p,c,age);case "spear"->spear(p,c,age);case "halberd"->halberd(p,c,age);default->{}}
  c.yaw=p.getYRot();return true;
 }
 private static void boom(ServerPlayer p,boolean impact){if(impact)WarriorUltimateSound.impact(p,p.position());else WarriorUltimateSound.heavySwing(p);}
 private static void damage(ServerPlayer p,LivingEntity t,float multiplier){if(WarriorComboService.validTarget(p,t)&&p.hasLineOfSight(t))t.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*multiplier);}
 private static void mark(ServerPlayer p,LivingEntity t,int ticks){t.getPersistentData().putLong("TncReaperUntil",p.level().getGameTime()+ticks);t.getPersistentData().putUUID("TncReaperOwner",p.getUUID());WarriorExecutionFx.send(p,t,0,ticks);}
 private static LivingEntity victim(ServerPlayer p,Sequence c){return c.victim!=null&&p.serverLevel().getEntity(c.victim) instanceof LivingEntity t&&WarriorComboService.validTarget(p,t)&&p.distanceTo(t)<32&&p.hasLineOfSight(t)?t:null;}
 private static boolean behind(ServerPlayer p,LivingEntity t,boolean airborne){
  Vec3 f=t.getLookAngle().multiply(1,0,1).normalize();if(f.lengthSqr()<.1)f=new Vec3(0,0,1);
  Vec3 end=t.position().subtract(f.scale(1.5));Vec3 delta=end.subtract(p.position());
  var destination=p.getBoundingBox().move(delta);
  if(!p.level().getWorldBorder().isWithinBounds(destination)||p.level().getBlockCollisions(p,destination).iterator().hasNext())return false;
  for(double u=0;u<=1;u+=.2/Math.max(.2,delta.length()))if(p.level().getBlockCollisions(p,p.getBoundingBox().move(delta.scale(u))).iterator().hasNext())return false;
  if(!airborne&&p.level().clip(new ClipContext(end.add(0,.1,0),end.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)return false;
  Vec3 from=p.position();p.teleportTo(end.x,end.y,end.z);Vec3 aim=t.getBoundingBox().getCenter().subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));p.yBodyRot=p.getYRot();WarriorSwordFx.send(p,from,end,0);return true;
 }
 private static void scythe(ServerPlayer p,Sequence c,int age){
  if(age==22)WarriorUltimateSound.anticipate(p,.8f);
  if(age==26||age==34||age==42){boom(p,false);WarriorSwordFx.send(p,p.position(),p.position(),3);for(var t:WarriorComboService.targets(p,9)){damage(p,t,1.8f);if(!c.targets.contains(t.getUUID()))c.targets.add(t.getUUID());if(c.victim==null)c.victim=t.getUUID();}}
  var t=victim(p,c);
  if(t==null&&age==48){
   t=c.targets.stream().map(p.serverLevel()::getEntity).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).filter(next->WarriorComboService.validTarget(p,next)&&p.distanceTo(next)<32&&p.hasLineOfSight(next)).min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
   if(t!=null)c.victim=t.getUUID();
  }
  if(t==null)return;
  if(t.getBbHeight()<4){
   if(age==48&&(c.landed=behind(p,t,true))){damage(p,t,3);WarriorExecutionFx.send(p,t,1,28);WarriorUltimateSound.hook(p);}
   if(age>=49&&age<=72&&c.landed){t.setDeltaMovement(0,.09,0);t.hurtMarked=true;if(age==54)mark(p,t,240);}
   if(age==74&&c.landed){damage(p,t,12);mark(p,t,240);WarriorUltimateSound.execution(p,t.position());WarriorSwordFx.send(p,t.position(),t.position(),1);}
  }else if(age==48){WarriorExecutionFx.send(p,t,2,48);WarriorUltimateSound.anticipate(p,.25f);}
  else if(age==56||age==64)WarriorUltimateSound.anticipate(p,(age-48)/20f);
  else if(age==72){damage(p,t,16);mark(p,t,300);WarriorUltimateSound.execution(p,t.position());WarriorSwordFx.send(p,t.position(),t.position(),1);}
 }
 private static void spear(ServerPlayer p,Sequence c,int age){
  if(age==22)WarriorUltimateSound.anticipate(p,1);
  if(age==25)for(var t:WarriorComboService.targets(p,20))c.targets.add(t.getUUID());
  if(age>=26&&age<=65){int batch=Math.max(1,(c.targets.size()+39)/40);for(int i=0;i<batch&&c.index<c.targets.size();i++){
   var e=p.serverLevel().getEntity(c.targets.get(c.index++));if(e instanceof LivingEntity t&&WarriorComboService.validTarget(p,t)&&p.distanceTo(t)<48&&p.hasLineOfSight(t)&&behind(p,t,true)){damage(p,t,2.8f);WarriorUltimateSound.rush(p,true);}
  }}
  if(age==76){returnToOrigin(p,c);boom(p,true);WarriorSwordFx.send(p,p.position(),p.position(),3);for(var t:WarriorComboService.targets(p,20)){damage(p,t,5);if(t.getBbHeight()<4){Vec3 d=t.position().subtract(p.position()).normalize();t.push(d.x*2,.45,d.z*2);t.hurtMarked=true;}}}
 }
 private static void returnToOrigin(ServerPlayer p,Sequence c){
  Vec3 from=p.position(),delta=c.origin.subtract(from);
  if(!p.level().getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(c.origin)))return;
  for(double d=0;d<=delta.length()+.2;d+=.2){Vec3 step=delta.normalize().scale(Math.min(d,delta.length()));if(p.level().getBlockCollisions(p,p.getBoundingBox().move(step)).iterator().hasNext())return;}
  p.teleportTo(c.origin.x,c.origin.y,c.origin.z);WarriorSwordFx.send(p,from,c.origin,0);
 }
 private static void halberd(ServerPlayer p,Sequence c,int age){
  if(age==24||age==36||age==44||age==80)WarriorUltimateSound.anticipate(p,age==80?1:Math.min(1,(age-24)/20f));
  if(age==46)WarriorUltimateSound.dive(p);
  if(age>=24&&age<=36){Vec3 next=p.position().add(0,.38,0);if(!p.level().getBlockCollisions(p,p.getBoundingBox().move(0,.38,0)).iterator().hasNext())p.teleportTo(next.x,next.y,next.z);}
  if(age>=46&&age<62&&!c.landed){Vec3 start=p.position(),end=start.add(0,-1.7,0);var hit=p.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
   if(hit.getType()==HitResult.Type.BLOCK){p.teleportTo(start.x,hit.getLocation().y+.02,start.z);land(p,c);}else if(!p.level().getBlockCollisions(p,p.getBoundingBox().move(0,-1.7,0)).iterator().hasNext())p.teleportTo(end.x,end.y,end.z);
  }
  if(age==62&&!c.landed){if(p.onGround())land(p,c);else return;}
  if(!c.landed)return;
  if(age==66||age==74){boom(p,false);WarriorSwordFx.send(p,p.position(),p.position(),3);for(var t:WarriorComboService.targets(p,10))damage(p,t,3);}
  if(age==86){var targets=WarriorComboService.targets(p,12);if(!targets.isEmpty()){var t=targets.stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElseThrow();float m=t.getBbHeight()<=2?Math.max(20,t.getMaxHealth()*10/(float)Math.max(1,p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))):18;damage(p,t,m);WarriorSwordFx.send(p,t.position(),t.position(),1);WarriorUltimateSound.execution(p,t.position());}}
 }
 private static void land(ServerPlayer p,Sequence c){c.landed=true;boom(p,true);WarriorSwordFx.send(p,p.position(),p.position(),2);WarriorSwordFx.send(p,p.position(),p.position(),5);for(var t:WarriorComboService.targets(p,10))damage(p,t,5);}
 @SubscribeEvent public static void dot(net.minecraftforge.event.TickEvent.LevelTickEvent e){
  if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||e.level.isClientSide||e.level.getGameTime()%20!=0)return;
  for(var entity:((net.minecraft.server.level.ServerLevel)e.level).getAllEntities())if(entity instanceof LivingEntity t){var tag=t.getPersistentData();if(!tag.contains("TncReaperUntil"))continue;
   if(tag.getLong("TncReaperUntil")<=e.level.getGameTime()){tag.remove("TncReaperUntil");tag.remove("TncReaperOwner");continue;}
   if(tag.hasUUID("TncReaperOwner")&&((net.minecraft.server.level.ServerLevel)e.level).getEntity(tag.getUUID("TncReaperOwner")) instanceof ServerPlayer p&&WarriorComboService.validTarget(p,t))t.hurt(p.damageSources().playerAttack(p),Math.min(40,Math.max(2,t.getMaxHealth()*.015f)));
  }
 }
 @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){CASTS.clear();}
}
