package com.tnc.tnc.tavern;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.npc.*;
import com.tnc.tnc.world.SkyIslandSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Additive cellar correction. The completed first interior and its hash remain intact. */
public final class TavernBasementUpgrade {
    public static final BlockPos MIN=new BlockPos(425,81,287),MAX=new BlockPos(440,89,298);
    private static final TicketType<ChunkPos> TICKET=TicketType.create("tnc_tavern_cellar",Comparator.comparingLong(ChunkPos::toLong),40);
    private static final Set<String> NATURAL=Set.of("minecraft:air","minecraft:stone","minecraft:andesite","minecraft:tuff","minecraft:deepslate","minecraft:dirt","minecraft:rooted_dirt","minecraft:coarse_dirt","minecraft:grass_block");
    private static final Map<MinecraftServer,TavernUpgrade.Plan> PLANS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Set<ChunkPos>> TICKETS=new WeakHashMap<>();
    public static TavernUpgrade.State state(ServerLevel l){return l.getDataStorage().computeIfAbsent(TavernUpgrade.State::load,TavernUpgrade.State::new,"tnc_tavern_basement_v1");}
    public static boolean inside(BlockPos p){return p.getX()>=425&&p.getX()<=440&&p.getY()>=81&&p.getY()<=89&&p.getZ()>=287&&p.getZ()<=298;}
    public static boolean compatible(BlockState actual,TavernUpgrade.Change c){return actual.equals(c.after())||NATURAL.contains(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(actual.getBlock()).toString());}
    public static TavernUpgrade.Plan load(ServerLevel l)throws Exception {
        byte[] bytes;try(var in=l.getServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("tnc","tavern/basement_v1.nbt")).orElseThrow().open()){bytes=in.readAllBytes();}
        var root=NbtIo.readCompressed(new ByteArrayInputStream(bytes));if(root.getInt("Revision")!=1||!Arrays.equals(root.getIntArray("Bounds"),new int[]{425,81,287,440,89,298}))throw new IOException("地下室范围或版本无效");
        var palette=new ArrayList<BlockState>();
        for(Tag raw:root.getList("Palette",10)){var t=(CompoundTag)raw;var id=ResourceLocation.tryParse(t.getString("Name"));if(id==null||!l.registryAccess().registryOrThrow(Registries.BLOCK).containsKey(id))throw new IOException("地下室缺少方块："+id);palette.add(NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),t));}
        var furniture=new HashMap<BlockPos,CompoundTag>();
        for(Tag raw:root.getList("BlockEntities",10)){var t=(CompoundTag)raw;var p=pos(t.getIntArray("Pos"));if(!inside(p)||furniture.put(p,t.getCompound("Data").copy())!=null)throw new IOException("地下室家具坐标无效");}
        var changes=new ArrayList<TavernUpgrade.Change>();var seen=new HashSet<BlockPos>();
        for(Tag raw:root.getList("Changes",10)){var t=(CompoundTag)raw;var p=pos(t.getIntArray("Pos"));int b=t.getInt("Before"),a=t.getInt("After");if(!inside(p)||!seen.add(p)||a<0||b<0||a>=palette.size()||b>=palette.size())throw new IOException("地下室施工坐标无效");changes.add(new TavernUpgrade.Change(p,palette.get(b),palette.get(a),furniture.remove(p)));}
        if(changes.isEmpty()||changes.size()>2000||!furniture.isEmpty())throw new IOException("地下室施工数量无效");
        return new TavernUpgrade.Plan(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),List.copyOf(changes),List.of(),TavernAtmosphere.load(l.getServer()));
    }
    private static BlockPos pos(int[] p)throws IOException{if(p.length!=3)throw new IOException("地下室坐标损坏");return new BlockPos(p[0],p[1],p[2]);}
    public static void validate(ServerLevel l,BlockPos origin,List<TavernUpgrade.Change> changes)throws IOException {
        for(var c:changes){var p=origin.offset(c.local());var actual=l.getBlockState(p);if(!compatible(actual,c))throw new IOException("保留地下室现场改动："+p.toShortString());if(l.getBlockEntity(p)!=null&&!actual.equals(c.after()))throw new IOException("保留地下室现场容器："+p.toShortString());}
    }
    public static void alignWorkers(ServerLevel l,BlockPos origin) {
        var target=origin.offset(TavernUpgrade.SELF);NpcPlacementSavedData.get(l).put(new NpcPlacementSavedData.Placement("self","ORIGIN",465,90,292));
        var box=new AABB(origin.offset(TavernUpgrade.MIN),origin.offset(TavernUpgrade.MAX).offset(1,1,1));
        for(var self:l.getEntitiesOfClass(SelfNpcEntity.class,box)){self.moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,90,0);self.setYHeadRot(90);self.setYBodyRot(90);}
        var guild=origin.offset(436,90,286);
        for(var npc:l.getEntitiesOfClass(com.tnc.tnc.adventure.TownServiceNpc.class,box,n->n.role().equals("guild"))){npc.moveTo(guild.getX()+.5,guild.getY(),guild.getZ()+.5,0,0);npc.setYHeadRot(0);npc.setYBodyRot(0);}
        com.tnc.tnc.adventure.TownServices.prepareDecoratedTavern(l.getServer(),true);
    }
    private static boolean ready(ServerLevel l,BlockPos origin) {
        var chunks=new HashSet<ChunkPos>();var a=origin.offset(TavernUpgrade.MIN);var b=origin.offset(TavernUpgrade.MAX);
        for(int x=a.getX()>>4;x<=b.getX()>>4;x++)for(int z=a.getZ()>>4;z<=b.getZ()>>4;z++)chunks.add(new ChunkPos(x,z));
        chunks.forEach(c->l.getChunkSource().addRegionTicket(TICKET,c,2,c));TICKETS.put(l.getServer(),chunks);return chunks.stream().allMatch(c->l.hasChunk(c.x,c.z)&&l.areEntitiesLoaded(c.toLong()));
    }
    public static void tick(MinecraftServer server) {
        var l=server.overworld();if(!TavernUpgrade.complete(server)||l.players().isEmpty())return;var s=state(l);if(s.phase==2||s.phase<0)return;
        try {
            var plan=PLANS.get(server);if(plan==null){plan=load(l);PLANS.put(server,plan);}var origin=SkyIslandSavedData.get(l).anchorPos("ORIGIN");
            if(s.hash.isEmpty()){s.hash=plan.hash();s.origin=origin;s.setDirty();}if(!s.hash.equals(plan.hash())||!s.origin.equals(origin))throw new IOException("地下室施工资源或岛原点变化");
            if(!ready(l,origin)||TavernUpgrade.occupied(l,origin)||!l.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,new AABB(origin.offset(MIN),origin.offset(MAX).offset(1,1,1)),p->!p.isSpectator()).isEmpty())return;
            if(s.phase==0){validate(l,origin,plan.changes());s.phase=1;s.cursor=0;s.setDirty();}
            for(int budget=256;budget>0&&s.cursor<plan.changes().size();budget--,s.cursor++){var c=plan.changes().get(s.cursor);if(!compatible(l.getBlockState(origin.offset(c.local())),c))throw new IOException("地下室施工中出现现场改动");TavernUpgrade.place(l,origin,c);}
            if(s.cursor==plan.changes().size()){alignWorkers(l,origin);TavernUpgrade.ensureGuests(l,origin,plan.atmosphere());l.getChunkSource().save(true);s.phase=2;s.setDirty();release(l);LogUtils.getLogger().info("[TN-C Tavern] cellar restored; entrance cushions empty; counters aligned at {}",origin);}
        }catch(Exception error){s.phase=-1;s.error=error.getMessage();s.setDirty();release(l);LogUtils.getLogger().error("[TN-C Tavern] cellar correction paused",error);}
    }
    private static void release(ServerLevel l){for(var c:TICKETS.getOrDefault(l.getServer(),Set.of()))l.getChunkSource().removeRegionTicket(TICKET,c,2,c);TICKETS.remove(l.getServer());}
    public static void stop(MinecraftServer server){release(server.overworld());PLANS.remove(server);}
}
