package com.tnc.tnc.world.skyhaven;

import java.util.*;

/** Sparse world-anchored storms; their spacing is not re-selected from the old dense gyre grid. */
public final class SeaTornadoLayout {
    private SeaTornadoLayout(){}
    public static List<SeaGyreLayout.Gyre> near(double x,double z){
        int cx=(int)Math.floor(x/192),cz=(int)Math.floor(z/192);
        var out=new ArrayList<SeaGyreLayout.Gyre>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            int gx=cx+dx,gz=cz+dz;
            var random=new Random(((long)gx*341873128712L)^((long)gz*132897987541L)^0x53544f524dL);
            double sx=gx*192+96+(random.nextDouble()-.5)*16,sz=gz*192+96+(random.nextDouble()-.5)*16;
            if(Math.hypot(sx-x,sz-z)>210)continue;
            out.add(new SeaGyreLayout.Gyre(sx,sz,24+random.nextDouble()*28,random.nextDouble()*Math.PI*2,random.nextBoolean()?1:-1));
        }
        out.sort(Comparator.comparingDouble(g->Math.hypot(g.x()-x,g.z()-z)));
        return List.copyOf(out);
    }
}
