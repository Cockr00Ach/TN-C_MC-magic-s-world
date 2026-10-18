package com.tnc.tnc.mana.sight;
import net.minecraft.world.phys.Vec3;
/** A successful transfer, or an actual source-production pulse; never an energy container. */
public record ManaFlow(Vec3 from,Vec3 to,int amount,int color,int kind){
 public static final int PLAYER=0,PLANT=1,ANIMAL=2,FIBER=3,MIRROR=4,EMISSION=5;
 public boolean valid(){return amount>0&&amount<=65535&&finite(from)&&finite(to)&&from.distanceToSqr(to)<=256*256&&kind>=0&&kind<=5;}
 private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=30000000&&Math.abs(p.z)<=30000000&&Math.abs(p.y)<=4096;}
}
