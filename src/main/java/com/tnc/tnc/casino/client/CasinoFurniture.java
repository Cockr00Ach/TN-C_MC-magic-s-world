package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/** Shared upholstered rails, gold inlay and physical chip trays. */
public final class CasinoFurniture {
    private CasinoFurniture() {}

    /** Sized frame with constant rail/leg thickness; tabletop ornaments use their own proportions. */
    public static void table(PoseStack p,VertexConsumer b,float width,float depth,float top,int light) {
        TNCasinoGeometry.box(p,b,.06F,top-.18F,.06F,width-.06F,top-.04F,depth-.06F,CasinoPalette.WOOD,light);
        TNCasinoGeometry.box(p,b,.18F,top-.04F,.18F,width-.18F,top,depth-.18F,CasinoPalette.FELT,light);
        for(float x:new float[]{.24F,width-.56F})for(float z:new float[]{.24F,depth-.56F}) {
            TNCasinoGeometry.box(p,b,x,0,z,x+.32F,top-.18F,z+.32F,CasinoPalette.WOOD_DARK,light);
            TNCasinoGeometry.box(p,b,x-.025F,.08F,z-.025F,x+.345F,.14F,z+.345F,CasinoPalette.BRASS_DARK,light);
        }
        TNCasinoGeometry.box(p,b,.02F,top-.04F,.02F,width-.02F,top+.05F,.16F,CasinoPalette.CARD_BACK,light);
        TNCasinoGeometry.box(p,b,.02F,top-.04F,depth-.16F,width-.02F,top+.05F,depth-.02F,CasinoPalette.CARD_BACK,light);
        TNCasinoGeometry.box(p,b,.02F,top-.04F,.16F,.16F,top+.05F,depth-.16F,CasinoPalette.CARD_BACK,light);
        TNCasinoGeometry.box(p,b,width-.16F,top-.04F,.16F,width-.02F,top+.05F,depth-.16F,CasinoPalette.CARD_BACK,light);
        bettingBox(p,b,.17F,top+.003F,.17F,width-.34F,depth-.34F,light);
    }

    public static void oval(PoseStack p, VertexConsumer b, float cx, float cz,
                            float rx, float rz, float bottom, float top, int color, int light) {
        // Narrow strips preserve the stepped silhouette of Minecraft furniture.
        for (int i=0;i<32;i++) {
            float z0=-rz+2*rz*i/32, z1=-rz+2*rz*(i+1)/32;
            float z=(z0+z1)/2;
            float half=rx*(float)Math.sqrt(1-z*z/(rz*rz));
            TNCasinoGeometry.box(p,b,cx-half,bottom,cz+z0,cx+half,top,cz+z1,color,light);
        }
    }

    public static void bettingBox(PoseStack p,VertexConsumer b,float x,float y,float z,
                                  float width,float depth,int light) {
        float t=0.012F;
        TNCasinoGeometry.box(p,b,x,y,z,x+width,y+0.003F,z+t,CasinoPalette.BRASS,light);
        TNCasinoGeometry.box(p,b,x,y,z+depth-t,x+width,y+0.003F,z+depth,CasinoPalette.BRASS,light);
        TNCasinoGeometry.box(p,b,x,y,z,x+t,y+0.003F,z+depth,CasinoPalette.BRASS,light);
        TNCasinoGeometry.box(p,b,x+width-t,y,z,x+width,y+0.003F,z+depth,CasinoPalette.BRASS,light);
    }

    public static void chips(PoseStack p,VertexConsumer b,float x,float y,float z,int light) {
        TNCasinoGeometry.box(p,b,x-0.05F,y-0.02F,z-0.05F,x+0.68F,y,z+0.23F,CasinoPalette.WOOD_DARK,light);
        for(int stack=0;stack<4;stack++) for(int layer=0;layer<3+stack%2;layer++) {
            int color=stack==0?CasinoPalette.CHIP_RED:stack==1?CasinoPalette.CHIP_BLUE:
                    stack==2?CasinoPalette.CHIP_WHITE:CasinoPalette.FELT;
            TNCasinoGeometry.cylinder(p,b,new Vector3f(x+stack*0.17F,y+0.015F+layer*0.025F,z+0.08F),
                    0.068F,0.021F,0.07F,12,0,color,light,null);
        }
    }

    /** A solid annular sector in the XY plane, with the zero angle pointing upward. */
    public static void sector(PoseStack p,VertexConsumer b,float inner,float outer,float front,float back,
                              float start,float end,int color,int light) {
        int steps=Math.max(1,(int)Math.ceil((end-start)/5));
        for(int i=0;i<steps;i++) {
            double a=Math.toRadians(start+(end-start)*i/steps),c=Math.toRadians(start+(end-start)*(i+1)/steps);
            float[][] v={{-(float)Math.sin(a)*inner,(float)Math.cos(a)*inner},
                    {-(float)Math.sin(a)*outer,(float)Math.cos(a)*outer},
                    {-(float)Math.sin(c)*outer,(float)Math.cos(c)*outer},
                    {-(float)Math.sin(c)*inner,(float)Math.cos(c)*inner}};
            for(int face=0;face<2;face++) for(int j=0;j<4;j++) {
                int index=face==0?3-j:j;
                vertex(p,b,v[index][0],v[index][1],face==0?front:back,0,0,face==0?-1:1,color,light);
            }
            for(int edge=0;edge<4;edge++) {
                int next=(edge+1)%4;
                float nx=v[next][1]-v[edge][1],ny=v[edge][0]-v[next][0];
                float length=(float)Math.sqrt(nx*nx+ny*ny);
                if(length==0) continue;
                nx/=length;ny/=length;
                vertex(p,b,v[edge][0],v[edge][1],front,nx,ny,0,color,light);
                vertex(p,b,v[next][0],v[next][1],front,nx,ny,0,color,light);
                vertex(p,b,v[next][0],v[next][1],back,nx,ny,0,color,light);
                vertex(p,b,v[edge][0],v[edge][1],back,nx,ny,0,color,light);
            }
        }
    }
    private static void vertex(PoseStack p,VertexConsumer b,float x,float y,float z,
                               float nx,float ny,float nz,int color,int light) {
        b.vertex(p.last().pose(),x,y,z).color(CasinoPalette.red(color),CasinoPalette.green(color),CasinoPalette.blue(color),1)
                .uv(0,0).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(p.last().normal(),nx,ny,nz).endVertex();
    }
}
