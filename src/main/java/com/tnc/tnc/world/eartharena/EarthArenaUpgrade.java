package com.tnc.tnc.world.eartharena;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One-time upgrade of already explored terrain, capped by work and time per server tick. */
@Mod.EventBusSubscriber(modid="tnc")
public final class EarthArenaUpgrade {
    private static final Map<LevelChunk,Job> JOBS = new LinkedHashMap<>();
    private static final Map<java.util.UUID,Long> FALL_GRACE = new java.util.HashMap<>();
    public static boolean safeLanding(net.minecraft.server.level.ServerPlayer player){
        Long until=FALL_GRACE.get(player.getUUID());
        if(until==null)return false;
        if(player.level().getGameTime()>until){FALL_GRACE.remove(player.getUUID());return false;}
        return true;
    }
    public static CompletableFuture<Void> ready(LevelChunk chunk) {
        if(EarthArenaChunkLedger.version(chunk)>=EarthArenaLayout.VERSION) return CompletableFuture.completedFuture(null);
        if(Math.hypot(chunk.getPos().getMiddleBlockX()-EarthArenaLayout.X,chunk.getPos().getMiddleBlockZ()-EarthArenaLayout.Z)>283) {
            mark(chunk);return CompletableFuture.completedFuture(null);
        }
        if(!JOBS.containsKey(chunk)&&matches(chunk)){mark(chunk);return CompletableFuture.completedFuture(null);}
        return JOBS.computeIfAbsent(chunk,Job::new).done;
    }
    private static void mark(LevelChunk chunk) { EarthArenaChunkLedger.mark(chunk,EarthArenaLayout.VERSION); }
    private static boolean matches(LevelChunk chunk){
        var pos=new BlockPos.MutableBlockPos();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int wx=chunk.getPos().getMinBlockX()+x,wz=chunk.getPos().getMinBlockZ()+z;
            if(Math.hypot(wx-EarthArenaLayout.X,wz-EarthArenaLayout.Z)>=270)continue;
            int top=EarthArenaTerrain.height(wx,wz,EarthArenaTerrain.DEFAULT_SEED);
            if(chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)!=top)return false;
            if(!chunk.getBlockState(pos.set(wx,top,wz)).equals(EarthArenaChunkGenerator.material(wx,top,wz,top,EarthArenaTerrain.layerOffset(wx,wz,EarthArenaTerrain.DEFAULT_SEED))))return false;
        }
        return true;
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && level.dimension().equals(EarthArena.KEY) && event.getChunk() instanceof LevelChunk chunk) {
            if(level.getServer().isSameThread())ready(chunk);else level.getServer().execute(()->ready(chunk));
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { JOBS.values().forEach(job->job.done.cancel(false));JOBS.clear();FALL_GRACE.clear(); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||JOBS.isEmpty())return;
        long until=System.nanoTime()+4_000_000;
        int budget=16384;
        while(budget>0&&!JOBS.isEmpty()&&System.nanoTime()<until) {
            var entry=JOBS.entrySet().iterator().next();Job job=entry.getValue();
            if(job.step()) { JOBS.remove(entry.getKey());job.finish(); }
            budget--;
        }
    }
    private static final class Job {
        final LevelChunk chunk;final CompletableFuture<Void> done=new CompletableFuture<>();
        final BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        int column, y=Integer.MIN_VALUE, bottom, top, offset, x,z;
        Job(LevelChunk chunk){this.chunk=chunk;}
        boolean step(){
            if(column>=256)return true;
            if(y==Integer.MIN_VALUE){
                x=chunk.getPos().getMinBlockX()+(column&15);z=chunk.getPos().getMinBlockZ()+(column>>4);
                if(Math.hypot(x-EarthArenaLayout.X,z-EarthArenaLayout.Z)>=270){column++;return false;}
                top=EarthArenaTerrain.height(x,z,EarthArenaTerrain.DEFAULT_SEED);
                int oldTop=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
                y=Math.max(top,oldTop);bottom=Math.min(oldTop,top-6);
                offset=EarthArenaTerrain.layerOffset(x,z,EarthArenaTerrain.DEFAULT_SEED);
            }
            var state=y>top?Blocks.AIR.defaultBlockState():EarthArenaChunkGenerator.material(x,y,z,top,offset);
            cursor.set(x,y,z);
            if(!chunk.getBlockState(cursor).equals(state))chunk.setBlockState(cursor,state,false);
            if(--y<bottom){column++;y=Integer.MIN_VALUE;}
            return column>=256;
        }
        void finish(){
            mark(chunk);ServerLevel level=(ServerLevel)chunk.getLevel();chunk.setLightCorrect(false);
            for(var player:level.players())if(player.chunkPosition().equals(chunk.getPos())){
                FALL_GRACE.put(player.getUUID(),level.getGameTime()+200);player.fallDistance=0;
            }
            level.getChunkSource().getLightEngine().lightChunk(chunk,false).whenCompleteAsync((ignored,error)->{
                if(error!=null){done.completeExceptionally(error);return;}
                var packet=new ClientboundLevelChunkWithLightPacket(chunk,level.getLightEngine(),null,null);
                level.getChunkSource().chunkMap.getPlayers(chunk.getPos(),false).forEach(player->player.connection.send(packet));
                done.complete(null);
            },level.getServer());
        }
    }
}
