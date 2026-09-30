package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Huge structures exceed vanilla's eight-chunk reference radius. Each FEATURES chunk places only its own slice. */
public final class LandmarkChunkFeature extends Feature<NoneFeatureConfiguration> {
    private record IndexKey(String asset,int alignX,int alignZ) { }
    private static final Map<IndexKey,Map<Long,List<StonecrestManifest.Piece>>> INDEX=new ConcurrentHashMap<>();
    public LandmarkChunkFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.level() instanceof WorldGenRegion region) || !LandmarkGenerationMode.enabled(region.getLevel())) return false;
        boolean placed=false;
        for (var site:LandmarkSites.touching(region.getLevel(),region.getCenter(),0)) {
            placeSlice(region,region.getCenter(),site); placed=true;
        }
        return placed;
    }
    static List<StonecrestManifest.Piece> piecesFor(String asset,BlockPos origin,ChunkPos chunk) {
        var key=new IndexKey(asset,Math.floorMod(origin.getX(),16),Math.floorMod(origin.getZ(),16));
        var index=INDEX.computeIfAbsent(key,k->{
            var map=new HashMap<Long,List<StonecrestManifest.Piece>>();
            for (var piece:StonecrestManifest.get(asset).pieces()) {
                int minX=Math.floorDiv(k.alignX+piece.offset().getX(),16),minZ=Math.floorDiv(k.alignZ+piece.offset().getZ(),16);
                int maxX=Math.floorDiv(k.alignX+piece.offset().getX()+piece.size().getX()-1,16);
                int maxZ=Math.floorDiv(k.alignZ+piece.offset().getZ()+piece.size().getZ()-1,16);
                for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++)
                    map.computeIfAbsent(ChunkPos.asLong(x,z),ignored->new ArrayList<>()).add(piece);
            }
            map.replaceAll((pos,list)->List.copyOf(list)); return Map.copyOf(map);
        });
        return index.getOrDefault(ChunkPos.asLong(chunk.x-Math.floorDiv(origin.getX(),16),chunk.z-Math.floorDiv(origin.getZ(),16)),List.of());
    }
    static void placeSlice(WorldGenLevel level,ChunkPos chunk,LandmarkSites.Site site) {
        var origin=site.origin(); var manifest=site.manifest();
        var box=new BoundingBox(chunk.getMinBlockX(),level.getMinBuildHeight(),chunk.getMinBlockZ(),
                chunk.getMaxBlockX(),level.getMaxBuildHeight()-1,chunk.getMaxBlockZ());
        var preserved=new PreservedSite();
        // Only containers/devices already present in this generating chunk; never look up distant FULL chunks.
        for (var pos:level.getChunk(chunk.x,chunk.z).getBlockEntitiesPos()) preserved.reserve(pos);
        if (!manifest.floating()) terrain(level,chunk,site,preserved);
        for (var piece:piecesFor(site.asset(),origin,chunk)) {
            var template=level.getLevel().getStructureManager().get(piece.resource()).orElseThrow();
            try {
                if (!template.getSize().equals(piece.size())) throw new IllegalStateException("Wrong landmark template size: "+piece.resource());
                var target=origin.offset(piece.offset());
                template.placeInWorld(level,target,target,new StructurePlaceSettings().setBoundingBox(box)
                                .setKnownShape(true).setIgnoreEntities(true).setKeepLiquids(false).addProcessor(preserved.processor),
                        RandomSource.create(origin.asLong()^piece.offset().asLong()),18);
            } finally {
                // Do not retain thousands of decoded templates (some contain millions of source blocks).
                level.getLevel().getStructureManager().remove(piece.resource());
            }
        }
    }
    private static void terrain(WorldGenLevel level,ChunkPos chunk,LandmarkSites.Site site,PreservedSite preserved) {
        var m=site.manifest(); var o=site.origin(); var p=new BlockPos.MutableBlockPos();
        for (int z=chunk.getMinBlockZ();z<=chunk.getMaxBlockZ();z++) for (int x=chunk.getMinBlockX();x<=chunk.getMaxBlockX();x++) {
            int lx=x-o.getX(),lz=z-o.getZ(); if (!m.terrainAt(lx,lz)) continue;
            int high=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)-1;
            int ground=high; p.set(x,ground,z);
            while (ground>level.getMinBuildHeight()+1 && !LargeLandmarkJobs.natural(level.getBlockState(p)) && high-ground<48) p.setY(--ground);
            var top=level.getBlockState(p);
            if (!LargeLandmarkJobs.natural(top)) top=Blocks.GRASS_BLOCK.defaultBlockState();
            var plan=StonecrestTerrainPlanner.plan(ground,o.getY()+m.anchorLocal().getY(),m.distanceAt(lx,lz),m.maxBlendDistance(),false,0,0);
            int bottom=Math.max(level.getMinBuildHeight()+1,Math.min(ground+1,m.buildingAt(lx,lz)?o.getY():plan.targetY()));
            int ceiling=Math.min(level.getMaxBuildHeight()-1,Math.max(high,m.buildingAt(lx,lz)?o.getY()-1:plan.targetY()));
            for (int y=bottom;y<=ceiling;y++) {
                p.set(x,y,z); if (preserved.contains(p)) continue;
                var target=m.buildingAt(lx,lz)?(y>=o.getY()?Blocks.AIR.defaultBlockState():Blocks.STONE.defaultBlockState())
                        :y>plan.targetY()?Blocks.AIR.defaultBlockState():y==plan.targetY()?top:Blocks.DIRT.defaultBlockState();
                if (!level.getBlockState(p).equals(target)) level.setBlock(p,target,18);
            }
        }
    }
}
