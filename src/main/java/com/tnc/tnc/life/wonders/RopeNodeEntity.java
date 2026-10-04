package com.tnc.tnc.life.wonders;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class RopeNodeEntity extends BlockEntity {
    public UUID line,owner;
    public RopeNodeEntity(BlockPos p,BlockState s){super(WonderContent.ROPE_ENTITY,p,s);}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(line!=null)t.putUUID("Line",line);if(owner!=null)t.putUUID("Owner",owner);}
    @Override public void load(CompoundTag t){super.load(t);line=t.hasUUID("Line")?t.getUUID("Line"):null;owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;}
}
