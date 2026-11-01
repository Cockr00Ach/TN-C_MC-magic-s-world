package com.tnc.tnc.world;

import com.tnc.tnc.skyacademy.AcademyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Per-player first arrival: bounded server prefetch, then a client render-ready acknowledgement. */
@Mod.EventBusSubscriber(modid="tnc")
public final class SkyhavenArrival {
    private static final TicketType<UUID> TICKET=TicketType.create("tnc_skyhaven_arrival",UUID::compareTo);
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private static final class Job {
        final ServerPlayer player;final ServerLevel world;final BlockPos at;final long token,started=System.nanoTime();
        final List<ChunkPos> chunks;final int clientRadius;int issued,ready;boolean scene;
        Job(ServerPlayer p,BlockPos at){player=p;world=SkyIslandWorlds.island(p.server);this.at=at;token=p.getRandom().nextLong();
            var center=new ChunkPos(at);var list=new ArrayList<ChunkPos>();int radius=Math.min(8,Math.max(4,p.server.getPlayerList().getViewDistance()));
            clientRadius=Math.min(3,Math.max(1,p.server.getPlayerList().getViewDistance()-1));
            for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)list.add(new ChunkPos(center.x+x,center.z+z));
            list.sort(Comparator.comparingInt(c->Math.max(Math.abs(c.x-center.x),Math.abs(c.z-center.z))));chunks=List.copyOf(list);
        }
    }
    public static boolean active(ServerPlayer p){return JOBS.containsKey(p.getUUID());}
    public static void begin(ServerPlayer p,BlockPos at){if(JOBS.containsKey(p.getUUID()))return;var job=new Job(p,at);JOBS.put(p.getUUID(),job);send(job,"terrain");}
    private static void send(Job j,String phase){var t=new CompoundTag();t.putString("Type","arrival_loading");t.putLong("Token",j.token);t.putString("Phase",phase);t.putLong("At",j.at.asLong());t.putInt("Ready",j.ready);t.putInt("Total",j.chunks.size());t.putInt("ClientRadius",j.clientRadius);AcademyNetwork.send(j.player,t);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        for(var it=JOBS.entrySet().iterator();it.hasNext();){var j=it.next().getValue();if(j.player.server!=e.getServer())continue;
            if(j.player.hasDisconnected()){release(j);it.remove();continue;}
            if(System.nanoTime()-j.started>180_000_000_000L){release(j);it.remove();j.player.connection.disconnect(net.minecraft.network.chat.Component.literal("天空岛仍未准备完成，请重新进入世界。"));continue;}
            j.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);j.player.fallDistance=0;
            if(j.scene)continue;
            // Never synchronously load the entire view radius in one tick.
            for(int budget=5;budget>0&&j.issued<j.chunks.size();budget--){var c=j.chunks.get(j.issued++);j.world.getChunkSource().addRegionTicket(TICKET,c,0,j.player.getUUID());}
            j.ready=(int)j.chunks.subList(0,j.issued).stream().filter(c->j.world.getChunkSource().getChunkNow(c.x,c.z)!=null).count();
            if(e.getServer().getTickCount()%10==0)send(j,"terrain");
            if(j.ready==j.chunks.size()){
                j.player.teleportTo(j.world,j.at.getX()+.5,j.at.getY(),j.at.getZ()+.5,180,0);
                j.player.setRespawnPosition(j.world.dimension(),j.at,180,true,false);j.scene=true;send(j,"scene");
                com.mojang.logging.LogUtils.getLogger().info("[Skyhaven Arrival] {} surrounding chunks ready; waiting for client rendering",j.ready);
            }
        }
    }
    public static void ready(ServerPlayer p,long token){var j=JOBS.get(p.getUUID());if(j==null||j.token!=token||!j.scene||p.serverLevel()!=j.world)return;
        var root=p.getPersistentData();if(!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,10))root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,new CompoundTag());
        var flags=root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);flags.putBoolean("SkyhavenWaiting",false);flags.putBoolean("SkyhavenArrived",true);
        release(j);JOBS.remove(p.getUUID());send(j,"done");
        com.mojang.logging.LogUtils.getLogger().info("[Skyhaven Arrival] Client scene ready; first arrival released after {} seconds",(System.nanoTime()-j.started)/1_000_000_000.);
    }
    private static void release(Job j){for(var c:j.chunks.subList(0,j.issued))j.world.getChunkSource().removeRegionTicket(TICKET,c,0,j.player.getUUID());}
    @SubscribeEvent public static void protect(LivingAttackEvent e){if(e.getEntity() instanceof ServerPlayer p&&active(p))e.setCanceled(true);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){var j=JOBS.remove(e.getEntity().getUUID());if(j!=null)release(j);}
    @SubscribeEvent public static void stop(ServerStoppingEvent e){JOBS.values().removeIf(j->{if(j.player.server!=e.getServer())return false;release(j);return true;});}
    private SkyhavenArrival(){}
}
