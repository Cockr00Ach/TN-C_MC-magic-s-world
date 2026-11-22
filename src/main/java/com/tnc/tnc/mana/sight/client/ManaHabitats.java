package com.tnc.tnc.mana.sight.client;

import com.tnc.tnc.arcana.client.ManaRealmRenderer;
import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import static com.tnc.tnc.mana.sight.client.SpiritShape.Kind;
import static com.tnc.tnc.mana.sight.client.SpiritSwarm.Flight;

/** Stable surface habitats and regional migrations. Only the camera's neighbourhood is materialised. */
final class ManaHabitats {
    record Site(long seed, Vec3 centre, Kind kind, Flight flight,int number,
                        double size,double extent,double range,long until) {}
    private record Candidate(Site site,double distance) {}
    private static final Map<Long,SpiritSwarm> active=new HashMap<>();
    private static long lastScan;
    private static Vec3 lastEye;
    private static ParticleStatus lastQuality;
    private static boolean whalePresent;
    private static final WhaleSong whaleSong=new WhaleSong();
    private static final int WHALE_REGION=2048;

    static boolean deepNight(long time){
        long t=Math.floorMod(time,24000);
        return t>=13800 && t<22200;
    }

    static boolean nightSky(ClientLevel world){
        var type=world.dimensionType();
        return type.hasSkyLight() && deepNight(type.fixedTime().orElse(world.getDayTime()));
    }

    static boolean whalePresent(){return whalePresent;}

    static void clear(){
        whaleSong.clear();
        active.values().forEach(SpiritSwarm::remove);
        active.clear();lastEye=null;lastQuality=null;whalePresent=false;
        ManaSkyWeather.clear();
    }

    static void update(ClientLevel level,Vec3 eye,SpriteSet sprites,boolean dead){
        if(dead || !nightSky(level)){
            if(lastEye!=null){clear();ManaSightParticles.clearScenery();}
            return;
        }
        long clock=level.getGameTime();
        var quality=Minecraft.getInstance().options.particles().get();
        if(quality!=lastQuality)clear();
        long salt=viewerSeed(Minecraft.getInstance().player.getUUID(),level.dimension().location().hashCode());
        if(lastEye==null||clock<lastScan||clock-lastScan>=10||lastEye.distanceToSqr(eye)>=64)
            refresh(level,eye,clock,salt,sprites,quality);
        if(!whalePresent)ManaSkyWeather.tick(level,eye,clock,salt);
    }

    private static void refresh(ClientLevel level,Vec3 eye,long clock,long salt,SpriteSet sprites,ParticleStatus quality){
        lastEye=eye;lastScan=clock;lastQuality=quality;
        int cap=quality==ParticleStatus.MINIMAL?4:quality==ParticleStatus.DECREASED?7:10;
        var candidates=new ArrayList<Candidate>();
        Long previous=whalePresent&&!active.isEmpty()?active.keySet().iterator().next():null;
        Site whale=whalePassage(eye,level.getMaxBuildHeight(),clock,salt,previous);
        if(whale!=null && ManaRealmRenderer.naturalAt(whale.centre)){
            if(!whalePresent)ManaSightParticles.clearForWhale();
            candidates.add(new Candidate(whale,whale.centre.distanceTo(eye)));
            whalePresent=true;
            whaleSong.update(whale,clock);
        }else{
            if(whalePresent)whaleSong.clear();
            whalePresent=false;
            collect(level,eye,clock,salt,112,2,true,quality,candidates);
            collect(level,eye,clock,salt,32,2,false,quality,candidates);
        }
        // Give regional visitors a slot even when the foreground is busy.
        candidates.sort(Comparator.comparingInt((Candidate c)->c.site.until==Long.MAX_VALUE?1:0)
                .thenComparingDouble(c->c.distance-(active.containsKey(c.site.seed)?5:0)));
        var selected=new HashSet<Long>();
        for(var c:candidates){
            if(selected.size()>=cap)break;
            var s=c.site;
            selected.add(s.seed);
            var old=active.get(s.seed);
            if(old!=null&&old.isAlive())continue;
            var swarm=new SpiritSwarm(level,s.centre,s.seed,s.kind,s.flight,s.number,
                    s.size,s.extent,s.range,s.until,sprites);
            active.put(s.seed,swarm);
            Minecraft.getInstance().particleEngine.add(swarm);
        }
        active.entrySet().removeIf(entry->{
            if(selected.contains(entry.getKey()))return false;
            entry.getValue().remove();return true;
        });
    }

    private static void collect(ClientLevel level,Vec3 eye,long clock,long salt,int spacing,int radius,
                                boolean regional,ParticleStatus quality,List<Candidate> result){
        int cx=Mth.floor(eye.x/spacing),cz=Mth.floor(eye.z/spacing);
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++){
            long seed=cellSeed(x,z,salt+(regional?349:0));
            var random=new Random(seed);
            int ax=x*spacing+spacing/4+random.nextInt(spacing/2);
            int az=z*spacing+spacing/4+random.nextInt(spacing/2);
            double range=regional?188:64;
            if(Math.hypot(ax-eye.x,az-eye.z)>range||!level.hasChunkAt(new BlockPos(ax,0,az)))continue;
            int floor=level.getHeight(Heightmap.Types.WORLD_SURFACE,ax,az);
            if(floor<=level.getMinBuildHeight())continue;
            Site site;
            if(regional){
                site=migration(ax,floor,az,seed,clock,random,quality);
            }else{
                site=local(ax,floor,az,seed,random,quality);
                // The surface of a valley may be far below an observer on a ridge.
                // Each open-air stratum has its own residents, at fixed world positions.
                int band=Math.floorDiv(Mth.floor(eye.y),32);
                for(int b=band-1;b<=band+1;b++){
                    int base=b*32;
                    if(base<floor+22)continue;
                    long skySeed=cellSeed(ax,b,seed^az);
                    var sky=local(ax,base,az,skySeed,new Random(skySeed),quality);
                    addCandidate(level,eye,sky,result);
                }
            }
            addCandidate(level,eye,site,result);
        }
    }

    private static void addCandidate(ClientLevel level,Vec3 eye,Site site,List<Candidate> result){
        if(site==null||!ManaRealmRenderer.naturalAt(site.centre))return;
        if(level.dimension().equals(SkyhavenContent.KEY)&&site.centre.y<120)return;
        double distance=site.centre.distanceTo(eye);
        if(distance<site.range)result.add(new Candidate(site,distance));
    }

    static long viewerSeed(UUID viewer,int dimension){
        return Long.rotateLeft(viewer.getMostSignificantBits(),19)^viewer.getLeastSignificantBits()^((long)dimension*2654435761L);
    }

    static long cellSeed(int x,int z,long salt){
        long n=(long)x*341873128712L^(long)z*132897987541L^salt*1274126177;
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return n^(n>>>31);
    }

    private static Site local(int x,int ground,int z,long seed,Random r,ParticleStatus quality){
        int roll=r.nextInt(20);
        Kind kind=roll<8?Kind.LANTERN:roll<11?Kind.JELLY:roll<14?Kind.RIBBON:
                roll<16?Kind.SUNFISH:roll<18?Kind.OCTOPUS:Kind.FANG;
        boolean school=kind==Kind.LANTERN;
        int count=school?5+r.nextInt(5):kind==Kind.JELLY?2+r.nextInt(3):1;
        double size=switch(kind){
            case LANTERN -> 1.6+r.nextDouble()*1.2;
            case RIBBON -> 5+r.nextDouble()*3;
            case FANG -> 6+r.nextDouble()*3;
            case JELLY -> 2.3+r.nextDouble()*1.5;
            default -> 2.9+r.nextDouble()*1.7;
        };
        int height=(school||kind==Kind.OCTOPUS)?3+r.nextInt(6):8+r.nextInt(16);
        return new Site(seed,new Vec3(x,ground+height,z),kind,school?Flight.SCHOOL:Flight.DRIFT,
                quality==ParticleStatus.MINIMAL?Math.max(1,count/2):count,size,school?10:6,64,Long.MAX_VALUE);
    }

    // These routes belong to the sky. Their location must also exist when the terrain below
    // them is outside the client's chunk cache. Nine regions cover the full viewing radius.
    static Site whalePassage(Vec3 eye,int buildCeiling,long clock,long salt,Long previous){
        int cx=Mth.floor(eye.x/WHALE_REGION),cz=Mth.floor(eye.z/WHALE_REGION);
        Site closest=null;
        double best=Double.POSITIVE_INFINITY;
        for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++){
            long seed=cellSeed(x,z,salt^0x5748414c45L);
            var route=new Random(seed);
            long tide=clock+route.nextInt(7200),epoch=Math.floorDiv(tide,7200);
            int elapsed=Math.floorMod(tide,7200);
            if(elapsed>=1400)continue;
            long id=seed^epoch*0x9e3779b97f4a7c15L;
            var visitor=new Random(id);
            if(visitor.nextInt(6)!=0)continue;
            var centre=new Vec3((x+.25+.5*route.nextDouble())*WHALE_REGION,
                    buildCeiling+204+route.nextInt(48),(z+.25+.5*route.nextDouble())*WHALE_REGION);
            double d=centre.distanceToSqr(eye);
            if(d>=(double)WHALE_REGION*WHALE_REGION)continue;
            var site=new Site(id,centre,Kind.WHALE,Flight.PASSAGE,1,870+visitor.nextDouble()*166,74,WHALE_REGION,clock+1400-elapsed);
            if(previous!=null&&previous==id)return site;
            if(d<best){closest=site;best=d;}
        }
        return closest;
    }

    private static Site migration(int x,int ground,int z,long seed,long clock,Random r,ParticleStatus quality){
        long tide=clock+r.nextInt(2200),epoch=Math.floorDiv(tide,2200);
        int tick=Math.floorMod(tide,2200);
        if(tick>=820)return null;
        var shoal=new Random(seed^epoch*0x632be59bd9b4e019L);
        if(shoal.nextInt(3)==0)return null;
        boolean ring=shoal.nextBoolean();
        int count=quality==ParticleStatus.MINIMAL?36:quality==ParticleStatus.DECREASED?70:112;
        return new Site(seed^epoch^0x53484f414cL,new Vec3(x,ground+26+shoal.nextInt(15),z),
                Kind.LANTERN,ring?Flight.RING:Flight.STREAM,count,ring?2.2:2.7,
                ring?15:34,142,clock+820-tick);
    }
}

