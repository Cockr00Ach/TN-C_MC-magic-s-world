package com.tnc.tnc.arcana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** A physical, owner-authorized flask. Spilling never breaks machinery or planted crops. */
public final class ArcanaWasteFlask extends ThrowableItemProjectile {
    public ArcanaWasteFlask(EntityType<? extends ArcanaWasteFlask> type, Level level){super(type,level);}
    @Override protected Item getDefaultItem(){return ArcanaContent.item("strange_waste");}
    public static boolean corrode(ServerPlayer p, LivingEntity target){
        if(!(target instanceof Monster)||target instanceof DimensionalSpirit||target.getPersistentData().hasUUID("CageOwner")||p.isAlliedTo(target)||com.tnc.tnc.home.TownProtection.denied(p,target.blockPosition()))return false;
        target.addEffect(new MobEffectInstance(ArcanaContent.CORROSION,400));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,400,1));
        target.hurt(p.damageSources().indirectMagic(p,p),4);return true;
    }
    @Override protected void onHit(HitResult hit){
        if(!(level() instanceof ServerLevel l))return;
        if(getOwner() instanceof ServerPlayer p&&p.isAlive()&&p.serverLevel()==l){
            var at=hit.getLocation();
            for(var mob:l.getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(2.5)))if(mob.distanceToSqr(at)<6.25)corrode(p,mob);
            var center=BlockPos.containing(at);
            for(var pos:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,1,1)))if(l.hasChunkAt(pos)&&!com.tnc.tnc.home.TownProtection.denied(p,pos)){
                var b=l.getBlockState(pos);if(b.is(Blocks.GRASS)||b.is(Blocks.FERN)||b.is(Blocks.TALL_GRASS)||b.is(Blocks.LARGE_FERN))l.destroyBlock(pos,false);
            }
            l.sendParticles(ParticleTypes.WITCH,at.x,at.y,at.z,35,.65,.4,.65,.05);
            l.sendParticles(ParticleTypes.ITEM_SLIME,at.x,at.y,at.z,18,.5,.3,.5,.06);
            l.playSound(null,center,net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,.8f,.7f);
            com.tnc.tnc.mana.sight.ManaSightServer.record(l,at,at.add(0,2,0),8,0x96d366,4);
        }discard();
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>200)discard();}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
