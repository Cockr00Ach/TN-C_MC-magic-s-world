package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncwarriorpractical")
@PrefixGameTestTemplate(false)
public final class WarriorBowUltimateGameTests {
    private static void guide(GameTestHelper h){
        WarriorSpecialUltimates.tick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true));
    }
    private static WarriorArrowEntity arrow(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner,Vec3 at,Vec3 motion){
        var arrow=new WarriorArrowEntity(h.getLevel(),owner,0);arrow.setPos(at.x,at.y,at.z);arrow.setDeltaMovement(motion);arrow.setNoGravity(true);
        arrow.getPersistentData().putBoolean("TncSeekingUltimate",true);h.getLevel().addFreshEntity(arrow);return arrow;
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void homingKeepsItsTargetAndImmediatelyStopsSteeringThroughWall(GameTestHelper h){
        // Stay in this template's compact column, above other parallel combat fixtures.
        var center=BlockPos.containing(h.absoluteVec(new Vec3(2,0,2)));int y=h.getLevel().getMaxBuildHeight()-12;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int dy=0;dy<5;dy++)
            h.getLevel().setBlockAndUpdate(new BlockPos(center.getX()+x,y+dy,center.getZ()+z),Blocks.AIR.defaultBlockState());
        Vec3 origin=new Vec3(center.getX()+.5,y,center.getZ()+.5);
        var owner=AdventureGameTests.player(h);owner.setNoGravity(true);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
        owner.setPos(origin.x,origin.y,origin.z-1.3);
        var first=WarriorEntities.TEST_DUMMY.get().create(h.getLevel());var nearer=WarriorEntities.TEST_DUMMY.get().create(h.getLevel());
        first.setPos(origin.x+1,origin.y,origin.z+1);nearer.setPos(origin.x-1.9,origin.y,origin.z+1.9);
        for(var target:java.util.List.of(first,nearer)){target.getPersistentData().putUUID("DummyOwner",owner.getUUID());h.getLevel().addFreshEntity(target);}
        var shot=arrow(h,owner,owner.getEyePosition(),new Vec3(0,0,1.8));
        guide(h);h.assertTrue(shot.getDeltaMovement().x>.05,"Initial arrow bends toward the nearer east target");
        nearer.setPos(origin.x-.3,origin.y,origin.z-.2);shot.setDeltaMovement(0,0,1.8);
        guide(h);h.assertTrue(shot.getDeltaMovement().x>.05,"A newly nearer enemy does not make the locked arrow zigzag between targets");
        var wall=BlockPos.containing(first.position());for(int dy=0;dy<3;dy++)h.getLevel().setBlockAndUpdate(wall.above(dy),Blocks.STONE.defaultBlockState());
        h.assertTrue(!owner.hasLineOfSight(first),"Fixture blocks the previously tracked enemy");
        shot.setDeltaMovement(0,0,1.8);guide(h);
        boolean stoppedTurning=Math.abs(shot.getDeltaMovement().x)<.00001;
        shot.discard();first.discard();nearer.discard();owner.discard();
        for(int dy=0;dy<3;dy++)h.getLevel().setBlockAndUpdate(wall.above(dy),Blocks.AIR.defaultBlockState());
        h.assertTrue(stoppedTurning,"Occlusion invalidates tracking immediately, before the next acquisition window");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void seekingArrowExpiresAtHundredTicksAndEmbeddedArrowDoesNotLinger(GameTestHelper h){
        var center=h.absolutePos(new BlockPos(2,0,2));int y=h.getLevel().getMaxBuildHeight()-6;
        Vec3 origin=new Vec3(center.getX()+.5,y,center.getZ()+.5);
        var owner=AdventureGameTests.player(h);owner.setNoGravity(true);owner.setPos(origin.x,origin.y,origin.z);
        var expired=arrow(h,owner,origin.add(0,1,0),new Vec3(0,0,1.8));expired.tickCount=100;
        guide(h);h.assertTrue(expired.isRemoved(),"A seeking projectile cannot remain beyond its hundredth tick");
        var block=BlockPos.containing(origin.add(0,2,0));h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        // An embedded vanilla arrow may retain non-zero velocity while onGround stays false.
        var embedded=arrow(h,owner,Vec3.atCenterOf(block),new Vec3(0,0,1.8));embedded.tickCount=10;embedded.setOnGround(false);
        h.runAfterDelay(6,()->{
            boolean cleaned=embedded.isRemoved();embedded.discard();owner.discard();h.getLevel().setBlockAndUpdate(block,Blocks.AIR.defaultBlockState());
            h.assertTrue(cleaned,"An arrow lodged in terrain is removed after actual travel stops, even with retained velocity");h.succeed();
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=20)
    public static void stormBowUsesElementalUltimateDespiteSharingNormalStyleIndex(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
        h.assertTrue(!WarriorSpecialUltimates.elemental(p),"Plain bow retains the ordinary ultimate");
        for(var item:java.util.List.of(com.tnc.tnc.TNMod.STORM_BOW.get(),com.tnc.tnc.TNMod.MOON_BOW.get(),com.tnc.tnc.TNMod.EMBER_BOW.get())){
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));h.assertTrue(WarriorSpecialUltimates.elemental(p),"Every authored elemental bow receives the twenty seeking arrows");
        }
        p.discard();h.succeed();
    }
}
