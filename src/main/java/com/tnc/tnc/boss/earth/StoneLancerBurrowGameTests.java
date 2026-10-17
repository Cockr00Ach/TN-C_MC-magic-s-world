package com.tnc.tnc.boss.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class StoneLancerBurrowGameTests {
    private static StoneLancerEntity setup(GameTestHelper h){
        var boss=h.spawn(StoneLancerRegistration.TYPE,new BlockPos(2,3,2));boss.setNoAi(true);
        // The fixture explicitly loads its route. Production dash code never generates chunks.
        for(int z=0;z<=128;z+=16)h.getLevel().getChunk(h.absolutePos(new BlockPos(2,3,z)));
        var target=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,110));target.setNoAi(true);boss.setTarget(target);return boss;
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void burrowIsAContinuousDashEmergenceAndCoreSequence(GameTestHelper h){
        var b=setup(h);var target=b.getTarget();double initial=b.distanceTo(target);
        h.assertTrue(b.beginBurrow(target)&&b.burrowPhase()==1&&b.isCasting(),"Distant target starts the digging animation");
        for(int i=0;i<24;i++)b.aiStep();
        h.assertTrue(b.burrowPhase()==2&&b.noPhysics&&b.distanceTo(target)==initial,"Boss sinks completely before starting horizontal travel");
        int ticks=0;while(b.burrowPhase()==2&&ticks++<40)b.aiStep();
        h.assertTrue(b.burrowPhase()==3&&b.distanceTo(target)<40&&!b.noPhysics,"The high-speed dash ends next to its target and begins emerging");
        h.assertTrue(b.attacks.isEmpty(),"Core cannot fire while the boss is still underground");
        for(int i=0;i<28;i++)b.aiStep();
        h.assertTrue(b.burrowPhase()==0&&b.isCasting()&&!b.isNoGravity(),"Emergence flows straight into the core cast and restores gravity");
        for(int i=0;i<24;i++)b.aiStep();
        h.assertTrue(b.attacks.size()==1&&b.attacks.get(0).mode()==2&&!b.isCasting(),"Exactly one attracting and exploding core follows the emergence");
        h.assertTrue(!b.beginBurrow(target),"The completed dash has a cooldown");
        b.discard();target.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void losingTargetAndReloadingCannotLeaveAnInvisibleBoss(GameTestHelper h){
        var b=setup(h);var target=b.getTarget();b.beginBurrow(target);
        for(int i=0;i<24;i++)b.aiStep();
        var tag=new CompoundTag();b.addAdditionalSaveData(tag);
        var restored=StoneLancerRegistration.TYPE.create(h.getLevel());restored.readAdditionalSaveData(tag);
        h.assertTrue(restored.burrowPhase()==0&&!restored.isCasting()&&!restored.noPhysics&&!restored.isNoGravity(),"Reload cancels transient underground state and restores collision and gravity");
        target.discard();b.aiStep();
        for(int i=0;i<28;i++)b.aiStep();
        h.assertTrue(b.burrowPhase()==0&&!b.isCasting()&&!b.noPhysics&&!b.isNoGravity()&&b.attacks.isEmpty(),"Lost target causes a normal emergence without a phantom attack");
        restored.discard();b.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void alternateRemoteResponseChannelsTripleCoreAndPullsDistantVictimsUpward(GameTestHelper h){
        var b=setup(h);var target=b.getTarget();
        var state=new CompoundTag();b.addAdditionalSaveData(state);state.putInt("StoneDistantResponses",1);b.readAdditionalSaveData(state);
        h.assertTrue(StoneLancerEntity.distantMoveFor(0)==-1&&StoneLancerEntity.distantMoveFor(1)==6&&StoneLancerEntity.distantMoveFor(2)==-1,"Remote responses alternate between burrowing and the giant gravity core");
        h.assertTrue(b.tryDistantResponse(target)&&b.burrowPhase()==0&&b.isCasting(),"The second distant encounter chooses a stationary core cast");
        var original=b.position();for(int i=0;i<60;i++)b.aiStep();
        h.assertTrue(b.attacks.size()==1&&b.attacks.get(0).gravityCore()&&b.isCasting(),"Exactly one gravity core appears, while its owner remains channeling");
        var core=b.attacks.get(0);h.assertTrue(core.scale()==150&&core.height()==150,"The response core is three times the ordinary 50-block core");
        h.assertTrue(b.position().subtract(original).horizontalDistance()<.001,"The boss casts in place");
        var chunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(2,3,350)));
        boolean alreadyForced=h.getLevel().getForcedChunks().contains(chunk.toLong());
        if(!alreadyForced)h.getLevel().setChunkForced(chunk.x,chunk.z,true);
        h.getLevel().getChunk(chunk.x,chunk.z);
        // Outside the small fixture, generated chunks need a tick to become entity-visible.
        h.runAfterDelay(6,()->{
            var distant=h.spawn(EntityType.VILLAGER,new BlockPos(2,3,350));distant.setNoAi(true);
            try{
                core.tick();core.tick();var pull=distant.getDeltaMovement();
                h.assertTrue(pull.length()>3&&pull.length()<=12.001&&pull.y>0&&pull.dot(core.position().subtract(distant.getBoundingBox().getCenter()))>0,
                        "Distant victim must be pulled rapidly in 3D; velocity="+pull+", distance="+core.distanceTo(distant));
                for(int i=0;i<84;i++)b.aiStep();h.assertTrue(!b.isCasting()&&b.burrowPhase()==0,"The channel ends normally instead of leaving an immobile boss");
                h.succeed();
            }finally{b.discard();target.discard();distant.discard();if(!alreadyForced)h.getLevel().setChunkForced(chunk.x,chunk.z,false);}
        });
    }
}
