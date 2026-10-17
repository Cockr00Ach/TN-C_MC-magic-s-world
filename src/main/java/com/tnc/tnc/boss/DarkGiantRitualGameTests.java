package com.tnc.tnc.boss;

import com.tnc.tnc.magic.TNMagicCircleEntity;
import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class DarkGiantRitualGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="dark_giant_ritual")
    public static void skyHandsHaveAnOversizedAirborneSealAndSafeReturn(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());boss.setNoAi(true);
        Vec3 home=Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)));boss.setPos(home);
        Vec3 landing=home.add(60,0,0);var ritual=new DarkGiantSkyCast(boss,landing);
        for(int i=0;i<DarkGiantSkyCast.RISE_TICKS;i++)ritual.windup(boss);
        h.assertTrue(boss.position().distanceTo(landing.add(0,44,0))<.001,"Boss reaches the point above the falling hands");
        var seals=h.getLevel().getEntitiesOfClass(TNMagicCircleEntity.class,boss.getBoundingBox().inflate(1),
                c->c.style()==TNMagicCircleEntity.STYLE_DARK_RITUAL&&c.position().distanceTo(boss.position())<1);
        h.assertTrue(seals.size()==1&&seals.get(0).radius()*2>boss.getBbWidth()*2,"An oversized seal appears at the feet in the sky");
        h.assertTrue(seals.get(0).getBoundingBoxForCulling().getXsize()>=seals.get(0).radius()*2,"The wide seal cannot be clipped to a tiny entity box");
        for(int i=0;i<DarkGiantSkyCast.HOLD_TICKS+DarkGiantSkyCast.RETURN_TICKS;i++)ritual.recover(boss);
        h.assertTrue(boss.position().distanceTo(home)<.001&&!boss.isNoGravity(),"Boss returns with gravity restored");
        h.assertTrue(seals.get(0).isRemoved(),"The airborne seal is cleaned up");boss.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="dark_giant_ritual")
    public static void savedSkyWindupCannotLeaveAnOldBossFloatingForever(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());boss.setNoAi(true);
        Vec3 home=Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)));boss.setPos(home);
        var enemy=h.spawn(net.minecraft.world.entity.EntityType.VILLAGER,new net.minecraft.core.BlockPos(5,2,5));enemy.setNoAi(true);
        boss.setTarget(enemy);boss.startCast(4,h.getLevel());
        for(int i=0;i<12;i++)boss.aiStep();
        h.assertTrue(boss.skyfallLanding()!=null&&boss.isNoGravity(),"Production T5 starts the airborne ritual");
        var saved=new net.minecraft.nbt.CompoundTag();boss.addAdditionalSaveData(saved);
        var restored=TNNpcs.DARK_GIANT.get().create(h.getLevel());restored.readAdditionalSaveData(saved);
        h.assertTrue(!restored.isNoGravity()&&restored.position().distanceTo(home)<.001,"Reload recovers the original grounded position");
        boss.discard();restored.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="dark_giant_ritual")
    public static void siphonRestrainsAndRaisesAPlayerWithoutPersistentGravityChanges(GameTestHelper h){
        var boss=TNNpcs.DARK_GIANT.get().create(h.getLevel());boss.setPos(Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1))));
        var player=com.tnc.tnc.adventure.AdventureGameTests.player(h);player.setPos(boss.position().add(40,0,0));
        player.setDeltaMovement(1,-1,1);player.fallDistance=12;
        DarkGiantSiphonControl.apply(boss,player);
        h.assertTrue(player.getDeltaMovement().x<0&&player.getDeltaMovement().y>0,"The captured player moves slowly inward and upward");
        h.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)<.001,"Voluntary walking is suppressed");
        h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)&&!player.isNoGravity(),"Short-lived effects suspend the player without a persistent gravity flag");
        h.assertTrue(player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN).getDuration()<=4,"Control expires promptly when the link stops refreshing");
        h.assertTrue(player.fallDistance==0,"The channel resets accumulated fall damage");boss.discard();h.succeed();
    }
}
