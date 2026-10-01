package com.tnc.tnc.world;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/** Compare-and-set landscape overlay. Does not reset the town or move any existing anchors. */
public final class SkyLandscapeUpgrade {
    static final ResourceLocation RESOURCE=ResourceLocation.fromNamespaceAndPath("tnc","sky_island/landscape_v1.nbt");
    static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_sky_landscape",Comparator.comparingLong(ChunkPos::toLong),40);
    static final Map<MinecraftServer,Plan> PLANS=new WeakHashMap<>();
    static final Map<MinecraftServer,Set<ChunkPos>> TICKETS=new WeakHashMap<>();
    record Change(BlockPos local,BlockState before,BlockState after) {}
    record Tile(List<Change> changes,BlockPos min,BlockPos max) {}
    record Plan(String hash,List<Tile> tiles,BlockPos tavern) {}

    static final class State extends SavedData {
        int phase,tile,cursor; // cursor is transient; recovery validates all tiles again
        String hash="",error="",waiting=""; BlockPos origin=BlockPos.ZERO;
        static State get(ServerLevel l) {return l.getDataStorage().computeIfAbsent(State::load,State::new,"tnc_sky_landscape_v1");}
        static State load(CompoundTag t) {
            var s=new State();s.phase=t.getInt("Phase");s.tile=t.getInt("Tile");s.hash=t.getString("Hash");
            s.error=t.getString("Error");s.origin=BlockPos.of(t.getLong("Origin"));
            // Always rescan on recovery. SavedData and individual chunks may have different save times.
            if(s.phase==0 || s.phase==1) {s.phase=0;s.tile=0;}
            return s;
        }
        public CompoundTag save(CompoundTag t) {
            t.putInt("Phase",phase);t.putInt("Tile",tile);t.putString("Hash",hash);t.putString("Error",error);
            t.putLong("Origin",origin.asLong());return t;
        }
    }

    static Plan load(ServerLevel l) throws Exception {
        var resource=l.getServer().getResourceManager().getResource(RESOURCE).orElseThrow(()->new IOException("缺少天空岛地形更新资源"));
        byte[] bytes;try(var in=resource.open()){bytes=in.readAllBytes();}
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        CompoundTag root=NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes));
        if(root.getInt("Revision")!=1) throw new IOException("不支持的地形更新版本");
        var palette=new ArrayList<BlockState>();
        for(Tag raw:root.getList("Palette",Tag.TAG_COMPOUND)) {
            var tag=(CompoundTag)raw; var id=ResourceLocation.tryParse(tag.getString("Name"));
            if(id==null || !l.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))
                throw new IOException("缺少更新所需方块："+tag.getString("Name"));
            var state=NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),tag);
            if(state.hasBlockEntity()&&!state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE))
                throw new IOException("增量资源包含未获准的方块实体");
            palette.add(state);
        }
        var tiles=new ArrayList<Tile>();var seen=new HashSet<BlockPos>();
        for(Tag raw:root.getList("Tiles",Tag.TAG_COMPOUND)) {
            int[] values=((CompoundTag)raw).getIntArray("Changes");
            if(values.length==0 || values.length%5!=0) throw new IOException("损坏的更新分片");
            var changes=new ArrayList<Change>();int x0=520,y0=220,z0=520,x1=0,y1=0,z1=0;
            for(int i=0;i<values.length;i+=5) {
                var p=new BlockPos(values[i],values[i+1],values[i+2]);
                if(p.getX()<0||p.getX()>=520||p.getY()<0||p.getY()>=220||p.getZ()<0||p.getZ()>=520||!seen.add(p)
                    ||values[i+3]<0||values[i+3]>=palette.size()||values[i+4]<0||values[i+4]>=palette.size())
                    throw new IOException("更新坐标重复、越界或状态无效");
                changes.add(new Change(p,palette.get(values[i+3]),palette.get(values[i+4])));
                x0=Math.min(x0,p.getX());y0=Math.min(y0,p.getY());z0=Math.min(z0,p.getZ());
                x1=Math.max(x1,p.getX());y1=Math.max(y1,p.getY());z1=Math.max(z1,p.getZ());
            }
            if(x1-x0>15||z1-z0>15||changes.size()>16000)throw new IOException("更新分片过大");
            tiles.add(new Tile(List.copyOf(changes),new BlockPos(x0,y0,z0),new BlockPos(x1,y1,z1)));
        }
        if(tiles.isEmpty())throw new IOException("地形更新为空");
        int[] p=root.getIntArray("Tavern");if(p.length!=3)throw new IOException("缺少酒馆入口");
        return new Plan(hash,List.copyOf(tiles),new BlockPos(p[0],p[1],p[2]));
    }

    static boolean sameStableState(BlockState actual,BlockState expected) {
        if(actual.getBlock()!=expected.getBlock())return false;
        if(actual.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
            return actual.setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1)
                    .equals(expected.setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE,1));
        return actual.equals(expected);
    }
    static boolean compatible(BlockState actual,Change c) {
        if(sameStableState(actual,c.before)||sameStableState(actual,c.after))return true;
        // Newly added soil can spread grass before the next column is finished.
        if(c.before.isAir()&&c.after.is(net.minecraft.world.level.block.Blocks.DIRT)
                &&actual.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK))return true;
        if(c.before.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)&&actual.is(net.minecraft.world.level.block.Blocks.DIRT)
                &&(c.after.is(net.minecraft.world.level.block.Blocks.STONE)||c.after.is(net.minecraft.world.level.block.Blocks.DIRT)))return true;
        // Template edge water can spread into formerly empty air after initial placement.
        // Never accept source water or a fluid replacing an existing solid/player building.
        return c.before.isAir()&&actual.is(net.minecraft.world.level.block.Blocks.WATER)
                &&actual.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)>0
                &&(c.after.is(net.minecraft.tags.BlockTags.DIRT)||c.after.is(net.minecraft.world.level.block.Blocks.STONE));
    }
    static void validate(ServerLevel l,BlockPos origin,Tile tile) throws IOException {
        for(var c:tile.changes) {
            var p=origin.offset(c.local);
            var actual=l.getBlockState(p);
            // A previously placed campfire is a no-op on recovery; never replace its inventory.
            boolean completedCampfire=actual.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)&&sameStableState(actual,c.after);
            if((l.getBlockEntity(p)!=null&&!completedCampfire) || !compatible(actual,c))
                throw new IOException("保留现场改动，更新暂停于 "+p.toShortString()+"，当前 "+l.getBlockState(p));
        }
    }

    static boolean ready(ServerLevel l,BlockPos origin,Tile tile) {
        var min=origin.offset(tile.min);var max=origin.offset(tile.max);var wanted=new HashSet<ChunkPos>();
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)wanted.add(new ChunkPos(x,z));
        Set<ChunkPos> old=TICKETS.getOrDefault(l.getServer(),Set.of());
        for(var c:old)if(!wanted.contains(c))l.getChunkSource().removeRegionTicket(TICKET,c,2,c);
        for(var c:wanted)l.getChunkSource().addRegionTicket(TICKET,c,2,c);
        TICKETS.put(l.getServer(),wanted);
        return wanted.stream().allMatch(c->l.hasChunk(c.x,c.z)&&l.areEntitiesLoaded(c.toLong()));
    }
    static boolean occupied(ServerLevel l,BlockPos origin,Tile tile) {
        var bounds=new AABB(origin.offset(tile.min),origin.offset(tile.max).offset(1,1,1)).inflate(1);
        return !l.getEntitiesOfClass(Entity.class,bounds,e->!(e instanceof Player p&&p.isSpectator())
                &&!(e instanceof net.minecraft.world.entity.Display)
                &&!(e instanceof net.minecraft.world.entity.item.ItemEntity)
                &&!(e instanceof net.minecraft.world.entity.ExperienceOrb)
                &&!(e instanceof net.minecraft.world.entity.projectile.Projectile)
                &&!(e instanceof net.minecraft.world.entity.AreaEffectCloud)
                &&!(e instanceof net.minecraft.world.entity.LightningBolt)).isEmpty();
    }
    static void step(ServerLevel l,State s,Plan plan) throws IOException {
        if(s.phase<0||s.phase==2)return;
        if(s.tile>=plan.tiles.size()) {
            // Persist touched chunks before recording completion in SavedData.
            if(s.phase==1)l.getChunkSource().save(true);
            s.tile=0;s.phase++;
            if(s.phase==2) {
                release(l);String message="天空岛地形与东侧酒馆已更新，酒馆入口："+s.origin.offset(plan.tavern).toShortString();
                LogUtils.getLogger().info("[TN-C Landscape] {}",message);
                l.players().forEach(p->p.sendSystemMessage(Component.literal(message)));
                try{var manifest=SkyIslandManifest.load(l.getServer());var island=SkyIslandSavedData.get(l);
                    var portal=new BlockPos(island.groundPortalX+7,island.groundPortalY+2,island.groundPortalZ+7);
                    l.players().forEach(p->SkyIslandPlayerNotifications.onGenerationComplete(p,manifest.version(),portal));
                }catch(IOException e){LogUtils.getLogger().warn("Completed landscape notification deferred",e);}
            }
            s.setDirty();return;
        }
        var tile=plan.tiles.get(s.tile);s.waiting="等待施工区块和实体加载";
        if(!ready(l,s.origin,tile))return;
        s.waiting="等待施工范围内玩家或生物离开";
        if(occupied(l,s.origin,tile))return;
        s.waiting="";
        validate(l,s.origin,tile);
        if(s.phase==1) {
            int budget=512;
            while(s.cursor<tile.changes.size()&&budget-->0) {
                var c=tile.changes.get(s.cursor);var pos=s.origin.offset(c.local);
                if(!sameStableState(l.getBlockState(pos),c.after)&&!l.setBlock(pos,c.after,18))
                    throw new IOException("方块写入失败："+pos.toShortString());
                s.cursor++;
            }
            if(s.cursor<tile.changes.size())return;
        }
        s.cursor=0;s.tile++;s.setDirty();
    }
    public static void tick(MinecraftServer server) {
        var l=server.overworld();var island=SkyIslandSavedData.get(l);
        if(!island.isSkyIslandComplete()||l.players().isEmpty())return;
        var s=State.get(l);if(s.phase<0||s.phase==2)return;
        try {
            Plan plan=PLANS.get(server);if(plan==null){plan=load(l);PLANS.put(server,plan);}
            var origin=island.anchorPos("ORIGIN");
            if(s.hash.isEmpty()){s.hash=plan.hash;s.origin=origin;s.setDirty();}
            if(!s.hash.equals(plan.hash)||!s.origin.equals(origin))throw new IOException("更新资源或岛屿坐标已变更，拒绝混用旧施工进度");
            step(l,s,plan);
        }catch(Exception e){s.phase=-1;s.error=e.getMessage();s.setDirty();release(l);
            LogUtils.getLogger().error("[TN-C Landscape] upgrade stopped: {}",s.error);
            l.players().forEach(p->p.sendSystemMessage(Component.literal("§e天空岛整修暂停："+s.error)));
        }
    }
    public static boolean complete(MinecraftServer server) { return State.get(server.overworld()).phase==2; }
    public static String status(MinecraftServer server) {
        var s=State.get(server.overworld());var plan=PLANS.get(server);
        return "天空岛整修："+(switch(s.phase){case 0->"检查现场";case 1->"施工";case 2->"已完成";default->"暂停";})
                +"，分片 "+s.tile+(plan==null?"":"/"+plan.tiles.size())
                +(s.error.isEmpty()?"":"；"+s.error)+(s.waiting.isEmpty()?"":"；"+s.waiting)
                +(plan==null?"":"；酒馆入口 "+s.origin.offset(plan.tavern).toShortString());
    }
    public static void retry(MinecraftServer server) {
        var s=State.get(server.overworld());if(s.phase==2)return;s.phase=0;s.tile=0;s.cursor=0;s.error="";s.setDirty();
    }
    static void release(ServerLevel l) {
        for(var c:TICKETS.getOrDefault(l.getServer(),Set.of()))l.getChunkSource().removeRegionTicket(TICKET,c,2,c);
        TICKETS.remove(l.getServer());
    }
    public static void stop(MinecraftServer s){release(s.overworld());PLANS.remove(s);}
}
