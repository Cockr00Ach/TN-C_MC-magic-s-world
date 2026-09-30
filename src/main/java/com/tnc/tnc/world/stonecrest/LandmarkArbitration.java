package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.ArrayList;
import java.util.List;

/** Deterministic non-recursive footprint arbitration, shared by discovery and actual placement. */
final class LandmarkArbitration {
    static boolean wins(Structure.GenerationContext context,String asset,BlockPos origin) {
        var dimensions=StonecrestManifest.get(asset).dimensions();
        for (var other:candidates(context,origin.getX()-16,origin.getZ()-16,
                origin.getX()+dimensions.getX()+15,origin.getZ()+dimensions.getZ()+15)) {
            if (asset.equals(other.asset()) && origin.equals(other.origin())) continue;
            int priority=other.asset().compareTo(asset);
            if (priority<0 || priority==0 && Long.compare(other.anchor().toLong(),context.chunkPos().toLong())<0) return false;
        }
        return true;
    }
    static boolean pitConflicts(Structure.GenerationContext context,int centerX,int centerZ) {
        // Prefer complete architecture over excavation, regardless of which area is loaded first.
        return !candidates(context,centerX-220,centerZ-180,centerX+219,centerZ+179).isEmpty();
    }
    private static List<LandmarkSites.Site> candidates(Structure.GenerationContext c,int minX,int minZ,int maxX,int maxZ) {
        var registry=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var state=c.chunkGenerator().createState(registry.asLookup(),c.randomState(),c.seed());
        var result=new ArrayList<LandmarkSites.Site>();
        for (var holder:state.possibleStructureSets()) {
            var set=holder.value();
            if (set.structures().size()!=1 || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) continue;
            if (!(set.structures().get(0).structure().value() instanceof LargeLandmarkStructure large)) continue;
            var d=StonecrestManifest.get(large.asset()).dimensions();
            int ax0=Math.floorDiv(minX-d.getX()+1+d.getX()/2-8,16),ax1=Math.floorDiv(maxX+d.getX()/2-8,16);
            int az0=Math.floorDiv(minZ+4,16),az1=Math.floorDiv(maxZ+d.getZ()+4,16);
            for (int rz=Math.floorDiv(az0,placement.spacing());rz<=Math.floorDiv(az1,placement.spacing());rz++)
                for (int rx=Math.floorDiv(ax0,placement.spacing());rx<=Math.floorDiv(ax1,placement.spacing());rx++) {
                    var anchor=placement.getPotentialStructureChunk(c.seed(),rx*placement.spacing(),rz*placement.spacing());
                    if (anchor.x<ax0 || anchor.x>ax1 || anchor.z<az0 || anchor.z>az1 || !placement.isStructureChunk(state,anchor.x,anchor.z)) continue;
                    var context=new Structure.GenerationContext(c.registryAccess(),c.chunkGenerator(),c.biomeSource(),c.randomState(),
                            c.structureTemplateManager(),c.seed(),anchor,c.heightAccessor(),large.biomes()::contains);
                    var stub=large.rawGenerationPoint(context); if (stub.isEmpty()) continue;
                    var pos=stub.get().position();
                    if (!large.biomes().contains(c.biomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                            QuartPos.fromBlock(pos.getY()),QuartPos.fromBlock(pos.getZ()),c.randomState().sampler()))) continue;
                    var marker=(LargeLandmarkMarker)stub.get().getPiecesBuilder().build().pieces().get(0);
                    var o=marker.origin;
                    if (o.getX()<=maxX && o.getX()+d.getX()>minX && o.getZ()<=maxZ && o.getZ()+d.getZ()>minZ)
                        result.add(new LandmarkSites.Site(marker.asset,o,anchor));
                }
        }
        return result;
    }
}
