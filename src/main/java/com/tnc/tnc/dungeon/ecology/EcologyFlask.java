package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.arcana.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** The actual potion stack travels with the projectile and survives save/load. */
public final class EcologyFlask extends ThrowableItemProjectile {
    public EcologyFlask(EntityType<? extends EcologyFlask> type,Level level){super(type,level);}
    @Override protected Item getDefaultItem(){return ArcanaContent.item("capture_shrink_elixir");}
    public String potion(){return getItem().getItem() instanceof ArcanaItem item?item.id:"capture_shrink_elixir";}
    @Override protected void onHit(HitResult hit){
        if(level() instanceof ServerLevel level){if(getOwner() instanceof ServerPlayer player&&player.isAlive()&&player.level()==level){EcologyHooks.splash(player,hit.getLocation(),potion());var at=hit.getLocation();level.sendParticles(EcologyHooks.firePotion(potion())?ParticleTypes.FLAME:ParticleTypes.ENCHANT,at.x,at.y,at.z,28,.7,.35,.7,.04);level.playSound(null,at.x,at.y,at.z,net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1);}discard();}
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>200)discard();}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
