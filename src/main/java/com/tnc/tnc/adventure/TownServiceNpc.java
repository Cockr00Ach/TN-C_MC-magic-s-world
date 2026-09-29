package com.tnc.tnc.adventure;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.nbt.CompoundTag;

/** Public town workers are separate from story actors and relationship residents. */
public final class TownServiceNpc extends Villager {
    private String role="";
    public TownServiceNpc(EntityType<? extends Villager> type,Level level){super(type,level);setNoAi(true);setInvulnerable(true);setPersistenceRequired();setAge(0);}
    public String role(){return role;}
    public void role(String value){role=value;}
    @Override public boolean isPushable(){return false;}
    @Override public boolean canChangeDimensions(){return false;}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){
        if(hand==InteractionHand.MAIN_HAND&&player instanceof net.minecraft.server.level.ServerPlayer p)TownServices.open(p,this);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putString("TncServiceRole",role);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);role=t.getString("TncServiceRole");setNoAi(true);setInvulnerable(true);setAge(0);}
}
