package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CasinoPopulation {
    public static final int GUESTS=24;
    public static final BlockPos FRONT=new BlockPos(-930,190,28);
    public static final BlockPos FRONT_LOCAL=new BlockPos(98,100,288);
    private static UUID id(BlockPos origin,int i){return UUID.nameUUIDFromBytes(("tnc-casino:"+origin+":"+i).getBytes(StandardCharsets.UTF_8));}
    public static void prepareMove(ServerLevel l,BlockPos origin) {
        for(int i=0;i<=GUESTS;i++)if(l.getEntity(id(origin,i)) instanceof CasinoGuestEntity e){
            e.getNavigation().stop();e.setNoAi(true);e.setNoGravity(true);e.moveTo(origin.getX()+62.5,origin.getY()+96,origin.getZ()+230.5+i*.7,0,0);
        }
    }
    public static void ensure(ServerLevel l,BlockPos origin,List<BlockPos> localWalk) {
        var min=origin.offset(SkyCasinoUpgrade.MIN);var max=origin.offset(SkyCasinoUpgrade.MAX);
        // Respawn checks require the whole casino's entities to be loaded, avoiding duplicates.
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
            if(!l.hasChunk(x,z)||!l.areEntitiesLoaded(new ChunkPos(x,z).toLong()))return;
        var walk=localWalk.stream().map(origin::offset).toList();
        var front=origin.offset(FRONT_LOCAL);
        var guestWalk=walk.stream().filter(p->!p.equals(front)).toList();
        int spawned=0;
        for(int i=0;i<=GUESTS;i++) {
            UUID id=id(origin,i);
            var p=i==0?front:guestWalk.get(((i-1)*guestWalk.size()/GUESTS)%guestWalk.size());
            if(l.getEntity(id) instanceof CasinoGuestEntity existing){if(existing.layoutVersion()<3)existing.configure(i==0,i,p,walk);continue;}
            if(spawned>=4||!l.hasChunkAt(p))continue;
            var box=new AABB(p.getX()+.2,p.getY(),p.getZ()+.2,p.getX()+.8,p.getY()+1.95,p.getZ()+.8);
            if(!l.noCollision(box)||l.getBlockState(p.below()).getCollisionShape(l,p.below()).isEmpty())continue;
            var e=CasinoResidents.TYPE.get().create(l);if(e==null)continue;
            e.setUUID(id);e.configure(i==0,i,p,walk);if(l.addFreshEntity(e))spawned++;
        }
    }
}
