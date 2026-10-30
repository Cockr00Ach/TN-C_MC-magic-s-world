package com.tnc.tnc.world;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncbosssites") @PrefixGameTestTemplate(false)
public final class SkyPortalRecoveryGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=1500)
    public static void occupiedOriginalSixteenCandidatesExtendSearchWithoutOverwriting(GameTestHelper h) {
        var level=h.getLevel();var spawn=level.getSharedSpawnPos();
        var manifest=SkyIslandManifest.parse(com.google.gson.JsonParser.parseString("""
            {"version":5,"seed":1,"dimensions":[480,160,512],"world_origin_y":90,
             "town_center_local":[240,90,260],"arrival_local":[209,90,424],
             "root_tips":[[240,0,260]],"non_air_blocks":1,"piece_count":1,
             "portal_templates":{"ground_portal":"tnc:sky_island/portal/ritual_gate","island_portal":"tnc:sky_island/portal/ritual_gate"},
             "pieces":[{"resource":"tnc:building_test_empty","offset":[0,0,0],"size":[1,1,1],"blocks":1}]}
            """).getAsJsonObject());
        var protectedBlocks=new java.util.ArrayList<BlockPos>();
        for(var offset:SkyIslandManager.portalSearchOffsets().subList(0,16)) {
            int x=spawn.getX()+offset.getX(),z=spawn.getZ()+offset.getZ();level.getChunk(x>>4,z>>4);
            var p=new BlockPos(x,level.getMaxBuildHeight()-10,z);level.setBlockAndUpdate(p,Blocks.BEDROCK.defaultBlockState());protectedBlocks.add(p);
        }
        int[] calls={0};h.onEachTick(()->{
            if(calls[0]<0)return;
            var result=SkyIslandManager.chooseGroundPortal(level,manifest);calls[0]++;
            if(result!=null) {
                h.assertTrue(calls[0]>16,"Blocked original sites were not skipped");
                h.assertTrue(Math.hypot(result.getX()+7-spawn.getX(),result.getZ()+7-spawn.getZ())>=255,"Fallback search stayed in the blocked spawn neighbourhood");
                for(var p:protectedBlocks)h.assertTrue(level.getBlockState(p).is(Blocks.BEDROCK),"Site search overwrote existing construction");
                calls[0]=-1;h.succeed();
            }
        });
    }
}
