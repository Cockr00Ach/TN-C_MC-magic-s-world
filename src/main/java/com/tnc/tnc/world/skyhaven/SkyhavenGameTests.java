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
  h.assertTrue(world.dimensionType().natural()&&world.dimensionType().bedWorks(),"island beds must allow real sleep and respawn");
  h.assertTrue(world.getMinBuildHeight()==0&&world.getMaxBuildHeight()==320,"island and 225-high blueprint fit realm limits");
  h.succeed();
 }
 @GameTest(template="building_test_empty",timeoutTicks=100)
 public static void realBedAllowsSleepingAndSetsRealmRespawn(GameTestHelper h){
  var s=h.getLevel().getServer();var world=s.getLevel(SkyhavenContent.KEY);long oldTime=s.overworld().getDayTime();
  var head=new net.minecraft.core.BlockPos(4,65,4);var foot=head.south();world.getChunk(0,0);
  var saved=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
  for(var pos:java.util.List.of(head,foot,head.below(),foot.below()))saved.put(pos,world.getBlockState(pos));
  var p=new net.minecraft.server.level.ServerPlayer(s,world,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"SkySleepTest"));
  p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(s,new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p){
   @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
   @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
  };
  try{
   world.setBlock(head.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),18);world.setBlock(foot.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),18);
   var bed=net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,net.minecraft.core.Direction.NORTH);
   world.setBlock(head,bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),18);
   world.setBlock(foot,bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.FOOT),18);
   s.overworld().setDayTime(18000);world.updateSkyBrightness();p.setPos(5.5,65,4.5);
   var result=p.startSleepInBed(head);
   h.assertTrue(result.right().isPresent()&&p.isSleeping(),"actual bed interaction must allow sleep: "+result);
   h.assertTrue(p.getRespawnDimension().equals(SkyhavenContent.KEY)&&head.equals(p.getRespawnPosition()),"bed sets respawn in the island dimension");h.succeed();
  }finally{p.stopSleepInBed(true,false);s.overworld().setDayTime(oldTime);world.updateSkyBrightness();saved.forEach((pos,state)->world.setBlock(pos,state,18));}
 }

}
