package com.tnc.tnc.world.stonecrest;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** One durable guardian per actual structure occurrence, never per visiting player. */
@Mod.EventBusSubscriber(modid="tnc")
public final class BossSiteSpawns {
    public static final Map<String,String> LANDMARKS=Map.of("gothic_cathedral","apollo","end_pvp_island","dark_giant");
    private static final String MARK="TncBossSite";
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("tnc")
            .then(net.minecraft.commands.Commands.literal("boss_sites").requires(s->s.hasPermission(2)).executes(c->{
                int count=0;
                for(var level:c.getSource().getServer().getAllLevels())for(var entry:State.get(level).sites.entrySet()) {
                    var s=entry.getValue();var at=s.spot==null?s.origin:s.spot;
                    c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal(s.type+" · "+level.dimension().location()+" · "+at.toShortString()+" · "+s.status),false);count++;
                }
                if(count==0)c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("尚未接近已绑定的建筑或战场，未触发首领刷新检查。"),false);
                return count;
            })));
    }
    static final class Site {
        String type,status="等待安全落点";BlockPos origin,spot;UUID boss;boolean defeated;
        Site(String type,BlockPos origin){this.type=type;this.origin=origin;}
    }
    static final class State extends SavedData {
        final Map<String,Site> sites=new LinkedHashMap<>();
        static State get(ServerLevel level){return level.getDataStorage().computeIfAbsent(State::load,State::new,"tnc_boss_sites_v1");}
        static State load(CompoundTag root){var state=new State();for(Tag raw:root.getList("Sites",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)raw;var site=new Site(t.getString("Type"),BlockPos.of(t.getLong("Origin")));
            site.spot=t.contains("Spot")?BlockPos.of(t.getLong("Spot")):null;site.boss=t.hasUUID("Boss")?t.getUUID("Boss"):null;
            site.defeated=t.getBoolean("Defeated");site.status=t.getString("Status");state.sites.put(t.getString("Key"),site);
        }return state;}
        @Override public CompoundTag save(CompoundTag root){var list=new ListTag();sites.forEach((key,s)->{
            var t=new CompoundTag();t.putString("Key",key);t.putString("Type",s.type);t.putLong("Origin",s.origin.asLong());
            if(s.spot!=null)t.putLong("Spot",s.spot.asLong());if(s.boss!=null)t.putUUID("Boss",s.boss);
            t.putBoolean("Defeated",s.defeated);t.putString("Status",s.status);list.add(t);
        });root.put("Sites",list);return root;}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();if(server.getTickCount()%100!=0)return;
        for(var level:server.getAllLevels()) {
            if(level.players().isEmpty())continue;
            String dimension=level.dimension().location().toString();
            if(dimension.equals("tnc:wind_arena")||dimension.equals("tnc:water_arena")) {
                String type=dimension.equals("tnc:wind_arena")?"canglan_warlord":"water_leviathan";
                var origin=new BlockPos(0,dimension.equals("tnc:wind_arena")?40:32,0);
                ensure(level,"arena:"+type,type,origin,origin,false);continue;
            }
            if(!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))continue;
            Set<String> visited=new HashSet<>();
            // Old-world construction may have moved an island during clearance surveying.
            // Its completed journal, not a fresh seed prediction, owns the actual position.
            for(var job:LandmarkData.get(level).jobs.values()) {
                String type=LANDMARKS.get(job.asset);if(type==null||job.phase!=3)continue;
                var actual=new LandmarkSites.Site(job.asset,job.origin,new ChunkPos(job.origin));
                if(level.players().stream().noneMatch(p->actual.intersects(p.chunkPosition(),32)))continue;
                String key=job.asset+":"+job.origin.asLong();visited.add(key);
                var d=job.manifest().dimensions();
                ensure(level,key,type,job.origin,job.origin.offset(d.getX()/2,0,d.getZ()/2),true);
            }
            if(!LandmarkGenerationMode.enabled(level))continue;
            for(var player:level.players())for(var landmark:LandmarkSites.touching(level,new ChunkPos(player.blockPosition()),32)) {
                String type=LANDMARKS.get(landmark.asset());if(type==null)continue;
                String key=landmark.asset()+":"+landmark.origin().asLong();if(!visited.add(key))continue;
                // A legacy job may still be writing this building. Never spawn into its unfinished shell.
                var pending=LandmarkData.get(level).jobs.values().stream().anyMatch(j->j.asset.equals(landmark.asset())&&j.origin.equals(landmark.origin())&&j.phase!=3);
                if(pending)continue;
                var dimensions=landmark.manifest().dimensions();
                var center=landmark.origin().offset(dimensions.getX()/2,0,dimensions.getZ()/2);
                ensure(level,key,type,landmark.origin(),center,true);
            }
        }
    }
    static void ensure(ServerLevel level,String key,String type,BlockPos origin,BlockPos center,boolean scan) {
        State state=State.get(level);Site site=state.sites.computeIfAbsent(key,k->new Site(type,origin));state.setDirty();
        if(site.defeated)return;
        if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){site.status="和平模式，等待切换难度后刷新";return;}
        if(site.boss!=null){site.status=level.getEntity(site.boss)!=null?"已刷新":"等待原首领所在区块加载（不复制首领）";return;}
        var entityType=ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("tnc",type));
        if(entityType==null||!(entityType.create(level) instanceof Mob boss)){site.status="首领实体未注册";return;}
        // Adopt a manually placed existing guardian instead of duplicating it.
        var existing=level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(center).inflate(160),e->e.getType()==entityType&&e.isAlive()&&!e.getPersistentData().contains(MARK));
        if(!existing.isEmpty()){boss=existing.get(0);boss.getPersistentData().putString(MARK,key);boss.setPersistenceRequired();site.boss=boss.getUUID();site.spot=boss.blockPosition();site.status="已接管原首领";return;}
        BlockPos spot=findStand(level,boss,center,scan);
        if(spot==null&&scan&&type.equals("dark_giant"))spot=giantPlatform(level,boss,center);
        if(spot==null){site.status="等待建筑区块加载／清理落点";return;}
        boss.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,180,0);boss.setPersistenceRequired();boss.restrictTo(spot,96);
        boss.getPersistentData().putString(MARK,key);
        if(!level.addFreshEntity(boss)){site.status="生成被拒绝，将重试";return;}
        site.boss=boss.getUUID();site.spot=spot;site.status="已刷新";
        com.mojang.logging.LogUtils.getLogger().info("[TN-C Boss Sites] {} spawned at {} in {}",type,spot,level.dimension().location());
    }
    static BlockPos findStand(ServerLevel level,Mob boss,BlockPos center,boolean scan) {
        for(int radius:new int[]{0,8,16,32,48,64,96,128,160})for(int side=0;side<(radius==0?1:8);side++) {
            int x=center.getX()+(int)Math.round(Math.cos(side*Math.PI/4)*radius),z=center.getZ()+(int)Math.round(Math.sin(side*Math.PI/4)*radius);
            if(!level.hasChunkAt(new BlockPos(x,center.getY(),z))||!level.areEntitiesLoaded(new ChunkPos(x>>4,z>>4).toLong()))continue;
            int start=scan?Math.max(level.getMinBuildHeight()+1,center.getY()+2):center.getY();
            int end=scan?level.getMaxBuildHeight()-1:center.getY()+20;
            for(int y=start;y<=end;y++) {
                var pos=new BlockPos(x,y,z);
                if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))continue;
                boss.setPos(x+.5,y,z+.5);
                var bounds=boss.getBoundingBox();boolean loaded=true;
                for(int cx=(int)Math.floor(bounds.minX/16);cx<=(int)Math.floor(bounds.maxX/16);cx++)
                    for(int cz=(int)Math.floor(bounds.minZ/16);cz<=(int)Math.floor(bounds.maxZ/16);cz++)
                        if(!level.areEntitiesLoaded(new ChunkPos(cx,cz).toLong()))loaded=false;
                if(!loaded)continue;
                if(boss.getBbWidth()>8) {
                    int support=(int)(boss.getBbWidth()/3);boolean supported=true;
                    for(int dx:new int[]{-support,support})for(int dz:new int[]{-support,support}) {
                        var foot=pos.offset(dx,-1,dz);
                        if(!level.getBlockState(foot).isFaceSturdy(level,foot,Direction.UP))supported=false;
                    }
                    if(!supported)continue;
                }
                if(level.getWorldBorder().isWithinBounds(boss.getBoundingBox())&&level.noCollision(boss,boss.getBoundingBox())
                        &&(boss instanceof net.minecraft.world.entity.animal.WaterAnimal ||level.getFluidState(pos).isEmpty()||!scan))return pos;
            }
        }return null;
    }
    /** Source PvP bridges are too narrow for the authored giant. Add, never excavate, a stage. */
    static BlockPos giantPlatform(ServerLevel level,Mob boss,BlockPos center) {
        int radius=19,top=level.getMinBuildHeight();
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            var p=center.offset(dx,0,dz);
            if(!level.areEntitiesLoaded(new ChunkPos(p).toLong()))return null;
            top=Math.max(top,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ()));
        }
        int y=top+1;if(y>=level.getMaxBuildHeight()-2)return null;
        var floor=new BlockPos(center.getX(),y,center.getZ());
        var area=new net.minecraft.world.phys.AABB(floor.offset(-radius,0,-radius),floor.offset(radius+1,1,radius+1));
        if(!level.getWorldBorder().isWithinBounds(floor.offset(-radius,0,-radius))||!level.getWorldBorder().isWithinBounds(floor.offset(radius,0,radius)))return null;
        if(!level.getEntitiesOfClass(LivingEntity.class,area.inflate(0,.1,0)).isEmpty())return null;
        boss.setPos(floor.getX()+.5,y+1,floor.getZ()+.5);
        if(!level.noCollision(boss,boss.getBoundingBox()))return null;
        // The entire write set is preflighted. No containers, foliage, liquids or blocks replaced.
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(dx*dx+dz*dz>radius*radius)continue;
            if(!level.getBlockState(floor.offset(dx,0,dz)).isAir())return null;
        }
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(dx*dx+dz*dz>radius*radius)continue;
            var block=dx*dx+dz*dz>(radius-2)*(radius-2)?net.minecraft.world.level.block.Blocks.PURPUR_BLOCK:net.minecraft.world.level.block.Blocks.END_STONE_BRICKS;
            level.setBlock(floor.offset(dx,0,dz),block.defaultBlockState(),18);
        }
        return floor.above();
    }
    @SubscribeEvent public static void death(LivingDeathEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        String key=event.getEntity().getPersistentData().getString(MARK);if(key.isEmpty())return;
        var state=State.get(level);var site=state.sites.get(key);
        if(site!=null&&event.getEntity().getUUID().equals(site.boss)){site.defeated=true;site.status="已击败";state.setDirty();}
    }
    @SubscribeEvent public static void removed(net.minecraftforge.event.entity.EntityLeaveLevelEvent event){
        var entity=event.getEntity();
        // A real unload is not a missing boss. Only a living actor explicitly discarded by
        // cleanup/peaceful-mode logic can safely be retried without cloning an unloaded actor.
        if(!(event.getLevel() instanceof ServerLevel level)||!(entity instanceof LivingEntity living)
                ||living.getHealth()<=0||entity.getRemovalReason()!=Entity.RemovalReason.DISCARDED)return;
        String key=entity.getPersistentData().getString(MARK);if(key.isEmpty())return;
        var state=State.get(level);var site=state.sites.get(key);
        if(site!=null&&!site.defeated&&entity.getUUID().equals(site.boss)){site.boss=null;site.status="首领被清理，等待重试";state.setDirty();}
    }
}
