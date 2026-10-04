package com.tnc.tnc.life.botanical;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class CultivationFreedomGameTests {
    @GameTest(template="building_test_empty",batch="cultivation_freedom",timeoutTicks=30)
    public static void allNineteenCultivarsGrowOnOrdinaryDrySoilUnderARoof(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,2,2));
        for(var s:BotanicalSpecies.values()){
            var block=BotanicalContent.BLOCKS.get(s.id);l.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            l.setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());
            for(int y=1;y<=3;y++)l.setBlockAndUpdate(p.above(y),Blocks.AIR.defaultBlockState());
            l.setBlockAndUpdate(p.above(4),Blocks.STONE.defaultBlockState());
            var state=block.defaultBlockState();h.assertTrue(state.canSurvive(l,p),s.id+" accepts ordinary dry soil");
            l.setBlockAndUpdate(p,state);var be=(BotanicalPlantEntity)l.getBlockEntity(p);
            h.assertTrue(be.reason(l).isEmpty(),s.id+" does not require its wild habitat, a frame, heat, ice or a book");
            var tag=be.saveWithoutMetadata();tag.putBoolean("WasGrowing",true);tag.putLong("LastActiveTick",l.getGameTime()-2400);be.load(tag);be.onLoad();
            h.assertTrue(be.growth==1200&&!be.ready&&be.pollinations==0,s.id+" catches up only basic growth, without inventing event or pollination evidence");
        }h.succeed();
    }
}
