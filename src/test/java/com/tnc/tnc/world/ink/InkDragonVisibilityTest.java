package com.tnc.tnc.world.ink;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class InkDragonVisibilityTest {
    @Test void lowViewDistanceDoesNotClipRemoteModel(){
        var original=new org.joml.Matrix4f().perspective((float)Math.toRadians(70),1.5F,.05F,128);
        var extended=InkDragonVisibilityProjection.extend(original);
        assertEquals(original.m00(),extended.m00());assertEquals(original.m11(),extended.m11());
        assertEquals(.05F,extended.m32()/(extended.m22()-1),.0001F);
        assertTrue(extended.m32()/(extended.m22()+1)>1000);
        assertTrue(original.m32()/(original.m22()+1)<130,"Terrain matrix is not mutated");
        var wide=new org.joml.Matrix4f().perspective(1,1,.05F,2048);
        assertEquals(wide,InkDragonVisibilityProjection.extend(wide));
    }
    @Test void unloadedOrUntrackedIsNotDead(){
        assertTrue(InkDragonVisibility.retainAfterRemoval(null));
        assertTrue(InkDragonVisibility.retainAfterRemoval(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK));
        assertFalse(InkDragonVisibility.retainAfterRemoval(net.minecraft.world.entity.Entity.RemovalReason.KILLED));
        assertFalse(InkDragonVisibility.retainAfterRemoval(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
        assertFalse(InkDragonVisibility.retainAfterRemoval(net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION));
    }
    @Test void rangeFollowsTailRatherThanOnlyAnchor(){
        var s=new InkDragonVisibility.Snapshot(UUID.randomUUID(),new Vec3(0,70,0),90);
        var viewer=new Vec3(650,80,0);
        assertTrue(viewer.distanceToSqr(s.position())>512*512);
        assertTrue(InkDragonVisibility.distanceSquared(s,viewer)<512*512);
        assertEquals(0,InkDragonVisibility.distanceSquared(s,new Vec3(180,80,0)),.001);
    }
    @Test void distantViewsRemainBounded(){
        var s=new InkDragonVisibility.Snapshot(UUID.randomUUID(),new Vec3(0,70,0),90);
        assertTrue(InkDragonVisibility.distanceSquared(s,new Vec3(1000,80,0))>512*512);
        assertTrue(InkDragonVisibility.MAX_SENT<=InkDragonVisibility.MAX_CACHED);
    }
}
