package com.tnc.tnc.dungeon;

import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.*;
import java.util.*;

public final class DungeonRealm {
    public static final ResourceKey<Level> KEY=ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,DungeonContent.id("rift_dungeons"));
    private static final ThreadLocal<Integer> WRITING=ThreadLocal.withInitial(()->0);
    public static boolean inside(Level l){return l.dimension().equals(KEY);}
    public static boolean locked(Level l){return !l.isClientSide&&inside(l)&&WRITING.get()==0;}
    public static void write(Runnable action){int old=WRITING.get();WRITING.set(old+1);try{action.run();}finally{WRITING.set(old);}}
    public static BlockPos base(DungeonData.Run run,int floor){long x=run.slot%1024,z=run.slot/1024;int f=Math.max(0,floor-1);return new BlockPos((int)(x*4096)+(f%6)*384,80,(int)(z*4096)+(f/6)*384);}
    public static BlockPos center(DungeonData.Run run,DungeonGraph.Room room){return base(run,run.floor).offset(room.cell().x()*40,0,room.cell().z()*40);}
    public static BlockPos lobby(DungeonData.Run run){return base(run,1).offset(0,0,-190);}
    public record Cell(BlockPos pos,BlockState state){}
    private static final Map<UUID,Job> JOBS=new LinkedHashMap<>();
    private static final class Job {final DungeonData.Run run;final List<Cell> cells;final Runnable done;int cursor;boolean chunkPending;Job(DungeonData.Run r,List<Cell> c,Runnable d){run=r;cells=c;done=d;}}
    public static boolean busy(UUID id){return JOBS.containsKey(id);}
    public static void build(ServerLevel l,DungeonData.Run r,List<Cell> cells,Runnable done){if(JOBS.containsKey(r.id))return;r.building=true;JOBS.put(r.id,new Job(r,cells,done));}
    public static void tick(ServerLevel l){int budget=1400;for(var job:new ArrayList<>(JOBS.values())){
      if(job.run.closed){JOBS.remove(job.run.id);continue;}while(job.cursor<job.cells.size()&&budget>0){var cell=job.cells.get(job.cursor);if(!l.hasChunkAt(cell.pos)){
        if(!job.chunkPending){job.chunkPending=true;var cp=new net.minecraft.world.level.ChunkPos(cell.pos);l.getChunkSource().getChunkFuture(cp.x,cp.z,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((c,e)->{job.chunkPending=false;if(e!=null){job.run.building=false;JOBS.remove(job.run.id);com.mojang.logging.LogUtils.getLogger().error("Dungeon chunk preparation failed",e);}},l.getServer());}break;
      }write(()->l.setBlock(cell.pos,cell.state,2));job.cursor++;budget--;}
      if(job.cursor==job.cells.size()){JOBS.remove(job.run.id);job.run.building=false;job.done.run();}if(budget<=0)break;
    }}
    public static void stop(){JOBS.clear();}
    private static void cell(List<Cell> a,BlockPos p,BlockState s){a.add(new Cell(p.immutable(),s));}
    private static void furnishing(List<Cell> a,BlockPos o,DungeonData.Run r,DungeonGraph.Room room){
      var trim=r.theme.trim();var light=r.theme.lamp();
      // Keep the central 9x9, all four door axes and the spawn ring free of obstructions.
      switch(room.layout()){
        case 0->{for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(Math.max(Math.abs(x),Math.abs(z))==8&&Math.abs(x)>2&&Math.abs(z)>2)cell(a,o.offset(x,0,z),trim);for(int s:new int[]{-1,1})for(int t:new int[]{-1,1}){cell(a,o.offset(s*8,1,t*8),trim);cell(a,o.offset(s*8,2,t*8),light);}}
        case 1->{for(int s:new int[]{-1,1})for(int t:new int[]{-1,1}){int high=s==t?4:2;for(int y=1;y<=high;y++)cell(a,o.offset(s*7,y,t*7),y==high?light:trim);cell(a,o.offset(s*6,1,t*7),trim);}}
        case 2->{for(int s:new int[]{-1,1})for(int z:new int[]{-6,6}){for(int y=1;y<=4;y++)cell(a,o.offset(s*9,y,z),trim);for(int dx=0;dx<3;dx++)cell(a,o.offset(s*(7+dx),4,z),trim);cell(a,o.offset(s*9,2,z+s),light);}}
        case 3->{for(int s:new int[]{-1,1})for(int z=-7;z<=7;z++)if(Math.abs(z)>=3)cell(a,o.offset(s*6,1,z),trim);for(int s:new int[]{-1,1}){cell(a,o.offset(s*6,2,-7),light);cell(a,o.offset(s*6,2,7),light);}}
        case 4->{for(int s:new int[]{-1,1})for(int x=7;x<=9;x++)for(int z=-5;z<=5;z++)if(Math.abs(z)>2){cell(a,o.offset(s*x,1,z),trim);if(x==9&&Math.abs(z)==5)cell(a,o.offset(s*x,2,z),light);}}
        case 5->{for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(Math.abs(x)+Math.abs(z)==10&&Math.abs(x)>2&&Math.abs(z)>2)cell(a,o.offset(x,0,z),light);for(int s:new int[]{-1,1})for(int t:new int[]{-1,1}){cell(a,o.offset(s*7,1,t*7),trim);cell(a,o.offset(s*7,3,t*7),light);}}
      }
    }
    public static List<Cell> lobbyCells(DungeonData.Run r){var a=new ArrayList<Cell>();var o=lobby(r);for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++){cell(a,o.offset(x,0,z),r.theme.floor());if(Math.abs(x)==7||Math.abs(z)==7)for(int y=1;y<=5;y++)cell(a,o.offset(x,y,z),y==2?r.theme.lamp():r.theme.wall());}cell(a,o.offset(0,1,-5),DungeonContent.CONSOLE.defaultBlockState());cell(a,o.offset(3,1,-5),DungeonContent.WAYSTONE.defaultBlockState());return a;}
    public static List<Cell> floorCells(DungeonData.Run r){var a=new ArrayList<Cell>();var graph=r.graph();for(var room:graph.rooms){var o=center(r,room);int half=room.kind()==DungeonGraph.Kind.GUARDIAN?24:11;int high=room.kind()==DungeonGraph.Kind.GUARDIAN?20:10;
      for(int x=-half;x<=half;x++)for(int z=-half;z<=half;z++){cell(a,o.offset(x,0,z),r.theme.floor());cell(a,o.offset(x,high,z),r.theme.wall());if(Math.abs(x)==half||Math.abs(z)==half)for(int y=1;y<high;y++)cell(a,o.offset(x,y,z),y==1||y==high-1?r.theme.trim():r.theme.wall());}
      for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){var p=o.offset(sx*(half-4),1,sz*(half-4));cell(a,p,r.theme.trim());cell(a,p.above(),r.theme.lamp());}
      if(room.kind()==DungeonGraph.Kind.COMBAT){furnishing(a,o,r,room);if(r.theme==DungeonTheme.TIDE)for(int x=-4;x<=4;x++)for(int z:new int[]{-7,7}){cell(a,o.offset(x,0,z),Blocks.SEA_LANTERN.defaultBlockState());cell(a,o.offset(x,1,z),Blocks.BLUE_STAINED_GLASS.defaultBlockState());}}
      if(room.kind()==DungeonGraph.Kind.TREASURE)cell(a,o.above(),DungeonContent.CACHE.defaultBlockState());
      if(room.kind()==DungeonGraph.Kind.EVENT||room.kind()==DungeonGraph.Kind.REST)cell(a,o.above(),DungeonContent.CONSOLE.defaultBlockState());
      if(room.kind()==DungeonGraph.Kind.EXIT||room.kind()==DungeonGraph.Kind.GUARDIAN){cell(a,o.offset(0,1,3),DungeonContent.WAYSTONE.defaultBlockState());for(int x=-2;x<=2;x++)cell(a,o.offset(x,0,3),r.theme.lamp());}
    }
    for(var room:graph.rooms)com.tnc.tnc.dungeon.leyline.LeylineService.build(a,r,room);
    var secret=graph.rooms.stream().filter(q->q.kind()==DungeonGraph.Kind.TREASURE&&!com.tnc.tnc.dungeon.leyline.LeylineService.puzzle(r,q)).toList();
    if(!secret.isEmpty()){var room=secret.get(Math.floorMod((int)(r.seed+r.floor),secret.size()));cell(a,center(r,room).offset(6,1,5),com.tnc.tnc.alchemy.AlchemyContent.HIDDEN_CACHE.defaultBlockState());}
    for(var room:graph.rooms)for(int target:room.links())if(target>room.id()){
      var other=graph.rooms.get(target);var from=center(r,room);var to=center(r,other);int dx=Integer.signum(to.getX()-from.getX()),dz=Integer.signum(to.getZ()-from.getZ());int ha=room.kind()==DungeonGraph.Kind.GUARDIAN?24:11,hb=other.kind()==DungeonGraph.Kind.GUARDIAN?24:11;
      for(int step=ha;step<=40-hb;step++)for(int side=-2;side<=2;side++)for(int y=0;y<=5;y++){var p=from.offset(dx*step+dz*side,y,dz*step+dx*side);boolean shell=Math.abs(side)==2||y==0||y==5;cell(a,p,shell?(y==0?r.theme.floor():r.theme.trim()):Blocks.AIR.defaultBlockState());}
      com.tnc.tnc.dungeon.leyline.LeylineCorridors.decorate(a,r,room,other);
    }return a;}
    public static List<BlockPos> door(DungeonData.Run r,DungeonGraph.Room room,int target){var from=center(r,room);var to=center(r,r.graph().rooms.get(target));int dx=Integer.signum(to.getX()-from.getX()),dz=Integer.signum(to.getZ()-from.getZ()),half=room.kind()==DungeonGraph.Kind.GUARDIAN?24:11;var out=new ArrayList<BlockPos>();for(int s=-1;s<=1;s++)for(int y=1;y<=4;y++)out.add(from.offset(dx*half+dz*s,y,dz*half+dx*s));return out;}
    public static void doors(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,boolean shut){write(()->{for(int target:room.links())for(var p:door(r,room,target))l.setBlock(p,shut?Blocks.TINTED_GLASS.defaultBlockState():Blocks.AIR.defaultBlockState(),2);});}
}
