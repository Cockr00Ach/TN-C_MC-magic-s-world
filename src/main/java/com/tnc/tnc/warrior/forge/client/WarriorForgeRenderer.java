package com.tnc.tnc.warrior.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.warrior.forge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

public final class WarriorForgeRenderer implements BlockEntityRenderer<WarriorForgeEntity> {
    public WarriorForgeRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(WarriorForgeEntity be,float partial,PoseStack p,MultiBufferSource b,int light,int overlay){
        if(be.getLevel()==null)return;
        double time=be.getLevel().getGameTime()+partial;
        if(be.kind()==WarriorForgeBlock.Kind.BATH&&be.water>0){
            ForgeGeometry.box(p,b,.10f,.64f,.10f,.80f,.025f,.80f,0xaa70adbb);
            boolean using=be.holding;
            for(int i=0;i<(using?4:1);i++){
                float radius=(float)((time*.013+i*.20)% .43);
                ForgeGeometry.ring(p,b,.5f,.67f,.5f,radius,.012f,using?0xbde2f5ee:0x557eafc7,0);
            }
        }
        var s=be.session;
        if(!be.work.isEmpty()&&s!=null){
            if(s.stage==ForgeSession.Stage.READY){
                p.pushPose();p.translate(.5,.98,.5);p.mulPose(Axis.XP.rotationDegrees(90));p.scale(.65f,.65f,.65f);
                Minecraft.getInstance().getItemRenderer().renderStatic(be.work,ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,p,b,be.getLevel(),0);p.popPose();
            }else{
                float progress=s.strokes/12f;
                float heat=s.stage==ForgeSession.Stage.ASSEMBLY&&!be.holding?0:s.heat/1000f;
                if(be.kind()==WarriorForgeBlock.Kind.BATH&&be.holding)heat*=Math.max(0,1-be.holdTicks/90f);
                int color=0xff000000|((int)(115+140*heat)<<16)|((int)(120+65*heat)<<8)|(int)(130-85*heat);
                float y=be.kind()==WarriorForgeBlock.Kind.ANVIL?.85f:.77f;
                if(be.kind()==WarriorForgeBlock.Kind.BATH&&be.holding)y-=Math.min(1,(be.holdTicks+partial)/30f)*.16f;
                float length=.38f+.48f*progress,width=.28f-.12f*progress,thickness=.15f-.10f*progress;
                if(s.pattern==ForgePattern.GUANDAO)width*=1.6f;
                if(s.pattern==ForgePattern.SPEAR)width*=.65f;
                for(int i=0;i<5;i++){
                    float taper=1-Math.max(0,i-2)*.2f*progress;
                    float bend=(float)(s.deviation[i%3]/100*.065*Math.sin(i));
                    ForgeGeometry.box(p,b,.5f-width*taper/2+bend,y,.5f-length/2+length*i/5,width*taper,thickness,length/5+.002f,color);
                }
                if(be.kind()==WarriorForgeBlock.Kind.ANVIL&&s.stage==ForgeSession.Stage.SHAPING&&s.strokes<12){
                    int target=s.pattern.zone(s.strokes);
                    for(int i=0;i<3;i++)ForgeGeometry.box(p,b,.02f+i/3f,.835f,.10f,.29f,.007f,.08f,i==target?0xc8f5c86f:0x335a6b72);
                }
                float impactAge=(float)(time-be.lastImpact);
                if(impactAge>=0&&impactAge<8){
                    float drop=(float)Math.sin(Math.PI*Math.min(1,impactAge/7));
                    ForgeGeometry.box(p,b,.1f+be.impactZone/3f,.91f+drop*.26f,.46f,.13f,.10f,.18f,0xff46515d);
                    ForgeGeometry.ring(p,b,.17f+be.impactZone/3f,.90f,.5f,.04f+impactAge*.03f,.012f,(Math.max(0,150-(int)impactAge*18)<<24)|0xffd8a0,0);
                }
            }
        }
        double age=time-be.divineAt;
        if(be.divineAt>=0&&age>=0&&age<240){
            float fade=(float)Math.min(Math.min(age/25,(240-age)/35),1);
            int color=((int)(fade*150)<<24)|0xf3d89c;
            ForgeGeometry.ring(p,b,.5f,16,.5f,12+2*(float)Math.sin(age*.015),.12f,color,age*.004);
            ForgeGeometry.ring(p,b,.5f,18,.5f,18,.18f,color,-age*.002);
            ForgeGeometry.ring(p,b,.5f,.82f,.5f,2+(float)age*.012f,.03f,color,0);
            if(age>70&&age<150){
                float y=21-(float)(age-70)*.075f;
                ForgeGeometry.box(p,b,-1.5f,y,-.2f,4,.7f,1.4f,color);
                ForgeGeometry.box(p,b,.3f,y+.7f,.3f,.4f,3,.4f,color);
            }
        }
    }
    @Override public boolean shouldRenderOffScreen(WarriorForgeEntity be){return be.divineAt>=0&&be.getLevel()!=null&&be.getLevel().getGameTime()-be.divineAt<240;}
    @Override public int getViewDistance(){return 128;}
}
