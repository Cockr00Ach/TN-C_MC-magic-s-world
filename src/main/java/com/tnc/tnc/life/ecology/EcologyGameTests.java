package com.tnc.tnc.life.ecology;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class EcologyGameTests {
    @GameTest(template="building_test_empty",batch="ecology",timeoutTicks=30)
    public static void plantStorageSurvivesAndCannotDoubleDrawInOneSecond(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));
        level.setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(p,EcologyContent.VERDANT.defaultBlockState().setValue(VerdantVeinBlock.AGE,3));
        var be=(VerdantVeinBlockEntity)level.getBlockEntity(p);
        var owner=AdventureGameTests.player(h).getUUID();var tag=be.saveWithoutMetadata();tag.putUUID("Owner",owner);tag.putInt("Mana",40);tag.putInt("Growth",24000);be.load(tag);
        h.assertTrue(be.drawMana(8)==1&&be.storedMana()==39,"A receiver debits only one real mana");
        h.assertTrue(be.drawMana(8)==0&&be.storedMana()==39,"A competing receiver cannot draw the same second");
        var copy=new VerdantVeinBlockEntity(p,be.getBlockState());copy.load(be.saveWithoutMetadata());
        h.assertTrue(copy.storedMana()==39&&owner.equals(copy.manaOwner()),"Owner and debited charge survive restart");
        be.prune();h.assertTrue(be.storedMana()==0&&level.getBlockState(p).getValue(VerdantVeinBlock.AGE)==1,"Pruning spends stored charge and gives up mature generation");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="ecology",timeoutTicks=50)
    public static void cultivatedPlantProducesRealManaEvenInShadeWithoutWater(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));l.setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());l.setBlockAndUpdate(p,EcologyContent.VERDANT.defaultBlockState().setValue(VerdantVeinBlock.AGE,3));
        l.setBlockAndUpdate(p.above(),Blocks.STONE.defaultBlockState());var be=(VerdantVeinBlockEntity)l.getBlockEntity(p);
        // Cross a real one-second generation boundary; an arbitrary direct call can
        // miss the modulo-20 update entirely and give a false-positive test.
        h.runAfterDelay(21,()->{int before=be.storedMana();h.assertTrue(before>=1&&before<=2&&be.drawMana(1)==1&&be.storedMana()==before-1,"A real generation boundary creates one mana, and transfer debits it even on dry shaded soil");h.succeed();});
    }
    @GameTest(template="building_test_empty",batch="ecology",timeoutTicks=30)
    public static void reloadCreditsCultivatedGrowthOnceButNeverOfflineMana(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));
        l.setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());l.setBlockAndUpdate(p,EcologyContent.VERDANT.defaultBlockState());l.setBlockAndUpdate(p.above(),Blocks.STONE.defaultBlockState());
        var be=(VerdantVeinBlockEntity)l.getBlockEntity(p);var tag=be.saveWithoutMetadata();
        tag.putInt("Growth",0);tag.putInt("Mana",0);tag.putBoolean("WasGrowing",true);tag.putLong("LastActiveTick",l.getGameTime()-96000);
        be.load(tag);be.onLoad();
        h.assertTrue(l.getBlockState(p).getValue(VerdantVeinBlock.AGE)==3&&be.storedMana()==0,"Cultivated growth catches up on dry shaded soil; unloaded energy generation remains zero");
        be.onLoad();h.assertTrue(be.storedMana()==0,"Repeated load callbacks cannot synthesize energy");h.succeed();
    }
}
