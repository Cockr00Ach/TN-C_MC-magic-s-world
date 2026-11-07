package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarriorwaves") @PrefixGameTestTemplate(false)
public final class WarriorWaveGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=45)
    public static void independentLevelTickMovesDamagesOnceAndExpires(GameTestHelper h) {
        var p=AdventureGameTests.player(h);var at=h.absolutePos(new BlockPos(5,3,3));
        p.setPos(at.getX(),at.getY(),at.getZ());p.setYRot(0);p.setXRot(0);
        p.setNoGravity(true);
        p.setInvulnerable(true); // neighbouring range tests must not knock the caster out of this isolated scenario
        for(int z=0;z<=9;z++)for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)
            h.getLevel().setBlock(p.blockPosition().offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        h.getLevel().addNewPlayer(p); // moving projectiles resolve their owner through the real entity table
        TemperingData.get(p.server).enroll(p.getUUID());
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TNMod.YUE_BRONZE_JIAN.get()));
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new BlockPos(5,3,6));
        target.getPersistentData().putUUID("DummyOwner",p.getUUID());
        target.setPos(p.getX(),p.getY(),p.getZ()+3);
        h.assertTrue(h.getLevel().getEntity(p.getUUID())==p,"Wave owner is registered in the real level");
        WarriorWaves.onFinisher(p,WarriorCombos.bySlot(WarriorWeaponFamily.LONGSWORD,0),1);
        h.assertTrue(WarriorWaves.activeCount(p.getUUID())==1,"Offensive finisher creates a wave");
        h.runAfterDelay(2,()->System.out.println("WAVE CHECK hits="+target.hitCount()+" active="+WarriorWaves.activeCount(p.getUUID())
                +" owner="+(h.getLevel().getEntity(p.getUUID())==p)+" visible="+p.hasLineOfSight(target)+" player="+p.position()+" target="+target.position()));
        h.runAfterDelay(18,()->{
            try {
                h.assertTrue(target.hitCount()==1,"Real level events hit exactly once; actual="+target.hitCount()+", active="+WarriorWaves.activeCount(p.getUUID()));
                h.assertTrue(WarriorWaves.activeCount(p.getUUID())==0,"Expired waves leave no server state");h.succeed();
            }finally{target.discard();p.discard();}
        });
    }
}
