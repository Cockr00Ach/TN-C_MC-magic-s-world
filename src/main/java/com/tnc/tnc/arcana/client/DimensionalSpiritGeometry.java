package com.tnc.tnc.arcana.client;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.tnc.tnc.alchemy.client.AlchemyRenderer;
/** Hollow 4D frame with traveling geometric energy packets; no additive solid faces. */
public final class DimensionalSpiritGeometry {
 private static final ResourceLocation WHITE=ResourceLocation.parse("tnc:textures/misc/alchemy_white.png");
 public static void render(PoseStack pose,MultiBufferSource buffers,double time){
  Vec3[] out=new Vec3[16];
  for(int i=0;i<16;i++){double[] p={(i&1)==0?-1:1,(i&2)==0?-1:1,(i&4)==0?-1:1,(i&8)==0?-1:1};rotate(p,0,3,time*.009);rotate(p,1,3,time*.006+.8);rotate(p,0,2,time*.004);double project=2.7/(3.8-p[3]);out[i]=new Vec3(p[0]*project,p[1]*project,p[2]*project);}
  pose.pushPose();pose.translate(0,1.5+Math.sin(time*.025)*.08,0);pose.scale(1.35f,1.35f,1.35f);
  var v=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
  for(int i=0;i<16;i++)for(int bit=0;bit<4;bit++)if((i&(1<<bit))==0){var a=out[i];var b=out[i|1<<bit];int tone=bit==3?0xe6beff:bit==2?0xffd893:0x7fe8e2;
   beam(v,pose,a,b,.005,0x80000000|tone);
   for(int j=0;j<3;j++){double t=(time*.012+i*.173+j/3.)%1;beam(v,pose,a.lerp(b,t),a.lerp(b,Math.min(1,t+.075)),.022,0xe0000000|tone);}
  }
  // Disconnected, counter-rotating gate sectors fold through the frame instead of filling it.
  for(int ring=0;ring<3;ring++)for(int i=0;i<64;i++){if(i%16>10)continue;double a=i*Math.PI/32+time*.009*(ring%2==0?1:-1),b=a+.035,r=1.25+ring*.12;Vec3 p=new Vec3(Math.cos(a)*r,Math.sin(a)*r,Math.sin(a*2+time*.015)*.18),q=new Vec3(Math.cos(b)*r,Math.sin(b)*r,Math.sin(b*2+time*.015)*.18);if(ring==1){p=new Vec3(p.x,p.z,p.y);q=new Vec3(q.x,q.z,q.y);}beam(v,pose,p,q,.012,0xb0bce9ff);}
  pose.pushPose();pose.mulPose(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
  // An open almond-shaped eye is the stable identity inside the impossible geometry.
  for(int i=0;i<32;i++){double a=i*Math.PI*2/32,b=(i+1)*Math.PI*2/32;beam(v,pose,new Vec3(Math.cos(a)*.33,Math.sin(a)*.13,0),new Vec3(Math.cos(b)*.33,Math.sin(b)*.13,0),.012,0xe0ffe3a3);}
  beam(v,pose,new Vec3(0,-.09,0),new Vec3(0,.09,0),.024,0xffb0ffff);pose.popPose();pose.popPose();
 }
 private static void beam(VertexConsumer v,PoseStack p,Vec3 a,Vec3 b,double width,int color){Vec3 axis=b.subtract(a).normalize(),u=axis.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize().scale(width);Vec3 w=axis.cross(u).normalize().scale(width);for(Vec3 side:new Vec3[]{u,w}){point(v,p,a.subtract(side),color);point(v,p,b.subtract(side),color);point(v,p,b.add(side),color);point(v,p,a.add(side),color);}}
 private static void rotate(double[] p,int a,int b,double angle){double x=p[a],y=p[b];p[a]=x*Math.cos(angle)-y*Math.sin(angle);p[b]=x*Math.sin(angle)+y*Math.cos(angle);}
 private static void point(VertexConsumer v,PoseStack p,Vec3 a,int c){AlchemyRenderer.vertex(v,p,(float)a.x,(float)a.y,(float)a.z,0,0,c);}
 private DimensionalSpiritGeometry(){}
}
