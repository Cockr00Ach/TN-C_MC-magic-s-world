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
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> YAW=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH=SynchedEntityData.defineId(TNWaterFieldEntity.class,EntityDataSerializers.FLOAT);
    private UUID owner,anchor;
    private int lastSwordImpact=-1;
    private boolean seaWeatherStarted;
    private final Set<UUID> hit=new HashSet<>();
    public TNWaterFieldEntity(EntityType<? extends TNWaterFieldEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public int kind(){return entityData.get(KIND);} public int tier(){return entityData.get(TIER);}public int age(){return entityData.get(AGE);}
    public Vec3 direction(){return Vec3.directionFromRotation(entityData.get(PITCH),entityData.get(YAW));}
    public double radius(){return WaterSpellRules.fieldRadius(kind(),tier());}
    public int life(){return WaterSpellRules.fieldLife(kind(),tier());}
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
            if(slash)at=player.getEyePosition();
            var directions=slash?WaterSpellRules.slashDirections(player.getYRot()):java.util.List.of(Vec3.directionFromRotation(0,player.getYRot()));
            int count=directions.size();
            for(int i=0;i<count;i++) {
                var e=TNOrbEntities.WATER_FIELD.get().create(player.level());if(e==null)continue;
                e.owner=player.getUUID();e.anchor=target==null?null:target.getUUID();e.setPos(at);
                Vec3 direction=directions.get(i);
                e.entityData.set(KIND,k+1);e.entityData.set(TIER,t+1);
                e.entityData.set(YAW,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
                e.entityData.set(PITCH,(float)-Math.toDegrees(Math.asin(direction.y)));
                player.level().addFreshEntity(e);
            }
            player.serverLevel().playSound(null,BlockPos.containing(at),t==4?SoundEvents.CONDUIT_ACTIVATE:SoundEvents.PLAYER_SPLASH,SoundSource.PLAYERS,t==4?3:1,t==4?.45F:.7F);
            return true;
        }
        return false;
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
    @Override protected void defineSynchedData(){entityData.define(KIND,1);entityData.define(TIER,1);entityData.define(AGE,0);entityData.define(YAW,0F);entityData.define(PITCH,0F);}
    @Override public void tick() {
        super.tick();if(level().isClientSide)return;ServerLevel level=(ServerLevel)level();
        var player=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
        if(player==null || !player.isAlive() || player.isSpectator() || player.level()!=level || player.distanceToSqr(this)>128*128 || tickCount>life()){discard();return;}
        entityData.set(AGE,tickCount);
        if(kind()==2 && tier()==5 && SeaGodSwordRules.cue(tickCount,SeaGodSwordRules.APPEAR_TICK))
            level.playSound(null,blockPosition(),SoundEvents.CONDUIT_ACTIVATE,SoundSource.PLAYERS,2,.45F);
        if(kind()==2 && tier()==5 && SeaGodSwordRules.cue(tickCount,SeaGodSwordRules.DROP_TICK))
            level.playSound(null,blockPosition(),SoundEvents.TRIDENT_RIPTIDE_3,SoundSource.PLAYERS,2,.6F);
        if(kind()==1 && tier()==5 && tickCount%40==0)
            level.playSound(null,blockPosition(),SoundEvents.PLAYER_SPLASH,SoundSource.PLAYERS,2,.4F);
        if(kind()==2 && tier()<3 && anchor!=null){var e=level.getEntity(anchor);if(e==null || !e.isAlive()){discard();return;}setPos(e.position());}
        if(kind()==3){rain(level,player);return;}
        affect(level,player);
        finishSea(level);
    }
    /** Shared real effect body; owner/lifecycle guards remain in tick(). */
    void affect(ServerLevel level,ServerPlayer player) {
        Vec3 center=waveCenter(tickCount),previous=waveCenter(tickCount-1);
        if(!level.hasChunkAt(BlockPos.containing(center))) {discard();return;}
        if(kind()==1 && tier()>1 && tier()<5) {
            Vec3 lift=tier()==3?Vec3.ZERO:new Vec3(0,.8,0);
            var wall=level.clip(new ClipContext(previous.add(lift),center.add(lift),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
            if(wall.getType()!=HitResult.Type.MISS){discard();return;}
        }
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
                }
            } else {
                enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,10,tier()==1?1:3,false,false));
                if(tier()>=3) {Vec3 pull=center.subtract(enemy.position()).normalize().scale(.12);enemy.push(pull.x,0,pull.z);}
                if(tickCount%20==0)enemy.hurt(level.damageSources().indirectMagic(this,player),tier()*WaterSpellRules.power(player));
                if(tier()>=4 && hit.add(enemy.getUUID()) && hit.size()==1)player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,tier()==5?1:0));
                if(tickCount==life() && tier()==5)enemy.hurt(level.damageSources().indirectMagic(this,player),12*WaterSpellRules.power(player));
            }
        }
    }
    /** One burst per scheduled sword, never once per animation frame. */
    void seaGodSwordImpact(ServerLevel level,ServerPlayer player) {
        int index=SeaGodSwordRules.impactIndex(tickCount);
        if(isRemoved() || kind()!=2 || tier()!=5 || index<0 || index<=lastSwordImpact)return;
        lastSwordImpact=index;
        Vec3 center=position();double r=radius();
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
        }
    }
    static boolean clearSight(ServerLevel level,Vec3 from,Vec3 to){return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null)).getType()==HitResult.Type.MISS;}
    void rain(ServerLevel level,ServerPlayer player) {
        double r=radius();
        var area=new AABB(position().add(-r,-1,-r),position().add(r,height(),r));
        var targets=new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,area));
        // Explicit owner inclusion also supports spell callbacks during entity-list registration.
        if(area.intersects(player.getBoundingBox())&&!targets.contains(player))targets.add(player);
        for(var target:targets) {
            boolean friend=target==player || tier()==5 && (player.isAlliedTo(target)||target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID()));
            if(!friend || !target.isAlive() || target.position().subtract(position()).horizontalDistanceSqr()>r*r
                    || !clearSight(level,position().add(0,1,0),target.getBoundingBox().getCenter()))continue;
            if(tickCount%20==0)target.heal(new float[]{1,1.5F,2,3,4}[tier()-1]);
            // Two ticks tolerate entity tick ordering without carrying protection out of the area.
            if(tier()>=4)target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,2,tier()-4,false,false));
        }
    }
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag t){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public boolean isPickable(){return false;}
}
