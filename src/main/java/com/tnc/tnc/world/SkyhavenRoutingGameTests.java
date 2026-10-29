package com.tnc.tnc.world;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncacademy") @PrefixGameTestTemplate(false)
public final class SkyhavenRoutingGameTests {
 @GameTest(template="building_test_empty",timeoutTicks=100)
 public static void freshAndLegacyIslandsResolveToTheirOwnWorld(GameTestHelper h){
  var s=h.getLevel().getServer();var root=s.overworld().getDataStorage();var original=SkyIslandSavedData.get(s.overworld());var choice=SkyIslandWorlds.Choice.get(s);boolean prior=choice.realm;
  try{
   root.set("tnc_sky_island_v5",new SkyIslandSavedData());choice.realm=false;
   h.assertTrue(SkyIslandWorlds.island(s)==s.overworld(),"legacy default is overworld");
   SkyIslandWorlds.chooseFresh(s);var realm=s.getLevel(com.tnc.tnc.world.skyhaven.SkyhavenContent.KEY);
   h.assertTrue(SkyIslandWorlds.island(s)==realm,"fresh island resolves to independent realm");
   h.assertTrue(SkyIslandSavedData.get(realm)==SkyIslandSavedData.get(s.overworld()),"all consumers share one durable lifecycle record");
   var legacy=SkyIslandSavedData.get(s.overworld());legacy.version=5;legacy.phase=SkyIslandSavedData.Phase.COMPLETE;
   choice.realm=false;boolean blocked=false;try{SkyIslandWorlds.chooseFresh(s);}catch(IllegalStateException expected){blocked=true;}
   h.assertTrue(blocked&&!SkyIslandWorlds.separate(s),"complete legacy island cannot be opted into migration");h.succeed();
  }finally{root.set("tnc_sky_island_v5",original);choice.realm=prior;choice.setDirty();}
 }
}
