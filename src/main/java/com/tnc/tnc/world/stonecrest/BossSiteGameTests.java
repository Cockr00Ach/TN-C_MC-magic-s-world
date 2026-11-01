package com.tnc.tnc.world.stonecrest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.level.block.Blocks;
@GameTestHolder("tncbosssites") @PrefixGameTestTemplate(false)
public final class BossSiteGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=3000)
    public static void actualCathedralAndFloatingIslandHaveRoomForTheirRealBosses(GameTestHelper h) {
        String[] assets={"gothic_cathedral","end_pvp_island"};int[] cursor={0};
        h.onEachTick(()->{
            if(cursor[0]>=538)return;
            int index=cursor[0]<9?0:1,cell=index==0?cursor[0]:cursor[0]-9,width=index==0?3:23;String asset=assets[index];
            var origin=new BlockPos(12000+index*4096,index==0?-48:100,12000);
            var dim=StonecrestManifest.get(asset).dimensions();var center=origin.offset(dim.getX()/2,0,dim.getZ()/2);
            var chunk=new net.minecraft.world.level.ChunkPos((center.getX()>>4)+cell%width-width/2,(center.getZ()>>4)+cell/width-width/2);
            h.getLevel().setChunkForced(chunk.x,chunk.z,true);
            h.getLevel().getChunk(chunk.x,chunk.z);
            var site=new LandmarkSites.Site(asset,origin,chunk);
            LandmarkChunkFeature.placeSlice(h.getLevel(),chunk,site);
            cursor[0]++;
            if(cursor[0]==538)h.runAfterDelay(10,()->{
                try { for(int i=0;i<2;i++) {
                    var o=new BlockPos(12000+i*4096,i==0?-48:100,12000);var d=StonecrestManifest.get(assets[i]).dimensions();
                    var c=o.offset(d.getX()/2,0,d.getZ()/2);
                    var type=i==0?com.tnc.tnc.npc.TNNpcs.APOLLO.get():com.tnc.tnc.npc.TNNpcs.DARK_GIANT.get();
                    var boss=(net.minecraft.world.entity.Mob)type.create(h.getLevel());
                    var floor=BossSiteSpawns.findStand(h.getLevel(),boss,c,true);
                    if(floor==null&&i==1)floor=BossSiteSpawns.giantPlatform(h.getLevel(),boss,c);
                    h.assertTrue(floor!=null,"Real source building has no valid guardian floor: "+assets[i]);
                    com.mojang.logging.LogUtils.getLogger().info("[TN-C Boss Sites Test] {} actual source floor {}",assets[i],floor);
                    String key="actual:"+assets[i];
                    BossSiteSpawns.ensure(h.getLevel(),key,i==0?"apollo":"dark_giant",o,c,true);
                    var guardian=BossSiteSpawns.State.get(h.getLevel()).sites.get(key);
                    h.assertTrue(guardian.boss!=null&&h.getLevel().getEntity(guardian.boss)!=null,"Real building guardian did not enter world: "+guardian.status);
                    h.getLevel().getEntity(guardian.boss).discard();
                }h.succeed(); } finally {
                    for(int i=0;i<2;i++) {
                        var o=new BlockPos(12000+i*4096,i==0?-48:100,12000);var d=StonecrestManifest.get(assets[i]).dimensions();
                        var c=new net.minecraft.world.level.ChunkPos(o.offset(d.getX()/2,0,d.getZ()/2));
                        int half=i==0?1:11;
                        for(int x=-half;x<=half;x++)for(int z=-half;z<=half;z++)h.getLevel().setChunkForced(c.x+x,c.z+z,false);
                    }
                }
            });
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=160)
    public static void windAndWaterGuardiansSpawnInTheirRealDimensions(GameTestHelper h) {
        var keys=java.util.List.of(com.tnc.tnc.world.elementarena.ElementArenas.WIND,com.tnc.tnc.world.elementarena.ElementArenas.WATER);
        for(var key:keys) {
            var level=h.getLevel().getServer().getLevel(key);
            h.assertTrue(level!=null,"Missing arena dimension: "+key);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        }
        h.runAfterDelay(30,()->{
            try {for(int i=0;i<2;i++) {
                var level=h.getLevel().getServer().getLevel(keys.get(i));
                var at=new BlockPos(0,i==0?40:32,0);String type=i==0?"canglan_warlord":"water_leviathan";
                String key="test:arena:"+type;BossSiteSpawns.ensure(level,key,type,at,at,false);
                var site=BossSiteSpawns.State.get(level).sites.get(key);
                h.assertTrue(site.boss!=null&&level.getEntity(site.boss)!=null,"Arena guardian failed: "+type+" "+site.status);
                var boss=level.getEntity(site.boss);
                h.assertTrue(level.noCollision(boss,boss.getBoundingBox()),"Guardian intersects arena");
                if(i==1)h.assertTrue(!level.getFluidState(boss.blockPosition()).isEmpty(),"Water guardian not underwater");
                boss.discard();
            }h.succeed();}finally{
                for(var key:keys){var level=h.getLevel().getServer().getLevel(key);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setChunkForced(x,z,false);}
            }
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void cathedralGuardianSpawnsOnceAndStaysDefeatedAcrossReload(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(3,2,3));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {h.getLevel().setBlockAndUpdate(at.offset(x,-1,z),Blocks.STONE.defaultBlockState());for(int y=0;y<10;y++)h.getLevel().setBlockAndUpdate(at.offset(x,y,z),Blocks.AIR.defaultBlockState());}
        h.runAfterDelay(5,()->{
            String key="test:"+at.asLong();BossSiteSpawns.ensure(h.getLevel(),key,"apollo",at,at,false);
            var state=BossSiteSpawns.State.get(h.getLevel());var site=state.sites.get(key);
            h.assertTrue(site.boss!=null,"Apollo was not spawned on the validated floor: "+site.status);
            var id=site.boss;BossSiteSpawns.ensure(h.getLevel(),key,"apollo",at,at,false);
            h.assertTrue(id.equals(site.boss),"Duplicate guardian spawned");
            var boss=(net.minecraft.world.entity.LivingEntity)h.getLevel().getEntity(id);
            BossSiteSpawns.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(boss,h.getLevel().damageSources().generic()));
            var restored=BossSiteSpawns.State.load(state.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(restored.sites.get(key).defeated&&id.equals(restored.sites.get(key).boss),"Defeat and identity did not survive save/load");boss.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=80)
    public static void blockedSpawnRetriesWithoutRecordingFalseSuccess(GameTestHelper h) {
        var at=h.absolutePos(new BlockPos(3,4,3));
        h.runAfterDelay(5,()->{
            String key="blocked:"+at.asLong();
            // No floor in this high air column; never invent terrain or record a phantom boss.
            BossSiteSpawns.ensure(h.getLevel(),key,"dark_giant",at.above(100),at.above(100),false);
            var site=BossSiteSpawns.State.get(h.getLevel()).sites.get(key);
            h.assertTrue(site.boss==null,"A missing floor was counted as a successful spawn");
            h.assertTrue(BossSiteSpawns.LANDMARKS.get("gothic_cathedral").equals("apollo")&&BossSiteSpawns.LANDMARKS.get("end_pvp_island").equals("dark_giant"),"Wrong assignments");
            h.assertTrue(!BossSiteSpawns.LANDMARKS.containsValue("yan_dark")&&!BossSiteSpawns.LANDMARKS.containsValue("volcanic_tortoise"),"Unrequested bosses placed");h.succeed();
        });
    }
}
