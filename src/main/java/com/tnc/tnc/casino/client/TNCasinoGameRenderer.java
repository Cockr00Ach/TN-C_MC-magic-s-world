package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.casino.machine.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.util.Mth;

/** Large casino furniture sharing walnut, burgundy, champagne gold and emerald felt. */
public final class TNCasinoGameRenderer implements BlockEntityRenderer<TNCasinoGameEntity> {
    private final Font font;
    public TNCasinoGameRenderer(BlockEntityRendererProvider.Context context) { font=context.getFont(); }
    @Override
    public void render(TNCasinoGameEntity machine,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(machine.getLevel()==null) return;
        VertexConsumer buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(TNCasinoGeometry.white()));
        boolean wheel=machine.game()==TNCasinoGameEntity.Game.WHEEL;
        float cx=wheel?2:4,cz=wheel?(float)(0.7*4/3):2;
        pose.pushPose();pose.translate(cx,0,cz);
        pose.mulPose(Axis.YP.rotationDegrees(TNCasinoGeometry.yawFor(machine.getBlockState().getValue(TNCasinoMachineBlock.FACING))));
        pose.translate(-cx,0,-cz);
        if(wheel)pose.scale(4F/3,4F/3,4F/3);
        float elapsed=machine.getLevel().getGameTime()-machine.startedAt()+partial;
        if(wheel) wheel(machine,pose,buffer,buffers,elapsed,light);
        else cards(machine,pose,buffer,buffers,elapsed,light);
        pose.popPose();
    }
    private void wheel(TNCasinoGameEntity m,PoseStack p,VertexConsumer b,MultiBufferSource buffers,float elapsed,int light) {
        TNCasinoGeometry.box(p,b,0.08F,0,0.08F,2.92F,0.15F,1.32F,CasinoPalette.WOOD_DARK,light);
        TNCasinoGeometry.box(p,b,0.13F,0.15F,0.13F,2.87F,0.19F,1.27F,CasinoPalette.BRASS_DARK,light);
        TNCasinoGeometry.box(p,b,1.2F,0.19F,0.82F,1.8F,2.16F,1.16F,CasinoPalette.WOOD,light);
        TNCasinoGeometry.box(p,b,1.23F,0.19F,0.79F,1.28F,2.16F,0.83F,CasinoPalette.BRASS,light);
        TNCasinoGeometry.box(p,b,1.72F,0.19F,0.79F,1.77F,2.16F,0.83F,CasinoPalette.BRASS,light);
        TNCasinoGeometry.box(p,b,0.95F,0.25F,0.14F,2.05F,0.35F,0.62F,CasinoPalette.WOOD,light);
        TNCasinoGeometry.box(p,b,1.03F,0.35F,0.21F,1.97F,0.36F,0.55F,CasinoPalette.BRASS_DARK,light);
        p.pushPose();p.translate(1.5F,2.03F,0.64F);
        CasinoFurniture.sector(p,b,0,1.43F,-0.02F,0.18F,0,360,CasinoPalette.WOOD_DARK,light);
        CasinoFurniture.sector(p,b,1.32F,1.43F,-0.065F,-0.02F,0,360,CasinoPalette.BRASS,light);
        CasinoFurniture.sector(p,b,1.36F,1.40F,-0.072F,-0.065F,0,360,CasinoPalette.CARD_BACK,light);
        float t=m.spinning()?Mth.clamp(elapsed/m.clientSpinTicks(),0,1):1;
        float spin=(1800-m.result(0)*30)*(1-(float)Math.pow(1-t,3));
        p.pushPose();p.mulPose(Axis.ZP.rotationDegrees(spin));
        for(int i=0;i<12;i++) {
            int mult=CasinoRules.WHEEL_MULTIPLIERS[i];
            int color=mult==0?CasinoPalette.CARD_BACK:mult==4?CasinoPalette.BRASS_DARK:CasinoPalette.FELT;
            CasinoFurniture.sector(p,b,0.18F,1.3F,-0.09F,-0.025F,i*30-14.5F,i*30+14.5F,color,light);
            p.pushPose();p.mulPose(Axis.ZP.rotationDegrees(i*30));
            for(int mark=0;mark<mult;mark++) TNCasinoGeometry.box(p,b,-0.11F+mark*0.06F,0.95F,-0.105F,
                    -0.075F+mark*0.06F,1.08F,-0.095F,CasinoPalette.CARD_FACE,light);
            TNCasinoGeometry.box(p,b,-0.016F,1.24F,-0.105F,0.016F,1.3F,-0.095F,CasinoPalette.BRASS,light);
            p.popPose();
        }
        CasinoFurniture.sector(p,b,0,0.22F,-0.16F,-0.09F,0,360,CasinoPalette.BRASS,light);
        CasinoFurniture.sector(p,b,0,0.15F,-0.17F,-0.16F,0,360,CasinoPalette.CARD_BACK,light);
        p.popPose();p.popPose();
        TNCasinoGeometry.box(p,b,1.42F,3.35F,0.40F,1.58F,3.59F,0.55F,CasinoPalette.BRASS,light);
        TNCasinoGeometry.box(p,b,1.465F,3.24F,0.39F,1.535F,3.45F,0.44F,CasinoPalette.CHIP_RED,light);
        // Complete all solid geometry before selecting font buffers.
        p.pushPose();p.translate(1.5F,2.03F,0.64F);p.mulPose(Axis.ZP.rotationDegrees(spin));
        for(int i=0;i<12;i++) {
            p.pushPose();p.mulPose(Axis.ZP.rotationDegrees(i*30));
            faceLabel(p,buffers,CasinoRules.WHEEL_MULTIPLIERS[i]+"×",0,0.73F,-0.108F,0.025F,light);
            p.popPose();
        }
        p.popPose();
        CasinoTableDetails.label(font,p,buffers,m.spinning()?"转动中…":m.spins()==0?"幸运转轮 · 1铜币":
                CasinoRules.WHEEL_MULTIPLIERS[m.result(0)]+"倍 · 返回"+m.lastPayout(),1.5F,0.365F,0.38F,light);
    }
    private void cards(TNCasinoGameEntity m,PoseStack p,VertexConsumer b,MultiBufferSource buffers,float elapsed,int light) {
        CasinoFurniture.oval(p,b,4,2,4,2,.79F,.95F,CasinoPalette.WOOD,light);
        CasinoFurniture.oval(p,b,4,2,3.96F,1.96F,.95F,.97F,CasinoPalette.BRASS,light);
        CasinoFurniture.oval(p,b,4,2,3.92F,1.92F,.97F,1.07F,CasinoPalette.CARD_BACK,light);
        CasinoFurniture.oval(p,b,4,2,3.76F,1.76F,1.07F,1.081F,CasinoPalette.BRASS,light);
        CasinoFurniture.oval(p,b,4,2,3.73F,1.73F,1.081F,1.09F,CasinoPalette.FELT,light);
        for(float x:new float[]{1.8F,6.2F}) {
            TNCasinoGeometry.box(p,b,x-.34F,0,1.4F,x+.34F,.1F,2.6F,CasinoPalette.WOOD_DARK,light);
            TNCasinoGeometry.box(p,b,x-.26F,.1F,1.55F,x+.26F,.15F,2.45F,CasinoPalette.BRASS_DARK,light);
            TNCasinoGeometry.box(p,b,x-.19F,.15F,1.7F,x+.19F,.79F,2.3F,CasinoPalette.WOOD,light);
        }
        CasinoFurniture.chips(p,b,5.6F,1.093F,2.8F,light);
        TNCasinoGeometry.box(p,b,1.2F,1.09F,2.65F,1.63F,1.22F,3.03F,CasinoPalette.WOOD_DARK,light);
        TNCasinoGeometry.box(p,b,1.22F,1.22F,2.67F,1.61F,1.24F,3.01F,CasinoPalette.BRASS_DARK,light);
        TNCasinoGeometry.box(p,b,1.25F,1.24F,2.71F,1.58F,1.26F,2.97F,CasinoPalette.CARD_BACK,light);
        for(int i=0;i<2;i++) {
            CasinoFurniture.bettingBox(p,b,2.5F+i*2F,1.091F,.8F,1F,1.1F,light);
            p.pushPose();p.translate(3F+i*2F,1.13F,1.35F);p.scale(1.4F,1.4F,1.4F);
            p.mulPose(Axis.ZP.rotationDegrees(m.spins()>0?180*(1-reveal(m,elapsed,i)):180));
            TNCasinoGeometry.box(p,b,-0.22F,-0.013F,-0.30F,0.22F,0.013F,0.30F,CasinoPalette.CARD_FACE,light);
            TNCasinoGeometry.box(p,b,-0.20F,-0.016F,-0.28F,0.20F,-0.014F,0.28F,CasinoPalette.CARD_BACK,light);
            CasinoFurniture.bettingBox(p,b,-0.16F,-0.019F,-0.24F,0.32F,0.48F,light);
            if(m.spins()>0) {
                CasinoCardFaces.suit(p,b,m.cardSuit(i),0,0.04F,0.025F,light);
                CasinoCardFaces.suit(p,b,m.cardSuit(i),-0.15F,-0.12F,0.010F,light);
                CasinoCardFaces.suit(p,b,m.cardSuit(i),0.15F,0.17F,0.010F,light);
            }
            p.popPose();
        }
        for(int seat=0;seat<6;seat++) CasinoFurniture.bettingBox(p,b,1.5F+seat*.88F,1.091F,3.45F,.6F,.32F,light);
        if(m.spins()>0) for(int i=0;i<2;i++) {
            p.pushPose();p.translate(3F+i*2F,1.13F,1.35F);p.scale(1.4F,1.4F,1.4F);p.mulPose(Axis.ZP.rotationDegrees(180*(1-reveal(m,elapsed,i))));
            CasinoTableDetails.label(font,p,buffers,rank(m.result(i)+1),-0.15F,0.017F,-0.23F,light,
                    0xFF000000|CasinoCardFaces.color(m.cardSuit(i)),0.012F);
            p.popPose();
        }
        CasinoTableDetails.label(font,p,buffers,"玩家",3F,1.094F,.55F,light);
        CasinoTableDetails.label(font,p,buffers,"庄家",5F,1.094F,.55F,light);
        CasinoTableDetails.label(font,p,buffers,"贵宾比牌 · "+m.betName(),4,1.094F,2.7F,light);
        CasinoTableDetails.label(font,p,buffers,m.spinning()?"翻牌中…":m.spins()==0?"A小 K大 · 同点返本":"返回 "+m.lastPayout()+" 铜币",4,1.094F,3.05F,light);
    }
    private static float reveal(TNCasinoGameEntity m,float elapsed,int i) { return m.spinning()?Mth.clamp((elapsed-i*18)/26,0,1):1; }
    private void faceLabel(PoseStack p,MultiBufferSource buffers,String text,float x,float y,float z,float scale,int light) {
        p.pushPose();p.translate(x,y,z);p.mulPose(Axis.YP.rotationDegrees(180));p.scale(scale,-scale,scale);
        font.drawInBatch(text,-font.width(text)/2F,0,0xFFF1D3,false,p.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);p.popPose();
    }
    private static String rank(int value) { return switch(value) { case 1 -> "A";case 11 -> "J";case 12 -> "Q";case 13 -> "K";default -> Integer.toString(value); }; }
}
