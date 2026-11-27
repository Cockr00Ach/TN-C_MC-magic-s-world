package com.tnc.tnc.life.seawild.client;

import com.tnc.tnc.life.seawild.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Member state exists only while its school is visible on this client. */
final class SeaSchool {
    final Vec3[] current,previous;
    final float[] turn,previousTurn,phase,bodyScale;
    private final int[] lastMemberTick;
    int lastTick=Integer.MIN_VALUE;
    private float panicPhase,panicStep;

    float panicPhase(float partial) { return panicPhase+partial*panicStep; }

    SeaSchool(SeaCreature entity) {
        int n=entity.schoolSize();
        current=new Vec3[n];previous=new Vec3[n];lastMemberTick=new int[n];
        java.util.Arrays.fill(lastMemberTick,-1000);
        turn=new float[n];previousTurn=new float[n];phase=new float[n];bodyScale=new float[n];
        var random=new java.util.Random(entity.getUUID().getMostSignificantBits());
        for (int i=0;i<n;i++) {
            current[i]=previous[i]=Vec3.ZERO;
            turn[i]=previousTurn[i]=entity.getYRot();
            phase[i]=random.nextFloat()*6.283f;
            bodyScale[i]=.78f+.46f*random.nextFloat();
        }
    }

    void step(SeaCreature e,int stride) {
        if (lastTick==e.tickCount) return;
        boolean stale=lastTick==Integer.MIN_VALUE || e.tickCount-lastTick>6;
        int elapsed=stale ? 0 : e.tickCount-lastTick;
        // Accumulate the change of frequency: an old shoal must not jump phase on alarm.
        panicPhase=(panicPhase+elapsed*panicStep)%Mth.TWO_PI;
        panicStep=.18f*e.spread(1);
        lastTick=e.tickCount;
        double yaw=Math.toRadians(-e.getYRot());
        boolean reef=e.kind==SeaKind.REEF;
        float panic=e.spread(1);
        int count=current.length;
        for(int i=0;i<count;i+=stride) {
            boolean fresh=stale || e.tickCount-lastMemberTick[i]>5;
            lastMemberTick[i]=e.tickCount;
            float ph=phase[i];
            double t=e.tickCount*(.020+.007*Math.sin(ph));
            double angle=i*2.399963+ph*.24+.15*Math.sin(t+ph);
            double radius=Math.sqrt((i+.33)/count);
            double side=Math.cos(angle)*radius*(reef?1.98:1.48);
            double forward=Math.sin(angle)*radius*(reef?1.75:3.08);
            side+=Math.copySign(panic*(1.32+.50*radius),side)+.19*Math.sin(t+ph);
            forward+=.24*Math.cos(t*.85+ph);
            double height=.25+.47*Math.sin(i*2.913+ph)+.11*Math.sin(t*1.17+ph);
            var desired=new Vec3(side*Math.cos(yaw)+forward*Math.sin(yaw),height,
                    forward*Math.cos(yaw)-side*Math.sin(yaw));
            if (fresh || (lastTick+i)%4==0) {
                // Test a corridor from the school centre through each member to its nose.
                Vec3 origin=e.position().add(0,.18,0);
                double extent=1;
                for(int probe=1;probe<=4;probe++) {
                    if (!SeaCreature.swimmable(e.level(),origin.add(desired.scale(probe*.27)))) {
                        extent=(probe-1)*.24;break;
                    }
                }
                shrink[i]=extent;
            }
            desired=desired.scale(shrink[i]);
            Vec3 old=current[i];
            Vec3 next=fresh?desired:old.lerp(desired,.19);
            // Moving anchors can brush a shore between corridor tests.
            if ((lastTick+i)%2==0 && !SeaCreature.swimmable(e.level(),e.position().add(next).add(0,.17,0))) next=Vec3.ZERO;
            previous[i]=fresh?next:old;
            current[i]=next;
            previousTurn[i]=turn[i];
            Vec3 heading=next.subtract(old).add(-Math.sin(Math.toRadians(e.getYRot()))*.085,0,
                    Math.cos(Math.toRadians(e.getYRot()))*.085);
            float direction=(float)(Math.atan2(-heading.x,heading.z)*Mth.RAD_TO_DEG);
            turn[i]=fresh?e.getYRot():Mth.approachDegrees(turn[i],direction,8.5f);
        }
    }

    private final double[] shrink=new double[45];
}
