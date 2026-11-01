package com.tnc.tnc.world.ink;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Persistent painting gate; deliberate right-click avoids bounce loops at the destination. */
public final class InkPortalEntity extends Entity {
    public InkPortalEntity(EntityType<?> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){}
    @Override protected void readAdditionalSaveData(CompoundTag n){}
    @Override protected void addAdditionalSaveData(CompoundTag n){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean isPickable(){return true;}
    @Override public InteractionResult interact(Player player,InteractionHand hand){
        if(level().isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer p)||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player.isShiftKeyDown()){
            if(level().dimension().equals(InkRealmTravel.INK_REALM)){
                boolean started=InkEncounter.start(p);
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(started?"三相画卷展开……":"已有三相试炼正在进行。"),false);
                return InteractionResult.CONSUME;
            }
            if(player.isCreative()){discard();return InteractionResult.CONSUME;}
            return InteractionResult.PASS;
        }
        if(player.isPassenger()||player.isVehicle())return InteractionResult.FAIL;
        if(p.level().dimension().equals(InkRealmTravel.INK_REALM))InkRealmTravel.leave(p);
        else if(InkRealmTravel.enter(p)){
            // Give every arrival an exit, without overwriting any terrain.
            if(p.serverLevel().getEntitiesOfClass(InkPortalEntity.class,p.getBoundingBox().inflate(16)).isEmpty()&&!place(p)){
                InkRealmTravel.leave(p);
                p.displayClientMessage(net.minecraft.network.chat.Component.literal("画境出口空间不足，已返回原处。请换一片开阔位置放置画窗。"),false);
                return InteractionResult.FAIL;
            }
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("右键画窗返回；潜行右键画窗开启鹤 → 虎 → 龙三相试炼。"),false);
        }
        return InteractionResult.CONSUME;
    }
    public static boolean place(ServerPlayer player){
        var gate=InkBossRegistration.INK_PORTAL.create(player.serverLevel());if(gate==null)return false;
        var forward=player.getLookAngle().multiply(1,0,1).normalize().scale(4);
        gate.moveTo(player.getX()+forward.x,player.getY(),player.getZ()+forward.z,player.getYRot(),0);
        if(!player.level().getWorldBorder().isWithinBounds(gate.getBoundingBox())||!player.level().noCollision(gate,gate.getBoundingBox()))return false;
        return player.serverLevel().addFreshEntity(gate);
    }
}
