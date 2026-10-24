package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.dungeon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/** Authored passage silhouettes; the three-wide, four-high walking envelope stays empty. */
public final class LeylineCorridors {
    public static void decorate(List<DungeonRealm.Cell> cells, DungeonData.Run run, DungeonGraph.Room a, DungeonGraph.Room b) {
        var origin=DungeonRealm.center(run,a);var end=DungeonRealm.center(run,b);
        int dx=Integer.signum(end.getX()-origin.getX()), dz=Integer.signum(end.getZ()-origin.getZ());
        int start=a.kind()==DungeonGraph.Kind.GUARDIAN?25:12, stop=b.kind()==DungeonGraph.Kind.GUARDIAN?15:28;
        int style=switch(run.theme){case CRYPT->0;case ASTRAL->1;case DESERT->2;case TIDE->3;};
        for(int step=start;step<=stop;step++) {
            var c=origin.offset(dx*step,0,dz*step);
            // Worn centreline, tiled margins and recessed lanterns guide ordinary-view navigation.
            put(cells,c,step%4==0?run.theme.trim():run.theme.floor());
            for(int sign:new int[]{-1,1}) {
                var side=c.offset(dz*sign*2,0,dx*sign*2);
                boolean arch=(step-start)%4==0;
                if(arch) {
                    for(int y=1;y<=5;y++)put(cells,side.above(y),run.theme.trim());
                    put(cells,side.above(2),run.theme.lamp());
                    for(int cross=-1;cross<=1;cross++)put(cells,c.offset(dz*cross,5,dx*cross),run.theme.trim());
                } else {
                    for(int y=2;y<=4;y++)put(cells,side.above(y),switch(style){
                        case 0 -> Blocks.IRON_BARS.defaultBlockState();
                        case 1 -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
                        case 2 -> y==4?Blocks.CHISELED_SANDSTONE.defaultBlockState():Blocks.OAK_FENCE.defaultBlockState();
                        default -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
                    });
                    if(style==1)put(cells,side.offset(dz*sign,1,dx*sign),Blocks.AMETHYST_BLOCK.defaultBlockState());
                    if(style==2)put(cells,side.above(5),Blocks.RED_SANDSTONE.defaultBlockState());
                    if(style==3){put(cells,side.offset(dz*sign,2,dx*sign),Blocks.PRISMARINE.defaultBlockState());put(cells,side.offset(dz*sign,3,dx*sign),Blocks.SEA_LANTERN.defaultBlockState());}
                }
            }
        }
        // Both adjacent rooms expose their own half of the artery, including in the corridor.
        append(run,a,origin.offset(dx*11,1,dz*11),origin.offset(dx*20,1,dz*20));
        append(run,b,end.offset(-dx*11,1,-dz*11),end.offset(-dx*20,1,-dz*20));
    }
    private static void append(DungeonData.Run run,DungeonGraph.Room room,BlockPos from,BlockPos to){
        var s=LeylineService.state(run,room);var nodes=s.getList("Nodes",Tag.TAG_COMPOUND);
        for(var p:List.of(from,to)){var n=new CompoundTag();n.putString("Role","trail");n.putInt("Index",nodes.size());n.putLong("Pos",p.asLong());nodes.add(n);}s.put("Nodes",nodes);
    }
    private static void put(List<DungeonRealm.Cell> cells,BlockPos pos,BlockState state){cells.add(new DungeonRealm.Cell(pos,state));}
    private LeylineCorridors(){}
}
