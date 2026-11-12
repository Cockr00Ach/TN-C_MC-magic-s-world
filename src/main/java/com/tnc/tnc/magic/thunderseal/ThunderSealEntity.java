package com.tnc.tnc.magic.thunderseal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import com.tnc.tnc.magic.TNOrbEntities;
public final class ThunderSealEntity extends Entity {
 private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(ThunderSealEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(ThunderSealEntity.class,EntityDataSerializers.LONG);
 private LivingEntity caster,marked;private float power=1;private int hits;
 /** ★ 2026-11-05 第三批：被雷牢"钉住"的敌人（UUID → 解除 tick）—— 作者："击中的敌人不能动啊" ✓ */
 private final java.util.Map<java.util.UUID,Long> rooted=new java.util.HashMap<>();
 public ThunderSealEntity(EntityType<? extends ThunderSealEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
 protected void defineSynchedData(){entityData.define(TIER,1);entityData.define(START,0L);}
 public int tier(){return entityData.get(TIER);}public LivingEntity owner(){return caster;}public int hits(){return hits;}
 public double age(float partial){return Math.max(0,level().getGameTime()-entityData.get(START)+partial);}
 public void configure(LivingEntity owner,int tier,Vec3 at,LivingEntity target){caster=owner;marked=target;entityData.set(TIER,ThunderSealRules.tier(tier));entityData.set(START,level().getGameTime());setPos(at);
  var attribute=net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("spell_power","lightning"));
  if(attribute!=null&&owner.getAttribute(attribute)!=null)power=(float)Math.min(4,1+Math.max(0,owner.getAttributeValue(attribute))*.25);
 }
 public Vec3 node(int slot,double age){int t=tier();double a=2*Math.PI*slot/ThunderSealRules.count(t)-Math.PI/2,r=ThunderSealRules.radius(t);
  double flight=t==5?net.minecraft.util.Mth.clamp((age-ThunderSealRules.finish(t)+12)/12,0,1):0;
  return new Vec3(Math.cos(a)*r*(1-flight*.75),Math.min(1,age/16)*.1+flight*16,Math.sin(a)*r*(1-flight*.75));
 }
 public boolean enemy(LivingEntity e){if(e==caster||!e.isAlive()||e.isSpectator()||e.isAlliedTo(caster))return false;
  if(caster instanceof net.minecraft.world.entity.player.Player p){if(e instanceof net.minecraft.world.entity.player.Player)return false;return e==marked||e instanceof net.minecraft.world.entity.monster.Enemy||e.getLastHurtByMob()==p||p.getLastHurtByMob()==e;}
  return true;
 }
 public void tick(){super.tick();if(level().isClientSide)return;int t=tier(),age=(int)age(0),finish=ThunderSealRules.finish(t);
  if(caster==null||!caster.isAlive()||caster.level()!=level()||caster.distanceToSqr(this)>160*160||age>ThunderSealRules.lifetime(t)){discard();return;}
   tickRooted();
  if(age==1)level().playSound(null,blockPosition(),SoundEvents.BEACON_ACTIVATE,SoundSource.PLAYERS,1.3F,.65F);
  if(age>=ThunderSealRules.charge(t)&&age<finish&&(age-ThunderSealRules.charge(t))%12==0)pulse(false);
  if(age==finish){pulse(true);level().playSound(null,blockPosition(),SoundEvents.LIGHTNING_BOLT_THUNDER,SoundSource.PLAYERS,t>=4?2:1.2F,.8F);}
 }
 private void pulse(boolean last){int t=tier();double radius=ThunderSealRules.radius(t)+(last?2:0);int count=0;
  var candidates=level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(radius,12,radius),this::enemy);candidates.sort(java.util.Comparator.comparingDouble(e->e.distanceToSqr(this)));
  for(var e:candidates){if(e.getBoundingBox().distanceToSqr(position())>radius*radius||!level().clip(new net.minecraft.world.level.ClipContext(position().add(0,.5,0),e.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this)).getType().equals(HitResult.Type.MISS))continue;if(++count>24)break;
   float damage=(last?(6+t*4):(1.5F+t*.65F))*power;
   // ★ 2026-11-05 第三批：4 格内一律钉住（不依赖 hurt —— 免疫伤害的怪也得站住 ✓）
   double rootR=Math.min(4.0D,radius);
   if(e.getBoundingBox().distanceToSqr(position())<=rootR*rootR){root(e,last?40:28);if(count<=3)tether(e);}
   if(e.hurt(level().damageSources().indirectMagic(this,caster),damage)){hits++;}
  }
  var l=(ServerLevel)level();l.sendParticles(ParticleTypes.ELECTRIC_SPARK,getX(),getY()+.5,getZ(),last?24:6,radius*.35,.5,radius*.35,last?.4:.08);
  level().playSound(null,blockPosition(),last?SoundEvents.LIGHTNING_BOLT_IMPACT:SoundEvents.TRIDENT_RETURN,SoundSource.PLAYERS,last?1.4F:.65F,last?.7F:1.5F);
 }
 public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(32,40,32);}public boolean shouldRenderAtSqrDistance(double d){return d<192*192;}
 protected void readAdditionalSaveData(CompoundTag tag){discard();}protected void addAdditionalSaveData(CompoundTag tag){}
 public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}

  /**
   * ★ 被雷牢钉住的敌人<b>不能动</b>（作者 2026-11-05：「雷牢应该击中的敌人不能动啊，4 格以下吧」）。
   *
   * <p>为什么不是单纯叠 {@code MOVEMENT_SLOWDOWN}：减速叠到 -90% 敌人还是会<b>慢慢蹭</b>，
   * 被击退、被水推、被载具带走的时候照样滑出去 ✗。这里直接 {@code setNoAi(true)}
   * （AI 全停：不追、不转、不跳），再每 tick 把水平速度清零 ⇒ 真的钉在原地 ✓；
   * 另外照旧挂一瓶高倍减速，作为非 Mob 实体的兜底 ✓。
   */
  private void root(net.minecraft.world.entity.LivingEntity e,int ticks){
   long until=level().getGameTime()+ticks;
   Long prev=rooted.get(e.getUUID());
   rooted.put(e.getUUID(),Math.max(until,prev==null?0:prev));
   if(e instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);
   e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ticks,6,false,false));
   e.setDeltaMovement(0,Math.min(0,e.getDeltaMovement().y),0);
  }

  /** 每 tick：还冻着的清零水平位移、到点解冻、实体没了就忘掉 ✓ */
  private void tickRooted(){
   if(rooted.isEmpty()||!(level() instanceof ServerLevel server))return;
   long now=level().getGameTime();
   var it=rooted.entrySet().iterator();
   while(it.hasNext()){
    var entry=it.next();
    var entity=server.getEntity(entry.getKey());
    if(entity==null){it.remove();continue;}
    if(now>=entry.getValue()||!entity.isAlive()){
     if(entity instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(false);
     it.remove();continue;
    }
    var dv=entity.getDeltaMovement();
    entity.setDeltaMovement(0,Math.min(0,dv.y),0);
    if(entity instanceof net.minecraft.world.entity.Mob mob)mob.getNavigation().stop();
   }
  }

  /** 封印没了 / 世界卸载：把所有还冻着的松开（否则怪会永久罚站 ✗） */
  private void releaseRooted(){
   if(rooted.isEmpty()||!(level() instanceof ServerLevel server))return;
   for(var id:rooted.keySet()){
    var entity=server.getEntity(id);
    if(entity instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(false);
   }
   rooted.clear();
  }

  /** 一道从笼顶拉到敌人身上的雷索（被钉住的敌人身上能看见电在拉着他 ✓） */
  private void tether(net.minecraft.world.entity.LivingEntity e){
   if(!(level() instanceof ServerLevel server))return;
   var arc=TNOrbEntities.LIGHTNING_ARC.get().create(server);
   if(arc==null)return;
   var top=position().add(0,ThunderSealRules.height(tier())*0.55,0);
   arc.configure(top,e.getBoundingBox().getCenter(),ThunderSealSpells.themeFor(tier()),5,0.09F,false);
   server.addFreshEntity(arc);
  }

  @Override public void remove(RemovalReason reason){releaseRooted();super.remove(reason);}
}
