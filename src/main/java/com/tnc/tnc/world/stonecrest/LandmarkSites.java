package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Reproduce vanilla's seed/placement/biome decision without loading the distant anchor chunk. */
final class LandmarkSites {
    record Key(Structure structure,long anchor,net.minecraft.world.level.chunk.ChunkGeneratorStructureState state) { }
    record Site(String asset,BlockPos origin,ChunkPos anchor) {
        StonecrestManifest manifest() { return StonecrestManifest.get(asset); }
        boolean intersects(ChunkPos chunk,int margin) {
            var d=manifest().dimensions();
            return origin.getX()-margin<=chunk.getMaxBlockX() && origin.getX()+d.getX()+margin>chunk.getMinBlockX()
                    && origin.getZ()-margin<=chunk.getMaxBlockZ() && origin.getZ()+d.getZ()+margin>chunk.getMinBlockZ();
        }
    }
    private static final Map<ServerLevel,ConcurrentHashMap<Key,Optional<Site>>> CACHE=new ConcurrentHashMap<>();
    static List<Site> touching(ServerLevel level,ChunkPos chunk,int margin) {
        var state=level.getChunkSource().getGeneratorState();
        var generator=level.getChunkSource().getGenerator();
        return touching(level,chunk,margin,generator,state);
    }
    static List<Site> touching(ServerLevel level,ChunkPos chunk,int margin,
                              net.minecraft.world.level.chunk.ChunkGenerator generator,
                              net.minecraft.world.level.chunk.ChunkGeneratorStructureState state) {
        var cache=CACHE.computeIfAbsent(level,k->new ConcurrentHashMap<>());
        var result=new ArrayList<Site>();
        for (var holder:state.possibleStructureSets()) {
            var set=holder.value();
            // All current TN-C sets have one entry. Do not incorrectly reproduce weighted vanilla sets.
            if (set.structures().size()!=1 || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;
            var structure=set.structures().get(0).structure().value();
            if (!(structure instanceof LargeLandmarkStructure large)) continue;
            var d=StonecrestManifest.get(large.asset(level.getStructureManager())).dimensions();
            int minX=Math.floorDiv(chunk.getMinBlockX()-margin-d.getX()+1+d.getX()/2-8,16);
            int maxX=Math.floorDiv(chunk.getMaxBlockX()+margin+d.getX()/2-8,16);
            int minZ=Math.floorDiv(chunk.getMinBlockZ()-margin+4,16);
            int maxZ=Math.floorDiv(chunk.getMaxBlockZ()+margin+d.getZ()+4,16);
            for (int rz=Math.floorDiv(minZ,placement.spacing());rz<=Math.floorDiv(maxZ,placement.spacing());rz++)
                for (int rx=Math.floorDiv(minX,placement.spacing());rx<=Math.floorDiv(maxX,placement.spacing());rx++) {
                    var anchor=placement.getPotentialStructureChunk(state.getLevelSeed(),rx*placement.spacing(),rz*placement.spacing());
                    if (anchor.x<minX || anchor.x>maxX || anchor.z<minZ || anchor.z>maxZ
                            || !placement.isStructureChunk(state,anchor.x,anchor.z)) continue;
                    var key=new Key(structure,anchor.toLong(),state);
                    var optional=cache.computeIfAbsent(key,k->{
                        var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),
                                state.randomState(),level.getStructureManager(),state.getLevelSeed(),anchor,level,structure.biomes()::contains);
                        return structure.findValidGenerationPoint(context).flatMap(stub->stub.getPiecesBuilder().build().pieces().stream()
                                .filter(p->p instanceof LargeLandmarkMarker).map(p->{
                                    var marker=(LargeLandmarkMarker)p;
                                    return new Site(marker.asset,marker.origin,anchor);
                                }).findFirst());
                    });
                    optional.filter(s->s.intersects(chunk,margin)).ifPresent(result::add);
                }
        }
        // Bounded even during very long compass searches. Plans are deterministic and safe to recompute.
        if (cache.size()>512) cache.clear();
        return result;
    }
    static void clear(MinecraftServer server) { CACHE.keySet().removeIf(l->l.getServer()==server); }
}
