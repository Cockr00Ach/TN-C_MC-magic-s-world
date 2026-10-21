package com.tnc.tnc.world.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncdungeon")
@PrefixGameTestTemplate(false)
public final class CanglanWarlordGameTests {
    @GameTest(template="building_test_empty", timeoutTicks=100, batch="canglan_registration")
    public static void registeredBossSpawnsTicksAndEntersSpearPhase(GameTestHelper helper) {
        var boss=helper.spawn(CanglanWarlordRegistration.CANGLAN_WARLORD,new BlockPos(2,1,2));
        boss.setNoGravity(true);
        helper.assertTrue(boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue()==1200.0D,"Boss attributes missing");
        helper.assertTrue(CanglanWarlordRegistration.EGG!=null,"Spawn egg missing");
        var target=helper.spawn(EntityType.VILLAGER,new BlockPos(3,1,2));
        target.setNoAi(true);
        boss.setTarget(target);
        boss.setHealth(500.0F);
        helper.runAfterDelay(65,()->{
            helper.assertTrue(boss.isAlive(),"Boss died during initial ticks");
            helper.assertTrue(boss.weaponForm()==CanglanWarlordAttackRules.FORM_SPEAR,"Half-health phase transition failed");
            helper.succeed();
        });
    }
}
