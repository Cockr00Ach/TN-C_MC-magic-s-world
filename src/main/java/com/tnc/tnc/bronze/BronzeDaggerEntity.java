package com.tnc.tnc.bronze;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Visual projectile owns no recoverable item. The original stack never leaves inventory. */
public final class BronzeDaggerEntity extends ThrowableItemProjectile {
    private float damage;private boolean returning;private long flightToken;private Vec3 origin;
    public BronzeDaggerEntity(EntityType<? extends BronzeDaggerEntity> type,Level level){super(type,level);}
    public static void launch(ServerPlayer p,ItemStack stack,int charge){
        if(!BronzeTwinWeapon.canUse(p,stack))return;
        var entity=new BronzeDaggerEntity(BronzeWeapons.DAGGER.get(),p.level());entity.setOwner(p);
        entity.setPos(p.getX(),p.getEyeY()-.15,p.getZ());entity.origin=entity.position();
        entity.flightToken=p.level().getGameTime()+60;
        entity.damage=(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE)*BronzeProfile.throwMultiplier(charge);
        entity.setItem(stack.copyWithCount(1));entity.shootFromRotation(p,p.getXRot(),p.getYRot(),0,1.1f+BronzeProfile.charge(charge)*1.1f,.3f);
        if(p.level().addFreshEntity(entity)){
            stack.getOrCreateTag().putLong(BronzeWeaponItem.FLIGHT,entity.flightToken);
            stack.getOrCreateTag().putUUID(BronzeWeaponItem.FLIGHT_ID,entity.getUUID());
            stack.hurtAndBreak(1,p,e->e.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
            p.level().playSound(null,p.blockPosition(),SoundEvents.TRIDENT_THROW,SoundSource.PLAYERS,.8f,.8f+BronzeProfile.charge(charge)*.3f);
        }
    }
    @Override protected Item getDefaultItem(){return BronzeWeapons.WEAPONS.get(BronzeProfile.DAGGER).get();}
    @Override protected float getGravity(){return returning?0:.025f;}
    @Override protected void onHitEntity(EntityHitResult hit){
        if(level().isClientSide||returning)return;
        if(getOwner() instanceof ServerPlayer player&&!pairedGripValid(player)){returning=true;return;}
        if(getOwner() instanceof ServerPlayer p&&hit.getEntity() instanceof LivingEntity target&&com.tnc.tnc.warrior.WarriorBronzeBridge.validTarget(p,target)){
            float bonus=EnchantmentHelper.getDamageBonus(getItem(),target.getMobType());
            if(target.hurt(damageSources().thrown(this,p),damage+bonus)){
                target.knockback(.35,p.getX()-target.getX(),p.getZ()-target.getZ());target.hurtMarked=true;
                int fire=EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT,getItem());if(fire>0)target.setSecondsOnFire(fire*4);
                com.tnc.tnc.warrior.WarriorBronzeBridge.impact(p,target,damage>=p.getAttributeValue(Attributes.ATTACK_DAMAGE)*1.9);
                level().playSound(null,blockPosition(),SoundEvents.TRIDENT_HIT,SoundSource.PLAYERS,.8f,.75f);
            }
        }
        returning=true;
    }
    @Override protected void onHitBlock(BlockHitResult hit){if(!level().isClientSide){returning=true;level().playSound(null,blockPosition(),SoundEvents.TRIDENT_HIT_GROUND,SoundSource.PLAYERS,.6f,.8f);}}
    @Override public void tick(){
        if(!level().isClientSide){
            if(!(getOwner() instanceof ServerPlayer p)||!p.isAlive()||p.isRemoved()||p.level()!=level()||tickCount>=58){finish();return;}
            if(!pairedGripValid(p))returning=true;
            if(origin==null)origin=position();if(position().distanceToSqr(origin)>18*18||tickCount>=22)returning=true;
            if(returning){noPhysics=true;setNoGravity(true);Vec3 delta=p.getEyePosition().add(0,-.3,0).subtract(position());
                if(delta.lengthSqr()<1){finish();return;}setDeltaMovement(delta.normalize().scale(1.65));}
        }
        super.tick();
        if(level().isClientSide)level().addParticle(net.minecraft.core.particles.ParticleTypes.WAX_OFF,getX(),getY(),getZ(),0,0,0);
    }
    private boolean pairedGripValid(ServerPlayer player){
        if(!BronzeTwinWeapon.isPair(getItem()))return true;
        var held=player.getMainHandItem();
        return BronzeTwinWeapon.isPair(held)&&player.getOffhandItem().isEmpty()&&held.hasTag()&&held.getTag().hasUUID(BronzeWeaponItem.FLIGHT_ID)&&held.getTag().getUUID(BronzeWeaponItem.FLIGHT_ID).equals(getUUID());
    }
    private void finish(){
        if(getOwner() instanceof ServerPlayer p)for(int i=0;i<p.getInventory().getContainerSize();i++){
            ItemStack stack=p.getInventory().getItem(i);
            if(stack.getItem() instanceof BronzeWeaponItem&&stack.hasTag()&&stack.getTag().hasUUID(BronzeWeaponItem.FLIGHT_ID)&&stack.getTag().getUUID(BronzeWeaponItem.FLIGHT_ID).equals(getUUID())){stack.getTag().remove(BronzeWeaponItem.FLIGHT);stack.getTag().remove(BronzeWeaponItem.FLIGHT_ID);}
        }
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("BronzeDamage",damage);tag.putBoolean("Returning",returning);tag.putLong("FlightToken",flightToken);tag.putInt("FlightAge",tickCount);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);damage=tag.getFloat("BronzeDamage");returning=true;flightToken=tag.getLong("FlightToken");tickCount=tag.getInt("FlightAge");}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
