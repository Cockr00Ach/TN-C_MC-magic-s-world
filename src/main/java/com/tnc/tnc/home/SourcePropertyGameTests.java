package com.tnc.tnc.home;
import com.google.gson.*;
import com.tnc.tnc.adventure.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;

/** Integration fixture places authored NBT tiles, rather than rebuilding the expected blueprint. */
@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class SourcePropertyGameTests {
    @GameTest(template="building_test_empty",batch="source_catalog",timeoutTicks=400)
    public static void allFiveAuthoredHousesCanBeDeliveredWithoutChangingTheirShell(GameTestHelper h)throws Exception{
        var level=h.getLevel();Path data;
        try(var dirs=Files.list(Path.of("..").toAbsolutePath().normalize().resolve("modpack"))){data=dirs.map(p->p.resolve("kubejs/data/tnc")).filter(p->Files.isRegularFile(p.resolve("sky_island/manifest.json"))).findFirst().orElseThrow();}
        var pieces=JsonParser.parseString(Files.readString(data.resolve("sky_island/manifest.json"))).getAsJsonObject().getAsJsonArray("pieces");var store=AdventureSavedData.get(level.getServer());var saved=store.housing.copy();var forced=new ArrayList<ChunkPos>();
        try{for(var plot:PlotCatalog.ALL)store.housing.remove(plot.id());int index=0;
            for(var plot:PlotCatalog.ALL){if(plot.kind()!=PlotCatalog.Kind.HOME)continue;var plan=HousingService.blueprint(level,plot.id());var origin=new BlockPos(10000+index++*64,180,10000).subtract(plot.min());var bounds=BoundingBox.fromCorners(origin.offset(plot.min()).offset(-2,-2,-2),origin.offset(plot.max()).offset(2,2,2));
                for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){var c=new ChunkPos(x,z);if(!level.getForcedChunks().contains(c.toLong())){forced.add(c);level.setChunkForced(x,z,true);}level.getChunk(x,z);}
                for(var value:pieces){var p=value.getAsJsonObject();if(!p.get("layer").getAsString().equals("town"))continue;var o=p.getAsJsonArray("offset");var s=p.getAsJsonArray("size");var local=new BlockPos(o.get(0).getAsInt(),o.get(1).getAsInt(),o.get(2).getAsInt());if(!plot.intersects(local,local.offset(s.get(0).getAsInt()-1,s.get(1).getAsInt()-1,s.get(2).getAsInt()-1)))continue;
                    var file=data.resolve("structures/"+p.get("resource").getAsString().split(":")[1]+".nbt");try(var in=Files.newInputStream(file)){var template=new StructureTemplate();template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),NbtIo.readCompressed(in));var pos=origin.offset(local);h.assertTrue(template.placeInWorld(level,pos,pos,new StructurePlaceSettings().setBoundingBox(bounds).setIgnoreEntities(true).setKeepLiquids(false).setKnownShape(true),net.minecraft.util.RandomSource.create(1),2),"Source tile placed for "+plot.id());}
                }
                var buyer=AdventureGameTests.player(h);AdventureService.profile(buyer).credit(8000,"fixture");
                // Preserve and reject contents first, then empty only fixture containers for delivery.
                for(var cell:plan.cells())if(level.getBlockEntity(origin.offset(cell.local())) instanceof Container container)container.clearContent();
                String result=HousingService.buyAt(buyer,origin,plan,false,plot);h.assertTrue(HousingService.owned(buyer),plot.id()+" is a real deliverable house: "+result);
                for(var cell:plan.cells()){var actual=level.getBlockState(origin.offset(cell.local()));h.assertTrue(cell.clear()?actual.isAir():HousingService.stable(actual,cell.state()),plot.id()+" shell preserved at "+cell.local());}
            }h.succeed();
        }finally{store.housing=saved;store.setDirty();for(var c:forced)level.setChunkForced(c.x,c.z,false);}
    }
}
