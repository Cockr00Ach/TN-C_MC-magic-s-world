package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.dungeon.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncmirror") @PrefixGameTestTemplate(false)
public final class MirrorArenaMigrationGameTests {
    @GameTest(template="building_test_empty",batch="echo_migration",timeoutTicks=180)
    public static void oldPoolDriesInPlaceWithoutBuryingCombatantsOrDrainingBuckets(GameTestHelper h){
        var player=AdventureGameTests.player(h);
        var level=Objects.requireNonNull(player.server.getLevel(DungeonRealm.KEY));
        var data=DungeonData.get(player.server);
        var run=new DungeonData.Run(UUID.randomUUID(),player.getUUID(),23,data.nextSlot++,DungeonTheme.ASTRAL);
        run.floor=30;
        data.runs.put(run.id,run);
        data.profile(player.getUUID()).putUUID("Run",run.id);
        var room=run.graph().rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
        var c=DungeonRealm.center(run,room);
        var chunk=new ChunkPos(c);
        var tickets=new ArrayList<ChunkPos>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var cp=new ChunkPos(chunk.x+x,chunk.z+z);
            if(level.setChunkForced(cp.x,cp.z,true))tickets.add(cp);
            level.getChunk(cp.x,cp.z);
        }
        run.state().events.putBoolean("EchoPoolBuilt",true);
        var outside=c.east(11);
        var foreign=c.east(7);
        DungeonRealm.write(()->{
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(x*x+z*z<=8.5*8.5){
                var pos=c.offset(x,0,z);
                level.setBlock(pos.below(),Blocks.POLISHED_BLACKSTONE.defaultBlockState(),18);
                level.setBlock(pos,Blocks.WATER.defaultBlockState(),18);
            }
            level.setBlock(outside,Blocks.WATER.defaultBlockState(),18);
            level.setBlock(foreign,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
        });
        player.teleportTo(level,c.getX()+1.5,c.getY()+.02,c.getZ()+.5,0,0);
        var mirror=Objects.requireNonNull(MirrorEntities.DOUBLE.get().create(level));
        mirror.bind(player,run.id,c);
        mirror.enterMelee();
        mirror.setPos(c.getX()+3.5,c.getY()+.2,c.getZ()+.5);
        mirror.setHealth(8);
        var web=c.west(2);
        var bucket=c.west(4).above();
        var party=List.of(player);
        var journal=MirrorTerrain.get(level);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(c),"Waiting for the old chamber")).thenExecute(()->{
            h.assertTrue(level.addFreshEntity(mirror),"Saved living duelist loaded");
            h.assertTrue(journal.place(level,run.id,web,"web",web.asLong()) && journal.place(level,run.id,bucket,"water",bucket.asLong()),"Old web and temporary bucket prepared");
            h.assertTrue(MirrorArena.ensureFloor(level,run,room,party),"Dry chamber migration completed");
            h.assertTrue(player.getY()==c.getY()+1 && mirror.getY()==c.getY()+1 && level.noCollision(player,player.getBoundingBox())
                    && level.noCollision(mirror,mirror.getBoundingBox()) && mirror.getHealth()==8,"Both bodies clear the new stone without damage");
            h.assertTrue(level.getBlockState(outside).is(Blocks.WATER) && level.getBlockState(foreign).is(Blocks.DIAMOND_BLOCK),"Only old water cells inside the disc changed");
            h.assertTrue(level.getBlockState(bucket).is(Blocks.WATER) && journal.reclaimWater(level,run.id,bucket),"Duelist's temporary water above the floor remains recoverable");
            journal.expire(level,level.getGameTime()+200);
            h.assertTrue(level.getFluidState(web).isEmpty() && !level.isEmptyBlock(web),"Expired overlay cannot bring back the pool");
            player.setPos(c.getX()-1.5,c.getY()+.1,c.getZ()+.5);
            MirrorArena.liftFromOldFloor(level,run,c,player);
            h.assertTrue(player.getY()==c.getY()+1,"Late reconnect also clears the raised floor");
            h.assertTrue(MirrorArena.ensureFloor(level,run,room,party) && level.getBlockState(foreign).is(Blocks.DIAMOND_BLOCK),"Repeat migration is idempotent");
            mirror.discard();run.closed=true;
            tickets.forEach(cp->level.setChunkForced(cp.x,cp.z,false));
        }).thenSucceed();
    }
}
