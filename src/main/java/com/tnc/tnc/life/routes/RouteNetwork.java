package com.tnc.tnc.life.routes;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.tnc.tnc.production.MagicForgeBlockEntity;
/** Loaded nodes only; every visited wire and source shares one budget per second. */
@Mod.EventBusSubscriber(modid="tnc")
public final class RouteNetwork {
    private static final Map<ServerLevel,Set<BlockPos>> LOADED=new WeakHashMap<>();
    public static void add(ServerLevel l,BlockPos p){LOADED.computeIfAbsent(l,k->new LinkedHashSet<>()).add(p.immutable());}
    public static void remove(ServerLevel l,BlockPos p){var s=LOADED.get(l);if(s!=null)s.remove(p);}
    public static Collection<BlockPos> positions(ServerLevel l){return List.copyOf(LOADED.getOrDefault(l,Set.of()));}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){LOADED.clear();}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel l)||l.getGameTime()%20!=0)return;tickNetwork(l);}
    public static void tickNetwork(ServerLevel l){List<RouteNodeEntity> sources=new ArrayList<>();Map<BlockPos,Integer> budget=new HashMap<>();for(var p:positions(l)){if(!l.hasChunkAt(p))continue;if(l.getBlockEntity(p) instanceof RouteNodeEntity node){node.inRate=0;node.outRate=0;if(RouteAccess.allowed(l,p,node.owner)){budget.put(p,node.kind().rate);if(node.kind().supply()&&!(node.kind()==RouteKind.ANIMAL_COLLECTOR&&node.animalCharging)&&node.mana>0)sources.add(node);}}}
        sources.sort(Comparator.comparing(n->n.getBlockPos()));Collections.rotate(sources,sources.isEmpty()?0:(int)(l.getGameTime()/20%sources.size()));
        for(var source:sources){var paths=paths(l,source,budget);List<BlockPos> targets=new ArrayList<>(paths.keySet());targets.remove(source.getBlockPos());targets.sort(Comparator.comparingInt(p->rank(l,p,pathPriority(l,paths.get(p),source.priority))));
            if(source.priority==2){Collections.rotate(targets,targets.isEmpty()?0:(int)(l.getGameTime()/20%targets.size()));targets.sort(Comparator.comparingInt(p->rank(l,p,2)));}
            for(var p:targets){if(source.mana<=(source.kind().storage()?source.reserve:0))break;var path=paths.get(p);int amount=Math.max(0,source.mana-(source.kind().storage()?source.reserve:0));for(var at:path)amount=Math.min(amount,budget.getOrDefault(at,0));if(amount<=0)continue;
                var raw=l.getBlockEntity(p);int accepted=0;
                if(raw instanceof RouteNodeEntity dest){if(dest.kind()==RouteKind.VALVE||dest.kind()==RouteKind.MIRROR||dest.kind()==RouteKind.FAR_MIRROR||dest.kind().wire()||dest.kind().supply()&&!dest.kind().storage()&&dest.kind()!=RouteKind.VALVE&&dest.kind()!=RouteKind.MIRROR&&dest.kind()!=RouteKind.FAR_MIRROR&&dest.kind()!=RouteKind.INFUSER&&!(dest.kind()==RouteKind.ANIMAL_COLLECTOR&&dest.animalCharging))continue;if(source.kind().storage()&&dest.kind().storage())continue;accepted=dest.receive(amount);dest.inRate+=accepted;}
                else if(raw instanceof MagicForgeBlockEntity forge&&source.owner.equals(forge.owner())&&forge.formedForTransfer()){accepted=Math.min(amount,MagicForgeBlockEntity.MAX_CHARGE-forge.charge());forge.addCharge(accepted);}
                if(accepted>0){if(raw instanceof RouteNodeEntity destination&&destination.kind().storage())RouteProgress.awardOwner(l,source.owner,"network/stored");source.extract(accepted);source.outRate+=accepted;final int transferred=accepted;for(var at:path)budget.computeIfPresent(at,(k,v)->v-transferred);trace(l,source.getBlockPos(),p,accepted);}
            }
        }
    }
    private static int pathPriority(ServerLevel l,List<BlockPos> path,int fallback){for(var p:path)if(l.getBlockEntity(p) instanceof RouteNodeEntity n&&n.kind()==RouteKind.VALVE)return n.priority;return fallback;}
    private static int rank(ServerLevel l,BlockPos p,int priority){var raw=l.getBlockEntity(p);if(!(raw instanceof RouteNodeEntity n))return 1;if(n.kind().storage())return 3;if(n.kind()==RouteKind.MIRROR||n.kind()==RouteKind.FAR_MIRROR||n.kind()==RouteKind.VALVE||n.kind()==RouteKind.INFUSER&&n.inventory.getItem(0).isEmpty())return 4;boolean lamp=n.kind()==RouteKind.LAMP||n.kind()==RouteKind.BRIGHT_LAMP;return priority==1?(lamp?2:0):(lamp?0:1);}
    private static Map<BlockPos,List<BlockPos>> paths(ServerLevel l,RouteNodeEntity source,Map<BlockPos,Integer> budget){Map<BlockPos,List<BlockPos>> paths=new LinkedHashMap<>();Queue<BlockPos> queue=new ArrayDeque<>();BlockPos start=source.getBlockPos();paths.put(start,List.of(start));queue.add(start);
        while(!queue.isEmpty()&&paths.size()<256){BlockPos p=queue.remove();var raw=l.getBlockEntity(p);if(raw instanceof RouteNodeEntity boundary&&boundary.kind()==RouteKind.VALVE&&l.hasNeighborSignal(p))continue;List<BlockPos> neighbours=new ArrayList<>();for(var d:Direction.values())neighbours.add(p.relative(d));
            if(raw instanceof RouteNodeEntity n&&(n.kind()==RouteKind.MIRROR||n.kind()==RouteKind.FAR_MIRROR)){int range=n.kind()==RouteKind.MIRROR?12:24;for(var d:Direction.values())for(int distance=1;distance<=range;distance++){var q=p.relative(d,distance);if(!l.hasChunkAt(q))break;var state=l.getBlockState(q);if(l.getBlockEntity(q) instanceof RouteNodeEntity other&&(other.kind()==RouteKind.MIRROR||other.kind()==RouteKind.FAR_MIRROR)){neighbours.add(q);break;}if(!state.isAir()&&!state.getCollisionShape(l,q).isEmpty())break;}}
            for(var q:neighbours){if(paths.containsKey(q)||!RouteAccess.allowed(l,q,source.owner))continue;var entity=l.getBlockEntity(q);if(entity instanceof RouteNodeEntity n&&!source.owner.equals(n.owner))continue;if(!(entity instanceof RouteNodeEntity)&&!(entity instanceof MagicForgeBlockEntity))continue;if(entity instanceof MagicForgeBlockEntity f&&!source.owner.equals(f.owner()))continue;List<BlockPos> path=new ArrayList<>(paths.get(p));path.add(q);paths.put(q,path);if(entity instanceof RouteNodeEntity)queue.add(q);else budget.putIfAbsent(q,128);}
        }return paths;
    }
    public static void trace(ServerLevel l,BlockPos from,BlockPos to,int amount){if(amount<=0||l.getGameTime()%40!=0)return;var a=net.minecraft.world.phys.Vec3.atCenterOf(from);var b=net.minecraft.world.phys.Vec3.atCenterOf(to);for(int i=1;i<=4;i++){var v=a.lerp(b,i/5.);l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.22f,.9f,.74f),.5f),v.x,v.y,v.z,1,0,0,0,0);}}
}
