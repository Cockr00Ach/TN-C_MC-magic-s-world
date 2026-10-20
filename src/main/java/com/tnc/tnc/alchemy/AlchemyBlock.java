package com.tnc.tnc.alchemy;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class AlchemyBlock extends BaseEntityBlock {
    public AlchemyBlock(){super(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2f,6f).sound(SoundType.METAL).noOcclusion().lightLevel(s->5));}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(1,0,1,15,13,15);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new AlchemyEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide?null:createTickerHelper(t,AlchemyContent.ENTITY,AlchemyEntity::tick);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(l.getBlockEntity(pos) instanceof AlchemyEntity be){if(player instanceof net.minecraft.server.level.ServerPlayer p){if(!be.mayUse(p))return InteractionResult.FAIL;var held=player.getItemInHand(hand);if(held.is(net.minecraft.world.item.Items.FLINT_AND_STEEL)||held.is(net.minecraft.world.item.Items.FIRE_CHARGE)){be.ignite(false);if(!p.isCreative()){if(held.is(net.minecraft.world.item.Items.FIRE_CHARGE))held.shrink(1);else held.hurtAndBreak(1,p,q->q.broadcastBreakEvent(hand));}}else net.minecraftforge.network.NetworkHooks.openScreen(p,be,pos);}return InteractionResult.sidedSuccess(l.isClientSide);}return InteractionResult.PASS;}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.LivingEntity e,net.minecraft.world.item.ItemStack stack){if(!l.isClientSide&&l.getBlockEntity(p) instanceof AlchemyEntity be){be.owner=e==null?null:e.getUUID();be.setChanged();}}
    @Override public void playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){if(!l.isClientSide&&player.isCreative()&&l.getBlockEntity(p) instanceof AlchemyEntity be){Containers.dropContents(l,p,be.inventory);be.inventory.clearContent();}super.playerWillDestroy(l,p,s,player);}
}
