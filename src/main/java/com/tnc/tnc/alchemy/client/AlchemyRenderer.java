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
    private static final int EMBER=0xffb961;
    private final java.util.Map<net.minecraft.core.BlockPos,Long> particles=new java.util.HashMap<>();
    public AlchemyRenderer(BlockEntityRendererProvider.Context c){}
    public static void vertex(VertexConsumer v,PoseStack pose,float x,float y,float z,float u,float w,int color){v.vertex(pose.last().pose(),x,y,z).color(color>>16&255,color>>8&255,color&255,color>>>24).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xf000f0).normal(pose.last().normal(),0,1,0).endVertex();}
    public static void ring(PoseStack p,MultiBufferSource b,double time,int color,float y,float radius){var v=b.getBuffer(RenderType.entityTranslucent(WHITE));for(int i=0;i<48;i++){double a=i*Math.PI/24+time*.02,c=(i+1)*Math.PI/24+time*.02;float r=radius,s=radius-.012f;vertex(v,p,.5f+(float)Math.cos(a)*r,y,.5f+(float)Math.sin(a)*r,0,0,color);vertex(v,p,.5f+(float)Math.cos(a)*s,y,.5f+(float)Math.sin(a)*s,0,1,color);vertex(v,p,.5f+(float)Math.cos(c)*s,y,.5f+(float)Math.sin(c)*s,1,1,color);vertex(v,p,.5f+(float)Math.cos(c)*r,y,.5f+(float)Math.sin(c)*r,1,0,color);}}
    @Override public void render(AlchemyEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getLevel()==null)return;double time=be.getLevel().getGameTime()+partial;
        float fill=be.water/3f;boolean finished=!be.ready.isEmpty();if(finished)fill=Math.max(fill,.4f);
        float y=.38f+.34f*fill;double stir=Math.max(0,1-(time-be.stirredAt)/40.0);
        var recipe=Elixir.find(be.job);float progress=recipe==null?0:Math.min(1,be.progress/(float)recipe.ticks);
        if(fill>0){var v=buffers.getBuffer(RenderType.entityTranslucent(WHITE));
            for(int i=0;i<48;i++){double a=i*Math.PI/24,c=(i+1)*Math.PI/24;float ripple=(float)Math.sin(a*4+time*.13)*.008f;
                vertex(v,pose,.5f,y-(float)stir*.055f,.5f,.5f,.5f,0xee000000|be.color);
                vertex(v,pose,.5f+(float)Math.cos(c)*.32f,y+ripple,.5f+(float)Math.sin(c)*.32f,1,0,0xdd000000|be.color);
                vertex(v,pose,.5f+(float)Math.cos(a)*.32f,y+ripple,.5f+(float)Math.sin(a)*.32f,0,1,0xdd000000|be.color);
                vertex(v,pose,.5f,y-(float)stir*.055f,.5f,.5f,.5f,0xee000000|be.color);
            }
        }
        // Actual recipe ingredients remain visible before ignition and dissolve during fusion.
        for(int i=0;i<4;i++){var item=be.inventory.getItem(i);if(item.isEmpty())continue;
            double a=time*(.008+stir*.025)+i*Math.PI*.5;pose.pushPose();
            pose.translate(.5+Math.cos(a)*.22,y+.065-progress*.06+Math.sin(time*.07+i)*.015,.5+Math.sin(a)*.22);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float)-a));pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(78));
            float size=.30f*(1-progress*.65f);pose.scale(size,size,size);
            Minecraft.getInstance().getItemRenderer().renderStatic(item,ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,be.getLevel(),i);pose.popPose();
        }
        if(stir>0){pose.pushPose();double a=time*.13;pose.translate(.5+Math.cos(a)*.2,y+.25,.5+Math.sin(a)*.2);
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float)-a));pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-28));pose.scale(.65f,.65f,.65f);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(AlchemyContent.STIRRER),ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,be.getLevel(),4);pose.popPose();}
        long tick=be.getLevel().getGameTime();if(particles.getOrDefault(be.getBlockPos(),-1L)==tick)return;
        particles.put(be.getBlockPos(),tick);if(particles.size()>128)particles.clear();
        var heart=net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).add(0,.18,0);
        if(time-be.pouredAt<22)for(int i=0;i<7;i++){double a=i*.897;var from=heart.add(.48+Math.cos(a)*.035,.75+i*.025,.05);effect(from,heart.add(Math.cos(a)*.22,-.05,Math.sin(a)*.22),i%2==0?0xbceaff:0x58b5e7,0,3,.045f,12);}
        if(be.fire==2&&be.water>0&&(be.burn>0||!be.inventory.getItem(4).isEmpty()))for(int i=0;i<3;i++){
            double a=time*.07+i*2.094;var from=heart.add(Math.cos(a)*.24,-.53,Math.sin(a)*.24);
            effect(from,from.add(0,.3,0),i==0?0xffcb68:0x53e5d0,1,3,.085f,18);}
        if(!be.working&&be.burst==0){if(fill>0&&tick%8==0)effect(heart,heart.add(0,.3,0),be.color,0,3,.04f,24);return;}
        int family=recipe==null?0:recipe.effectFamily();
        double reach=recipe!=null&&recipe.ticks>=260?3.8:2.1;
        if(be.burst>0){
            if(be.burst>55)for(int i=0;i<18;i++){double a=i*Math.PI/9+time*.09;var end=heart.add(Math.cos(a)*reach,1+Math.sin(a*3)*.55,Math.sin(a)*reach);
                effect(heart,end,i%3==0?0xffe7a0:be.color,i%5==0?1:0,3,.07f,32);}
            if(tick%3==0)effect(heart.add(0,.65,0),heart,be.color,2,0,.75f,32);return;
        }
        // Dense discrete grains, wisps and glints. No opaque ribbons or beam meshes.
        for(int i=0;i<10;i++){
            double a=time*.055+i*Math.PI*.2,r=.17+(i%3)*.065;
            var from=heart.add(Math.cos(a)*r,-.04,Math.sin(a)*r);
            var end=heart.add(Math.cos(a+1.5)*(.6+progress),.65+progress*1.5,Math.sin(a+1.5)*(.6+progress));
            effect(from,end,i%4==0?0xffe9bc:be.color,i==0?1:0,3,.045f+(i%3)*.018f,26+i);
        }
        if(tick%3==0)effect(heart.add(0,.25,0),heart.add(0,1,0),be.color,2,3,.30f+progress*.3f,32);
        if(progress>.3f){
            for(int i=0;i<6;i++){double a=time*.032+i*Math.PI/3, r=reach*(.45+progress*.45);
                var at=heart.add(Math.cos(a)*r,.8+Math.sin(a*2)*.55+progress,Math.sin(a)*r);
                if(family==0)effect(at,heart,0xb8e66b,i%3==0?1:0,1,.065f,45);
                else if(family==1){var star=heart.add(Math.cos(a)*r,2.4+Math.sin(a)*.7,Math.sin(a)*r);effect(star,heart.add(0,2.4,0),i%2==0?0x73e7ff:0xffe7a5,1,1,.12f,35);}
                else if(family==2)effect(heart.add(0,-.3,0),at, i%2==0?0xffc573:be.color,1,3,.08f,30);
                else effect(at,heart.add(0,2.6,0),i%3==0?0xe8fcff:be.color,0,3,.08f,45);
            }
        }
        if(progress>.65f&&tick%3==0){
            for(int i=0;i<5;i++){double a=time*.024+i*Math.PI*.4;var at=heart.add(Math.cos(a)*reach,1.5+Math.sin(a*3),Math.sin(a)*reach);effect(at,heart.add(0,.7,0),be.color,2,2,.33f,45);}
        }
    }
    private static void effect(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,int color,int sprite,int motion,float size,int life){com.tnc.tnc.mana.sight.client.ManaSightParticles.effect(from,to,color,sprite,motion,size,life);}
    @Override public int getViewDistance(){return 48;}
}
