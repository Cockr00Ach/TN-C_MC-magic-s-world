package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.dungeon.DungeonService;
import com.tnc.tnc.mana.sight.ManaSightServer;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Per-observer invisibility is presentation only; attack eligibility is checked on the server. */
public final class ManaSiphonJelly extends EcologyMob {
    private static final EntityDataAccessor<Integer> EXPOSED=SynchedEntityData.defineId(ManaSiphonJelly.class,EntityDataSerializers.INT);
    private UUID host;
    private int attachCooldown;
    public ManaSiphonJelly(EntityType<? extends Monster> type,Level level){super(type,level,"siphon");setNoGravity(true);}
    @Override public String ecologyKind(){return "siphon";}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(EXPOSED,0);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));}
    public boolean attached(){return action()==1;}
    public int exposedTicks(){return entityData.get(EXPOSED);}
    public UUID host(){return host;}
    public boolean attach(ServerPlayer player){if(!canFight()||attachCooldown>0||!DungeonService.mayTarget(this,player)||distanceToSqr(player)>4)return false;host=player.getUUID();action(1);getNavigation().stop();setDeltaMovement(Vec3.ZERO);return true;}
    public void dislodge(){host=null;action(0);attachCooldown=100;entityData.set(EXPOSED,100);setDeltaMovement((random.nextDouble()-.5)*.5,.35,(random.nextDouble()-.5)*.5);hasImpulse=true;playSound(SoundEvents.PHANTOM_FLAP,.8f,1.8f);}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level))return;
        if(attachCooldown>0)attachCooldown--;if(exposedTicks()>0)entityData.set(EXPOSED,exposedTicks()-1);
        if(!canFight()){if(host!=null){host=null;action(0);}return;}
        if(host!=null){
            var player=level.getPlayerByUUID(host);if(!(player instanceof ServerPlayer p)||!DungeonService.mayTarget(this,p)){dislodge();return;}
            var behind=p.getLookAngle().multiply(1,0,1).normalize().scale(-.42);setPos(p.getX()+behind.x,p.getY()+1.38,p.getZ()+behind.z);setDeltaMovement(Vec3.ZERO);setYRot(p.getYRot());
            if(tickCount%20==0)drain(p);return;
        }
        if(tickCount%10==0){ServerPlayer nearest=null;double distance=18*18;for(var p:level.players())if(DungeonService.mayTarget(this,p)&&distanceToSqr(p)<distance&&hasLineOfSight(p)){nearest=p;distance=distanceToSqr(p);}setTarget(nearest);}
        var target=getTarget();if(target!=null&&attachCooldown==0){Vec3 direction=target.position().add(0,1.3,0).subtract(position());if(horizontalCollision)setDeltaMovement(getDeltaMovement().add(0,.08,0));else setDeltaMovement(getDeltaMovement().scale(.8).add(direction.normalize().scale(.055)));if(target instanceof ServerPlayer p&&direction.lengthSqr()<1.2)attach(p);}
        else setDeltaMovement(getDeltaMovement().scale(.9).add(0,Math.sin(tickCount*.08)*.004,0));
        if(tickCount%65==0)playSound(SoundEvents.BEE_LOOP_AGGRESSIVE,.18f,.6f);
    }
    public int drain(ServerPlayer player){var mana=com.tnc.tnc.magic.MagicStone.getOrNull(player);if(mana==null)return 0;int amount=Math.min(supplyState()==SupplyState.CUT?4:8,mana.getMana());if(amount<=0)return 0;mana.addMana(-amount);com.tnc.tnc.network.MagicStoneNetwork.syncTo(player);ManaSightServer.record(player.serverLevel(),player.getEyePosition(),position().add(0,.35,0),amount,0x8affef,0);return amount;}
    @Override public boolean hurt(DamageSource source,float amount){
        boolean fire=source.is(DamageTypeTags.IS_FIRE);
        if(!fire&&source.getEntity() instanceof ServerPlayer player&&!ManaSightServer.enabled(player)&&exposedTicks()<=0)return false;
        if(!fire&&source.getEntity()!=null&&!(source.getEntity() instanceof ServerPlayer))return false;
        if(fire||amount>0)dislodge();return super.hurt(source,amount);
    }
    @Override public boolean isPickable(){return super.isPickable()&&(!level().isClientSide||com.tnc.tnc.dungeon.ecology.client.EcologyClient.canPickSiphon(this));}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canCollideWith(Entity entity){return false;}
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public boolean applyPotion(ServerPlayer player,String potion){if(EcologyHooks.firePotion(potion)&&canReceivePotion(player)){dislodge();return super.hurt(player.damageSources().indirectMagic(player,player),5);}return super.applyPotion(player,potion);}
    @Override public void prepareForCage(){super.prepareForCage();host=null;action(0);}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(host!=null)tag.putUUID("EcologyHost",host);tag.putInt("EcologyAttachCooldown",attachCooldown);tag.putInt("EcologyExposed",exposedTicks());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);host=tag.hasUUID("EcologyHost")?tag.getUUID("EcologyHost"):null;attachCooldown=Math.max(0,Math.min(100,tag.getInt("EcologyAttachCooldown")));entityData.set(EXPOSED,Math.max(0,Math.min(100,tag.getInt("EcologyExposed"))));setNoGravity(true);}
}
