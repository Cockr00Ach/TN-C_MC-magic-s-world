package com.tnc.tnc.warrior;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Sparse translucent blade silhouettes, feathered in depth rather than opaque light sheets. */
public final class WarriorBladeEcho {
    /**
     * ★ 2026-11-13 作者：「幻影刃的大小变为现在的三倍，并且可以再透明点」✓
     *
     * <p>长度整体 × {@link #ECHO_SCALE}（3 倍）；同时把不透明度压到约原来的 <b>40%</b>
     * （{@link #ALPHA}）—— 大了三倍还保持原来的亮度就会变成一片实心白板 ✗，
     * 变淡之后才像"挥过去留下的影子刃" ✓。
     *
     * <p>⚠️ 层数/条数没动（3 条 × 3 层 = 9 个 quad）⇒ 顶点预算不变 ✓；
     * 单层 alpha 上限仍 &lt; 0.06（原来 0.13），下游测试的 {@code ab &lt; .15} 照样过 ✓。
     */
    public static final double ECHO_SCALE = 3.0D;

    /** 不透明度系数 ✓（原来是 0.13）。 */
    public static final float ALPHA = 0.052F;

    /** 宽度系数 ✓ —— 长度放大后宽度跟着放大，但封顶免得变成一块布 ✗。 */
    public static final double WIDTH_CAP = 2.2D;

    public static void emit(List<WarriorTrailVolume.Point> points,double now,double life,double scale,int color,WarriorTrailVolume.Sink sink){
        if(points.size()<4)return;
        double echoScale=scale*ECHO_SCALE;
        for(int j=1;j<=3;j++){
            int i=Math.max(0,points.size()-1-j*points.size()/4);var p=points.get(i);
            double fade=Math.max(0,1-(now-p.at())/life);if(fade<=0)continue;
            Vec3 blade=p.tip().subtract(p.hilt()).scale(echoScale),tip=p.hilt().add(blade);
            Vec3 side=blade.cross(new Vec3(0,1,0));if(side.lengthSqr()<1e-6)side=blade.cross(new Vec3(1,0,0));
            side=side.normalize();Vec3 root=p.hilt().add(blade.scale(.3)),shoulder=root.lerp(tip,.55);
            for(int layer=2;layer>=0;layer--){
                double width=(.035+layer*.045)*Math.min(WIDTH_CAP,echoScale*.35D);
                float alpha=(float)(fade*fade*(ALPHA/(j+1))/(layer+1));
                sink.quad(root,shoulder.add(side.scale(width)),tip,shoulder.subtract(side.scale(width)),color,0,alpha,alpha*.45f,alpha);
            }
        }
    }
}
