package com.tnc.tnc.life.routes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
public final class MagicSoilBlock extends Block implements EntityBlock {
    final boolean source;
    public MagicSoilBlock(boolean source){super(BlockBehaviour.Properties.copy(Blocks.DIRT).lightLevel(s->source?3:1));this.source=source;}
    @Override public boolean canSustainPlant(BlockState s,BlockGetter l,BlockPos p,net.minecraft.core.Direction d,net.minecraftforge.common.IPlantable plant){return true;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return source?new MagicSoilEntity(p,s):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide&&source&&t==RouteContent.SOIL_ENTITY.get()?(world,p,state,be)->MagicSoilEntity.tick((net.minecraft.server.level.ServerLevel)world,p,(MagicSoilEntity)be):null;}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity who,ItemStack stack){if(who!=null&&l.getBlockEntity(p) instanceof MagicSoilEntity be){be.owner=who.getUUID();be.next=l.getGameTime()+200;be.setChanged();}}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){if(random.nextInt(source?5:35)==0)level.addParticle(new DustParticleOptions(new Vector3f(.22f,.85f,.72f),source?.55f:.35f),pos.getX()+random.nextDouble(),pos.getY()+1.03,pos.getZ()+random.nextDouble(),0,.007,0);}
    public static boolean isSoil(BlockState state){return state.getBlock() instanceof MagicSoilBlock;}
    public static boolean irrigated(Level level,BlockPos soil){for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++)for(int dy=0;dy<=1;dy++){BlockPos p=soil.offset(dx,dy,dz);if(level.hasChunkAt(p)&&level.getFluidState(p).getFluidType()==RouteContent.MANA_TYPE.get())return true;}return false;}
    public static boolean growing(Level level,BlockPos root){return isSoil(level.getBlockState(root.below()))&&irrigated(level,root.below());}
}
