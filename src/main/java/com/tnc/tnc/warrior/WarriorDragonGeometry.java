package com.tnc.tnc.warrior;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** A carved, articulated block dragon: segmented armour, paws, claws, jaws and forked antlers.
 * Pure production geometry, also exported by the unit test for offline visual inspection.
 */
public final class WarriorDragonGeometry {

    /** 从出现到消失的 tick 数：前 {@link #TRAVEL} tick 冲出去，之后整条停住淡出。 */
    public static final int LIFE = 18;
    /** 龙头推进占用的 tick 数。 */
    public static final int TRAVEL = 10;
    /** 沿龙身的采样段数。 */
    public static final int SAMPLES = 40;
    /** 龙长（格）。 */
    public static final double LENGTH = 7.5;

    public static final int SHELL = 0x48272E;
    public static final int BODY = 0x9F5261;
    public static final int CORE = 0xDDC9A2;

    /** 一个上色三角形：渲染器三个顶点，预览脚本三个点。 */
    public record Tri(Vec3 a, Vec3 b, Vec3 c, int color, float alpha) {
    }

    private WarriorDragonGeometry() {
    }

    /** 龙身中心线：向前推 + 左右蛇摆 + 先抬后落的弧线。 */
    public static Vec3 path(Vec3 origin, Vec3 dir, Vec3 side, double s) {
        double wave = Math.sin(s * Math.PI * 2.4) * (0.55 + 0.9 * s);
        double rise = Math.sin(s * Math.PI) * 1.15 - 0.35 * s;
        return origin.add(dir.scale(LENGTH * s)).add(side.scale(wave)).add(0, rise, 0);
    }

    /** 局部切向；{@code head} 是龙头的 s，越靠近龙头步长越小，免得把曲线末端抹平。 */
    public static Vec3 tangent(Vec3 origin, Vec3 dir, Vec3 side, double s, double head) {
        double step = Math.min(0.02, Math.max(0.002, (head - s) * 0.5 + 0.01));
        Vec3 back = path(origin, dir, side, Math.max(0, s - step));
        Vec3 ahead = path(origin, dir, side, Math.min(head, s + step));
        Vec3 tangent = ahead.subtract(back);
        return tangent.lengthSqr() < 1.0E-8 ? dir : tangent.normalize();
    }

    /** 半径：尾细、中段最粗、龙头收一点。 */
    public static double radius(double s) {
        double taper = Math.sin(Math.PI * Math.pow(Math.max(0, s), 0.75));
        return 0.035 + 0.30 * Math.max(0, taper) + .19 * Math.max(0, Math.min(1, (s-.72)/.28));
    }

    /** 龙头进度 0..1。 */
    public static double head(double age) {
        return Math.max(0, Math.min(1, age / TRAVEL));
    }

    /** 整体透明度：最后 8 tick 淡出。 */
    public static float fade(double age) {
        return (float) Math.max(0, Math.min(1, (LIFE - age) / 8.0));
    }

    /**
     * 把一条龙变成三角形列表。{@code head} 是龙头进度（0..1），{@code fade} 是整体透明度。
     */
    public static List<Tri> build(Vec3 origin, Vec3 dir, Vec3 side, double head, float fade) {
        List<Tri> out = new ArrayList<>(5000);
        if (head <= .01) return out;
        for (int i=0;i<SAMPLES;i++) {
            double s=head*(i+.5)/SAMPLES;
            Vec3 point=path(origin,dir,side,s), forward=tangent(origin,dir,side,s,head);
            Vec3 right=new Vec3(-forward.z,0,forward.x).normalize();
            Vec3 up=right.cross(forward).normalize();
            double half=head/(SAMPLES*2.0);
            double length=path(origin,dir,side,s-half).distanceTo(path(origin,dir,side,s+half))*1.35;
            double r=radius(s);
            float alpha=fade*(float)(.65+.3*i/SAMPLES);
            block(out,point,right,up,forward,0,0,0,r*1.8,r*1.65,length,BODY,alpha);
            block(out,point,right,up,forward,0,-r*.8,0,r*1.15,r*.28,length*.8,CORE,alpha);
            // Individually fitted scale courses, a segmented mane and dorsal crest.
            for(int sign:new int[]{-1,1})for(int row=0;row<2;row++)
                block(out,point,right,up,forward,sign*r*(.78+row*.06),r*(.25-row*.6),length*.10,
                      r*.32,r*.42,length*.72,row==0?0xA85562:SHELL,alpha);
            block(out,point,right,up,forward,0,r*.94,-length*.1,r*.38,r*.45,length*.5,0xD5B27B,alpha);
            if(i%4==0)for(int sign:new int[]{-1,1})
                block(out,point,right,up,forward,sign*r*.4,r*.72,0,r*.2,r*.3,length*.45,CORE,alpha);
            // Two pairs of folded legs, jointed paws and three separate claws.
            if(i==SAMPLES-7 || i==SAMPLES-18)for(int sign:new int[]{-1,1}) {
                block(out,point,right,up,forward,sign*r*1.3,-r*.5,0,.25,.25,.38,SHELL,alpha);
                block(out,point,right,up,forward,sign*r*1.7,-r*.95,.15,.2,.35,.23,BODY,alpha);
                for(int claw=0;claw<3;claw++)block(out,point,right,up,forward,
                        sign*r*1.7+(claw-1)*.09,-r*1.13,.33,.055,.08,.25,CORE,alpha);
            }
        }
        carvedHead(out,origin,dir,side,head,fade);
        return out;
    }

    private static void carvedHead(List<Tri> out,Vec3 origin,Vec3 dir,Vec3 side,double head,float fade) {
        Vec3 at=path(origin,dir,side,head),f=tangent(origin,dir,side,head,head);
        Vec3 r=new Vec3(-f.z,0,f.x).normalize(),u=r.cross(f).normalize();
        block(out,at,r,u,f,0,.05,0,.66,.50,.74,0x713B49,fade);
        block(out,at,r,u,f,0,.22,-.1,.48,.20,.56,0xA85562,fade);
        block(out,at,r,u,f,0,-.07,.48,.48,.24,.50,0xA85562,fade);
        block(out,at,r,u,f,0,-.23,.37,.46,.13,.58,0xD5B27B,fade);
        block(out,at,r,u,f,0,-.12,.64,.50,.12,.12,SHELL,fade);
        for(int sign:new int[]{-1,1}) {
            block(out,at,r,u,f,sign*.32,.13,.24,.12,.19,.25,SHELL,fade);
            block(out,at,r,u,f,sign*.385,.15,.29,.035,.085,.12,CORE,fade);
            block(out,at,r,u,f,sign*.37,.27,.16,.22,.12,.39,0xD5B27B,fade);
            // Antlers fork into a tall rear tine and a shorter forward branch.
            for(int j=0;j<5;j++)block(out,at,r,u,f,sign*(.22+j*.055),.38+j*.105,-.22-j*.095,
                    .13-j*.012,.15,.17,0xD5B27B,fade);
            for(int j=0;j<3;j++)block(out,at,r,u,f,sign*(.32+j*.055),.53+j*.085,-.23+j*.09,.09,.13,.13,CORE,fade);
            for(int j=0;j<5;j++)block(out,at,r,u,f,sign*(.25+j*.085),-.08+j*.035,.49-j*.065,.11,.07,.11,0xD5B27B,fade);
            for(int j=0;j<3;j++)block(out,at,r,u,f,sign*.18,-.15,.35+j*.12,.05,.10,.06,0xE8D8B8,fade);
            for(int j=0;j<4;j++)block(out,at,r,u,f,sign*(.38+j*.055),.05-j*.06,-.25-j*.12,.16,.15,.20,SHELL,fade);
        }
    }

    /** Actual box faces, with directional pixel-art shading; no overlapping glow tubes. */
    private static void block(List<Tri> out,Vec3 at,Vec3 r,Vec3 u,Vec3 f,double x,double y,double z,
                              double w,double h,double d,int color,float alpha) {
        Vec3[] p=new Vec3[8];
        for(int i=0;i<8;i++)p[i]=at.add(r.scale(x+((i&1)==0?-.5:.5)*w))
                .add(u.scale(y+((i&2)==0?-.5:.5)*h)).add(f.scale(z+((i&4)==0?-.5:.5)*d));
        int[][] faces={{0,2,3,1},{4,5,7,6},{0,4,6,2},{1,3,7,5},{0,1,5,4},{2,6,7,3}};
        double[] shades={.78,.90,.70,.84,.60,1};
        for(int i=0;i<faces.length;i++) {
            int[] q=faces[i];double shade=shades[i];
            int c=((int)((color>>16&255)*shade)<<16)|((int)((color>>8&255)*shade)<<8)|(int)((color&255)*shade);
            out.add(new Tri(p[q[0]],p[q[1]],p[q[2]],c,alpha));
            out.add(new Tri(p[q[0]],p[q[2]],p[q[3]],c,alpha));
        }
    }

}
