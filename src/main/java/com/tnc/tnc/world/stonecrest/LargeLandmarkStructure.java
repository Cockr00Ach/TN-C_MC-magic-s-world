package com.tnc.tnc.world.stonecrest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

/** Native discovery anchor for full-sized, asynchronously assembled landmarks. */
public final class LargeLandmarkStructure extends Structure {
    public static final Codec<LargeLandmarkStructure> CODEC=RecordCodecBuilder.create(i->i.group(
            settingsCodec(i),Codec.STRING.fieldOf("asset").forGetter(s->s.asset)).apply(i,LargeLandmarkStructure::new));
    private final String asset;
    public LargeLandmarkStructure(StructureSettings settings,String asset) {
        super(settings); this.asset=asset;
        var m=StonecrestManifest.get(asset);
        for (var p:m.pieces()) if (p.size().getX()>16 || p.size().getY()>16 || p.size().getZ()>16)
            throw new IllegalArgumentException("Landmarks require <=16 cube checkpoints: "+p.resource());
    }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext c) {
        var m=StonecrestManifest.get(asset); var d=m.dimensions();
        // Locate at the southern approach, not inside a roof or unbuilt floating island.
        int x=c.chunkPos().getMinBlockX()+8,z=c.chunkPos().getMinBlockZ()+8;
        int ox=x-d.getX()/2,oz=z-d.getZ()-12;
        var heights=new ArrayList<Integer>();
        for (int lz=0;lz<d.getZ();lz+=Math.max(16,d.getZ()/8))
            for (int lx=0;lx<d.getX();lx+=Math.max(16,d.getX()/8)) {
                if (!m.buildingAt(lx,lz)) continue;
                int h=c.chunkGenerator().getFirstOccupiedHeight(ox+lx,oz+lz,Heightmap.Types.WORLD_SURFACE_WG,c.heightAccessor(),c.randomState());
                var state=c.chunkGenerator().getBaseColumn(ox+lx,oz+lz,c.heightAccessor(),c.randomState()).getBlock(h);
                if (!m.floating() && (!state.getFluidState().isEmpty() || h<60)) return Optional.empty();
                heights.add(h);
            }
        if (heights.isEmpty()) return Optional.empty();
        Collections.sort(heights);
        int min=heights.get(0),max=heights.get(heights.size()-1);
        if (!m.floating() && max-min>30) return Optional.empty();
        int oy=LandmarkPlacementPlan.originY(heights.get(heights.size()/2),max,m.anchorLocal().getY(),d.getY(),
                c.heightAccessor().getMaxBuildHeight(),m.floating(),m.sunken());
        if (!LandmarkPlacementPlan.fits(oy,d.getY(),c.heightAccessor().getMinBuildHeight(),c.heightAccessor().getMaxBuildHeight())) return Optional.empty();
        int y=c.chunkGenerator().getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,c.heightAccessor(),c.randomState())+1;
        BlockPos marker=new BlockPos(x,y,z),origin=new BlockPos(ox,oy,oz);
        return Optional.of(new GenerationStub(marker,b->b.addPiece(new LargeLandmarkMarker(asset,marker,origin))));
    }
    @Override public StructureType<?> type() { return TNStructures.LARGE_LANDMARK.get(); }
}
