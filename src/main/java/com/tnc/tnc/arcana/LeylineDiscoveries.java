package com.tnc.tnc.arcana;

import com.tnc.tnc.life.routes.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Index only actually generated specimens. No searching or generating half a million blocks. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class LeylineDiscoveries extends SavedData {
    private final Set<BlockPos> sites=new LinkedHashSet<>();
    private static LeylineDiscoveries get(ServerLevel l){return l.getDataStorage().computeIfAbsent(t->{var d=new LeylineDiscoveries();for(long p:t.getLongArray("Sites"))if(d.sites.size()<8192)d.sites.add(BlockPos.of(p));return d;},LeylineDiscoveries::new,"tnc_leyline_sites");}
    public static void add(ServerLevel l,BlockPos p){var d=get(l);if(d.sites.size()<8192&&d.sites.add(p.immutable()))d.setDirty();}
    @SubscribeEvent public static void chunk(net.minecraftforge.event.level.ChunkEvent.Load e){if(!(e.getLevel() instanceof ServerLevel l)||!(e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk c))return;
        // Chunk load may be delivered off-thread. Schedule registry access on the server thread.
        l.getServer().execute(()->{
            for(var be:List.copyOf(c.getBlockEntities().values()))if(ArcanaEcology.owner(be)==null&&(be instanceof RoutePlantEntity||be instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity))add(l,be.getBlockPos());
            var sections=c.getSections();for(int i=0;i<sections.length;i++){var section=sections[i];if(!section.maybeHas(s->s.getBlock() instanceof com.tnc.tnc.dungeon.DungeonGateBlock))continue;
                boolean found=false;for(int y=0;y<16&&!found;y++)for(int x=0;x<16&&!found;x++)for(int z=0;z<16;z++)if(section.getBlockState(x,y,z).getBlock() instanceof com.tnc.tnc.dungeon.DungeonGateBlock){add(l,new BlockPos(c.getPos().getMinBlockX()+x,c.getSectionYFromSectionIndex(i)*16+y,c.getPos().getMinBlockZ()+z));found=true;break;}
            }
        });
    }
    private static boolean valid(ServerLevel l,BlockPos at){if(!l.hasChunkAt(at))return true;var state=l.getBlockState(at);if(state.getBlock() instanceof com.tnc.tnc.dungeon.DungeonGateBlock)return true;var be=l.getBlockEntity(at);return be instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity b?b.wild&&b.owner==null:be instanceof RoutePlantEntity r&&r.owner==null&&state.getValue(RoutePlantBlock.WILD);}
    @SubscribeEvent public static void broken(net.minecraftforge.event.level.BlockEvent.BreakEvent e){if(!e.isCanceled()&&e.getLevel() instanceof ServerLevel l){var d=get(l);if(d.sites.remove(e.getPos()))d.setDirty();}}
    public static boolean activate(ServerPlayer p,RouteNodeEntity n){var data=get(p.serverLevel());if(data.sites.removeIf(at->!valid(p.serverLevel(),at)))data.setDirty();var seen=com.tnc.tnc.journey.JourneyData.get(p.server).player(p.getUUID()).getCompound("LeylineExplored");String dim=p.level().dimension().location().toString();
        var target=get(p.serverLevel()).sites.stream().filter(at->at.distSqr(n.getBlockPos())<=500*500&&!seen.getBoolean(dim+"/"+at.asLong())).min(Comparator.comparingDouble(at->at.distSqr(n.getBlockPos()))).orElse(null);
        if(target==null||n.mana<200){p.displayClientMessage(net.minecraft.network.chat.Component.literal(target==null?"附近尚无已生成而未探索的魔植或地下城。继续向野外探索。":"寻迹需要200M。"),true);return false;}
        n.spend(200);var t=ArcanaPhysiology.data(p);t.putLong("LeylineUntil",ArcanaPhysiology.now(p)+600);t.putLong("LeylineTarget",target.asLong());t.putString("LeylineDimension",dim);com.tnc.tnc.journey.JourneyData.get(p.server).setDirty();return true;
    }
    public static void guide(ServerPlayer p){var t=ArcanaPhysiology.data(p);String dim=p.level().dimension().location().toString();if(t.getLong("LeylineUntil")<=ArcanaPhysiology.now(p)||!dim.equals(t.getString("LeylineDimension")))return;var at=BlockPos.of(t.getLong("LeylineTarget"));
        if(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))<144){var root=com.tnc.tnc.journey.JourneyData.get(p.server).player(p.getUUID());var seen=root.getCompound("LeylineExplored");seen.putBoolean(dim+"/"+at.asLong(),true);root.put("LeylineExplored",seen);t.remove("LeylineUntil");return;}
        if(com.tnc.tnc.mana.sight.ManaSightServer.enabled(p)){var from=p.getEyePosition().add(0,1,0);var to=from.add(net.minecraft.world.phys.Vec3.atCenterOf(at).subtract(from).normalize().scale(20));com.tnc.tnc.mana.sight.ManaSightServer.record(p.serverLevel(),from,to,200,0xffdc7c,4);}
    }
    @Override public CompoundTag save(CompoundTag t){t.putLongArray("Sites",sites.stream().mapToLong(BlockPos::asLong).toArray());return t;}
}
