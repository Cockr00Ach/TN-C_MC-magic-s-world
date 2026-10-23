package com.tnc.tnc.world.elementarena;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
public final class ElementArenaChunkGenerator extends ChunkGenerator {
 public static final Codec<ElementArenaChunkGenerator> CODEC=RecordCodecBuilder.create(i->i.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ElementArenaChunkGenerator::getBiomeSource),Codec.BOOL.fieldOf("underwater").forGetter(g->g.underwater)).apply(i,ElementArenaChunkGenerator::new));
 private final boolean underwater;
 public ElementArenaChunkGenerator(BiomeSource source,boolean water){super(source);underwater=water;}
 protected Codec<? extends ChunkGenerator> codec(){return CODEC;}
 public int getGenDepth(){return 256;} public int getMinY(){return 0;} public int getSeaLevel(){return underwater?128:0;}
 public int getSpawnHeight(LevelHeightAccessor level){return underwater?32:40;}
 public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> s,RandomState r,long seed){return ChunkGeneratorStructureState.createForFlat(r,seed,getBiomeSource(),Stream.empty());}
 public void buildSurface(WorldGenRegion r,StructureManager s,RandomState n,ChunkAccess c){}
 public void applyCarvers(WorldGenRegion r,long seed,RandomState n,BiomeManager b,StructureManager s,ChunkAccess c,GenerationStep.Carving step){}
 public void spawnOriginalMobs(WorldGenRegion r){}
 public BlockState material(int x,int y,int z){
  if(y<0||y>=256)return Blocks.AIR.defaultBlockState();
  if(y<3)return Blocks.BEDROCK.defaultBlockState();
  int rise=underwater?0:ElementArenaLayout.rise(x,z),localY=y-rise;
  int top=ElementArenaLayout.pillarTop(x,z)+rise;
  if(localY<32||ElementArenaLayout.pillarSolid(x,localY,z)){
   int shade=ElementArenaLayout.shade(x,localY,z);
   if(underwater)return com.tnc.tnc.building.ElementBossBlocks.block("water",switch(shade){case 2->6;case 4->7;case 5->4;case 6->5;case 7->2;default->shade;}).defaultBlockState();
   return ElementArenas.STONES[shade-1].defaultBlockState();
  }
  return (underwater&&y<128?Blocks.WATER:Blocks.AIR).defaultBlockState();
 }
 public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor,Blender b,RandomState r,StructureManager s,ChunkAccess chunk){
  return CompletableFuture.supplyAsync(()->{
   var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
   var pos=new BlockPos.MutableBlockPos();int sx=chunk.getPos().getMinBlockX(),sz=chunk.getPos().getMinBlockZ();
   for(int x=0;x<16;x++)for(int z=0;z<16;z++){
    int solidTop=ElementArenaLayout.pillarTop(sx+x,sz+z)+(underwater?0:ElementArenaLayout.rise(sx+x,sz+z)),top=underwater?127:solidTop;
    for(int y=0;y<=top;y++){var state=material(sx+x,y,sz+z);chunk.setBlockState(pos.set(x,y,z),state,false);}
    floor.update(x,solidTop,z,material(sx+x,solidTop,sz+z));surface.update(x,top,z,material(sx+x,top,sz+z));
   }return chunk;
  },executor);
 }
 public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor l,RandomState r){return underwater&&type!=Heightmap.Types.OCEAN_FLOOR&&type!=Heightmap.Types.OCEAN_FLOOR_WG?128:ElementArenaLayout.pillarTop(x,z)+(underwater?0:ElementArenaLayout.rise(x,z))+1;}
 public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor l,RandomState r){var states=new BlockState[l.getHeight()];for(int i=0;i<states.length;i++)states[i]=material(x,i+l.getMinBuildHeight(),z);return new NoiseColumn(l.getMinBuildHeight(),states);}
 public void addDebugScreenInfo(List<String> lines,RandomState r,BlockPos p){lines.add("Element arena: "+(underwater?"underwater":"wind chessboard"));}
}
