package com.tnc.tnc.fish;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import java.nio.file.Path;

class FishSchoolGeometryTest {
    @Test void allFishStayWithinTheAdvertisedCorridorThroughoutTheCast() {
        for (boolean metal:new boolean[]{false,true}) for (int lane=0;lane<FishSchoolGeometry.COUNT;lane++) {
            for (double age=0;age<=24;age+=.125) {
                var p=FishSchoolGeometry.offset(lane,age,metal);
                assertTrue(Math.abs(p.side())+FishSchoolGeometry.HALF_WIDTH<=FishSchoolGeometry.WIDTH/2);
                assertTrue(p.forward()+FishSchoolGeometry.RADIUS<=FishSchoolGeometry.RANGE+1e-9);
                assertTrue(p.forward()>=0);
            }
        }
    }
    @Test void formationDoesNotAdvanceBeforeEightTicksAndStopsAtTheRangeLimit() {
        assertEquals(0,FishSchoolGeometry.progress(0));assertEquals(0,FishSchoolGeometry.progress(8));
        assertEquals(.5,FishSchoolGeometry.progress(16));assertEquals(1,FishSchoolGeometry.progress(24));
        assertEquals(1,FishSchoolGeometry.progress(1000));assertEquals(0,FishSchoolGeometry.progress(-100));
        for (int lane=0;lane<12;lane++) for (boolean metal:new boolean[]{false,true}) {
            assertEquals(FishSchoolGeometry.offset(lane,0,metal).forward(),FishSchoolGeometry.offset(lane,8,metal).forward());
            assertTrue(FishSchoolGeometry.offset(lane,24,metal).forward()>FishSchoolGeometry.offset(lane,8,metal).forward());
        }
    }
    @Test void sweptHullCatchesThinWallsEvenWhenTheCenterRayMisses() {
        Vec3 from=new Vec3(0,0,0),to=new Vec3(0,0,1);
        AABB thin=new AABB(.12,-.5,.4,.13,.5,.401);
        assertTrue(FishSchoolGeometry.segment(thin,from,to).isEmpty());
        Vec3 hull=FishSchoolGeometry.halfExtents(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0));
        Vec3 wall=FishSchoolGeometry.segment(thin.inflate(hull.x,hull.y,hull.z),from,to).orElseThrow();
        assertTrue(wall.z<.4);
        assertFalse(FishSchoolGeometry.beforeWall(from,new Vec3(0,0,.8),wall));
        assertFalse(FishSchoolGeometry.beforeWall(from,wall,wall));
        assertTrue(FishSchoolGeometry.beforeWall(from,new Vec3(0,0,.01),wall));
    }
    @Test void aFishStartingInsideAColliderIsBlockedImmediately() {
        Vec3 origin=new Vec3(0,0,0);
        assertEquals(origin,FishSchoolGeometry.segment(new AABB(-1,-1,-1,1,1,1),origin,new Vec3(0,0,3)).orElseThrow());
    }
    @Test void verticalAndHorizontalAimUseTheSameOrthonormalLayout() {
        for (float pitch:new float[]{-90,-30,0,45,90}) for (float yaw:new float[]{0,45,90,180}) {
            Vec3 f=Vec3.directionFromRotation(pitch,yaw).normalize();
            double a=Math.toRadians(yaw);Vec3 right=new Vec3(Math.cos(a),0,Math.sin(a));Vec3 up=f.cross(right).normalize();
            assertEquals(0,f.dot(right),1e-6);assertEquals(0,f.dot(up),1e-6);assertEquals(1,up.length(),1e-6);
            var o=FishSchoolGeometry.offset(5,16,true);
            Vec3 p=FishSchoolGeometry.world(Vec3.ZERO,f,right,up,5,16,true);
            assertEquals(o.forward(),p.dot(f),1e-5);assertEquals(o.side(),p.dot(right),1e-5);
        }
    }
    @Test void verticalAimNeverProducesAnUndefinedHorizontalKnockback() {
        Vec3 right=new Vec3(1,0,0);
        assertEquals(0,new Vec3(0,0,1).distanceToSqr(FishSchoolGeometry.horizontalKnockback(new Vec3(0,1,0),right)),1e-12);
        assertEquals(0,new Vec3(0,0,1).distanceToSqr(FishSchoolGeometry.horizontalKnockback(new Vec3(0,-1,0),right)),1e-12);
        Vec3 forward=new Vec3(.5,.5,.5);
        assertSame(forward,FishSchoolGeometry.horizontalKnockback(forward,right));
    }
    @Test void centralFishFitThroughAOneBlockOpeningWhileOuterFishAreBlocked() {
        Vec3 forward=new Vec3(0,0,1),right=new Vec3(1,0,0),up=new Vec3(0,1,0);
        Vec3 hull=FishSchoolGeometry.halfExtents(forward,right,up);
        AABB left=new AABB(-2,-2,-1,-.5,2,2).inflate(hull.x,hull.y,hull.z);
        AABB rightWall=new AABB(.5,-2,-1,2,2,2).inflate(hull.x,hull.y,hull.z);
        var center=FishSchoolGeometry.offset(1,16,true);
        Vec3 from=new Vec3(center.side(),center.up(),0),to=from.add(0,0,1);
        assertTrue(FishSchoolGeometry.segment(left,from,to).isEmpty());
        assertTrue(FishSchoolGeometry.segment(rightWall,from,to).isEmpty());
        var outer=FishSchoolGeometry.offset(0,16,true);
        from=new Vec3(outer.side(),outer.up(),0);to=from.add(0,0,1);
        assertTrue(FishSchoolGeometry.segment(left,from,to).isPresent());
    }
    @Test void agreedCostsAndCooldownsAreGameTicks() {
        assertEquals(20,FishSchoolGeometry.mana(false));assertEquals(30,FishSchoolGeometry.mana(true));
        assertEquals(160,FishSchoolGeometry.cooldown(false));assertEquals(240,FishSchoolGeometry.cooldown(true));
        assertEquals(6,FishSchoolGeometry.damage(false));assertEquals(10,FishSchoolGeometry.damage(true));
        assertEquals(24,FishSchoolGeometry.LIFETIME);
    }
    @Test void bothSchoolTexturesAreSmallOpaqueStaticAssets() throws Exception {
        for (String variant:new String[]{"living","metal"}) {
            var image=ImageIO.read(Path.of("src/main/resources/assets/tnc/textures/entity/fish_school_"+variant+".png").toFile());
            assertEquals(64,image.getWidth());assertEquals(64,image.getHeight());
            for (int x=0;x<64;x++) for (int y=0;y<64;y++) assertEquals(255,(image.getRGB(x,y)>>>24)&255);
        }
    }
}

