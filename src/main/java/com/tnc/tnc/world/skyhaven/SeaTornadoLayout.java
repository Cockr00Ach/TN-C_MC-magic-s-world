package com.tnc.tnc.world.skyhaven;

import java.util.*;

/** Dense world-anchored storm banks; camera motion never moves a storm's root. */
public final class SeaTornadoLayout {
    public static final int SPACING=128;
    private SeaTornadoLayout(){}
    public static List<SeaGyreLayout.Gyre> near(double x,double z){
        int cx=(int)Math.floor(x/SPACING),cz=(int)Math.floor(z/SPACING);
        var out=new ArrayList<SeaGyreLayout.Gyre>();
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
            int gx=cx+dx,gz=cz+dz;
            var random=new Random(((long)gx*341873128712L)^((long)gz*132897987541L)^0x53544f524dL);
            double sx=gx*SPACING+SPACING*.5+(random.nextDouble()-.5)*12,sz=gz*SPACING+SPACING*.5+(random.nextDouble()-.5)*12;
            if(Math.hypot(sx-x,sz-z)>SeaTornadoGeometry.VIEW_DISTANCE)continue;
            double radius=random.nextDouble()<.22?30+random.nextDouble()*8:20+random.nextDouble()*10;
            out.add(new SeaGyreLayout.Gyre(sx,sz,radius,random.nextDouble()*Math.PI*2,random.nextBoolean()?1:-1));
        }
        out.sort(Comparator.comparingDouble(g->Math.hypot(g.x()-x,g.z()-z)));
        return List.copyOf(out);
    }
}
