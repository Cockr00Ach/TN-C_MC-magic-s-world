package com.tnc.tnc.warrior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc")
public final class WarriorSpecialUltimates {
 public static boolean elemental(net.minecraft.world.entity.player.Player p){
  var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
  return WarriorDomainLink.activeElementId(p)!=null||WarriorArrowEntity.bowStyle(p.getMainHandItem())!=0||id!=null&&id.toString().equals("tnc:storm_bow");
 }
 public static void heavy(ServerPlayer p,int age){
  if(age==22||age==34||age==46)WarriorUltimateSound.anticipate(p,(age-22)/24f);
  if(age==50)WarriorUltimateSound.heavySwing(p);
  if(age!=54&&age!=64&&age!=74&&age!=84)return;
  boolean slam=age==54;
  if(slam)WarriorUltimateSound.impact(p,p.position());else WarriorUltimateSound.heavySwing(p);
  WarriorSwordFx.send(p,p.position(),p.position(),slam?2:3);

  for(var t:WarriorComboService.targets(p,slam?8:10)){
   t.hurt(p.damageSources().playerAttack(p),(float)p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*(slam?5:2));
   if(slam&&t.getBbHeight()<4){t.push(0,1.8,0);t.hurtMarked=true;}
  }
  if(slam&&!PracticeArena.inside(p))for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
   if(x*x+z*z>9)continue;var pos=p.blockPosition().offset(x,-1,z);var state=p.level().getBlockState(pos);
   if(!state.isAir()&&state.getDestroySpeed(p.level(),pos)>=0&&state.getFluidState().isEmpty()&&p.level().getBlockEntity(pos)==null&&p.mayInteract(p.level(),pos)&&net.minecraftforge.event.ForgeEventFactory.onEntityDestroyBlock(p,pos,state))p.level().destroyBlock(pos,false,p);
  }
 }
 public static void bow(ServerPlayer p,int age){
  if(age<26){if(age%5==0)WarriorSwordFx.send(p,p.position(),p.position(),4);if(age==4||age==14||age==24)WarriorUltimateSound.anticipate(p,age/24f);return;}
  if(age==26)WarriorUltimateSound.bowBurst(p);
  if(age<=83&&(age-26)%3==0){arrow(p,((age-26)/3%5-2)*12);if(age==83){p.getPersistentData().putLong("TncSeekingVolleyUntil",p.level().getGameTime()+200);WarriorUltimateSound.volleyReady(p);WarriorExecutionFx.send(p,p,3,200);}}
 }
 private static void arrow(ServerPlayer p,float yaw){
  var a=WarriorArrowEntity.create(p.level(),p,net.minecraft.world.item.ItemStack.EMPTY,p.getMainHandItem());
  a.shootFromRotation(p,p.getXRot()-8,p.getYRot()+yaw,0,1.8f,0);a.setBaseDamage(4);a.setNoGravity(true);a.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;markSeeking(a,p);p.level().addFreshEntity(a);WarriorUltimateSound.volley(p);
 }
 @SubscribeEvent public static void spawn(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
  if(e.getLevel().isClientSide||!(e.getEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow a)||a.getPersistentData().getBoolean("TncSeekingUltimate")||!(a.getOwner() instanceof ServerPlayer p)||p.getPersistentData().getLong("TncSeekingVolleyUntil")<=p.level().getGameTime()||WarriorWeapons.family(p.getMainHandItem())!=WarriorWeaponFamily.BOW)return;
  markSeeking(a,p);a.setNoGravity(true);arrow(p,-12);arrow(p,12);
 }
 private static final String SEEKING="TncSeekingUltimate", TARGET="TncSeekingTarget", EXPIRES="TncSeekingExpiresAt",
         NEXT_SCAN="TncSeekingNextScan", SCAN_OFFSET="TncSeekingScanOffset";
 private static final int MAX_LIFE=100, REACQUIRE_TICKS=4, ACQUIRE_RAYS=8;
 private record OwnerTargets(java.util.List<net.minecraft.world.entity.LivingEntity> candidates,java.util.Set<java.util.UUID> allowed){}
 private static void markSeeking(net.minecraft.world.entity.projectile.AbstractArrow arrow,ServerPlayer owner){
  var tag=arrow.getPersistentData();tag.putBoolean(SEEKING,true);tag.putLong(EXPIRES,owner.level().getGameTime()+MAX_LIFE);
  tag.putLong(NEXT_SCAN,owner.level().getGameTime());tag.remove(TARGET);tag.remove(SCAN_OFFSET);
 }
 private static boolean visible(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.projectile.AbstractArrow arrow,net.minecraft.world.entity.LivingEntity target){
  return level.clip(new net.minecraft.world.level.ClipContext(arrow.position(),target.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,arrow)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
 }
 /** Vanilla in-ground arrows can retain velocity and never set onGround(). Track actual travel instead. */
 private static boolean stopped(net.minecraft.world.entity.projectile.AbstractArrow arrow,long now){
  var tag=arrow.getPersistentData();int still=tag.getInt("TncSeekingStill");
  if(tag.contains("TncSeekingObservedAt")&&now>tag.getLong("TncSeekingObservedAt")){
   Vec3 previous=new Vec3(tag.getDouble("TncSeekingLastX"),tag.getDouble("TncSeekingLastY"),tag.getDouble("TncSeekingLastZ"));
   still=previous.distanceToSqr(arrow.position())<.0001?still+1:0;
   tag.putInt("TncSeekingStill",still);
  }
  tag.putLong("TncSeekingObservedAt",now);tag.putDouble("TncSeekingLastX",arrow.getX());tag.putDouble("TncSeekingLastY",arrow.getY());tag.putDouble("TncSeekingLastZ",arrow.getZ());
  return arrow.tickCount>2&&still>=2;
 }
 private static OwnerTargets candidates(ServerPlayer owner){
  var candidates=WarriorComboService.targets(owner,32);var allowed=new java.util.HashSet<java.util.UUID>();
  for(var candidate:candidates)allowed.add(candidate.getUUID());return new OwnerTargets(candidates,allowed);
 }
 private static boolean valid(ServerPlayer owner,net.minecraft.world.entity.LivingEntity target,OwnerTargets choices){
  return target!=null&&target.isAlive()&&choices.allowed().contains(target.getUUID())&&owner.distanceToSqr(target)<=32*32&&WarriorComboService.validTarget(owner,target);
 }
 private static net.minecraft.world.entity.LivingEntity acquire(net.minecraft.server.level.ServerLevel level,ServerPlayer owner,net.minecraft.world.entity.projectile.AbstractArrow arrow,OwnerTargets choices){
  var tag=arrow.getPersistentData();long now=level.getGameTime();if(now<tag.getLong(NEXT_SCAN))return null;
  tag.putLong(NEXT_SCAN,now+REACQUIRE_TICKS);
  var nearest=choices.candidates().stream().filter(t->valid(owner,t,choices)).sorted(java.util.Comparator.comparingDouble(arrow::distanceToSqr)).toList();
  if(nearest.isEmpty()){tag.putInt(SCAN_OFFSET,0);return null;}
  int start=Math.floorMod(tag.getInt(SCAN_OFFSET),nearest.size()),count=Math.min(ACQUIRE_RAYS,nearest.size());
  for(int i=0;i<count;i++){
   var target=nearest.get((start+i)%nearest.size());
   if(visible(level,arrow,target)){tag.putUUID(TARGET,target.getUUID());tag.putInt(SCAN_OFFSET,0);return target;}
  }
  // A wall must not starve visible targets further down the list forever.
  tag.putInt(SCAN_OFFSET,(start+count)%nearest.size());return null;
 }
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent e){
  if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||e.level.isClientSide)return;
  var level=(net.minecraft.server.level.ServerLevel)e.level;long now=level.getGameTime();
  // Tick-local ownership prevents stale entity references across unloads, logouts or dimensions.
  var ownerTargets=new java.util.HashMap<java.util.UUID,OwnerTargets>();
  for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow&&arrow.getPersistentData().getBoolean(SEEKING)){
   var tag=arrow.getPersistentData();
   if(!tag.contains(EXPIRES))tag.putLong(EXPIRES,now+Math.max(0,MAX_LIFE-arrow.tickCount));
   if(arrow.tickCount>=MAX_LIFE||now>=tag.getLong(EXPIRES)||arrow.onGround()||stopped(arrow,now)
           ||!(arrow.getOwner() instanceof ServerPlayer owner)||!owner.isAlive()||owner.level()!=level){arrow.discard();continue;}
   var choices=ownerTargets.computeIfAbsent(owner.getUUID(),id->candidates(owner));
   net.minecraft.world.entity.LivingEntity target=null;
   if(tag.hasUUID(TARGET)&&level.getEntity(tag.getUUID(TARGET)) instanceof net.minecraft.world.entity.LivingEntity cached
           &&valid(owner,cached,choices)&&visible(level,arrow,cached))target=cached;
   if(target==null){tag.remove(TARGET);target=acquire(level,owner,arrow,choices);}
   // Keep a readable target lock until it dies, leaves range, becomes allied, or is occluded.
   if(target!=null){Vec3 desired=target.getBoundingBox().getCenter().subtract(arrow.position()).normalize().scale(1.8);arrow.setDeltaMovement(arrow.getDeltaMovement().scale(.75).add(desired.scale(.25)));}
   if(arrow.tickCount%2==0)WarriorSwordFx.send(owner,arrow.position().subtract(arrow.getDeltaMovement().scale(2)),arrow.position(),3);
  }
 }
}
