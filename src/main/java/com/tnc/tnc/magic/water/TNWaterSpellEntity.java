package com.tnc.tnc.magic.water;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Fixed cast frame. Clients render it; only the server fires, damages and drills. */
public final class TNWaterSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BLOCKED=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private Vec3 barrageAim;
    private WaterTerrainBore bore;
    public TNWaterSpellEntity(EntityType<? extends TNWaterSpellEntity> type,Level level) { super(type,level); noPhysics=true;setNoGravity(true); }
    public static void cast(ServerPlayer player,ResourceLocation spell) {
        if(!spell.getNamespace().equals("tnc"))return;
        int tier=WaterSpellRules.tier(spell.getPath()); if(tier==0)return;
        if(tier<=2) { bolt(player, tier,player.getEyePosition().add(player.getLookAngle().scale(.5)),player.getLookAngle().scale(tier==1?1.5:2));return; }
        // One active large cast per player; repeated release cannot multiply drilling work.
        for(var old:player.serverLevel().getEntitiesOfClass(TNWaterSpellEntity.class,player.getBoundingBox().inflate(128)))
            if(player.getUUID().equals(old.owner))old.discard();
        var cast=TNOrbEntities.WATER_SPELL.get().create(player.serverLevel());
        if(cast==null)return;
        cast.owner=player.getUUID();cast.entityData.set(TIER,tier);
        cast.entityData.set(YAW,player.getYRot());cast.entityData.set(PITCH,player.getXRot());
        LivingEntity target=tier==3?TNWaterFieldEntity.aimEnemy(player,48):null;
        var aimWall=player.level().clip(new ClipContext(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(48)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        cast.barrageAim=target!=null?target.getBoundingBox().getCenter():aimWall.getLocation();
        Vec3 desired=player.getEyePosition().add(player.getLookAngle().scale(tier==3?-2:2)).add(0,tier==3?2.5:0,0);
        if(tier==5) {
            // The complete 14-block aperture must not start buried in the caster's own ground.
            double verticalRadius=WaterSpellRules.radius(tier)*Math.sqrt(Math.max(0,1-player.getLookAngle().y*player.getLookAngle().y));
            desired=desired.add(0,Math.max(0,verticalRadius-player.getEyeHeight()+.8-2*player.getLookAngle().y),0);
        }
        var obstacle=player.level().clip(new ClipContext(player.getEyePosition(),desired,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        cast.setPos(obstacle.getType()==HitResult.Type.MISS?desired:obstacle.getLocation().subtract(desired.subtract(player.getEyePosition()).normalize().scale(.2)));
        if(tier==5) {
            Vec3 aim=aimWall.getType()==HitResult.Type.MISS?player.getEyePosition().add(player.getLookAngle().scale(96)):aimWall.getLocation();
            Vec3 direction=aim.subtract(cast.position()).normalize();
            cast.entityData.set(YAW,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
            cast.entityData.set(PITCH,(float)-Math.toDegrees(Math.asin(direction.y)));
        }
        player.serverLevel().addFreshEntity(cast);
    }
    static void bolt(LivingEntity player,int tier,Vec3 at,Vec3 velocity) {
        var bolt=TNOrbEntities.WATER_BOLT.get().create(player.level());
        if(bolt!=null) { bolt.configure(player,tier,at,velocity);player.level().addFreshEntity(bolt); }
    }
    public int tier(){return entityData.get(TIER);}
    public int age(){return entityData.get(AGE);}
    public float length(){return entityData.get(LENGTH);}
    public boolean blocked(){return entityData.get(BLOCKED);}
    public Vec3 direction(){return Vec3.directionFromRotation(entityData.get(PITCH),entityData.get(YAW));}
    @Override protected void defineSynchedData(){entityData.define(TIER,3);entityData.define(AGE,0);entityData.define(LENGTH,0F);entityData.define(YAW,0F);entityData.define(PITCH,0F);entityData.define(BLOCKED,false);}
    @Override public void tick() {
        super.tick(); if(level().isClientSide)return;
        ServerLevel level=(ServerLevel)level();
        ServerPlayer player=owner==null?null:level.getServer().getPlayerList().getPlayer(owner);
        if(player==null || !player.isAlive() || player.isSpectator() || player.level()!=level || player.distanceToSqr(this)>128*128) {discard();return;}
        entityData.set(AGE,tickCount);
        int active=tickCount-WaterSpellRules.charge(tier());
        if(active<0)return;
        if(active>=WaterSpellRules.duration(tier())) { discard();return; }
        Vec3 dir=direction();
        if(active==0) level.playSound(null,blockPosition(),SoundEvents.CONDUIT_ACTIVATE,SoundSource.PLAYERS,1.5F,tier()==5?.5F:.85F);
        if(tier()==3) {
            if(active%4==0) {
                Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
                for(int i=0;i<3;i++) {
                    double angle=(active/4*3+i)*2.399963;
                    Vec3 at=position().add(right.scale(Math.cos(angle)*2.8)).add(up.scale(Math.sin(angle)*2.8));
                    Vec3 aim=barrageAim!=null?barrageAim:position().add(dir.scale(38));
                    bolt(player,3,at,aim.subtract(at).normalize().scale(1.8));
                }
            }
            return;
        }
        double len;
        if(tier()==5) {
            if(bore==null)bore=new WaterTerrainBore(position(),dir,WaterSpellRules.radius(tier()),WaterSpellRules.range(tier()));
            bore.tick(level,player);len=bore.length();
            if(bore.stopped()&&!blocked()) {
                entityData.set(BLOCKED,true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§b龙滅受阻："+bore.stopReason()+"。请换到开阔处施放。"),true);
                level.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,.7F,.55F);
            }
        } else len=clearLength(level,dir);
        entityData.set(LENGTH,(float)len);
        if(len>0 && active%10==0) {
            double radius=WaterSpellRules.radius(tier());
            Vec3 end=position().add(dir.scale(len));
            for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(position(),end).inflate(radius),t->WaterSpellRules.enemy(player,t))) {
                Vec3 center=target.getBoundingBox().getCenter();
                double along=center.subtract(position()).dot(dir);
                double hitRadius=radius+target.getBbWidth()*.5;
                if(along>=0 && along<=len && WaterSpellRules.segmentDistanceSquared(center,position(),dir,len)<=hitRadius*hitRadius
                        && TNWaterFieldEntity.clearSight(level,position(),center))
                    target.hurt(level.damageSources().indirectMagic(this,player),WaterSpellRules.damage(tier())*WaterSpellRules.power(player));
            }
        }
        if(active%20==0 && len>0)level.playSound(null,blockPosition(),SoundEvents.WATER_AMBIENT,SoundSource.PLAYERS,1.2F,.65F);
    }
    private double clearLength(ServerLevel level,Vec3 dir) {
        double length=WaterSpellRules.range(tier());
        for(int i=0;i<=length;i++)if(!level.hasChunkAt(BlockPos.containing(position().add(dir.scale(i))))) {length=Math.max(0,i-1);break;}
        java.util.Set<BlockPos> seen=new java.util.HashSet<>();
        Vec3 end=position().add(dir.scale(length));
        for(int step=0;step<=length;step++) {
            BlockPos center=BlockPos.containing(position().add(dir.scale(step)));
            for(BlockPos mutable:BlockPos.betweenClosed(center.offset(-2,-2,-2),center.offset(2,2,2))) {
                BlockPos pos=mutable.immutable();if(!seen.add(pos))continue;
                if(!level.hasChunkAt(pos)){length=Math.min(length,Math.max(0,step-2));continue;}
                for(AABB box:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs()) {
                    AABB volume=box.move(pos).inflate(.8);
                    if(volume.contains(position())){length=0;break;}
                    var hit=volume.clip(position(),end);
                    if(hit.isPresent())length=Math.min(length,position().distanceTo(hit.get()));
                }
            }
        }
        return length;
    }
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isAttackable(){return false;}
}
