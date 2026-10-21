package com.tnc.tnc.arcana;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
public final class DimensionalSpirit extends Monster {
    public UUID owner;
    public DimensionalSpirit(EntityType<? extends Monster> t,Level l){super(t,l);setNoGravity(true);setNoAi(true);noPhysics=true;setPersistenceRequired();}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override public boolean canCollideWith(Entity e){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isPickable(){return !level().isClientSide||com.tnc.tnc.arcana.client.ArcanaClient.spiritVisible();}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){if(source.getEntity() instanceof ServerPlayer p){var mana=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(mana!=null){ArcanaPhysiology.data(p).remove("MealSunUntil");mana.setManaFreedom(false);mana.setMana(0);com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);}p.teleportTo(p.serverLevel(),p.getX(),Math.min(p.serverLevel().getMaxBuildHeight()-5,p.getY()+100),p.getZ(),p.getYRot(),p.getXRot());p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,900,0));}return false;}
    @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){if(p instanceof ServerPlayer sp)ArcanaSpirits.open(sp,this);return InteractionResult.sidedSuccess(level().isClientSide);}
    @Override public void tick(){super.tick();if(tickCount%200==0&&level() instanceof net.minecraft.server.level.ServerLevel l){var p=owner==null?null:l.getServer().getPlayerList().getPlayer(owner);if(p==null||p.serverLevel()!=l||ArcanaPhysiology.data(p).getDouble("vision_sanity")<70)discard();}}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);if(owner!=null)t.putUUID("SpiritOwner",owner);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);owner=t.hasUUID("SpiritOwner")?t.getUUID("SpiritOwner"):null;}
}
