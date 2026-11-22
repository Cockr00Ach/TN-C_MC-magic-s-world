package com.tnc.tnc.mana.sight.client;

import com.tnc.tnc.arcana.client.ManaRealmRenderer;
import com.tnc.tnc.skyacademy.SkyAcademyWorld;
import com.tnc.tnc.skyacademy.client.AcademyClient;
import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

/** Both the authored summit and ordinary night skies use the same showers and drifting grain clouds. */
final class ManaSkyWeather {
    private static final int[] COLORS={0x8EEBFF,0xA5A2FF,0xF3B6E7,0xF6DFAD,0x98F3D9};
    private static long ridgeStart=Long.MIN_VALUE;
    static void clear(){ridgeStart=Long.MIN_VALUE;}

    static void tick(ClientLevel level,Vec3 eye,long clock,long viewer){
        if(!level.dimensionType().hasSkyLight() || !level.canSeeSky(BlockPos.containing(eye))) {clear();return;}
        var quality=Minecraft.getInstance().options.particles().get();
        int density=quality==ParticleStatus.MINIMAL?20:quality==ParticleStatus.DECREASED?36:60;
        var origin=AcademyClient.origin();
        if(origin!=null&&(level.dimension().equals(SkyhavenContent.KEY)||level.dimension().equals(Level.OVERWORLD))){
            var ridge=Vec3.atCenterOf(origin.offset(SkyAcademyWorld.SITES.get("stargaze")));
            if(eye.distanceToSqr(ridge)<48*48){
                if(ridgeStart==Long.MIN_VALUE)ridgeStart=clock;
                long age=clock-ridgeStart+Math.floorMod(viewer,24);
                long cycle=age/180;
                double offset=Math.floorMod(viewer>>>6,628)*.01;
                var centre=ridge.add(Math.sin(cycle*2.399+offset)*57,12+Math.cos(cycle*1.7+offset)*14,85+Math.sin(cycle*.9)*18);
                cloud(level,centre,age,density);
                shower(ridge.add(0,0,116),age,Math.sin(offset)*.4,160);
                return;
            }
        }
        clear();
        // Position by landscape cell. Turning the camera leaves constellations in their world positions.
        int grid=96,cx=Mth.floor(eye.x/grid),cz=Mth.floor(eye.z/grid);
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++){
            long seed=ManaHabitats.cellSeed(x,z,viewer);
            var random=new Random(seed);
            int ax=x*grid+32+random.nextInt(32),az=z*grid+32+random.nextInt(32);
            if(!level.hasChunkAt(new BlockPos(ax,0,az)))continue;
            int ground=level.getHeight(Heightmap.Types.WORLD_SURFACE,ax,az);
            if(ground<=level.getMinBuildHeight())continue;
            var centre=new Vec3(ax,ground+39+random.nextInt(21),az);
            if(eye.distanceToSqr(centre)>155*155||!ManaRealmRenderer.naturalAt(centre))continue;
            long time=clock+random.nextInt(6400),cycle=time/180;
            if(cycle%3==0)cloud(level,centre.add(15*Math.sin(cycle),0,12*Math.cos(cycle)),time,density/3);
            shower(centre,time,random.nextDouble()*2*Math.PI,650);
        }
    }

    private static void cloud(ClientLevel level,Vec3 centre,long time,int density){
        int count=(int)(density*Math.sin(Math.PI*(time%180)/180.));
        int color=COLORS[Math.floorMod(time/180,COLORS.length)];
        var random=level.random;
        for(int i=0;i<count;i++){
            double x=random.nextGaussian()*12,y=random.nextGaussian()*5,z=random.nextGaussian()*7;
            var at=centre.add(x,y,z);
            ManaSightParticles.scenic(at,at.add(x*.18+2,y*.2+1,z*.18),color,0,.075f+random.nextFloat()*.065f,30);
        }
    }

    private static void shower(Vec3 centre,long time,double bearing,int period){
        int phase=Math.floorMod(time-12,period);
        if(phase>=61)return;
        double c=Math.cos(bearing),s=Math.sin(bearing);
        var velocity=new Vec3(-2.1*c-.08*s,-.65,-2.1*s+.08*c);
        for(int star=0;star<5;star++){
            int t=phase-star*9;
            if(t<0||t>=25)continue;
            double x=65-star*23,z=star*9;
            var head=centre.add(c*x-s*z,48+star%3*8,s*x+c*z).add(velocity.scale(t));
            ManaSightParticles.scenic(head,head.add(velocity.scale(2)),0xF5F4DF,1,.65f,6);
            for(int tail=1;tail<=9;tail++){
                var at=head.subtract(velocity.scale(tail*.55));
                ManaSightParticles.scenic(at,at.add(velocity.scale(.4)),COLORS[star],0,(float)(.32*(1-tail/11.)),10);
            }
        }
    }
}

