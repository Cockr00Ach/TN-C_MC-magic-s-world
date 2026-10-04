package com.tnc.tnc.life.wonders;

import java.util.UUID;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SoundRelayEntity extends BlockEntity {
    private UUID owner;
    private int pitch=6;
    private boolean bell;
    private long nextPulse,powerUntil;
    public SoundRelayEntity(BlockPos p,BlockState s){super(WonderContent.RELAY_ENTITY,p,s);}
    public void setOwner(UUID id){owner=id;setChanged();}
    public boolean mayUse(ServerPlayer p){return owner==null||owner.equals(p.getUUID())||p.isCreative();}
    public boolean hasBell(){return bell;}
    public int pitch(){return pitch;}
    public void installBell(){bell=true;setChanged();}
    public void removeBell(){bell=false;setChanged();}
    public void tune(){pitch=(pitch+1)%25;setChanged();}
    public boolean hear(ServerLevel l,int note){return note==pitch&&pulse(l);}
    public boolean pulse(ServerLevel l){
        if(owner==null||l.getGameTime()<nextPulse||TownProtection.hazard(l,worldPosition)&&TownProtection.denied(net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(owner,"sound_relay")),worldPosition))return false;
        nextPulse=l.getGameTime()+100;powerUntil=l.getGameTime()+10;
        l.setBlock(worldPosition,getBlockState().setValue(SoundRelayBlock.POWERED,true),3);l.updateNeighborsAt(worldPosition,WonderContent.RELAY);l.scheduleTick(worldPosition,WonderContent.RELAY,10);
        l.playSound(null,worldPosition,SoundEvents.NOTE_BLOCK_BELL.value(),SoundSource.BLOCKS,.8F,(float)Math.pow(2,(pitch-12)/12.0));
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,worldPosition.getX()+.5,worldPosition.getY()+1.1,worldPosition.getZ()+.5,1,pitch/24.0,0,0,0);setChanged();return true;
    }
    public void endPulse(ServerLevel l){if(l.getGameTime()<powerUntil){l.scheduleTick(worldPosition,WonderContent.RELAY,(int)(powerUntil-l.getGameTime()));return;}if(getBlockState().getValue(SoundRelayBlock.POWERED)){l.setBlock(worldPosition,getBlockState().setValue(SoundRelayBlock.POWERED,false),3);l.updateNeighborsAt(worldPosition,WonderContent.RELAY);}}
    @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l&&getBlockState().getValue(SoundRelayBlock.POWERED))l.scheduleTick(worldPosition,WonderContent.RELAY,(int)Math.max(1,powerUntil-l.getGameTime()));}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);t.putInt("Pitch",pitch);t.putBoolean("Bell",bell);t.putLong("NextPulse",nextPulse);t.putLong("PowerUntil",powerUntil);}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;pitch=Math.max(0,Math.min(24,t.getInt("Pitch")));bell=t.getBoolean("Bell");nextPulse=t.getLong("NextPulse");powerUntil=t.getLong("PowerUntil");}
}
