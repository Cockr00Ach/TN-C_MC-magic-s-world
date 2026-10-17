package com.tnc.tnc.mana.sight;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.tnc.tnc.life.routes.*;
/** Bounded batches, sent only to nearby subscribers. Balances are never mutated here. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ManaSightServer {
 private record Edge(Vec3 from,Vec3 to,int color,int kind){}
 private static final Map<ServerPlayer,Boolean> SUBSCRIBERS=new WeakHashMap<>();
 private static final Map<ServerLevel,LinkedHashMap<Edge,Integer>> QUEUES=new WeakHashMap<>();
 public static void subscribe(ServerPlayer p,boolean enabled){if(enabled)SUBSCRIBERS.put(p,true);else SUBSCRIBERS.remove(p);}
 public static void record(ServerLevel l,Vec3 from,Vec3 to,int amount,int color,int kind){if(amount<=0||SUBSCRIBERS.isEmpty())return;
  boolean interested=SUBSCRIBERS.keySet().stream().anyMatch(p->p.serverLevel()==l&&(p.position().distanceToSqr(from)<96*96||p.position().distanceToSqr(to)<96*96));if(!interested)return;
  var f=new ManaFlow(from,to,Math.min(65535,amount),color,kind);if(!f.valid())return;var q=QUEUES.computeIfAbsent(l,k->new LinkedHashMap<>());var key=new Edge(from,to,color,kind);if(q.size()<512||q.containsKey(key))q.merge(key,f.amount(),(a,b)->Math.min(65535,a+b));
 }
 public static void transfer(ServerLevel l,BlockPos a,BlockPos b,int amount){
  if(amount<=0||SUBSCRIBERS.isEmpty())return;
  Vec3 from=Vec3.atCenterOf(a),to=Vec3.atCenterOf(b);int kind=ManaFlow.FIBER,color=0x63e9ef;
  for(var p:l.players())if(p.blockPosition().equals(a)){from=p.position().add(0,1.3,0);kind=ManaFlow.PLAYER;color=0xb9f8ff;break;}
  var source=l.getBlockState(a);if(RoutePlanting.magic(source)){kind=ManaFlow.PLANT;color=0x76f7a9;from=from.add(0,.18,0);}
  if(l.getBlockEntity(a) instanceof RouteNodeEntity n){if(n.kind()==RouteKind.GOLD_WIRE)color=0xffad9a;if(n.kind()==RouteKind.CRYSTAL_WIRE)color=0xbca3ff;if(n.kind()==RouteKind.MIRROR||n.kind()==RouteKind.FAR_MIRROR){kind=ManaFlow.MIRROR;color=0xb7b3ff;}}
  if(kind==ManaFlow.FIBER&&l.getBlockEntity(a)==null)for(var beast:l.getEntitiesOfClass(com.tnc.tnc.life.pasture.PastureAnimal.class,new AABB(a).inflate(1))){from=beast.position().add(0,beast.getBbHeight()*.6,0);kind=ManaFlow.ANIMAL;color=0xffca85;break;}
  record(l,from,to,amount,color,kind);
 }
 public static void emission(ServerLevel l,BlockPos p,int amount){record(l,Vec3.atCenterOf(p).add(0,.2,0),Vec3.atCenterOf(p).add(0,1.2,0),amount,0x7df5b9,ManaFlow.EMISSION);}
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.getServer().getTickCount()%5!=0)return;
  SUBSCRIBERS.keySet().removeIf(p->p.isRemoved()||p.hasDisconnected());
  for(var entry:QUEUES.entrySet()){var l=entry.getKey();for(var p:List.copyOf(SUBSCRIBERS.keySet())){if(p.serverLevel()!=l)continue;var flows=new ArrayList<ManaFlow>();for(var f:entry.getValue().entrySet()){if(p.position().distanceToSqr(f.getKey().from)>96*96&&p.position().distanceToSqr(f.getKey().to)>96*96)continue;var edge=f.getKey();flows.add(new ManaFlow(edge.from,edge.to,f.getValue(),edge.color,edge.kind));if(flows.size()==128)break;}if(!flows.isEmpty())com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new ManaFlowPacket(l.dimension().location(),flows));}entry.getValue().clear();}
 }
 @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){SUBSCRIBERS.remove(e.getEntity());}
 @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){SUBSCRIBERS.clear();QUEUES.clear();}
}
