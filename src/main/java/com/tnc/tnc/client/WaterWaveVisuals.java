package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.magic.water.WaterSpellRules;
import com.tnc.tnc.magic.water.OceanWaveRules;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 水纹链的浪体与浪唇（Alex's Caves 那种分层卷浪 ✗）。
 *
 * <h2>2026-10-10 第十批：为什么整条链的浪都要改</h2>
 * 作者："**水法全部都不流畅啊，t 级越高的法术越让我觉得卡**"✗ ——
 * 拿 {@code WaterVisualsCostTest}（新加的顶点计数器 ✗）一量，最贵的一发就是这个文件：
 * **灭世之海六道浪合计 81,408 顶点** ✗✗（半径 64 格、持续 30 秒 ✗）。
 *
 * <p>钱花在两处 ✗：
 * <ol>
 *   <li>"画一条线"用的是 {@code tube} ✗：半径 ≥ .1 就是 **24 边＝96 顶点** ✗
 *       ⇒ 浪唇/泡沫/浪花那一圈圈细线比浪体本身还贵 ✗；</li>
 *   <li>每个四边形都要算 **4 次**剖面 ✗（剖面是这个函数里最贵的一步 ✗）。</li>
 * </ol>
 * 本批的改法：**亮线一律换成 4 顶点的亮带** ✓（{@link WaterGeometry#stroke} 同一个思路 ✗）、
 * **逐段缓存剖面列** ✓（每个四边形只算 2 次 ✓）、段数按"玩家分辨不出来"的下限给 ✓。
 * 观感不变 ✗（浪还是那道浪、浪唇还是那条亮边 ✓），顶点数少 6 倍以上 ✓。
 */
final class WaterWaveVisuals {
    /** 角向段数 ✓：96 → **48** ✗（半径 64 的浪一段 8.4 格 ✓，曲面照样圆滑 ✓，顶点数直接减半 ✓）。 */
    private static final int WAVE_SEGMENTS = 48;
    /** 纵向层数上限 ✓：10 → **8** ✗（层数＝overdraw 层数 ✗：半透明不写深度 ✗）。 */
    private static final int WAVE_LAYERS = 8;

    /** Full 360-degree breaker: deep sloping belly, forward lip, then an inward-curled foam edge. */
    static void breakingWave(VertexConsumer out,Matrix4f pose,double front,double height,double limit,double age,float alpha,float[] tint) {
        if(front<=0 || height<=0 || alpha<=0)return;
        int segments=WAVE_SEGMENTS,points=Math.min(WAVE_LAYERS,OceanWaveRules.profilePoints());
        double[] radius=new double[points],lift=new double[points],radiusNext=new double[points],liftNext=new double[points];
        double[] profileHeight=new double[points];
        for(int j=0;j<points;j++) {
            radius[j]=radiusNext[j]=OceanWaveRules.profileRadius(j,front,height,limit);
            profileHeight[j]=OceanWaveRules.profileHeight(j,height);
        }
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            // 逐段只算两列剖面 ✓（原来是每个四边形 4 次 ✗）
            double waveLift=OceanWaveRules.waveLift(a,age),waveLiftNext=OceanWaveRules.waveLift(b,age);
            for(int j=0;j<points;j++) {
                lift[j]=profileHeight[j]*waveLift;
                liftNext[j]=profileHeight[j]*waveLiftNext;
            }
            for(int j=0;j<points-1;j++) {
                float t=j/(float)(points-1);
                // 纵向渐变：浪腹用这道浪自己的色相 ✗、浪唇推近白 ✓（作者 2026-10-10："每个波的颜色可以有渐变"）
                float cr=tint[0]+(.92F-tint[0])*t, cg=tint[1]+(.98F-tint[1])*t, cb=tint[2]+(1-tint[2])*t;
                WaterGeometry.quad(out,pose,polar(a,radius[j],lift[j]),polar(b,radiusNext[j],liftNext[j]),
                        polar(b,radiusNext[j+1],liftNext[j+1]),polar(a,radius[j+1],lift[j+1]),cr,cg,cb,alpha*(.32F+t*.38F));
            }
            // 浪唇亮边：4 顶点的竖亮带 ✓（原来每个角段一根 24 边管＝96 顶点 ✗ ⇒ 48 段就是 4.6k ✗）
            rippleBand(out,pose,a,b,radius[points-1],lift[points-1],radiusNext[points-1],liftNext[points-1],.085*3,.72F,.96F,1,alpha);
            if(i%4==0)
                flick(out,pose,a,radius[points-1],lift[points-1]+.35+Math.sin(age*.2+a*9)*.2,.06*3,.85F,.98F,1,alpha*.8F);
            // Broken white foam bands on the turning crest reveal the roll direction.
            if(i%2==0) {
                int k=Math.min(points-1,7);
                rippleBand(out,pose,a,b,radius[k],lift[k],radiusNext[k],liftNext[k],.075*3,.55F,.9F,1,alpha*.8F);
            }
            if(i%6==0) {
                // 浪花：从浪唇被甩出去的一撮 ✓（随时间起伏 ⇒ 不是固定装饰 ✗）
                double f=Math.sin(age*.22+a*7);
                if(f>0) {
                    flick(out,pose,a,radius[points-1],lift[points-1]+.5+f*.9,.06*3,.9F,.99F,1,alpha*.75F);
                    flick(out,pose,a,radius[points-1],lift[points-1]+1.0+f*1.3,.035*3,1,1,1,alpha*.55F);
                }
            }
        }
    }

    /** 每道浪前在一条"由深到亮"的渐变上取色 ✓（越新的浪越亮 ✗）。 */
    static float[] frontTint(int slot) {
        int n=Math.max(2,OceanWaveRules.MAX_FRONTS);
        float t=Math.min(1,slot/(float)(n-1));
        return new float[]{.02F+t*.46F,.16F+t*.66F,.52F+t*.46F};
    }

    private static Vec3 polar(double angle,double radius,double y) { return new Vec3(Math.cos(angle)*radius,y,Math.sin(angle)*radius); }

    /** 沿浪面的一条**竖亮带** ✓（半径对之间的一片 4 顶点面 ✗，不是一根 tube ✗）。 */
    private static void rippleBand(VertexConsumer out,Matrix4f pose,double a1,double a2,double r1,double y1,double r2,double y2,
                                   double half,float cr,float cg,float cb,float alpha) {
        WaterGeometry.quad(out,pose,polar(a1,r1,y1-half),polar(a2,r2,y2-half),polar(a2,r2,y2+half),polar(a1,r1,y1+half),cr,cg,cb,alpha);
    }

    /** 浪花/水沫的一小撮**上飘亮带** ✓（横向窄、纵向拉长 ✗，仍然是 4 顶点 ✓）。 */
    private static void flick(VertexConsumer out,Matrix4f pose,double angle,double radius,double y,double half,
                              float cr,float cg,float cb,float alpha) {
        double across=radius>0?Math.max(.05,half/radius):.05;
        WaterGeometry.quad(out,pose,polar(angle-across,radius,y),polar(angle,radius+half,y+half*.6),
                polar(angle,radius+half,y+half*1.8),polar(angle,radius,y+half*1.2),cr,cg,cb,alpha);
    }

    private static Vec3 breakerProfile(double angle,int point,double front,double height,double limit,double age) {
        double r=OceanWaveRules.profileRadius(point,front,height,limit);
        double y=OceanWaveRules.profileHeight(point,height)*OceanWaveRules.waveLift(angle,age);
        return new Vec3(Math.cos(angle)*r,y,Math.sin(angle)*r);
    }
    static void crescent(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double r,double time,float alpha,double roll) {
        // 刃面基绕飞行轴滚转 roll ✓ ⇒ 同一批斩击不再全是竖着的 C ✓（作者 2026-10-10 ✓）
        Vec3[] basis=WaterGeometry.rolledBasis(dir,roll);Vec3 right=basis[0],up=basis[1];
        for(int i=0;i<40;i++) {
            double a=-Math.PI*.55+i*Math.PI*1.1/40,b=-Math.PI*.55+(i+1)*Math.PI*1.1/40;
            Vec3 p=center.add(WaterSpellRules.slashArc(dir,roll,a,r)),q=center.add(WaterSpellRules.slashArc(dir,roll,b,r));
            double innerA=r*(.78+.18*Math.pow(Math.abs(a)/(Math.PI*.55),2)),innerB=r*(.78+.18*Math.pow(Math.abs(b)/(Math.PI*.55),2));
            Vec3 u=q.subtract(dir.scale(.08+.80*Math.pow(Math.cos(b),2))),v=p.subtract(dir.scale(.08+.80*Math.pow(Math.cos(a),2)));
            Vec3 depth=up.scale(.18);
            WaterGeometry.quad(out,pose,p,q,u,v,.05F,.5F,.95F,alpha*.6F);
            WaterGeometry.quad(out,pose,p.subtract(depth),v.subtract(depth),u.subtract(depth),q.subtract(depth),.12F,.75F,1,alpha*.45F);
            WaterGeometry.quad(out,pose,p,q,q.subtract(depth),p.subtract(depth),.6F,.95F,1,alpha*.85F);
            // 刃口亮线：一片亮带 ✓（原来半径 .055 的 tube ✗ —— 那是 4 边 ✗，也已经很便宜 ✓，这里再省一半 ✓）
            WaterGeometry.stroke(out,pose,up,p,q,.055,.85F,.98F,1,alpha);
            if(i%4==0)WaterGeometry.stroke(out,pose,up,p.add(dir.scale(.08+Math.sin(time*.3+i)*.08)),p.add(dir.scale(.08+Math.sin(time*.3+i)*.08)).add(up.scale(.16)),.045,1,1,1,alpha*.65F);
        }
    }
    static void rollingWave(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double height,double time,float alpha,double bulk) {
        Vec3 right=WaterSpellRules.right(dir),up=new Vec3(0,1,0);
        // 2026-10-10 作者："水波的那个模型也可以改改，精细多一点，加点浪花的感觉" ⇒ 段数 40→64、层数 6→10 ✗
        //           "水波的 t4 水柱可以加粗一倍" ⇒ 浪体厚度与浪花水柱都 ×bulk ✓（t4 传 2 ✓、t2 传 1 ✓）
        // 第十批：段数 64→48 ✓、层数 10→6 ✓ —— 半透明浪体是**按层填充**的 ✗，10 层就是 10 次 overdraw ✗；
        // 48×6 在半径 8～22 的浪上仍然圆滑 ✓（作者要的"精细"主要体现在浪舌/浪花/亮边 ✓，那些都留着 ✓）。
        int segments=48,layers=6;
        double depth=1.6*bulk, fat=1+(bulk-1)*.8;
        for(int i=0;i<segments;i++) {
            double a=-Math.PI*.48+i*Math.PI*.96/segments,b=-Math.PI*.48+(i+1)*Math.PI*.96/segments;
            Vec3 prev=null,prevNext=null;
            for(int j=0;j<=layers;j++) {
                Vec3 pA=profile(center,dir,right,up,a,j/(double)layers,radius,height,time,depth);
                Vec3 pB=profile(center,dir,right,up,b,j/(double)layers,radius,height,time,depth);
                if(j>0) {
                    double t0=(j-1)/(double)layers;
                    WaterGeometry.quad(out,pose,prev,prevNext,pB,pA,.05F,.4F+(float)t0*.045F,.93F,alpha*(.16F+(float)t0*.042F));
                    if(j==layers) {
                        // 浪冠白线 + 向前下方翻卷的浪舌 ✓（原来只有一条线，完全没有"卷"的感觉 ✗）
                        WaterGeometry.stroke(out,pose,up,prev,pA,.055*(float)fat,.92F,.99F,1,alpha);
                        double tongue=.42*(1+(bulk-1)*.5), drop=.30*(1+(bulk-1)*.5);
                        Vec3 u2=prev.add(dir.scale(tongue)).subtract(0,drop,0),v2=pA.add(dir.scale(tongue)).subtract(0,drop,0);
                        WaterGeometry.quad(out,pose,prev,pA,v2,u2,.35F,.85F,1,alpha*.55F);
                        WaterGeometry.quad(out,pose,u2,v2,pA,prev,.90F,1,1,alpha*.45F);
                    }
                }
                prev=pA;prevNext=pB;
            }
            // 浪花：每隔几段从浪冠甩出去的一撮 ✓（随时间起伏 ⇒ 不是固定装饰 ✗）
            if(i%3==0) {
                Vec3 crest=profile(center,dir,right,up,a,1,radius,height,time,depth);
                double f=Math.sin(time*.28+i*1.1);
                if(f>0) {
                    double lift=Math.max(1,height*.30);
                    Vec3 tip=crest.add(0,.35+f*.60*lift,0).add(dir.scale((.20+f*.25)*(1+(bulk-1)*.4)));
                    WaterGeometry.stroke(out,pose,up,crest,tip,.05F*(float)bulk,.90F,.99F,1,alpha*.80F);
                    WaterGeometry.stroke(out,pose,up,tip,tip.add(0,.22+f*.30,0),.032F*(float)bulk,1,1,1,alpha*.60F);
                }
            }
            // 内侧一层薄水体 ⇒ 浪有厚度，不是一张纸 ✗
            if(i%4==0) {
                Vec3 p=profile(center,dir,right,up,a,.2,radius,height,time,depth),q=profile(center,dir,right,up,b,.2,radius,height,time,depth);
                Vec3 v=profile(center,dir,right,up,b,.6,radius,height,time,depth),u=profile(center,dir,right,up,a,.6,radius,height,time,depth);
                WaterGeometry.quad(out,pose,p,q,v,u,.02F,.30F,.88F,alpha*.22F);
            }
        }
    }
    private static Vec3 profile(Vec3 center,Vec3 dir,Vec3 right,Vec3 up,double a,double t,double r,double h,double time) {
        return profile(center,dir,right,up,a,t,r,h,time,1.6);
    }
    /**
     * @param depth 浪体前后厚度 ✓ —— 2026-10-10 作者："水波的 t4 水柱可以加粗一倍" ✗
     *              ⇒ t4 传 2 倍（1.6 → 3.2 格）✓，t2 仍是 1 倍 ✓。
     */
    private static Vec3 profile(Vec3 center,Vec3 dir,Vec3 right,Vec3 up,double a,double t,double r,double h,double time,double depth) {
        double crest=h*Math.cos(a)*(1+.04*Math.sin(a*9+time*.15));
        return center.add(right.scale(Math.sin(a)*r)).add(up.scale(.05+crest*Math.sin(t*Math.PI*.55)))
                .add(dir.scale(Math.cos(a)*depth+Math.sin(t*Math.PI*1.5)*depth*.5));
    }
    static void circularWave(VertexConsumer out,Matrix4f pose,double radius,double height,double age,float alpha) {
        // 第十批：64 段 × 6 层 ＋ 每段一根 **半径 .14 的 tube（24 边＝96 顶点 ✗）** ⇒ 实测 7,680 ✗
        // ⇒ 段数 48 ✓、浪冠改用 **stroke 亮带（4 顶点 ✓）** ⇒ 约 1.3k ✓（少 5.7 倍 ✗）
        int segments=48,layers=6;
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            Vec3 prev=null,prevNext=null;
            for(int j=0;j<=layers;j++) {
                Vec3 pA=circularProfile(a,j/(double)layers,radius,height,age),pB=circularProfile(b,j/(double)layers,radius,height,age);
                if(j>0) {
                    double t0=(j-1)/(double)layers;
                    WaterGeometry.quad(out,pose,prev,prevNext,pB,pA,.005F,.12F+(float)t0*.045F,.9F,alpha*(.3F+(float)t0*.06F));
                    if(j==layers)WaterGeometry.stroke(out,pose,new Vec3(0,1,0),prev,pA,.14,.2F,.72F,1,alpha);
                }
                prev=pA;prevNext=pB;
            }
        }
    }
    private static Vec3 circularProfile(double a,double t,double radius,double height,double age) {
        double r=Math.max(.1,radius+Math.sin(t*Math.PI*1.5)*.9);
        return new Vec3(Math.cos(a)*r,.1+height*Math.sin(t*Math.PI*.55)*(.9+.1*Math.sin(a*7+age*.16)),Math.sin(a)*r);
    }

    /* ---- 加色发光层（2026-10-10 第二批次 · 水纹链）----
       只画"浪唇/环边"那一条亮线 ✗，主体水体仍走原来的 alpha 层 ✓：
       加色混合会把重叠处推白 ⇒ 浪唇像被光打亮，而不是整片水变浅蓝 ✗。 */

    /** 地面扩散环的加色亮边（t1 水纹）✓。 */
    static void rippleGlow(VertexConsumer out,Matrix4f pose,double radius,double age,float alpha) {
        for(int i=0;i<2;i++) {
            double r=radius-i*.18;
            if(r<=0)continue;
            WaterGeometry.ringColor(out,pose,new Vec3(0,.13+i*.10,0),new Vec3(0,1,0),r,.12,
                    .55F,.95F,1,alpha*(.55F-i*.20F),32);
        }
    }

    /** 卷浪浪唇的加色亮线（t2 水波 / t4 海啸）✓ —— 与 {@link #rollingWave} 用同一个 profile ✗。 */
    static void rollingGlow(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double radius,double height,double time,float alpha,double bulk) {
        Vec3 right=WaterSpellRules.right(dir),up=new Vec3(0,1,0);
        for(int i=0;i<40;i++) {
            double a=-Math.PI*.48+i*Math.PI*.96/40,b=-Math.PI*.48+(i+1)*Math.PI*.96/40;
            Vec3 p=profile(center,dir,right,up,a,1,radius,height,time,1.6*bulk),q=profile(center,dir,right,up,b,1,radius,height,time,1.6*bulk);
            WaterGeometry.stroke(out,pose,up,p,q,.09,1,1,1,alpha*.50F);
            if(i%5==0)WaterGeometry.stroke(out,pose,up,p,p.add(up.scale(.5)),.06,.75F,1,1,alpha*.40F);
        }
    }

    /** 薄刃边缘的加色亮线（t3 海浪斩击）✓。 */
    static void crescentGlow(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 dir,double r,double time,float alpha,double roll) {
        // 与 crescent 用同一套滚转基 ✓，否则亮线会和刃面错开 ✗
        Vec3[] basis=WaterGeometry.rolledBasis(dir,roll);Vec3 right=basis[0],up=basis[1];
        for(int i=0;i<20;i++) {
            double a=-Math.PI*.55+i*Math.PI*1.1/20,b=-Math.PI*.55+(i+1)*Math.PI*1.1/20;
            Vec3 p=center.add(WaterSpellRules.slashArc(dir,roll,a,r)),q=center.add(WaterSpellRules.slashArc(dir,roll,b,r));
            WaterGeometry.stroke(out,pose,up,p,q,.05,1,1,1,alpha*.60F);
            if(i%5==0)WaterGeometry.stroke(out,pose,up,p,p.add(dir.scale(.45)),.035,.8F,1,1,alpha*.45F);
        }
    }

    /**
     * 灭世之海浪唇的加色亮线（t5）✓ —— 与 {@link #breakingWave} 共用同一条剖面 ✗。
     *
     * <p>第十批：原来每段一根 **半径 .10 的 tube** ✗ —— `.10 < .1` 是 false ✗ ⇒ 走 24 边＝**96 顶点** ✗
     * ⇒ 六道浪的光晕层实测 38,400 顶点 ✗（比浪体还贵 ✗）。现在改成 **stroke 亮带（4 顶点 ✓）** ⇒ 约 2.5k ✓。
     */
    static void breakingGlow(VertexConsumer out,Matrix4f pose,double front,double height,double limit,double age,float alpha,float[] tint) {
        if(front<=0||height<=0||alpha<=0)return;
        int segments=WAVE_SEGMENTS,crest=Math.min(WAVE_LAYERS,OceanWaveRules.profilePoints())-1;
        // 每道浪的亮线带一点自己的色相 ✓（不然六道浪的浪唇全长一样 ✗）
        float r=tint[0]+(.95F-tint[0])*.6F, g=tint[1]+(1-tint[1])*.6F, b=tint[2]+(1-tint[2])*.6F;
        for(int i=0;i<segments;i++) {
            double a=i*Math.PI*2/segments,bb=(i+1)*Math.PI*2/segments;
            Vec3 p=breakerProfile(a,crest,front,height,limit,age),q=breakerProfile(bb,crest,front,height,limit,age);
            WaterGeometry.stroke(out,pose,new Vec3(0,1,0),p,q,.10,r,g,b,alpha*.55F);
            if(i%4==0)WaterGeometry.stroke(out,pose,new Vec3(0,1,0),p,p.add(0,.6,0),.06,.80F,1,1,alpha*.42F);
        }
    }
}
