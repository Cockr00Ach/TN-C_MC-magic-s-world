package com.tnc.tnc.life.pasture;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class PastureFlightGameTests {
    @GameTest(template="building_test_empty",batch="pasture_flight",timeoutTicks=30)
    public static void allFourFlyersCanRiseDescendAndBrakeWithoutGroundInputs(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(2,3,2));
        for(var id:java.util.List.of("post_heron","mirrorwing_moth","papersail_ray","dewbound_whale")){
            var animal=PastureRegistry.TYPES.get(id).create(l);animal.setNoAi(true);
            animal.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
            var control=animal.getMoveControl();
            control.setWantedPosition(animal.getX(),animal.getY()+5,animal.getZ(),1);
            double start=animal.getY();
            for(int n=0;n<45;n++){animal.xxa=1;animal.yya=1;animal.zza=1;control.tick();animal.move(MoverType.SELF,animal.getDeltaMovement());}
            h.assertTrue(animal.getY()>start+2&&animal.xxa==0&&animal.yya==0&&animal.zza==0,id+" rises with bounded velocity without residual walking inputs");
            control.setWantedPosition(animal.getX(),start+.5,animal.getZ(),1);
            double top=animal.getY();for(int n=0;n<45;n++){control.tick();animal.move(MoverType.SELF,animal.getDeltaMovement());}
            h.assertTrue(animal.getY()<top-1,id+" can descend instead of permanently floating upward");
            animal.setDeltaMovement(new Vec3(.2,0,.2));
            control.setWantedPosition(animal.getX(),animal.getY(),animal.getZ(),0);
            for(int n=0;n<30;n++)control.tick();
            h.assertTrue(animal.getDeltaMovement().length()<.005,id+" brakes when its goal ends");animal.discard();
        }h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture_flight",timeoutTicks=30)
    public static void floorCollisionLiftsAndCalmingCancelsOldWaypoints(GameTestHelper h){
        var l=h.getLevel();var at=h.absolutePos(new BlockPos(2,2,2));l.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
        var animal=PastureRegistry.TYPES.get("post_heron").create(l);animal.setNoAi(true);animal.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
        animal.move(MoverType.SELF,new Vec3(0,-.1,0));
        h.assertTrue(animal.verticalCollision&&animal.verticalCollisionBelow,"Fixture actually collides with the floor");
        animal.getMoveControl().setWantedPosition(animal.getX()+3,animal.getY()-.5,animal.getZ(),1);animal.getMoveControl().tick();
        h.assertTrue(animal.getDeltaMovement().y>0,"A low destination cannot pin a flyer into the ground");
        animal.calmFor(100);animal.getMoveControl().tick();h.assertTrue(animal.getDeltaMovement().length()<.001,"Calming cancels a stale flight waypoint");animal.discard();h.succeed();
    }
}
