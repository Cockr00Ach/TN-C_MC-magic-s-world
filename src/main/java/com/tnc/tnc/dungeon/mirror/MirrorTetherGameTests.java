package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.dungeon.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;

/** A pre-removal save can be reopened halfway through an active pulse. */
@GameTestHolder("tncmirror") @PrefixGameTestTemplate(false)
public final class MirrorTetherGameTests {
    @GameTest(template="building_test_empty",batch="echo_legacy_room",timeoutTicks=160)
    public static void savedChargedMirrorResumesAtOriginalHealthAcrossFormerCycles(GameTestHelper h){
        var server=h.getLevel().getServer();
        var level=Objects.requireNonNull(server.getLevel(DungeonRealm.KEY));
        var player=AdventureGameTests.player(h);
        var dungeon=DungeonData.get(server);
        var run=new DungeonData.Run(UUID.randomUUID(),player.getUUID(),84,dungeon.nextSlot++,DungeonTheme.ASTRAL);
        run.floor=30;
        dungeon.runs.put(run.id,run);
        var room=run.graph().rooms.stream().filter(r->r.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
        var center=DungeonRealm.center(run,room);
        var chunk=new ChunkPos(center);
        boolean owned=level.setChunkForced(chunk.x,chunk.z,true);
        level.getChunk(chunk.x,chunk.z);
        var copy=Objects.requireNonNull(MirrorEntities.DOUBLE.get().create(level));
        copy.bind(player,run.id,center);
        copy.setPos(center.getX()+3.5,center.getY()+1,center.getZ()+.5);
        copy.setHealth(7);
        var image=copy.saveWithoutId(new CompoundTag());
        image.putBoolean("MirrorCharged",true);
        var record=new CompoundTag();
        record.putUUID("Entity",copy.getUUID());
        record.putLong("At",copy.blockPosition().asLong());
        record.put("Original",image.copy());
        record.put("Live",image);
        var people=new CompoundTag();
        people.put(player.getStringUUID(),record);
        var state=new CompoundTag();
        state.putInt("Phase",MirrorFight.MAGIC);
        state.putInt("Room",room.id());
        state.putLong("Origin",center.asLong());
        state.put("People",people);
        state.putInt("Tide",318);
        state.putUUID("Link",copy.getUUID());
        run.state().events.put("EchoingMirror",state);
        run.state().events.putInt("MirrorPreviewRoom",room.id());
        dungeon.profile(player.getUUID()).putUUID("Run",run.id);
        player.teleportTo(level,center.getX()+2,center.getY()+1,center.getZ()+.5,0,0);

        h.startSequence().thenWaitUntil(()->h.assertTrue(level.areEntitiesLoaded(chunk.toLong())
                && level.isPositionEntityTicking(center),"Waiting for the persisted room")).thenExecute(()->{
            float before=player.getHealth();
            for(int i=0;i<660;i++){
                MirrorFight.tick(level,run,room,List.of(player));
                h.assertTrue(!state.contains("Tide") && !state.contains("Link"),"Old timer and receiver stay retired on room tick "+i);
            }
            var loaded=(EchoDouble)level.getEntity(copy.getUUID());
            h.assertTrue(loaded!=null && loaded.ownerId().equals(player.getUUID()) && loaded.getHealth()==7,"Snapshot restored the same wounded reflection");
            h.assertTrue(!loaded.saveWithoutId(new CompoundTag()).contains("MirrorCharged"),"Saved charge is ignored on load and omitted on save");
            h.assertTrue(loaded.getPersistentData().getCompound("MirrorSpellbook").getList("Pool",net.minecraft.nbt.Tag.TAG_STRING).isEmpty(),
                    "An old preview reflection keeps its empty loadout after recovery");
            h.assertTrue(player.getHealth()==before && state.getInt("Phase")==MirrorFight.MAGIC,"Player and original battle phase survive multiple old pulse intervals");
            MirrorFight.cleanup(server,run);
            run.closed=true;
            if(owned)level.setChunkForced(chunk.x,chunk.z,false);
        }).thenSucceed();
    }
}
