package com.tnc.tnc.world.ink;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** Shared rotating ink decals and an airborne, gathering explosion orb. No persistent world edits. */
public final class InkRitualEntity extends Entity {
    public static final int CRANE=0,ERUPTION=1,ORB=2;
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(InkRitualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE=SynchedEntityData.defineId(InkRitualEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(InkRitualEntity.class,EntityDataSerializers.LONG);
    private UUID ownerId,targetId;
    public InkRitualEntity(EntityType<?> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public static InkRitualEntity spawn(Mob owner,LivingEntity target,int mode,Vec3 at,int life){
        var e=InkBossRegistration.INK_RITUAL.create(owner.level());if(e==null)return null;
        e.ownerId=owner.getUUID();e.targetId=target==null?null:target.getUUID();e.entityData.set(MODE,mode);e.entityData.set(LIFE,life);e.entityData.set(START,owner.level().getGameTime());
        e.setPos(at);owner.level().addFreshEntity(e);return e;
    }
    @Override protected void defineSynchedData(){entityData.define(MODE,0);entityData.define(LIFE,100);entityData.define(START,0L);}
    public int mode(){return entityData.get(MODE);}public int age(){return (int)(level().getGameTime()-entityData.get(START));}public int life(){return entityData.get(LIFE);}
    public boolean ownedBy(Entity e){return e.getUUID().equals(ownerId);}
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        var level=(ServerLevel)level();Entity raw=ownerId==null?null:level.getEntity(ownerId);
        if(!(raw instanceof Mob owner)||!owner.isAlive()||age()>=life()){discard();return;}
        if(mode()!=ORB)return;
        if(age()<65){
            for(LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(28),e->hostile(owner,e))){
                Vec3 d=position().subtract(victim.getBoundingBox().getCenter());double length=d.length();
                if(length<1||length>28||!visible(victim))continue;Vec3 dir=d.scale(1/length),v=victim.getDeltaMovement();
                victim.setDeltaMovement(v.add(dir.scale(Math.max(0,Math.min(.13,.55-v.dot(dir))))));victim.hurtMarked=true;
            }
        }else if(age()==65){
            for(LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(16),e->hostile(owner,e)))
                if(victim.position().distanceToSqr(position())<256&&visible(victim))victim.hurt(damageSources().mobAttack(owner),28);
            playSound(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,3,.5F);
        }
    }
    private static boolean hostile(Mob owner,LivingEntity e){return e!=owner&&e.isAlive()&&!owner.isAlliedTo(e)&&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    private boolean visible(LivingEntity e){return level().clip(new ClipContext(position(),e.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()==HitResult.Type.MISS;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag n){discard();}@Override protected void addAdditionalSaveData(CompoundTag n){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(96);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<512*512;}
}
