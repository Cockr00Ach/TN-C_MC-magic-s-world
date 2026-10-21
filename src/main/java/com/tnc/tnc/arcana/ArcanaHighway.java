package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaHighway {
    private static final Map<UUID,Entity> CARRIED=new HashMap<>();
    public static boolean ring(RouteNodeEntity n){return n.kind()==RouteKind.SKY_LAUNCHER||n.kind()==RouteKind.SKY_RELAY;}
    public static void select(ServerPlayer p,RouteNodeEntity n,ItemStack marker){var t=marker.getOrCreateTag();String dim=p.level().dimension().location().toString();if(!t.contains("SkyOrigin")||!dim.equals(t.getString("SkyDimension"))){t.putLong("SkyOrigin",n.getBlockPos().asLong());t.putString("SkyDimension",dim);p.displayClientMessage(net.minecraft.network.chat.Component.literal("已选发射端；右键20格内下一环，建立单向路径"),true);return;}
        var pos=BlockPos.of(t.getLong("SkyOrigin"));if(p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getBlockEntity(pos) instanceof RouteNodeEntity source&&ring(source)&&source.mayUse(p)&&n.mayUse(p)&&!pos.equals(n.getBlockPos())&&pos.distSqr(n.getBlockPos())<=400){source.getPersistentData().putLong("SkyTarget",n.getBlockPos().asLong());source.setChanged();p.serverLevel().sendBlockUpdated(pos,source.getBlockState(),source.getBlockState(),3);t.putLong("SkyOrigin",n.getBlockPos().asLong());p.displayClientMessage(net.minecraft.network.chat.Component.literal("路径已连接；继续选择下一环。潜行空手清除节点去向"),true);}
    }
    public static Vec3 center(BlockPos p){return Vec3.atCenterOf(p).add(0,.3,0);}
    public static List<BlockPos> path(ServerLevel l,RouteNodeEntity start){var result=new ArrayList<BlockPos>();var seen=new HashSet<BlockPos>();var current=start;
        while(result.size()<17&&seen.add(current.getBlockPos())){result.add(current.getBlockPos());var t=current.getPersistentData();if(!t.contains("SkyTarget"))break;var q=BlockPos.of(t.getLong("SkyTarget"));if(!l.hasChunkAt(q)||current.getBlockPos().distSqr(q)>400||!(l.getBlockEntity(q) instanceof RouteNodeEntity next)||!ring(next)||!start.owner.equals(next.owner)||!RouteAccess.allowed(l,q,start.owner))break;
            var hit=l.clip(new net.minecraft.world.level.ClipContext(center(current.getBlockPos()),center(q),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,null));if(hit.getType()!=HitResult.Type.MISS)break;current=next;
        }return result;
    }
    public static void tick(ServerLevel l,RouteNodeEntity n){if(n.kind()!=RouteKind.SKY_LAUNCHER)return;var path=path(l,n);var t=n.getPersistentData();if(path.size()<2){n.status="用星路标记杖选环，单段不超过20格";t.remove("SkyPath");return;}int cost=(path.size()-1)*8;if(!n.spend(cost)){n.status="缺少 "+cost+" M/s · 天路暂停";t.remove("SkyPath");return;}
        t.putLongArray("SkyPath",path.stream().mapToLong(BlockPos::asLong).toArray());t.putLong("SkyActiveUntil",l.getGameTime()+25);n.status="天路运行 · "+(path.size()-1)+"段 / "+cost+" M/s";
        for(int i=1;i<path.size();i++){var node=(RouteNodeEntity)l.getBlockEntity(path.get(i-1));node.getPersistentData().putLong("SkyActiveUntil",l.getGameTime()+25);l.sendBlockUpdated(node.getBlockPos(),node.getBlockState(),node.getBlockState(),3);com.tnc.tnc.mana.sight.ManaSightServer.record(l,center(path.get(i-1)),center(path.get(i)),8,0x8fdfff,4);}
    }
    public static double projection(Vec3 point,Vec3 a,Vec3 b){var d=b.subtract(a);return Math.max(0,Math.min(1,point.subtract(a).dot(d)/Math.max(.001,d.lengthSqr())));}
    @SubscribeEvent public static void travel(net.minecraftforge.event.TickEvent.LevelTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.level instanceof ServerLevel l))return;var moved=new HashSet<UUID>();int scanned=0;
        for(var pos:RouteNetwork.positions(l)){if(++scanned>2048)break;if(!l.hasChunkAt(pos)||!(l.getBlockEntity(pos) instanceof RouteNodeEntity source)||source.kind()!=RouteKind.SKY_LAUNCHER||source.getPersistentData().getLong("SkyActiveUntil")<=l.getGameTime())continue;var raw=source.getPersistentData().getLongArray("SkyPath");
            for(int i=1;i<raw.length;i++){var a=center(BlockPos.of(raw[i-1]));var b=center(BlockPos.of(raw[i]));if(!l.hasChunkAt(BlockPos.of(raw[i])))break;
                for(var entity:l.getEntities((Entity)null,new AABB(a,b).inflate(1.4),x->x instanceof ServerPlayer||x instanceof ItemEntity)){
                    if(moved.size()>=256||moved.contains(entity.getUUID()))continue;
                    if(entity instanceof ServerPlayer p){if(!p.getUUID().equals(source.owner)||p.isShiftKeyDown()||p.isSpectator()||!p.isAlive()||com.tnc.tnc.combat.DownedCombat.isDowned(p))continue;}
                    else if(entity instanceof ItemEntity item){var owner=item.getOwner();if(owner==null&&!item.getPersistentData().hasUUID("SkyOwner")||owner!=null&&!owner.getUUID().equals(source.owner)||owner==null&&!source.owner.equals(item.getPersistentData().getUUID("SkyOwner")))continue;item.getPersistentData().putUUID("SkyOwner",source.owner);}
                    var point=entity.position().add(0,entity instanceof ServerPlayer?.8:0,0);double at=projection(point,a,b);var nearest=a.lerp(b,at);if(point.distanceToSqr(nearest)>1)continue;
                    if(at>.96&&i==raw.length-1){
                        if(entity instanceof ItemEntity item){var end=BlockPos.of(raw[i]);net.minecraft.world.Container box=null;for(var q:List.of(end.below(),end.below(2),end.above()))if(l.hasChunkAt(q)&&RouteAccess.allowed(l,q,source.owner)&&(!(l.getBlockEntity(q) instanceof RouteNodeEntity owned)||source.owner.equals(owned.owner))&&l.getBlockEntity(q) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity c){box=c;break;}
                            if(box!=null){var remaining=net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null,box,item.getItem().copy(),null);item.setItem(remaining);if(remaining.isEmpty()){item.discard();continue;}}item.setDeltaMovement(Vec3.ZERO);if(!item.getPersistentData().contains("SkyWasNoGravity"))item.getPersistentData().putBoolean("SkyWasNoGravity",item.isNoGravity());item.setNoGravity(true);CARRIED.put(item.getUUID(),item);moved.add(item.getUUID());continue;
                        }else{entity.setDeltaMovement(b.subtract(a).normalize().scale(.18));continue;}
                    }
                    double speed=entity instanceof ServerPlayer p&&ArcanaEquipment.tailwind(p)?.9:.65;
                    var velocity=b.subtract(a).normalize().scale(speed).add(nearest.subtract(point).scale(.18));
                    if(!l.noCollision(entity,entity.getBoundingBox().move(velocity)))continue;
                    if(!CARRIED.containsKey(entity.getUUID())&&!entity.getPersistentData().contains("SkyWasNoGravity"))entity.getPersistentData().putBoolean("SkyWasNoGravity",entity.isNoGravity());entity.setNoGravity(true);entity.setDeltaMovement(velocity);entity.fallDistance=0;entity.hurtMarked=true;
                    if(entity instanceof ServerPlayer p){ArcanaPhysiology.data(p).putLong("SkyGraceUntil",ArcanaPhysiology.now(p)+100);p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));}
                    CARRIED.put(entity.getUUID(),entity);moved.add(entity.getUUID());
                }
            }
        }
        var it=CARRIED.entrySet().iterator();while(it.hasNext()){var entry=it.next();var entity=entry.getValue();if(entity.level()!=l)continue;if(!entity.isAlive()||!moved.contains(entry.getKey())){entity.setNoGravity(entity.getPersistentData().getBoolean("SkyWasNoGravity"));entity.getPersistentData().remove("SkyWasNoGravity");it.remove();}}
    }
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getLevel().isClientSide)return;var entity=e.getEntity();if(entity.getPersistentData().contains("SkyWasNoGravity")){entity.setNoGravity(entity.getPersistentData().getBoolean("SkyWasNoGravity"));entity.getPersistentData().remove("SkyWasNoGravity");CARRIED.remove(entity.getUUID());}}
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){var p=e.getEntity();if(p.getPersistentData().contains("SkyWasNoGravity")){p.setNoGravity(p.getPersistentData().getBoolean("SkyWasNoGravity"));p.getPersistentData().remove("SkyWasNoGravity");}}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){var p=e.getEntity();if(CARRIED.remove(p.getUUID())!=null)p.setNoGravity(p.getPersistentData().getBoolean("SkyWasNoGravity"));}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){CARRIED.values().forEach(x->x.setNoGravity(x.getPersistentData().getBoolean("SkyWasNoGravity")));CARRIED.clear();}
}
