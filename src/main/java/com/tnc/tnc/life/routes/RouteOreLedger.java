package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** Placed ores never refresh a chunk's finite natural budget. */
public final class RouteOreLedger extends SavedData {
    private final Map<Long,Integer> remaining=new HashMap<>();private final Set<Long> placed=new HashSet<>();private long epoch=-1;
    public static RouteOreLedger get(ServerLevel l){return l.getDataStorage().computeIfAbsent(RouteOreLedger::load,RouteOreLedger::new,"tnc_ore_mana");}
    public void placed(BlockPos p){placed.add(p.asLong());setDirty();}
    public int drawNatural(ServerLevel l,BlockPos p,int request){long day=l.getGameTime()/24000;if(epoch<day){remaining.clear();epoch=day;setDirty();}boolean natural=false;for(BlockPos q:BlockPos.betweenClosed(p.offset(-2,-3,-2),p.offset(2,-1,2))){if(!l.hasChunkAt(q)||placed.contains(q.asLong()))continue;var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(l.getBlockState(q).getBlock());if(id!=null&&id.getPath().endsWith("_ore")){natural=true;break;}}if(!natural)return 0;long chunk=net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4);int left=remaining.getOrDefault(chunk,1200),got=Math.min(left,request);remaining.put(chunk,left-got);if(got>0)setDirty();return got;}
    private static RouteOreLedger load(CompoundTag t){var out=new RouteOreLedger();out.epoch=t.getLong("Epoch");for(Tag raw:t.getList("Budgets",10)){var row=(CompoundTag)raw;out.remaining.put(row.getLong("Chunk"),Math.max(0,Math.min(1200,row.getInt("Left"))));}for(long p:t.getLongArray("Placed"))out.placed.add(p);return out;}
    @Override public CompoundTag save(CompoundTag t){t.putLong("Epoch",epoch);t.putLongArray("Placed",placed.stream().mapToLong(Long::longValue).toArray());var list=new ListTag();remaining.forEach((key,value)->{var row=new CompoundTag();row.putLong("Chunk",key);row.putInt("Left",value);list.add(row);});t.put("Budgets",list);return t;}
}
