package com.tnc.tnc.world.elementarena;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
@GameTestHolder("tncelementarena")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ElementArenaGameTests {
 @GameTest(template="empty",timeoutTicks=100)
 public static void bothRealmsUseMatchingBlocksAndAccurateHeightmaps(GameTestHelper h){
  for(var key:java.util.List.of(ElementArenas.WIND,ElementArenas.WATER)){
   var level=h.getLevel().getServer().getLevel(key);h.assertTrue(level!=null,"Dimension missing: "+key);
   var gen=(ElementArenaChunkGenerator)level.getChunkSource().getGenerator();
   for(int[] point:new int[][]{{0,0},{8,0},{120,120},{-120,-120}}){
    int x=point[0],z=point[1];level.getChunk(x>>4,z>>4);
    h.assertTrue(level.getBlockState(new net.minecraft.core.BlockPos(x,31,z)).equals(gen.material(x,31,z)),"Wrong checkerboard block");
    int solid=ElementArenaLayout.pillarTop(x,z)+(key.equals(ElementArenas.WIND)?ElementArenaLayout.rise(x,z):0);
    h.assertTrue(level.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)==solid+1,"Solid heightmap mismatch");
    h.assertTrue(level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)==(key.equals(ElementArenas.WATER)?128:solid+1),"Surface heightmap mismatch");
    h.assertTrue(level.getBlockState(new net.minecraft.core.BlockPos(x,solid,z)).equals(gen.material(x,solid,z)),"Column top missing");
   }
   h.assertTrue(!level.getBlockState(new net.minecraft.core.BlockPos(8,33,8)).getFluidState().isEmpty()==key.equals(ElementArenas.WATER),"Spawn is not underwater / wind spawn is flooded");
   var shades=new java.util.HashSet<Integer>();for(int x=-128;x<=128;x++)for(int z=-128;z<=128;z++){shades.add(ElementArenaLayout.shade(x,31,z));if(ElementArenaLayout.pillarSolid(x,50,z))shades.add(ElementArenaLayout.shade(x,50,z));}
   for(int y=32;y<100;y++)if(ElementArenaLayout.pillarSolid(120,y,122))shades.add(ElementArenaLayout.shade(120,y,122));
   h.assertTrue(shades.size()==8,"Architecture omits a material role");
   if(key.equals(ElementArenas.WIND)){
    h.assertTrue(ElementArenaLayout.WIND_STAGE_SIZE>2*256,"Boss stage is smaller than the full detection diameter");
    h.assertTrue(level.getHeight(Heightmap.Types.OCEAN_FLOOR,8,8)==40,"Wind entry is not on raised stage");
    h.assertTrue(ElementArenaLayout.rise(500,0)==0&&ElementArenaLayout.rise(385,0)==7,"Platform does not connect back to flat terrain");
   }
  }h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void inkArrivalStandsOnGroundInAFreshChunk(GameTestHelper h){
  var ink=h.getLevel().getServer().getLevel(com.tnc.tnc.world.ink.InkRealmTravel.INK_REALM);h.assertTrue(ink!=null,"Ink realm missing");
  var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(ink);
  h.assertTrue(com.tnc.tnc.world.ink.InkRealmTravel.teleport(player,ink,5000.2,-5000.2),"Ink travel rejected safe ground");
  int height=ink.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,player.blockPosition().getX(),player.blockPosition().getZ());
  h.assertTrue(Math.abs(player.getY()-height)<.0001,"Ink arrival floats above terrain");
  h.assertTrue(ink.noCollision(player,player.getBoundingBox()),"Ink arrival intersects terrain");h.succeed();
 }
}
