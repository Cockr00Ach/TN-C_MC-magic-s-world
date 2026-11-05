package com.tnc.tnc.world.skyhaven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Bounded slots with hysteresis: retire a distant root before gently admitting a nearer one. */
public final class SeaStormVisibility {
    private List<SeaGyreLayout.Gyre> roots=List.of();
    private final HashMap<SeaGyreLayout.Gyre,Double> appeared=new HashMap<>();
    private SeaGyreLayout.Gyre retiring;
    private double retirement;
    public List<SeaGyreLayout.Gyre> roots(){return roots;}
    public void clear(){roots=List.of();appeared.clear();retiring=null;}

    public List<SeaGyreLayout.Gyre> update(List<SeaGyreLayout.Gyre> eligible,double x,double z,double tick){
        var keep=new ArrayList<SeaGyreLayout.Gyre>();
        for(var root:roots)if(eligible.contains(root))keep.add(root);
        if(retiring!=null&&(!keep.contains(retiring)||tick-retirement>=30)){
            appeared.remove(retiring);
            keep.remove(retiring);retiring=null;
        }
        var candidates=new ArrayList<>(keep);candidates.addAll(eligible);
        roots=SeaTornadoGeometry.select(candidates);
        appeared.keySet().retainAll(roots);
        for(var root:roots)appeared.putIfAbsent(root,tick);
        if(retiring==null&&roots.size()==SeaTornadoGeometry.MAX_VISIBLE){
            SeaGyreLayout.Gyre closestMissing=null,farthest=null;
            double closest=Double.POSITIVE_INFINITY,far=0;
            for(var root:eligible)if(!roots.contains(root)){
                double distance=Math.hypot(root.x()-x,root.z()-z);
                if(distance<closest){closest=distance;closestMissing=root;}
            }
            for(var root:roots){
                double distance=Math.hypot(root.x()-x,root.z()-z);
                if(distance>far){far=distance;farthest=root;}
            }
            if(closestMissing!=null&&closest+56<far){retiring=farthest;retirement=tick;}
        }
        return roots;
    }
    public double opacity(SeaGyreLayout.Gyre root,double tick){
        Double birth=appeared.get(root);if(birth==null)return 0;
        double weight=smooth((tick-birth)/40);
        if(root.equals(retiring))weight*=1-smooth((tick-retirement)/30);
        return weight;
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
