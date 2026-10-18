package com.tnc.tnc.light;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class LightBeamControlGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_beam_controls")
    public static void forwardRayFollowsCasterLookRatherThanAnEnemy(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(2,2,2));caster.setNoAi(true);
        var target=h.spawn(EntityType.ZOMBIE,new BlockPos(6,2,6));target.setNoAi(true);
        var beam=TNOrbEntities.LIGHT_BEAM.get().create(h.getLevel());
        beam.configure(caster,.1,30,0,120,TNLightBeamEntity.STYLE_RAY,target);beam.setFanOffset(-7);
        caster.setYRot(90);caster.setYHeadRot(90);caster.setXRot(-25);beam.tickCount=2;beam.tick();
        Vec3 expected=LightBeamAim.direction(caster.getViewVector(1),-7);
        h.assertTrue(beam.getLookAngle().distanceTo(expected)<.001,"Ray uses caster look and preserves its fan offset");
        h.assertTrue(beam.position().distanceTo(caster.getEyePosition())<.001,"Ray follows the caster's eye position");
        h.assertTrue(Math.abs(beam.length()-30)<.001&&!beam.tracking(),"A nearby target cannot shorten or lock the ray");
        target.discard();caster.setYRot(-60);caster.setYHeadRot(-60);caster.setXRot(30);beam.tickCount=3;beam.tick();
        h.assertTrue(beam.getLookAngle().distanceTo(LightBeamAim.direction(caster.getViewVector(1),-7))<.001,"Removing an enemy does not trigger target reacquisition");
        caster.discard();beam.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="light_beam_controls")
    public static void skyColumnStaysAtItsMarkedLocationWhenEnemiesAndCasterMove(GameTestHelper h) {
        var caster=h.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));caster.setNoAi(true);
        var target=h.spawn(EntityType.ZOMBIE,new BlockPos(5,2,5));target.setNoAi(true);
        var beam=TNOrbEntities.LIGHT_BEAM.get().create(h.getLevel());
        Vec3 origin=target.position().add(0,40,0);
        beam.moveTo(origin.x,origin.y,origin.z,0,90);
        beam.configure(caster,.2,41,0,120,TNLightBeamEntity.STYLE_DESCENT,target);
        target.setPos(target.position().add(12,3,0));caster.setPos(caster.position().add(-4,0,5));
        beam.tickCount=2;beam.tick();
        h.assertTrue(beam.position().distanceTo(origin)<.001,"Sky column stays aligned with its original ground seal");
        h.assertTrue(beam.getLookAngle().distanceTo(new Vec3(0,-1,0))<.001,"Sky column remains vertical");
        target.discard();beam.tickCount=3;beam.tick();
        h.assertTrue(beam.position().distanceTo(origin)<.001,"Target removal cannot relocate the marked column");
        caster.discard();beam.discard();h.succeed();
    }
}
