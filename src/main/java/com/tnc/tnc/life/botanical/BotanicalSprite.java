package com.tnc.tnc.life.botanical;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** A saved independent entity, not a particle. Its mother and slot survive restart. */
public final class BotanicalSprite extends Entity {
    private static final EntityDataAccessor<Integer> SLOT=SynchedEntityData.defineId(BotanicalSprite.class,EntityDataSerializers.INT);
    public UUID mother;
    public BlockPos home=BlockPos.ZERO;
    public int slot;
    public long night;
    private int stuck;
    public BotanicalSprite(EntityType<? extends BotanicalSprite> type,Level level){super(type,level);noPhysics=true;}
    public void bind(UUID mother,BlockPos home,int slot,long night){this.mother=mother;this.home=home.immutable();this.slot=slot;this.night=night;entityData.set(SLOT,slot);}
    public int variant(){return entityData.get(SLOT);}
    @Override protected void defineSynchedData(){entityData.define(SLOT,0);}
    @Override public void tick(){super.tick();if(level().isClientSide)return;var l=(ServerLevel)level();if(!l.hasChunkAt(home))return;if(!(l.getBlockEntity(home) instanceof BotanicalPlantEntity plant)||mother==null||!mother.equals(plant.motherId)||!plant.getBlockState().is(BotanicalContent.BLOCKS.get("star_rest"))){discard();return;}
        if(l.isDay()||stuck>=600){plant.spriteReturned(slot,night);discard();return;}
        double angle=(l.getGameTime()+slot*61)*.027;Vec3 target=new Vec3(home.getX()+.5+Math.cos(angle)*(1.7+slot*.6),home.getY()+.9+Math.sin(angle*.6+slot)*.45,home.getZ()+.5+Math.sin(angle)*(1.7+slot*.6));BlockPos at=BlockPos.containing(target);if(!l.hasChunkAt(at))return;if(!l.getBlockState(at).isAir()){stuck++;return;}stuck=0;setPos(target.x,target.y,target.z);setYRot((float)(-angle*180/Math.PI));if(tickCount%10==0)l.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,getX(),getY(),getZ(),1,0,0,0,0);
    }
    @Override public boolean hurt(DamageSource source,float amount){if(!level().isClientSide)discard();return true;}
    @Override protected void readAdditionalSaveData(CompoundTag t){mother=t.hasUUID("Mother")?t.getUUID("Mother"):null;home=BlockPos.of(t.getLong("Home"));slot=Math.max(0,Math.min(2,t.getInt("Slot")));night=t.getLong("Night");stuck=Math.max(0,Math.min(600,t.getInt("Stuck")));entityData.set(SLOT,slot);}
    @Override protected void addAdditionalSaveData(CompoundTag t){if(mother!=null)t.putUUID("Mother",mother);t.putLong("Home",home.asLong());t.putInt("Slot",slot);t.putLong("Night",night);t.putInt("Stuck",stuck);}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
