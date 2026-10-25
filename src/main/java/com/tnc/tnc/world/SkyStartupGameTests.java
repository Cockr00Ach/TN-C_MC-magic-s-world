package com.tnc.tnc.world;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import java.nio.file.*;
import java.util.*;

/** A real production shell slice: tickets must wait across ticks, then release after placement. */
@GameTestHolder("tncstartup")
public final class SkyStartupGameTests {
    @GameTestGenerator public static Collection<TestFunction> tests() {
        return List.of(new TestFunction("startup","nonblocking_production_slice","tncstartup:building_test_empty",
                Rotation.NONE,2400,0,true,SkyStartupGameTests::build));
    }
    private static void build(GameTestHelper helper) {
        try {
            var source=Path.of(System.getProperty("tnc.skyIslandSource"));
            SkyIslandManifest manifest;
            try(var reader=Files.newBufferedReader(source.resolve("sky_island/manifest.json"))) {
                manifest=SkyIslandManifest.parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            var level=helper.getLevel();var piece=manifest.pieces().get(0);
            var tag=NbtIo.readCompressed(source.resolve("structures/"+piece.resource().getPath()+".nbt").toFile());
            level.getStructureManager().getOrCreate(piece.resource()).load(level.holderLookup(Registries.BLOCK),tag);
            var data=new SkyIslandSavedData();data.originX=24000;data.originY=90;data.originZ=24000;
            data.layoutReady=true;data.phase=SkyIslandSavedData.Phase.BUILDING;
            long started=System.nanoTime();SkyIslandManager.buildStep(level,data,manifest);
            long elapsed=(System.nanoTime()-started)/1_000_000;
            if(data.activeChunks.isEmpty()||data.nextPiece!=0)throw new IllegalStateException("Missing asynchronous checkpoint");
            // This guards the original multi-second synchronous load, not a tight performance benchmark.
            if(elapsed>1000)throw new IllegalStateException("Ticket request blocked server tick: "+elapsed+"ms");
            var palette=tag.getList("palette",Tag.TAG_COMPOUND);var blocks=tag.getList("blocks",Tag.TAG_COMPOUND);
            helper.succeedWhen(()->{
                // This fixture has no connected player. Keep the dimension active while
                // the real asynchronous chunk pipeline handles our remote region tickets.
                level.resetEmptyTime();
                try {SkyIslandManager.buildStep(level,data,manifest);}catch(Exception ex){throw new IllegalStateException(ex);}
                helper.assertTrue(data.nextPiece==1,"Waiting for real production slice; tick="+helper.getTick()+", world="+level.getGameTime()+", tickets="+data.activeChunks.size());
                helper.assertTrue(data.activeChunks.isEmpty(),"Build tickets not released");
                int checked=0;
                for(int i=0;i<blocks.size();i+=Math.max(1,blocks.size()/20)){
                    var b=blocks.getCompound(i);var xyz=b.getList("pos",Tag.TAG_INT);
                    var pos=new BlockPos(data.originX,data.originY,data.originZ).offset(piece.offset()).offset(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                    var expected=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),palette.getCompound(b.getInt("state")));
                    helper.assertTrue(level.getBlockState(pos).equals(expected),"Missing production block at "+pos);checked++;
                }
                com.mojang.logging.LogUtils.getLogger().info("[TN-C Startup Test] ticket request={}ms, production samples={}, tickets released",elapsed,checked);
            });
        }catch(Exception ex){helper.fail(ex.toString());}
    }
}
