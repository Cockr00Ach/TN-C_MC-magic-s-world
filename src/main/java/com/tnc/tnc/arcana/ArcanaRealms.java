package com.tnc.tnc.arcana;

import com.tnc.tnc.life.routes.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Persistent bounded cross-dimensional pairs. Maintenance and transport debit the real tower. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaRealms extends SavedData {
    private final List<CompoundTag> links=new ArrayList<>();
    public static ArcanaRealms get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(t->{var d=new ArcanaRealms();for(Tag raw:t.getList("Links",10))if(d.links.size()<256)d.links.add(((CompoundTag)raw).copy());return d;},ArcanaRealms::new,"tnc_mana_realms");}
    public static boolean barren(Level l){return l.dimension().equals(Level.NETHER)||l.dimension().equals(Level.END);}
    public static ResourceKey<Level> dimension(String s){return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse(s));}
    private static GlobalPos endpoint(CompoundTag t,boolean source){return GlobalPos.of(dimension(t.getString(source?"SourceDimension":"TargetDimension")),BlockPos.of(t.getLong(source?"Source":"Target")));}
    private static boolean at(CompoundTag t,ServerLevel l,BlockPos p){return endpoint(t,true).equals(GlobalPos.of(l.dimension(),p))||endpoint(t,false).equals(GlobalPos.of(l.dimension(),p));}
    public static boolean powered(ServerLevel l,BlockPos p){if(!barren(l))return true;long now=l.getServer().overworld().getGameTime();for(var t:get(l.getServer()).links)if(t.getLong("ActiveUntil")>now&&endpoint(t,false).dimension().equals(l.dimension())&&endpoint(t,false).pos().distSqr(p)<=48*48)return true;return false;}
    public static long[] nearby(ServerPlayer p){long now=p.server.overworld().getGameTime();return get(p.server).links.stream().filter(t->t.getLong("ActiveUntil")>now&&endpoint(t,false).dimension().equals(p.level().dimension())&&endpoint(t,false).pos().distToCenterSqr(p.position())<160*160).mapToLong(t->t.getLong("Target")).toArray();}
    public static RouteNodeEntity tower(ServerLevel l,BlockPos p,UUID owner,int radius){RouteNodeEntity best=null;double distance=(double)radius*radius;for(var at:RouteNetwork.positions(l))if(at.distSqr(p)<=distance&&l.hasChunkAt(at)&&l.getBlockEntity(at) instanceof RouteNodeEntity n&&n.kind()==RouteKind.ABYSS_TOWER&&Objects.equals(owner,n.owner)&&ArcanaDevices.formed(l,at)){best=n;distance=at.distSqr(p);}return best;}

    public static String link(ServerPlayer p,GlobalPos first,GlobalPos second){
        if(first.dimension().equals(second.dimension()))return "同维度请走普通镜面配对";
        var source=first.dimension().equals(Level.OVERWORLD)?first:second;var target=source==first?second:first;
        if(!source.dimension().equals(Level.OVERWORLD)||!target.dimension().equals(Level.NETHER)&&!target.dimension().equals(Level.END))return "跨维棱镜连接主世界与下界／末地";
        var a=p.server.getLevel(source.dimension());var b=p.server.getLevel(target.dimension());if(a==null||b==null)return "维度未就绪";
        // Only previously selected, actually placed endpoints may acquire tickets.
        if(!a.hasChunkAt(source.pos()))return "主世界端尚未加载；先在塔旁完成端点部署";
        if(!b.hasChunkAt(target.pos())||!(a.getBlockEntity(source.pos()) instanceof RouteNodeEntity x)||!(b.getBlockEntity(target.pos()) instanceof RouteNodeEntity y)||x.kind()!=RouteKind.DIMENSION_PRISM||y.kind()!=RouteKind.DIMENSION_PRISM||!x.mayUseOwner(p.getUUID())||!y.mayUseOwner(p.getUUID()))return "需要自己有权限的两面高维棱镜";
        var tower=tower(a,source.pos(),p.getUUID(),4);if(tower==null)return "主世界棱镜须在成型深渊塔四格内";
        var data=get(p.server);if(data.links.stream().filter(t->p.getUUID().equals(t.getUUID("Owner"))).count()>=4)return "每位玩家最多维持四条跨维通道";
        RouteMirrorLinks.unlink(a,x);RouteMirrorLinks.unlink(b,y);
        x.mirrorTarget=target.pos();x.getPersistentData().putString("MirrorTargetDimension",target.dimension().location().toString());y.mirrorTarget=source.pos();y.getPersistentData().putString("MirrorTargetDimension",source.dimension().location().toString());
        var t=new CompoundTag();t.putString("SourceDimension",source.dimension().location().toString());t.putLong("Source",source.pos().asLong());t.putString("TargetDimension",target.dimension().location().toString());t.putLong("Target",target.pos().asLong());t.putLong("Tower",tower.getBlockPos().asLong());t.putUUID("Owner",p.getUUID());t.putUUID("Ticket",UUID.randomUUID());data.links.add(t);tickets(p.server,t,true);sync(x);sync(y);data.setDirty();return "";
    }
    /** Selecting the origin pins only the one endpoint chunk until binding or timeout. */
    public static void pin(ServerLevel l,RouteNodeEntity n){if(n.kind()!=RouteKind.DIMENSION_PRISM)return;n.getPersistentData().putLong("BindingPinUntil",l.getServer().overworld().getGameTime()+6000);pinChunks(l,n,true);}
    public static void unpin(ServerLevel l,RouteNodeEntity n){if(!n.getPersistentData().contains("BindingPinUntil"))return;pinChunks(l,n,false);n.getPersistentData().remove("BindingPinUntil");}
    private static void pinChunks(ServerLevel l,RouteNodeEntity n,boolean load){var seen=new HashSet<Long>();for(int dx:new int[]{-5,5})for(int dz:new int[]{-5,5}){var at=n.getBlockPos().offset(dx,0,dz);var c=new net.minecraft.world.level.ChunkPos(at);if(seen.add(c.toLong()))net.minecraftforge.common.world.ForgeChunkManager.forceChunk(l,"tnc",n.getBlockPos(),c.x,c.z,load,false);}}
    private static void sync(RouteNodeEntity n){n.setChanged();n.getLevel().sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);}
    private static void tickets(MinecraftServer s,CompoundTag t,boolean load){for(boolean source:new boolean[]{true,false}){var gp=endpoint(t,source);var l=s.getLevel(gp.dimension());if(l==null)continue;var chunks=new HashSet<Long>();chunks.add(new net.minecraft.world.level.ChunkPos(gp.pos()).toLong());if(source){var tower=BlockPos.of(t.getLong("Tower"));for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})chunks.add(new net.minecraft.world.level.ChunkPos(tower.offset(x,0,z)).toLong());}for(long raw:chunks){var c=new net.minecraft.world.level.ChunkPos(raw);net.minecraftforge.common.world.ForgeChunkManager.forceChunk(l,"tnc",t.getUUID("Ticket"),c.x,c.z,load,false);if(load)l.getChunk(c.x,c.z);}}}
    public static void unlink(ServerLevel l,BlockPos p){var d=get(l.getServer());var it=d.links.iterator();while(it.hasNext()){var t=it.next();if(!at(t,l,p))continue;tickets(l.getServer(),t,false);for(boolean source:new boolean[]{true,false}){var gp=endpoint(t,source);var realm=l.getServer().getLevel(gp.dimension());if(realm!=null&&realm.hasChunkAt(gp.pos())&&realm.getBlockEntity(gp.pos()) instanceof RouteNodeEntity n){n.mirrorTarget=null;n.getPersistentData().remove("MirrorTargetDimension");sync(n);}}it.remove();d.setDirty();}}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH) public static void planting(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e){if(e.getLevel() instanceof ServerLevel l&&RoutePlanting.magic(e.getPlacedBlock())&&!powered(l,e.getPos())){e.setCanceled(true);if(e.getEntity() instanceof ServerPlayer p)p.displayClientMessage(net.minecraft.network.chat.Component.literal("异界无魔荒漠：请在通电棱镜48格绿洲内种植。"),true);}}
    @SubscribeEvent public static void bonemeal(net.minecraftforge.event.entity.player.BonemealEvent e){if(e.getLevel() instanceof ServerLevel l&&RoutePlanting.magic(e.getBlock())&&!powered(l,e.getPos()))e.setCanceled(true);}
    @SubscribeEvent public static void start(net.minecraftforge.event.server.ServerStartedEvent e){for(var t:get(e.getServer()).links){t.remove("ActiveUntil");tickets(e.getServer(),t,true);}}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var s=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(s==null||s.overworld().getGameTime()%20!=0)return;pump(s);}
    public static void pump(MinecraftServer s){var d=get(s);long now=s.overworld().getGameTime();
        for(var t:List.copyOf(d.links)){var a=endpoint(t,true);var b=endpoint(t,false);var l=s.getLevel(a.dimension());var r=s.getLevel(b.dimension());if(l==null||r==null)continue;
            if(!(l.getBlockEntity(a.pos()) instanceof RouteNodeEntity origin)||!(r.getBlockEntity(b.pos()) instanceof RouteNodeEntity dest)||!(l.getBlockEntity(BlockPos.of(t.getLong("Tower"))) instanceof RouteNodeEntity heart)||origin.kind()!=RouteKind.DIMENSION_PRISM||dest.kind()!=RouteKind.DIMENSION_PRISM||heart.kind()!=RouteKind.ABYSS_TOWER||!Objects.equals(t.getUUID("Owner"),heart.owner)||!Objects.equals(heart.owner,origin.owner)||!Objects.equals(origin.owner,dest.owner)||!a.pos().equals(dest.mirrorTarget)||!b.pos().equals(origin.mirrorTarget)){unlink(l,a.pos());continue;}
            if(!RouteAccess.allowed(l,a.pos(),heart.owner)||!RouteAccess.allowed(r,b.pos(),heart.owner)||!ArcanaDevices.formed(l,heart.getBlockPos())||!heart.spend(100)){t.remove("ActiveUntil");dest.status="跨维维护断供 · 主世界塔需要100M/s";sync(dest);continue;}
            t.putLong("ActiveUntil",now+25);int moved=ArcanaEnergy.move(heart,dest,Math.min(Math.max(0,4096-heart.outRate),heart.mana));heart.outRate+=moved;dest.inRate+=moved;dest.status="跨维绿洲48格 · 维护100M/s · 流入"+moved+"M/s";origin.status="跨维心脏 · 维护100M/s · 输送"+moved+"M/s";sync(dest);sync(origin);sync(heart);d.setDirty();
        }
        for(var l:s.getAllLevels())for(var pos:RouteNetwork.positions(l))if(l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof RouteNodeEntity n&&n.getPersistentData().contains("BindingPinUntil")&&n.getPersistentData().getLong("BindingPinUntil")<=now)unpin(l,n);
    }
    public static RouteNodeEntity beacon(ServerPlayer p){var direct=tower(p.serverLevel(),p.blockPosition(),p.getUUID(),64);if(direct!=null)return direct;long now=p.server.overworld().getGameTime();for(var t:get(p.server).links)if(t.getLong("ActiveUntil")>now&&p.getUUID().equals(t.getUUID("Owner"))&&endpoint(t,false).dimension().equals(p.level().dimension())&&endpoint(t,false).pos().distToCenterSqr(p.position())<=32*32){var l=p.server.getLevel(endpoint(t,true).dimension());if(l!=null&&l.getBlockEntity(BlockPos.of(t.getLong("Tower"))) instanceof RouteNodeEntity n&&ArcanaDevices.formed(l,n.getBlockPos()))return n;}return null;}
    @Override public CompoundTag save(CompoundTag t){var list=new ListTag();links.forEach(x->list.add(x.copy()));t.put("Links",list);return t;}
}
