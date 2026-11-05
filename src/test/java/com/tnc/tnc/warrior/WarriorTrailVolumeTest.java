package com.tnc.tnc.warrior;

import java.util.*;
import java.nio.file.*;
import com.google.gson.GsonBuilder;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarriorTrailVolumeTest {
    @Test void bladeLengthGradientKeepsTipColourAndSupportsWhiteAndDarkRoots(){
        for(String weapon:List.of("dragon_sword","doom_scythe")){
            var style=WarriorTrailAppearance.of(weapon,WarriorWeaponFamily.GREATSWORD,"");
            assertEquals(weapon.equals("doom_scythe")?0x10101B:0xF5F7FF,style.base());
            boolean[] gradient={false};float[] brightest={0};
            WarriorTrailVolume.emit(List.of(point(0,0),point(60,70)),70,320,.1,1.8,style,.62f,new WarriorTrailVolume.Sink(){
                public void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float aa,float ab,float ac,float ad){
                    brightest[0]=Math.max(brightest[0],Math.max(ab,ac));
                }
                public void gradient(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int ca,int cb,int cc,int cd,float aa,float ab,float ac,float ad){
                    if(ca==style.base() && cb==style.shell()){
                        assertEquals(style.shell(),cc);assertEquals(style.base(),cd);
                        gradient[0]=true;
                    }
                }
            });
            assertTrue(gradient[0],"lengthwise gradient must reach the vertex consumer");
            assertTrue(brightest[0]>.62f && brightest[0]<1,"narrow tip is brighter while remaining translucent");
        }
    }
    @Test void translucentTrailKeepsFeatherTransparentAndFadesWithAge(){
        var curve=WarriorTrailVolume.resample(List.of(point(0,0),point(65,55),point(140,110)),96);
        var style=WarriorTrailAppearance.of("dragon_sword",WarriorWeaponFamily.LONGSWORD,"");
        float[] peaks=new float[2];
        boolean[] feather={false};
        for(int sample=0;sample<2;sample++){
            final int index=sample;
            WarriorTrailVolume.emit(curve,110+sample*100,320,.18,1.35,style,.62f,(a,b,c,d,color,aa,ab,ac,ad)->{
                for(float alpha:new float[]{aa,ab,ac,ad}){
                    assertTrue(alpha>=0 && alpha<=.92f,"no opaque sheets");
                    peaks[index]=Math.max(peaks[index],alpha);
                    if(alpha==0)feather[0]=true;
                }
            });
        }
        assertTrue(feather[0]);
        assertTrue(peaks[0]>peaks[1] && peaks[1]>0,"tail must fade rather than pop off");
    }
    private static WarriorTrailVolume.Point point(double degrees,double at){
        double a=Math.toRadians(degrees);return new WarriorTrailVolume.Point(Vec3.ZERO,new Vec3(Math.cos(a)*2,0,Math.sin(a)*2),at);
    }
    @Test void fastRotationKeepsBladeLengthAndHandlesAntipodalDirections(){
        for(double degrees:new double[]{0,90,150,180})for(double t=0;t<=1;t+=.025){
            var p=WarriorTrailVolume.interpolate(point(0,0),point(degrees,33),t);
            assertEquals(2,p.hilt().distanceTo(p.tip()),1e-8,"arc interpolation must not collapse the blade radius");
            assertTrue(Double.isFinite(p.tip().x+p.tip().y+p.tip().z));
        }
    }
    @Test void resamplingHasHardPointAndQuadBudgetsAcrossFrameRates(){
        for(int fps:new int[]{15,30,60,144,360}){
            var raw=new ArrayList<WarriorTrailVolume.Point>();
            for(int i=0;i<=Math.max(2,fps/4);i++)raw.add(point(i*720.0/fps,i*1000.0/fps));
            for(int budget:new int[]{40,96}){
                var curve=WarriorTrailVolume.resample(raw,budget);assertTrue(curve.size()<=budget);
                assertEquals(raw.get(raw.size()-1),curve.get(curve.size()-1));
                int quads=WarriorTrailVolume.emit(curve,raw.get(raw.size()-1).at(),440,.12,1.45,
                        WarriorTrailAppearance.of("crimson_warblade",WarriorWeaponFamily.GREATSWORD,""),.7f,
                        (a,b,c,d,col,aa,ab,ac,ad)->{for(var p:List.of(a,b,c,d))assertTrue(Double.isFinite(p.x+p.y+p.z));});
                assertTrue(quads<=WarriorTrailVolume.maxQuads(budget));
            }
        }
        assertEquals(667,WarriorTrailVolume.maxQuads(96));
    }
    @Test void actualVolumeHasSideThicknessAndExpiresCompletely() throws Exception {
        var curve=WarriorTrailVolume.resample(List.of(point(0,0),point(65,55),point(140,110)),96);
        var faces=new ArrayList<Object>();var y=new ArrayList<Double>();
        var style=WarriorTrailAppearance.of("dragon_sword",WarriorWeaponFamily.LONGSWORD,"");
        WarriorTrailVolume.emit(curve,110,320,.18,1.35,style,.7f,(a,b,c,d,color,aa,ab,ac,ad)->{
            for(var p:List.of(a,b,c,d))y.add(p.y);
            faces.add(Map.of("points",List.of(List.of(a.x,a.y,a.z),List.of(b.x,b.y,b.z),List.of(c.x,c.y,c.z),List.of(d.x,d.y,d.z)),
                    "color",color,"alpha",List.of(aa,ab,ac,ad)));
        });
        assertTrue(Collections.max(y)-Collections.min(y)>=style.thickness()*.9,"sweep must be a volume, not a coplanar sheet");
        assertEquals(0,WarriorTrailVolume.emit(curve,1000,320,.18,1.35,style,.7f,(a,b,c,d,col,aa,ab,ac,ad)->fail("expired geometry")));
        Path out=Path.of("build/tnc-warrior-trail-volume.json");Files.createDirectories(out.getParent());
        Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(faces));
    }
    @Test void budgetApportionmentNeverOvershootsByRounding(){
        var random=new Random(11);
        for(int run=0;run<1000;run++){
            int n=1+random.nextInt(30),sum=0;int[] steps=new int[n];for(int i=0;i<n;i++){steps[i]=1+random.nextInt(24);sum+=steps[i];}
            int budget=n+random.nextInt(Math.max(1,sum-n+1));var limited=WarriorTrailCurve.limit(steps,budget);
            assertTrue(Arrays.stream(limited).sum()<=budget);for(int count:limited)assertTrue(count>=1);
        }
    }
    @Test void idleGuardAndChargeNeverShowGhostsAndWeaponsHaveDistinctColours(){
        assertFalse(WarriorTrailAppearance.attacking(null,false,false));
        for(var animation:List.of(WarriorAnimation.SWORD_STANCE,WarriorAnimation.GS_STANCE,WarriorAnimation.SCYTHE_GUARD,WarriorAnimation.SWORD_CHARGE))
            assertFalse(WarriorTrailAppearance.attacking(animation,true,true));
        assertTrue(WarriorTrailAppearance.attacking(WarriorAnimation.GS_SMASH,false,false));
        assertEquals(0,WarriorTrailAppearance.visibility(1000,1000,false));
        assertEquals(0,WarriorTrailAppearance.visibility(0,0,true));
        assertEquals(1,WarriorTrailAppearance.visibility(40,0,true));
        assertTrue(WarriorTrailAppearance.visibility(100,45,false)>0);
        assertEquals(0,WarriorTrailAppearance.visibility(200,90,false));
        var colors=new HashSet<Integer>();
        for(String id:List.of("dragon_sword","frost_katana","star_rapier","crimson_warblade","doom_scythe","earthshaker_hammer","sky_halberd","greatsword","hammer","spear"))
            assertTrue(colors.add(WarriorTrailAppearance.of(id,WarriorWeaponFamily.LONGSWORD,"").shell()),id);
        var base=WarriorTrailAppearance.of("dragon_sword",WarriorWeaponFamily.LONGSWORD,"");
        var ice=WarriorTrailAppearance.of("dragon_sword",WarriorWeaponFamily.LONGSWORD,"ice");
        assertEquals(base.shell(),ice.shell());assertNotEquals(base.core(),ice.core());
    }
}
