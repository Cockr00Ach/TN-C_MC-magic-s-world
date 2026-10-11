package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
public final class RouteWildSpecimens {
 public static boolean place(ServerLevel l,BlockPos p,String biome){String id=biome.contains("hill")||biome.contains("peak")?"warning_moss":new String[]{"homeward_flower","mana_root","sky_vine"}[l.random.nextInt(3)];var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",id));if(block==null)return false;BlockState state=block.defaultBlockState().setValue(RoutePlantBlock.WILD,true);if(id.equals("homeward_flower"))state=state.setValue(com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING,true);if(id.equals("warning_moss")){state=state.setValue(com.tnc.tnc.life.WarningMoss.MossBlock.AGE,3);for(var d:Direction.Plane.HORIZONTAL){var s=state.setValue(com.tnc.tnc.life.WarningMoss.MossBlock.FACING,d);if(s.canSurvive(l,p)){l.setBlock(p,s,3);return true;}}return false;}if(!state.canSurvive(l,p))return false;l.setBlock(p,state,3);return true;}
}
