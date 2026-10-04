package com.tnc.tnc.life.wonders;

import java.util.*;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

/** A saved physical climbing line. Taking it back never removes a replacement block. */
public final class WonderRopes {
    public static InteractionResult use(UseOnContext c,int length){
        if(!(c.getPlayer() instanceof ServerPlayer player))return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        ItemStack stack=c.getItemInHand();var tag=stack.getOrCreateTag();var level=player.serverLevel();
        if(tag.contains("RopeNodes")){
            if(tag.hasUUID("RopeOwner")&&!player.getUUID().equals(tag.getUUID("RopeOwner"))){player.displayClientMessage(net.minecraft.network.chat.Component.literal("这根绳还挂在原主人那里，请原主人先收回。"),true);return InteractionResult.FAIL;}
            if(!player.isShiftKeyDown()){player.displayClientMessage(net.minecraft.network.chat.Component.literal("这根绳已展开，潜行右键收回。"),true);return InteractionResult.CONSUME;}
            if(!tag.getString("RopeDimension").equals(level.dimension().location().toString()))return InteractionResult.FAIL;
            UUID id=tag.hasUUID("RopeId")?tag.getUUID("RopeId"):null;
            var remaining=new ArrayList<Long>();
            for(long v:tag.getLongArray("RopeNodes")){
                BlockPos p=BlockPos.of(v);if(!level.hasChunkAt(p)){remaining.add(v);continue;}
                if(level.getBlockEntity(p) instanceof RopeNodeEntity node&&Objects.equals(id,node.line)){
                    if(!player.getUUID().equals(node.owner)){remaining.add(v);continue;}
                    if(TownProtection.denied(player,p)){remaining.add(v);continue;}level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
                }
            }
            if(remaining.isEmpty()){tag.remove("RopeNodes");tag.remove("RopeId");tag.remove("RopeDimension");tag.remove("RopeOwner");}
            else tag.putLongArray("RopeNodes",remaining);
            return InteractionResult.CONSUME;
        }
        BlockPos start=c.getClickedPos().relative(c.getClickedFace());var sites=new ArrayList<BlockPos>();
        for(int n=0;n<length;n++){
            BlockPos p=start.below(n);if(!level.hasChunkAt(p)||!level.isInWorldBounds(p)||!level.getBlockState(p).isAir())break;
            if(TownProtection.denied(player,p)||!level.mayInteract(player,p)||net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level,p,level.getBlockState(p),player)))break;
            sites.add(p.immutable());
        }
        if(sites.isEmpty())return InteractionResult.FAIL;
        UUID id=UUID.randomUUID();var placed=new ArrayList<Long>();
        for(var p:sites){level.setBlock(p,WonderContent.ROPE.defaultBlockState(),3);if(level.getBlockEntity(p) instanceof RopeNodeEntity node){node.line=id;node.owner=player.getUUID();node.setChanged();placed.add(p.asLong());}}
        tag.putUUID("RopeId",id);tag.putLongArray("RopeNodes",placed);tag.putString("RopeDimension",level.dimension().location().toString());
        tag.putUUID("RopeOwner",player.getUUID());
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("已展开 "+placed.size()+" 格攀绳，可攀爬；潜行右键收回。"),true);return InteractionResult.CONSUME;
    }
}
