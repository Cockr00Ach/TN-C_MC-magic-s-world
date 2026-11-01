package com.tnc.tnc.world.ink;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncink") @PrefixGameTestTemplate(false)
public final class InkDragonStormGameTests {
    @GameTest(batch="ink_pillarsDamageAndEruptionCreatesOrb",template="ink_crane_empty",timeoutTicks=215)
    public static void pillarsDamageAndEruptionCreatesOrb(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);
        var dragon=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(5,185,5));dragon.setNoAi(true);
        var pig=h.spawn(EntityType.PIG,new BlockPos(-40,175,5));pig.setNoAi(true);pig.setNoGravity(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);pig.setHealth(200);
        h.assertTrue(dragon.startInkAttack(3,pig),"Pillar attack starts");
        h.runAtTickTime(65,()->h.assertTrue(pig.getHealth()<200,"First telegraphed pillar deals server damage"));
        h.runAtTickTime(130,()->{
            pig.setHealth(200);pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            h.assertTrue(dragon.startInkAttack(4,pig),"Eruption starts after previous move ends");
        });
        h.runAtTickTime(195,()->h.assertTrue(pig.getY()>h.absolutePos(new BlockPos(-40,175,5)).getY()+40,"Eruption carries its target high into the air"));
        h.runAtTickTime(205,()->{
            boolean orb=!h.getLevel().getEntitiesOfClass(InkRitualEntity.class,dragon.getBoundingBox().inflate(400),e->e.ownedBy(dragon)&&e.mode()==InkRitualEntity.ORB).isEmpty();
            dragon.discard();h.assertTrue(!InkRestraints.held(pig),"Removing caster releases the target");pig.discard();
            h.assertTrue(orb,"Eruption creates an airborne gathering orb after surfacing");h.succeed();
        });
    }
    @GameTest(batch="ink_lockedPillarsCanBeDodged",template="ink_crane_empty",timeoutTicks=105)
    public static void lockedPillarsCanBeDodged(GameTestHelper h){
        InkCombatTestIsolation.prepare(h);
        var dragon=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(5,245,5));dragon.setNoAi(true);
        var pig=h.spawn(EntityType.PIG,new BlockPos(-40,235,5));pig.setNoAi(true);pig.setNoGravity(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);pig.setHealth(200);
        h.assertTrue(dragon.startInkAttack(3,pig),"Pillar attack starts");
        h.runAtTickTime(30,()->pig.setPos(pig.position().add(60,0,0)));
        h.runAtTickTime(98,()->{
            float health=pig.getHealth();double distance=dragon.effectVector("point").distanceTo(pig.position());dragon.discard();pig.discard();
            h.assertTrue(health==200&&distance>50,"Moving after telegraph lock escapes every pillar");h.succeed();
        });
    }

    @net.minecraft.gametest.framework.BeforeBatch(batch="ink_pillarsDamageAndEruptionCreatesOrb")
    public static void isolate_pillarsDamageAndEruptionCreatesOrb(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}

    @net.minecraft.gametest.framework.BeforeBatch(batch="ink_lockedPillarsCanBeDodged")
    public static void isolate_lockedPillarsCanBeDodged(net.minecraft.server.level.ServerLevel level){InkCombatTestIsolation.clear(level);}
}
