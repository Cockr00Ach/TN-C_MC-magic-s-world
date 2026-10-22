package com.tnc.tnc.arcana.client;

import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.mana.sight.client.ManaSightParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Sky light converges into a readable celestial apparition before entering the collector. */
public final class CelestialCollectorVisual {
    public static void tick(RouteNodeEntity node, boolean sight) {
        var level=node.getLevel(); var mc=Minecraft.getInstance();
        if(level==null||mc.player==null||node.getPersistentData().getLong("CelestialFlowUntil")<level.getGameTime()
                ||node.getPersistentData().getInt("CelestialYield")<=0)return;
        Vec3 bowl=Vec3.atLowerCornerOf(node.getBlockPos()).add(.5,1,.5);
        if(mc.player.distanceToSqr(bowl)>6400)return;
        boolean sun=node.kind()==RouteKind.SOLAR;
        double time=level.getGameTime(),angle=level.getSunAngle(0);
        // Match vanilla sky's Y(-90) * X(sunAngle): sunrise is east, sunset west.
        Vec3 sky=new Vec3(-Math.sin(angle),Math.cos(angle),0).scale(sun?1:-1).normalize();
        Vec3 focus=bowl.add(sky.scale(2.2)).add(0,1.4,0);
        Vec3 facing=mc.gameRenderer.getMainCamera().getPosition().subtract(focus).normalize();
        Vec3 u=facing.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();
        Vec3 v=u.cross(facing).normalize();
        int grains=sight?56:12;double radius=sight?1.15:.52;
        if(sun){
            // A bright disc edge, rotating solar prominences and inward gold filaments.
            for(int i=0;i<grains;i++){
                double a=i*Math.PI*2/grains+time*.006;
                Vec3 p=point(focus,u,v,a,radius);
                mote(p,p.add(u.scale(-Math.sin(a)*.035)).add(v.scale(Math.cos(a)*.035)),i%7==0?0xfff3bf:0xffbc53,i%9==0?1:0,sight?.045f:.025f,20);
                if(sight){double ray=1+.17*Math.pow(Math.sin(a*8+time*.025),2);Vec3 q=point(focus,u,v,a,radius*ray);mote(q,p,0xf68b38,0,.034f,22);}
            }
            if(sight)for(int arm=0;arm<5;arm++)for(int i=0;i<7;i++){
                double t=(time*.009+i/7.)%1,a=arm*Math.PI*.4+t*1.2;
                Vec3 origin=focus.add(sky.scale(7*(1-t))).add(u.scale(Math.cos(a)*(1-t)*1.8)).add(v.scale(Math.sin(a)*(1-t)*1.8));
                mote(origin,origin.lerp(focus,.14),i%5==0?0xfff2b3:0xeebc67,i%5==0?1:0,.055f,24);
            }
        }else{
            // Phase terminator: full moon is a silver disc; new moon keeps only a faint rim.
            double illuminated=(1+Math.cos(level.getMoonPhase()*Math.PI/4))/2;
            for(int i=0;i<grains;i++){
                double a=i*Math.PI*2/grains;
                Vec3 edge=point(focus,u,v,a,radius);
                mote(edge,edge,0xa9cde8,0,sight?.031f:.018f,18);
                if(sight&&illuminated>.03){double y=Math.sin(a),extent=Math.sqrt(Math.max(0,1-y*y));
                    double x=extent*(1-2*illuminated)*(level.getMoonPhase()<4?1:-1);
                    Vec3 terminator=focus.add(u.scale(x*radius)).add(v.scale(y*radius));
                    mote(terminator,terminator,0xe0edff,0,.038f,20);
                    double sweep=(time*.013+i*.618)%1,side=level.getMoonPhase()<4?1:-1;
                    Vec3 fill=focus.add(u.scale((x+(side*extent-x)*sweep)*radius)).add(v.scale(y*radius));
                    mote(fill,fill.add(v.scale(-.015)),0x9bbbea,i%11==0?1:0,.027f,18);
                }
            }
            if(sight)for(int i=0;i<30;i++){
                double t=(time*.007+i/30.)%1,a=i*2.399+time*.003;
                Vec3 p=focus.add(sky.scale((1-t)*5)).add(u.scale(Math.cos(a)*(1-t)*2.1)).add(v.scale(Math.sin(a)*(1-t)*1.3));
                mote(p,p.lerp(focus,.09),i%7==0?0xe3e9ff:0x91b5db,i%7==0?1:0,.045f,28);
            }
        }
        // A continuous curved shower visibly ends in the actual instrument, never a skyward dotted rod.
        for(int strand=0;strand<(sight?3:1);strand++)for(int i=0;i<(sight?14:5);i++){
            double t=(time*.012+i/(sight?14.:5.)+strand*.11)%1,a=strand*2.094+t*4;
            Vec3 p=focus.lerp(bowl,t).add(Math.cos(a)*Math.sin(Math.PI*t)*.32,0,Math.sin(a)*Math.sin(Math.PI*t)*.32);
            mote(p,p.lerp(bowl,.13),sun?(i%4==0?0xffe5a0:0xeda849):(i%4==0?0xe7f4ff:0x82b9df),i%7==0?1:0,sight?.048f:.026f,22);
        }
        for(int i=0;i<(sight?18:5);i++){
            double r=.07+((time*.018+i*.055)%1)*.38,a=i*2.399+time*.015;
            Vec3 p=bowl.add(Math.cos(a)*r,-.12,Math.sin(a)*r);
            mote(p,p.add(Math.cos(a)*.045,0,Math.sin(a)*.045),sun?0xffd586:0xafd7f4,0,.025f,22);
        }
    }
    private static Vec3 point(Vec3 c,Vec3 u,Vec3 v,double a,double r){return c.add(u.scale(Math.cos(a)*r)).add(v.scale(Math.sin(a)*r));}
    private static void mote(Vec3 from,Vec3 to,int color,int shape,float size,int life){ManaSightParticles.effect(from,to,color,shape,3,size,life);}
    private CelestialCollectorVisual(){}
}
