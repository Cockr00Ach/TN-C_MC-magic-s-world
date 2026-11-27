package com.tnc.tnc.life.routes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.registries.ForgeRegistries;

public final class RouteWildSpecimens {
    private static final BooleanProperty WILD=BooleanProperty.create("wild");
    static BlockState specimenState(Block block,String id){
        BlockState state=block.defaultBlockState();
        // Special specimens do not share RoutePlantBlock's state definition.
        if(state.hasProperty(WILD))state=state.setValue(WILD,true);
        var bloom=com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING;
        var age=com.tnc.tnc.life.WarningMoss.MossBlock.AGE;
        if(id.equals("homeward_flower")&&state.hasProperty(bloom))state=state.setValue(bloom,true);
        if(id.equals("warning_moss")&&state.hasProperty(age))state=state.setValue(age,3);
        return state;
    }
    static boolean placeSpecimen(ServerLevel level,BlockPos pos,String id){
        var block=ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("tnc",id));
        if(block==null||block==Blocks.AIR)return false;
        BlockState state=specimenState(block,id);
        var facing=com.tnc.tnc.life.WarningMoss.MossBlock.FACING;
        if(id.equals("warning_moss")&&state.hasProperty(facing)){
            for(var direction:Direction.Plane.HORIZONTAL){
                var candidate=state.setValue(facing,direction);
                if(candidate.canSurvive(level,pos))return level.setBlock(pos,candidate,3);
            }
            return false;
        }
        return state.canSurvive(level,pos)&&level.setBlock(pos,state,3);
    }
}
