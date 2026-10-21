package com.tnc.tnc.world.ink;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
/** One transient moving region per ink dimension, never a persisted force-load. */
public final class InkDragonKeepAlive {
    private static final TicketType<UUID> TYPE=TicketType.create("tnc_ink_dragon",Comparator.comparing(UUID::toString),60);
    private record Active(ChunkPos chunk,UUID id){}
    private static final Map<ServerLevel,Active> ACTIVE=new IdentityHashMap<>();
    private InkDragonKeepAlive(){}
    public static boolean active(ServerLevel level){return ACTIVE.containsKey(level);}
    public static void update(ServerLevel level,Collection<InkDragonVisibility.Snapshot> snapshots){
        if(!level.dimension().equals(InkRealmTravel.INK_REALM)||level.players().isEmpty()){release(level);return;}
        var selected=snapshots.stream().filter(s->level.players().stream().anyMatch(p->InkDragonVisibility.distanceSquared(s,p.position())<512*512))
                .min(Comparator.comparingDouble(s->level.players().stream().mapToDouble(p->InkDragonVisibility.distanceSquared(s,p.position())).min().orElse(Double.MAX_VALUE))).orElse(null);
        if(selected==null){release(level);return;}
        var next=new Active(new ChunkPos(net.minecraft.core.BlockPos.containing(selected.position())),selected.id());
        var old=ACTIVE.get(level);
        if(old!=null&&!old.equals(next))release(level);
        // Distance 4: at most 9x9 full chunks, 5x5 entity-ticking chunks around one boss.
        level.getChunkSource().addRegionTicket(TYPE,next.chunk(),4,next.id());ACTIVE.put(level,next);
    }
    public static void release(ServerLevel level){var old=ACTIVE.remove(level);if(old!=null)level.getChunkSource().removeRegionTicket(TYPE,old.chunk(),4,old.id());}
    public static void clear(){for(var level:new ArrayList<>(ACTIVE.keySet()))release(level);}
}
