package com.tnc.tnc.arcana.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import com.tnc.tnc.alchemy.client.AlchemyRenderer;
/** Four-dimensional rotation projected into nested, folding crystal faces. Not an ambient particle cloud. */
public final class DimensionalSpiritGeometry {
 private static final ResourceLocation WHITE=ResourceLocation.parse("tnc:textures/misc/alchemy_white.png");
 public static void render(PoseStack pose,MultiBufferSource buffers,double time){
  var out=new double[16][3];
  for(int i=0;i<16;i++){double[] p={(i&1)==0?-1:1,(i&2)==0?-1:1,(i&4)==0?-1:1,(i&8)==0?-1:1};rotate(p,0,3,time*.012);rotate(p,1,3,time*.008+.8);rotate(p,0,2,time*.006);double project=2.7/(3.8-p[3]);for(int j=0;j<3;j++)out[i][j]=p[j]*project;}
  pose.pushPose();pose.translate(0,1.5+Math.sin(time*.025)*.08,0);pose.scale(1.35f,1.35f,1.35f);var v=buffers.getBuffer(RenderType.eyes(WHITE));
  for(int a=0;a<4;a++)for(int b=a+1;b<4;b++)for(int i=0;i<16;i++)if((i&(1<<a|1<<b))==0){int[] face={i,i|1<<a,i|1<<a|1<<b,i|1<<b};int tone=(a+b)%2==0?0xabeaff:0xefdcff;for(int n:face)point(v,pose,out[n],0x18000000|tone);}
  // Thin luminous bevels frame the translucent 4D sections; their apparent depth changes continuously.
  for(int i=0;i<16;i++)for(int bit=0;bit<4;bit++)if((i&(1<<bit))==0){var a=out[i];var b=out[i|1<<bit];double dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2],n=Math.sqrt(dx*dx+dz*dz);double ox=n<.001?.012:-dz/n*.012,oz=n<.001?0:dx/n*.012;int c=bit==3?0xb5f9ebff:0xaaf0e4b8;point(v,pose,new double[]{a[0]-ox,a[1],a[2]-oz},c);point(v,pose,new double[]{b[0]-ox,b[1],b[2]-oz},c);point(v,pose,new double[]{b[0]+ox,b[1],b[2]+oz},c);point(v,pose,new double[]{a[0]+ox,a[1],a[2]+oz},c);}
  pose.popPose();
 }
 private static void rotate(double[] p,int a,int b,double angle){double x=p[a],y=p[b];p[a]=x*Math.cos(angle)-y*Math.sin(angle);p[b]=x*Math.sin(angle)+y*Math.cos(angle);}
 private static void point(VertexConsumer v,PoseStack p,double[] a,int c){AlchemyRenderer.vertex(v,p,(float)a[0],(float)a[1],(float)a[2],0,0,c);}
 private DimensionalSpiritGeometry(){}
}
