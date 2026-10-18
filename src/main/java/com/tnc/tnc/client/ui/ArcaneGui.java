package com.tnc.tnc.client.ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
/** Shared dark grimoire skin. Original slots, services and network actions stay independent. */
public final class ArcaneGui {
 public static final int TEXT=0xffe6daca,DIM=0xffb5aa98,GOLD=0xffb79a6b,CYAN=0xff91d6d5;
 private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath("tnc","textures/gui/arcane_grimoire.png");
 private static final int SIZE=1254;
 private ArcaneGui(){}
 public static void frame(GuiGraphics g,int x,int y,int w,int h){
  g.fill(x-2,y-2,x+w+2,y+h+2,0xe6000000);
  int d=Math.min(16,Math.min(w,h)/4),s=SIZE/6;int[] ds={d,w-2*d,d},hs={d,h-2*d,d},ss={s,SIZE-2*s,s};int yy=y,v=0;
  for(int row=0;row<3;row++){int xx=x,u=0;for(int col=0;col<3;col++){g.blit(FRAME,xx,yy,ds[col],hs[row],(float)u,(float)v,ss[col],ss[row],SIZE,SIZE);xx+=ds[col];u+=ss[col];}yy+=hs[row];v+=ss[row];}
 }
 /** Tile straight rails and uniformly scale ornaments; never stretch a round crest into an ellipse. */
 public static void proportionalFrame(GuiGraphics g,int x,int y,int w,int h){
  g.fill(x-2,y-2,x+w+2,y+h+2,0xe6000000);
  g.blit(FRAME,x+8,y+8,w-16,h-16,240f,240f,774,774,SIZE,SIZE);
  for(int dx=24;dx<w-24;dx+=20){
   int n=Math.min(20,w-24-dx),sw=n*8;
   g.blit(FRAME,x+dx,y,n,12,330f,0f,sw,96,SIZE,SIZE);
   g.blit(FRAME,x+dx,y+h-12,n,12,330f,SIZE-96f,sw,96,SIZE,SIZE);
  }
  for(int dy=24;dy<h-24;dy+=20){
   int n=Math.min(20,h-24-dy),sh=n*8;
   g.blit(FRAME,x,y+dy,12,n,0f,270f,96,sh,SIZE,SIZE);
   g.blit(FRAME,x+w-12,y+dy,12,n,SIZE-96f,270f,96,sh,SIZE,SIZE);
  }
  for(int cx=0;cx<2;cx++)for(int cy=0;cy<2;cy++)
   g.blit(FRAME,x+cx*(w-24),y+cy*(h-24),24,24,cx*(SIZE-209f),cy*(SIZE-209f),209,209,SIZE,SIZE);
  // Source crops retain their original aspect ratio, including the central moon ornament.
  g.blit(FRAME,x+(w-30)/2,y,30,25,515f,0f,225,184,SIZE,SIZE);
  g.blit(FRAME,x+(w-14)/2,y+h-13,14,13,570f,1150f,114,104,SIZE,SIZE);
 }
 public static void slot(GuiGraphics g,int x,int y){g.fill(x-2,y-2,x+18,y+18,0xff302b24);g.fill(x-1,y-1,x+17,y+17,0xff8e7653);g.fill(x,y,x+16,y+16,0xff14191f);for(int dx:new int[]{-2,15})for(int dy:new int[]{-2,15})g.fill(x+dx,y+dy,x+dx+3,y+dy+3,0xffc1a677);}
 public static void line(GuiGraphics g,double ax,double ay,double bx,double by,double width,int color){double dx=bx-ax,dy=by-ay,len=Math.hypot(dx,dy);if(len<.0001)return;double sx=-dy/len*width*.5,sy=dx/len*width*.5;var v=g.bufferSource().getBuffer(RenderType.gui());var m=g.pose().last().pose();point(v,m,bx+sx,by+sy,color);point(v,m,bx-sx,by-sy,color);point(v,m,ax-sx,ay-sy,color);point(v,m,ax+sx,ay+sy,color);}
 private static void point(com.mojang.blaze3d.vertex.VertexConsumer v,org.joml.Matrix4f m,double x,double y,int c){v.vertex(m,(float)x,(float)y,0).color(c>>16&255,c>>8&255,c&255,c>>>24).endVertex();}
 public static void ring(GuiGraphics g,double x,double y,double rx,double ry,double angle,int color){double c=Math.cos(angle),s=Math.sin(angle);for(int i=0;i<64;i++){double a=i*Math.PI/32,b=(i+1)*Math.PI/32;double ax=Math.cos(a)*rx,ay=Math.sin(a)*ry,bx=Math.cos(b)*rx,by=Math.sin(b)*ry;line(g,x+ax*c-ay*s,y+ax*s+ay*c,x+bx*c-by*s,y+bx*s+by*c,.7,color);}}
 public static void sigil(GuiGraphics g,int x,int y,int radius,double time,float strength){
  ring(g,x,y,radius,radius,0,0x706b624e);ring(g,x,y,radius-3,radius-3,0,0xa49c8057);
  ring(g,x,y,radius*.92,radius*.33,time*.006,0xb7baa47f);ring(g,x,y,radius*.83,radius*.30,-time*.008+.8,0xa973ccdd);
  for(int i=0;i<8;i++){double a=i*Math.PI/4+time*.001;line(g,x+Math.cos(a)*(radius-1),y+Math.sin(a)*(radius-1),x+Math.cos(a)*(radius+2),y+Math.sin(a)*(radius+2),1,GOLD);}
  int h=Math.max(7,radius/2),w=Math.max(3,radius/5);double pulse=.6+.4*Math.sin(time*.04);int glow=(int)(70+50*pulse+50*strength);
  line(g,x,y-h-2,x,y+h+2,w*3,(glow<<24)|0x5faad9);var v=g.bufferSource().getBuffer(RenderType.gui());var m=g.pose().last().pose();
  point(v,m,x,y-h,0xffd5ffff);point(v,m,x-w,y,0xff80b7e3);point(v,m,x,y+h,0xff4282bc);point(v,m,x,y,0xffb8ffff);
  point(v,m,x,y-h,0xffb9ffff);point(v,m,x,y,0xff8bc4f3);point(v,m,x,y+h,0xffd9ffff);point(v,m,x+w,y,0xff568ab2);
  line(g,x,y-h,x-w,y,.7,0xffd6ffff);line(g,x-w,y,x,y+h,.7,0xffb8eaf8);line(g,x,y+h,x+w,y,.7,0xffc6f6ff);line(g,x+w,y,x,y-h,.7,0xffd6ffff);
 }
}
