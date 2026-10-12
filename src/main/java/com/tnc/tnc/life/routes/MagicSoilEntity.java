package com.tnc.tnc.life.routes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class MagicSoilEntity extends BlockEntity {
    public UUID owner;public long next;
    public MagicSoilEntity(BlockPos p,BlockState s){super(RouteContent.SOIL_ENTITY.get(),p,s);}
    public static void tick(ServerLevel l,BlockPos p,MagicSoilEntity be){if(l.getGameTime()<be.next)return;be.next=l.getGameTime()+200;int converted=0;
        if(!RouteAccess.allowed(l,p,be.owner))return;
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){var at=p.offset(dx,0,dz);if(RouteAccess.allowed(l,at,be.owner)&&l.getBlockState(at).is(Blocks.DIRT)&&l.getBlockEntity(at)==null){l.setBlock(at,RouteContent.SOIL.get().defaultBlockState(),3);converted++;}}
        if(converted>0){l.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW,p.getX()+.5,p.getY()+1.1,p.getZ()+.5,Math.min(12,converted),2,.01,2,0);l.playSound(null,p,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.BLOCKS,.3f,1.5f);}be.setChanged();
    }
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);t.putLong("Next",next);}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;next=t.getLong("Next");}
}
