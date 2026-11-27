package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tnc.tnc.magic.water.WaterPrisonShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Four-rank Abyss. Its shell, broken surf and floor engraving share the water transparency pass. */
final class AbyssDome extends RenderType {
    private static final double TAU = Math.PI * 2;
    private static final int SIDES = 64, ROWS = 20, STRIDE = SIDES+1, COUNT = (ROWS+1)*STRIDE;
    static final RenderType FILM = material("undertow",131072), SURF = material("breakers",65536),
            SEAL = material("tide_seal",4096), BUBBLE=material("bubble",8192);
    // Entity rendering copies these values into the vertex buffer synchronously on the render thread.
    private static final float[] X=new float[COUNT], Y=new float[COUNT], Z=new float[COUNT],
            U=new float[COUNT], V=new float[COUNT], A=new float[COUNT], edge=new float[COUNT];
    private static final double[] cos=new double[STRIDE], sin=new double[STRIDE],
            width=new double[ROWS+1], elevation=new double[ROWS+1], normalY=new double[ROWS+1];
    private static final double BOTTOM=-.69, LOW=Math.sin(BOTTOM), SWEEP=Math.PI/2-BOTTOM;
    static {
        for(int s=0;s<=SIDES;s++){cos[s]=Math.cos(s*TAU/SIDES);sin[s]=Math.sin(s*TAU/SIDES);}
        for(int j=0;j<=ROWS;j++){
            double lat=BOTTOM+SWEEP*j/ROWS;
            width[j]=Math.cos(lat); normalY[j]=Math.sin(lat);
            elevation[j]=(normalY[j]-LOW)/(1-LOW);
        }
    }
    private AbyssDome(){super("tnc_abyss",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
    private static RenderType material(String name,int size){
        return create("tnc_abyss_"+name,DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,size,false,true,
                CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("tnc","textures/effects/abyss/"+name+".png"),true,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
    }

    static void render(PoseStack stack, MultiBufferSource buffers, double radius, double fieldHeight,
                       double age, float fade, Vec3 eye) {
        if(radius<.1 || fade<=0)return;
        float time=(float)age;
        double rise=WaterPrisonShape.rise(age);
        double height=WaterPrisonShape.domeHeight(radius,fieldHeight)*(.08+.92*rise);
        double r=radius*(.78+.22*rise);
        Matrix4f m=stack.last().pose();
        floor(buffers.getBuffer(SEAL),m,r*1.26,time,fade);
        surface(buffers,m,r,height,time,fade,eye);
        var surf=buffers.getBuffer(SURF);
        breakingFoot(surf,m,r,height,time,fade);
        for(int lane=0;lane<4;lane++)undertow(surf,m,r,height,time,fade,lane);
        orbit(surf,m,r,height,time,fade,eye);
        droplets(buffers.getBuffer(BUBBLE),m,r,height,time,fade,eye);
    }
    private static void floor(VertexConsumer out,Matrix4f m,double radius,float t,float opacity) {
        double a=t*.0016, c=radius*Math.cos(a),s=radius*Math.sin(a);
        tex(out,m,-c+s,.105,-s-c,0,0,opacity);
        tex(out,m,c+s,.105,s-c,1,0,opacity);
        tex(out,m,c-s,.105,s+c,1,1,opacity);
        tex(out,m,-c-s,.105,-s+c,0,1,opacity);
    }
    private static void surface(MultiBufferSource buffers,Matrix4f m,double r,double h,float t,float opacity,Vec3 eye){
        double shellR=r*.92, vertical=h/(1-LOW), aspect=shellR/vertical;
        double ex=eye.x,ey=eye.y,ez=eye.z;
        boolean inside=(ex*ex+ez*ez)/(shellR*shellR)+Math.pow((ey+LOW*vertical)/vertical,2)<1;
        for(int j=0;j<=ROWS;j++){
            double row=j/(double)ROWS, ring=width[j],ny=normalY[j]*aspect;
            double nlen=1/Math.sqrt(ny*ny+ring*ring);
            for(int s=0;s<=SIDES;s++){
                int i=j*STRIDE+s;
                double theta=TAU*s/SIDES;
                double undulation=1+ring*(.007*Math.sin(theta*3+row*8-t*.046)+.004*Math.sin(theta*7-row*9+t*.051));
                double rr=shellR*ring*undulation;
                float x=(float)(rr*cos[s]),z=(float)(rr*sin[s]);
                float y=(float)(.17+h*elevation[j]+r*.005*ring*Math.sin(theta*5+t*.047+row*9));
                X[i]=x;Y[i]=y;Z[i]=z;
                double dx=ex-x,dy=ey-y,dz=ez-z,dist=Math.sqrt(dx*dx+dy*dy+dz*dz);
                double dot=(ring*cos[s]*dx+ny*dy+ring*sin[s]*dz)*nlen/Math.max(.01,dist);
                double grazing=1-Math.min(1,Math.abs(dot)), rim=grazing*grazing*grazing;
                double near=Math.min(1,Math.max(0,(dist-.8)/2));
                A[i]=(float)(opacity*near*(inside?.41:dot<0?.46:1)*(.46+.42*rim+.17*row*row));
                edge[i]=(float)(opacity*near*(inside?.35:1)*rim);
                U[i]=(float)(3.0*s/SIDES+row*.48+t*.00092+.040*Math.sin(theta*2+row*6-t*.036)*ring);
                V[i]=(float)(row*1.12-t*.0017+.050*Math.sin(theta*3+row*4+t*.024)*ring);
            }
        }
        var film=buffers.getBuffer(FILM);
        for(int row=0;row<ROWS;row++)for(int s=0;s<SIDES;s++){
            int i=row*STRIDE+s;
            filmVertex(film,m,i);filmVertex(film,m,i+1);filmVertex(film,m,i+STRIDE+1);filmVertex(film,m,i+STRIDE);
        }
        var shimmer=buffers.getBuffer(WaterRenderTypes.glow());
        for(int row=0;row<ROWS;row++)for(int s=0;s<SIDES;s++){
            int i=row*STRIDE+s;
            if(Math.max(Math.max(edge[i],edge[i+1]),Math.max(edge[i+STRIDE],edge[i+STRIDE+1]))<.040F)continue;
            rimVertex(shimmer,m,i);rimVertex(shimmer,m,i+1);rimVertex(shimmer,m,i+STRIDE+1);rimVertex(shimmer,m,i+STRIDE);
        }
    }
    private static void filmVertex(VertexConsumer out,Matrix4f m,int i){
        tex(out,m,X[i],Y[i],Z[i],U[i],V[i],A[i]);
    }
    private static void rimVertex(VertexConsumer out,Matrix4f m,int i){
        out.vertex(m,X[i],Y[i],Z[i]).color(.47F,.88F,1F,edge[i]*.77F).endVertex();
    }
    private static void tex(VertexConsumer out,Matrix4f m,double x,double y,double z,double u,double v,double alpha){
        out.vertex(m,(float)x,(float)y,(float)z).color(255,255,255,(int)(Math.min(1,Math.max(0,alpha))*255))
                .uv((float)u,(float)v).endVertex();
    }

    // The breaking sheet climbs from a wider footprint and folds inward toward the globe.
    private static void breakingFoot(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity){
        for(int s=0;s<SIDES;s++){
            footVertex(out,m,r,h,t,opacity,s,false);footVertex(out,m,r,h,t,opacity,s+1,false);
            footVertex(out,m,r,h,t,opacity,s+1,true);footVertex(out,m,r,h,t,opacity,s,true);
        }
    }
    private static void footVertex(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity,int s,boolean upper){
        double a=s*TAU/SIDES, swirl=.045*Math.sin(3*a-t*.037)+.031*Math.sin(7*a+t*.061);
        double lobe=.5+.5*Math.cos(2*(a-t*.003));
        lobe=lobe*lobe;
        double rad=r*(upper?.75+swirl*.38:.91+.13*lobe+swirl);
        double lift=upper?h*(.105+.29*lobe+.046*Math.sin(5*a-t*.032))+.22:.12;
        double az=a+(upper?.14+.12*Math.sin(a+t*.009):0);
        tex(out,m,rad*Math.cos(az),lift,rad*Math.sin(az),s*1.0/SIDES-t*.0021,upper?.007:.994,opacity);
    }

    // Tapered ribbons climb inside the globe and break apart into whitewater at their edges.
    private static void undertow(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity,int lane){
        final int sections=28;
        for(int k=0;k<sections;k++){
            swellVertex(out,m,r,h,t,opacity,lane,k/(double)sections,false);
            swellVertex(out,m,r,h,t,opacity,lane,(k+1)/(double)sections,false);
            swellVertex(out,m,r,h,t,opacity,lane,(k+1)/(double)sections,true);
            swellVertex(out,m,r,h,t,opacity,lane,k/(double)sections,true);
        }
    }
    private static void swellVertex(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity,int lane,double q,boolean lip){
        double envelope=Math.sin(q*Math.PI);
        double a=lane*TAU/4+t*.0038+q*(.91+(lane%2)*.46)+.15*Math.sin(q*TAU+t*.027+lane);
        double lat=BOTTOM+.05+q*(lane%2==0?1.36:.93);
        if(lip)lat+=envelope*(.10+.066*Math.sin(q*8+lane+t*.045));
        double rad=r*(.90-.23*q+.036*Math.sin(lane+q*8-t*.03))*Math.cos(lat);
        double y=.19+h*(Math.sin(lat)-LOW)/(1-LOW);
        tex(out,m,rad*Math.cos(a),y,rad*Math.sin(a),lane*.249+q*.62-t*.0035,lip?.08:.97,
                opacity*envelope*.85);
    }
    private static void orbit(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity,Vec3 eye){
        for(int s=0;s<2*SIDES;s++){
            orbitVertex(out,m,r,h,t,opacity,s,-1,eye);orbitVertex(out,m,r,h,t,opacity,s+1,-1,eye);
            orbitVertex(out,m,r,h,t,opacity,s+1,1,eye);orbitVertex(out,m,r,h,t,opacity,s,1,eye);
        }
    }
    private static void orbitVertex(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity,int s,int side,Vec3 eye){
        double a=s*TAU/(SIDES*2);
        double spark=Math.max(.06,.52+.33*Math.sin(3*a+t*.039)+.21*Math.sin(8*a-t*.043));
        double rad=r*(1.067+.017*Math.sin(3*a+t*.026))+.035*side;
        double y=h*.435+r*.092*Math.sin(a+.7+t*.004)+r*.021*Math.sin(2*a-t*.02)+side*r*(.002+.005*spark);
        double x=rad*Math.cos(a),z=rad*Math.sin(a);
        double near=Math.min(1,eye.distanceToSqr(x,y,z)/25);
        tex(out,m,x,y,z,a/TAU*3-t*.0036,side>0?.42:.98,opacity*spark*near);
    }
    private static void droplets(VertexConsumer out,Matrix4f m,double r,double h,float t,float opacity,Vec3 eye){
        var look=eye.subtract(0,h*.43,0).normalize();
        var right=new Vec3(-look.z,0,look.x).normalize();
        if(right.lengthSqr()<.01)right=new Vec3(1,0,0);
        var up=right.cross(look).normalize();
        for(int k=0;k<28;k++){
            double phase=(k*.618034+t*(.0055+(k%4)*.0006))%1, a=k*2.39996+phase*.24+t*.0019;
            double rr=r*(.93+.13*Math.sin(phase*Math.PI)+.06*Math.sin(k*7.1));
            double x=rr*Math.cos(a),z=rr*Math.sin(a),y=.18+h*(k%4==0?.27:.04)+h*.21*Math.sin(phase*Math.PI);
            double size=r*(.004+.008*Math.pow(.5+.5*Math.cos(k*.8),2));
            double alpha=opacity*Math.sin(phase*Math.PI);
            bubbleVertex(out,m,x,y,z,right,up,-size,-size,0,1,alpha);
            bubbleVertex(out,m,x,y,z,right,up,size,-size,1,1,alpha);
            bubbleVertex(out,m,x,y,z,right,up,size,size,1,0,alpha);
            bubbleVertex(out,m,x,y,z,right,up,-size,size,0,0,alpha);
        }
    }
    private static void bubbleVertex(VertexConsumer out,Matrix4f m,double x,double y,double z,Vec3 right,Vec3 up,double c,double s,float u,float v,double alpha){
        tex(out,m,x+c*right.x+s*up.x,y+s*up.y,z+c*right.z+s*up.z,u,v,alpha);
    }
}
