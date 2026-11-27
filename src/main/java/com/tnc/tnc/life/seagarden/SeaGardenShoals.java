package com.tnc.tnc.life.seagarden;

import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Invoked once by the mainland fresh-chunk decorator after landmark / habitation checks. */
public final class SeaGardenShoals {
    enum Zone { SHOAL, REEF, COLD, DEEP }
    static Zone zone(LevelChunk chunk, BlockPos pos, int depth) {
        var biome=chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()),QuartPos.fromBlock(pos.getY()),QuartPos.fromBlock(pos.getZ()));
        if (!biome.is(BiomeTags.IS_OCEAN)) return null;
        if (depth>=24) return Zone.DEEP;
        if (biome.is(Biomes.COLD_OCEAN) || biome.is(Biomes.DEEP_COLD_OCEAN) || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)) return Zone.COLD;
        if (biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)) return Zone.REEF;
        return Zone.SHOAL;
    }
    static SeaGarden.Kind pick(Zone zone, RandomSource random) {
        int n=random.nextInt(10);
        return switch(zone) {
            case SHOAL -> n<5?SeaGarden.Kind.LANTERN:n<8?SeaGarden.Kind.FAN:SeaGarden.Kind.BINDWEED;
            case REEF -> n<5?SeaGarden.Kind.BLOSSOM:n<8?SeaGarden.Kind.ANEMONE:SeaGarden.Kind.FAN;
            case COLD -> n<5?SeaGarden.Kind.KELP:n<8?SeaGarden.Kind.BINDWEED:SeaGarden.Kind.LANTERN;
            case DEEP -> n<4?SeaGarden.Kind.ANEMONE:n<7?SeaGarden.Kind.KELP:n<9?SeaGarden.Kind.LANTERN:SeaGarden.Kind.BINDWEED;
        };
    }
    public static int populate(ServerLevel level, LevelChunk chunk) {
        if (level!=level.getServer().overworld()) return 0;
        var cp=chunk.getPos();
        var random=RandomSource.create(level.getSeed() ^ Mth.getSeed(cp.x,36341,cp.z));
        if (random.nextInt(5)==0) return 0;
        for (int sample=0;sample<5;sample++) {
            int ax=3+random.nextInt(10),az=3+random.nextInt(10);
            var anchor=floor(chunk,ax,az);
            if (anchor==null) continue;
            var zone=zone(chunk,anchor,depth(chunk,anchor));
            if (zone==null) continue;
            var kind=pick(zone,random);
            if (!place(level,chunk,anchor,kind,random)) continue;
            int total=1,target=kind==SeaGarden.Kind.KELP ? 2 : 2+random.nextInt(3);
            for (int j=0;j<11 && total<target;j++) {
                int nx=ax+random.nextInt(7)-3,nz=az+random.nextInt(7)-3;
                if (nx<2 || nx>13 || nz<2 || nz>13) continue;
                var pos=floor(chunk,nx,nz);
                if (pos==null || Math.abs(pos.getY()-anchor.getY())>3 || zone(chunk,pos,depth(chunk,pos))!=zone) continue;
                if (place(level,chunk,pos,random.nextInt(4)==0?pick(zone,random):kind,random)) total++;
            }
            return total;
        }
        return 0;
    }
    private static BlockPos floor(LevelChunk chunk, int x, int z) {
        int y=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)+1;
        var cp=chunk.getPos();
        var p=new BlockPos(cp.getMinBlockX()+x,y,cp.getMinBlockZ()+z);
        return depth(chunk,p)>=2 && SeaPlantBlock.seaBed(chunk.getBlockState(p.below())) && SeaPlantBlock.source(chunk.getBlockState(p)) ? p : null;
    }
    private static int depth(LevelChunk chunk,BlockPos p) {
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE,p.getX()&15,p.getZ()&15)-p.getY()+1;
    }
    static boolean place(ServerLevel level, LevelChunk chunk, BlockPos pos, SeaGarden.Kind kind, RandomSource random) {
        var cp=chunk.getPos();
        if ((pos.getX()>>4)!=cp.x || (pos.getZ()>>4)!=cp.z || !level.isInWorldBounds(pos)) return false;
        if (!SeaPlantBlock.seaBed(chunk.getBlockState(pos.below())) || TownProtection.hazard(level,pos) || HousingService.plotAt(level,pos)!=null) return false;
        int height=kind==SeaGarden.Kind.KELP ? 3+random.nextInt(3) : 1;
        for (int i=0;i<=height;i++)
            if (!SeaPlantBlock.source(chunk.getBlockState(pos.above(i)))) return false;
        var block=kind.block();
        var root=block.defaultBlockState();
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
        if (kind!=SeaGarden.Kind.KELP) {
            if (kind==SeaGarden.Kind.BLOSSOM) root=root.setValue(SeaPlantBlock.STAGE,SeaPlantBlock.moonStage(level.getDayTime()));
            return level.setBlock(pos,root,flags);
        }
        for (int i=0;i<height-1;i++) level.setBlock(pos.above(i),root.setValue(TidekelpBlock.PART,TidekelpBlock.Part.STEM).setValue(SeaPlantBlock.STAGE,1),flags);
        level.setBlock(pos.above(height-1),root.setValue(SeaPlantBlock.STAGE,2),flags);
        return true;
    }
    private SeaGardenShoals() {}
}
