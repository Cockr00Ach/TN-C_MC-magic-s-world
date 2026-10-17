package com.tnc.tnc.life.routes;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class RouteWildSpecimensGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="wild_specimen_crash")
    public static void everySpecialSpecimenUsesItsOwnSupportedStateProperties(GameTestHelper h){
        for(String id:new String[]{"homeward_flower","warning_moss","mana_root","sky_vine"}){
            var block=ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("tnc",id));
            h.assertTrue(block!=null&&block!=Blocks.AIR,"Specimen exists: "+id);
            var state=RouteWildSpecimens.specimenState(block,id);
            h.assertTrue(state.getBlock()==block,"Correct specimen state survives preparation: "+id);
        }
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="wild_specimen_crash")
    public static void actualSkyVineGenerationOnDirtDoesNotCrashTheServerTick(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,3,3));
        level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(RouteWildSpecimens.placeSpecimen(level,pos,"sky_vine"),"Sky vine can be generated on valid dirt");
        h.assertTrue(level.getBlockState(pos).is(com.tnc.tnc.life.wonders.WonderContent.SKY_VINE),"The original crashing branch now places the vine");
        h.assertTrue(!RouteWildSpecimens.placeSpecimen(level,pos.above(),"missing_specimen_fixture"),"Unknown specimen ids fail without placing air or throwing");
        h.succeed();
    }
}
