package com.tnc.tnc.tavern;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.npc.*;
import com.tnc.tnc.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Independently versioned, bounded tavern migration. Never resets/rebuilds the island. */
public final class TavernUpgrade {
    public static final BlockPos MIN=new BlockPos(416,89,270),MAX=new BlockPos(486,131,327);
    public static final BlockPos SELF=new BlockPos(464,90,292),BOARD=new BlockPos(432,91,285);
    public static final float SELF_YAW=90;
    private static final BlockPos OLD_BOARD=new BlockPos(426,94,285);
    private static final ResourceLocation RESOURCE=ResourceLocation.fromNamespaceAndPath("tnc","tavern/interior_v1.nbt");
    private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_tavern",Comparator.comparingLong(ChunkPos::toLong),40);
    private static final Map<MinecraftServer,Plan> PLANS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<ChunkPos>> TICKETS=new WeakHashMap<>();
    private static final Map<ServerPlayer,String> SENT=new WeakHashMap<>();
    public record Change(BlockPos local,BlockState before,BlockState after,CompoundTag furniture) {}
    public record Plan(String hash,List<Change> changes,List<CompoundTag> decor,TavernAtmosphere atmosphere) {}
    public static boolean inside(BlockPos p){return p.getX()>=MIN.getX()&&p.getX()<=MAX.getX()&&p.getY()>=MIN.getY()&&p.getY()<=MAX.getY()&&p.getZ()>=MIN.getZ()&&p.getZ()<=MAX.getZ();}
    public static final class State extends SavedData {
        public int phase,cursor;public String hash="",error="";public BlockPos origin=BlockPos.ZERO;
        public CompoundTag legacyBoard=new CompoundTag();
        public static State get(ServerLevel l){return l.getDataStorage().computeIfAbsent(State::load,State::new,"tnc_tavern_v1");}
        public static State load(CompoundTag t){var s=new State();s.phase=t.getInt("Phase");s.hash=t.getString("Hash");s.error=t.getString("Error");s.origin=BlockPos.of(t.getLong("Origin"));s.legacyBoard=t.getCompound("LegacyBoard").copy();if(s.phase==1)s.phase=0;return s;}
        public CompoundTag save(CompoundTag t){t.putInt("Phase",phase);t.putString("Hash",hash);t.putString("Error",error);t.putLong("Origin",origin.asLong());t.put("LegacyBoard",legacyBoard);return t;}
    }
    public static Plan load(ServerLevel level)throws Exception {
        var resource=level.getServer().getResourceManager().getResource(RESOURCE).orElseThrow();byte[] bytes;
        try(var in=resource.open()){bytes=in.readAllBytes();}
        var root=NbtIo.readCompressed(new ByteArrayInputStream(bytes));
        if(root.getInt("Revision")!=1||!Arrays.equals(root.getIntArray("Bounds"),new int[]{416,89,270,486,131,327}))throw new IOException("无效酒馆装修范围或版本");
        var palette=new ArrayList<BlockState>();
        for(Tag raw:root.getList("Palette",10)) {
            var tag=(CompoundTag)raw;var id=ResourceLocation.tryParse(tag.getString("Name"));
            if(id==null||!level.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))throw new IOException("酒馆缺少方块："+tag.getString("Name"));
            palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),tag));
        }
        var furniture=new HashMap<BlockPos,CompoundTag>();
        for(Tag raw:root.getList("BlockEntities",10)){var tag=(CompoundTag)raw;var p=position(tag.getIntArray("Pos"));if(!inside(p)||furniture.put(p,tag.getCompound("Data").copy())!=null)throw new IOException("无效酒馆家具坐标");}
        var changes=new ArrayList<Change>();var seen=new HashSet<BlockPos>();
        for(Tag raw:root.getList("Changes",10)) {
            var tag=(CompoundTag)raw;var p=position(tag.getIntArray("Pos"));int b=tag.getInt("Before"),a=tag.getInt("After");
            if(!inside(p)||!seen.add(p)||a<0||b<0||a>=palette.size()||b>=palette.size())throw new IOException("无效或重复的酒馆装修方块");
            changes.add(new Change(p,palette.get(b),palette.get(a),furniture.remove(p)));
        }
        if(changes.isEmpty()||changes.size()>10000||!furniture.isEmpty())throw new IOException("酒馆装修方块数量或家具引用无效");
        var decor=new ArrayList<CompoundTag>();var allowed=Set.of("minecraft:painting","minecraft:item_frame","minecraft:glow_item_frame","minecraft:armor_stand");
        for(Tag raw:root.getList("Decor",10)) {
            var tag=(CompoundTag)raw;var pos=tag.getList("Pos",6);
            if(!allowed.contains(tag.getString("id"))||pos.size()!=3||!inside(BlockPos.containing(pos.getDouble(0),pos.getDouble(1),pos.getDouble(2))))throw new IOException("无效酒馆挂饰");
            decor.add(tag.copy());
        }
        return new Plan(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),List.copyOf(changes),List.copyOf(decor),TavernAtmosphere.load(level.getServer()));
    }
    private static BlockPos position(int[] p)throws IOException{if(p.length!=3)throw new IOException("酒馆坐标损坏");return new BlockPos(p[0],p[1],p[2]);}
    public static boolean compatible(BlockState actual,Change c){return actual.equals(c.before)||actual.equals(c.after);}
    public static void validate(ServerLevel l,State state,Plan plan)throws IOException {
        for(var c:plan.changes) {
            var p=state.origin.offset(c.local);var actual=l.getBlockState(p);var be=l.getBlockEntity(p);
            boolean oldBoard=c.local.equals(OLD_BOARD)&&be!=null&&be.saveWithFullMetadata().getString("id").equals("bountiful:board-be");
            if(!compatible(actual,c)&&!oldBoard)throw new IOException("保留现场改动，酒馆更新暂停于 "+p.toShortString()+"："+actual);
            if(be!=null&&!actual.equals(c.after)&&!oldBoard)throw new IOException("保留原容器，酒馆更新暂停于 "+p.toShortString());
            if(oldBoard&&state.legacyBoard.isEmpty()){state.legacyBoard=be.saveWithFullMetadata();state.setDirty();}
        }
    }
    /** Same-state furniture is deliberately retained, including all existing inventories. */
    public static void place(ServerLevel l,BlockPos origin,Change c)throws IOException {
        var pos=origin.offset(c.local);if(l.getBlockState(pos).equals(c.after))return;
        if(!l.setBlock(pos,c.after,18))throw new IOException("酒馆方块写入失败："+pos.toShortString());
        if(c.furniture!=null) {
            var be=l.getBlockEntity(pos);if(be==null)throw new IOException("酒馆家具未创建："+pos.toShortString());
            var data=c.furniture.copy();data.putInt("x",pos.getX());data.putInt("y",pos.getY());data.putInt("z",pos.getZ());
            if(!data.getString("id").equals(be.saveWithFullMetadata().getString("id")))throw new IOException("酒馆家具类型不匹配："+pos.toShortString());
            be.load(data);be.setChanged();l.sendBlockUpdated(pos,c.after,c.after,3);
        }
    }
    public static boolean occupied(ServerLevel level,BlockPos origin){return !level.getEntitiesOfClass(Player.class,new AABB(origin.offset(MIN),origin.offset(MAX).offset(1,1,1)),p->!p.isSpectator()).isEmpty();}
    private static boolean ready(ServerLevel l,BlockPos origin,Plan plan) {
        var chunks=new HashSet<ChunkPos>();plan.changes.forEach(c->chunks.add(new ChunkPos(origin.offset(c.local))));
        for(var c:chunks)l.getChunkSource().addRegionTicket(TICKET,c,2,c);
        TICKETS.put(l.getServer(),chunks);
        return chunks.stream().allMatch(c->l.hasChunk(c.x,c.z)&&l.areEntitiesLoaded(c.toLong()));
    }
    private static void finish(ServerLevel l,State state,Plan plan) {
        if(!state.legacyBoard.isEmpty()) {
            var p=state.origin.offset(BOARD);var be=l.getBlockEntity(p);
            if(be!=null){var data=state.legacyBoard.copy();data.putInt("x",p.getX());data.putInt("y",p.getY());data.putInt("z",p.getZ());be.load(data);be.setChanged();l.sendBlockUpdated(p,l.getBlockState(p),l.getBlockState(p),3);}
        }
        installDecor(l,state.origin,plan.decor);
        var data=NpcPlacementSavedData.get(l);data.put(new NpcPlacementSavedData.Placement("self","ORIGIN",SELF.getX(),SELF.getY(),SELF.getZ()));
        var target=state.origin.offset(SELF);
        for(var self:l.getEntitiesOfClass(SelfNpcEntity.class,new AABB(state.origin.offset(MIN),state.origin.offset(MAX).offset(1,1,1)))) {
            self.moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,SELF_YAW,0);self.setYHeadRot(SELF_YAW);self.setYBodyRot(SELF_YAW);
        }
        com.tnc.tnc.adventure.TownServices.prepareDecoratedTavern(l.getServer(),!state.legacyBoard.isEmpty());
        l.getChunkSource().save(true);state.phase=2;state.cursor=0;state.setDirty();release(l);
        LogUtils.getLogger().info("[TN-C Tavern] interior installed at {}, {} seated patrons configured",state.origin,plan.atmosphere.seats().size());
    }
    static void installDecor(ServerLevel l,BlockPos origin,List<CompoundTag> decorations) {
        for(int i=0;i<decorations.size();i++) {
            var tag=decorations.get(i).copy();var a=tag.getList("Pos",6);double x=origin.getX()+a.getDouble(0),y=origin.getY()+a.getDouble(1),z=origin.getZ()+a.getDouble(2);
            var type=EntityType.byString(tag.getString("id")).orElseThrow();
            if(!l.getEntitiesOfClass(Entity.class,new AABB(x-.3,y-.3,z-.3,x+.3,y+.3,z+.3),e->e.getType()==type).isEmpty())continue;
            var pos=new ListTag();pos.add(DoubleTag.valueOf(x));pos.add(DoubleTag.valueOf(y));pos.add(DoubleTag.valueOf(z));tag.put("Pos",pos);
            for(var entry:List.of(Map.entry("TileX",origin.getX()),Map.entry("TileY",origin.getY()),Map.entry("TileZ",origin.getZ())))if(tag.contains(entry.getKey()))tag.putInt(entry.getKey(),tag.getInt(entry.getKey())+entry.getValue());
            tag.putUUID("UUID",UUID.nameUUIDFromBytes(("tnc-tavern-v1:"+l.getSeed()+":"+origin+":"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var entity=EntityType.loadEntityRecursive(tag,l,e->e);if(entity!=null)l.addFreshEntityWithPassengers(entity);
        }
    }
    public static boolean complete(MinecraftServer server){return State.get(server.overworld()).phase==2;}
    public static void tick(MinecraftServer server) {
        var l=server.overworld();var island=SkyIslandSavedData.get(l);if(!island.isSkyIslandComplete()||!SkyLandscapeUpgrade.complete(server)||l.players().isEmpty())return;
        var s=State.get(l);if(s.phase<0)return;
        try {
            var plan=PLANS.get(server);if(plan==null){plan=load(l);PLANS.put(server,plan);}
            var origin=island.anchorPos("ORIGIN");
            if(s.hash.isEmpty()){s.hash=plan.hash;s.origin=origin;s.setDirty();}
            if(!s.hash.equals(plan.hash)||!s.origin.equals(origin))throw new IOException("酒馆资源或岛原点已变更，拒绝混用更新进度");
            if(s.phase!=2) {
                if(!ready(l,origin,plan)||occupied(l,origin))return;
                if(s.phase==0){validate(l,s,plan);s.phase=1;s.cursor=0;s.setDirty();}
                for(int budget=512;budget>0&&s.cursor<plan.changes.size();budget--,s.cursor++) {
                    var c=plan.changes.get(s.cursor);
                    if(!compatible(l.getBlockState(origin.offset(c.local)),c)&&!c.local.equals(OLD_BOARD))throw new IOException("酒馆施工现场已发生改动");
                    place(l,origin,c);
                }
                if(s.cursor==plan.changes.size())finish(l,s,plan);
            }
            if(s.phase==2&&server.getTickCount()%40==0) {
                ensureGuests(l,origin,plan.atmosphere);
                for(var p:server.getPlayerList().getPlayers()) {
                    sync(p,false);
                }
            }
        }catch(Exception e){s.phase=-1;s.error=e.getMessage();s.setDirty();release(l);LogUtils.getLogger().error("[TN-C Tavern] update paused: {}",s.error,e);}
    }
    public static void sync(ServerPlayer player,boolean immediate) {
        var server=player.server;var l=server.overworld();var s=State.get(l);if(s.phase!=2)return;
        try {
            var plan=PLANS.get(server);if(plan==null){plan=load(l);PLANS.put(server,plan);}
            String key=player.serverLevel().dimension().location()+":"+s.origin;
            if(!immediate&&key.equals(SENT.get(player)))return;
            var rooms=player.serverLevel()==l?plan.atmosphere.rooms().stream().map(b->b.move(s.origin.getX(),s.origin.getY(),s.origin.getZ())).toList():List.<AABB>of();
            com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new TavernRoomPacket(player.serverLevel().dimension().location(),rooms));SENT.put(player,key);
        }catch(Exception error){LogUtils.getLogger().error("[TN-C Tavern] room sync failed",error);}
    }
    public static int ensureGuests(ServerLevel l,BlockPos origin,TavernAtmosphere atmosphere) {
        int spawned=0;
        var retained=atmosphere.seats().stream().map(TavernAtmosphere.Seat::id).collect(java.util.stream.Collectors.toSet());
        for(var guest:l.getEntitiesOfClass(TavernGuestEntity.class,new AABB(origin.offset(MIN),origin.offset(MAX).offset(1,1,1))))if(!retained.contains(guest.seatId()))guest.discard();
        for(var seat:atmosphere.seats()) {
            var p=origin.offset(seat.local());var chunk=new ChunkPos(p);
            if(!l.hasChunkAt(p)||!l.areEntitiesLoaded(chunk.toLong())||!l.isPositionEntityTicking(p))continue;
            var existing=l.getEntitiesOfClass(TavernGuestEntity.class,new AABB(p).inflate(2),e->e.seatId().equals(seat.id()));
            if(!existing.isEmpty()){for(int i=1;i<existing.size();i++)existing.get(i).discard();continue;}
            if(!net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(l.getBlockState(p).getBlock()).toString().equals(seat.block()))continue;
            var guest=TNNpcs.TAVERN_GUEST.get().create(l);if(guest==null)continue;guest.configure(seat,origin);
            if(l.addFreshEntity(guest))spawned++;
        }
        return spawned;
    }
    private static void release(ServerLevel l){for(var c:TICKETS.getOrDefault(l.getServer(),Set.of()))l.getChunkSource().removeRegionTicket(TICKET,c,2,c);TICKETS.remove(l.getServer());}
    public static void stop(MinecraftServer server){release(server.overworld());PLANS.remove(server);SENT.clear();}
}
