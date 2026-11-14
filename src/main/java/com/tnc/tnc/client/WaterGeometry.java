package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.tnc.tnc.magic.water.WaterSpellRules;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;

/** Continuous geometry rather than a cloud of disconnected particles. */
final class WaterGeometry {
    private WaterGeometry() {}
    private static final Vec3 UP=new Vec3(0,1,0);
    /** Pearl, cyan and restrained gold: the same palette as the tide seal in the spell docs. */
    private static final float WR=.78F,WG=.96F,WB=1.0F;
    private static final float CR=.26F,CG=.78F,CB=1.0F;
    private static final float GR=.95F,GG=.74F,GB=.30F;
    private static final float D_R=.02F,D_G=.10F,D_B=.28F;
    static void vertex(VertexConsumer out,Matrix4f pose,Vec3 p,float r,float g,float b,float a) {
        // Highlights frequently add to their base tint. Clamp before float colors become bytes,
        // otherwise values above one wrap and a bright water seam becomes a dark wrong color.
        out.vertex(pose,(float)p.x,(float)p.y,(float)p.z).color(
                net.minecraft.util.Mth.clamp(r,0,1),net.minecraft.util.Mth.clamp(g,0,1),
                net.minecraft.util.Mth.clamp(b,0,1),net.minecraft.util.Mth.clamp(a,0,1)).endVertex();
    }
    static void quad(VertexConsumer out,Matrix4f pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha) {
        vertex(out,pose,a,r,g,blue,alpha);vertex(out,pose,b,r,g,blue,alpha);
        vertex(out,pose,c,r,g,blue,alpha);vertex(out,pose,d,r,g,blue,alpha);
    }
    static Vec3 radial(Vec3 right,Vec3 up,double angle,double radius) {
        return right.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));
    }
    /** 绕飞行轴滚转 roll 弧度后的刃面基 ✓（right,up,dir 本就是正交基 ⇒ 就是 Rodrigues ✗）。
        不加这一层的话，水平飞行的刃 up 恒等于世界竖直 ⇒ 8 片刃全是竖着的 C ✗。 */
    static Vec3[] rolledBasis(Vec3 dir,double roll) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        double c=Math.cos(roll),s=Math.sin(roll);
        return new Vec3[]{right.scale(c).add(up.scale(s)),up.scale(c).subtract(right.scale(s))};
    }
    static void tube(VertexConsumer out,Matrix4f pose,Vec3 start,Vec3 end,double radius,float r,float g,float b,float a) {
        // 段数按半径给 ✗：细线 4 边就够 ✓（24 边＝96 顶点 ✗，而它只有 0.1 格粗 ✗ ⇒ 纯属白烧 ✗）
        tube(out,pose,start,end,radius,r,g,b,a,radius<.1?4:24);
    }
    /**
     * 同上，但**边数可调** ✓ —— 第十批加的 ✗。
     *
     * <p>⚠️ 这个重载是为了修一个很隐蔽的坑 ✗：原来的判据是 {@code radius<.1?4:24} ✗，
     * 所以 **`radius=.10` 这种"看起来是细线"的写法会走 24 边＝96 顶点** ✗ ——
     * 灭世之海的光晕层就是这么被算成 38,400 顶点的 ✗。要画细线就显式给 4～6 边 ✓。
     */
    static void tube(VertexConsumer out,Matrix4f pose,Vec3 start,Vec3 end,double radius,float r,float g,float b,float a,int sides) {
        Vec3 axis=end.subtract(start).normalize(); if(axis.lengthSqr()<.5)return;
        Vec3 right=WaterSpellRules.right(axis),up=right.cross(axis).normalize();
        for(int i=0;i<sides;i++) {
            Vec3 v=radial(right,up,i*Math.PI*2/sides,radius),w=radial(right,up,(i+1)*Math.PI*2/sides,radius);
            quad(out,pose,start.add(v),end.add(v),end.add(w),start.add(w),r,g,b,a);
        }
    }
    /** Filled aperture, encoded as degenerate quads for the shared QUADS / NO_CULL buffer. */
    static void disk(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,float r,float g,float b,float alpha) {
        disk(out,pose,center,dir,radius,r,g,b,alpha,64);
    }
    /** 段数可调版 ✗（64 段＝256 顶点 ✗；一次施法里光面常常要画好几层 ✗）。 */
    static void disk(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,float r,float g,float b,float alpha,int segments) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<segments;i++) {
            Vec3 p=center.add(radial(right,up,i*Math.PI*2/segments,radius));
            Vec3 q=center.add(radial(right,up,(i+1)*Math.PI*2/segments,radius));
            quad(out,pose,center,p,q,center,r,g,b,alpha);
        }
    }
    static void ring(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,double phase,float alpha) {
        ring(out,pose,center,dir,radius,width,phase,alpha,96);
    }
    /** 段数可调版 ✗（96 段＝384 顶点 ✗ —— 长光束上每 10 格一环 ✗，一画就是十几圈 ✗）。 */
    static void ring(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,double phase,float alpha,int segments) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<segments;i++) {
            double a=phase+i*Math.PI*2/segments,b=phase+(i+1)*Math.PI*2/segments;
            quad(out,pose,center.add(radial(right,up,a,radius)),center.add(radial(right,up,b,radius)),
                    center.add(radial(right,up,b,radius-width)),center.add(radial(right,up,a,radius-width)),.015F,.32F,1,alpha);
        }
    }
    static void circle(VertexConsumer out,Matrix4f pose,Vec3 dir,double radius,double time,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        ring(out,pose,Vec3.ZERO,dir,radius,.055,0,alpha);
        ring(out,pose,Vec3.ZERO,dir,radius*.92,.035,0,alpha);
        ring(out,pose,dir.scale(.025),dir,radius*.65,.05,0,alpha);
        ring(out,pose,Vec3.ZERO,dir,radius*.25,.035,0,alpha);
        // Twelve moving angular glyphs, and a counter-rotating inner star.
        for(int i=0;i<12;i++) {
            double a=time*.009+i*Math.PI/6;
            Vec3 center=radial(right,up,a,radius*.79);
            Vec3 v=radial(right,up,a,radius*.055),w=radial(right,up,a+Math.PI/2,radius*.045);
            tube(out,pose,center.subtract(v),center.add(w),.025,.7F,.94F,1,alpha);
            tube(out,pose,center.add(w),center.add(v),.025,.7F,.94F,1,alpha);
            double star=-time*.012+i*Math.PI/6;
            tube(out,pose,radial(right,up,star,radius*.6),radial(right,up,star+Math.PI*2/3,radius*.6),.022,.25F,.7F,1,alpha*.65F);
        }
    }
    static void ringColor(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,
                          float r,float g,float b,float alpha) {
        ringColor(out,pose,center,dir,radius,width,r,g,b,alpha,64);
    }
    /** 同上，但**段数可调** ✓ —— 大半径的亮环不需要 64 段 ✗（每段 4 顶点 ✗：16 格半径的环用 64 段＝256 顶点 ✗，40 段＝160 ✓）。 */
    static void ringColor(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double width,
                          float r,float g,float b,float alpha,int segments) {
        if(segments<=0)return;
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        double[][] samples=CIRCLE_SAMPLES.computeIfAbsent(segments,n -> {
            double[][] values=new double[2][n+1];
            for(int i=0;i<=n;i++) {
                double angle=i*Math.PI*2/n;
                values[0][i]=Math.cos(angle);values[1][i]=Math.sin(angle);
            }
            return values;
        });
        float cr=net.minecraft.util.Mth.clamp(r,0,1),cg=net.minecraft.util.Mth.clamp(g,0,1);
        float cb=net.minecraft.util.Mth.clamp(b,0,1),ca=net.minecraft.util.Mth.clamp(alpha,0,1);
        double inner=radius-width;
        for(int i=0;i<segments;i++) {
            ringVertex(out,pose,center,right,up,samples[0][i],samples[1][i],radius,cr,cg,cb,ca);
            ringVertex(out,pose,center,right,up,samples[0][i+1],samples[1][i+1],radius,cr,cg,cb,ca);
            ringVertex(out,pose,center,right,up,samples[0][i+1],samples[1][i+1],inner,cr,cg,cb,ca);
            ringVertex(out,pose,center,right,up,samples[0][i],samples[1][i],inner,cr,cg,cb,ca);
        }
    }
    private static final java.util.Map<Integer,double[][]> CIRCLE_SAMPLES=new java.util.concurrent.ConcurrentHashMap<>();
    private static void ringVertex(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 right,Vec3 up,
                                   double cos,double sin,double radius,float r,float g,float b,float a) {
        out.vertex(pose,(float)(center.x+radius*(right.x*cos+up.x*sin)),
                (float)(center.y+radius*(right.y*cos+up.y*sin)),
                (float)(center.z+radius*(right.z*cos+up.z*sin))).color(r,g,b,a).endVertex();
    }
    /**
     * 面内的一条**平亮笔划** ✓（从 a 到 b，宽度在 {@code dir} 的垂直平面里 ✗）—— 4 顶点 ✓。
     *
     * <p><b>这是第十批最重要的一件工具</b> ✗：以前"画一条线"的做法是 {@code tube}（24 边 ✗＝96 顶点 ✗），
     * 于是一座法阵的 12 个符文 × 7 根管就是 8k 顶点 ✗、六道浪的浪唇泡沫又是 5 万 ✗。
     * 换成这个之后，同样的观感只要 4 顶点 ✓（实测见 {@code WaterVisualsCostTest} ✗）。
     */
    static void stroke(VertexConsumer out,Matrix4f pose,Vec3 dir,Vec3 a,Vec3 q,double halfWidth,
                       float r,float g,float blue,float alpha) {
        Vec3 axis=q.subtract(a);
        if(axis.lengthSqr()<1e-9)return;
        Vec3 across=axis.normalize().cross(dir);
        if(across.lengthSqr()<1e-9)across=WaterSpellRules.right(dir);
        across=across.normalize().scale(halfWidth);
        quad(out,pose,a.subtract(across),a.add(across),q.add(across),q.subtract(across),r,g,blue,alpha);
    }
    /**
     * Layered tide seal. Body is alpha-blended deep blue/cyan; the bright edges go through the
     * deferred emission layer, so the seal reads as water rather than glowing plastic.
     *
     * <p>Tier 1–2 is deliberately compact (rings, short wave glyphs, four crests, a central
     * drain). Tier 3 adds the crescent and two more crests; tier 4–5 add the outer whirlpool
     * arms and the wider crest ring. The old textured seal model is still available for the
     * model audit, but gameplay circles no longer use it.
     */
    static void seal(VertexConsumer out,VertexConsumer glow,Matrix4f pose,Vec3 center,Vec3 dir,
                     double radius,double time,float alpha,int tier) {
        if(radius<=1e-4||alpha<=1e-4)return;
        int t=Math.max(1,Math.min(5,tier));
        Vec3 axis=dir.lengthSqr()<1e-9?UP:dir.normalize();
        Vec3 right=WaterSpellRules.right(axis),up=right.cross(axis).normalize();
        boolean rich=t>=3;
        double spin=time*.012;
        int glyphs=t<=2?8:12;
        int crests=t<=2?4:t==3?6:8;
        int arms=t>=5?4:t==4?3:0;

        // Soft body wash: keep it weak, otherwise the world underneath turns into blue concrete.
        WaterGeometry.disk(out,pose,center.add(axis.scale(.002)),axis,radius*.99,
                .02F,.10F,.28F,alpha*.075F,48);
        WaterGeometry.disk(glow,pose,center.add(axis.scale(.003)),axis,radius*.94,
                .04F,.26F,.58F,alpha*.13F,40);
        WaterGeometry.disk(glow,pose,center.add(axis.scale(.005)),axis,radius*.42,
                .12F,.48F,.78F,alpha*.16F,24);

        // Concentric tide rings.
        ringColor(out,pose,center,axis,radius*.995,radius*.011,WR,WG,WB,alpha*.88F,64);
        ringColor(glow,pose,center,axis,radius*.995,radius*.007,WR,WG,WB,alpha*.55F,48);
        ringColor(out,pose,center,axis,radius*1.045,radius*.0028,GR,GG,GB,alpha*.30F,40);
        ringColor(out,pose,center,axis,radius*.855,radius*.0050,CR,CG,CB,alpha*.55F,44);
        ringColor(glow,pose,center,axis,radius*.855,radius*.0035,CR,CG,CB,alpha*.35F,40);
        ringColor(out,pose,center,axis,radius*.58,radius*.0042,WR,WG,WB,alpha*.30F,36);

        // Short wave crests around the outer band: three strokes each, not a cross/rune.
        for(int i=0;i<glyphs;i++) {
            double a=spin+i*Math.PI*2/glyphs;
            sealGlyph(out,glow,pose,center,right,up,axis,a,radius*.895,radius*.058,
                    alpha*(i%3==0?.78F:.55F));
        }

        // Crescent: the tide moon. Low tiers keep only the concentric rings.
        if(rich) sealCrescent(out,glow,pose,center,right,up,axis,radius,spin*.55,alpha);

        // Partial ripple arcs inside the crescent.
        for(int k=0;k<(rich?3:2);k++) {
            double rr=radius*(.72-k*.075),a0=spin*(.35+k*.4)+k*1.7,span=Math.PI*.72;
            sealArc(out,glow,pose,center,right,up,axis,rr,a0,a0+span,12,
                    k==0?GR:WR,k==0?GG:WG,k==0?GB:WB,alpha*(.18F+k*.03F));
        }

        // Whirlpool arms: tier 4–5 only. Low tier stays a clean seal, not a vortex.
        for(int i=0;i<arms;i++) {
            double base=spin*.8+i*Math.PI*2/arms+Math.PI/8;
            sealArm(out,glow,pose,center,right,up,axis,base,radius,alpha);
        }

        // Crest blades around the rim: short alternating fins, thicker than a line.
        for(int i=0;i<crests;i++) {
            double a=spin*1.1+i*Math.PI*2/crests+Math.PI/8;
            sealCrest(out,glow,pose,center,right,up,axis,a,radius,i%2==0,
                    alpha*(.26F+.10F*(i%2)));
        }

        // Central whirlpool: a draining spiral, not the old teardrop/eye.
        sealVortex(out,glow,pose,center,right,up,axis,radius,time,alpha,rich);
    }

    private static Vec3 sealPoint(Vec3 center,Vec3 right,Vec3 up,Vec3 axis,
                                  double angle,double radius,double lift) {
        return center.add(right.scale(Math.cos(angle)*radius))
                .add(up.scale(Math.sin(angle)*radius)).add(axis.scale(lift));
    }

    private static void sealGlyph(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                                  Vec3 center,Vec3 right,Vec3 up,Vec3 axis,double angle,
                                  double radius,double size,float alpha) {
        for(int k=0;k<2;k++) {
            double off=(k==0?-.012D:.012D);
            Vec3 a=sealPoint(center,right,up,axis,angle-.070D,radius-off,.004D);
            Vec3 b=sealPoint(center,right,up,axis,angle-.024D,radius+size*.55D-off,.004D);
            Vec3 c=sealPoint(center,right,up,axis,angle+.024D,radius+size*.55D-off,.004D);
            Vec3 d=sealPoint(center,right,up,axis,angle+.070D,radius-off,.004D);
            float ar=(k==0?GR:CR),ag=(k==0?GG:CG),ab=(k==0?GB:CB);
            stroke(out,pose,axis,a,b,size*.10D,ar,ag,ab,alpha);
            stroke(out,pose,axis,b,c,size*.10D,ar,ag,ab,alpha*.92F);
            stroke(out,pose,axis,c,d,size*.10D,ar,ag,ab,alpha*.84F);
            stroke(glow,pose,axis,a,d,size*.025D,WR,WG,WB,alpha*.42F);
        }
    }

    private static void sealCrescent(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                                     Vec3 center,Vec3 right,Vec3 up,Vec3 axis,
                                     double radius,double angle,float alpha) {
        int seg=18;
        double mid=radius*.465D;
        Vec3 prevO=null,prevI=null;
        for(int i=0;i<=seg;i++) {
            double u=i/(double)seg;
            double taper=Math.sin(Math.PI*u);
            double outer=mid+radius*.045D*taper,inner=mid-radius*.045D*taper;
            double a=angle-Math.PI*.69D+i*Math.PI*1.38D/seg;
            Vec3 po=sealPoint(center,right,up,axis,a,outer,.008D);
            Vec3 pi=sealPoint(center,right,up,axis,a,inner,.008D);
            if(prevO!=null) quad(out,pose,prevI,prevO,po,pi,CR,CG,CB,alpha*.26F);
            prevO=po;prevI=pi;
        }
        for(int edge=0;edge<2;edge++) {
            for(int i=0;i<seg;i++) {
                double u0=i/(double)seg,u1=(i+1)/(double)seg;
                double t0=Math.sin(Math.PI*u0),t1=Math.sin(Math.PI*u1);
                double rr0=mid+(edge==0?radius*.045D*t0:-radius*.045D*t0);
                double rr1=mid+(edge==0?radius*.045D*t1:-radius*.045D*t1);
                double a0=angle-Math.PI*.69D+i*Math.PI*1.38D/seg;
                double a1=angle-Math.PI*.69D+(i+1)*Math.PI*1.38D/seg;
                Vec3 p=sealPoint(center,right,up,axis,a0,rr0,.010D);
                Vec3 q=sealPoint(center,right,up,axis,a1,rr1,.010D);
                float w=(float)(radius*(edge==0?.0045D:.0035D)*(.55D+.45D*t0));
                stroke(out,pose,axis,p,q,Math.max(radius*.0018D,w),edge==0?WR:CR,
                        edge==0?WG:CG,edge==0?WB:CB,alpha*(edge==0?.82F:.55F));
                if(edge==0)stroke(glow,pose,axis,p,q,radius*.0025D,WR,WG,WB,alpha*.40F);
            }
        }
    }

    private static void sealArc(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                                Vec3 center,Vec3 right,Vec3 up,Vec3 axis,
                                double radius,double a0,double a1,int seg,
                                float r,float g,float b,float alpha) {
        for(int i=0;i<seg;i++) {
            double t0=a0+(a1-a0)*i/seg,t1=a0+(a1-a0)*(i+1)/seg;
            Vec3 p=sealPoint(center,right,up,axis,t0,radius,.006D);
            Vec3 q=sealPoint(center,right,up,axis,t1,radius,.006D);
            stroke(out,pose,axis,p,q,radius*.0050D,r,g,b,alpha);
            if(i%3==0)stroke(glow,pose,axis,p,q,radius*.0022D,WR,WG,WB,alpha*.40F);
        }
    }

    private static void sealArm(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                                Vec3 center,Vec3 right,Vec3 up,Vec3 axis,
                                double base,double radius,float alpha) {
        int steps=7;
        Vec3 prev=null,prevSide=null,prevP=null;
        for(int j=0;j<=steps;j++) {
            double t=j/(double)steps;
            double a=base+t*1.18D;
            double rr=radius*(.27D+.65D*t);
            Vec3 p=sealPoint(center,right,up,axis,a,rr,.010D);
            Vec3 tangent=right.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
            Vec3 side=tangent.scale(radius*.020D*(1.0D-.45D*t));
            if(prev!=null) {
                quad(out,pose,prev.add(prevSide),prev.subtract(prevSide),
                        p.subtract(side),p.add(side),CR,CG,CB,alpha*.20F);
                stroke(glow,pose,axis,prevP,p,radius*.0024D,CR,CG,CB,alpha*.32F);
            }
            prev=p;prevSide=side;prevP=p;
        }
    }

    private static void sealCrest(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                                  Vec3 center,Vec3 right,Vec3 up,Vec3 axis,
                                  double angle,double radius,boolean longCrest,float alpha) {
        double r0=radius*.855D,r1=radius*.975D,r2=radius*(longCrest?1.055D:1.005D);
        Vec3 base=sealPoint(center,right,up,axis,angle,r0,.006D);
        Vec3 mid=sealPoint(center,right,up,axis,angle+.020D,r1,.006D);
        Vec3 tip=sealPoint(center,right,up,axis,angle+.042D,r2,.006D);
        Vec3 tan0=right.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle)));
        Vec3 tan1=right.scale(-Math.sin(angle+.020D)).add(up.scale(Math.cos(angle+.020D)));
        Vec3 tan2=right.scale(-Math.sin(angle+.042D)).add(up.scale(Math.cos(angle+.042D)));
        Vec3 b0=tan0.scale(radius*.044D),b1=tan1.scale(radius*.026D),b2=tan2.scale(radius*.010D);
        quad(out,pose,base.add(b0),base.subtract(b0),mid.subtract(b1),mid.add(b1),
                CR,CG,CB,alpha*.56F);
        quad(out,pose,mid.add(b1),mid.subtract(b1),tip.subtract(b2),tip.add(b2),
                CR,CG,CB,alpha*.48F);
        stroke(out,pose,axis,base.add(b0),tip.add(b2),radius*.0038D,WR,WG,WB,alpha*.74F);
        stroke(glow,pose,axis,base.add(b0),tip.add(b2),radius*.0024D,WR,WG,WB,alpha*.48F);
    }

    /**
     * Central drain: three broad spiral bands, four tight filaments, broken rim arcs and an
     * open comma core. There is deliberately no closed ring around a solid centre — the old
     * teardrop + inner ring + bright dot read as an eye from above.
     */
    private static void sealVortex(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                                   Vec3 center,Vec3 right,Vec3 up,Vec3 axis,
                                   double radius,double time,float alpha,boolean rich) {
        Vec3 origin=center.add(axis.scale(.010D));
        double spin=time*.032D;
        int arms=3;
        int armSteps=rich?14:11;
        int threads=rich?4:3;
        int threadSteps=rich?13:10;

        // A shallow dark basin, then a faint blue upwelling. No outline, so it cannot read as a pupil.
        disk(out,pose,origin,axis,radius*.225D,.012F,.050F,.20F,alpha*.22F,24);
        disk(glow,pose,origin.add(axis.scale(.002D)),axis,radius*.175D,
                .03F,.16F,.42F,alpha*.14F,20);

        // Broad translucent bands: the body of the whirlpool.
        for(int arm=0;arm<arms;arm++) {
            double base=spin+arm*Math.PI*2.0D/arms;
            Vec3 prev=null,prevSide=null;
            for(int j=0;j<=armSteps;j++) {
                double t=j/(double)armSteps;
                double rr=radius*(.30D-.255D*t);
                double a=base+t*(2.25D+.15D*(rich?1:0));
                Vec3 p=sealPoint(center,right,up,axis,a,rr,.004D+.016D*(1.0D-t));
                Vec3 tangent=right.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
                Vec3 side=tangent.scale(radius*(.028D*(1.0D-t)+.003D));
                if(prev!=null) {
                    quad(out,pose,prev.subtract(prevSide),prev.add(prevSide),
                            p.add(side),p.subtract(side),CR,CG,CB,
                            alpha*(.12F+.025F*(rich?1F:0F))*(1F-.25F*(float)t));
                    stroke(glow,pose,axis,prev.add(prevSide),p.add(side),radius*.0016D,
                            CR,CG,CB,alpha*.18F);
                }
                prev=p;prevSide=side;
            }
        }

        // Tight bright filaments: they make the rotation legible at a distance.
        for(int k=0;k<threads;k++) {
            double base=spin*1.65D+k*Math.PI*2.0D/threads;
            Vec3 prev=null;
            for(int j=0;j<=threadSteps;j++) {
                double t=j/(double)threadSteps;
                double rr=radius*(.285D-.260D*t);
                double a=base+t*(3.35D+.25D*(rich?1:0));
                Vec3 p=sealPoint(center,right,up,axis,a,rr,.012D);
                if(prev!=null) stroke(glow,pose,axis,prev,p,
                        (float)(radius*(.0036D*(1.0D-t)+.0011D)),
                        k%2==0?WR:CR,k%2==0?WG:CG,k%2==0?WB:CB,
                        alpha*(.38F-.18F*(float)t));
                prev=p;
            }
        }

        // Broken rim currents, not a full iris ring.
        for(int k=0;k<3;k++) {
            double rr=radius*(.245D-.040D*k);
            double a0=spin*1.6D+k*2.0D;
            sealArc(out,glow,pose,center,right,up,axis,rr,a0,a0+1.45D,7,
                    k==1?GR:CR,k==1?GG:CG,k==1?GB:CB,alpha*.16F);
        }

        // Open comma core: two short arcs ending off-centre, so the centre is a drain, not a pupil.
        for(int k=0;k<2;k++) {
            double base=spin*2.1D+k*Math.PI;
            Vec3 prev=null;
            for(int j=0;j<=8;j++) {
                double t=j/8.0D;
                double rr=radius*(.085D-.070D*t);
                double a=base+t*2.5D;
                Vec3 p=sealPoint(center,right,up,axis,a,rr,.014D);
                if(prev!=null) stroke(glow,pose,axis,prev,p,
                        (float)(radius*(.005D*(1.0D-t)+.001D)),
                        k==0?WR:CR,k==0?WG:CG,k==0?WB:CB,
                        alpha*(.55F-.25F*(float)t));
                prev=p;
            }
        }

        // The actual drain + a few foam specks caught in the current.
        disk(out,pose,origin.add(axis.scale(.002D)),axis,radius*.040D,
                .006F,.025F,.10F,alpha*.50F,12);
        disk(glow,pose,origin.add(axis.scale(.004D)),axis,radius*.011D,
                .82F,.98F,1,alpha*.85F,8);
        for(int k=0;k<(rich?3:2);k++) {
            double a=spin*1.2D+k*Math.PI*.55D;
            double rr=radius*(.17D-.035D*k);
            disk(out,pose,sealPoint(center,right,up,axis,a,rr,.016D),axis,radius*.006D,
                    WR,WG,WB,alpha*.30F,6);
        }
    }

    static void sealSatellite(VertexConsumer out,VertexConsumer glow,Matrix4f pose,
                              Vec3 center,Vec3 dir,double radius,double time,float alpha) {
        Vec3 axis=dir.lengthSqr()<1e-9?UP:dir.normalize();
        ringColor(out,pose,center,axis,radius,radius*.055,WR,WG,WB,alpha*.72F,12);
        ringColor(glow,pose,center,axis,radius,radius*.070,CR,CG,CB,alpha*.42F,10);
        ringColor(out,pose,center,axis,radius*.55,radius*.045,GR,GG,GB,alpha*.55F,8);
        disk(glow,pose,center.add(axis.scale(.004)),axis,radius*.18,
                .75F,.96F,1,alpha*.72F,6);
    }

    static void richCircle(MultiBufferSource buffers,PoseStack stack,Vec3 center,Vec3 dir,double radius,double time,float alpha) {
        richCircle(buffers,stack,center,dir,radius,time,alpha,radius>=6?6:3,3);
    }
    /**
     * @param satellites 卫星子阵数量 ✓ —— 半径 15 的龙滅大阵给 6 个子阵太费（作者 2026-10-10：
     *                   "把 t5 水柱释放过程里的线条量都可以减半" ✗）⇒ 调用方可以只要 3 个 ✓。
     */
    static void richCircle(MultiBufferSource buffers,PoseStack stack,Vec3 center,Vec3 dir,double radius,double time,float alpha,int satellites) {
        richCircle(buffers,stack,center,dir,radius,time,alpha,satellites,3);
    }
    static void richCircle(MultiBufferSource buffers,PoseStack stack,Vec3 center,Vec3 dir,
                           double radius,double time,float alpha,int satellites,int tier) {
        Matrix4f pose=stack.last().pose();
        int n=Math.max(0,Math.min(6,satellites));
        WaterVisualBuffers.draw(buffers,(out,glow) -> {
            seal(out,glow,pose,center,dir,radius,time,alpha,tier);
            for(int i=0;i<n;i++) {
                double a=time*.010D+i*Math.PI*2.0D/Math.max(1,n)+Math.PI/4.0D;
                Vec3 axis=dir.lengthSqr()<1e-9?UP:dir.normalize();
                Vec3 right=WaterSpellRules.right(axis),up=right.cross(axis).normalize();
                Vec3 at=center.add(right.scale(Math.cos(a)*radius*1.13D))
                        .add(up.scale(Math.sin(a)*radius*1.13D));
                sealSatellite(out,glow,pose,at,axis,radius*.055D,time,alpha*.88F);
            }
        });
    }
    static void staticSeal(MultiBufferSource buffers,PoseStack stack,Vec3 center,Vec3 dir,
                           double radius,double time,float alpha,int tier) {
        Matrix4f pose=stack.last().pose();
        WaterVisualBuffers.draw(buffers,(out,glow) -> seal(out,glow,pose,center,dir,radius,time,alpha,tier));
    }
    static void drawCircle(VertexConsumer out,VertexConsumer glow,Matrix4f pose,Vec3 center,Vec3 dir,
                           double radius,double time,float alpha,int satellites) {
        sigil(out,pose,center,dir,radius,time*.016,alpha*.96F);
        // The main seal already contains four nested rings and runes.
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        for(int i=0;i<satellites;i++) {
            // 卫星子阵只要环 ✗：它半径只有主阵的 .14 ✗，12 个符文在 2 格大的盘上根本看不见 ✗
            //（原来每个子阵也是一座完整 sigil＝9k 顶点 ✗ —— 冥穴一次画 6 个 ✗）
            double a=time*.006+i*Math.PI*2/satellites;
            satellite(out,glow,pose,center.add(radial(right,up,a,radius*1.1)).add(dir.scale(.06)),dir,radius*.14,-time*.02,alpha*.94F);
        }
        ringColor(out,pose,center,dir,radius*1.04,radius*.028,.005F,.10F,.48F,alpha*.9F,48);
        ringColor(out,pose,center.add(dir.scale(.03)),dir,radius*1.03,radius*.018,.34F,.18F,1,alpha*.95F,48);
        ringColor(out,pose,center.add(dir.scale(.07)),dir,radius*.73,radius*.024,.58F,.3F,1,alpha*.9F,48);
        for(int i=0;i<24;i++) {
            double a=-time*.018+i*Math.PI/12;
            Vec3 p=center.add(radial(right,up,a,radius*.84));
            Vec3 v=radial(right,up,a,radius*.045),w=radial(right,up,a+Math.PI/2,radius*.018);
            stroke(out,pose,dir,p.subtract(v),p.add(w),radius*.006,.6F,.22F,1,alpha*.96F);
            stroke(out,pose,dir,p.add(w),p.add(v),radius*.006,.6F,.22F,1,alpha*.96F);
        }
        sigilGlow(glow,pose,center,dir,radius,time,alpha);
    }
    /** 卫星子阵：**只有几圈环 ＋ 一个小核** ✓（约 200 顶点 ✗，不是一座 9k 的完整 sigil ✗）。 */
    private static void satellite(VertexConsumer out,VertexConsumer glow,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double time,float alpha) {
        ringColor(out,pose,center,dir,radius,radius*.05,.48F,.22F,1,alpha,16);
        ringColor(out,pose,center,dir,radius*.62,radius*.05,.30F,.18F,.92F,alpha*.9F,16);
        ringColor(glow,pose,center,dir,radius,radius*.06,.6F,.3F,1,alpha*.55F,16);
        stroke(glow,pose,dir,center.add(radial(WaterSpellRules.right(dir),WaterSpellRules.right(dir).cross(dir).normalize(),time*.02,radius*.5)),
                center.add(radial(WaterSpellRules.right(dir),WaterSpellRules.right(dir).cross(dir).normalize(),time*.02+Math.PI,radius*.5)),radius*.05,.7F,.35F,1,alpha*.5F);
    }
    /**
     * 法阵自发光（2026-10-10 作者："魔法阵太素了，纯线条，加上自发光吧"）。
     *
     * <p>原来法阵只有 alpha 混合的**细线条** ✗ ⇒ 看着像"画上去的图案"，不像"在发光的东西" ✗。
     * 这里补三样：
     * <ol>
     *   <li><b>光晕盘</b>：一串由外向内递减的同心环，把线条之间的空隙填成柔和的径向发光 ✓；</li>
     *   <li><b>加色亮线</b>：主环/内环/中心环各再描一遍，走 {@code WaterRenderTypes.glow()}
     *       （加色混合 ⇒ 重叠处推向白亮 ✓，普通 alpha 只会涂成一块浅蓝 ✗）；</li>
     *   <li><b>流动亮弧 + 中心亮核</b>：三层不同半径、不同转向的短亮弧 ⇒ "能量在阵里转" ✓。</li>
     * </ol>
     *
     * <p>只加在<b>主阵</b>上 ✗（子阵/卫星阵不加 ✗）—— 一次施法最多画 3 个主阵（雨滴链）✓，
     * 全加上去只会白花花一片 ✗。
     */
    private static void sigilGlow(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double time,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        // 光晕层数：小阵 4 层、大阵 3 层 ✗（第十批：原来是 8 / 4 层 ✗ —— 每层 256 顶点 ✗，
        // 六道浪那一发光是光晕就上万 ✗）。段数也跟着半径给 ✗：大阵不需要 64 段 ✗。
        int halo=1,seg=radius>=6?48:32;
        for(int i=0;i<halo;i++) {
            float t=i/(float)halo;
            ringColor(out,pose,center,dir,radius*(1-t*.92),radius*(.20F+ .10F/halo),.22F,.62F,1,alpha*(.05F+t*.02F),seg);
        }
        ringColor(out,pose,center,dir,radius,radius*.018,.55F,.92F,1,alpha*.55F,seg);
        ringColor(out,pose,center,dir,radius*.94,radius*.013,.70F,.96F,1,alpha*.50F,seg);
        ringColor(out,pose,center,dir,radius*.61,radius*.018,.88F,1,1,alpha*.55F,seg);
        ringColor(out,pose,center,dir,radius*.28,radius*.022,1,1,1,alpha*.60F,seg);
        for(int k=0;k<3;k++) {
            double r=radius*(k==0?1:.66-.14*k);
            double a0=time*.020*(k%2==0?1:-1.4)+k*2.1;
            for(int i=0;i<10;i++) {
                double a=a0+i*.055;
                Vec3 p=center.add(radial(right,up,a,r)),q=center.add(radial(right,up,a+.055,r));
                stroke(out,pose,dir,p,q,radius*.014,1,1,1,alpha*(.52F-i*.045F));
            }
        }
        disk(out,pose,center,dir,radius*.15,.85F,1,1,alpha*.45F);
    }
    private static void sigil(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double spin,float alpha) {
        Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
        int seg=radius>=6?48:32;
        ringColor(out,pose,center,dir,radius,radius*.013,.12F,.08F,.65F,alpha,seg);
        ringColor(out,pose,center,dir,radius*.94,radius*.01,.48F,.22F,1,alpha,seg);
        ringColor(out,pose,center,dir,radius*.61,radius*.015,.28F,.18F,.92F,alpha,seg);
        ringColor(out,pose,center,dir,radius*.28,radius*.018,.7F,.4F,1,alpha,seg);
        for(int i=0;i<12;i++) {
            double a=spin+i*Math.PI/6;
            Vec3 p=center.add(radial(right,up,a,radius*.6));
            Vec3 q=center.add(radial(right,up,a+Math.PI*2/3,radius*.6));
            stroke(out,pose,dir,p,q,radius*.004,.38F,.18F,.9F,alpha*.8F);
            // 符文：4 道**平笔划**的十字星 ✓（原来是 4 根 24 边管＝384 顶点 ✗ ⇒ 现在 16 顶点 ✓）
            Vec3 c=center.add(radial(right,up,a,radius*.79));
            Vec3 v=radial(right,up,a,radius*.065),w=radial(right,up,a+Math.PI/2,radius*.025);
            stroke(out,pose,dir,c.subtract(v),c.add(w),radius*.006,.62F,.3F,1,alpha);
            stroke(out,pose,dir,c.add(w),c.add(v),radius*.006,.62F,.3F,1,alpha);
            stroke(out,pose,dir,c.add(v),c.subtract(w),radius*.006,.62F,.3F,1,alpha);
            stroke(out,pose,dir,c.subtract(w),c.subtract(v),radius*.006,.62F,.3F,1,alpha);
        }
    }
    static void spiral(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 axis,double radius,double height,
                       double time,float red,float green,float blue,float alpha) {
        Vec3 right=WaterSpellRules.right(axis),up=right.cross(axis).normalize();
        for(int i=0;i<48;i++) {
            double t=i/48.0,q=(i+1)/48.0,a=time+t*Math.PI*4,b=time+q*Math.PI*4;
            Vec3 p=center.add(axis.scale(height*t)).add(radial(right,up,a,radius));
            Vec3 r=center.add(axis.scale(height*q)).add(radial(right,up,b,radius));
            tube(out,pose,p,r,.045,red,green,blue,alpha);
        }
    }
    /** 水板墙的板数 ✓（内外两层发光层必须用同一个数 ✗，否则亮边会和板缝错开 ✗）。
        板宽按 ~1.5 格取整，上限 48 ✗ —— 半径 18 的深渊若按 1.5 格算要 75 块，
        每块 96 顶点 ⇒ 单实体上万个顶点纯属白烧 GPU ✗。 */
    static int panelCount(double radius) { return Math.max(8,Math.min(48,(int)Math.round(Math.PI*2*radius/1.5))); }
    /**
     * 水牢柱廊一圈几根 ✓（作者 2026-11-10："水牢的隔板……做成一个个罗马圣柱"✗）。
     *
     * <p>比 {@link #panelCount} **少得多** ✗：隔板要严丝合缝（1.5 格一块 ✓），
     * 柱子是"廊"——柱距 3.4 格才看得出是一根根柱子，30 根挤在半径 12 的圈上会连成一堵墙 ✗。
     * 上限 28 根：t5 冥穴半径 26 格（周长 163）也只要 28 根，再多就是烧顶点 ✗。
     */
    static int columnCount(double radius) { return Math.max(8,Math.min(22,(int)Math.round(Math.PI*2*radius/4.2))); }

    /**
     * 单根柱子的直径（格 ✓）—— 三个上限取最小：
     * <ol>
     *   <li>{@code chord*0.62}：别把柱廊挤成一堵墙（柱距六成 ✓）；</li>
     *   <li>{@code height/6.5}：**别做成细针** ✗ —— 24 格高的柱子如果只有 1.4 格粗，
     *       看起来像根旗杆而不是罗马柱（试玩前就能算出来 ✓）；</li>
     *   <li>2.6 格硬上限：再粗就不像柱子、像水塔 ✗。</li>
     * </ol>
     */
    static double columnDiameter(double radius,double height,int columns) {
        if(columns<=0||radius<=0)return .8D;
        double chord=2*radius*Math.sin(Math.PI/columns);
        return Math.max(.55D,Math.min(2.6D,Math.min(chord*.62D,height/6.5D)));
    }
    /**
     * 水板圆筒（2026-10-10 第七批）：作者 ——"水牢…把他改成一个个竖起来的水板连在一起变成一个圆困住敌人"✓。
     *
     * <p>做法：{@code panels} 块**竖直弧面板**围成一圈 ✗，每块板再分 {@code segs} 段 × {@code rows} 行，
     * 这样顶点色能做出"下深上浅"的水色渐变 ✓（整块板单色会像塑料板 ✗，正是作者不喜欢的"线条牢"的反面 ✗）。
     * 三样细节让它读起来是**水板**而不是玻璃 ✗：
     * <ol>
     *   <li><b>板缝亮竖线</b>：每块板边界描一条亮线 ⇒ "一块一块连起来"看得出 ✓；</li>
     *   <li><b>板顶亮边</b>：板顶把外沿和内沿封起来并提亮 ⇒ 像被光照到的水面 ✓；</li>
     *   <li><b>内层暗一档</b>：同一块板画内外两层、内层压暗 ⇒ 板有厚度 ✓。</li>
     * </ol>
     * 每块板中心再向外鼓一点点 ⇒ 单块板自带弧度 ✓，不会读成一圈多边形 ✗。
     */
    static void panelWall(VertexConsumer out,Matrix4f pose,Vec3 center,double radius,double height,
                          int panels,double time,float alpha) {
        double span=Math.PI*2/panels;
        int segs=3,rows=3;
        double bow=radius*.018,base=.12;
        for(int p=0;p<panels;p++) {
            double a0=p*span+time*.003;
            for(int s=0;s<segs;s++) {
                double a=a0+span*s/segs,b=a0+span*(s+1)/segs;
                // 笼子恒为世界竖直 ⇒ 直接用 XZ 极坐标 ✓（不必过 right/up 基 ✗）
                double rOut=radius+bow*Math.sin((s+.5)/segs*Math.PI);
                for(int row=0;row<rows;row++) {
                    double y0=base+height*row/rows,y1=base+height*(row+1)/rows;
                    float t0=row/(float)rows,t1=(row+1)/(float)rows;
                    // 水色渐变：底部深蓝、越往上越亮越青 ✓
                    float r0=.02F+t0*.12F,g0=.26F+t0*.32F,b0=.70F+t0*.26F;
                    float r1=.02F+t1*.12F,g1=.26F+t1*.32F,b1=.70F+t1*.26F;
                    float aLo=alpha*(.34F+t0*.42F),aHi=alpha*(.34F+t1*.42F);
                    Vec3 oa=new Vec3(Math.cos(a)*rOut,y0,Math.sin(a)*rOut);
                    Vec3 ob=new Vec3(Math.cos(b)*rOut,y0,Math.sin(b)*rOut);
                    double rIn=rOut-.07;
                    Vec3 ia=new Vec3(Math.cos(a)*rIn,y0,Math.sin(a)*rIn);
                    Vec3 ib=new Vec3(Math.cos(b)*rIn,y0,Math.sin(b)*rIn);
                    Vec3 oa1=oa.add(0,y1-y0,0),ob1=ob.add(0,y1-y0,0);
                    Vec3 ia1=ia.add(0,y1-y0,0),ib1=ib.add(0,y1-y0,0);
                    // 外层面（朝外）
                    vertex(out,pose,oa,r0,g0,b0,aLo);vertex(out,pose,ob,r0,g0,b0,aLo);
                    vertex(out,pose,ob1,r1,g1,b1,aHi);vertex(out,pose,oa1,r1,g1,b1,aHi);
                    // NO_CULL renders both sides; a duplicate inner wall doubles translucent overdraw.
                    // 板顶亮边：外沿→内沿封口并提亮 ✓
                    if(row==rows-1)
                        quad(out,pose,oa1,ob1,ib1,ia1,.58F,.94F,1,alpha*.80F);
                }
            }
            // 板缝：一条亮竖线 ⇒ 读得出"一块一块连起来"✓
            // 第十批：改成**一片 4 顶点的亮带** ✗（原来是一根 24 边 tube＝96 顶点 ✗，
            // 半径 18 的深渊 48 块板光板缝就 4.6k ✗ —— 实测占了整个圆筒的 3/4 ✗）
            Vec3 seam=new Vec3(Math.cos(a0)*(radius+bow),base,Math.sin(a0)*(radius+bow));
            Vec3 seamAcross=new Vec3(-Math.sin(a0),0,Math.cos(a0)).scale(.05);
            quad(out,pose,seam.subtract(seamAcross),seam.add(seamAcross),
                    seam.add(seamAcross).add(0,height,0),seam.subtract(seamAcross).add(0,height,0),.40F,.86F,1,alpha*.78F);
        }
        ringColor(out,pose,center.add(0,base+height,0),UP,radius+bow,.12,.52F,.95F,1,alpha*.85F);
    }

    /* ==================== 雨滴链（第四条链 · 2026-10-10 第九批：五档全部重做）====================

       作者 2026-10-10（第八批之后）："**水法的雨链很丑啊，我建议五个重做，你定就好**"✗。

       **先自己盘清楚"丑在哪"**（不猜 ✗）：
       1. 除了雨幕（第五/七批已经换成粒子 ✓ 那个方向对 ✓），雨链**所有标志物都是细 `tube` 线框** ✗ ——
          莲瓣是线 ✗、穹顶是 8×20 根线 ✗、天洪的十二道落水柱是**螺旋线** ✗、水冠是线 ✗。
          这正是作者在水牢那批点名不喜欢的"**线条**"✗（"就这种线条牢我不是很喜欢"✗）。
       2. 顶点数是四条链里最高的 ✗：`spiral` 一条 4.6k ✗、tube 穹顶 7.7k ✗、
          t5 十二道落水柱 ×（螺旋＋管＋环）≈ **5 万** ✗ —— 半径 32 格还持续 35 秒，又丑又贵 ✗。
       3. 地面只有**一圈细环** ✗ ⇒ 看不出"脚下是一片水域"✗（雨落在什么上面？没有"上面"✗）。

       **重做的共同手法**：**面片（quad strip）替代细管** ✓ ——
       同样读出"有体积"，顶点数少一个量级 ✓。四个手法全部沿用作者已经认可过的那一套 ✗
       （水板那批的"厚度 / 渐变 / 亮边 / 亮缝"✓）：板面外凸、内层压暗、竖直渐变、亮缝亮边 ✓。

       本节的工具：`waterSurface`（实心水面＋涟漪）/ `spiralBand`（螺旋水带）/ `curtain`（水帘·落水柱）/
       `waterColumn`（水柱）/ `waterCrown`（水冠）/ `waterLotus`（水莲）/ `waterDome`（水穹顶）/
       `rainSeal`（雨链专用**轻量**发光阵 ✗）。 */

    /**
     * 地面**实心水膜**（径向渐变 ✓）＋ n 圈向外推的涟漪 ✓。
     *
     * <p>为什么必须有它：雨链原来地面只有一圈细环 ✗ ⇒ 雨是"落在虚空里"✗。
     * 有了水面，雨滴粒子、溅花、"涟漪"才有落脚点 ✓（作者的档位文案里 t1 就叫"露珠与涟漪"✗）。
     *
     * @param ripples 涟漪圈数（t1 用 2 ✓、t5 用 5 ✓）
     * @param speed   涟漪向外推进的速度（圈/秒 ✗）
     */
    static void waterSurface(VertexConsumer out,Matrix4f pose,double radius,double y,double time,
                             int ripples,double speed,double waviness,float r,float g,float b,float alpha) {
        Vec3 ex=new Vec3(1,0,0),ez=new Vec3(0,0,1),center=new Vec3(0,y,0);
        int seg=36;
        // ① 水膜本体：内圈亮、外圈暗（两段扇形 ✗）—— 中心亮才是"水面反光"✓
        for(int i=0;i<seg;i++) {
            double a=i*Math.PI*2/seg,c=(i+1)*Math.PI*2/seg;
            Vec3 pa=radial(ex,ez,a,1),pc=radial(ex,ez,c,1);
            Vec3 inA=center.add(pa.scale(radius*.52)),inC=center.add(pc.scale(radius*.52));
            Vec3 outA=center.add(pa.scale(radius)),outC=center.add(pc.scale(radius));
            quad(out,pose,center,inA,inC,center,r,g,b,alpha*.62F);
            quad(out,pose,inA,outA,outC,inC,r*.78F,g*.86F,b,alpha*.30F);
        }
        // ② 涟漪：环半径带 sin(angle*7) ⇒ 是"水面上的波"而不是圆规画的圆 ✗
        for(int k=0;k<ripples;k++) {
            double t=(time*speed+k/(double)ripples)%1.0,rr=radius*(.16+.84*t);
            double width=radius*(.012+.030*(1-t));
            float fa=(float)((1-t)*.85+.15);
            for(int i=0;i<seg;i++) {
                double a=i*Math.PI*2/seg,c=(i+1)*Math.PI*2/seg;
                double wa=1+waviness*Math.sin(a*7+time*.05),wc=1+waviness*Math.sin(c*7+time*.05);
                Vec3 oa=center.add(radial(ex,ez,a,rr*wa)),oc=center.add(radial(ex,ez,c,rr*wc));
                Vec3 ia=center.add(radial(ex,ez,a,Math.max(0,rr*wa-width))),ic=center.add(radial(ex,ez,c,Math.max(0,rr*wc-width)));
                quad(out,pose,oa,oc,ic,ia,r+.16F,g+.16F,Math.min(1,b+.06F),alpha*.75F*fa);
            }
        }
    }

    /**
     * **螺旋水带**：绕轴盘升的一条**带** ✓（宽度 ＋ 渐变 ＋ 外沿亮边 ＋ 内层压暗 ⇒ 有厚度 ✗）。
     *
     * <p>替掉 {@link #spiral}（一条 4.6k 顶点的细管 ✗）：同一个"碧绿螺旋水带"的读感 ✓，顶点数 ÷8 ✓。
     *
     * @param turns 盘升圈数；@param taper 越往上收得越细的比例（0.35 ⇒ 顶端 65% 半径 ✓ 像水龙卷 ✗）
     */
    static void spiralBand(VertexConsumer out,Matrix4f pose,Vec3 center,double radius,double height,double turns,
                           double time,double width,double taper,int segments,float r,float g,float b,float alpha) {
        for(int i=0;i<segments;i++) {
            double t=i/(double)segments,u=(i+1)/(double)segments;
            double a=time+t*turns*Math.PI*2,c=time+u*turns*Math.PI*2;
            double ra=radius*(1-taper*t),rb=radius*(1-taper*u);
            double wa=width*(1-.35*t),wb=width*(1-.35*u);
            double ya=height*t,yb=height*u;
            Vec3 inA=center.add(Math.cos(a)*ra,ya,Math.sin(a)*ra),inB=center.add(Math.cos(c)*rb,yb,Math.sin(c)*rb);
            Vec3 outA=center.add(Math.cos(a)*(ra+wa),ya,Math.sin(a)*(ra+wa)),outB=center.add(Math.cos(c)*(rb+wb),yb,Math.sin(c)*(rb+wb));
            // 外侧面（亮 ✓）与内侧面（压暗 ⇒ 带子有厚度 ✗）
            quad(out,pose,inA,outA,outB,inB,r+.18F,g+.18F,Math.min(1,b+.10F),alpha);
            Vec3 iA=center.add(Math.cos(a)*(ra-wa*.30),ya,Math.sin(a)*(ra-wa*.30)),iB=center.add(Math.cos(c)*(rb-wb*.30),yb,Math.sin(c)*(rb-wb*.30));
            quad(out,pose,iB,iA,inA,inB,r*.45F,g*.6F,Math.min(1,b*.95F),alpha*.72F);
            // 外沿亮边 ⇒ 读得出"这条带子是水做的"✓
            double ea=wa*.22,eb=wb*.22;
            quad(out,pose,outA,center.add(Math.cos(a)*(ra+wa+ea),ya,Math.sin(a)*(ra+wa+ea)),
                    center.add(Math.cos(c)*(rb+wb+eb),yb,Math.sin(c)*(rb+wb+eb)),outB,r+.28F,g+.26F,1,alpha*.85F);
        }
    }

    /**
     * **水帘 / 落水柱**：一圈**向下淌的水板** ✓ —— 板面有竖直渐变、**向下流动的亮带** ✗、
     * 板缝亮竖线、板顶亮边 ✓（全部照抄作者认可的水板那套 ✗）。
     *
     * <p>这是雨链最核心的一件道具 ✓：t2"初雨"的水帘、t5"天洪"的十二道落水柱都是它 ✓。
     * 单位：每块板 2 列 × `rows` 行 × 2 层（外层＋内层压暗 ✗）⇒ 约 90 顶点/块 ✗
     * （原来是 4.6k 顶点/道的螺旋线 ✗）。
     *
     * @param top    板顶的 y（高于 bottom ✓）
     * @param bottom 板底的 y
     * @param fill   每块水板占自己那一格角度的比例 ✓ —— `1.0` ⇒ 一条**连续水帘**（t2 ✓）；
     *               `0.34` ⇒ 十二**道分开的落水柱**（t5"十二道落水柱"✗，不然会连成一圈水墙 ✗）
     * @param phase  整圈板的角度相位（多圈同时用时要错开 ✗）
     */
    static void curtain(VertexConsumer out,Matrix4f pose,double radius,double top,double bottom,int panels,
                        double fill,int rows,double time,double phase,float r,float g,float b,float alpha) {
        if(panels<=0) return;
        double span=Math.PI*2/panels,bow=radius*.03;
        double slot=span*Math.max(.05,Math.min(1,fill)),slack=(span-slot)*.5;
        for(int p=0;p<panels;p++) {
            double a0=p*span+phase+slack;
            for(int col=0;col<2;col++) {
                double a=a0+slot*col/2.0,c=a0+slot*(col+1)/2.0;
                // 板面外凸 ＋ 越往下越外扩 ⇒ 落水的"散开"感 ✗（不是一根柱子 ✗）
                double rOut=radius+bow*Math.sin((col+.5)/2.0*Math.PI);
                for(int row=0;row<rows;row++) {
                    double t0=row/(double)rows,t1=(row+1)/(double)rows;
                    double yLo=top+(bottom-top)*t0,yHi=top+(bottom-top)*t1;
                    double rLo=rOut*(1+.06*t0),rHi=rOut*(1+.06*t1);
                    // 向下流动的亮带：相位随 time 增大 ⇒ 亮带往下走 ✓
                    float fLo=flow(t0,time,p),fHi=flow(t1,time,p);
                    float lo=.30F+.70F*fLo,hi=.30F+.70F*fHi;
                    Vec3 oa=new Vec3(Math.cos(a)*rLo,yLo,Math.sin(a)*rLo),ob=new Vec3(Math.cos(c)*rLo,yLo,Math.sin(c)*rLo);
                    Vec3 oa1=new Vec3(Math.cos(a)*rHi,yHi,Math.sin(a)*rHi),ob1=new Vec3(Math.cos(c)*rHi,yHi,Math.sin(c)*rHi);
                    vertex(out,pose,oa,r*lo,g*lo,b*lo,alpha*(.30F+.16F*lo));
                    vertex(out,pose,ob,r*lo,g*lo,b*lo,alpha*(.30F+.16F*lo));
                    vertex(out,pose,ob1,r*hi,g*hi,b*hi,alpha*(.30F+.16F*hi));
                    vertex(out,pose,oa1,r*hi,g*hi,b*hi,alpha*(.30F+.16F*hi));
                    // 内层压暗 ⇒ 板有厚度，不是一张纸 ✗
                    Vec3 ia=new Vec3(Math.cos(a)*(rLo-.08),yLo,Math.sin(a)*(rLo-.08)),ib=new Vec3(Math.cos(c)*(rLo-.08),yLo,Math.sin(c)*(rLo-.08));
                    Vec3 ia1=new Vec3(Math.cos(a)*(rHi-.08),yHi,Math.sin(a)*(rHi-.08)),ib1=new Vec3(Math.cos(c)*(rHi-.08),yHi,Math.sin(c)*(rHi-.08));
                    vertex(out,pose,ib,r*lo*.45F,g*lo*.6F,b*lo*.95F,alpha*.22F);
                    vertex(out,pose,ia,r*lo*.45F,g*lo*.6F,b*lo*.95F,alpha*.22F);
                    vertex(out,pose,ia1,r*hi*.45F,g*hi*.6F,b*hi*.95F,alpha*.22F);
                    vertex(out,pose,ib1,r*hi*.45F,g*hi*.6F,b*hi*.95F,alpha*.22F);
                }
            }
            // 板缝亮竖线 ⇒ 读得出"一条一条水流并在一起"✓
            double sa=a0,seamR=radius+bow;
            Vec3 sLo=new Vec3(Math.cos(sa)*seamR,top,Math.sin(sa)*seamR);
            Vec3 sHi0=new Vec3(Math.cos(sa)*(seamR+.06),top,Math.sin(sa)*(seamR+.06));
            Vec3 sLo2=new Vec3(Math.cos(sa)*seamR,bottom,Math.sin(sa)*seamR);
            Vec3 sHi2=new Vec3(Math.cos(sa)*(seamR+.06),bottom,Math.sin(sa)*(seamR+.06));
            quad(out,pose,sLo,sHi0,sHi2,sLo2,r+.30F,g+.30F,1,alpha*.55F);
        }
    }

    /** 水帘上"往下走"的亮带相位 ✓（0..1 ✗）。t 是**从板顶往下**的高度比例（0=顶、1=底 ✗）。 */
    private static float flow(double t,double time,int panel) {
        return (float)(.5+.5*Math.sin(((1-t)*2.4+time*.11+panel*.21)*Math.PI*2));
    }

    /**
     * **水柱**：三层嵌套管（外透 / 中 / 亮芯 ✗）＋ 柱身亮环 ＋ 柱顶水冠环 ＋ 一条细螺旋 ✓
     * —— 与龙滅光束"三层管读作有体积"是同一套做法 ✓（原来是 0.18 半径的**一根细管** ✗）。
     */
    static void waterColumn(VertexConsumer out,Matrix4f pose,Vec3 base,double radius,double height,double time,
                            double twist,float r,float g,float b,float alpha) {
        r=.03F;g=.25F;b=.98F;
        Vec3 top=base.add(0,height,0);
        // 第十批：三层管显式给边数 ✗（老判据 radius≥.1 就是 24 边＝96 顶点 ✗，四根柱子光管身就 1.2k ✗）
        tube(out,pose,base,top,radius,r,g,b,alpha*.28F,14);
        tube(out,pose,base,top,radius*.62,r*.7F,g*.85F,Math.min(1,b+.08F),alpha*.42F,12);
        tube(out,pose,base.add(0,height*.05,0),top.add(0,-height*.05,0),radius*.28,r+.22F,g+.22F,1,alpha*.78F,10);
        for(int i=1;i<=2;i++)
            ringColor(out,pose,base.add(0,height*(i==1?.42:.78),0),UP,radius*(i==1?1.08:1.16),radius*.10,r+.20F,g+.20F,1,alpha*(i==1?.62F:.72F),40);
        ringColor(out,pose,top,UP,radius*1.30,radius*.16,r+.30F,g+.30F,1,alpha*.85F,40);
        spiralBand(out,pose,base,radius*1.10,height,2.2,time+twist,radius*.30,.18,16,r,g,b,alpha*.70F);
        // Fine streams are single strips rather than additional high-sided tubes.
        for(int strand=0;strand<8;strand++)for(int step=0;step<4;step++) {
            double a=strand*Math.PI/4+twist+time*.014,t=step/4.0,u=(step+1)/4.0;
            double rr=radius*(1.025+.035*Math.sin(time*.08+t*7+strand));
            Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a));
            Vec3 low=base.add(Math.cos(a)*rr,height*t,Math.sin(a)*rr),high=base.add(Math.cos(a+.07)*rr,height*u,Math.sin(a+.07)*rr);
            double width=radius*.018;
            boolean violet=(strand*4+step)%10==9;
            quad(out,pose,low.subtract(tangent.scale(width)),low.add(tangent.scale(width)),high.add(tangent.scale(width)),high.subtract(tangent.scale(width)),violet?.58F:.22F,violet?.14F:.62F,1,alpha*.52F);
        }
        double pulse=(time*.10+twist)%1;
        ringColor(out,pose,base.add(0,height*pulse,0),UP,radius*1.08,radius*.045,.50F,.92F,1,alpha*.6F,32);
    }

    /**
     * **水冠**（t5 档位文案点名的"中心水冠"✗）：一圈向上翻卷的水舌 ✓ ＋ 中心升腾的螺旋 ✓ ＋ 两圈亮环 ✓。
     */
    static void waterCrown(VertexConsumer out,Matrix4f pose,double radius,double height,double time,
                           float r,float g,float b,float alpha) {
        int blades=16;
        for(int i=0;i<blades;i++) {
            double a=i*Math.PI*2/blades;
            Vec3 dir=new Vec3(Math.cos(a),0,Math.sin(a));
            Vec3 tan=new Vec3(-Math.sin(a),0,Math.cos(a));
            for(int seg=0;seg<3;seg++) {
                double t0=seg/3.0,t1=(seg+1)/3.0;
                // 先向上、再向外翻 ⇒ 水舌 ✓（纯竖线读不出"冠"✗）
                double h0=height*(.30+.70*Math.sin(t0*Math.PI*.62)),h1=height*(.30+.70*Math.sin(t1*Math.PI*.62));
                double d0=radius*(.45+.75*t0*t0),d1=radius*(.45+.75*t1*t1);
                double w0=radius*.16*(1-.55*t0),w1=radius*.16*(1-.55*t1);
                Vec3 a0=dir.scale(d0).add(tan.scale(w0)).add(0,h0,0),b0=dir.scale(d0).subtract(tan.scale(w0)).add(0,h0,0);
                Vec3 a1=dir.scale(d1).add(tan.scale(w1)).add(0,h1,0),b1=dir.scale(d1).subtract(tan.scale(w1)).add(0,h1,0);
                float f0=.55F+.45F*(float)t0,f1=.55F+.45F*(float)t1;
                quad(out,pose,b0,a0,a1,b1,r*f0,g*f0,Math.min(1,b*f0+.12F),alpha*(.75F-.15F*(float)t0));
            }
            // 水舌尖端：一片薄**亮面** ✓（不用 tube ✗ —— 16 根管就是 1.5k 顶点 ✗，第九批就是为了不再堆管 ✗）
            Vec3 tipBase=dir.scale(radius*.92).add(0,height*.92,0),tip=dir.scale(radius*1.22).add(0,height,0);
            Vec3 half=tan.scale(radius*.05);
            quad(out,pose,tipBase.subtract(half),tipBase.add(half),tip.add(half.scale(.4)),tip.subtract(half.scale(.4)),r+.30F,g+.30F,1,alpha*.85F);
        }
        ringColor(out,pose,new Vec3(0,height*.12,0),UP,radius*1.12,radius*.10,r+.22F,g+.22F,1,alpha*.75F);
        ringColor(out,pose,new Vec3(0,height*.30,0),UP,radius*.72,radius*.08,r+.30F,g+.30F,1,alpha*.65F);
        spiralBand(out,pose,Vec3.ZERO,radius*.34,height*1.15,2.6,-time*.6,radius*.13,.25,36,r+.25F,g+.25F,1,alpha*.80F);
    }

    /**
     * **水莲**（t3 档位文案点名的"蓝色水莲"✗）：N 瓣**有厚度**的水花瓣 ✓，随 {@code open} 缓缓张开 ✓。
     *
     * <p>原来是 8 条 `tube` 折线 ✗ ⇒ 只是"八条线"✗。现在每瓣是一张**面** ✗：
     * 宽度按 sin 收成叶形 ✓、瓣面自身向上卷 ✓、尖端外翻下垂 ✓、外沿亮边 ✓、内层压暗 ⇒ 有厚度 ✓。
     *
     * @param open 0（合拢的水苞）→1（完全张开 ✗）—— 由年龄驱动，所以莲是"活"的 ✓
     */
    static void waterLotus(VertexConsumer out,Matrix4f pose,double radius,double time,double open,
                           float r,float g,float b,float alpha) {
        int petals=8,us=4,vs=3;
        for(int p=0;p<petals;p++) {
            double a=p*Math.PI*2/petals+time*.003;
            Vec3 dir=new Vec3(Math.cos(a),0,Math.sin(a)),tan=new Vec3(-Math.sin(a),0,Math.cos(a));
            for(int i=0;i<us;i++)for(int j=0;j<vs;j++) {
                double u0=i/(double)us,u1=(i+1)/(double)us,v0=-1+2*j/(double)vs,v1=-1+2*(j+1)/(double)vs;
                Vec3 q00=petalPoint(dir,tan,u0,v0,radius,open),q01=petalPoint(dir,tan,u0,v1,radius,open);
                Vec3 q11=petalPoint(dir,tan,u1,v1,radius,open),q10=petalPoint(dir,tan,u1,v0,radius,open);
                float f0=.55F+.45F*(float)u0,f1=.55F+.45F*(float)u1;
                // 外侧面（受光 ✓）
                vertex(out,pose,q00,r*f0,g*f0,Math.min(1,b*f0+.10F),alpha*.80F);
                vertex(out,pose,q01,r*f0,g*f0,Math.min(1,b*f0+.10F),alpha*.80F);
                vertex(out,pose,q11,r*f1,g*f1,Math.min(1,b*f1+.10F),alpha*.86F);
                vertex(out,pose,q10,r*f1,g*f1,Math.min(1,b*f1+.10F),alpha*.86F);
                // 内侧面：往上抬 0.05 并压暗 ⇒ 花瓣有厚度 ✗
                vertex(out,pose,q10.add(0,.05,0),r*f1*.5F,g*f1*.62F,b*f1*.95F,alpha*.45F);
                vertex(out,pose,q11.add(0,.05,0),r*f1*.5F,g*f1*.62F,b*f1*.95F,alpha*.45F);
                vertex(out,pose,q01.add(0,.05,0),r*f0*.5F,g*f0*.62F,b*f0*.95F,alpha*.45F);
                vertex(out,pose,q00.add(0,.05,0),r*f0*.5F,g*f0*.62F,b*f0*.95F,alpha*.45F);
            }
            // 瓣缘亮边 + 中脉亮线 ⇒ 一眼看出是"水做的花瓣"✓
            for(int i=0;i<us;i++) {
                double u0=i/(double)us,u1=(i+1)/(double)us;
                for(double v:new double[]{-1,1}) {
                    Vec3 e0=petalPoint(dir,tan,u0,v,radius,open),e1=petalPoint(dir,tan,u1,v,radius,open);
                    Vec3 n0=e0.add(0,.09,0).add(dir.scale(.05)),n1=e1.add(0,.09,0).add(dir.scale(.05));
                    quad(out,pose,e0,n0,n1,e1,r+.30F,g+.28F,1,alpha*.85F);
                }
                Vec3 m0=petalPoint(dir,tan,u0,0,radius,open),m1=petalPoint(dir,tan,u1,0,radius,open);
                quad(out,pose,m0,m0.add(0,.07,0),m1.add(0,.07,0),m1,r+.25F,g+.25F,1,alpha*.55F);
            }
        }
    }

    /** 莲瓣上的一个点 ✓：宽度按 sin 收成叶形 ✗、u 越大越外扩、瓣面向上卷、尖端外翻下垂 ✓。
        ⚠️ u=1（瓣尖）时这里的 y **正好等于** `WaterSpellRules.rainLotusTipHeight` ✗ ——
        粒子与发光星读那一条 ✓，改这个公式必须两处一起改 ✗。 */
    private static Vec3 petalPoint(Vec3 dir,Vec3 tan,double u,double v,double radius,double open) {
        double spread=WaterSpellRules.rainLotusSpread(open);
        // 半宽 ≈ 长度的 1/4 ✓：再宽就成"荷叶"而不是"花瓣"了 ✗（八瓣在尖端那一圈正好留出缝 ✗）
        double width=Math.pow(Math.sin(Math.min(1,u*1.06)*Math.PI),.72)*radius*.26*spread;
        double distance=radius*(.14+.86*u)*spread;
        double y=Math.sin(u*Math.PI*.58)*radius*.40*open+(.18+.22*u)-Math.max(0,u-.68)*radius*.55*open;
        return dir.scale(distance).add(tan.scale(v*width)).add(0,y-v*v*width*.18,0);
    }

    /**
     * **水穹顶 / 护盾**（t3"穹顶"、t4"蓝紫护盾"✗）：**实心半透明水壳** ✓，
     * 亮度沿纬度分层 ＋ 缓慢流动的环带 ＋ 几条亮经线 ＋ 两圈亮环 ✓。
     *
     * <p>原来是一个 **8 经 × 20 纬的线框球** ✗（7.7k 顶点 ✗，还是个笼子 ✗）。
     * 现在 16 经 × 5 纬 **实心面片** ⇒ 只有 320 顶点 ✓，而且读作"一层水膜罩着"✓。
     */
    static void waterDome(VertexConsumer out,Matrix4f pose,double radius,double height,int meridians,
                          double time,float r,float g,float b,float alpha) {
        int lat=5;
        for(int i=0;i<meridians;i++)for(int j=0;j<lat;j++) {
            double a=i*Math.PI*2/meridians,c=(i+1)*Math.PI*2/meridians;
            double t0=j/(double)lat*Math.PI*.5,t1=(j+1)/(double)lat*Math.PI*.5;
            Vec3 p00=domePoint(a,t0,radius,height),p01=domePoint(c,t0,radius,height);
            Vec3 p11=domePoint(c,t1,radius,height),p10=domePoint(a,t1,radius,height);
            // 越靠底越"厚"✗、顶上薄 ⇒ 不挡视线 ✓；再叠一条缓慢流动的环带 ✓
            float band=(float)(.72+.28*Math.sin(a*3+t0*4-time*.06));
            float f0=(float)(.20+.55*Math.sin(t0*Math.PI)),f1=(float)(.20+.55*Math.sin(t1*Math.PI));
            quad(out,pose,p00,p01,p11,p10,r*f0*band,g*f0*band,Math.min(1,b*f0*band+.06F),alpha*(.22F+.34F*f0));
        }
        // 亮经线（只留 6 条 ✗：全画就又变回笼子 ✗）
        for(int i=0;i<6;i++) {
            double a=i*Math.PI/3+time*.004;
            for(int j=0;j<lat;j++) {
                double t0=j/(double)lat*Math.PI*.5,t1=(j+1)/(double)lat*Math.PI*.5;
                Vec3 p0=domePoint(a,t0,radius,height),p1=domePoint(a,t1,radius,height);
                Vec3 w0=new Vec3(Math.cos(a),0,Math.sin(a)).scale(radius*.05),w1=w0.scale(Math.max(.2,Math.cos(t1)));
                quad(out,pose,p0,p0.add(w0),p1.add(w1),p1,r+.28F,g+.28F,1,alpha*.55F);
            }
        }
        for(int j=1;j<=2;j++) {
            double t=j/3.0*Math.PI*.5;
            ringColor(out,pose,new Vec3(0,height*Math.sin(t),0),UP,radius*Math.cos(t),radius*.03,r+.22F,g+.22F,1,alpha*.60F);
        }
    }

    private static Vec3 domePoint(double angle,double lat,double radius,double height) {
        return new Vec3(Math.cos(angle)*radius*Math.cos(lat),height*Math.sin(lat),Math.sin(angle)*radius*Math.cos(lat));
    }

    /**
     * **雨链专用的轻量发光法阵** ✓：**实心光面 ＋ 亮环 ＋ 12 个符文刻度 ＋ 6 道旋弧 ＋ 光晕** ⇒ 约 1.6k 顶点 ✓。
     *
     * <p>为什么不直接用 {@link #richCircle} ✗：那个是给近景四条链的主力阵 ✓（≈10k 顶点 ✗）。
     * 天洪要同时画**两层天空阵 ＋ 一层地面阵** ✗ ⇒ 三份 `richCircle` 就是 3 万顶点 ✗（原来是两份 ✗）。
     * 雨链这个版本观感上**更实**（填了光的面 ＋ 亮环 ＋ 符文 ✗）也更便宜 ✓。
     */
    static void rainSeal(MultiBufferSource buffers,PoseStack stack,double radius,double y,double time,float fade,
                         float r,float g,float b) {
        Vec3 center=new Vec3(0,y,0);
        var out=buffers.getBuffer(WaterRenderTypes.geometry());Matrix4f pose=stack.last().pose();
        // ① 实心光面：中心亮、外沿暗 ⇒ 一眼是"发着光的阵面"，不是一圈线 ✗
        int seg=48;
        for(int i=0;i<seg;i++) {
            double a=i*Math.PI*2/seg,c=(i+1)*Math.PI*2/seg;
            Vec3 pa=radial(new Vec3(1,0,0),new Vec3(0,0,1),a,1),pc=radial(new Vec3(1,0,0),new Vec3(0,0,1),c,1);
            quad(out,pose,center,center.add(pa.scale(radius*.42)),center.add(pc.scale(radius*.42)),center,r*.75F,g*.9F,1,fade*.16F);
            quad(out,pose,center.add(pa.scale(radius*.42)),center.add(pa.scale(radius)),center.add(pc.scale(radius)),center.add(pc.scale(radius*.42)),r*.6F,g*.8F,1,fade*.08F);
        }
        ringColor(out,pose,center,UP,radius,radius*.026,r+.15F,g+.15F,1,fade*.75F);
        ringColor(out,pose,center,UP,radius*.72,radius*.018,r+.10F,g+.10F,1,fade*.60F);
        ringColor(out,pose,center,UP,radius*.38,radius*.022,r+.25F,g+.25F,1,fade*.70F);
        // ② 加色层：光晕 + 亮环 + 符文刻度 + 旋弧 ✓（加色才会"发光"✗）
        // ⚠️ 旋弧用**面片**而不是 tube ✗：一根 tube 就是 96 顶点 ✗，30 根直接 3k ✗
        //（这正是旧雨链"又丑又贵"的同一个坑 ✗ —— 第九批的全部意义就是不再用细管堆细节 ✗）。
        Vec3 ex=new Vec3(1,0,0),ez=new Vec3(0,0,1);
        var glow=buffers.getBuffer(WaterRenderTypes.glow());Matrix4f gpose=stack.last().pose();
        float pulse=(float)(.74+.26*Math.sin(time*.05));
        for(int i=0;i<2;i++) {
            float t=i/2F;
            ringColor(glow,gpose,center,UP,radius*(1-t*.34),radius*(.18F+t*.05F),r,g,1,fade*(.060F+t*.022F));
        }
        ringColor(glow,gpose,center,UP,radius,radius*.020,r+.35F,g+.30F,1,fade*.55F*pulse);
        ringColor(glow,gpose,center,UP,radius*.95,radius*.014,r+.40F,g+.35F,1,fade*.45F*pulse);
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6-time*.008;
            Vec3 dir=radial(ex,ez,a,1),p=center.add(dir.scale(radius*.86));
            Vec3 w=radial(ex,ez,a+Math.PI/2,1).scale(radius*.035);
            quad(glow,gpose,p.subtract(w),p.add(w),p.add(w).subtract(dir.scale(radius*.05)),p.subtract(w).subtract(dir.scale(radius*.05)),r+.4F,g+.35F,1,fade*.60F*pulse);
        }
        for(int k=0;k<3;k++) {
            double rr=radius*(k==0?1:.66-.14*k),a0=time*.020*(k%2==0?1:-1.4)+k*2.1;
            for(int i=0;i<8;i++) {
                double a=a0+i*.075,c=a+.075;
                Vec3 p=center.add(radial(ex,ez,a,rr)),q=center.add(radial(ex,ez,c,rr));
                Vec3 po=center.add(radial(ex,ez,a,rr+radius*.022)),qo=center.add(radial(ex,ez,c,rr+radius*.022));
                quad(glow,gpose,p,q,qo,po,r+.4F,g+.35F,1,fade*(.45F-i*.035F)*pulse);
            }
        }
        disk(glow,gpose,center,UP,radius*.12,r+.3F,g+.3F,1,fade*.40F*pulse);
    }

    /**
     * **露珠**（t1 档位文案点名的"深蓝露珠"✗）：一颗悬着的小水珠 ✓
     * —— 8×3 面的**扁球**（按纬度明暗 ✓）＋ 顶部一圈高光 ⇒ 读作"珠子"而不是"小柱子"✓。
     *
     * <p>⚠️ 不用 `tube` 拼 ✗：一根 24 边管 + 两圈 `ringColor` 就是 800 顶点 ✗，
     * 七颗露珠直接 5.6k ✗（旧雨链那种"细节全靠细管堆"的坑 ✗）。扁球只要 128 顶点 ✓。
     */
    static void droplet(VertexConsumer out,Matrix4f pose,Vec3 at,double size,double time,
                        float r,float g,float b,float alpha) {
        // 轻微上下呼吸 + 依位置错相 ⇒ 露珠是"活"的 ✓
        Vec3 c=at.add(0,Math.sin(time*.06+at.x*1.7+at.z*2.3)*size*.25,0);
        int az=8,lat=3;
        for(int i=0;i<az;i++)for(int j=0;j<lat;j++) {
            double a=i*Math.PI*2/az,d=(i+1)*Math.PI*2/az;
            double t0=-Math.PI/2+j/(double)lat*Math.PI,t1=-Math.PI/2+(j+1)/(double)lat*Math.PI;
            Vec3 p00=beadPoint(c,a,t0,size),p01=beadPoint(c,d,t0,size),p11=beadPoint(c,d,t1,size),p10=beadPoint(c,a,t1,size);
            float f0=(float)(.42+.58*Math.cos(t0)),f1=(float)(.42+.58*Math.cos(t1));
            quad(out,pose,p00,p01,p11,p10,r*f0,g*f0,Math.min(1,b*f0+.12F),alpha*(.42F+.36F*f0));
        }
        // 顶部高光：一个 8 片的小扇面（退化四边形 ⇒ 三角 ✗，与 disk 同一个手法 ✓）
        double capLat=Math.PI/2*.55;
        Vec3 apex=c.add(0,size*.88,0);
        for(int i=0;i<az;i++) {
            double a=i*Math.PI*2/az,d=(i+1)*Math.PI*2/az;
            quad(out,pose,beadPoint(c,a,capLat,size),beadPoint(c,d,capLat,size),apex,apex,1,1,1,alpha*.50F);
        }
    }

    private static Vec3 beadPoint(Vec3 c,double angle,double lat,double size) {
        return c.add(Math.cos(angle)*Math.cos(lat)*size,Math.sin(lat)*size*.92,Math.sin(angle)*Math.cos(lat)*size);
    }
}
