package com.tnc.tnc.magic.thunderseal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.magic.thunderseal.ThunderSealContent;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;

/** Authored line geometry; the neutral one-pixel texture only supplies shader coverage. */
public final class ThunderSealSigil {
    private static final ResourceLocation INK=ThunderSealContent.id("textures/entity/thunder_seal_ink.png");
    private static final int GOLD=0xFFEDA6,BLUE=0x5BD3FF,VIOLET=0xA78BE7;
    // x, z, red, green, blue, alpha. Construct once, scale the mesh at draw time.
    private static final float[] MESH=build();
    private ThunderSealSigil(){}

    public static void render(PoseStack pose,MultiBufferSource buffers,float radius,float alpha){
        var consumer=buffers.getBuffer(RenderType.entityTranslucentEmissive(INK));
        var frame=pose.last();
        for(int i=0;i<MESH.length;i+=6){
            consumer.vertex(frame.pose(),MESH[i]*radius,0,MESH[i+1]*radius)
                .color((int)MESH[i+2],(int)MESH[i+3],(int)MESH[i+4],(int)(MESH[i+5]*alpha*255))
                .uv(.5F,.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(frame.normal(),0,1,0).endVertex();
        }
    }

    private static float[] build(){
        var mesh=new ArrayList<Float>();
        // Broken outer binding ring, quiet blue inner rule, and sparse radial ticks.
        for(int i=0;i<12;i++)arc(mesh,.955,i*30+3,i*30+27,.008,GOLD,1);
        arc(mesh,.874,0,360,.004,BLUE,.75F);
        for(int i=0;i<48;i++){
            double angle=Math.toRadians(i*7.5),outer=i%4==0?.938:.924;
            radial(mesh,.895,outer,angle,i%4==0?.007:.0035,i%4==0?GOLD:BLUE,i%4==0?1:.6F);
        }
        // Six open inner arcs frame the centre without filling the floor with filigree.
        for(int i=0;i<6;i++)arc(mesh,.595,i*60+5,i*60+55,.006,GOLD,.9F);
        // A finite alphabet of angular, repeatable glyphs; no painted jewellery or gradient disk.
        double[][][] glyphs={
            {{-.04,-.055},{.015,-.055},{-.01,0},{.04,0},{-.025,.06}},
            {{-.035,-.055},{-.035,.045},{.035,.045},{.035,-.02},{-.035,-.02}},
            {{-.04,-.05},{.025,-.05},{-.01,0},{.035,.05},{-.04,.05}},
            {{-.035,.05},{-.035,-.045},{.03,-.045},{.03,.01},{-.01,.01},{-.01,.05}}
        };
        for(int i=0;i<12;i++){
            double a=Math.toRadians(i*30);double[][] glyph=glyphs[i%glyphs.length];
            for(int k=1;k<glyph.length;k++){
                double[] x=local(glyph[k-1][0],glyph[k-1][1],.744,a),z=local(glyph[k][0],glyph[k][1],.744,a);
                line(mesh,x[0],x[1],z[0],z[1],.0065,i%3==0?GOLD:BLUE,.95F);
            }
        }
        // Three small purple binding stitches, deliberately much less prominent than the two main inks.
        for(int i=0;i<3;i++){
            double a=Math.toRadians(i*120+15);
            radial(mesh,.565,.63,a,.009,VIOLET,.9F);
            arc(mesh,.65,i*120+12,i*120+18,.006,VIOLET,.8F);
        }
        // Three intertwined lightning strokes form a recognisable thunder seal at the centre.
        double[][] bolt={{0,-.035},{-.065,.08},{.025,.065},{-.045,.245},{.065,.12},{-.015,.13},{.035,.035}};
        for(int i=0;i<3;i++){
            double angle=Math.toRadians(i*120);
            for(int k=1;k<bolt.length;k++){
                double[] a=local(bolt[k-1][0],bolt[k-1][1],0,angle),b=local(bolt[k][0],bolt[k][1],0,angle);
                line(mesh,a[0],a[1],b[0],b[1],.010,i==1?BLUE:GOLD,1);
            }
            radial(mesh,.34,.49,angle,.005,BLUE,.7F);
            arc(mesh,.32,i*120+15,i*120+78,.004,BLUE,.65F);
        }
        float[] data=new float[mesh.size()];for(int i=0;i<data.length;i++)data[i]=mesh.get(i);
        return data;
    }

    private static double[] local(double tangent,double outward,double radius,double angle){
        return new double[]{Math.cos(angle)*(radius+outward)-Math.sin(angle)*tangent,Math.sin(angle)*(radius+outward)+Math.cos(angle)*tangent};
    }
    private static void radial(ArrayList<Float> m,double inner,double outer,double angle,double width,int color,float alpha){
        line(m,Math.cos(angle)*inner,Math.sin(angle)*inner,Math.cos(angle)*outer,Math.sin(angle)*outer,width,color,alpha);
    }
    private static void arc(ArrayList<Float> m,double radius,double start,double end,double width,int color,float alpha){
        width*=2.4;
        int steps=Math.max(1,(int)Math.ceil((end-start)/4));
        for(int i=0;i<steps;i++){
            double a=Math.toRadians(start+(end-start)*i/steps),b=Math.toRadians(start+(end-start)*(i+1)/steps);
            double inner=radius-width/2,outer=radius+width/2;
            put(m,Math.cos(a)*inner,Math.sin(a)*inner,color,alpha);put(m,Math.cos(b)*inner,Math.sin(b)*inner,color,alpha);
            put(m,Math.cos(b)*outer,Math.sin(b)*outer,color,alpha);put(m,Math.cos(a)*outer,Math.sin(a)*outer,color,alpha);
        }
    }
    private static void line(ArrayList<Float> m,double x,double z,double a,double b,double width,int color,float alpha){
        width*=2.4;
        double distance=Math.hypot(a-x,b-z),dx=-(b-z)/distance*width/2,dz=(a-x)/distance*width/2;
        put(m,x+dx,z+dz,color,alpha);put(m,a+dx,b+dz,color,alpha);
        put(m,a-dx,b-dz,color,alpha);put(m,x-dx,z-dz,color,alpha);
    }
    private static void put(ArrayList<Float> m,double x,double z,int color,float alpha){
        m.add((float)x);m.add((float)z);m.add((float)((color>>16)&255));m.add((float)((color>>8)&255));m.add((float)(color&255));m.add(alpha);
    }
}
