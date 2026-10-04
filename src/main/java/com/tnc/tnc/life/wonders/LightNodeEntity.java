package com.tnc.tnc.life.wonders;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class LightNodeEntity extends BlockEntity {
    private UUID bloom;
    private long expires;
    public LightNodeEntity(BlockPos p,BlockState s){super(WonderContent.LIGHT_ENTITY,p,s);}
    public void bind(UUID id,long until){bloom=id;expires=until;setChanged();}
    public UUID bloom(){return bloom;}
    public void expireIfDue(){if(level!=null&&!level.isClientSide&&(bloom==null||level.getGameTime()>=expires)&&level.getBlockEntity(worldPosition)==this)level.setBlock(worldPosition,Blocks.AIR.defaultBlockState(),3);}
    @Override public void onLoad(){super.onLoad();if(bloom!=null)expireIfDue();}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(bloom!=null)t.putUUID("Bloom",bloom);t.putLong("Expires",expires);}
    @Override public void load(CompoundTag t){super.load(t);bloom=t.hasUUID("Bloom")?t.getUUID("Bloom"):null;expires=t.getLong("Expires");}
}
