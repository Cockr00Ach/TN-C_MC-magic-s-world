package com.tnc.tnc.world.stonecrest;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One bounded checkpoint at a time. Never load chunks synchronously from worldgen. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class LargeLandmarkJobs {
    private record Request(String asset,BlockPos origin) { }
    private static final Map<ServerLevel,Set<Request>> REQUESTS=new ConcurrentHashMap<>();
    private static final Map<MinecraftServer,List<ChunkPos>> TICKETS=new WeakHashMap<>();
    private static final Map<MinecraftServer,ServerBossEvent> BARS=new WeakHashMap<>();
    private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_landmark",Comparator.comparingLong(ChunkPos::toLong));
    static void request(ServerLevel l,String asset,BlockPos origin) {
        if (l.dimension()==Level.OVERWORLD) REQUESTS.computeIfAbsent(l,k->ConcurrentHashMap.newKeySet()).add(new Request(asset,origin.immutable()));
    }
    private static void drain(ServerLevel l) {
        var q=REQUESTS.get(l); if (q==null) return; var data=LandmarkData.get(l);
        for (var r:List.copyOf(q)) {
            q.remove(r); var j=new LandmarkData.Job(r.asset,r.origin);
            if (data.jobs.containsKey(j.key())) continue;
            try {
                var m=j.manifest();
                if (j.origin.getY()<l.getMinBuildHeight()+1 || j.origin.getY()+m.dimensions().getY()>l.getMaxBuildHeight())
                    throw new IllegalStateException("Landmark outside world height");
                for (var other:data.jobs.values()) if (other.phase>=0 && overlaps(j,other))
                    throw new IllegalStateException("Overlapping landmark "+other.key());
                for (var other:AbyssCitadelData.get(l).jobs.values()) if (other.phase>=0
                        && j.origin.getX()<other.center().getX()+220 && j.origin.getX()+m.dimensions().getX()>other.center().getX()-220
                        && j.origin.getZ()<other.center().getZ()+180 && j.origin.getZ()+m.dimensions().getZ()>other.center().getZ()-180)
                    throw new IllegalStateException("Overlaps an abyss site");
            } catch (Exception e) { j.phase=-1; j.error=e.toString(); }
            data.jobs.put(j.key(),j); data.setDirty();
            LogUtils.getLogger().info("[TN-C Landmark] queued {} at {}",j.asset,j.origin);
        }
    }
    private static boolean overlaps(LandmarkData.Job a,LandmarkData.Job b) {
        var ad=a.manifest().dimensions(); var bd=b.manifest().dimensions();
        return a.origin.getX()<b.origin.getX()+bd.getX()+16 && a.origin.getX()+ad.getX()+16>b.origin.getX()
                && a.origin.getZ()<b.origin.getZ()+bd.getZ()+16 && a.origin.getZ()+ad.getZ()+16>b.origin.getZ();
    }
    static boolean conflictsWithAbyss(ServerLevel l,BlockPos origin) {
        int x=origin.getX()+122,z=origin.getZ()+70;
        for (var j:LandmarkData.get(l).jobs.values()) if (j.phase>=0) {
            var d=j.manifest().dimensions();
            if (j.origin.getX()<x+220 && j.origin.getX()+d.getX()>x-220
                    && j.origin.getZ()<z+180 && j.origin.getZ()+d.getZ()>z-180) return true;
        }
        return false;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase!=TickEvent.Phase.END) return; var l=e.getServer().overworld(); if (l==null) return;
        drain(l); var data=LandmarkData.get(l);
        var j=data.jobs.values().stream().filter(v->v.phase>=0 && v.phase<3).findFirst().orElse(null);
        if (j==null) { release(l); var bar=BARS.remove(e.getServer()); if (bar!=null) bar.removeAllPlayers(); return; }
        try {
            j.waiting=false;
            if (!j.initialized) {
                boolean restored=LandmarkJournal.restore(l,j);
                if (j.phase>0 && !restored) throw new IllegalStateException("Missing immutable landmark journal");
                if (!restored) {
                    j.surfaces=new int[j.columns()]; j.grounds=new int[j.columns()]; j.materials=new int[j.columns()];
                    j.palette.add(Blocks.GRASS_BLOCK.defaultBlockState());
                }
                if (j.manifest().floating()) {
                    var d=j.manifest().dimensions();
                    j.floatingFootprint=new FloatingFootprint(d.getX(),d.getZ(),d.getY());
                    j.clearance=new FloatingClearance(Math.max(80,l.getMinBuildHeight()+8),l.getMaxBuildHeight()-4-d.getY());
                    j.validated=0; // Rebuild the transient footprint on every recovery, including completed jobs.
                }
                j.initialized=true;
            }
            var m=j.manifest();
            // Check every resource before touching terrain, also after a restart.
            if (j.validated<m.pieces().size()) {
                for (int n=0;n<4 && j.validated<m.pieces().size();n++,j.validated++) {
                    var p=m.pieces().get(j.validated); var t=l.getStructureManager().get(p.resource()).orElseThrow();
                    try {
                        if (!t.getSize().equals(p.size())) throw new IllegalStateException("Wrong template size "+p.resource());
                        if (m.floating()) j.floatingFootprint.include(t.save(new net.minecraft.nbt.CompoundTag()),p.offset());
                    } finally { l.getStructureManager().remove(p.resource()); }
                }
            } else if (j.phase==0) survey(l,j);
            else if (j.phase==1) terrain(l,j);
            else templates(l,j);
            progress(l,j); data.setDirty();
        } catch (Exception error) {
            j.error="phase="+j.phase+" tile="+j.tile+" piece="+j.piece+": "+error;
            j.phase=-1; data.setDirty(); release(l);
            LogUtils.getLogger().error("[TN-C Landmark] stopped {} {}",j.key(),j.error,error);
            announce(l,j,"§c遗迹施工暂停："+j.error+"。详情 /tnc landmark status");
            var bar=BARS.remove(e.getServer()); if (bar!=null) bar.removeAllPlayers();
        }
    }
    private static boolean area(ServerLevel l,BlockPos p,int sx,int sz) {
        var desired=new ArrayList<ChunkPos>();
        for (int z=p.getZ()>>4;z<=(p.getZ()+sz-1)>>4;z++)
            for (int x=p.getX()>>4;x<=(p.getX()+sx-1)>>4;x++) desired.add(new ChunkPos(x,z));
        if (!desired.equals(TICKETS.get(l.getServer()))) {
            release(l); TICKETS.put(l.getServer(),desired);
            for (var c:desired) l.getChunkSource().addRegionTicket(TICKET,c,2,c);
        }
        return desired.stream().allMatch(c->l.getChunkSource().getChunkNow(c.x,c.z)!=null);
    }
    private static void release(ServerLevel l) {
        var old=TICKETS.remove(l.getServer()); if (old!=null)
            for (var c:old) l.getChunkSource().removeRegionTicket(TICKET,c,2,c);
    }
    private static boolean used(LandmarkData.Job j) {
        var p=j.tilePos();
        for (int z=0;z<16;z++) for (int x=0;x<16;x++)
            if (surveyColumn(j,p.getX()-j.origin.getX()+x,p.getZ()-j.origin.getZ()+z)) return true;
        return false;
    }
    private static boolean surveyColumn(LandmarkData.Job j,int x,int z) {
        return j.manifest().terrainAt(x,z) && (!j.manifest().floating() || j.floatingFootprint.occupied(x,z));
    }
    private static boolean near(ServerLevel l,LandmarkData.Job j,BlockPos p,int sx,int sz,int minY,int maxY) {
        var dims=j.manifest().dimensions();
        int maxX=Math.min(p.getX()+sx,j.origin.getX()+dims.getX());
        int maxZ=Math.min(p.getZ()+sz,j.origin.getZ()+dims.getZ());
        j.waiting=l.players().stream().anyMatch(v->!v.isSpectator()
                && LandmarkWorkArea.near(v.getX(),v.getY(),v.getZ(),p.getX(),minY,p.getZ(),maxX,maxY,maxZ));
        return j.waiting;
    }
    private static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK);
    }
    private static void survey(ServerLevel l,LandmarkData.Job j) throws java.io.IOException {
        if (j.tile>=j.tileCount()) {
            if (j.manifest().floating()) {
                int y=j.clearance.choose(j.origin.getY());
                if (y==Integer.MIN_VALUE) throw new IllegalStateException("浮岛场地在允许高度内没有完整净空，未覆盖现有建筑");
                if (y!=j.origin.getY()) {
                    LogUtils.getLogger().info("[TN-C Landmark] adjusted floating altitude {} from {} to {} (XZ unchanged)",j.key(),j.origin.getY(),y);
                    j.origin=new BlockPos(j.origin.getX(),y,j.origin.getZ());
                }
            }
            LandmarkJournal.commit(l,j); j.tile=0; j.phase=1; return;
        }
        if (!used(j)) { j.tile++; return; }
        var p=j.tilePos(); if (!area(l,p,16,16)) return;
        for (var c:TICKETS.get(l.getServer())) {
            var chunk=l.getChunk(c.x,c.z);
            if (chunk.getInhabitedTime()>24000) throw new IllegalStateException("Previously inhabited chunk "+c);
        }
        var m=j.manifest(); int w=m.dimensions().getX();
        for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
            int lx=p.getX()-j.origin.getX()+x,lz=p.getZ()-j.origin.getZ()+z;
            if (!surveyColumn(j,lx,lz)) continue; int idx=lz*w+lx;
            int high=l.getHeight(Heightmap.Types.WORLD_SURFACE,p.getX()+x,p.getZ()+z)-1;
            int ground=high; var bp=new BlockPos(p.getX()+x,ground,p.getZ()+z);
            while (ground>l.getMinBuildHeight()+1 && !natural(l.getBlockState(bp)) && high-ground<48) { ground--; bp=bp.below(); }
            var top=l.getBlockState(bp); if (!natural(top)) top=Blocks.GRASS_BLOCK.defaultBlockState();
            if (m.floating()) {
                int bottom=j.floatingFootprint.bottomAt(lx,lz),ceiling=j.floatingFootprint.topAt(lx,lz);
                var check=new BlockPos.MutableBlockPos(p.getX()+x,0,p.getZ()+z);
                for (int y=Math.max(l.getMinBuildHeight(),j.clearance.minimum+bottom-4);
                     y<=Math.min(high,Math.min(l.getMaxBuildHeight()-1,j.clearance.maximum+ceiling+4));y++) {
                    check.setY(y);
                    if (!l.getBlockState(check).isAir()) j.clearance.obstacle(y,bottom,ceiling);
                }
            }
            j.surfaces[idx]=high; j.grounds[idx]=ground;
            int material=j.palette.indexOf(top); if (material<0) { material=j.palette.size(); j.palette.add(top); }
            j.materials[idx]=material;
        }
        protectTerrain(l,j,p);
        j.tile++;
    }
    /** Uses the immutable plan, never rejects unrelated underground or outside-mask containers. */
    private static void protectTerrain(ServerLevel l,LandmarkData.Job j,BlockPos tile) {
        var m=j.manifest(); int w=m.dimensions().getX();
        for (var c:TICKETS.get(l.getServer())) for (var be:l.getChunk(c.x,c.z).getBlockEntitiesPos()) {
            if (be.getX()<tile.getX() || be.getX()>=tile.getX()+16 || be.getZ()<tile.getZ() || be.getZ()>=tile.getZ()+16) continue;
            int x=be.getX()-j.origin.getX(),z=be.getZ()-j.origin.getZ();
            if (!surveyColumn(j,x,z)) continue;
            int idx=z*w+x,y=be.getY();
            boolean affected;
            if (m.floating()) affected=y>=j.origin.getY()+j.floatingFootprint.bottomAt(x,z)
                    && y<j.origin.getY()+m.dimensions().getY();
            else if (m.buildingAt(x,z)) affected=y>=Math.min(j.origin.getY(),j.grounds[idx]+1)
                    && y<=Math.max(j.surfaces[idx],j.origin.getY()+m.dimensions().getY()-1);
            else {
                var plan=StonecrestTerrainPlanner.plan(j.grounds[idx],j.origin.getY()+m.anchorLocal().getY(),m.distanceAt(x,z),m.maxBlendDistance(),false,0,0);
                affected=y>=Math.min(plan.targetY(),j.grounds[idx]+1) && y<=Math.max(plan.targetY(),j.surfaces[idx]);
            }
            if (affected && !j.preserved.contains(be)) {
                if (j.phase==0) j.preserved.reserve(be);
                else throw new IllegalStateException("Protected block entity in pending write volume "+be);
            }
        }
    }
    private static void terrain(ServerLevel l,LandmarkData.Job j) {
        var m=j.manifest();
        if (m.floating() || j.tile>=j.tileCount()) { j.phase=2; j.piece=0; return; }
        if (!used(j)) { j.tile++; return; }
        var p=j.tilePos(); if (!area(l,p,16,16)) return;
        var chunks=TICKETS.get(l.getServer()); String phase=j.asset+"_T";
        if (AbyssChunkLedger.complete(l,chunks,j.origin,phase,j.tile)) { j.tile++; j.cursor=0; return; }
        protectTerrain(l,j,p); // Recheck every slice, including after restart or a player's visit.
        // Do not scan the entire 384-layer world for every small terrain tile.
        // Bounds depend only on the immutable plan, so the cursor is stable after a pause.
        int base=l.getMaxBuildHeight(),top=l.getMinBuildHeight();
        for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
            int lx=p.getX()-j.origin.getX()+x,lz=p.getZ()-j.origin.getZ()+z;
            if (!m.terrainAt(lx,lz)) continue;
            int idx=lz*m.dimensions().getX()+lx,current=j.grounds[idx];
            if (m.buildingAt(lx,lz)) {
                base=Math.min(base,Math.min(current+1,j.origin.getY()));
                // WORLD_SURFACE already bounds the highest pre-existing non-air block.
                // Scanning hundreds of empty sky layers delayed cathedrals by minutes.
                top=Math.max(top,Math.max(j.surfaces[idx],j.origin.getY()-1));
            } else {
                var plan=StonecrestTerrainPlanner.plan(current,j.origin.getY()+m.anchorLocal().getY(),m.distanceAt(lx,lz),m.maxBlendDistance(),false,0,0);
                base=Math.min(base,Math.min(current+1,plan.targetY())); top=Math.max(top,Math.max(j.surfaces[idx],plan.targetY()));
            }
        }
        base=Math.max(l.getMinBuildHeight()+1,base); top=Math.min(l.getMaxBuildHeight()-1,top);
        if (near(l,j,p,16,16,base,top+1)) return;
        if (j.cursor==0) AbyssChunkLedger.invalidate(l,chunks,j.origin,j.asset+"_P");
        int limit=Math.max(0,top-base+1)*256;
        long deadline=System.nanoTime()+6_000_000L; int checked=0;
        var bp=new BlockPos.MutableBlockPos(); int w=m.dimensions().getX();
        while (j.cursor<limit && checked<32768) {
            int col=j.cursor%256,y=base+j.cursor/256,x=col%16,z=col/16;
            int lx=p.getX()-j.origin.getX()+x,lz=p.getZ()-j.origin.getZ()+z;
            int distance=m.distanceAt(lx,lz);
            if (distance<=m.maxBlendDistance()) {
                int idx=lz*w+lx,current=j.grounds[idx],high=j.surfaces[idx];
                var plan=StonecrestTerrainPlanner.plan(current,j.origin.getY()+m.anchorLocal().getY(),distance,m.maxBlendDistance(),
                        m.buildingAt(lx,lz),j.origin.getY(),j.origin.getY()+m.dimensions().getY()-1);
                BlockState target=null;
                if (m.buildingAt(lx,lz)) {
                    if (y>=j.origin.getY() && y<=Math.max(high,plan.clearToY())) target=Blocks.AIR.defaultBlockState();
                    else if (y>current && y<j.origin.getY()) target=Blocks.STONE.defaultBlockState();
                } else {
                    if (y>plan.targetY() && y<=high) target=Blocks.AIR.defaultBlockState();
                    else if (y==plan.targetY()) target=j.palette.get(j.materials[idx]);
                    else if (y>current && y<plan.targetY()) target=natural(j.palette.get(j.materials[idx]))
                            && !j.palette.get(j.materials[idx]).is(BlockTags.DIRT) ? j.palette.get(j.materials[idx]) : Blocks.DIRT.defaultBlockState();
                }
                if (target!=null) { bp.set(p.getX()+x,y,p.getZ()+z);
                    if (!j.preserved.contains(bp) && !l.getBlockState(bp).equals(target)) l.setBlock(bp,target,18); }
            }
            j.cursor++; checked++; if (checked%128==0 && System.nanoTime()>deadline) break;
        }
        if (j.cursor>=limit) { AbyssChunkLedger.mark(l,chunks,j.origin,phase,j.tile); j.tile++; j.cursor=0; }
    }
    private static void templates(ServerLevel l,LandmarkData.Job j) {
        var pieces=j.manifest().pieces();
        if (j.piece>=pieces.size()) {
            j.phase=3; release(l);
            LogUtils.getLogger().info("[TN-C Landmark] complete {} pieces={} preserved={}",j.key(),j.piece,j.preserved.blocks.size());
            announce(l,j,"§a遗迹已生成："+j.asset+"，建筑中心 "+buildingCenter(j).toShortString());
            return;
        }
        var p=pieces.get(j.piece); var target=j.origin.offset(p.offset());
        if (!area(l,target,p.size().getX(),p.size().getZ())) return;
        var chunks=TICKETS.get(l.getServer()); String phase=j.asset+"_P";
        if (AbyssChunkLedger.complete(l,chunks,j.origin,phase,j.piece)) { j.piece++; return; }
        if (near(l,j,target,p.size().getX(),p.size().getZ(),target.getY(),target.getY()+p.size().getY())) return;
        var t=l.getStructureManager().get(p.resource()).orElseThrow();
        try {
            protectTemplate(l,target,chunks,t,j.preserved);
            if (!t.placeInWorld(l,target,target,new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true).setKeepLiquids(false)
                            .addProcessor(j.preserved.processor),
                    RandomSource.create(j.origin.asLong()^j.piece),18)) throw new IllegalStateException("Failed template "+p.resource());
        } finally { l.getStructureManager().remove(p.resource()); }
        AbyssChunkLedger.mark(l,chunks,j.origin,phase,j.piece); j.piece++;
    }
    static void protectTemplate(ServerLevel l,BlockPos target,List<ChunkPos> chunks,
                                net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template) {
        protectTemplate(l,target,chunks,template,new PreservedSite());
    }
    private static void protectTemplate(ServerLevel l,BlockPos target,List<ChunkPos> chunks,
                                net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template,PreservedSite preserved) {
        var size=template.getSize(); var protectedPositions=new HashSet<BlockPos>();
        for (var c:chunks) for (var be:l.getChunk(c.x,c.z).getBlockEntitiesPos())
            if (be.getX()>=target.getX() && be.getX()<target.getX()+size.getX()
                    && be.getZ()>=target.getZ() && be.getZ()<target.getZ()+size.getZ()
                    && be.getY()>=target.getY() && be.getY()<target.getY()+size.getY()) protectedPositions.add(be);
        if (protectedPositions.isEmpty()) return;
        // Sparse templates omit source air/outside-mask cells. Only those actual
        // writes may veto a container, not the surrounding 16-cube bounding box.
        var saved=template.save(new net.minecraft.nbt.CompoundTag());
        for (var raw:saved.getList("blocks",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var pos=((net.minecraft.nbt.CompoundTag)raw).getList("pos",net.minecraft.nbt.Tag.TAG_INT);
            var destination=target.offset(pos.getInt(0),pos.getInt(1),pos.getInt(2));
            if (protectedPositions.contains(destination) && !preserved.contains(destination))
                throw new IllegalStateException("Protected block entity in pending template "+destination);
        }
    }
    private static void progress(ServerLevel l,LandmarkData.Job j) {
        if (l.getGameTime()%10!=0) return;
        var bar=BARS.computeIfAbsent(l.getServer(),k->new ServerBossEvent(Component.empty(),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(Component.literal(j.asset+" · "+switch(j.phase) {
                    case 0->j.validated<j.manifest().pieces().size()?"读取建筑 "+j.validated+"/"+j.manifest().pieces().size():"勘测场地 "+j.tile+"/"+j.tileCount();
                    case 1->"整修地形 "+j.tile+"/"+j.tileCount();case 2->"建筑正在显现 "+j.piece+"/"+j.manifest().pieces().size();default->"完成"; }
                +(j.waiting?" · 请退出施工区或使用旁观模式":"")));
        float f=j.phase==0 ? .15F*j.tile/j.tileCount() : j.phase==1 ? .15F+.35F*j.tile/j.tileCount() : j.phase==2 ? .5F+.5F*j.piece/j.manifest().pieces().size():1;
        bar.setProgress(Math.max(0,Math.min(1,f)));
        for (var p:List.copyOf(bar.getPlayers())) if (p.level()!=l || p.distanceToSqr(j.origin.getX(),p.getY(),j.origin.getZ())>4000000) bar.removePlayer(p);
        for (var p:l.players()) if (p.distanceToSqr(j.origin.getX(),p.getY(),j.origin.getZ())<=4000000) bar.addPlayer(p);
    }
    private static BlockPos buildingCenter(LandmarkData.Job j) {
        var m=j.manifest(); return j.origin.offset(m.dimensions().getX()/2,m.anchorLocal().getY()+1,m.dimensions().getZ()/2);
    }
    private static void announce(ServerLevel l,LandmarkData.Job j,String message) {
        var center=buildingCenter(j);
        for (var p:l.players()) if (p.distanceToSqr(center.getX(),p.getY(),center.getZ())<=4000000)
            p.sendSystemMessage(Component.literal(message));
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) {
        var l=e.getServer().overworld(); if (l!=null) { drain(l); release(l); }
        var bar=BARS.remove(e.getServer()); if (bar!=null) bar.removeAllPlayers();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { REQUESTS.keySet().removeIf(l->l.getServer()==e.getServer()); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("tnc").then(Commands.literal("landmark").requires(s->s.hasPermission(2))
                .then(Commands.literal("status").executes(c->{
                    var d=LandmarkData.get(c.getSource().getServer().overworld());
                    for (var j:d.jobs.values()) {
                        var m=j.manifest();
                        String label=switch(j.asset) {case "heroskand_complex"->"赫萝斯堪德宫殿";case "gothic_cathedral"->"哥特大教堂";
                            case "elden_coastal_castle"->"Elden 海岸城堡";case "end_pvp_island"->"末地浮岛";default->j.asset;};
                        var approach=j.origin.offset(m.dimensions().getX()/2,0,m.dimensions().getZ()+12);
                        c.getSource().sendSuccess(()->Component.literal(label+" · "+switch(j.phase){case -1->"暂停";case 0->"预检";case 1->"地形施工";case 2->"搭建";default->"完成";}
                                +" · 地形 "+j.tile+"/"+j.tileCount()+" · 建筑 "+j.piece+"/"+m.pieces().size()
                                +" · 建筑中心 "+buildingCenter(j).toShortString()+" · 南侧接近点 X="+approach.getX()+" Z="+approach.getZ()
                                +(j.waiting?" · 请退出施工区或使用旁观模式":"")+" "+j.error),false);
                    }
                    if (d.jobs.isEmpty()) c.getSource().sendSuccess(()->Component.literal("尚未触发大型遗迹。/locate 定位后到达现场。"),false);
                    return d.jobs.size();
                }))));
    }
}
