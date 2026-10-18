package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/** Small physical details shared by the dice and card tables. */
public final class CasinoTableDetails {
    private CasinoTableDetails() {}
    public static void label(Font font, PoseStack pose, MultiBufferSource buffers,
                             String text, float x, float y, float z, int light) {
        label(font,pose,buffers,text,x,y,z,light,0xFFF1D3,0.012F);
    }
    public static void label(Font font,PoseStack pose,MultiBufferSource buffers,String text,
                             float x,float y,float z,int light,int color,float scale) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text)/2.0F, 0, color, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
    public static void die(PoseStack pose, VertexConsumer buffer, float x, float y, float z,
                           int face, float elapsed, int duration, boolean spinning, int light) {
        pose.pushPose();
        float t = spinning ? Mth.clamp(elapsed/duration, 0, 1) : 1;
        float bounce = (float)Math.abs(Math.sin(t*Math.PI*4)) * (1-t) * 0.22F;
        pose.translate(x, y+bounce, z);
        float turn = 1080 * (1-t)*(1-t);
        pose.mulPose(Axis.XP.rotationDegrees(turn));
        pose.mulPose(Axis.ZP.rotationDegrees(turn*0.75F));
        TNCasinoGeometry.box(pose, buffer, -0.10F,-0.10F,-0.10F,0.10F,0.10F,0.10F,CasinoPalette.DICE,light);
        // Draw every face, with opposite values summing to seven.
        for (int side=0;side<6;side++) {
            pose.pushPose();
            if (side==1) pose.mulPose(Axis.XP.rotationDegrees(180));
            if (side==2) pose.mulPose(Axis.XP.rotationDegrees(90));
            if (side==3) pose.mulPose(Axis.XP.rotationDegrees(-90));
            if (side==4) pose.mulPose(Axis.ZP.rotationDegrees(90));
            if (side==5) pose.mulPose(Axis.ZP.rotationDegrees(-90));
            int adjacent = face != 1 && face != 6 ? 1 : 2;
            int other = face != 3 && face != 4 ? 3 : 2;
            int value = switch(side) { case 0 -> face; case 1 -> 7-face; case 2 -> adjacent;
                case 3 -> 7-adjacent; case 4 -> other; default -> 7-other; };
            pips(pose,buffer,value,light);
            pose.popPose();
        }
        pose.popPose();
    }
    private static void pips(PoseStack pose, VertexConsumer buffer, int face, int light) {
        for (int row=-1;row<=1;row++) for (int col=-1;col<=1;col++) {
            boolean dot = (face%2==1 && row==0 && col==0)
                    || (face>=2 && row==col && row!=0)
                    || (face>=4 && row==-col && row!=0)
                    || (face==6 && row==0 && col!=0);
            if (dot) TNCasinoGeometry.box(pose, buffer,col*0.055F-0.013F,0.1005F,row*0.055F-0.013F,
                    col*0.055F+0.013F,0.105F,row*0.055F+0.013F,CasinoPalette.PIP,light);
        }
    }
}
