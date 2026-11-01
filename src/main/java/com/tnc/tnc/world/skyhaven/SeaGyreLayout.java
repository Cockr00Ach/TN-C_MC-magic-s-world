package com.tnc.tnc.world.skyhaven;
import java.util.*;
/** One large gyre occupies roughly sixteen former cells, with irregular stable placement. */
public final class SeaGyreLayout {
 public record Gyre(double x,double z,double radius,double phase,int direction){}
 public static List<Gyre> near(double x,double z){
  int cx=(int)Math.floor(x/96),cz=(int)Math.floor(z/96);var out=new ArrayList<Gyre>();
  for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
   long seed=((long)(cx+dx)*341873128712L)^((long)(cz+dz)*132897987541L)^0x534541L;
   var random=new Random(seed);double gx=(cx+dx)*96+48+(random.nextDouble()-.5)*28,gz=(cz+dz)*96+48+(random.nextDouble()-.5)*28;
   if(Math.hypot(gx-x,gz-z)>210)continue;
   out.add(new Gyre(gx,gz,34+random.nextDouble()*11,random.nextDouble()*Math.PI*2,random.nextBoolean()?1:-1));
  }
  out.sort(Comparator.comparingDouble(g->Math.hypot(g.x-x,g.z-z)));
  return List.copyOf(out.subList(0,Math.min(9,out.size())));
 }
 private SeaGyreLayout(){}
}
