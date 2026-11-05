package com.tnc.tnc.magic;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side ground debris for every earth presentation. Fixed cadence and a
 * bounded budget: no server particle packets, no block edits, no per-frame work.
 *
 * <p>The opening beat is the important one: chips are thrown out of each column
 * base while the column is still breaking the surface, together with a ground
 * dust ring that keeps expanding after the chips land.</p>
 */
public final class EarthSpellParticles {
    private EarthSpellParticles() {}

    public static int count(int tier,boolean opening) {return opening?8+tier*2:2+tier;}

    public static void tick(WindEarthVisualEntity entity) {
        if(!entity.level().isClientSide()||entity.tickCount<=0||entity.tickCount>=entity.life()-8)return;
        int age=entity.tickCount,kind=entity.kind(),tier=entity.tier();
        boolean opening=age<=18;
        // Dense while the rock is coming out of the ground, sparse afterwards.
        if(opening?(age&1)!=0:(age&3)!=0)return;
        var level=entity.level();
        var owner=entity.followsOwner()?level.getEntity(entity.ownerId()):null;
        var centre=owner==null?entity.position():owner.position();
        double radius=Math.max(.7,entity.radius());
        var dirt=new BlockParticleOption(ParticleTypes.BLOCK,Blocks.COARSE_DIRT.defaultBlockState());
        var stone=new BlockParticleOption(ParticleTypes.BLOCK,Blocks.STONE.defaultBlockState());
        if(kind==3) {projectile(level,centre,tier,opening,dirt);return;}
        if(kind==5) {mud(level,centre,radius,age,tier,opening,dirt,stone);return;}
        if(kind<4)return;
        int columns=kind==6?10+tier*2:5+tier*2;
        columns(level,centre,radius,age,tier,opening,columns,kind==6,dirt,stone);
    }

    private static void columns(Level level,Vec3 c,double radius,int age,int tier,boolean opening,
                                int columns,boolean aura,BlockParticleOption dirt,BlockParticleOption stone) {
        double phase=Math.min(1,age/18.0);
        if(opening) {
            int n=count(tier,true);
            for(int i=0;i<n;i++) {
                int column=(i+age)%Math.max(1,columns);
                double angle=column*Math.PI*2/columns+(EarthRuptureLayout.jitter(i+age,31)-.5)*.22;
                double base=aura?radius*.78:radius*(.42+.22*(column%3)/2);
                double x=c.x+Math.cos(angle)*base,z=c.z+Math.sin(angle)*base;
                double out=.07+EarthRuptureLayout.jitter(i,32)*.07;
                level.addParticle(column%3==0?stone:dirt,x,c.y+.12,z,
                        Math.cos(angle)*out,.17+EarthRuptureLayout.jitter(i,33)*.2,Math.sin(angle)*out);
            }
            // Ground dust ring: starts tight, expands with the rupture.
            double ring=radius*(.18+.9*phase);
            for(int i=0;i<3;i++) {
                double angle=age*.7+i*Math.PI*2/3;
                level.addParticle(EarthParticles.DUST.get(),c.x+Math.cos(angle)*ring,c.y+.08,c.z+Math.sin(angle)*ring,
                        Math.cos(angle)*.13,.012,Math.sin(angle)*.13);
            }
            if(age==16) { // The columns lock in with a low, fast shock ring.
                for(int i=0;i<12;i++) {
                    double angle=i*Math.PI*2/12,r=radius*.82;
                    level.addParticle(EarthParticles.DUST.get(),c.x+Math.cos(angle)*r,c.y+.06,c.z+Math.sin(angle)*r,
                            Math.cos(angle)*.19,.02,Math.sin(angle)*.19);
                }
            }
            // Each staggered batch gets a wind-up puff and a landing cough, so a
            // column reads as weight hitting the floor twice instead of sliding up.
            for(int i=0;i<columns;i++) {
                int delay=(i%5)*2;
                if(delay<1)continue;
                double angle=i*Math.PI*2/Math.max(1,columns);
                double base=aura?radius*.78:radius*(.42+.22*(i%3)/2);
                double x=c.x+Math.cos(angle)*base,z=c.z+Math.sin(angle)*base;
                if(age==delay-2) {
                    level.addParticle(dirt,x,c.y+.14,z,0,.07,0);
                    for(int k=0;k<2;k++) {
                        double a=angle+(k==0?-.42:.42);
                        level.addParticle(EarthParticles.DUST.get(),x,c.y+.05,z,Math.cos(a)*.04,.02,Math.sin(a)*.04);
                    }
                }
                if(age==delay+(int)EarthRuptureLayout.PUNCH_TICKS) {
                    level.addParticle(stone,x,c.y+.12,z,0,.05,0);
                    for(int k=0;k<3;k++) {
                        double a=angle+(k-1)*.55;
                        level.addParticle(EarthParticles.DUST.get(),x,c.y+.06,z,Math.cos(a)*.11,.015,Math.sin(a)*.11);
                    }
                }
            }
            // The first break gets one fast, tight ring: the whole field snaps at once.
            if(age==2) {
                for(int i=0;i<16;i++) {
                    double a=i*Math.PI*2/16,r=radius*.42;
                    level.addParticle(EarthParticles.DUST.get(),c.x+Math.cos(a)*r,c.y+.05,c.z+Math.sin(a)*r,
                            Math.cos(a)*.30,.01,Math.sin(a)*.30);
                }
            }
        } else {
            int n=count(tier,false);
            for(int i=0;i<n;i++) {
                int column=(i*3+age)%Math.max(1,columns);
                double angle=column*Math.PI*2/columns;
                double base=aura?radius*.7:radius*(.5+.2*(column%3));
                double x=c.x+Math.cos(angle)*base,z=c.z+Math.sin(angle)*base;
                double top=.5+tier*.22;
                if(i%2==0)level.addParticle(dirt,x,c.y+top,z,
                        Math.cos(angle)*.018,-.008,Math.sin(angle)*.018); // chips trickle off the top
                else level.addParticle(EarthParticles.DUST.get(),x,c.y+.1,z,
                        Math.cos(angle)*.03,.008,Math.sin(angle)*.03);
            }
        }
    }

    private static void mud(Level level,Vec3 c,double radius,int age,int tier,boolean opening,
                            BlockParticleOption dirt,BlockParticleOption stone) {
        double phase=Math.min(1,age/20.0);
        int n=count(tier,opening);
        for(int i=0;i<n;i++) {
            double angle=i*Math.PI*2/Math.max(1,n)+age*.05;
            double r=radius*(.25+.7*phase)*(.55+.45*EarthRuptureLayout.jitter(i,41));
            double x=c.x+Math.cos(angle)*r,z=c.z+Math.sin(angle)*r;
            if(opening) {
                double out=.05+EarthRuptureLayout.jitter(i,42)*.06;
                level.addParticle(i%4==0?stone:dirt,x,c.y+.1,z,
                        Math.cos(angle)*out,.12+EarthRuptureLayout.jitter(i,43)*.13,Math.sin(angle)*out);
            } else {
                // Suction field: dust drifts back towards the compressed centre.
                level.addParticle(EarthParticles.DUST.get(),x,c.y+.12,z,
                        -Math.cos(angle)*.035,.012,-Math.sin(angle)*.035);
            }
        }
    }

    private static void projectile(Level level,Vec3 c,int tier,boolean opening,BlockParticleOption dirt) {
        int n=count(tier,opening);
        for(int i=0;i<n;i++) {
            double angle=i*Math.PI*2/Math.max(1,n)+level.getGameTime()*.15;
            double r=.55+EarthRuptureLayout.jitter(i,51)*.25;
            level.addParticle(dirt,c.x+Math.cos(angle)*r,c.y+.5+Math.sin(angle*.7)*.35,c.z+Math.sin(angle)*r,
                    Math.cos(angle)*.02,opening?.05:.015,Math.sin(angle)*.02);
        }
    }
}
