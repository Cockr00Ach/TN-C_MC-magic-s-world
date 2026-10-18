package com.tnc.tnc.magic.water;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BoreSliceGeometryTest {
    @Test void restrictedSlabMatchesFullCubeForObliqueAndBoundaryDirections() {
        Vec3 start=new Vec3(-4.5,11.001,3.999);
        for(Vec3 raw:new Vec3[]{new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1),
                new Vec3(-1,.1,.3),new Vec3(1,1,1),new Vec3(.00001,-1,1),new Vec3(-.4,-.7,-1)}) {
            Vec3 d=raw.normalize();
            for(double r:new double[]{.8,3,15})for(int k:new int[]{0,1,12,95}) {
                Vec3 a=start.add(d.scale(k)),b=start.add(d.scale(k+1));
                BlockPos min=BlockPos.containing(Math.min(a.x,b.x)-r-1,Math.min(a.y,b.y)-r-1,Math.min(a.z,b.z)-r-1);
                BlockPos max=BlockPos.containing(Math.max(a.x,b.x)+r+1,Math.max(a.y,b.y)+r+1,Math.max(a.z,b.z)+r+1);
                double extent=.5*(Math.abs(d.x)+Math.abs(d.y)+Math.abs(d.z));
                Set<Long> full=new HashSet<>(),slab=new HashSet<>();
                int visited=0,total=0;
                for(BlockPos p:BlockPos.betweenClosed(min,max)) {
                    total++;if(inGuard(p,start,d,k,r,extent))full.add(p.asLong());
                }
                for(BlockPos p:BoreSliceGeometry.candidates(start,d,min,max,k,k+1,extent)) {
                    visited++;if(inGuard(p,start,d,k,r,extent))assertTrue(slab.add(p.asLong()),"No duplicate voxel");
                }
                assertEquals(full,slab,"Same protected volume for direction "+d+", radius "+r+", slice "+k);
                if(r==15)assertTrue(visited<total*.30,"Large slab visits less than 30% of cube: "+visited+"/"+total);
            }
        }
    }
    private static boolean inGuard(BlockPos p,Vec3 start,Vec3 d,int k,double r,double extent) {
        double x=p.getX()+.5-start.x,y=p.getY()+.5-start.y,z=p.getZ()+.5-start.z;
        double along=x*d.x+y*d.y+z*d.z;
        if(along+extent<k||along-extent>=k+1)return false;
        x-=d.x*along;y-=d.y*along;z-=d.z*along;
        double guard=r+Math.sqrt(3)/2;
        return x*x+y*y+z*z<=guard*guard;
    }
}
