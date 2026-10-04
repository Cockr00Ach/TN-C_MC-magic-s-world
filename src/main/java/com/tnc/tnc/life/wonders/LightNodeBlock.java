package com.tnc.tnc.life.wonders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Replaceable invisible carrier with a saved identity; cleanup cannot delete a player's new block. */
public final class LightNodeBlock extends BaseEntityBlock {
    public LightNodeBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).noCollission().noOcclusion().replaceable().strength(-1).lightLevel(s->15).noLootTable());}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new LightNodeEntity(p,s);}
    // A single saved queue removes at most 32 nodes per server tick. Individual
    // loaded nodes do not all change their light on the same expiry tick.
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide?null:createTickerHelper(type,WonderContent.LIGHT_ENTITY,(level,pos,state,node)->{if(level instanceof net.minecraft.server.level.ServerLevel server&&!LightBloomData.get(server).tracks(node.bloom()))node.expireIfDue();});}
}
