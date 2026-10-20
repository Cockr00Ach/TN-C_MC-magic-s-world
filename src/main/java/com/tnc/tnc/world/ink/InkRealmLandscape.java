package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Migration only: removes our obsolete V1 voxel scenery. New scenery is flat painting. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class InkRealmLandscape {
    private static final String KEY="TNCInkLandscapeFlatV2";
    private static final Set<Long> DONE=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<Long> LEGACY=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static Job job;
    private InkRealmLandscape() {}
    private static boolean ink(LevelChunk chunk) {
        return !chunk.getLevel().isClientSide && chunk.getLevel().dimension().equals(InkRealmTravel.INK_REALM);
    }
    @SubscribeEvent public static void load(ChunkDataEvent.Load e) {
        if(e.getChunk() instanceof LevelChunk c && ink(c) && e.getData().getBoolean(KEY)) DONE.add(c.getPos().toLong());
        if(e.getChunk() instanceof LevelChunk c && ink(c) && e.getData().getBoolean("TNCInkLandscapeV1")) LEGACY.add(c.getPos().toLong());
    }
    @SubscribeEvent public static void save(ChunkDataEvent.Save e) {
        if(e.getChunk() instanceof LevelChunk c && ink(c) && DONE.contains(c.getPos().toLong())) e.getData().putBoolean(KEY,true);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel l && l.dimension().equals(InkRealmTravel.INK_REALM)){DONE.clear();LEGACY.clear();job=null;}
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.level instanceof ServerLevel level)
                || !level.dimension().equals(InkRealmTravel.INK_REALM))return;
        if(job!=null && level.getChunkSource().getChunkNow(job.chunk.getPos().x,job.chunk.getPos().z)!=job.chunk)job=null;
        if(job==null) {
            search: for(int radius=0;radius<=6;radius++) for(var player:level.players())
                for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
                    if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                    int x=player.chunkPosition().x+dx,z=player.chunkPosition().z+dz;
                    if(DONE.contains(ChunkPos.asLong(x,z)))continue;
                    LevelChunk c=level.getChunkSource().getChunkNow(x,z);
                    if(c!=null){job=new Job(c);break search;}
                }
        }
        if(job!=null && job.advance(level)) {
            DONE.add(job.chunk.getPos().toLong());job.chunk.setUnsaved(true);job=null;
        }
    }
    private static BlockState state(InkLandscapePattern.Material m) {
        return switch(m) {
            case INK -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case CHARCOAL -> Blocks.GRAY_CONCRETE.defaultBlockState();
            case WASH -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case PAPER -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case NEEDLES -> Blocks.BLACK_WOOL.defaultBlockState();
            case PALE_NEEDLES -> Blocks.GRAY_WOOL.defaultBlockState();
        };
    }
    static final class Job {
        final LevelChunk chunk;
        final List<Placement> blocks=new ArrayList<>();
        int cursor;
        Job(LevelChunk chunk) {this(chunk,LEGACY.contains(chunk.getPos().toLong()));}
        Job(LevelChunk chunk,boolean verifiedLegacy) {
            this.chunk=chunk;
            int base=chunk.getMinBuildHeight()+4;
            for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)
                for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++) {
                    var column=InkLandscapePattern.column(x,z);
                    // Do not engulf a player's building: any unfamiliar block protects the whole column.
                    boolean protectedColumn=false;int matching=0;
                    for(int y=1;y<=InkLandscapePattern.MAX_HEIGHT;y++) {
                        BlockState old=chunk.getBlockState(new BlockPos(x,base+y,z));
                        var expected=column.at(y);
                        if(!old.isAir() && (expected==null || !old.equals(state(expected)))) {protectedColumn=true;break;}
                        if(!old.isAir())matching++;
                    }
                    if(protectedColumn || (!verifiedLegacy && matching<4))continue;
                    for(int y=1;y<=InkLandscapePattern.MAX_HEIGHT;y++) {
                        var material=column.at(y);
                        BlockPos pos=new BlockPos(x,base+y,z);
                        if(material!=null && chunk.getBlockState(pos).equals(state(material)))
                            blocks.add(new Placement(pos,state(material)));
                    }
                }
        }
        boolean advance(ServerLevel level) {
            // Shared across all players: at most 384 block checks/writes per server tick.
            int end=Math.min(cursor+384,blocks.size());
            while(cursor<end) {
                Placement p=blocks.get(cursor++);
                if(level.getBlockState(p.pos).equals(p.state))
                    level.setBlock(p.pos,Blocks.AIR.defaultBlockState(),2);
            }
            return cursor==blocks.size();
        }
    }
    private record Placement(BlockPos pos,BlockState state) {}
}
