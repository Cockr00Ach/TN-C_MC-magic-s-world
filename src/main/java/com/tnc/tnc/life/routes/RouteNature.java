package com.tnc.tnc.life.routes;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Only fresh wilderness chunks; installs no structures and never changes the town. */
@Mod.EventBusSubscriber(modid="tnc")
public final class RouteNature {
    // First-load events can be delivered before the full chunk is published in ServerChunkCache.
    // Defer only to the next server ticks; do not request a chunk ticket or retry on a later visit.
    private record Pending(ServerLevel level, net.minecraft.world.level.ChunkPos pos, WeakReference<ChunkAccess> original, long expires) {}
    private static final Queue<Pending> QUEUE = new ConcurrentLinkedQueue<>();

    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
                || level != level.getServer().overworld()) return;
        var pos = event.getChunk().getPos();
        if (WildPlantCommunities.eligible(level.getSeed(),pos))
            QUEUE.add(new Pending(level,pos,new WeakReference<>(event.getChunk()),level.getGameTime()+200));
    }

    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event) {
        QUEUE.clear();
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) return;
        List<Pending> waiting = new ArrayList<>();
        for (int jobs=0; jobs<8; jobs++) {
            var next = QUEUE.poll();
            if (next == null) break;
            var level = next.level();
            var original = next.original().get();
            if (original == null || level.getGameTime()>next.expires()) continue;
            var chunk = level.getChunkSource().getChunkNow(next.pos().x,next.pos().z);
            if (chunk == null) {
                waiting.add(next);
            } else if (chunk == original) {
                WildPlantCommunities.populate(level,chunk);
            }
        }
        QUEUE.addAll(waiting);
    }
    @SubscribeEvent public static void bolt(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(!(e.getLevel() instanceof ServerLevel l)||!(e.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt))return;if(bolt.getCause()!=null||bolt.getPersistentData().getBoolean("TncArtificial"))return;for(var p:BlockPos.betweenClosed(bolt.blockPosition().offset(-3,-1,-3),bolt.blockPosition().offset(3,2,3)))if(l.hasChunkAt(p)&&l.getBlockEntity(p) instanceof RoutePlantEntity be&&be.kind()==NewPlantKind.STORM&&be.growth>=18000&&RouteAccess.allowed(l,p,be.owner)&&MagicSoilBlock.growing(l,p))be.naturalLightning();}
}
