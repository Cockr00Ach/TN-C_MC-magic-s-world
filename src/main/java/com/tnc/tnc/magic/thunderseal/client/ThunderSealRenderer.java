package com.tnc.tnc.magic.thunderseal.client;

import com.tnc.tnc.magic.thunderseal.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ThunderSealRenderer extends EntityRenderer<ThunderSealEntity> {
    private static final ResourceLocation MODEL=ThunderSealContent.id("thunder_seal_pylon");
    private static final ResourceLocation GLOW=ResourceLocation.fromNamespaceAndPath("minecraft","textures/entity/beacon_beam.png");
    private static final int[][] COLORS={{255,215,85,255,248,184},{55,181,255,184,242,255},{153,78,255,226,188,255}};

    public ThunderSealRenderer(EntityRendererProvider.Context context){super(context);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ThunderSealContent.SEAL,ThunderSealRenderer::new);}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){e.register(new net.minecraft.client.resources.model.ModelResourceLocation(MODEL,"inventory"));}
    public ResourceLocation getTextureLocation(ThunderSealEntity e){return TextureAtlas.LOCATION_BLOCKS;}

    public void render(ThunderSealEntity e,float yaw,float partial,PoseStack p,MultiBufferSource buffers,int light){
        double age=e.age(partial);int tier=e.tier(),finish=ThunderSealRules.finish(tier);
        float fade=(float)Math.min(1,Math.max(0,(ThunderSealRules.lifetime(tier)-age)/15));
        double radius=ThunderSealRules.radius(tier),growth=Math.min(1,age/18),height=ThunderSealRules.height(tier),impact=Math.max(0,age-finish);
        ring(p,buffers,radius,.045,age*.12,fade*.9F);
        var mc=Minecraft.getInstance();
        var model=mc.getModelManager().getModel(new net.minecraft.client.resources.model.ModelResourceLocation(MODEL,"inventory"));
        int count=ThunderSealRules.count(tier);
        boolean pulse=age>=ThunderSealRules.charge(tier)&&age<finish&&((int)(age-ThunderSealRules.charge(tier))%12)<5;
        if(age>12&&age<finish+10){
            float strength=fade*(float)Math.min(1,(age-12)/8)* (pulse?.94F:.62F);
            strength*= (float)Math.max(0,1-Math.max(0,age-finish)/10);
            cage(e,p,buffers,radius,height*growth,age,tier,strength,pulse);
        }
        if(tier>=4)ring(p,buffers,radius*.92,height*1.28, -age*.07,fade*.35F);
        for(int slot=0;slot<count;slot++){
            Vec3 node=e.node(slot,age);
            p.pushPose();p.translate(node.x,node.y-(1-growth)*height,node.z);
            p.mulPose(Axis.YP.rotationDegrees((float)(slot*360D/count)));
            float scale=(float)(height/1.75);p.scale(scale,scale,scale);p.translate(-.25,0,-.25);
            mc.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p,buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS)));p.popPose();
            Vec3 tip=node.add(0,height*.92,0);
            if(pulse)bolt(e,p,buffers,tip,new Vec3(0,.6,0),age,slot+20,fade*.9F,.10);
            if(age>=finish-3&&age<finish+14){
                Vec3 from=tier==5?node.add(0,height,0):new Vec3(node.x,24,node.z);
                bolt(e,p,buffers,from,new Vec3(0,.2,0),age,slot+80,fade*(float)Math.max(0,1-impact/15),tier==5?.23:.12);
            }
        }
        if(age>=finish&&age<finish+18){
            ring(p,buffers,radius*(1+impact*.08),.1,0,fade*(float)(1-impact/18));
            if(tier==5)bolt(e,p,buffers,new Vec3(0,38,0),new Vec3(0,.1,0),age,300,fade*(float)(1-impact/18),.6);
        }
    }

    // Fixed topology: at most 18 bars, three belts, and nine roof ribs per field.
    // These are client mesh strips, never extra entities or server particle packets.
    private static void cage(ThunderSealEntity e,PoseStack p,MultiBufferSource b,double r,double h,double age,int tier,float alpha,boolean pulse){
        int nodes=ThunderSealRules.count(tier);
        int sections=Math.max(2,(8+nodes-1)/nodes)*nodes;
        double width=pulse?.065:.042;
        for(int i=0;i<sections;i++){
            double a=2*Math.PI*i/sections-Math.PI/2,next=a+2*Math.PI/sections;
            Vec3 foot=radial(r,a,.16),top=radial(r,a,h*.97);
            bolt(e,p,b,foot,top,age,100+i,alpha,width);
            for(int belt=0;belt<3;belt++){
                double y=h*(.27+belt*.34);
                bolt(e,p,b,radial(r,a,y),radial(r,next,y),age,140+i+belt*23,alpha*(belt==1?.72F:.9F),width);
            }
            if(i%2==0){
                Vec3 bend=radial(r*.58,a,h*1.23),crown=new Vec3(0,h*1.33,0);
                bolt(e,p,b,top,bend,age,220+i,alpha*.8F,width);
                bolt(e,p,b,bend,crown,age,241+i,alpha*.8F,width);
            }
        }
    }
    private static Vec3 radial(double r,double a,double y){return new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);}

    private static void ring(PoseStack p,MultiBufferSource b,double r,double y,double angle,float alpha){
        p.pushPose();p.translate(0,y,0);p.mulPose(Axis.YP.rotationDegrees((float)angle));
        ThunderSealSigil.render(p,b,(float)r,alpha);p.popPose();
    }
    private static void bolt(ThunderSealEntity e,PoseStack p,MultiBufferSource b,Vec3 from,Vec3 to,double age,int seed,float alpha,double width){
        Vec3 line=to.subtract(from),axis=line.normalize();
        Vec3 camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.position()).subtract(from.add(to).scale(.5));
        Vec3 side=axis.cross(camera).normalize();if(side.lengthSqr()<.01)side=new Vec3(1,0,0);Vec3 up=axis.cross(side).normalize();
        int pieces=Math.min(24,Math.max(4,(int)(line.length()*1.8)));var out=b.getBuffer(RenderType.entityTranslucentEmissive(GLOW));
        int[] color=COLORS[Math.floorMod(seed,3)];
        for(int layer=0;layer<2;layer++)for(int i=0;i<pieces;i++){
            Vec3 a=point(from,line,side,up,i/(double)pieces,age,seed),z=point(from,line,side,up,(i+1D)/pieces,age,seed);
            Vec3 edge=side.scale(width*(layer==0?2.1:.48));float opacity=alpha*(layer==0?.36F:1);
            int offset=layer*3,red=color[offset],green=color[offset+1],blue=color[offset+2];
            vertex(out,p.last(),a.subtract(edge),0,0,opacity,red,green,blue);vertex(out,p.last(),z.subtract(edge),1,0,opacity,red,green,blue);
            vertex(out,p.last(),z.add(edge),1,1,opacity,red,green,blue);vertex(out,p.last(),a.add(edge),0,1,opacity,red,green,blue);
        }
    }
    private static Vec3 point(Vec3 from,Vec3 line,Vec3 side,Vec3 up,double t,double age,int seed){
        double envelope=Math.sin(t*Math.PI)*.23,phase=age*.95;
        return from.add(line.scale(t)).add(side.scale(Math.sin(t*87+phase+seed*4.1)*envelope)).add(up.scale(Math.cos(t*71-phase*.7+seed)*envelope*.6));
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose m,Vec3 point,float u,float w,float a,int r,int g,int b){
        v.vertex(m.pose(),(float)point.x,(float)point.y,(float)point.z).color(r,g,b,(int)(Math.max(0,Math.min(1,a))*255)).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(m.normal(),0,1,0).endVertex();
    }
}
