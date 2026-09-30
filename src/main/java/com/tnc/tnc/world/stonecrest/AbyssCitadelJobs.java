package com.tnc.tnc.world.stonecrest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded server-thread excavation; worldgen workers only enqueue immutable origins. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class AbyssCitadelJobs {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<ServerLevel, java.util.Set<BlockPos>> REQUESTS = new ConcurrentHashMap<>();
    private static final Map<MinecraftServer,List<ChunkPos>> TICKETS = new WeakHashMap<>();
    private static final Map<MinecraftServer,ServerBossEvent> BARS = new WeakHashMap<>();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("tnc_abyss_build",java.util.Comparator.comparingLong(ChunkPos::toLong));
    private static List<Piece> pieces;
    private record Piece(ResourceLocation id, BlockPos offset, int sx, int sy, int sz) { }

    static void request(ServerLevel level, BlockPos origin) {
        if (level.dimension() == Level.OVERWORLD)
            REQUESTS.computeIfAbsent(level,k->ConcurrentHashMap.newKeySet()).add(origin.immutable());
    }
    private static List<Piece> pieces() {
        if (pieces != null) return pieces;
        var stream = AbyssCitadelJobs.class.getResourceAsStream("/data/tnc/buildings/abyss_citadel.json");
        if (stream == null) throw new IllegalStateException("Missing citadel manifest");
        try (var reader = new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root = new Gson().fromJson(reader,JsonObject.class);
            var dims = root.getAsJsonArray("dimensions");
            if (dims.get(0).getAsInt()!=244 || dims.get(1).getAsInt()!=384 || dims.get(2).getAsInt()!=141)
                throw new IllegalStateException("Unexpected citadel dimensions");
            var result = new ArrayList<Piece>();
            for (var raw : root.getAsJsonArray("pieces")) {
                var p = raw.getAsJsonObject(); var o=p.getAsJsonArray("offset"); var s=p.getAsJsonArray("size");
                int x=o.get(0).getAsInt(),y=o.get(1).getAsInt(),z=o.get(2).getAsInt();
                int sx=s.get(0).getAsInt(),sy=s.get(1).getAsInt(),sz=s.get(2).getAsInt();
                if (sx<1 || sx>16 || sy<1 || sy>16 || sz<1 || sz>16 || x<0 || y<0 || z<0
                        || x+sx>244 || y+sy>384 || z+sz>141) throw new IllegalStateException("Invalid citadel piece");
                result.add(new Piece(new ResourceLocation(p.get("resource").getAsString()),new BlockPos(x,y,z),sx,sy,sz));
            }
            if (result.isEmpty() || result.size()!=root.get("piece_count").getAsInt()) throw new IllegalStateException("Citadel piece count mismatch");
            return pieces = List.copyOf(result);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level=event.getServer().overworld();
        if (level==null) return;
        var data=AbyssCitadelData.get(level);
        drainRequests(level,data);
        var job=data.jobs.values().stream().filter(j->j.phase>=0 && j.phase<4).findFirst().orElse(null);
        if (job==null) {
            release(level);
            var bar=BARS.remove(event.getServer()); if (bar!=null) bar.removeAllPlayers();
            return;
        }
        try {
            if (!job.initialized) {
                boolean restored=AbyssTerrainJournal.restore(level,job);
                if (job.phase>0 && !restored) throw new IllegalStateException("Missing immutable terrain journal; refusing to guess original terrain");
                job.initialized=true;
            }
            job.waitingForPlayers=false;
            pieces(); // Validate metadata before the first destructive operation.
            if (job.phase<=1) terrain(level,job);
            else if (job.phase==2) template(level,job);
            else lookout(level,job);
            progress(level,job);
            data.setDirty();
        } catch (Exception e) {
            job.error="phase="+job.phase+", tile="+job.tile+", piece="+job.piece+": "+e;
            job.phase=-1; data.setDirty(); release(level);
            LOG.error("[TN-C Abyss] stopped safely at {}: {}",job.origin,job.error,e);
        }
    }

    private static void drainRequests(ServerLevel level,AbyssCitadelData data) {
        var requests=REQUESTS.get(level);
        if (requests!=null) for (var origin : List.copyOf(requests)) {
            requests.remove(origin);
            var existing=data.jobs.get(origin.asLong());
            if (existing!=null) {
                if (existing.phase==4 && !existing.initialized) { existing.phase=1; data.setDirty(); }
                continue;
            }
            var job=new AbyssCitadelData.Job(origin);
            if (LargeLandmarkJobs.conflictsWithAbyss(level,origin)) {
                job.phase=-1; job.error="Overlapping landmark site";
            }
            if (origin.getY()!=-64 || level.getMinBuildHeight()!=-64 || level.getMaxBuildHeight()!=320) {
                job.phase=-1; job.error="World height must be -64..319";
            }
            data.jobs.put(origin.asLong(),job); data.setDirty();
            LOG.info("[TN-C Abyss] queued {}",origin);
        }
    }

    private static boolean loadArea(ServerLevel level,BlockPos pos,int sx,int sz) {
        var desired=new ArrayList<ChunkPos>();
        for (int z=pos.getZ()>>4;z<=(pos.getZ()+sz-1)>>4;z++)
            for (int x=pos.getX()>>4;x<=(pos.getX()+sx-1)>>4;x++) desired.add(new ChunkPos(x,z));
        if (!desired.equals(TICKETS.get(level.getServer()))) {
            release(level);
            TICKETS.put(level.getServer(),desired);
            for (var c:desired) level.getChunkSource().addRegionTicket(TICKET,c,2,c);
        }
        boolean ready=true;
        for (var c:desired) {
            // getChunkFuture() on the server thread calls managedBlock internally.
            // Region tickets drive generation on later ticks; this is a true nonblocking poll.
            if (level.getChunkSource().getChunkNow(c.x,c.z)==null) ready=false;
        }
        return ready;
    }
    private static void release(ServerLevel level) {
        var old=TICKETS.remove(level.getServer());
        if (old!=null) for (var c:old) level.getChunkSource().removeRegionTicket(TICKET,c,2,c);
    }
    private static boolean playersNear(ServerLevel level,BlockPos p,int sx,int sz) {
        return level.players().stream().anyMatch(v->!v.isSpectator()
                && v.getX()>=p.getX()-6 && v.getX()<p.getX()+sx+6
                && v.getZ()>=p.getZ()-6 && v.getZ()<p.getZ()+sz+6);
    }

    private static void terrain(ServerLevel level,AbyssCitadelData.Job j) throws java.io.IOException {
        if (j.tile>=AbyssCitadelData.TILE_COUNT) {
            if (j.phase==0 && j.piece<pieces().size()) {
                // Missing resource must fail BEFORE excavation, not after a half-built pit.
                for (int i=0;i<4 && j.piece<pieces().size();i++,j.piece++) {
                    var p=pieces().get(j.piece);
                    var t=level.getStructureManager().get(p.id()).orElseThrow(()->new IllegalStateException("Missing "+p.id()));
                    if (t.getSize().getX()!=p.sx() || t.getSize().getY()!=p.sy() || t.getSize().getZ()!=p.sz())
                        throw new IllegalStateException("Wrong template size "+p.id());
                }
                return;
            }
            if (j.phase==0) {
                var anchor=j.center().offset(0,0,168);
                if (!loadArea(level,anchor.offset(-4,0,-4),9,9)) return;
                j.lookoutY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,anchor.getX(),anchor.getZ())-1;
                AbyssTerrainJournal.commit(level,j);
            }
            j.piece=0;
            j.tile=0; j.cursor=0; j.phase++;
            LOG.info("[TN-C Abyss] {} entered phase {}",j.origin,j.phase); return;
        }
        BlockPos tile=j.tileOrigin();
        // Tiles wholly outside the ellipse require neither loading nor mutation.
        boolean intersects=false;
        for (int z=0;z<16 && !intersects;z++) for (int x=0;x<16;x++)
            if (AbyssPitPlan.radius(tile.getX()+x-j.center().getX(),tile.getZ()+z-j.center().getZ())<1) { intersects=true; break; }
        if (!intersects) { j.tile++; return; }
        if (!loadArea(level,tile,16,16)) return;
        if (j.phase==0) {
            // Refuse long-inhabited chunks before excavating anything. Still use a fresh
            // test world: vanilla structures can cross a previously generated boundary.
            for (var c:TICKETS.get(level.getServer()))
                if (level.getChunk(c.x,c.z).getInhabitedTime()>24000)
                    throw new IllegalStateException("Previously inhabited area; excavation not started: "+c);
            for (int z=0;z<16;z++) for (int x=0;x<16;x++) j.originalSurfaces[j.tile*256+z*16+x]=
                    level.getHeight(Heightmap.Types.WORLD_SURFACE,tile.getX()+x,tile.getZ()+z)-1;
            j.tile++; return;
        }
        var chunks=TICKETS.get(level.getServer());
        if (AbyssChunkLedger.complete(level,chunks,j.origin,"T",j.tile)) { j.tile++; j.cursor=0; return; }
        if (playersNear(level,tile,16,16)) { j.waitingForPlayers=true; return; }
        if (j.cursor==0) AbyssChunkLedger.invalidateTemplates(level,chunks,j.origin);
        int top=java.util.Arrays.stream(j.originalSurfaces,j.tile*256,j.tile*256+256).max().orElse(64);
        int limit=(Math.min(top,319)+65)*256;
        long deadline=System.nanoTime()+8_000_000L;
        var p=new BlockPos.MutableBlockPos();
        int checked=0;
        while (j.cursor<limit && checked<8192) {
            int idx=j.cursor%256,y=-64+j.cursor/256,x=idx%16,z=idx/16;
            int wx=tile.getX()+x,wz=tile.getZ()+z;
            int floor=AbyssPitPlan.floor(wx-j.center().getX(),wz-j.center().getZ(),j.originalSurfaces[j.tile*256+idx]);
            if (floor!=Integer.MIN_VALUE) {
                p.set(wx,y,wz);
                var target=y==-64 ? Blocks.BEDROCK.defaultBlockState()
                        : y>floor ? Blocks.AIR.defaultBlockState()
                        : y>=floor-4 ? Blocks.DEEPSLATE.defaultBlockState() : null;
                if (target!=null && !level.getBlockState(p).equals(target)) level.setBlock(p,target,18);
            }
            j.cursor++; checked++;
            if (checked%128==0 && System.nanoTime()>deadline) break;
        }
        if (j.cursor>=limit) {
            AbyssChunkLedger.mark(level,chunks,j.origin,"T",j.tile);
            j.tile++; j.cursor=0;
        }
    }

    private static void template(ServerLevel level,AbyssCitadelData.Job j) {
        if (j.piece>=pieces().size()) { j.phase=3; return; }
        var p=pieces().get(j.piece); var target=j.origin.offset(p.offset());
        if (!loadArea(level,target,p.sx(),p.sz())) return;
        var chunks=TICKETS.get(level.getServer());
        if (AbyssChunkLedger.complete(level,chunks,j.origin,"P",j.piece)) { j.piece++; return; }
        if (playersNear(level,target,p.sx(),p.sz())) { j.waitingForPlayers=true; return; }
        var template=level.getStructureManager().get(p.id()).orElseThrow(()->new IllegalStateException("Missing "+p.id()));
        if (!template.placeInWorld(level,target,target,new StructurePlaceSettings().setKnownShape(true)
                .setKeepLiquids(false).setIgnoreEntities(true)
                .addProcessor(com.tnc.tnc.world.ConquestPlantProcessor.INSTANCE),RandomSource.create(j.origin.asLong()^j.piece),18))
            throw new IllegalStateException("Failed to place "+p.id());
        AbyssChunkLedger.mark(level,chunks,j.origin,"P",j.piece);
        j.piece++;
    }

    private static void lookout(ServerLevel level,AbyssCitadelData.Job j) {
        var center=j.center();
        var anchor=new BlockPos(center.getX(),64,center.getZ()+168);
        if (!loadArea(level,anchor.offset(-4,0,-4),9,9)) return;
        var chunks=TICKETS.get(level.getServer());
        if (AbyssChunkLedger.complete(level,chunks,j.origin,"L",0)) { j.phase=4; release(level); return; }
        int y=j.lookoutY;
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
            var pos=new BlockPos(anchor.getX()+x,y,anchor.getZ()+z);
            level.setBlock(pos,Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),18);
            if (Math.abs(x)==3 && Math.abs(z)==3) level.setBlock(pos.above(),Blocks.SOUL_LANTERN.defaultBlockState(),18);
        }
        AbyssChunkLedger.mark(level,chunks,j.origin,"L",0);
        j.phase=4; release(level);
        LOG.info("[TN-C Abyss] complete origin={} pieces={}",j.origin,j.piece);
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        var level=event.getServer().overworld();
        if (level!=null) { drainRequests(level,AbyssCitadelData.get(level)); release(level); }
        var bar=BARS.remove(event.getServer()); if (bar!=null) bar.removeAllPlayers();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        REQUESTS.keySet().removeIf(level->level.getServer()==event.getServer());
    }

    private static void progress(ServerLevel level,AbyssCitadelData.Job j) {
        if (level.getGameTime()%10!=0) return;
        var bar=BARS.computeIfAbsent(level.getServer(),s->new ServerBossEvent(Component.empty(),
                BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS));
        String label=switch(j.phase) { case 0 -> "勘测遗迹"; case 1 -> "深渊正在显现";
            case 2 -> "扭曲城堡正在苏醒"; case 3 -> "整修坑沿"; default -> "深渊城堡已显现"; };
        bar.setName(Component.literal(label+(j.waitingForPlayers ? " · 请退出施工区，或用旁观模式观察" : "")));
        float fraction=switch(j.phase) { case 0 -> 0.15F*j.tile/AbyssCitadelData.TILE_COUNT;
            case 1 -> 0.15F+0.65F*j.tile/AbyssCitadelData.TILE_COUNT;
            case 2 -> 0.8F+0.18F*j.piece/pieces().size(); case 3 -> 0.99F; default -> 1F; };
        bar.setProgress(Math.max(0,Math.min(1,fraction)));
        for (var p:List.copyOf(bar.getPlayers()))
            if (p.level()!=level || p.distanceToSqr(j.center().getX(),p.getY(),j.center().getZ())>360000) bar.removePlayer(p);
        for (var p:level.players())
            if (p.distanceToSqr(j.center().getX(),p.getY(),j.center().getZ())<=360000) bar.addPlayer(p);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc").then(Commands.literal("abyss").requires(s->s.hasPermission(2))
                .then(Commands.literal("status").executes(c->{
                    var data=AbyssCitadelData.get(c.getSource().getServer().overworld());
                    if (data.jobs.isEmpty()) c.getSource().sendSuccess(()->Component.literal("尚未触发深渊城堡。先 /locate structure tnc:abyss_citadel，再到达坑沿坐标。"),false);
                    for (var j:data.jobs.values()) c.getSource().sendSuccess(()->Component.literal(
                            j.origin+" phase="+j.phase+" terrain="+j.tile+"/"+AbyssCitadelData.TILE_COUNT
                                    +" templates="+j.piece+"/"+pieces().size()+" "+j.error
                                    +"（phase: 0场地检查 1挖坑 2建筑 3坑沿 4完成；玩家靠近施工区时暂停）"),false);
                    return data.jobs.size();
                }))));
    }
}
