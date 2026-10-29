package com.tnc.tnc.world.skyhaven;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncacademy") @PrefixGameTestTemplate(false)
public final class SkyhavenGameTests {
 @GameTest(template="building_test_empty",timeoutTicks=100)
 public static void seaUsesIndependentFluidAndLoadedDimension(GameTestHelper h){
  var world=h.getLevel().getServer().getLevel(SkyhavenContent.KEY);
  h.assertTrue(world!=null,"skyhaven dimension must load from the data registry");
  h.assertTrue(SkyhavenContent.SOURCE.getFluidType()!=com.tnc.tnc.life.routes.RouteContent.MANA_TYPE.get(),"black sea must not become irrigation mana");
  h.assertTrue(SkyhavenContent.SEA.defaultBlockState().getFluidState().isSource(),"sea block is real source fluid");
  h.assertTrue(world.getMinBuildHeight()==0&&world.getMaxBuildHeight()==320,"island and 225-high blueprint fit realm limits");
  h.succeed();
 }
}
