package com.tnc.tnc.boss;

import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class DarkGiantScaleGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="dark_giant_scale")
    public static void tenfoldSummonsHaveMatchingCollisionDimensions(GameTestHelper h){
        var summon=com.tnc.tnc.magic.TNOrbEntities.DARK_KING.get().create(h.getLevel());
        summon.setScale(10);
        h.assertTrue(Math.abs(summon.scale()-10)<.001,"Summon scale is not clamped to the old eightfold limit");
        h.assertTrue(Math.abs(summon.getBbWidth()-9)<.001&&Math.abs(summon.getBbHeight()-20)<.001,
                "Model scale and collision dimensions agree");
        summon.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="dark_giant_scale")
    public static void existingGiantsUpgradeTheirSavedDetectionRange(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());
        boss.setNoAi(true);
        var follow=boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        follow.setBaseValue(40);boss.aiStep();
        h.assertTrue(Math.abs(follow.getBaseValue()-128)<.001,"Old saved giants acquire targets at the upgraded range");
        boss.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="dark_giant_scale")
    public static void summonsKeepTheirMobOwnerAndObeyItsTarget(GameTestHelper h){
        var owner=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new net.minecraft.core.BlockPos(1,2,1));
        owner.setNoAi(true);
        var enemy=h.spawn(net.minecraft.world.entity.EntityType.VILLAGER,new net.minecraft.core.BlockPos(5,2,5));
        enemy.setNoAi(true);owner.setTarget(enemy);
        var summon=h.spawn(com.tnc.tnc.magic.TNOrbEntities.DARK_KING.get(),new net.minecraft.core.BlockPos(3,2,3));
        summon.setOwner(owner.getUUID());summon.setLifetime(1400);
        summon.aiStep();
        h.assertTrue(summon.isAlive(),"A mob-owned summon must not vanish as though its player logged out");
        h.assertTrue(summon.getTarget()==enemy,"The army obeys its mob owner's target");
        owner.discard();summon.aiStep();
        h.assertTrue(summon.isRemoved(),"An orphaned army is cleaned up");
        enemy.discard();summon.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="dark_giant_scale")
    public static void actualCollisionAndCullingMatchTheTenfoldGiant(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());
        h.assertTrue(boss!=null,"Boss is registered");
        h.assertTrue(Math.abs(boss.getBbWidth()-24)<.001&&Math.abs(boss.getBbHeight()-56)<.001,
                "Actual spawned dimensions follow tenfold scale even with an older registry size");
        h.assertTrue(boss.getBoundingBoxForCulling().getYsize()>=64,"The upper vapor stays inside the render bounds");
        boss.discard();h.succeed();
    }
}
