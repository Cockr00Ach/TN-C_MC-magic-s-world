package com.tnc.tnc.warrior;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Sparse translucent blade silhouettes, feathered in depth rather than opaque light sheets. */
public final class WarriorBladeEcho {
    public static void emit(List<WarriorTrailVolume.Point> points,double now,double life,double scale,int color,WarriorTrailVolume.Sink sink){
        if(points.size()<4)return;
        for(int j=1;j<=3;j++){
            int i=Math.max(0,points.size()-1-j*points.size()/4);var p=points.get(i);
            double fade=Math.max(0,1-(now-p.at())/life);if(fade<=0)continue;
            Vec3 blade=p.tip().subtract(p.hilt()).scale(scale),tip=p.hilt().add(blade);
            Vec3 side=blade.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-6)side=blade.cross(new Vec3(1,0,0));
            side=side.normalize();Vec3 root=p.hilt().add(blade.scale(.3)),shoulder=root.lerp(tip,.55);
            for(int layer=2;layer>=0;layer--){
                double width=(.035+layer*.045)*Math.min(2,scale);
                float alpha=(float)(fade*fade*(.13/(j+1))/(layer+1));
                sink.quad(root,shoulder.add(side.scale(width)),tip,shoulder.subtract(side.scale(width)),color,0,alpha,alpha*.45f,alpha);
            }
        }
    }
}
