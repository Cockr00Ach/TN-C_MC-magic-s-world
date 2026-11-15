package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;

/** Collision-checked three-dimensional approach followed by a true through-target thrust. */
public final class WarriorSwordDash {
    public record Plan(List<Vec3> approach,Vec3 strikeFrom,Vec3 strikeTo,Vec3 direction){}
    private static final double STEP=.18;
    private WarriorSwordDash(){}
    public static int tickFor(int index){return 26+Math.round(index*14f/3);}
    public static double yawFor(double firstYaw,int index){return firstYaw+index*40;}
    public static boolean clear(Vec3 a,Vec3 b,Predicate<Vec3> free){
        double distance=a.distanceTo(b);int steps=Math.max(1,(int)Math.ceil(distance/STEP));
        for(int i=0;i<=steps;i++)if(!free.test(a.lerp(b,i/(double)steps)))return false;
        return true;
    }
    public static Plan plan(Vec3 from,Vec3 anchor,double firstYaw,int index,Predicate<Vec3> free){
        if(!Double.isFinite(from.lengthSqr()+anchor.lengthSqr()+firstYaw)||from.distanceTo(anchor)>40)return null;
        double yaw=Math.toRadians(yawFor(firstYaw,index));Vec3 direction=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        double desired=Math.min(20,Math.max(8,2*(from.distanceTo(anchor)+2)));
        double distance=from.subtract(anchor).horizontalDistance();
        double radius=Math.min(4.5,Math.max(1.25,distance));
        for(double entryRadius:new double[]{radius,2,1.1}){
            Vec3 entry=anchor.subtract(direction.scale(entryRadius)).add(0,.025,0);
            if(!free.test(entry))continue;
            Vec3 end=entry;
            for(double d=STEP;d<=desired+STEP;d+=STEP){Vec3 next=entry.add(direction.scale(Math.min(d,desired)));if(!free.test(next))break;end=next;}
            if(end.subtract(entry).dot(direction)<entryRadius+.2)continue;
            List<Vec3> route=route(from,entry,anchor,free);
            if(route!=null)return new Plan(List.copyOf(route),entry,end,direction);
        }
        return null;
    }
    private static List<Vec3> route(Vec3 from,Vec3 entry,Vec3 center,Predicate<Vec3> free){
        Vec3 a=from.subtract(center),b=entry.subtract(center);double angleA=Math.atan2(a.z,a.x),angleB=Math.atan2(b.z,b.x);
        double shortest=Math.atan2(Math.sin(angleB-angleA),Math.cos(angleB-angleA));
        // Prefer the shorter visible orbit, then the other side if a wall occupies it.
        for(double turn:new double[]{shortest,shortest+(shortest>=0?-Math.PI*2:Math.PI*2)}){
            int steps=Math.max(1,(int)Math.ceil(Math.abs(turn)/Math.toRadians(20)));
            for(double lift:new double[]{0,.75,1.5,3,5,7}){
                List<Vec3> points=new ArrayList<>();points.add(from);
                double ceiling=Math.max(from.y,entry.y)+lift;
                if(lift>0)points.add(new Vec3(from.x,ceiling,from.z));
                for(int i=1;i<=steps;i++){
                    double t=i/(double)steps,angle=angleA+turn*t;
                    double radius=a.horizontalDistance()*(1-t)+b.horizontalDistance()*t;
                    double y=lift>0?ceiling:from.y+(entry.y-from.y)*t;
                    points.add(new Vec3(center.x+Math.cos(angle)*radius,y,center.z+Math.sin(angle)*radius));
                }
                if(points.get(points.size()-1).distanceToSqr(entry)>.00001)points.add(entry);else points.set(points.size()-1,entry);
                boolean valid=true;double travel=0;
                for(int i=1;i<points.size();i++){travel+=points.get(i).distanceTo(points.get(i-1));if(travel>64||!clear(points.get(i-1),points.get(i),free)){valid=false;break;}}
                if(valid)return points;
            }
        }
        return null;
    }
}
