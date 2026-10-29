package com.tnc.tnc.skyacademy;

import com.google.gson.*;
import com.tnc.tnc.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored scenery is built before new-island entry; existing worlds never opt in. */
@Mod.EventBusSubscriber(modid="tnc")
public final class SkyAcademyWorld {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_academy_build",Comparator.comparingLong(ChunkPos::toLong),40);
    private static final Map<MinecraftServer,Plan> PLANS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<ChunkPos>> TICKETS=new WeakHashMap<>();
    public static final Map<String,BlockPos> SITES=sites();
    private record Plan(List<BlockState> palette,List<int[]> tiles){}
    public static final class State extends SavedData {
        boolean enabled,done; int tile,cursor; String error="";
        static State get(ServerLevel l){return l.getDataStorage().computeIfAbsent(t->{var s=new State();s.enabled=t.getBoolean("Enabled");s.done=t.getBoolean("Done");/* Before entry, replay unfinished construction: SavedData can reach disk before a chunk. */s.tile=0;s.cursor=0;s.setDirty();return s;},()->{var s=new State();s.enabled=l.getGameTime()==0;s.setDirty();return s;},"tnc_sky_academy_v1");}
        public CompoundTag save(CompoundTag t){t.putBoolean("Enabled",enabled);t.putBoolean("Done",done);t.putInt("Tile",tile);t.putInt("Cursor",cursor);t.putString("Error",error);return t;}
    }
    private static Map<String,BlockPos> sites(){
        try(var in=SkyAcademyWorld.class.getResourceAsStream("/data/tnc/sky_academy/sites.json");var r=new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)){
            var map=new LinkedHashMap<String,BlockPos>();JsonParser.parseReader(r).getAsJsonObject().entrySet().forEach(e->{var a=e.getValue().getAsJsonArray();map.put(e.getKey(),new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));});return Map.copyOf(map);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static BlockPos origin(MinecraftServer s){return SkyIslandSavedData.get(s.overworld()).anchorPos("ORIGIN");}
    /** Called by the island lifecycle, never inferred from a fleeting game-time value. */
    public static void beginFreshIsland(MinecraftServer s){var d=State.get(s.overworld());d.enabled=true;d.done=false;d.tile=d.cursor=0;d.error="";d.setDirty();}
    public static BlockPos at(MinecraftServer s,String site){var o=origin(s);var p=SITES.get(site);return o==null||p==null?null:o.offset(p);}
    public static boolean available(MinecraftServer s){var d=State.get(s.overworld());return d.enabled&&d.done;}
    public static void enableIsolatedAudit(MinecraftServer s){if(!Boolean.getBoolean("tnc.skyAcademyAudit"))throw new IllegalStateException("Audit opt-in required");var d=State.get(s.overworld());if(!d.enabled){d.enabled=true;d.done=false;d.tile=d.cursor=0;d.error="";d.setDirty();}}
    public static boolean ready(MinecraftServer s){var d=State.get(s.overworld());return !d.enabled||d.done;}
    public static String status(MinecraftServer s){var d=State.get(s.overworld());return !d.enabled?"旧存档保留原地形":d.done?"云溪景观与教学场地就绪":"云溪景观施工 "+d.tile+" / "+(PLANS.containsKey(s)?PLANS.get(s).tiles.size():"待加载")+(d.error.isEmpty()?"":" · "+d.error);}
    @SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e){if(e.getLevel() instanceof ServerLevel l&&l.dimension()==net.minecraft.world.level.Level.OVERWORLD)State.get(l);}
    private static Plan loadPlan(ServerLevel l)throws IOException{
        CompoundTag tag;try(var in=SkyAcademyWorld.class.getResourceAsStream("/data/tnc/sky_academy/landscape.nbt")){tag=NbtIo.readCompressed(Objects.requireNonNull(in));}
        if(tag.getInt("Version")!=1)throw new IOException("景观版本不兼容");
        var palette=new ArrayList<BlockState>();for(var t:tag.getList("Palette",10)){
            var p=(CompoundTag)t;var id=net.minecraft.resources.ResourceLocation.tryParse(p.getString("Name"));
            if(id==null||!l.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))throw new IOException("缺少景观方块 "+id);
            var b=NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),p);if(b.hasBlockEntity())throw new IOException("地形不能包含机器");palette.add(b);
        }
        var tiles=new ArrayList<int[]>();for(var t:tag.getList("Tiles",10)){
            int[] a=((CompoundTag)t).getIntArray("Blocks");if(a.length==0||a.length%4!=0||a.length>16384)throw new IOException("损坏的景观分片");
            for(int i=0;i<a.length;i+=4)if(a[i]<0||a[i]>=520||a[i+1]<0||a[i+1]>=220||a[i+2]<0||a[i+2]>=520||a[i+3]<0||a[i+3]>=palette.size())throw new IOException("景观坐标越界");tiles.add(a);
        }return new Plan(List.copyOf(palette),List.copyOf(tiles));
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var server=e.getServer();var l=server.overworld();var d=State.get(l);
        if(!d.enabled||d.done||!d.error.isEmpty()||l.players().isEmpty()||!SkyLandscapeUpgrade.complete(server))return;
        var o=origin(server);if(o==null)return;
        try{
            var plan=PLANS.get(server);if(plan==null){plan=loadPlan(l);PLANS.put(server,plan);}
            if(d.tile>=plan.tiles.size()){l.getChunkSource().save(true);d.done=true;d.setDirty();release(server);return;}
            int[] a=plan.tiles.get(d.tile);var wanted=new HashSet<ChunkPos>();
            for(int i=0;i<a.length;i+=4)wanted.add(new ChunkPos(o.offset(a[i],a[i+1],a[i+2])));
            for(var c:TICKETS.getOrDefault(server,Set.of()))if(!wanted.contains(c))l.getChunkSource().removeRegionTicket(TICKET,c,2,c);
            TICKETS.put(server,wanted);for(var c:wanted)l.getChunkSource().addRegionTicket(TICKET,c,2,c);
            if(wanted.stream().anyMatch(c->l.getChunkSource().getChunkNow(c.x,c.z)==null))return;
            long until=System.nanoTime()+2_000_000;int budget=256;
            while(d.cursor<a.length&&budget-->0&&System.nanoTime()<until){
                int i=d.cursor;var p=o.offset(a[i],a[i+1],a[i+2]);
                if(l.getBlockEntity(p)!=null)throw new IOException("发现非预期设施，保留现场 "+p.toShortString());
                if(l.players().stream().anyMatch(player->!player.isSpectator()&&player.distanceToSqr(p.getX(),p.getY(),p.getZ())<9))return;
                l.setBlock(p,plan.palette.get(a[i+3]),18);d.cursor+=4;
            }
            if(d.cursor>=a.length){d.tile++;d.cursor=0;}d.setDirty();
        }catch(Exception ex){d.error=ex.getMessage();d.setDirty();release(server);com.mojang.logging.LogUtils.getLogger().error("[Sky Academy] Construction paused",ex);}
    }
    private static void release(MinecraftServer s){for(var c:TICKETS.getOrDefault(s,Set.of()))s.overworld().getChunkSource().removeRegionTicket(TICKET,c,2,c);TICKETS.remove(s);}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppingEvent e){release(e.getServer());PLANS.remove(e.getServer());}
}
