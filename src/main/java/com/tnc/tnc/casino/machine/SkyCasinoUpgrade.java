package com.tnc.tnc.casino.machine;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.tavern.TavernUpgrade;
import com.tnc.tnc.world.SkyIslandSavedData;
import com.tnc.tnc.world.SkyLandscapeUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** One-time, bounded import of the supplied casino. Rechecks saved chunks after recovery. */
public final class SkyCasinoUpgrade {
    public static final BlockPos MIN = new BlockPos(64,80,232), MAX = new BlockPos(124,132,307);
    private static final ResourceLocation RESOURCE = ResourceLocation.fromNamespaceAndPath("tnc","casino/island_v3.nbt");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("tnc_sky_casino",Comparator.comparingLong(ChunkPos::toLong),40);
    private static final Map<MinecraftServer,Plan> PLANS = new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<ChunkPos>> TICKETS = new WeakHashMap<>();
    public record Plan(String hash,List<TavernUpgrade.Change> changes,List<CompoundTag> decor,Map<BlockPos,BlockState> previous,Map<BlockPos,BlockState> previousV1,Map<BlockPos,BlockPos> moves,Map<BlockPos,BlockPos> movesV1,List<BlockPos> walk) {}
    public static final class State extends net.minecraft.world.level.saveddata.SavedData {
        public int phase,cursor; public String hash="",error=""; public BlockPos origin=BlockPos.ZERO;
        public int migrationVersion; public boolean migration,captured; public CompoundTag carried=new CompoundTag();
        public Set<Long> applied=new HashSet<>();
        static State load(CompoundTag t) {
            var s=new State();s.phase=t.getInt("Phase");if(s.phase==1)s.phase=0;
            s.hash=t.getString("Hash");s.error=t.getString("Error");s.origin=BlockPos.of(t.getLong("Origin"));
            s.migrationVersion=t.getInt("MigrationVersion");s.migration=t.getBoolean("Migration");s.captured=t.getBoolean("Captured");s.carried=t.getCompound("Carried").copy();for(long p:t.getLongArray("Applied"))s.applied.add(p);return s;
        }
        public CompoundTag save(CompoundTag t) {
            t.putInt("Phase",phase);t.putString("Hash",hash);t.putString("Error",error);t.putLong("Origin",origin.asLong());
            t.putInt("MigrationVersion",migrationVersion);t.putBoolean("Migration",migration);t.putBoolean("Captured",captured);t.put("Carried",carried);t.putLongArray("Applied",applied.stream().mapToLong(Long::longValue).toArray());return t;
        }
    }
    public static State state(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(State::load,State::new,"tnc_sky_casino_v3");
    }
    public static boolean inside(BlockPos p) {
        return p.getX()>=MIN.getX()&&p.getX()<=MAX.getX()&&p.getY()>=MIN.getY()&&p.getY()<=MAX.getY()&&p.getZ()>=MIN.getZ()&&p.getZ()<=MAX.getZ();
    }
    private static BlockPos position(int[] p)throws IOException {
        if(p.length!=3)throw new IOException("赌场坐标损坏");
        var pos=new BlockPos(p[0],p[1],p[2]);if(!inside(pos))throw new IOException("赌场坐标越界");return pos;
    }
    public static Plan load(ServerLevel l)throws Exception {
        byte[] bytes;try(var in=l.getServer().getResourceManager().getResource(RESOURCE).orElseThrow().open()){bytes=in.readAllBytes();}
        var root=NbtIo.readCompressed(new ByteArrayInputStream(bytes));
        if(root.getInt("Revision")!=3||!Arrays.equals(root.getIntArray("Bounds"),new int[]{64,80,232,124,132,307}))throw new IOException("赌场范围或版本无效");
        var palette=new ArrayList<BlockState>();
        for(Tag raw:root.getList("Palette",10)) {
            var t=(CompoundTag)raw;var id=ResourceLocation.tryParse(t.getString("Name"));
            if(id==null||!l.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))throw new IOException("赌场缺少方块："+id);
            var s=NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),t);
            if(s.is(Blocks.COMMAND_BLOCK)||s.is(Blocks.CHAIN_COMMAND_BLOCK)||s.is(Blocks.REPEATING_COMMAND_BLOCK)||s.is(Blocks.STRUCTURE_BLOCK)||s.is(Blocks.JIGSAW))throw new IOException("赌场含控制方块");
            palette.add(s);
        }
        var furniture=new HashMap<BlockPos,CompoundTag>();
        for(Tag raw:root.getList("BlockEntities",10)) {
            var t=(CompoundTag)raw;var p=position(t.getIntArray("Pos"));var data=t.getCompound("Data").copy();
            if(data.contains("Items")||data.contains("LootTable")||furniture.put(p,data)!=null)throw new IOException("赌场家具含物品或坐标重复");
        }
        var changes=new ArrayList<TavernUpgrade.Change>();var seen=new HashSet<BlockPos>();var previous=new HashMap<BlockPos,BlockState>();var previousV1=new HashMap<BlockPos,BlockState>();
        for(Tag raw:root.getList("Changes",10)) {
            var t=(CompoundTag)raw;var p=position(t.getIntArray("Pos"));int b=t.getInt("Before"),a=t.getInt("After");
            if(!seen.add(p)||b<0||a<0||b>=palette.size()||a>=palette.size())throw new IOException("赌场状态或坐标无效");
            int old=t.getInt("Previous");if(old<0||old>=palette.size())throw new IOException("赌场旧版状态无效");previous.put(p,palette.get(old));int old1=t.getInt("PreviousV1");if(old1<0||old1>=palette.size())throw new IOException("赌场v1状态无效");previousV1.put(p,palette.get(old1));
            changes.add(new TavernUpgrade.Change(p,palette.get(b),palette.get(a),furniture.remove(p)));
        }
        if(changes.isEmpty()||changes.size()>250000||!furniture.isEmpty())throw new IOException("赌场施工数量或家具引用无效");
        var decor=new ArrayList<CompoundTag>();var allowed=Set.of("minecraft:painting","minecraft:item_frame","minecraft:glow_item_frame","minecraft:armor_stand");
        for(Tag raw:root.getList("Decor",10)) {
            var t=(CompoundTag)raw;var p=t.getList("Pos",6);
            if(!allowed.contains(t.getString("id"))||p.size()!=3||!inside(BlockPos.containing(p.getDouble(0),p.getDouble(1),p.getDouble(2))))throw new IOException("赌场挂饰无效");
            decor.add(t.copy());
        }
        var moves=new HashMap<BlockPos,BlockPos>();
        for(Tag raw:root.getList("Moves",10)){var t=(CompoundTag)raw;var a=position(t.getIntArray("From"));var b=position(t.getIntArray("To"));if(moves.put(a,b)!=null)throw new IOException("赌场迁移坐标重复");}
        var movesV1=new HashMap<BlockPos,BlockPos>();for(Tag raw:root.getList("MovesV1",10)){var t=(CompoundTag)raw;movesV1.put(position(t.getIntArray("From")),position(t.getIntArray("To")));}
        var walk=new ArrayList<BlockPos>();for(Tag raw:root.getList("Walk",11))walk.add(position(((IntArrayTag)raw).getAsIntArray()));
        if(walk.size()<28)throw new IOException("赌场行走空间不足");
        return new Plan(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),List.copyOf(changes),List.copyOf(decor),Map.copyOf(previous),Map.copyOf(previousV1),Map.copyOf(moves),Map.copyOf(movesV1),List.copyOf(walk));
    }
    private static boolean same(BlockState a,BlockState b) {
        if(a.getBlock()!=b.getBlock())return false;
        if(a.getBlock() instanceof LeavesBlock)return a.setValue(LeavesBlock.DISTANCE,1).equals(b.setValue(LeavesBlock.DISTANCE,1));
        // Opening an imported door is normal use, rather than a structural edit.
        if(a.getBlock() instanceof DoorBlock)return a.setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false)
                .equals(b.setValue(DoorBlock.OPEN,false).setValue(DoorBlock.POWERED,false));
        return a.equals(b);
    }
    public static boolean compatible(BlockState actual,TavernUpgrade.Change c) {
        if(same(actual,c.before())||same(actual,c.after()))return true;
        // Grass can spread and stairs/fences acquire neighbour connections during placement.
        if((actual.is(Blocks.DIRT)||actual.is(Blocks.GRASS_BLOCK))&&(c.before().is(Blocks.DIRT)||c.before().is(Blocks.GRASS_BLOCK)))return true;
        return false;
    }
    public static void validate(ServerLevel l,BlockPos origin,List<TavernUpgrade.Change> changes)throws IOException {
        for(var c:changes) {
            var p=origin.offset(c.local());var actual=l.getBlockState(p);
            if(!compatible(actual,c))throw new IOException("保留现场改建："+p.toShortString());
            if(l.getBlockEntity(p)!=null&&!same(actual,c.after()))throw new IOException("保留现场容器："+p.toShortString());
        }
    }
    private static boolean ready(ServerLevel l,BlockPos origin,Plan plan) {
        var chunks=TICKETS.get(l.getServer());boolean first=chunks==null;
        if(first){chunks=new HashSet<ChunkPos>();for(var c:plan.changes())chunks.add(new ChunkPos(origin.offset(c.local())));TICKETS.put(l.getServer(),chunks);}
        if(first||l.getServer().getTickCount()%20==0)for(var c:chunks)l.getChunkSource().addRegionTicket(TICKET,c,2,c);
        return chunks.stream().allMatch(c->l.hasChunk(c.x,c.z)&&l.areEntitiesLoaded(c.toLong()));
    }
    private static boolean occupied(ServerLevel l,BlockPos origin) {
        return !l.getEntitiesOfClass(LivingEntity.class,new AABB(origin.offset(MIN),origin.offset(MAX).offset(1,1,1)),e->!e.isSpectator()&&!(e instanceof CasinoGuestEntity)).isEmpty();
    }
    private static void decorations(ServerLevel l,BlockPos origin,Plan plan) {
        String marker="tnc_casino_decor_";
        var box=new AABB(origin.offset(MIN),origin.offset(MAX).offset(1,1,1)).inflate(2);
        for(int i=0;i<plan.decor().size();i++) {
            String id=marker+i;
            if(!l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box,e->e.getTags().contains(id)).isEmpty())continue;
            var t=plan.decor().get(i).copy();var pos=t.getList("Pos",6);
            t.remove("UUID");t.put("Pos",new ListTag());var translated=t.getList("Pos",6);
            for(int axis=0;axis<3;axis++)translated.add(DoubleTag.valueOf(pos.getDouble(axis)+(axis==0?origin.getX():axis==1?origin.getY():origin.getZ())));
            for(String key:List.of("TileX","TileY","TileZ"))if(t.contains(key))t.putInt(key,t.getInt(key)+(key.equals("TileX")?origin.getX():key.equals("TileY")?origin.getY():origin.getZ()));
            // loadEntityRecursive constructs the entity; addition is explicit and UUID is new.
            var e=EntityType.loadEntityRecursive(t,l,v->v);
            if(e!=null){e.addTag(id);l.addFreshEntity(e);}
        }
    }
    public static void tick(MinecraftServer server) {
        var l=server.overworld();var island=SkyIslandSavedData.get(l);
        if(!island.isSkyIslandComplete()||!SkyLandscapeUpgrade.complete(server)||l.players().isEmpty())return;
        var s=state(l);if(s.phase<0)return;
        try {
            var plan=PLANS.get(server);if(plan==null){plan=load(l);PLANS.put(server,plan);}
            var origin=island.anchorPos("ORIGIN");
            if(s.phase==2){if(server.getTickCount()%200==0)CasinoPopulation.ensure(l,origin,plan.walk());return;}
            if(s.hash.isEmpty()){
                var old2=l.getDataStorage().computeIfAbsent(State::load,State::new,"tnc_sky_casino_v2");
                var old=l.getDataStorage().computeIfAbsent(TavernUpgrade.State::load,TavernUpgrade.State::new,"tnc_sky_casino_v1");
                if(old.phase==1)throw new IOException("旧赌场尚在施工，请先完成旧版施工");
                s.migrationVersion=old2.phase==2?2:old.phase==2?1:0;s.migration=s.migrationVersion>0;s.hash=plan.hash();s.origin=origin;s.setDirty();
            }
            if(!s.hash.equals(plan.hash())||!s.origin.equals(origin))throw new IOException("赌场资源或岛原点变化");
            if(!ready(l,origin,plan)||occupied(l,origin))return;
            if(s.phase==0){
                var effective=new ArrayList<TavernUpgrade.Change>();
                for(var c:plan.changes())effective.add(effective(c,s,plan));
                for(var c:effective) {
                    var p=origin.offset(c.local());var actual=l.getBlockState(p);
                    if(!compatible(actual,c))throw new IOException("保留现场改建："+p.toShortString());
                    if(l.getBlockEntity(p)!=null&&!same(actual,c.after())&&!(s.migration&&moves(s,plan).containsKey(c.local())))
                        throw new IOException("保留现场容器："+p.toShortString());
                }
                if(s.migration&&!s.captured){
                    for(var move:moves(s,plan).entrySet()){
                        var be=l.getBlockEntity(origin.offset(move.getKey()));if(be==null)continue;
                        var data=be.saveWithFullMetadata();s.carried.put(Long.toString(move.getValue().asLong()),data);
                    }
                    s.captured=true;s.setDirty();
                }
                CasinoPopulation.prepareMove(l,origin);
                s.phase=1;s.cursor=0;s.setDirty();l.getDataStorage().save();
            }
            for(int budget=96;budget>0&&s.cursor<plan.changes().size();budget--,s.cursor++) {
                var c=effective(plan.changes().get(s.cursor),s,plan);
                if(!compatible(l.getBlockState(origin.offset(c.local())),c))throw new IOException("赌场施工中出现现场改动");
                // The saved snapshot is restored at the destination; suppress source break drops.
                if(s.migration&&!s.applied.contains(c.local().asLong())&&moves(s,plan).containsKey(c.local()))
                    l.removeBlockEntity(origin.offset(c.local()));
                TavernUpgrade.place(l,origin,c);
                if(s.migration){restoreCarried(l,origin,c,s);if(moves(s,plan).containsKey(c.local()))s.applied.add(c.local().asLong());}
            }
            if(s.cursor==plan.changes().size()) {
                if(s.migration) {
                    var box=new AABB(origin.offset(MIN),origin.offset(MAX).offset(1,1,1)).inflate(2);
                    for(var e:l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box,v->v.getTags().stream().anyMatch(t->t.startsWith("tnc_casino_decor_"))))e.discard();
                }
                decorations(l,origin,plan);CasinoPopulation.ensure(l,origin,plan.walk());l.getChunkSource().save(true);s.phase=2;s.setDirty();release(l);
                LogUtils.getLogger().info("[TN-C Casino] map installed at {}",origin.offset(66,84,234));
            }
        }catch(Exception e) {
            s.phase=-1;s.error=e.getMessage();s.setDirty();release(l);LogUtils.getLogger().error("[TN-C Casino] import paused",e);
        }
    }
    private static TavernUpgrade.Change effective(TavernUpgrade.Change c,State s,Plan plan) {
        var saved=s.carried.getCompound(Long.toString(c.local().asLong()));
        return new TavernUpgrade.Change(c.local(),s.migration?(s.migrationVersion==1?plan.previousV1():plan.previous()).get(c.local()):c.before(),c.after(),saved.isEmpty()?c.furniture():saved);
    }
    private static Map<BlockPos,BlockPos> moves(State s,Plan p){return s.migrationVersion==1?p.movesV1():p.moves();}
    static void restoreCarried(ServerLevel l,BlockPos origin,TavernUpgrade.Change c,State s)throws IOException {
        long key=c.local().asLong();if(s.applied.contains(key))return;
        var data=s.carried.getCompound(Long.toString(key));if(data.isEmpty())return;
        var p=origin.offset(c.local());var be=l.getBlockEntity(p);
        if(be==null||!data.getString("id").equals(be.saveWithFullMetadata().getString("id")))throw new IOException("赌场迁移家具类型不符："+p.toShortString());
        data=data.copy();data.putInt("x",p.getX());data.putInt("y",p.getY());data.putInt("z",p.getZ());
        be.load(data);be.setChanged();l.sendBlockUpdated(p,c.after(),c.after(),3);s.applied.add(key);s.setDirty();
    }
    public static String status(MinecraftServer server) {
        var s=state(server.overworld());var plan=PLANS.get(server);
        return "天空赌场："+(s.phase==2?"已完成":s.phase<0?"暂停":s.phase==1?"施工":"等待检查")+"，"+s.cursor+(plan==null?"":"/"+plan.changes().size())
                +(s.error.isEmpty()?"":"；"+s.error)+"；位置 "+s.origin.offset(66,90,234).toShortString();
    }
    public static void retry(MinecraftServer server) {
        var s=state(server.overworld());if(s.phase==2)return;s.phase=0;s.cursor=0;s.error="";s.setDirty();
    }
    private static void release(ServerLevel l) {
        for(var c:TICKETS.getOrDefault(l.getServer(),Set.of()))l.getChunkSource().removeRegionTicket(TICKET,c,2,c);TICKETS.remove(l.getServer());
    }
    public static void stop(MinecraftServer server){release(server.overworld());PLANS.remove(server);}
}
