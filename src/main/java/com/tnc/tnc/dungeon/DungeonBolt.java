package com.tnc.tnc.dungeon;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.effect.*;
import org.joml.Vector3f;
public final class DungeonBolt extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(DungeonBolt.class,EntityDataSerializers.INT);
    private float damage=5;private int effect;
    public DungeonBolt(EntityType<? extends DungeonBolt> t,Level l){super(t,l);}
    @Override protected void defineSynchedData(){entityData.define(COLOR,0x67d8cf);}
    public int color(){return entityData.get(COLOR);}public void configure(int color,float damage,int effect){entityData.set(COLOR,color);this.damage=damage;this.effect=effect;}
    @Override protected float getGravity(){return 0;}
    @Override public void tick(){super.tick();if(tickCount>65){discard();return;}if(level().isClientSide){int c=color();level().addParticle(new DustParticleOptions(new Vector3f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f),1),getX(),getY(),getZ(),0,0,0);}}
    @Override protected boolean canHitEntity(Entity e){return e instanceof net.minecraft.world.entity.player.Player&&super.canHitEntity(e);}
    @Override protected void onHitEntity(EntityHitResult hit){if(!level().isClientSide&&hit.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&getOwner() instanceof DungeonMob mob&&DungeonService.mayTarget(mob,p)){p.hurt(damageSources().indirectMagic(this,getOwner()),damage);if(effect==1)p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,45,0));if(effect==2)p.addEffect(new MobEffectInstance(MobEffects.POISON,35,0));}discard();}
    @Override protected void onHitBlock(BlockHitResult h){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putInt("Color",color());t.putFloat("Damage",damage);t.putInt("Effect",effect);}
    @Override protected void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);configure(t.getInt("Color"),t.getFloat("Damage"),t.getInt("Effect"));}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
