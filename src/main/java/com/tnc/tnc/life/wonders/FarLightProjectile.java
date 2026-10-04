package com.tnc.tnc.life.wonders;

import com.tnc.tnc.life.botanical.BotanicalContent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public final class FarLightProjectile extends ThrowableItemProjectile {
    public FarLightProjectile(EntityType<? extends FarLightProjectile> type,Level level){super(type,level);}
    public FarLightProjectile(Level level,LivingEntity owner){super(WonderContent.FARLIGHT,owner,level);}
    @Override protected Item getDefaultItem(){return BotanicalContent.PRODUCTS.get("farlight_fruit");}
    @Override protected void onHit(HitResult hit){
        if(level().isClientSide)return;
        if(getOwner() instanceof ServerPlayer player){
            boolean begun=LightBloomData.get(player.serverLevel()).begin(player,net.minecraft.core.BlockPos.containing(hit.getLocation()).above());
            if(!begun&&!player.isCreative()){
                ItemStack refund=new ItemStack(getDefaultItem());if(!player.getInventory().add(refund))player.drop(refund,false);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("这里没有获准的空地可照亮；远照果已退回。"),true);
            }
        }
        discard();
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>200){if(getOwner() instanceof ServerPlayer p&&!p.isCreative()){var refund=new ItemStack(getDefaultItem());if(!p.getInventory().add(refund))p.drop(refund,false);}discard();}}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
