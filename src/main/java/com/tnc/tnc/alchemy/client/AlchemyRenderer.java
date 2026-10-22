package com.tnc.tnc.alchemy.client;
import com.tnc.tnc.alchemy.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
public final class AlchemyRenderer implements BlockEntityRenderer<AlchemyEntity>{
    private static final ResourceLocation WHITE=ResourceLocation.parse("tnc:textures/misc/alchemy_white.png");
    private final java.util.Map<net.minecraft.core.BlockPos,Long> particles=new java.util.HashMap<>();
    public AlchemyRenderer(BlockEntityRendererProvider.Context c){}
    public static void ring(PoseStack p,MultiBufferSource b,double time,int color,float y,float radius){
        var v=b.getBuffer(RenderType.entityTranslucent(WHITE));
        for(int i=0;i<48;i++){
            double a=i*Math.PI/24+time*.02,c=(i+1)*Math.PI/24+time*.02;float r=radius,s=radius-.012f;
            vertex(v,p,.5f+(float)Math.cos(a)*r,y,.5f+(float)Math.sin(a)*r,0,0,color);
            vertex(v,p,.5f+(float)Math.cos(a)*s,y,.5f+(float)Math.sin(a)*s,0,1,color);
            vertex(v,p,.5f+(float)Math.cos(c)*s,y,.5f+(float)Math.sin(c)*s,1,1,color);
            vertex(v,p,.5f+(float)Math.cos(c)*r,y,.5f+(float)Math.sin(c)*r,1,0,color);
        }
    }
    public static void vertex(VertexConsumer v,PoseStack pose,float x,float y,float z,float u,float w,int color){v.vertex(pose.last().pose(),x,y,z).color(color>>16&255,color>>8&255,color&255,color>>>24).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xf000f0).normal(pose.last().normal(),0,1,0).endVertex();}
    private static void spiral(VertexConsumer v,PoseStack p,double cx,double y,double cz,double radius,double time,int tone){for(int arm=0;arm<3;arm++)for(int i=0;i<56;i++){double t=i/56.,tn=(i+1)/56.,a=time+arm*Math.PI*2/3+t*Math.PI*3,an=time+arm*Math.PI*2/3+tn*Math.PI*3,r=radius*(1-t),rn=radius*(1-tn),w=.012*(1-t*.6);int c=((int)(190*Math.sin(Math.PI*t))<<24)|tone;vertex(v,p,(float)(cx+Math.cos(a)*(r+w)),(float)y,(float)(cz+Math.sin(a)*(r+w)),0,0,c);vertex(v,p,(float)(cx+Math.cos(a)*(r-w)),(float)y,(float)(cz+Math.sin(a)*(r-w)),0,1,c);vertex(v,p,(float)(cx+Math.cos(an)*(rn-w)),(float)y,(float)(cz+Math.sin(an)*(rn-w)),1,1,c);vertex(v,p,(float)(cx+Math.cos(an)*(rn+w)),(float)y,(float)(cz+Math.sin(an)*(rn+w)),1,0,c);}}
    @Override public void render(AlchemyEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getLevel()==null)return;double time=be.getLevel().getGameTime()+partial;boolean sight=com.tnc.tnc.mana.sight.client.ManaSightClient.enabled();double sync=be.vortex_sync/100.;float y=.43f+.32f*be.water/3f;
        if(be.sealed){var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(ResourceLocation.parse("tnc:block/spellflame_cauldron_lid"));mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(Sheets.cutoutBlockSheet()),be.getBlockState(),model,1,1,1,light,overlay);}
        if(be.water>0&&!be.sealed){var v=buffers.getBuffer(RenderType.entityTranslucent(WHITE));for(int i=0;i<64;i++){double a=i*Math.PI/32,an=(i+1)*Math.PI/32;vertex(v,pose,1,y,1,.5f,.5f,0xdd000000|be.color);vertex(v,pose,1+(float)Math.cos(an)*.70f,y,1+(float)Math.sin(an)*.70f,1,0,0xdd000000|be.color);vertex(v,pose,1+(float)Math.cos(a)*.70f,y,1+(float)Math.sin(a)*.70f,0,1,0xdd000000|be.color);vertex(v,pose,1,y,1,.5f,.5f,0xdd000000|be.color);}}
        if(!be.sealed)for(int i=0;i<3;i++){var item=be.inventory.getItem(i);if(item.isEmpty())continue;double a=time*.009+i*Math.PI*2/3,r=.47*(1-sync*.65);pose.pushPose();pose.translate(1+Math.cos(a)*r,y+.075+Math.sin(time*.04+i)*.018,1+Math.sin(a)*r);pose.mulPose(com.mojang.math.Axis.YP.rotation((float)-a));pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(78));float size=(float)(.39*(1-sync*.25));pose.scale(size,size,size);Minecraft.getInstance().getItemRenderer().renderStatic(item,ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,be.getLevel(),i);pose.popPose();}
        if(be.water>0){int thermal=com.tnc.tnc.arcana.RitualRules.temperatureColor(be.temperature);var v=buffers.getBuffer(RenderType.entityTranslucent(WHITE));for(int i=0;i<64;i++)for(int j=0;j<8;j++){double a=i*Math.PI/32,an=(i+1)*Math.PI/32,h=j/8.,hn=(j+1)/8.;int c=((int)((sight?95:18)*Math.pow(Math.sin(Math.PI*h),2))<<24)|thermal,cn=((int)((sight?95:18)*Math.pow(Math.sin(Math.PI*hn),2))<<24)|thermal;float r=1.01f;vertex(v,pose,1+(float)Math.cos(a)*r,(float)(.15+h*.95),1+(float)Math.sin(a)*r,0,0,c);vertex(v,pose,1+(float)Math.cos(an)*r,(float)(.15+h*.95),1+(float)Math.sin(an)*r,1,0,c);vertex(v,pose,1+(float)Math.cos(an)*r,(float)(.15+hn*.95),1+(float)Math.sin(an)*r,1,1,cn);vertex(v,pose,1+(float)Math.cos(a)*r,(float)(.15+hn*.95),1+(float)Math.sin(a)*r,0,1,cn);}
            if(sight){double vy=be.sealed?1.1:y+.13;int[] tones={0x68e9c4,0xffcf88,0x8fcbff};if(sync>=.99||be.sealed)spiral(v,pose,1,vy,1,.5,time*.035,be.ruined?0x46364b:0xa7f9e7);else for(int i=0;i<3;i++)if(!be.inventory.getItem(i).isEmpty()){double a=time*.009+i*Math.PI*2/3,r=.5*(1-sync);spiral(v,pose,1+Math.cos(a)*r,vy,1+Math.sin(a)*r,.17+sync*.23,time*.04*(i==1?-1:1),tones[i]);}}
        }
        long tick=be.getLevel().getGameTime();if(particles.getOrDefault(be.getBlockPos(),-1L)==tick)return;particles.put(be.getBlockPos(),tick);if(particles.size()>128)particles.clear();var heart=be.center();
        if(be.water>0&&tick%3==0){var at=heart.add((Math.random()-.5)*.8,.25,(Math.random()-.5)*.8);effect(at,at.add(0,be.sealed?.6:.35,0),be.color,2,3,sight?.16f:.065f,26);}
        if(!sight)return;
        if(be.water>0)for(int i=0;i<9;i++){double a=time*.018+i*Math.PI*2/9;var at=heart.add(Math.cos(a)*1.04,-.13+Math.sin(a*2)*.18,Math.sin(a)*1.04);effect(at,at.add(0,.3,0),com.tnc.tnc.arcana.RitualRules.temperatureColor(be.temperature),0,0,.055f,24);}
        if(!be.sealed&&be.water>0)for(int k=0;k<3;k++)if(!be.inventory.getItem(k).isEmpty()){double a=time*.009+k*Math.PI*2/3,r=.5*(1-sync);var middle=heart.add(Math.cos(a)*r,.12,Math.sin(a)*r);for(int i=0;i<5;i++){double t=time*.065*(k==1?-1:1)+i*Math.PI*.4;var at=middle.add(Math.cos(t)*(.15+sync*.2),.03,Math.sin(t)*(.15+sync*.2));effect(at,middle,k==0?0x68e9c4:k==1?0xffcf88:0x8fcbff,0,1,.045f,22);}}
        if(be.perfectRemaining>0&&!be.sealed)effect(heart.add(0,.35,0),heart.add(0,.35,0),0xffedb3,1,0,.45f,12);
        if(be.ruined)effect(heart.add(0,.2,0),heart.add(0,.7,0),0x443349,2,3,.3f,30);
        if(be.burst>50)for(int i=0;i<10;i++){double a=i*Math.PI/5+time*.04;effect(heart,heart.add(Math.cos(a)*2,1.2,Math.sin(a)*2),i%3==0?0xffe2a5:be.color,i%3==0?1:0,3,.07f,32);}
    }
    private static void effect(net.minecraft.world.phys.Vec3 a,net.minecraft.world.phys.Vec3 b,int c,int s,int m,float size,int life){com.tnc.tnc.mana.sight.client.ManaSightParticles.effect(a,b,c,s,m,size,life);}
    @Override public int getViewDistance(){return 48;}
}
