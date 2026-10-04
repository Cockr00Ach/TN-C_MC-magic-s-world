package com.tnc.tnc.life.wonders;

import java.util.*;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class LightBloomData extends SavedData {
    static final int RADIUS=50,MAX_NODES=256;
    static final class Bloom {
        UUID id,owner;long expires;int cursor;
        final List<BlockPos> nodes=new ArrayList<>();
    }
    private final Map<UUID,Bloom> blooms=new LinkedHashMap<>();
    public boolean tracks(UUID id){return id!=null&&blooms.containsKey(id);}
    public static LightBloomData get(ServerLevel l){return l.getDataStorage().computeIfAbsent(LightBloomData::load,LightBloomData::new,"tnc_farlight_blooms");}
    public boolean canBegin(UUID owner,long now){return blooms.values().stream().filter(b->b.expires>now).count()<8&&blooms.values().stream().filter(b->b.expires>now&&b.owner.equals(owner)).count()<2;}
    public boolean begin(ServerPlayer player,BlockPos center){
        ServerLevel level=player.serverLevel();long now=level.getGameTime();
        if(!canBegin(player.getUUID(),now)||TownProtection.denied(player,center))return false;
        List<BlockPos> sites=findSites(player,center);
        if(sites.isEmpty())return false;
        Bloom b=new Bloom();b.id=UUID.randomUUID();b.owner=player.getUUID();b.expires=now+400;b.nodes.addAll(sites);blooms.put(b.id,b);setDirty();return true;
    }
    static List<BlockPos> findSites(ServerPlayer player,BlockPos center){
        var level=player.serverLevel();var result=new ArrayList<BlockPos>();
        Vec3 origin=Vec3.atCenterOf(center);
        for(int dx=-48;dx<=48;dx+=8)for(int dz=-48;dz<=48;dz+=8){
            if(dx*dx+dz*dz>RADIUS*RADIUS)continue;
            for(int dy=8;dy>=-8;dy--){
                BlockPos site=center.offset(dx,dy,dz);
                if(!level.isInWorldBounds(site)||!level.hasChunkAt(site)||!level.getBlockState(site).isAir()||level.getBlockState(site.below()).getCollisionShape(level,site.below()).isEmpty())continue;
                if(TownProtection.denied(player,site)||!level.mayInteract(player,site))continue;
                var ray=level.clip(new ClipContext(origin,Vec3.atCenterOf(site),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
                if(ray.getType()!=HitResult.Type.MISS)continue;
                result.add(site.immutable());break;
            }
        }
        result.sort(Comparator.comparingDouble(center::distSqr));
        return result.size()>MAX_NODES?new ArrayList<>(result.subList(0,MAX_NODES)):result;
    }
    public void tick(ServerLevel level){
        long now=level.getGameTime();int operations=0;
        var it=blooms.values().iterator();
        while(it.hasNext()){
            Bloom b=it.next();
            if(now>=b.expires){
                // Unloaded nodes keep their own saved deadline and expire on load.
                for(var p:b.nodes){if(operations>=32)break;if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof LightNodeEntity node&&b.id.equals(node.bloom())){level.setBlock(p,Blocks.AIR.defaultBlockState(),3);operations++;}}
                if(b.nodes.stream().noneMatch(p->level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof LightNodeEntity node&&b.id.equals(node.bloom()))) {it.remove();setDirty();}
                continue;
            }
            ServerPlayer owner=level.getServer().getPlayerList().getPlayer(b.owner);
            if(owner==null||owner.serverLevel()!=level)owner=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(b.owner,"farlight"));
            while(b.cursor<b.nodes.size()&&operations<32){
                BlockPos p=b.nodes.get(b.cursor++);operations++;
                if(!level.hasChunkAt(p)||!level.getBlockState(p).isAir()||TownProtection.denied(owner,p))continue;
                level.setBlock(p,WonderContent.LIGHT_NODE.defaultBlockState(),3);
                if(level.getBlockEntity(p) instanceof LightNodeEntity node)node.bind(b.id,b.expires);
                if(b.cursor%8==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,p.getX()+.5,p.getY()+.7,p.getZ()+.5,2,.2,.2,.2,0);
                setDirty();
            }
        }
    }
    public static LightBloomData load(CompoundTag t){var data=new LightBloomData();for(Tag raw:t.getList("Blooms",Tag.TAG_COMPOUND)){var tag=(CompoundTag)raw;if(!tag.hasUUID("Id")||!tag.hasUUID("Owner"))continue;Bloom b=new Bloom();b.id=tag.getUUID("Id");b.owner=tag.getUUID("Owner");b.expires=tag.getLong("Expires");for(long p:tag.getLongArray("Nodes"))if(b.nodes.size()<MAX_NODES)b.nodes.add(BlockPos.of(p));b.cursor=Math.max(0,Math.min(b.nodes.size(),tag.getInt("Cursor")));if(data.blooms.size()<32)data.blooms.put(b.id,b);}return data;}
    @Override public CompoundTag save(CompoundTag t){var list=new ListTag();for(Bloom b:blooms.values()){var tag=new CompoundTag();tag.putUUID("Id",b.id);tag.putUUID("Owner",b.owner);tag.putLong("Expires",b.expires);tag.putInt("Cursor",b.cursor);tag.putLongArray("Nodes",b.nodes.stream().mapToLong(BlockPos::asLong).toArray());list.add(tag);}t.put("Blooms",list);return t;}
}
