package com.tnc.tnc.world.eartharena;

import com.tnc.tnc.boss.earth.StoneLancerEntity;
import com.tnc.tnc.boss.earth.StoneLancerRegistration;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One persistent central guardian; a party entering the disc wakes it. */
@Mod.EventBusSubscriber(modid="tnc")
public final class EarthArenaEncounter {
    private static final Set<Long> FORCED = new HashSet<>();
    private static final net.minecraft.server.level.TicketType<ChunkPos> TICKET = net.minecraft.server.level.TicketType.create(
            "tnc_earth_arena_guardian",java.util.Comparator.comparingLong(ChunkPos::toLong),40);
    private static int readyTicks,missingTicks;
    public static boolean participant(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof ServerPlayer && entity.isAlive() && !entity.isSpectator()
                && entity.level().dimension().equals(EarthArena.KEY) && EarthArenaLayout.inside(entity.getX(),entity.getY(),entity.getZ());
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||!level.dimension().equals(EarthArena.KEY)||level.getGameTime()%10!=0)return;
        update(level);
    }
    public static void update(ServerLevel level) {
        var state=EarthArenaEncounterState.get(level);
        var party=level.players().stream().filter(EarthArenaEncounter::participant).toList();
        boolean nearby=level.players().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&EarthArenaLayout.distance(p.getX(),p.getZ())<EarthArenaLayout.RADIUS+64);
        if(!nearby){
            release(level);readyTicks=0;missingTicks=0;
            if(state.defeated){state.defeated=false;state.boss=null;state.setDirty();}
            if(state.boss!=null&&level.getEntity(state.boss) instanceof StoneLancerEntity boss)boss.setArenaAwake(false);
            return;
        }
        for(int x=7;x<=8;x++)for(int z=-1;z<=0;z++){
            long key=ChunkPos.asLong(x,z);
            var pos=new ChunkPos(x,z);
            level.getChunkSource().addRegionTicket(TICKET,pos,2,pos);FORCED.add(key);
        }
        if((readyTicks+=10)<40||!level.getChunkSource().hasChunk(8,0))return;
        var centre=level.getChunk(8,0);
        if(!EarthArenaUpgrade.ready(centre).isDone())return;
        StoneLancerEntity boss=state.boss==null?null:level.getEntity(state.boss) instanceof StoneLancerEntity found?found:null;
        if(state.defeated){if(party.isEmpty()){state.defeated=false;state.boss=null;state.setDirty();}return;}
        if(boss==null){
            if(state.boss!=null&&(missingTicks+=10)<200)return;
            // On reload entity storage may finish after the terrain chunk. Reuse any loaded guardian.
            var existing=level.getEntitiesOfClass(StoneLancerEntity.class,new net.minecraft.world.phys.AABB(new BlockPos(EarthArenaLayout.X,EarthArenaLayout.FLOOR,0)).inflate(40),entity->entity.isArenaBound()&&entity.isAlive());
            if(!existing.isEmpty())boss=existing.get(0);
            else {boss=StoneLancerRegistration.TYPE.create(level);boss.bindToArena();boss.setPos(EarthArenaLayout.X+.5,EarthArenaLayout.FLOOR+1,.5);level.addFreshEntity(boss);}
            state.boss=boss.getUUID();state.setDirty();missingTicks=0;
        }
        if(!boss.isAlive())return;
        boss.setArenaAwake(!party.isEmpty());
        if(!party.isEmpty()){
            var guardian=boss;
            boss.setTarget(party.stream().min(java.util.Comparator.comparingDouble(guardian::distanceToSqr)).orElseThrow());
        }
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if(event.getEntity() instanceof StoneLancerEntity boss&&boss.isArenaBound()&&boss.level() instanceof ServerLevel level){
            var state=EarthArenaEncounterState.get(level);state.defeated=true;state.setDirty();
        }
    }
    private static void release(ServerLevel level){for(long key:FORCED){var pos=new ChunkPos(key);level.getChunkSource().removeRegionTicket(TICKET,pos,2,pos);}FORCED.clear();}
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){var level=event.getServer().getLevel(EarthArena.KEY);if(level!=null)release(level);readyTicks=0;missingTicks=0;}
}
