package com.tnc.tnc.skyacademy;

import com.tnc.tnc.world.SkyIslandWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Compare-before-write overlay: buildings, containers and player edits are never replaced. */
@Mod.EventBusSubscriber(modid="tnc")
public final class FirstSightPeak {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_first_sight_peak",Comparator.comparingLong(ChunkPos::toLong),40);
    private record Plan(List<BlockState> palette,int[] blocks){}
    private static final Map<MinecraftServer,Plan> PLANS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<ChunkPos>> TICKETS=new WeakHashMap<>();
    private static final class State extends SavedData {
        boolean done;int cursor;String error="";
        static State get(ServerLevel level){return level.getDataStorage().computeIfAbsent(t->{var s=new State();s.done=t.getBoolean("Done");return s;},State::new,"tnc_first_sight_peak_v2");}
        public CompoundTag save(CompoundTag t){t.putBoolean("Done",done);t.putString("Error",error);return t;}
    }
    public static boolean ready(MinecraftServer server){return State.get(SkyIslandWorlds.island(server)).done;}
    private static Plan load(ServerLevel level)throws Exception{
        CompoundTag tag;try(var in=FirstSightPeak.class.getResourceAsStream("/data/tnc/sky_academy/peak_v2.nbt")){tag=NbtIo.readCompressed(Objects.requireNonNull(in));}
        if(tag.getInt("Version")!=2)throw new java.io.IOException("Unknown peak revision");
        var palette=new ArrayList<BlockState>();for(var raw:tag.getList("Palette",10)){
            var t=(CompoundTag)raw;var id=net.minecraft.resources.ResourceLocation.parse(t.getString("Name"));
            if(!level.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))throw new java.io.IOException("Missing peak block "+id);
            var state=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),t);if(state.hasBlockEntity())throw new java.io.IOException("Peak contains a container");palette.add(state);
        }
        int[] a=tag.getIntArray("Changes");if(a.length%5!=0||a.length>2_500_000)throw new java.io.IOException("Invalid peak size");
        for(int i=0;i<a.length;i+=5)if(a[i]<200||a[i]>323||a[i+1]<70||a[i+1]>210||a[i+2]<15||a[i+2]>92||a[i+3]<0||a[i+3]>=palette.size()||a[i+4]<0||a[i+4]>=palette.size())throw new java.io.IOException("Peak outside reserved land");
        return new Plan(List.copyOf(palette),a);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var server=event.getServer();
        if(!SkyAcademyWorld.available(server))return;
        var level=SkyIslandWorlds.island(server);var s=State.get(level);if(s.done||!s.error.isEmpty())return;
        var origin=SkyAcademyWorld.origin(server);if(origin==null)return;
        try{
            var plan=PLANS.get(server);if(plan==null){plan=load(level);PLANS.put(server,plan);}
            var tickets=TICKETS.computeIfAbsent(server,k->{var chunks=new HashSet<ChunkPos>();for(int x=200;x<=323;x+=8)for(int z=15;z<=92;z+=8)chunks.add(new ChunkPos(origin.offset(x,0,z)));chunks.add(new ChunkPos(origin.offset(323,0,92)));return chunks;});
            if(server.getTickCount()%20==0||s.cursor==0)for(var chunk:tickets)level.getChunkSource().addRegionTicket(TICKET,chunk,2,chunk);
            if(tickets.stream().anyMatch(c->level.getChunkSource().getChunkNow(c.x,c.z)==null))return;
            long until=System.nanoTime()+2_000_000;int budget=384;
            while(s.cursor<plan.blocks.length&&budget-->0&&System.nanoTime()<until){
                int i=s.cursor;var p=origin.offset(plan.blocks[i],plan.blocks[i+1],plan.blocks[i+2]);var after=plan.palette.get(plan.blocks[i+4]);var actual=level.getBlockState(p);
                if(!actual.equals(after)){
                    if(!actual.equals(plan.palette.get(plan.blocks[i+3]))||level.getBlockEntity(p)!=null)throw new java.io.IOException("保留现场改动，山顶升级暂停于 "+p.toShortString());
                    if(level.players().stream().anyMatch(player->!player.isSpectator()&&p.closerToCenterThan(player.position(),5)))return;
                    level.setBlock(p,after,18);
                }s.cursor+=5;
            }
            if(s.cursor>=plan.blocks.length){level.getChunkSource().save(true);s.done=true;s.setDirty();release(server);com.mojang.logging.LogUtils.getLogger().info("[First Sight] Mountain platform raised 66 blocks; overlay complete");}
        }catch(Exception error){s.error=error.getMessage();s.setDirty();release(server);com.mojang.logging.LogUtils.getLogger().error("[First Sight] Peak update paused",error);}
    }
    private static void release(MinecraftServer server){for(var c:TICKETS.getOrDefault(server,Set.of()))SkyIslandWorlds.island(server).getChunkSource().removeRegionTicket(TICKET,c,2,c);TICKETS.remove(server);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent event){release(event.getServer());PLANS.remove(event.getServer());}
    private FirstSightPeak(){}
}
