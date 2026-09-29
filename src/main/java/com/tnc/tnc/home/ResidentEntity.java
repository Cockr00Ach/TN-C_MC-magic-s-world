package com.tnc.tnc.home;

import com.tnc.tnc.adventure.AdventureSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Separate adult villagers, never the story actors or MCA's replacement inhabitants. */
public final class ResidentEntity extends Villager {
    private String residentId="";
    public ResidentEntity(EntityType<? extends Villager> type,Level level){super(type,level);setNoAi(true);setInvulnerable(true);setPersistenceRequired();setAge(0);}
    public void identity(String id){residentId=id;}
    public String identity(){return residentId;}
    @Override public boolean canChangeDimensions(){return false;}
    @Override public boolean isPushable(){return false;}
    @Override public InteractionResult mobInteract(Player p,InteractionHand hand){
        if(hand==InteractionHand.MAIN_HAND&&p instanceof net.minecraft.server.level.ServerPlayer s)ResidentService.open(s,this);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putString("TncResident",residentId);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);residentId=t.getString("TncResident");setNoAi(true);setInvulnerable(true);setAge(0);}
    @Override public void tick(){
        super.tick();if(level() instanceof ServerLevel l&&tickCount%100==0&&!residentId.isEmpty()){
            var data=AdventureSavedData.get(l.getServer());var records=data.housing.getCompound("Residents");var record=records.getCompound(residentId);
            if(record.hasUUID("UUID")&&record.getUUID("UUID").equals(getUUID())){record.putLong("Pos",blockPosition().asLong());data.setDirty();}
        }
    }
}
