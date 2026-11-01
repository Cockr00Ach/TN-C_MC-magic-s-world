package com.tnc.tnc.world.ink;

/** Huge bosses must not damage another simultaneously running fixture. Dev test world only. */
final class InkCombatTestIsolation {
    static void prepare(net.minecraft.gametest.framework.GameTestHelper helper){
        var origin=helper.absolutePos(net.minecraft.core.BlockPos.ZERO);var level=helper.getLevel();
        for(int x=Math.floorDiv(origin.getX()-128,16);x<=Math.floorDiv(origin.getX()+128,16);x++)
            for(int z=Math.floorDiv(origin.getZ()-128,16);z<=Math.floorDiv(origin.getZ()+128,16);z++){
                level.getChunk(x,z);level.setChunkForced(x,z,true);
            }
    }
    static void clear(net.minecraft.server.level.ServerLevel level){
        var entities=new java.util.ArrayList<net.minecraft.world.entity.Entity>();level.getAllEntities().forEach(entities::add);
        for(var e:entities)if(e instanceof net.minecraft.world.entity.Mob||e instanceof InkCranePhantomEntity||e instanceof InkTigerEffectEntity||e instanceof InkRitualEntity)e.discard();
    }
}
