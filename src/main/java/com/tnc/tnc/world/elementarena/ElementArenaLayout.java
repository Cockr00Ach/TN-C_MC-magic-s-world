package com.tnc.tnc.world.elementarena;
/** Architectural chessboard: restrained fields, trim hierarchy and continuous fluted pillars. */
public final class ElementArenaLayout {
 public static final int FLOOR=32,WATER_TOP=128;
 public static final int WIND_STAGE_SIZE=768,WIND_STAGE_RISE=8;
 public static int rise(int x,int z){long distance=Math.max(Math.abs((long)x),Math.abs((long)z));return (int)Math.max(0,Math.min(WIND_STAGE_RISE,WIND_STAGE_SIZE/2L+WIND_STAGE_RISE-distance));}
 public static boolean dark(int x,int z){return (Math.floorDiv(x,8)+Math.floorDiv(z,8)&1)!=0;}
 private static int centre(int n){return Math.floorDiv(n,48)*48+24;}
 public static int capital(int x,int z){return FLOOR+44+Math.floorMod(Math.floorDiv(x,48)+Math.floorDiv(z,48),3)*8;}
 public static int pillarTop(int x,int z){
  int cx=centre(x),cz=centre(z),dx=Math.abs(x-cx),dz=Math.abs(z-cz);
  if((long)cx*cx+(long)cz*cz<72*72||dx>3||dz>3||dx+dz>5)return FLOOR-1;
  return capital(x,z);
 }
 public static boolean pillarSolid(int x,int y,int z){
  int top=pillarTop(x,z);if(top<FLOOR||y>top)return false;
  int dx=Math.abs(x-centre(x)),dz=Math.abs(z-centre(z));
  return y<FLOOR+4||y>=top-3||(dx<=2&&dz<=2&&dx+dz<4);
 }
 public static int shade(int x,int y,int z){
  if(y>=FLOOR){int top=capital(x,z);if(y==FLOOR||y==top)return 8;if(y<FLOOR+4||y>=top-2)return 3;if(y==top-3)return 5;if(y==top-6&&((x==centre(x)&&Math.abs(z-centre(z))==2)||(z==centre(z)&&Math.abs(x-centre(x))==2)))return 6;return 7;}
  boolean blue=Math.floorMod(Math.floorDiv(x,64)+Math.floorDiv(z,64),4)==0;
  return dark(x,z)?(blue?4:2):(blue?3:1);
 }
 private ElementArenaLayout(){}
}
