package com.tnc.tnc.life.pasture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

public final class PastureFacilityBlock extends BaseEntityBlock {
    public enum Kind{TROUGH,MARKER,TRAY,EGGS,DEW,CHARGING,BOTTLE,LIGHT}
    private final Kind kind;
    public PastureFacilityBlock(Kind kind){super(kind==Kind.LIGHT?BlockBehaviour.Properties.of().noCollission().noOcclusion().noLootTable().lightLevel(s->10):BlockBehaviour.Properties.of().strength(1.8f).sound(SoundType.WOOD).noOcclusion());this.kind=kind;}
    public Kind kind(){return kind;}
    @Override public RenderShape getRenderShape(BlockState state){return kind==Kind.LIGHT?RenderShape.INVISIBLE:RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,CollisionContext context){return kind==Kind.LIGHT?Shapes.empty():kind==Kind.MARKER?Block.box(6,0,6,10,14,10):kind==Kind.CHARGING?Block.box(2,0,2,14,15,14):Block.box(1,0,1,15,8,15);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PastureFacilityEntity(pos,state);}
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,PastureRegistry.FACILITY_ENTITY,PastureFacilityEntity::tick);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack){if(placer instanceof ServerPlayer player&&level.getBlockEntity(pos) instanceof PastureFacilityEntity facility)facility.claim(player.getUUID());}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(player.getItemInHand(hand).getItem() instanceof PastureStaffItem||player.getItemInHand(hand).getItem() instanceof ManaBottleItem)return InteractionResult.PASS;if(level.isClientSide)return InteractionResult.SUCCESS;if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof PastureFacilityEntity facility)return facility.use(server,player.getItemInHand(hand));return InteractionResult.PASS;}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState newState,boolean moving){if(state.getBlock()!=newState.getBlock()&&level.getBlockEntity(pos) instanceof PastureFacilityEntity facility){Containers.dropContents(level,pos,facility);level.updateNeighbourForOutputSignal(pos,this);}super.onRemove(state,level,pos,newState,moving);}
}
