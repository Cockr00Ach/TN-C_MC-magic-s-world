package com.tnc.tnc.boss;

import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.phys.Vec3;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class GiantSimulationGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="giant_simulation")
    public static void giantPhysicsIsBoundedAndCombatBoundsFollowMovement(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());
        boss.setPos(Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(2,3,2))));
        var start=boss.position();var body=boss.getBoundingBox();
        GiantSimulation.run(boss,()->{
            h.assertTrue(boss.getBoundingBox().getXsize()<=3&&boss.getBoundingBox().getYsize()<=6,"Terrain work uses at most 3x6x3 blocks");
            boss.setPos(start.add(2,1,-3));
            h.assertTrue(boss.getBoundingBox().getXsize()<=3,"Position changes cannot recreate the full scan during physics");
        });
        h.assertTrue(boss.getBoundingBox().equals(body.move(2,1,-3)),"The complete combat hitbox follows the final position");
        h.assertTrue(boss.getBbWidth()==24&&boss.getBbHeight()==56,"Render and targeting dimensions are unchanged");
        boss.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="giant_simulation")
    public static void nestedAndFailedPhysicsNeverLeakCompactCombatBounds(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());var body=boss.getBoundingBox();
        try{GiantSimulation.run(boss,()->GiantSimulation.run(boss,()->{throw new IllegalStateException("fixture");}));}
        catch(IllegalStateException expected){}
        h.assertTrue(!GiantSimulation.active(boss)&&boss.getBoundingBox().equals(body),"Physics scopes always restore hit detection, even after a failed callback");
        boss.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=20,batch="giant_simulation")
    public static void giantApproachSteersWithoutAnAStarPathAndSuspendsForCasting(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());boss.setNoAi(true);
        var enemy=h.spawn(net.minecraft.world.entity.EntityType.VILLAGER,new net.minecraft.core.BlockPos(5,2,5));enemy.setNoAi(true);
        boss.setPos(enemy.position().add(-50,0,0));boss.setTarget(enemy);
        var goal=new GiantApproachGoal(boss,1,()->!boss.isCasting());
        h.assertTrue(goal.canUse(),"Giant can approach its target");goal.start();goal.tick();
        h.assertTrue(boss.getNavigation().getPath()==null&&boss.getMoveControl().hasWanted(),"Chasing issues steering without building a huge path search");
        boss.startCast(4,h.getLevel());h.assertTrue(!goal.canContinueToUse(),"Steering yields to the airborne T5 ritual");
        goal.stop();boss.discard();enemy.discard();h.succeed();
    }
}
