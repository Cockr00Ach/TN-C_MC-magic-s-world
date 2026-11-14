package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.mana.sight.client.ManaSightParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;

/** World anchored prismatic arch and brief showers; shares the scenic particle budget. */
final class SummitMagicVista {
    private static long previous=Long.MIN_VALUE;
    private static int age;
    private static Object world;
    private static final int[] COLORS={0x8EEBFF,0xA5A2FF,0xF3B6E7,0xF6DFAD,0x98F3D9};
    static void tick(Vec3 ridge,long clock) {
        var mc=Minecraft.getInstance();
        if(world!=mc.level || clock-previous!=1) {age=0;world=mc.level;}
        previous=clock;age++;
        int count=mc.options.particles().get()==ParticleStatus.MINIMAL?20:
                mc.options.particles().get()==ParticleStatus.DECREASED?36:60;
        if(!FirstSightScene.active())return;
        // Soft clouds form and disperse; a new world-space centre follows each cycle.
        int cycle=age/180;
        double progress=(age%180)/180.;
        double envelope=Math.sin(Math.PI*progress);
        var random=mc.level.random;
        var centre=ridge.add(Math.sin(cycle*2.399)*57,12+Math.cos(cycle*1.7)*14,85+Math.sin(cycle*.9)*18);
        for(int i=0;i<(int)(count*envelope);i++) {
            double x=random.nextGaussian()*12,y=random.nextGaussian()*5,z=random.nextGaussian()*7;
            var at=centre.add(x,y,z);
            var drift=new Vec3(x*.18+2,y*.2+1,z*.18);
            ManaSightParticles.scenic(at,at.add(drift),COLORS[Math.floorMod(cycle,5)],0,.075f+random.nextFloat()*.065f,30);
        }
        // Five staggered shooting stars at first sight, then a brief shower every 24 seconds.
        int phase=(age-12)%160;
        if(phase<0)return;
        for(int star=0;star<5;star++) {
            int t=phase-star*9;
            if(t<0||t>=25)continue;
            var start=ridge.add(65-star*23,48+star%3*8,116+star*9);
            var velocity=new Vec3(-2.1,-.65,.08);
            var head=start.add(velocity.scale(t));
            ManaSightParticles.scenic(head,head.add(velocity.scale(2)),0xF5F4DF,1,.65f,6);
            for(int tail=1;tail<=9;tail++) {
                var at=head.subtract(velocity.scale(tail*.55));
                ManaSightParticles.scenic(at,at.add(velocity.scale(.4)),COLORS[star],0,
                        (float)(.32*(1-tail/11.)),10);
            }
        }
    }
}
