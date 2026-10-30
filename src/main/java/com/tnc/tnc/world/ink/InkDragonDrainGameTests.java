package com.tnc.tnc.world.ink;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncink") @PrefixGameTestTemplate(false)
public final class InkDragonDrainGameTests {
    @BeforeBatch(batch="ink_drain") public static void isolateDrain(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}
    @BeforeBatch(batch="ink_quarter") public static void isolateQuarter(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}
    @GameTest(batch="ink_drain",template="ink_crane_empty",timeoutTicks=345)
    public static void nearRoarOpensJawDrainsAndEventuallyRestoresSpeed(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);
        var dragon=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(0,110,0));dragon.setNoAi(true);
        var pig=h.spawn(EntityType.PIG,new BlockPos(35,90,0));pig.setNoAi(true);pig.setNoGravity(true);
        double base=pig.getAttributeValue(Attributes.MOVEMENT_SPEED);h.assertTrue(dragon.startInkAttack(5,pig),"Fifth attack starts");
        h.runAtTickTime(55,()->{h.assertTrue(dragon.jawOpen(0)>.95,"Near roar opens the jaw");h.assertTrue(dragon.mouthPosition().distanceTo(pig.getEyePosition())<7,"Nose is close to victim");});
        h.runAtTickTime(95,()->{h.assertTrue(InkDragonDread.active(pig),"Roar applies ink dread");h.assertTrue(Math.abs(pig.getAttributeValue(Attributes.MOVEMENT_SPEED)-base*.25)<.00001,"Real movement attribute is quarter speed");});
        h.runAtTickTime(175,()->h.assertTrue(dragon.inkMove()==0&&dragon.jawOpen(0)==0&&dragon.getY()>pig.getY()+10,"Attack closes jaw and recovers safely above ground"));
        h.runAtTickTime(335,()->{h.assertTrue(!InkDragonDread.active(pig)&&Math.abs(pig.getAttributeValue(Attributes.MOVEMENT_SPEED)-base)<.00001,"Timed status restores original speed");dragon.discard();pig.discard();h.succeed();});
    }
    @GameTest(batch="ink_quarter",template="ink_crane_empty",timeoutTicks=270)
    public static void actualPotionUseTakesFourTimesAsLongAndRemovalRestores(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);var pig=h.spawn(EntityType.PIG,new BlockPos(0,90,0));pig.setNoAi(true);pig.setNoGravity(true);
        double base=pig.getAttributeValue(Attributes.MOVEMENT_SPEED);InkDragonDread.apply(pig);
        pig.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.POTION));pig.startUsingItem(InteractionHand.MAIN_HAND);
        h.runAtTickTime(40,()->h.assertTrue(pig.isUsingItem()&&pig.getUseItemRemainingTicks()>20,"Drinking does not finish at vanilla duration"));
        h.runAtTickTime(134,()->h.assertTrue(!pig.isUsingItem(),"32-tick potion completes after approximately 128 ticks"));
        h.runAtTickTime(250,()->{h.assertTrue(!InkDragonDread.active(pig)&&Math.abs(pig.getAttributeValue(Attributes.MOVEMENT_SPEED)-base)<.00001,"All speed restored on expiry");pig.discard();h.succeed();});
    }
}
