package com.tnc.tnc.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Explicit operator-only static blueprint placement; never a world-generation feature. */
@Mod.EventBusSubscriber(modid="tnc")
public final class AurelianBlueprint {
 private static final Map<MinecraftServer,Job> JOBS=new WeakHashMap<>();
 private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_aurelian",Comparator.comparingLong(ChunkPos::toLong),40);
 private static class Job {ServerLevel level;BlockPos origin;UUID owner;List<BlockState> palette;int[] blocks;int cursor;boolean placing;ChunkPos ticket;}
 @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){
  e.getDispatcher().register(Commands.literal("aurelian").requires(s->s.hasPermission(2))
   .then(Commands.literal("summon").then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(c->{
    var p=c.getSource().getPlayerOrException();var s=p.server;if(JOBS.containsKey(s)){c.getSource().sendFailure(Component.literal("巨龙正在检查或生成，请等待，或使用 /aurelian cancel"));return 0;}
    var pos=BlockPosArgument.getBlockPos(c,"origin");var l=p.serverLevel();
    if(pos.getY()<l.getMinBuildHeight()||pos.getY()+225>l.getMaxBuildHeight()||!l.getWorldBorder().isWithinBounds(pos)||!l.getWorldBorder().isWithinBounds(pos.offset(299,224,288))){c.getSource().sendFailure(Component.literal("需要300×225×289格空间；当前高度或边界不足。建议在空旷处用Y=64。"));return 0;}
    try(var in=AurelianBlueprint.class.getResourceAsStream("/data/tnc/blueprints/aurelian.nbt")){
     var n=NbtIo.readCompressed(Objects.requireNonNull(in));var j=new Job();j.level=l;j.origin=pos;j.owner=p.getUUID();j.palette=new ArrayList<>();
     for(var raw:n.getList("Palette",10)){var tag=(CompoundTag)raw;var id=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Name"));if(id==null||!l.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))throw new IllegalStateException("缺少蓝图方块");j.palette.add(NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),tag));}
     j.blocks=n.getIntArray("Blocks");JOBS.put(s,j);tell(j,"开始检查巨龙建筑位置；不会清空整片区域。检查通过后分批放置。底角："+pos.toShortString());return 1;
    }catch(Exception ex){c.getSource().sendFailure(Component.literal("蓝图读取失败："+ex.getMessage()));return 0;}
   })))
   .then(Commands.literal("cancel").executes(c->{var j=JOBS.remove(c.getSource().getServer());if(j!=null){release(j);tell(j,"已停止；已经放置的方块保留。");}return 1;})));
 }
 private static void tell(Job j,String text){var p=j.level.getServer().getPlayerList().getPlayer(j.owner);if(p!=null)p.sendSystemMessage(Component.literal(text));}
 private static void release(Job j){if(j.ticket!=null)j.level.getChunkSource().removeRegionTicket(TICKET,j.ticket,2,j.ticket);}
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
  if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var j=JOBS.get(e.getServer());if(j==null)return;
  long end=System.nanoTime()+2_000_000;int budget=512;
  while(j.cursor<j.blocks.length&&budget-->0&&System.nanoTime()<end){
   int i=j.cursor;var p=j.origin.offset(j.blocks[i],j.blocks[i+1],j.blocks[i+2]);var chunk=new ChunkPos(p);
   if(!chunk.equals(j.ticket)){release(j);j.ticket=chunk;}
   j.level.getChunkSource().addRegionTicket(TICKET,chunk,2,chunk);
   if(j.level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null)return;
   var old=j.level.getBlockState(p);var target=j.palette.get(j.blocks[i+3]);
   if((!old.isAir()&&!old.canBeReplaced())||j.level.getBlockEntity(p)!=null||com.tnc.tnc.home.HousingService.intersectsOwned(j.level,p,new Vec3i(1,1,1))){
    tell(j,"位置被占用，停止以保护原建筑："+p.toShortString()+"。请换一处空旷位置。");release(j);JOBS.remove(e.getServer());return;
   }
   if(j.placing)j.level.setBlock(p,target,18);j.cursor+=4;
  }
  if(j.cursor>=j.blocks.length){if(!j.placing){j.placing=true;j.cursor=0;tell(j,"位置检查通过，正在生成157801个方块。");}else{tell(j,"加隆·奥瑞利安已生成。它是静态建筑，不是活动生物。");release(j);JOBS.remove(e.getServer());}}
 }
 @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){var j=JOBS.remove(e.getServer());if(j!=null)release(j);}
 private AurelianBlueprint(){}
}
