package com.tnc.tnc.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Sample terrain once per footprint change, never once per frame or vertex. */
final class WindEarthGroundSurface implements WindEarthGeometry.Ground {
    private final double[] heights=new double[81];
    final BlockPos origin;
    private final double radius;
    WindEarthGroundSurface(Level level,Vec3 centre,double radius) {
        this.radius=radius;origin=BlockPos.containing(centre);
        for(int ix=0;ix<9;ix++)for(int iz=0;iz<9;iz++) {
            int x=(int)Math.floor(centre.x-radius+ix*radius/4),z=(int)Math.floor(centre.z-radius+iz*radius/4);
            for(int y=origin.getY()+1;y>=origin.getY()-6;y--) {
                BlockPos pos=new BlockPos(x,y,z);
                var state=level.getBlockState(pos);
                if(state.is(net.minecraft.tags.BlockTags.LEAVES))continue;
                var shape=state.getCollisionShape(level,pos);
                if(!shape.isEmpty()) {
                    heights[ix*9+iz]=y+shape.max(net.minecraft.core.Direction.Axis.Y)-centre.y;
                    break;
                }
            }
        }
    }
    public double y(double x,double z) {
        double gx=Math.max(0,Math.min(7.999,(x+radius)*4/radius));
        double gz=Math.max(0,Math.min(7.999,(z+radius)*4/radius));
        int ix=(int)gx,iz=(int)gz;double fx=gx-ix,fz=gz-iz;
        return heights[ix*9+iz]*(1-fx)*(1-fz)+heights[(ix+1)*9+iz]*fx*(1-fz)
                +heights[ix*9+iz+1]*(1-fx)*fz+heights[(ix+1)*9+iz+1]*fx*fz;
    }
}
