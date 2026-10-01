package com.tnc.tnc.adventure;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;

/** Actual source NBT and shipped landscape, including the bank's real floor. */
@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class SourceTownCounterGameTests {
    @GameTest(template="building_test_empty",batch="source_counters",timeoutTicks=400)
    public static void allFiveCountersStandInsideTheirActualAuthoredBuildings(GameTestHelper h)throws Exception{
        var level=h.getLevel();Path data;try(var dirs=Files.list(Path.of("..").toAbsolutePath().normalize().resolve("modpack"))){data=dirs.map(p->p.resolve("kubejs/data/tnc")).filter(p->Files.isRegularFile(p.resolve("sky_island/manifest.json"))).findFirst().orElseThrow();}
        var pieces=JsonParser.parseString(Files.readString(data.resolve("sky_island/manifest.json"))).getAsJsonObject().getAsJsonArray("pieces");CompoundTag overlay;
        try(var in=level.getServer().getResourceManager().getResource(ResourceLocation.parse("tnc:sky_island/landscape_v1.nbt")).orElseThrow().open()){overlay=NbtIo.readCompressed(in);}
        var palette=new ArrayList<net.minecraft.world.level.block.state.BlockState>();for(var raw:overlay.getList("Palette",10))palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),(CompoundTag)raw));
        var forced=new ArrayList<ChunkPos>();var origins=new ArrayList<BlockPos>();int index=0;
        try{for(var post:TownServices.POSTS){var room=post.room();var origin=new BlockPos(12000+index++*64,180,10000).subtract(post.local());var lo=new BlockPos(room.minX()-2,room.y()-2,room.minZ()-2);var hi=new BlockPos(room.maxX()+2,room.y()+3,room.maxZ()+2);var bounds=BoundingBox.fromCorners(origin.offset(lo),origin.offset(hi));origins.add(origin);
            for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){var c=new ChunkPos(x,z);if(!level.getForcedChunks().contains(c.toLong())){forced.add(c);level.setChunkForced(x,z,true);}level.getChunk(x,z);}
            for(var value:pieces){var p=value.getAsJsonObject();if(!p.get("layer").getAsString().equals("town"))continue;var offset=p.getAsJsonArray("offset");var size=p.getAsJsonArray("size");var local=new BlockPos(offset.get(0).getAsInt(),offset.get(1).getAsInt(),offset.get(2).getAsInt());var box=BoundingBox.fromCorners(local,local.offset(size.get(0).getAsInt()-1,size.get(1).getAsInt()-1,size.get(2).getAsInt()-1));if(!box.intersects(BoundingBox.fromCorners(lo,hi)))continue;
                try(var in=Files.newInputStream(data.resolve("structures/"+p.get("resource").getAsString().split(":")[1]+".nbt"))){var template=new StructureTemplate();template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),NbtIo.readCompressed(in));var pos=origin.offset(local);template.placeInWorld(level,pos,pos,new StructurePlaceSettings().setBoundingBox(bounds).setIgnoreEntities(true).setKeepLiquids(false).setKnownShape(true),net.minecraft.util.RandomSource.create(1),2);}
            }
            for(var raw:overlay.getList("Tiles",10)){var changes=((CompoundTag)raw).getIntArray("Changes");for(int i=0;i<changes.length;i+=5){var local=new BlockPos(changes[i],changes[i+1],changes[i+2]);if(BoundingBox.fromCorners(lo,hi).isInside(local))level.setBlock(origin.offset(local),palette.get(changes[i+4]),18);}}
        }
        h.succeedWhen(()->{
            for(var c:forced)h.assertTrue(level.areEntitiesLoaded(c.toLong()),"Waiting for real source fixture entity sections");
            for(int i=0;i<TownServices.POSTS.size();i++){var post=TownServices.POSTS.get(i);var origin=origins.get(i);var record=new CompoundTag();
                h.assertTrue(TownServices.ensurePost(level,origin,post,record),"Actual "+post.role()+" interior supports its worker; no invented fixture floor");var npc=level.getEntity(record.getUUID("UUID"));h.assertTrue(npc instanceof TownServiceNpc worker&&worker.role().equals(post.role())&&post.room().contains(npc.blockPosition().subtract(origin)),"Correct role inside actual room: "+post.role());
                var player=AdventureGameTests.player(h);player.teleportTo(npc.getX(),npc.getY(),npc.getZ());h.assertTrue(AdventureService.snapshot(player,"",post.role()).getString("ServiceRole").equals(post.role()),"Actual worker opens its own counter");npc.discard();
            }for(var c:forced)level.setChunkForced(c.x,c.z,false);
        });}catch(Exception e){for(var c:forced)level.setChunkForced(c.x,c.z,false);throw e;}
    }
}
