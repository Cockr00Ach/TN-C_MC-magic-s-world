package com.tnc.tnc.magic.water;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Wave, restraint and rain fields. No real water blocks and no terrain damage. */
public final class TNWaterFieldEntity extends Entity {
    private static final String[][] IDS={
            {"water_ripple","water_wave","wave_slash","tsunami","world_ending_sea"},
            {"water_bind","water_prison","water_burial","abyss","sea_god_crypt"},
            {"raindrop","first_rain","rainfall","downpour","flood_of_heaven"}};
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.INT);
    private final WaterVisualClock visualClock=new WaterVisualClock();
    public double visualAge(float partial){return visualClock.time(tickCount,partial,age());}
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EMITTER=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.BOOLEAN);
    /** 刃面滚转（度）✓：让一批斩击不再是清一色竖着的 C ✗。只影响表现 ✗，判定与它无关 ✓。 */
    private static final EntityDataAccessor<Float> ROLL=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BOSS_CAST=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SKY_HEIGHT=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.FLOAT);
    public boolean bossCrypt(){return entityData.get(BOSS_CAST) && kind()==2 && tier()==5;}
    public int swordCount(){return bossCrypt()?1:SeaGodSwordRules.COUNT;}
    public float skySealHeight(){return entityData.get(SKY_HEIGHT);}
    public Vec3 skySealCenter(){return position().add(0,skySealHeight(),0);}
    public int swordImpactIndex(int age){int index=SeaGodSwordRules.impactIndex(age);return index>=swordCount()?-1:index;}
    private boolean swordCue(int age,int first){return bossCrypt()?age==first:SeaGodSwordRules.cue(age,first);}
    private UUID owner,anchor;
    private int lastSwordImpact=-1;
    private boolean seaWeatherStarted;
    private final Set<UUID> hit=new HashSet<>();
    public TNWaterFieldEntity(EntityType<? extends TNWaterFieldEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public int kind(){return entityData.get(KIND);} public int tier(){return entityData.get(TIER);}public int age(){return entityData.get(AGE);}
    public Vec3 direction(){return Vec3.directionFromRotation(entityData.get(PITCH),entityData.get(YAW));}
    public double radius(){return WaterSpellRules.fieldRadius(kind(),tier());}
    public boolean isEmitter(){return entityData.get(EMITTER);}
    public float roll(){return entityData.get(ROLL);}
    public int life(){return bossCrypt()?200:isEmitter()?WaterSpellRules.SLASH_CHANNEL_TICKS:WaterSpellRules.fieldLife(kind(),tier());}
    public double height(){return WaterSpellRules.fieldHeight(kind(),tier());}
    public Vec3 waveCenter(double age){return kind()==1 && tier()>1 && tier()<5?position().add(direction().scale(age*(tier()==3?1.35:tier()==4?.5:.8))):position();}
    public static boolean cast(ServerPlayer player,ResourceLocation spell) {
        if(!spell.getNamespace().equals("tnc"))return false;
        for(int k=0;k<IDS.length;k++)for(int t=0;t<5;t++)if(IDS[k][t].equals(spell.getPath())) {
            // One rain/control area per caster and route; no endless stacked healing/damage.
            if(k>0 || k==0&&t==4)for(var old:player.serverLevel().getEntitiesOfClass(TNWaterFieldEntity.class,player.getBoundingBox().inflate(128)))
                if(player.getUUID().equals(old.owner) && old.kind()==k+1 && (k>0 || old.tier()==5))old.discard();
            Vec3 at=player.position();LivingEntity target=null;
            if(k>0) {
                var block=player.level().clip(new ClipContext(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(36)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                Vec3 surface=block.getLocation().add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(.2));
                if(k==1) {
                    target=aimEnemy(player,36);
                    if(target!=null)at=target.position();
                    else if(t<2)return true;
                    else at=block.getType()==HitResult.Type.MISS?player.position().add(player.getLookAngle().scale(12)):surface;
                } else at=block.getType()==HitResult.Type.MISS?player.position():surface.add(0,.05,0);
            }
            boolean slash=k==0&&t==2;
            if(slash) {
                at=player.getEyePosition();
                for(var old:player.serverLevel().getEntitiesOfClass(TNWaterFieldEntity.class,player.getBoundingBox().inflate(128)))
                    if(player.getUUID().equals(old.owner)&&old.kind()==1&&old.tier()==3)old.discard();
                var emitter=TNOrbEntities.WATER_FIELD.get().create(player.level());
                if(emitter!=null) {
                    emitter.owner=player.getUUID();emitter.setPos(at);emitter.entityData.set(TIER,3);
                    emitter.entityData.set(EMITTER,true);emitter.entityData.set(YAW,player.getYRot());emitter.entityData.set(PITCH,player.getXRot());player.level().addFreshEntity(emitter);
                    emitter.emitSlash(player,0);
                }
            }
            if(slash) {
                player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.PLAYER_SPLASH,SoundSource.PLAYERS,1,.7F);
                return true;
            }
            var directions=java.util.List.of(Vec3.directionFromRotation(0,player.getYRot()));
            int count=directions.size();
            for(int i=0;i<count;i++) {
                var e=TNOrbEntities.WATER_FIELD.get().create(player.level());if(e==null)continue;
                e.owner=player.getUUID();e.anchor=target==null?null:target.getUUID();e.setPos(at);
                Vec3 direction=directions.get(i);
                e.entityData.set(KIND,k+1);e.entityData.set(TIER,t+1);
                e.entityData.set(YAW,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
                e.entityData.set(PITCH,(float)-Math.toDegrees(Math.asin(direction.y)));
                // 刃面滚转：环内槽位 × 45° ⇒ 同一批斩击出现 0/45/90/135/180/… 的刃角 ✓
                if(slash)e.entityData.set(ROLL,WaterSpellRules.slashRoll(i));
                if(player.level().addFreshEntity(e)&&k==1&&target!=null)WaterRestraints.hold(e,target);
            }
            player.serverLevel().playSound(null,BlockPos.containing(at),t==4?SoundEvents.CONDUIT_ACTIVATE:SoundEvents.PLAYER_SPLASH,SoundSource.PLAYERS,t==4?3:1,t==4?.45F:.7F);
            // 施法那一下向前压出一面水墙 ✓（纯表现 ✗，不改伤害/时长 ✗）
            if(k==0)WaterFx.waveCast(player.serverLevel(),at,Vec3.directionFromRotation(0,player.getYRot()),t+1);
            return true;
        }
        return false;
    }
    /** Boss casts share the real wave/crypt model, timeline and effects with player magic. */
    public static boolean castBoss(com.tnc.tnc.boss.TNWaterLeviathanEntity caster,
                                   LivingEntity target, String spell) {
        if (!(caster.level() instanceof ServerLevel level) || !caster.isAlive()
                || !WaterSpellRules.enemy(caster, target)) return false;
        boolean crypt = spell.equals("sea_god_crypt");
        if (!crypt && !spell.equals("tsunami")) return false;
        if (crypt) for (var old : level.getEntitiesOfClass(TNWaterFieldEntity.class,
                caster.getBoundingBox().inflate(128))) {
            if (caster.getUUID().equals(old.owner) && old.kind()==2) old.discard();
        }
        Vec3 toward = target.position().subtract(caster.position()).multiply(1, 0, 1);
        Vec3 direction = toward.lengthSqr()<0.01D
                ? Vec3.directionFromRotation(0, caster.getYRot()) : toward.normalize();
        Vec3 at = crypt ? target.position() : caster.position().add(direction.scale(4.0D));
        if (!crypt && !caster.isInWater()) {
            var ground = level.clip(new ClipContext(at.add(0, 1, 0), at.add(0, -16, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (ground.getType()!=HitResult.Type.MISS) at = ground.getLocation().add(0, 0.1D, 0);
        }
        if (!level.hasChunkAt(BlockPos.containing(at))) return false;
        var field = TNOrbEntities.WATER_FIELD.get().create(level);
        if (field==null) return false;
        field.owner=caster.getUUID(); field.setPos(at);
        field.entityData.set(BOSS_CAST,true);
        field.entityData.set(SKY_HEIGHT,(float)Math.max(8,Math.min(116,level.getMaxBuildHeight()-at.y-24)));
        field.entityData.set(KIND, crypt ? 2 : 1);
        field.entityData.set(TIER, crypt ? 5 : 4);
        field.entityData.set(YAW, (float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        field.entityData.set(PITCH, 0.0F);
        boolean added = level.addFreshEntity(field);
        if (added && crypt) caster.beginChannel(field,target,
                field.skySealCenter().add(0,6.5D,0).subtract(direction.scale(1.7D)),
                field.entityData.get(YAW));
        if (added && !crypt) WaterFx.waveCast(level, at, direction, 4);
        level.playSound(null, BlockPos.containing(at), crypt ? SoundEvents.CONDUIT_ACTIVATE
                : SoundEvents.PLAYER_SPLASH, SoundSource.HOSTILE, crypt ? 3 : 1.5F, 0.55F);
        return added;
    }

    public static LivingEntity aimEnemy(ServerPlayer player,double range) {
        Vec3 from=player.getEyePosition(),end=from.add(player.getLookAngle().scale(range));
        var wall=player.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        LivingEntity closest=null;double d=from.distanceToSqr(end);
        for(var target:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(1),e->WaterSpellRules.enemy(player,e))) {
            var p=target.getBoundingBox().inflate(.25).clip(from,end);
            if(p.isPresent() && p.get().distanceToSqr(from)<d){closest=target;d=p.get().distanceToSqr(from);}
        }
        return closest;
    }
    @Override protected void defineSynchedData(){entityData.define(BOSS_CAST,false);entityData.define(SKY_HEIGHT,116F);entityData.define(KIND,1);entityData.define(TIER,1);entityData.define(AGE,0);entityData.define(YAW,0F);entityData.define(PITCH,0F);entityData.define(EMITTER,false);entityData.define(ROLL,0F);}
    @Override public void tick() {
        super.tick();
        if(level().isClientSide) { clientRain(); return; }
        ServerLevel level=(ServerLevel)level();
        LivingEntity player=WaterSpellOwners.resolve(level,owner);
        if(player==null || player.distanceToSqr(this)>128*128 || tickCount>life()){discard();return;}
        entityData.set(AGE,tickCount);
        if(isEmitter()) {
            if (!(player instanceof ServerPlayer serverPlayer)) { discard(); return; }
            setPos(player.getEyePosition());
            if(tickCount<life()&&tickCount%WaterSpellRules.SLASH_VOLLEY_INTERVAL==0) {
                emitSlash(serverPlayer,tickCount/WaterSpellRules.SLASH_VOLLEY_INTERVAL);
                // Each rapidly emitted front has a short hand splash.
                WaterFx.waveCast(level,position(),player.getLookAngle(),3);
            }
            entityData.set(YAW,player.getYRot());entityData.set(PITCH,player.getXRot());
            if(tickCount>=life())discard();
            return;
        }
        if(kind()==2 && tier()==5 && swordCue(tickCount,SeaGodSwordRules.APPEAR_TICK)) {
            level.playSound(null,blockPosition(),SoundEvents.CONDUIT_ACTIVATE,SoundSource.PLAYERS,2,.45F);
            // 剑在 64 格高处出现那一下 ✓（客户端同期画剑 ✗）
            WaterFx.cryptCue(level,position(),radius(),false);
        }
        if(kind()==2 && tier()==5 && swordCue(tickCount,SeaGodSwordRules.DROP_TICK)) {
            level.playSound(null,blockPosition(),SoundEvents.TRIDENT_RIPTIDE_3,SoundSource.PLAYERS,2,.6F);
            WaterFx.cryptCue(level,position(),radius(),true);
        }
        if(kind()==1 && tier()==5 && tickCount%40==0)
            level.playSound(null,blockPosition(),SoundEvents.PLAYER_SPLASH,SoundSource.PLAYERS,2,.4F);
        if(kind()==2 && tier()<3 && anchor!=null){var e=level.getEntity(anchor);if(e==null || !e.isAlive()){discard();return;}}
        if(kind()==3){if(player instanceof ServerPlayer p)rain(level,p);else discard();return;}
        if(kind()==1)waveFx(level);
        else if(kind()==2)WaterFx.bindFx(level,position(),radius(),height(),tier(),tickCount);
        affect(level,player);
        finishSea(level);
        if(bossCrypt() && tickCount>=life()) discard();
    }
    /**
     * 水纹链粒子（纯表现 ✗，不改伤害/时长/判定 ✗）。
     *
     * <p>位置全部照抄客户端几何：t1 用地面环那条公式 ✓、t2/t4 用 {@code WaterWaveVisuals}
     * 的卷浪剖面 ✓、t3 用薄刃弧 ✓、t5 用 {@link OceanWaveRules} 的真实浪前剖面 ✓ ——
     * 所以粒子是"长在浪上"的，不是在浪旁边乱撒 ✗。
     */
    void waveFx(ServerLevel level) {
        if(tier()==5) {
            if(tickCount%4==0)WaterFx.seaFronts(level,position(),radius(),tickCount);
            return;
        }
        if(tickCount%2!=0)return;
        if(tier()==1) {WaterFx.ripple(level,position(),radius(),tickCount);return;}
        Vec3 center=waveCenter(tickCount);
        if(tier()==3)WaterFx.slashFront(level,center,direction(),radius(),tickCount,WaterSpellRules.slashRollRadians(roll(),tickCount));
        else WaterFx.waveFront(level,center,direction(),radius(),height(),tickCount,tier());
    }
    void emitSlash(ServerPlayer player,int index) {
        if(index>=WaterSpellRules.SLASH_COUNT)return;
        Vec3 direction=player.getLookAngle().normalize();
        var front=TNOrbEntities.WATER_FIELD.get().create(level());if(front==null)return;
        front.owner=owner;front.setPos(player.getEyePosition().add(direction.scale(.65)));
        front.entityData.set(TIER,3);
        front.entityData.set(YAW,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        front.entityData.set(PITCH,(float)-Math.toDegrees(Math.asin(direction.y)));
        front.entityData.set(ROLL,WaterSpellRules.slashRoll(index));
        level().addFreshEntity(front);
    }
    /** Shared real effect body; owner/lifecycle guards remain in tick(). */
    void affect(ServerLevel level,ServerPlayer player) { affect(level,(LivingEntity)player); }
    void affect(ServerLevel level,LivingEntity player) {
        Vec3 center=waveCenter(tickCount),previous=waveCenter(tickCount-1);
        if(!level.hasChunkAt(BlockPos.containing(center))) {discard();return;}
        if(kind()==1 && tier()>1 && tier()<5) {
            Vec3 lift=tier()==3?Vec3.ZERO:new Vec3(0,.8,0);
            var wall=level.clip(new ClipContext(previous.add(lift),center.add(lift),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(wall.getType()!=HitResult.Type.MISS){discard();return;}
        }
        // Ocean damage only occurs once per second; avoid area queries and ray casts on the other ticks.
        if(kind()==1 && tier()==5 && tickCount%20!=0)return;
        double r=radius(),height=height();
        if(kind()==2 && tier()==5)seaGodSwordImpact(level,player);
        var box=kind()==2||kind()==1&&tier()==5?WaterSpellRules.uprightArea(center,r,height):new AABB(previous,center).inflate(r,height,r);
        for(var enemy:level.getEntitiesOfClass(LivingEntity.class,box,e->WaterSpellRules.enemy(player,e))) {
            Vec3 delta=enemy.position().subtract(center);
            if(kind()==1 && tier()==3) {
                if(!WaterSpellRules.slashIntersects(enemy.getBoundingBox(),previous,center,direction(),r))continue;
            } else if(kind()==1 && tier()>1 && tier()<5) {
                if(Math.abs(delta.dot(direction()))>2 || Math.abs(delta.dot(WaterSpellRules.right(direction())))>r)continue;
            } else if(delta.x*delta.x+delta.z*delta.z>r*r)continue;
            if(kind()==1 && tier()==5 && !OceanWaveRules.hits(delta.horizontalDistance(),delta.y,Math.atan2(delta.z,delta.x),tickCount))continue;
            if(!clearSight(level,kind()==1&&tier()==3?center:center.add(0,1,0),enemy.getBoundingBox().getCenter()))continue;
            if(kind()==1) {
                if(tier()==5 ? tickCount%20!=0 : !hit.add(enemy.getUUID()))continue;
                if(enemy.hurt(level.damageSources().indirectMagic(this,player),(tier()==5?10:2+tier()*2)*WaterSpellRules.power(player))) {
                    Vec3 push=tier()==1||tier()==5?delta.normalize():direction();enemy.push(push.x*.65,tier()==3?push.y*.65+.1:.15,push.z*.65);
                    // 命中那一下在被击中的目标身上炸一小圈 ✓（每目标一次 / t5 每 20 tick 一次 ✗）
                    WaterFx.hit(level,enemy.position().add(0,enemy.getBbHeight()*.5,0),delta.normalize(),tier());
                }
            } else {
                if(WaterSpellRules.canRestrain(enemy.getBbHeight())) {
                    WaterRestraints.hold(this,enemy);
                    enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,2,4,false,false));
                }
                if(tickCount%20==0)enemy.hurt(level.damageSources().indirectMagic(this,player),tier()*WaterSpellRules.power(player));
                if(tier()>=4 && hit.add(enemy.getUUID()) && hit.size()==1)player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,tier()==5?1:0));
                if(tickCount==life() && tier()==5)enemy.hurt(level.damageSources().indirectMagic(this,player),12*WaterSpellRules.power(player));
            }
        }
    }
    /** One burst per scheduled sword, never once per animation frame. */
    void seaGodSwordImpact(ServerLevel level,ServerPlayer player) { seaGodSwordImpact(level,(LivingEntity)player); }
    void seaGodSwordImpact(ServerLevel level,LivingEntity player) {
        int index=swordImpactIndex(tickCount);
        if(isRemoved() || kind()!=2 || tier()!=5 || index<0 || index<=lastSwordImpact)return;
        lastSwordImpact=index;
        Vec3 center=position();double r=radius();
        // 落剑冲击表现 ✓ —— 每一剑一次（靠上面的 index 去重 ✗），不是每帧 ✗
        WaterFx.cryptImpact(level,center,r,index);
        for(var enemy:level.getEntitiesOfClass(LivingEntity.class,WaterSpellRules.uprightArea(center,r,height()),e->WaterSpellRules.enemy(player,e))) {
            if(enemy.position().subtract(center).horizontalDistanceSqr()>r*r
                    || !clearSight(level,center.add(0,1,0),enemy.getBoundingBox().getCenter()))continue;
            if(enemy.hurt(level.damageSources().indirectMagic(this,player),SeaGodSwordRules.DAMAGE*WaterSpellRules.power(player))) {
                Vec3 delta=enemy.position().subtract(center);
                Vec3 push=new Vec3(delta.x,0,delta.z).normalize();enemy.push(push.x*.7,.35,push.z*.7);
            }
        }
        level.playSound(null,blockPosition(),SoundEvents.TRIDENT_THUNDER,SoundSource.PLAYERS,4,.55F);
        level.playSound(null,blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,2,.65F);
    }
    void finishSea(ServerLevel level) {
        if(!isRemoved() && kind()==1 && tier()==5 && tickCount==life() && !seaWeatherStarted) {
            seaWeatherStarted=true;WaterWeather.storm(level);
            WaterFx.seaBurst(level,position());
        }
    }
    static boolean clearSight(ServerLevel level,Vec3 from,Vec3 to){return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null)).getType()==HitResult.Type.MISS;}
    public static boolean visible(ServerLevel level,Vec3 from,Vec3 to){return clearSight(level,from,to);}
    void rain(ServerLevel level,ServerPlayer player) {
        double r=radius();
        // 雨滴链粒子 ✓（纯表现 ✗）：雨幕 / 地面水环 / 水莲 / 水柱 / 天洪天空法阵 ✗
        WaterFx.rainFx(level,position(),r,height(),tier(),tickCount);
        // Lower tiers only heal once per second. Higher tiers still refresh in-area resistance every tick.
        if(tier()<4 && tickCount%20!=0)return;
        var area=new AABB(position().add(-r,-1,-r),position().add(r,height(),r));
        var targets=tier()<5?new ArrayList<LivingEntity>():new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,area,
                target -> target.isAlive() && (target==player || player.isAlliedTo(target)
                        || target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID()))));
        // Explicit owner inclusion also supports spell callbacks during entity-list registration.
        if(area.intersects(player.getBoundingBox())&&!targets.contains(player))targets.add(player);
        for(var target:targets) {
            boolean friend=target==player || tier()==5 && (player.isAlliedTo(target)||target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID()));
            if(!friend || !target.isAlive() || target.position().subtract(position()).horizontalDistanceSqr()>r*r
                    || !clearSight(level,position().add(0,1,0),target.getBoundingBox().getCenter()))continue;
            if(tickCount%20==0) {
                target.heal(switch(tier()){case 1 -> 1F;case 2 -> 1.5F;case 3 -> 2F;case 4 -> 3F;default -> 4F;});
                // 被雨治好那一下：目标身上冒水光 ✓（每目标每 20 tick 一次 ✗，很省 ✓）
                WaterFx.heal(level,target.position().add(0,target.getBbHeight()*.5,0));
            }
            // Two ticks tolerate entity tick ordering without carrying protection out of the area.
            if(tier()>=4)target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,2,tier()-4,false,false));
        }
    }
    private void clientRain() {
        if(kind()!=3 || tickCount%2!=0)return;
        var viewer=level().getNearestPlayer(this,80);
        if(viewer==null)return;
        int drops=2+tier();
        for(int i=0;i<drops;i++) {
            int count=WaterRainModelLayout.count(tier(),true);
            Vec3 at=WaterRainModelLayout.drop((tickCount/2+i)%count,radius(),height(),visualAge(0));
            level().addParticle(net.minecraft.core.particles.ParticleTypes.GLOW,
                    getX()+at.x,getY()+at.y,getZ()+at.z,0,-.12,0);
        }
    }
    @Override public void remove(RemovalReason reason) {
        if(level() instanceof ServerLevel server)WaterRestraints.release(server,getUUID());
        super.remove(reason);
    }
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag t){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public boolean isPickable(){return false;}
}
