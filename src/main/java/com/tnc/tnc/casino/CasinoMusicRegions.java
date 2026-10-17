package com.tnc.tnc.casino;
import java.util.*;
import com.tnc.tnc.casino.machine.SkyCasinoUpgrade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class CasinoMusicRegions {
 private static final Map<ServerPlayer,String> SENT=new WeakHashMap<>();
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.getServer().getTickCount()%20!=0)return;var l=e.getServer().overworld();var s=SkyCasinoUpgrade.state(l);for(var p:e.getServer().getPlayerList().getPlayers()){
  boolean available=s.phase==2&&p.serverLevel()==l;String key=p.serverLevel().dimension().location()+":"+available+":"+s.origin;
  if(key.equals(SENT.get(p)))continue;var lo=s.origin.offset(SkyCasinoUpgrade.MIN);var hi=s.origin.offset(SkyCasinoUpgrade.MAX);
  var boxes=available?List.of(new AABB(lo.getX()-2,lo.getY()-2,lo.getZ()-2,hi.getX()+3,hi.getY()+3,hi.getZ()+3)):List.<AABB>of();
  com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new CasinoRoomPacket(p.serverLevel().dimension().location(),boxes));SENT.put(p,key);
 }}
}
