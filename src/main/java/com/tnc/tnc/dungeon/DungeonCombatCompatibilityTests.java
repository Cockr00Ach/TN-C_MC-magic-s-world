package com.tnc.tnc.dungeon;
import com.tnc.tnc.combat.CombatGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
/** Reuse the original combat regression scenarios outside the dungeon dimension. */
@GameTestHolder("tncdungeon") @PrefixGameTestTemplate(false)
public final class DungeonCombatCompatibilityTests {
 @GameTest(template="mirror_test_empty",timeoutTicks=100) public static void originalThreeSecondTeammateRescueStillWorks(GameTestHelper h){CombatGameTests.cooperativeRescueRequiresRealThreeSeconds(h);}
 @GameTest(template="mirror_test_empty",timeoutTicks=30) public static void originalNonDungeonPartyDeathStillWorks(GameTestHelper h){CombatGameTests.multiplayerWholePartyDownedProducesRealDeaths(h);}
 @GameTest(template="mirror_test_empty",timeoutTicks=30) public static void originalReconnectKeepsDownedAndCooldown(GameTestHelper h){CombatGameTests.cloneAndReconnectDoNotResetDownedStateOrCooldown(h);}
}
